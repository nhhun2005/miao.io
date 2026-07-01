/**
 * Environment configuration.
 * Values are resolved at build time from Vite env variables
 * with sensible development defaults.
 */

/**
 * WebSocket URL used to connect to the game server.
 *
 * Defaults to a same-origin URL so that, in development, the request goes
 * through the Vite dev-server proxy (see `vite.config.ts` -> `server.proxy`)
 * instead of pointing the browser straight at the backend port. This avoids
 * cross-origin connection failures and keeps a single source of truth for
 * the backend address. Set `VITE_WS_URL` to override (e.g. for production).
 */
const wsProtocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
export const WS_URL: string =
  import.meta.env.VITE_WS_URL ?? `${wsProtocol}://${window.location.host}/ws/game`;

/** Maximum nickname length accepted by the client. */
export const MAX_NICKNAME_LENGTH = 16;

/** Minimum nickname length accepted by the client. */
export const MIN_NICKNAME_LENGTH = 1;
