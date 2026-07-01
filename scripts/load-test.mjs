const url = process.env.MIMOPE_WS_URL ?? 'ws://localhost:8080/ws/game';
const clients = Number(process.env.MIMOPE_CLIENTS ?? 25);
const durationMs = Number(process.env.MIMOPE_DURATION_MS ?? 30000);

let connected = 0;
let messages = 0;
let welcomes = 0;
let snapshots = 0;
let errors = 0;
const sockets = [];

for (let i = 0; i < clients; i++) {
  const ws = new WebSocket(url);
  sockets.push(ws);

  ws.addEventListener('open', () => {
    connected++;
    ws.send(JSON.stringify({ type: 'join', nickname: `Bot${i}` }));
    const timer = setInterval(() => {
      if (ws.readyState !== WebSocket.OPEN) return;
      ws.send(JSON.stringify({
        type: 'input',
        seq: Date.now(),
        angle: Math.random() * Math.PI * 2,
        intensity: 1,
        boost: Math.random() > 0.8,
        ability: Math.random() > 0.95,
        timestamp: Date.now(),
      }));
    }, 50);
    ws.addEventListener('close', () => clearInterval(timer));
  });

  ws.addEventListener('message', async (event) => {
    try {
      let text;
      if (typeof event.data === 'string') {
        text = event.data;
      } else if (event.data instanceof Blob) {
        text = await event.data.text();
      } else if (event.data instanceof ArrayBuffer) {
        text = new TextDecoder().decode(event.data);
      } else {
        text = String(event.data);
      }
      const msg = JSON.parse(text);
      messages++;
      if (msg.type === 'welcome') welcomes++;
      if (msg.type === 'snapshot') snapshots++;
    } catch {
      errors++;
    }
  });

  ws.addEventListener('error', () => {
    errors++;
  });
}

setTimeout(() => {
  for (const socket of sockets) {
    socket.close();
  }
  console.log(JSON.stringify({ url, clients, connected, messages, welcomes, snapshots, errors }, null, 2));
  process.exit(errors > clients || connected !== clients || snapshots === 0 ? 1 : 0);
}, durationMs);
