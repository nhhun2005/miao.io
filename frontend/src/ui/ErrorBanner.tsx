import { useUIStore } from "../state/uiStore";

/** A dismissible error banner shown at the top of the screen. */
export function ErrorBanner() {
  const error = useUIStore((s) => s.error);
  const setError = useUIStore((s) => s.setError);

  if (!error) return null;

  return (
    <div className="error-banner" role="alert">
      <span>{error}</span>
      <button
        type="button"
        className="error-banner__close"
        onClick={() => setError(null)}
        aria-label="Dismiss error"
      >
        ✕
      </button>
    </div>
  );
}
