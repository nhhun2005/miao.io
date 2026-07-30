import { useEffect, useRef, useState } from 'react';
import { useUIStore } from '../../state/uiStore';
import { useGameStore } from '../../state/gameStore';
import { GameCanvas } from '../../game/GameCanvas';
import { ANIMALS, getAnimalPreviewPath, getEvolutionOptions } from '../../game/data/animals';
import { Button, Modal } from '../../ui';
import type { GameConnection } from '../../network/GameConnection';

/**
 * Game screen — renders the PixiJS canvas and HUD overlays.
 * Phase 9: Receives the GameConnection from LoadingScreen via window global
 * and passes it to GameCanvas for server-driven rendering.
 */
export function GameScreen() {
  const nickname = useUIStore((s) => s.nickname);

  const localPlayerId = useGameStore((s) => s.localPlayerId);
  const players = useGameStore((s) => s.players);
  const evolutionOptions = useGameStore((s) => s.evolutionOptions);
  const clearEvolutionOptions = useGameStore((s) => s.clearEvolutionOptions);
  const leaderboard = useGameStore((s) => s.leaderboard);
  const evolutionRequestPending = useRef(false);

  const [connection] = useState<GameConnection | null>(() => {
    const win = window as unknown as Record<string, unknown>;
    return (win.__mimope_connection as GameConnection) ?? null;
  });

  useEffect(() => {
    return () => {
      // Only destroy the socket when the app actually leaves the game flow.
      // React dev StrictMode intentionally mounts/unmounts effects once to
      // detect unsafe side effects; destroying the connection during that
      // probe leaves the real GameCanvas mounted without live snapshots.
      if (connection && useUIStore.getState().screen !== 'game') {
        const win = window as unknown as Record<string, unknown>;
        connection.destroy();
        delete win.__mimope_connection;
      }
    };
  }, [connection]);

  const [latency, setLatency] = useState(0);

  useEffect(() => {
    if (!connection) return;
    const timer = window.setInterval(() => setLatency(connection.latency), 500);
    return () => window.clearInterval(timer);
  }, [connection]);

  useEffect(() => {
    if (evolutionOptions.length > 0) {
      evolutionRequestPending.current = false;
    }
  }, [evolutionOptions]);

  const localPlayer = localPlayerId ? players[localPlayerId] : null;
  const currentAnimal = localPlayer ? ANIMALS[localPlayer.animalId] : null;
  const nextEvolution = currentAnimal ? getEvolutionOptions(currentAnimal.id)[0] : null;
  const xpForCurrentTier = currentAnimal?.xpRequired ?? 0;
  const xpForNextTier = nextEvolution?.xpRequired ?? xpForCurrentTier;
  const xpProgress = nextEvolution && localPlayer
    ? Math.max(0, Math.min(1, (localPlayer.xp - xpForCurrentTier) / (xpForNextTier - xpForCurrentTier)))
    : 1;
  const healthProgress = localPlayer
    ? Math.max(0, Math.min(1, localPlayer.health / localPlayer.maxHealth))
    : 0;
  const dashReady = !localPlayer || localPlayer.dashCooldownTicks <= 0;

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
      <GameCanvas connection={connection} />

      {/* HUD overlay — player info */}
      <div className="game-overlay game-overlay--top-left">
        <div className="game-hud">
          <div className="game-hud__identity">
            <span className="game-hud__nickname">{nickname || 'Player'}</span>
            <span className="game-hud__animal">{currentAnimal?.name ?? 'Mouse'}</span>
          </div>
          <div className="game-meter">
            <span>HP</span>
            <div className="game-meter__track">
              <div className="game-meter__fill game-meter__fill--health" style={{ width: `${healthProgress * 100}%` }} />
            </div>
            <strong>{Math.round(localPlayer?.health ?? 0)}</strong>
          </div>
          <div className="game-meter">
            <span>XP</span>
            <div className="game-meter__track">
              <div className="game-meter__fill game-meter__fill--xp" style={{ width: `${xpProgress * 100}%` }} />
            </div>
            <strong>{Math.floor(localPlayer?.xp ?? 0)}</strong>
          </div>
          <div className="game-hud__ability">
            <span>Dash</span>
            <strong>{dashReady ? 'Ready' : `${localPlayer?.dashCooldownTicks ?? 0}t`}</strong>
          </div>
        </div>
      </div>

      <div className="game-overlay game-overlay--top-right">
        <div className="leaderboard-panel">
          <div className="leaderboard-panel__title">Leaderboard</div>
          {leaderboard.length === 0 && <div className="leaderboard-panel__empty">No scores yet</div>}
          {leaderboard.map((entry, index) => (
            <div className="leaderboard-panel__row" key={`${entry.nickname}-${index}`}>
              <span>{index + 1}. {entry.nickname}</span>
              <strong>{Math.floor(entry.xp)}</strong>
            </div>
          ))}
        </div>
      </div>

      <div className="game-overlay game-overlay--right">
        <div className="minimap">
          {Object.values(players).map((player) => (
            <span
              key={player.id}
              className={`minimap__dot ${player.id === localPlayerId ? 'minimap__dot--self' : ''}`}
              style={{
                left: `${(player.x / 5000) * 100}%`,
                top: `${(player.y / 5000) * 100}%`,
              }}
            />
          ))}
        </div>
      </div>

      <div className="game-overlay game-overlay--bottom-right">
        <div className="game-status-panel">
          <span>Ping {latency || '--'}ms</span>
          <span>{connection.connectionState}</span>
        </div>
      </div>

      <Modal open={evolutionOptions.length > 0}>
        <div className="evolution-modal">
          <h2 className="evolution-modal__title">Choose Evolution</h2>
          <p className="evolution-modal__meta">
            Current XP: {Math.floor(localPlayer?.xp ?? 0)}
          </p>
          <div className="evolution-modal__options">
            {evolutionOptions.map((option) => {
              const previewPath = getAnimalPreviewPath(option.animalId);
              return (
                <button
                  key={option.animalId}
                  className="evolution-card"
                  onClick={(event) => {
                    // A rapid double-click can otherwise enqueue the same
                    // evolution twice before React has removed the modal.
                    // The second request arrives after the first one changed
                    // the animal and is then (misleadingly) rejected.
                    if (evolutionRequestPending.current) return;
                    evolutionRequestPending.current = true;
                    event.currentTarget.disabled = true;
                    connection.sendEvolve(option.animalId);
                    clearEvolutionOptions();
                  }}
                >
                  {previewPath && (
                    <img
                      className="evolution-card__image"
                      src={`/${previewPath}`}
                      alt=""
                    />
                  )}
                  <span className="evolution-card__name">{option.name}</span>
                  <span className="evolution-card__tier">Tier {option.tier}</span>
                </button>
              );
            })}
          </div>
        </div>
      </Modal>
    </div>
  );
}
