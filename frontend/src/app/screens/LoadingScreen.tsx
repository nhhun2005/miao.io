import { useEffect, useRef } from 'react';
import { useUIStore } from '../../state/uiStore';
import { Panel } from '../../ui';
import { GameConnection } from '../../network/GameConnection';

/**
 * Loading screen — connects to the game server via WebSocket,
 * sends a join message, and transitions to the game screen
 * once the server sends a welcome message.
 */
export function LoadingScreen() {
  const setScreen = useUIStore((s) => s.setScreen);
  const setError = useUIStore((s) => s.setError);
  const nickname = useUIStore((s) => s.nickname);
  const starterAnimalId = useUIStore((s) => s.starterAnimalId);
  const connectionRef = useRef<GameConnection | null>(null);

  useEffect(() => {
    // Prevent double-init in StrictMode
    if (connectionRef.current) return;

    const conn = new GameConnection({
      onStateChange: (state) => {
        if (state === 'connected') {
          // WebSocket is open — send join message
          conn.join(nickname, starterAnimalId);
        }
        if (state === 'joined') {
          // Server accepted our join — store the connection globally and go to game
          // Store on window for GameScreen/PixiGame to pick up
          (window as unknown as Record<string, unknown>).__mimope_connection = conn;
          setScreen('game');
        }
        if (state === 'disconnected') {
          // Only treat a disconnect as a fatal connect failure while the
          // loading screen is still the active screen. Once we've moved on
          // to the game or death screen, socket closes (e.g. after a death)
          // are handled elsewhere and must not clobber those screens.
          if (connectionRef.current && useUIStore.getState().screen === 'loading') {
            setError('Failed to connect to game server.');
            setScreen('home');
          }
        }

      },
      onError: (message) => {
        setError(message);
        setScreen('home');
      },
    });

    connectionRef.current = conn;
    conn.connect();

    return () => {
      // Only clean up if we didn't successfully join (i.e., still on loading screen)
      const uiState = useUIStore.getState();
      if (uiState.screen !== 'game') {
        conn.destroy();
        connectionRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="screen screen--loading">
      <Panel className="loading-panel">
        <div className="spinner" aria-label="Loading" />
        <p className="loading-panel__text">Connecting to server…</p>
      </Panel>
    </div>
  );
}
