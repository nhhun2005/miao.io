import { useCallback } from 'react';
import { useUIStore } from '../../state/uiStore';
import { useGameStore } from '../../state/gameStore';
import { Button, Panel } from '../../ui';
import type { GameConnection } from '../../network/GameConnection';

/** Screen shown when the player dies. */
export function DeathScreen() {
  const deathMessage = useUIStore((s) => s.deathMessage);
  const setScreen = useUIStore((s) => s.setScreen);
  const resetGame = useGameStore((s) => s.reset);

  const handleRespawn = useCallback(() => {
    // Destroy the active connection if it still exists
    const win = window as unknown as Record<string, unknown>;
    const conn = win.__mimope_connection as GameConnection | undefined;
    if (conn) {
      conn.destroy();
      delete win.__mimope_connection;
    }

    resetGame();
    setScreen('home');
  }, [resetGame, setScreen]);

  return (
    <div className="screen screen--death">
      <Panel className="death-panel">
        <h2 className="death-panel__title">You Died!</h2>
        {deathMessage && (
          <p className="death-panel__message">{deathMessage}</p>
        )}
        <Button variant="primary" block onClick={handleRespawn}>
          Play Again
        </Button>
      </Panel>
    </div>
  );
}
