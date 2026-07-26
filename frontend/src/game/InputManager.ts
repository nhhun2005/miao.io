/**
 * InputManager — Handles all player input for Mimope.
 *
 * Responsibilities:
 * - Track mouse/pointer position relative to canvas center
 * - Convert mouse position to movement angle and distance
 * - Track dash input (left-click / spacebar / two-finger touch); a tap dashes
 *   once, holding the control re-dashes on every cooldown
 * - Assign monotonically increasing sequence numbers to input frames
 * - Throttle input sending rate (configurable, default 20 Hz)
 * - Handle window blur/focus to prevent stuck inputs
 * - Abstract pointer vs touch for future mobile support
 *
 * The InputManager is owned by PixiGame and exposes a snapshot of the
 * current input state that can be sent to the server or read by the
 * Zustand input store.
 */

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** A snapshot of the player's input at a point in time. */
export interface InputSnapshot {
  /** Monotonically increasing sequence number. */
  seq: number;
  /** Movement angle in radians (0 = right, PI/2 = down). */
  angle: number;
  /** Movement magnitude: 1 while steering, 0 inside the dead zone. */
  intensity: number;
  /**
   * Whether the player is requesting a dash this frame — set by a fresh tap
   * and kept set for as long as a dash control is held down.
   */
  dash: boolean;
  /** Timestamp when this snapshot was created (ms). */
  timestamp: number;
}

/** Callback invoked when a throttled input frame is ready to send. */
export type InputSendCallback = (snapshot: InputSnapshot) => void;

export interface InputManagerOptions {
  /** The DOM element to listen on (typically the game container). */
  container: HTMLElement;
  /** Target input send rate in Hz. Default: 20. */
  sendRate?: number;
  /** Callback invoked at the throttled rate with the latest input. */
  onInput?: InputSendCallback;
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Default input send rate (times per second). */
const DEFAULT_SEND_RATE = 20;

/** Minimum pointer distance (px) before we consider it "moving". */
const DEAD_ZONE = 3;

/** Keyboard codes that trigger a dash. Holding any of them auto-repeats it. */
const DASH_KEYS = ['Space', 'KeyW', 'Enter'];

// ---------------------------------------------------------------------------
// InputManager class
// ---------------------------------------------------------------------------

export class InputManager {
  private container: HTMLElement;
  private destroyed = false;

  // Pointer state
  private pointerX = 0; // offset from canvas center (px)
  private pointerY = 0;
  private canvasWidth = 0;
  private canvasHeight = 0;

  // Computed movement
  private _angle = 0;
  private _intensity = 0;

  // Action state
  private _dashTriggered = false; // single-fire flag (one tap = one dash)

  /**
   * Whether the pointer's dash button is currently held down. While any dash
   * control is held, every input frame carries {@code dash: true}, so the
   * server re-fires the dash the moment its cooldown expires instead of
   * waiting for another click. Held dash keys are tracked separately in
   * {@link keysDown}.
   */
  private _dashHeldPointer = false;

  // Keyboard tracking for stuck-key prevention
  private keysDown = new Set<string>();

  // Sequence numbers
  private _seq = 0;

  // Throttle
  private sendRate: number;
  private sendIntervalMs: number;
  private lastSendTime = 0;
  private throttleTimer: ReturnType<typeof setInterval> | null = null;
  private onInput: InputSendCallback | null;

  // Focus state
  private _focused = true;

  // Bound handlers (for clean removal)
  private handlePointerMove: (e: PointerEvent) => void;
  private handlePointerDown: (e: PointerEvent) => void;
  private handlePointerUp: (e: PointerEvent) => void;
  private handleTouchStart: (e: TouchEvent) => void;
  private handleTouchMove: (e: TouchEvent) => void;
  private handleTouchEnd: (e: TouchEvent) => void;
  private handleKeyDown: (e: KeyboardEvent) => void;
  private handleKeyUp: (e: KeyboardEvent) => void;
  private handleContextMenu: (e: Event) => void;
  private handleWindowBlur: () => void;
  private handleWindowFocus: () => void;
  private handleVisibilityChange: () => void;

  constructor(options: InputManagerOptions) {
    this.container = options.container;
    this.sendRate = options.sendRate ?? DEFAULT_SEND_RATE;
    this.sendIntervalMs = 1000 / this.sendRate;
    this.onInput = options.onInput ?? null;

    // Bind all handlers
    this.handlePointerMove = this._onPointerMove.bind(this);
    this.handlePointerDown = this._onPointerDown.bind(this);
    this.handlePointerUp = this._onPointerUp.bind(this);
    this.handleTouchStart = this._onTouchStart.bind(this);
    this.handleTouchMove = this._onTouchMove.bind(this);
    this.handleTouchEnd = this._onTouchEnd.bind(this);
    this.handleKeyDown = this._onKeyDown.bind(this);
    this.handleKeyUp = this._onKeyUp.bind(this);
    this.handleContextMenu = this._onContextMenu.bind(this);
    this.handleWindowBlur = this._onBlur.bind(this);
    this.handleWindowFocus = this._onFocus.bind(this);
    this.handleVisibilityChange = this._onVisibilityChange.bind(this);
  }

