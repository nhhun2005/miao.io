import { useCallback } from "react";
import { useUIStore } from "../../state/uiStore";
import { Button, Panel } from "../../ui";
import { MAX_NICKNAME_LENGTH, MIN_NICKNAME_LENGTH } from "../../config/env";
import { ANIMALS, STARTER_ANIMAL_IDS } from "../../game/data/animals";

export function HomeScreen() {
  const nickname = useUIStore((s) => s.nickname);
  const starterAnimalId = useUIStore((s) => s.starterAnimalId);
  const setNickname = useUIStore((s) => s.setNickname);
  const setStarterAnimalId = useUIStore((s) => s.setStarterAnimalId);
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
      <div className="home-backdrop" aria-hidden="true">
        <span className="home-backdrop__orb home-backdrop__orb--one" />
        <span className="home-backdrop__orb home-backdrop__orb--two" />
        <span className="home-backdrop__leaf">✦</span>
      </div>
      <Panel className="home-panel">
        <div className="home-panel__brand">
          <span className="home-panel__eyebrow">Eat · Grow · Evolve</span>
          <h1 className="home-panel__title">
            Mimope<span>.io</span>
          </h1>
        </div>
        <p className="home-panel__subtitle">
          Enter a wild multiplayer world, find food and evolve to the top.
        </p>

        <label className="home-panel__label">
          <span>Choose your name</span>
          <div className="home-panel__input-wrap">
            <input
              className="home-panel__input"
              type="text"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="What should we call you?"
              maxLength={MAX_NICKNAME_LENGTH}
              autoFocus
            />
            <small>{nickname.length}/{MAX_NICKNAME_LENGTH}</small>
          </div>
        </label>

        <div className="home-panel__starter-section">
          <div className="home-panel__section-heading">
            <span>Pick a starter</span>
            <small>You can evolve later</small>
          </div>
          <div className="home-panel__starter-group" aria-label="Starter animal">
            {STARTER_ANIMAL_IDS.map((animalId) => {
              const animal = ANIMALS[animalId];
              const selected = starterAnimalId === animalId;
              return (
                <button
                  key={animalId}
                  type="button"
                  className={`home-panel__starter ${selected ? "home-panel__starter--active" : ""}`}
                  onClick={() => setStarterAnimalId(animalId)}
                  aria-pressed={selected}
                >
                  <span className="home-panel__starter-image">
                    <img src={`/${animal.skinPath}`} alt="" />
                  </span>
                  <span>{animal.name}</span>
                  <small>{animal.biome}</small>
                </button>
              );
            })}
          </div>
        </div>

        <Button
          variant="primary"
          block
          disabled={!nicknameValid}
          onClick={handleStart}
        >
          Enter the wild
        </Button>

        <small className="home-panel__hint">
          Move with your pointer · Dash with click, Space or W
        </small>
      </Panel>
    </div>
  );
}
