# qfolder

Peer-to-peer collaborative workspace — no accounts, no central content server.

Share files, chat, whiteboard, and notes. Each member exposes a direct WebSocket endpoint and invitations point to a peer for bootstrap and reconnection.

## Quick start

```bash
# Build
./build.sh

# Launch
java -jar dist/qfolder.jar
```

### Development (3 local instances)

```bash
java -Dqfolder.user.name=devA -Dqfolder.shared.dir=/tmp/qfolder-dev/home-a/qfolder -Dqfolder.ws.port=18765 -Dqfolder.tunnel.mock=true -jar dist/qfolder.jar &
java -Dqfolder.user.name=devB -Dqfolder.shared.dir=/tmp/qfolder-dev/home-b/qfolder -Dqfolder.ws.port=18766 -Dqfolder.tunnel.mock=true -jar dist/qfolder.jar &
java -Dqfolder.user.name=devC -Dqfolder.shared.dir=/tmp/qfolder-dev/home-c/qfolder -Dqfolder.ws.port=18767 -Dqfolder.tunnel.mock=true -jar dist/qfolder.jar &
```

Prefer the reproducible local launcher. It copies `dist/qfolder.jar` to an immutable snapshot before launching instances, avoiding class-loading failures if the build output is overwritten while instances are running.

```bash
./build.sh
./scripts/dev-3-instances.sh
```

For two local instances using real Cloudflare tunnels:

```bash
./build.sh
./scripts/dev-2-realinstances.sh
```

See `docs/MANUAL_E2E_CORE.md` for the end-to-end validation checklist.

## Launch options

| Property | Default | Description |
|---|---|---|
| `qfolder.user.name` | `$USER` | User name |
| `qfolder.shared.dir` | `~/qfolder` | Local qfolder storage root |
| `qfolder.ws.port` | `18765` | WebSocket port |
| `qfolder.tunnel.mock` | `false` | Dev mode without cloudflared |

Local storage layout under `qfolder.shared.dir`:

```text
userdata/          — user data per workspace
  YYYY/MM/DD/HHmm-{id}-{slug}/
    files/         — shared/downloaded files
    chat/          — session chat history
    notes/         — exported notes (RTF/TXT)
    whiteboard/    — exported whiteboard images (PNG)
    logs/          — session logs
    members/       — member snapshots
systemdata/        — internal qfolder technical data
  identity.properties
  workspaces/{id}/
    events/        — core event store
    chunks/        — P2P file chunks
    snapshots/
    state/
    index-cache.properties
```

## Highlights

- Zero registration — no accounts and no central content server
- Direct P2P bootstrap — invitations connect to a peer endpoint
- Ephemeral — sessions save locally for later review
- Files are shared by dropping them on Archivos or attaching them in chat
- Files download through verified distributed chunks between peers
- Reply and pin messages
- Collaborative whiteboard (draw, text, images, shapes)
- Rich-text shared notes with images
- Auto-detects English / Spanish from system locale

## Build

Requires JDK 21+ and Maven.

```bash
# Core jar
./build.sh

# Core jar, running tests first
QFOLDER_RUN_TESTS=true ./build.sh

# Optional static analysis
cd p2p-client && mvn -Pstatic-analysis verify

# Linux package (app-image)
./scripts/package-linux.sh

# Windows package (app-image)
powershell .\scripts\package-windows.ps1
```

### Linux Packaging

Requires:

- Linux x64.
- JDK 21+ with `jpackage` available (`JAVA_HOME` recommended).
- Maven.
- `curl`, if `cloudflared` is not already installed locally.
- For installer formats instead of `app-image`, the native packaging tools required by `jpackage` such as `fakeroot`/`dpkg` for `deb` or `rpm` tooling for `rpm`.

Commands:

```bash
# Portable app-image + tar.gz in packages/linux/
./scripts/package-linux.sh

# Same as above, explicit
./scripts/package-linux.sh app-image

# Optional native packages, if your distro has the required tools
./scripts/package-linux.sh deb
./scripts/package-linux.sh rpm
```

The script runs `./build.sh`, bundles `qfolder.jar`, `qfolder.properties`, a runtime image produced by `jpackage`, and `cloudflared` under the app content. The default output is `packages/linux/qfolder-linux-x64.tar.gz`.

### Windows Packaging

Build the Windows package on Windows. `jpackage` generally cannot produce Windows installers from Linux.

Requires:

- Windows x64.
- JDK 21+ installed and `JAVA_HOME` pointing to it.
- Maven available as `mvn`, or `MAVEN_HOME` set.
- PowerShell.
- Internet access if `cloudflared.exe` is not already at `%LOCALAPPDATA%\qfolder\bin\cloudflared.exe`.
- For `exe`/`msi`, WiX Toolset may be required by `jpackage` depending on the JDK/distribution.

Commands from the repo root on Windows:

```powershell
# Portable app-image + zip in packages\windows\
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1

# Same as above, explicit
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1 -PackageType app-image

# Optional installer formats, if jpackage/WiX requirements are installed
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1 -PackageType exe
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1 -PackageType msi
```

The default output is `packages\windows\qfolder-windows-x64.zip`.

## Author

Damian Lezcano — [github.com/damianlezcano/qfolder](https://github.com/damianlezcano/qfolder)
