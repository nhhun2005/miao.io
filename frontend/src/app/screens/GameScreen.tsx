import { useEffect, useRef, useState } from 'react';
import { useUIStore } from '../../state/uiStore';
import { useInputStore } from '../../state/inputStore';
import { GameCanvas } from '../../game/GameCanvas';
import { Button } from '../../ui';
import type { GameConnection } from '../../network/GameConnection';

/**
 * Game screen — renders the PixiJS canvas and HUD overlays.
 * Phase 9: Receives the GameConnection from LoadingScreen via window global
 * and passes it to GameCanvas for server-driven rendering.
 */
export function GameScreen() {
  const nickname = useUIStore((s) => s.nickname);
  const showDeath = useUIStore((s) => s.showDeath);

  // Input debug state from InputManager → inputStore
  const angle = useInputStore((s) => s.angle);
  const intensity = useInputStore((s) => s.intensity);
  const boost = useInputStore((s) => s.boost);
  const ability = useInputStore((s) => s.ability);
  const seq = useInputStore((s) => s.seq);
  const focused = useInputStore((s) => s.focused);

  // Retrieve the GameConnection stored by LoadingScreen
  const connectionRef = useRef<GameConnection | null>(null);

  useEffect(() => {
    const win = window as unknown as Record<string, unknown>;
    connectionRef.current = (win.__mimope_connection as GameConnection) ?? null;

    return () => {
      // Only destroy the socket when the app actually leaves the game flow.
      // React dev StrictMode intentionally mounts/unmounts effects once to
      // detect unsafe side effects; destroying the connection during that
      // probe leaves the real GameCanvas mounted without live snapshots.
      if (connectionRef.current && useUIStore.getState().screen !== 'game') {
        connectionRef.current.destroy();
        connectionRef.current = null;
        delete win.__mimope_connection;
      }
    };
  }, []);

  const connection = connectionRef.current
    ?? ((window as unknown as Record<string, unknown>).__mimope_connection as GameConnection | undefined)
    ?? null;

  const [showGridDebug, setShowGridDebug] = useState(false);

  const angleDeg = ((angle * 180) / Math.PI).toFixed(0);

  if (!connection) {
    return (
      <div className="screen screen--game">
        <div className="game-canvas-error">
          <p>⚠️ No active connection. Please rejoin.</p>
          <Button variant="primary" onClick={() => useUIStore.getState().setScreen('home')}>
            Return Home
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="screen screen--game">
      {/* PixiJS canvas fills the screen */}
      <GameCanvas connection={connection} showGridDebug={showGridDebug} />

      {/* HUD overlay — player info */}
      <div className="game-overlay game-overlay--top-left">
        <div className="game-hud-info">
          <span className="game-hud-info__nickname">🎮 {nickname || 'Player'}</span>
          <span className="game-hud-info__hint">
            Move: mouse · Boost: click/space · Ability: W/right-click · Zoom: scroll
          </span>
        </div>
      </div>

      {/* Input debug overlay */}
      <div className="game-overlay game-overlay--top-right">
        <div className="game-debug-panel">
          <div className="game-debug-panel__title">Input Debug</div>
          <div className="game-debug-panel__row">
            <span>Angle:</span>
            <span>{angleDeg}°</span>
          </div>
          <div className="game-debug-panel__row">
            <span>Intensity:</span>
            <span>{(intensity * 100).toFixed(0)}%</span>
          </div>
          <div className="game-debug-panel__row">
            <span>Boost:</span>
            <span className={boost ? 'game-debug-panel__active' : ''}>
              {boost ? '🔥 ON' : 'OFF'}
            </span>
          </div>
          <div className="game-debug-panel__row">
            <span>Ability:</span>
            <span className={ability ? 'game-debug-panel__active' : ''}>
              {ability ? '⚡ FIRED' : 'ready'}
            </span>
          </div>
          <div className="game-debug-panel__row">
            <span>Seq:</span>
            <span>{seq}</span>
          </div>
          <div className="game-debug-panel__row">
            <span>Focus:</span>
            <span className={!focused ? 'game-debug-panel__warn' : ''}>
              {focused ? '✅' : '⏸ paused'}
            </span>
          </div>
        </div>
      </div>

      {/* Grid debug toggle */}
      <div className="game-overlay game-overlay--bottom-left">
        <button
          className={`grid-debug-toggle ${showGridDebug ? 'grid-debug-toggle--active' : ''}`}
          onClick={() => setShowGridDebug((v) => !v)}
          title="Toggle spatial grid debug visualization"
        >
          {showGridDebug ? '📊 Grid: ON' : '📊 Grid: OFF'}
        </button>
      </div>

      {/* Temporary test button — will be removed when real gameplay is added */}
      <div className="game-overlay game-overlay--bottom">
        <Button
          variant="danger"
          onClick={() => showDeath('You were eaten by a Lion!')}
        >
          Test Death Screen
        </Button>
      </div>
    </div>
  );
}
