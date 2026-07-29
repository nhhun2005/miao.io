const numberOption = (name, fallback) => {
  const value = Number(process.env[name] ?? fallback);
  if (!Number.isFinite(value) || value < 0) throw new Error(`Invalid ${name}`);
  return value;
};

const options = {
  url: process.env.MIMOPE_WS_URL ?? 'ws://localhost:8080/ws/game',
  clients: numberOption('MIMOPE_CLIENTS', 25),
  durationMs: numberOption('MIMOPE_DURATION_MS', 30_000),
  inputIntervalMs: numberOption('MIMOPE_INPUT_INTERVAL_MS', 50),
  joinTimeoutMs: numberOption('MIMOPE_JOIN_TIMEOUT_MS', 10_000),
  allowedErrors: numberOption('MIMOPE_ALLOWED_ERRORS', 0),
};

const startedAt = Date.now();
const states = Array.from({ length: options.clients }, (_, index) => ({
  index, socket: null, inputTimer: null, connected: false, welcomed: false,
  snapshots: 0, messages: 0, errors: 0, unexpectedCloses: 0,
}));

let stopping = false;
let joinTimer;
let finishTimer;

function cleanup() {
  clearTimeout(joinTimer);
  clearTimeout(finishTimer);
  for (const state of states) {
    clearInterval(state.inputTimer);
    if (state.socket?.readyState === WebSocket.OPEN
        || state.socket?.readyState === WebSocket.CONNECTING) {
      state.socket.close(1000, 'load test complete');
    }
  }
}

function finish(reason) {
  if (stopping) return;
  stopping = true;
  cleanup();
  const runtimeMs = Date.now() - startedAt;
  const summary = {
    ...options,
    reason,
    clientsRequested: options.clients,
    clientsConnected: states.filter((s) => s.connected).length,
    welcomeMessages: states.filter((s) => s.welcomed).length,
    totalSnapshots: states.reduce((n, s) => n + s.snapshots, 0),
    snapshotsByClient: Object.fromEntries(states.map((s) => [`Bot${s.index}`, s.snapshots])),
    clientsWithoutSnapshots: states.filter((s) => s.snapshots === 0).map((s) => `Bot${s.index}`),
    websocketErrors: states.reduce((n, s) => n + s.errors, 0),
    unexpectedCloses: states.reduce((n, s) => n + s.unexpectedCloses, 0),
    totalMessages: states.reduce((n, s) => n + s.messages, 0),
    runtimeMs,
    snapshotThroughputPerSecond:
      states.reduce((n, s) => n + s.snapshots, 0) / Math.max(runtimeMs / 1000, 0.001),
  };
  const failures = [];
  if (summary.clientsConnected !== options.clients) failures.push('not all clients connected');
  if (summary.welcomeMessages !== options.clients) failures.push('not all clients received welcome');
  if (summary.clientsWithoutSnapshots.length) failures.push('clients missed snapshots');
  if (summary.websocketErrors + summary.unexpectedCloses > options.allowedErrors) {
    failures.push('error threshold exceeded');
  }
  console.log(JSON.stringify({ ...summary, failures }, null, 2));
  setTimeout(() => process.exit(failures.length ? 1 : 0), 50);
}

process.on('SIGINT', () => finish('interrupted'));
process.on('uncaughtException', (error) => {
  console.error(error);
  finish('uncaught exception');
});

for (const state of states) {
  const ws = new WebSocket(options.url);
  state.socket = ws;
  ws.addEventListener('open', () => {
    state.connected = true;
    ws.send(JSON.stringify({ type: 'join', nickname: `Bot${state.index}` }));
    let seq = 0;
    state.inputTimer = setInterval(() => {
      if (ws.readyState !== WebSocket.OPEN) return;
      ws.send(JSON.stringify({
        type: 'input', seq: ++seq, angle: Math.random() * Math.PI * 2,
        intensity: 1, dash: Math.random() > 0.9, timestamp: Date.now(),
      }));
    }, options.inputIntervalMs);
  });
  ws.addEventListener('message', async (event) => {
    try {
      const text = typeof event.data === 'string' ? event.data : await event.data.text();
      const message = JSON.parse(text);
      state.messages++;
      if (message.type === 'welcome') state.welcomed = true;
      if (message.type === 'snapshot') state.snapshots++;
    } catch {
      state.errors++;
    }
  });
  ws.addEventListener('error', () => state.errors++);
  ws.addEventListener('close', (event) => {
    clearInterval(state.inputTimer);
    if (!stopping && event.code !== 1000) state.unexpectedCloses++;
  });
}

joinTimer = setTimeout(() => {
  if (states.some((s) => !s.connected || !s.welcomed)) finish('join timeout');
}, options.joinTimeoutMs);
finishTimer = setTimeout(() => finish('duration complete'), options.durationMs);
