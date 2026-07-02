// Ad-hoc probe: join, then force-kill via test-support, print all messages.
const ws = new WebSocket('ws://localhost:8080/ws/game');
const seen = [];
ws.addEventListener('open', () => {
  ws.send(JSON.stringify({ type: 'join', nickname: 'ProbeKill' }));
});
ws.addEventListener('message', async (e) => {
  const text = typeof e.data === 'string' ? e.data : await e.data.text();
  const msg = JSON.parse(text);
  seen.push(msg.type);
  if (msg.type === 'welcome') {
    // Give the loop a moment, then request the kill.
    setTimeout(async () => {
      const res = await fetch(
        'http://localhost:8080/test-support/kill?nickname=ProbeKill',
        { method: 'POST' },
      );
      console.log('kill status', res.status, await res.text());
    }, 500);
  }
  if (msg.type === 'death') {
    console.log('GOT DEATH:', JSON.stringify(msg));
    ws.close();
  }
});
setTimeout(() => {
  console.log('types seen:', [...new Set(seen)].join(','));
  console.log('death seen:', seen.includes('death'));
  process.exit(seen.includes('death') ? 0 : 1);
}, 6000);
