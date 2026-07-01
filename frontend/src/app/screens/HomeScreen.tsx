import { useCallback } from "react";
import { useUIStore } from "../../state/uiStore";
import { Button, Panel } from "../../ui";
import { MAX_NICKNAME_LENGTH, MIN_NICKNAME_LENGTH } from "../../config/env";

export function HomeScreen() {
  const nickname = useUIStore((s) => s.nickname);
  const setNickname = useUIStore((s) => s.setNickname);
  const setScreen = useUIStore((s) => s.setScreen);
  const setError = useUIStore((s) => s.setError);

  const nicknameValid =
    nickname.trim().length >= MIN_NICKNAME_LENGTH &&
    nickname.trim().length <= MAX_NICKNAME_LENGTH;

  const handleStart = useCallback(() => {
    const trimmed = nickname.trim();
    if (trimmed.length < MIN_NICKNAME_LENGTH) {
      setError("Please enter a nickname.");
      return;
    }
    if (trimmed.length > MAX_NICKNAME_LENGTH) {
      setError(`Nickname must be at most ${MAX_NICKNAME_LENGTH} characters.`);
      return;
    }
    setError(null);
    setNickname(trimmed);
    // Transition to loading screen (will connect to server in later phases)
    setScreen("loading");
  }, [nickname, setError, setNickname, setScreen]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Enter") handleStart();
    },
    [handleStart],
  );

  return (
    <div className="screen screen--home">
      <Panel className="home-panel">
        <img
          src="/img/logo.png"
          alt="Mimope"
          className="home-panel__logo"
          onError={(e) => {
            // Hide broken image if logo asset is missing
            (e.target as HTMLImageElement).style.display = "none";
          }}
        />
        <h1 className="home-panel__title">Mimope</h1>
        <p className="home-panel__subtitle">
          A multiplayer animal evolution game
        </p>

        <label className="home-panel__label">
          Nickname
          <input
            className="home-panel__input"
            type="text"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Enter your name…"
            maxLength={MAX_NICKNAME_LENGTH}
            autoFocus
          />
        </label>

        <Button
          variant="primary"
          block
          disabled={!nicknameValid}
          onClick={handleStart}
        >
          Play
        </Button>

        <small className="home-panel__hint">
          Enter a nickname and click Play to start!
        </small>
      </Panel>
    </div>
  );
}