  // -----------------------------------------------------------------------
  // Lifecycle
  // -----------------------------------------------------------------------

  /** Attach all event listeners and start the throttle timer. */
  attach(): void {
    const el = this.container;

    // Pointer events (works for both mouse and pen)
    el.addEventListener('pointermove', this.handlePointerMove);
    el.addEventListener('pointerdown', this.handlePointerDown);
    el.addEventListener('pointerup', this.handlePointerUp);

    // Releasing outside the canvas must still clear the held-dash state,
    // otherwise the creature would keep auto-dashing after the button is up.
    window.addEventListener('pointerup', this.handlePointerUp);
    window.addEventListener('pointercancel', this.handlePointerUp);

    // Touch events (for mobile abstraction)
    el.addEventListener('touchstart', this.handleTouchStart, { passive: false });
    el.addEventListener('touchmove', this.handleTouchMove, { passive: false });
    el.addEventListener('touchend', this.handleTouchEnd);

    // Keyboard events (global)
    window.addEventListener('keydown', this.handleKeyDown);
    window.addEventListener('keyup', this.handleKeyUp);

    // Prevent context menu on right-click
    el.addEventListener('contextmenu', this.handleContextMenu);

    // Focus management
    window.addEventListener('blur', this.handleWindowBlur);
    window.addEventListener('focus', this.handleWindowFocus);
    document.addEventListener('visibilitychange', this.handleVisibilityChange);

    // Cache canvas dimensions
    this._updateCanvasSize();

    // Start throttled send timer
    this.throttleTimer = setInterval(() => this._throttledSend(), this.sendIntervalMs);
  }

  /** Remove all event listeners and stop the throttle timer. */
  detach(): void {
    this.destroyed = true;
    const el = this.container;

    el.removeEventListener('pointermove', this.handlePointerMove);
    el.removeEventListener('pointerdown', this.handlePointerDown);
    el.removeEventListener('pointerup', this.handlePointerUp);
    window.removeEventListener('pointerup', this.handlePointerUp);
    window.removeEventListener('pointercancel', this.handlePointerUp);
    el.removeEventListener('touchstart', this.handleTouchStart);
    el.removeEventListener('touchmove', this.handleTouchMove);
    el.removeEventListener('touchend', this.handleTouchEnd);

    window.removeEventListener('keydown', this.handleKeyDown);
    window.removeEventListener('keyup', this.handleKeyUp);

    el.removeEventListener('contextmenu', this.handleContextMenu);

    window.removeEventListener('blur', this.handleWindowBlur);
    window.removeEventListener('focus', this.handleWindowFocus);
    document.removeEventListener('visibilitychange', this.handleVisibilityChange);

    if (this.throttleTimer !== null) {
      clearInterval(this.throttleTimer);
      this.throttleTimer = null;
    }

    this._resetAllInputs();
  }

  // -----------------------------------------------------------------------
  // Public API — read current state
  // -----------------------------------------------------------------------

  /** Movement angle in radians. */
  get angle(): number {
    return this._angle;
  }

  /** Movement intensity: 1 while steering, 0 inside the dead zone. */
  get intensity(): number {
    return this._intensity;
  }

  /** Whether a dash is being requested — a fresh tap, or a held control. */
  get dash(): boolean {
    return this._dashTriggered || this._isDashHeld();
  }

  /** Current sequence number. */
  get seq(): number {
    return this._seq;
  }

  /** Whether the window is currently focused. */
  get focused(): boolean {
    return this._focused;
  }

  /** Raw pointer offset from canvas center (px). */
  get pointerOffset(): { x: number; y: number } {
    return { x: this.pointerX, y: this.pointerY };
  }

  /** Build and return the current input snapshot (increments seq). */
  snapshot(): InputSnapshot {
    this._seq++;
    const snap: InputSnapshot = {
      seq: this._seq,
      angle: this._angle,
      intensity: this._intensity,
      // A held dash control keeps the flag raised on every frame. The server
      // ignores the request while the dash is on cooldown, so holding simply
      // re-dashes at the first tick the cooldown allows.
      dash: this._dashTriggered || this._isDashHeld(),
      timestamp: Date.now(),
    };

    // Reset single-fire dash flag after capturing
    this._dashTriggered = false;

    return snap;
  }

  /**
   * Update the canvas size cache. Call this if the container resizes
   * outside of the window resize event.
   */
  updateCanvasSize(): void {
    this._updateCanvasSize();
  }

  // -----------------------------------------------------------------------
  // Pointer / Mouse handlers
  // -----------------------------------------------------------------------

  private _onPointerMove(e: PointerEvent): void {
    this._updatePointerFromClient(e.clientX, e.clientY);
  }

  private _onPointerDown(e: PointerEvent): void {
    if (e.button === 0) {
      // Left click = dash. Holding the button keeps requesting a dash so it
      // auto-repeats as soon as the cooldown is up.
      this._dashTriggered = true;
      this._dashHeldPointer = true;
    }
  }

