import { useEffect, useState } from 'react';
import { useUIStore } from '../../state/uiStore';
import { useInputStore } from '../../state/inputStore';
import { useGameStore } from '../../state/gameStore';
import { GameCanvas } from '../../game/GameCanvas';
import { ANIMALS, getEvolutionOptions } from '../../game/data/animals';
import { Button, Modal } from '../../ui';
import type { GameConnection } from '../../network/GameConnection';

/**
 * Game screen — renders the PixiJS canvas and HUD overlays.
 * Phase 9: Receives the GameConnection from LoadingScreen via window global
 * and passes it to GameCanvas for server-driven rendering.
 */
export function GameScreen() {
  const nickname = useUIStore((s) => s.nickname);

  // Input debug state from InputManager → inputStore
  const angle = useInputStore((s) => s.angle);
  const intensity = useInputStore((s) => s.intensity);
  const boost = useInputStore((s) => s.boost);
  const ability = useInputStore((s) => s.ability);
  const seq = useInputStore((s) => s.seq);
  const focused = useInputStore((s) => s.focused);
  const localPlayerId = useGameStore((s) => s.localPlayerId);
  const players = useGameStore((s) => s.players);
  const evolutionOptions = useGameStore((s) => s.evolutionOptions);
  const clearEvolutionOptions = useGameStore((s) => s.clearEvolutionOptions);
  const leaderboard = useGameStore((s) => s.leaderboard);

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

  const [showGridDebug, setShowGridDebug] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [latency, setLatency] = useState(0);

  useEffect(() => {
    if (!connection) return;
    const timer = window.setInterval(() => setLatency(connection.latency), 500);
    return () => window.clearInterval(timer);
  }, [connection]);

  const angleDeg = ((angle * 180) / Math.PI).toFixed(0);
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
  const abilityReady = !localPlayer || localPlayer.abilityCooldownTicks <= 0;

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
            <span>{currentAnimal?.abilityName ?? 'Ability'}</span>
            <strong>{abilityReady ? 'Ready' : `${localPlayer?.abilityCooldownTicks ?? 0}t`}</strong>
          </div>
        </div>
      </div>

      {/* Input debug overlay */}
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
          <button type="button" onClick={() => setSettingsOpen((v) => !v)}>Settings</button>
        </div>
        {settingsOpen && (
          <div className="settings-panel">
            <label className="settings-panel__toggle">
              <input
                type="checkbox"
                checked={soundEnabled}
                onChange={(e) => setSoundEnabled(e.target.checked)}
              />
              Sound
            </label>
            <div className="settings-panel__section">
              <div className="settings-panel__title">Controls</div>
              <div className="settings-panel__row">
                <span>Move</span>
                <strong>Mouse pointer</strong>
              </div>
              <div className="settings-panel__row">
                <span>Boost</span>
                <strong>Left click / Space</strong>
              </div>
              <div className="settings-panel__row">
                <span>Dash</span>
                <strong>Right click / W / Enter</strong>
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="game-overlay game-overlay--debug">
        <details className="game-debug-panel">
          <summary className="game-debug-panel__title">Input Debug</summary>
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
        </details>
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

      <Modal open={evolutionOptions.length > 0}>
        <div className="evolution-modal">
          <h2 className="evolution-modal__title">Choose Evolution</h2>
          <p className="evolution-modal__meta">
            Current XP: {Math.floor(localPlayer?.xp ?? 0)}
          </p>
          <div className="evolution-modal__options">
            {evolutionOptions.map((option) => {
              const animal = ANIMALS[option.animalId];
              return (
                <button
                  key={option.animalId}
                  className="evolution-card"
                  onClick={() => {
                    connection.sendEvolve(option.animalId);
                    clearEvolutionOptions();
                  }}
                >
                  {animal?.fullSizePath && (
                    <img
                      className="evolution-card__image"
                      src={`/${animal.fullSizePath}`}
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
