const WebSocket = require("ws");

const PORT = process.env.PORT || 8080;
const wss = new WebSocket.Server({ port: PORT });

// rooms: Map<roomCode, { host: WebSocket | null, viewers: Set<WebSocket> }>
const rooms = new Map();

function getRoom(code) {
  if (!rooms.has(code)) {
    rooms.set(code, { host: null, viewers: new Set() });
  }
  return rooms.get(code);
}

wss.on("connection", (ws) => {
  let currentRoom = null;
  let role = null;

  ws.on("message", (data, isBinary) => {
    if (isBinary) {
      // Binary video/image frame from host -> broadcast to all viewers in the room
      if (role === "host" && currentRoom) {
        const room = rooms.get(currentRoom);
        if (room) {
          for (const viewer of room.viewers) {
            if (viewer.readyState === WebSocket.OPEN) {
              viewer.send(data, { binary: true });
            }
          }
        }
      }
      return;
    }

    let msg;
    try {
      msg = JSON.parse(data.toString());
    } catch {
      return;
    }

    if (msg.type === "join" && msg.room && msg.role) {
      currentRoom = String(msg.room).trim();
      role = msg.role;
      const room = getRoom(currentRoom);

      if (role === "host") {
        room.host = ws;
        ws.send(JSON.stringify({ type: "joined", role: "host", room: currentRoom }));
        console.log(`[Room ${currentRoom}] Host connected`);
      } else if (role === "viewer") {
        room.viewers.add(ws);
        ws.send(JSON.stringify({ type: "joined", role: "viewer", room: currentRoom }));
        console.log(`[Room ${currentRoom}] Viewer connected (total: ${room.viewers.size})`);
      }
      return;
    }

    // Forward touch/navigation control commands from viewer -> host
    if (role === "viewer" && currentRoom) {
      const room = rooms.get(currentRoom);
      if (room && room.host && room.host.readyState === WebSocket.OPEN) {
        room.host.send(JSON.stringify(msg));
      }
    }
  });

  ws.on("close", () => {
    if (!currentRoom || !rooms.has(currentRoom)) return;
    const room = rooms.get(currentRoom);
    if (role === "host" && room.host === ws) {
      room.host = null;
      console.log(`[Room ${currentRoom}] Host disconnected`);
    } else if (role === "viewer") {
      room.viewers.delete(ws);
      console.log(`[Room ${currentRoom}] Viewer disconnected`);
    }
    if (!room.host && room.viewers.size === 0) {
      rooms.delete(currentRoom);
    }
  });
});

console.log(`PhoneMirrorControl Relay Server listening on ws://0.0.0.0:${PORT}`);