  private _onPointerUp(e: PointerEvent): void {
    if (e.button === 0) {
      this._dashHeldPointer = false;
    }
  }

  // -----------------------------------------------------------------------
  // Touch handlers (mobile abstraction)
  // -----------------------------------------------------------------------

  private _onTouchStart(e: TouchEvent): void {
    e.preventDefault(); // prevent scroll
    if (e.touches.length >= 1) {
      const touch = e.touches[0];
      this._updatePointerFromClient(touch.clientX, touch.clientY);
    }
    // Single touch = move; two-finger tap = dash, and holding two fingers
    // down auto-repeats it on every cooldown.
    if (e.touches.length >= 2) {
      this._dashTriggered = true;
      this._dashHeldPointer = true;
    }
  }

  private _onTouchMove(e: TouchEvent): void {
    e.preventDefault();
    if (e.touches.length >= 1) {
      const touch = e.touches[0];
      this._updatePointerFromClient(touch.clientX, touch.clientY);
    }
  }

  private _onTouchEnd(e: TouchEvent): void {
    if (e.touches.length < 2) {
      this._dashHeldPointer = false;
    }
    if (e.touches.length === 0) {
      // Reset pointer to center when no touches
      this.pointerX = 0;
      this.pointerY = 0;
      this._computeAngleAndIntensity();
    }
  }

  // -----------------------------------------------------------------------
  // Keyboard handlers
  // -----------------------------------------------------------------------

  private _onKeyDown(e: KeyboardEvent): void {
    // Avoid repeat events
    if (this.keysDown.has(e.code)) return;
    this.keysDown.add(e.code);

    switch (e.code) {
      case 'Space':
      case 'KeyW':
      case 'Enter':
        // Dash. Like the mouse button, holding the key auto-repeats the dash
        // each time the cooldown expires.
        this._dashTriggered = true;
        e.preventDefault();
        break;
    }
  }

  private _onKeyUp(e: KeyboardEvent): void {
    this.keysDown.delete(e.code);
  }

  /** Whether any dash control — pointer button or key — is held down. */
  private _isDashHeld(): boolean {
    return (
      this._dashHeldPointer ||
      DASH_KEYS.some((code) => this.keysDown.has(code))
    );
  }

  private _onContextMenu(e: Event): void {
    e.preventDefault(); // prevent right-click menu
  }

  // -----------------------------------------------------------------------
  // Focus management
  // -----------------------------------------------------------------------

  private _onBlur(): void {
    this._focused = false;
    this._resetAllInputs();
  }

  private _onFocus(): void {
    this._focused = true;
    this._updateCanvasSize(); // in case window was resized while blurred
  }

  private _onVisibilityChange(): void {
    if (document.hidden) {
      this._focused = false;
      this._resetAllInputs();
    } else {
      this._focused = true;
      this._updateCanvasSize();
    }
  }

  // -----------------------------------------------------------------------
  // Internal helpers
  // -----------------------------------------------------------------------

  /** Convert raw client coordinates to offset from canvas center. */
  private _updatePointerFromClient(clientX: number, clientY: number): void {
    this._updateCanvasSize();
    const rect = this.container.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;

    this.pointerX = clientX - centerX;
    this.pointerY = clientY - centerY;

    this._computeAngleAndIntensity();
  }

  /** Recalculate angle and intensity from pointer offset. */
  private _computeAngleAndIntensity(): void {
    const dx = this.pointerX;
    const dy = this.pointerY;
    const dist = Math.sqrt(dx * dx + dy * dy);

    if (dist < DEAD_ZONE) {
      this._angle = 0;
      this._intensity = 0;
      return;
    }

    this._angle = Math.atan2(dy, dx);

    // Fixed movement speed: the pointer only chooses a direction, never a
    // magnitude. Scaling intensity with the cursor distance made the creature
    // crawl near the center of the screen and sprint at the edges, which read
    // as an unstable, fluctuating speed.
    this._intensity = 1;
  }

  /** Cache canvas dimensions. */
  private _updateCanvasSize(): void {
    const rect = this.container.getBoundingClientRect();
    this.canvasWidth = rect.width;
    this.canvasHeight = rect.height;
  }

  /** Reset all input state — called on blur/detach to prevent stuck keys. */
  private _resetAllInputs(): void {
    this._dashTriggered = false;
    this._dashHeldPointer = false;
    this.keysDown.clear();
    // We do NOT reset pointer position or angle — the player should
    // continue in the last direction rather than snapping to center.
  }

  /** Throttled send: emit an input snapshot at the configured rate. */
  private _throttledSend(): void {
    if (this.destroyed) return;
    if (!this._focused) return;

    // The interval already paces the sends; an extra early-drop guard here only
    // ever swallowed whole input frames (including single-fire dash clicks) when
    // setInterval fired a millisecond early, which showed up as a movement hitch.
    this.lastSendTime = Date.now();

    if (this.onInput) {
      this.onInput(this.snapshot());
    }
  }
}
