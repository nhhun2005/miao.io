import { useUIStore } from "../state/uiStore";
import { ErrorBanner } from "../ui";
import { HomeScreen, LoadingScreen, GameScreen, DeathScreen } from "./screens";

/** Renders the active screen based on UI store state. */
function ActiveScreen() {
  const screen = useUIStore((s) => s.screen);

  switch (screen) {
    case "home":
      return <HomeScreen />;
    case "loading":
      return <LoadingScreen />;
    case "game":
      return <GameScreen />;
    case "death":
      return <DeathScreen />;
    default:
      return <HomeScreen />;
  }
}

export function App() {
  return (
    <div className="app-root">
      <ErrorBanner />
      <ActiveScreen />
    </div>
  );
}
