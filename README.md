# qfolder

Peer-to-peer collaborative workspace — no accounts, no servers.

Share files, chat, whiteboard, and notes. Workspaces are distributed: if the hub goes down, another member takes over automatically.

## Quick start

```bash
# Build
./build.sh

# Launch
java -jar dist/qfolder.jar
```

### Development (3 local instances)

```bash
java -Dqfolder.user.name=devA -Dqfolder.shared.dir=/tmp/a -Dqfolder.ws.port=18765 -Dqfolder.tunnel.mock=true -jar dist/qfolder.jar &
java -Dqfolder.user.name=devB -Dqfolder.shared.dir=/tmp/b -Dqfolder.ws.port=18766 -Dqfolder.tunnel.mock=true -jar dist/qfolder.jar &
java -Dqfolder.user.name=devC -Dqfolder.shared.dir=/tmp/c -Dqfolder.ws.port=18767 -Dqfolder.tunnel.mock=true -jar dist/qfolder.jar &
```

## Launch options

| Property | Default | Description |
|---|---|---|
| `qfolder.user.name` | `$USER` | User name |
| `qfolder.shared.dir` | `~/qfolder/temporal` | Shared directory |
| `qfolder.history.dir` | `~/qfolder/history` | Session history |
| `qfolder.ws.port` | `18765` | WebSocket port |
| `qfolder.tunnel.mock` | `false` | Dev mode without cloudflared |

## Highlights

- Zero registration — no accounts, no servers
- Distributed — if the hub disconnects, another member takes over
- Ephemeral — sessions save locally for later review
- File sync between members with clickable links in chat
- Reply and pin messages
- Collaborative whiteboard (draw, text, images, shapes)
- Rich-text shared notes with images
- Auto-detects English / Spanish from system locale

## Build

Requires JDK 21+ and Maven.

```bash
# Core jar
./build.sh

# Linux package (app-image)
./scripts/package-linux.sh

# Windows package (app-image)
powershell .\scripts\package-windows.ps1
```

## Author

Damian Lezcano — [github.com/damianlezcano/qfolder](https://github.com/damianlezcano/qfolder)
