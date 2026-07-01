/**
 * GameCanvas — React component that mounts and manages the PixiJS game.
 *
 * Phase 9: Accepts a GameConnection and wires snapshot callbacks
 * into the PixiGame renderer.
 */

import { useEffect, useRef, useState } from 'react';
import { PixiGame } from './PixiGame';
import type { GameConnection } from '../network/GameConnection';

export interface GameCanvasProps {
  /** The active game connection (from LoadingScreen). */
  connection: GameConnection;
  /** Whether to show the spatial grid debug overlay. */
  showGridDebug?: boolean;
}

export function GameCanvas({ connection, showGridDebug = false }: GameCanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const gameRef = useRef<PixiGame | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    // Prevent double-init in React StrictMode
    if (gameRef.current) return;

    const game = new PixiGame({ container, connection, showGridDebug });
    gameRef.current = game;

    // Wire snapshot callback from GameConnection into PixiGame
    connection.setSnapshotCallback(game.onSnapshot);

    game
      .init()
      .then(() => {
        setLoading(false);
      })
      .catch((err) => {
        console.error('[GameCanvas] Failed to initialize PixiGame:', err);
        setError(err instanceof Error ? err.message : String(err));
        setLoading(false);
      });

    return () => {
      game.destroy();
      gameRef.current = null;
      // Remove our snapshot callback
      connection.setSnapshotCallback(undefined);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // Only run once on mount

  useEffect(() => {
    connection.sendGridDebugToggle(showGridDebug);
  }, [connection, showGridDebug]);

  return (
    <div className="game-canvas-wrapper">
      <div ref={containerRef} className="game-canvas-container" />

      {loading && (
        <div className="game-canvas-loading">
          <div className="spinner" />
          <p>Loading game assets…</p>
        </div>
      )}

      {error && (
        <div className="game-canvas-error">
          <p>⚠️ Failed to start game engine</p>
          <p className="game-canvas-error__detail">{error}</p>
        </div>
      )}
    </div>
  );
}
