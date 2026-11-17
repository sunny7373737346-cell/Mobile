import asyncio
import json
import os
import websockets

PORT = int(os.environ.get("PORT", 8080))
rooms = {}  # room_code -> {"host": ws | None, "viewers": set()}


def get_room(code: str):
    if code not in rooms:
        rooms[code] = {"host": None, "viewers": set()}
    return rooms[code]


async def handler(websocket):
    current_room = None
    role = None
    try:
        async for message in websocket:
            if isinstance(message, bytes):
                if role == "host" and current_room in rooms:
                    viewers = list(rooms[current_room]["viewers"])
                    for v in viewers:
                        try:
                            await v.send(message)
                        except Exception:
                            pass
                continue

            try:
                msg = json.loads(message)
            except json.JSONDecodeError:
                continue

            if msg.get("type") == "join" and msg.get("room") and msg.get("role"):
                current_room = str(msg["room"]).strip()
                role = msg["role"]
                room = get_room(current_room)
                if role == "host":
                    room["host"] = websocket
                    await websocket.send(json.dumps({"type": "joined", "role": "host", "room": current_room}))
                    print(f"[Room {current_room}] Host connected")
                elif role == "viewer":
                    room["viewers"].add(websocket)
                    await websocket.send(json.dumps({"type": "joined", "role": "viewer", "room": current_room}))
                    print(f"[Room {current_room}] Viewer connected")
                continue

            if role == "viewer" and current_room in rooms:
                host = rooms[current_room].get("host")
                if host is not None:
                    try:
                        await host.send(json.dumps(msg))
                    except Exception:
                        pass
    finally:
        if current_room and current_room in rooms:
            room = rooms[current_room]
            if role == "host" and room["host"] == websocket:
                room["host"] = None
            elif role == "viewer":
                room["viewers"].discard(websocket)
            if room["host"] is None and not room["viewers"]:
                del rooms[current_room]


async def main():
    print(f"PhoneMirrorControl Python Relay Server listening on ws://0.0.0.0:{PORT}")
    async with websockets.serve(handler, "0.0.0.0", PORT, max_size=10 * 1024 * 1024):
        await asyncio.Future()


if __name__ == "__main__":
    asyncio.run(main())
