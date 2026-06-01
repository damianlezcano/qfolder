# qfolder — Release Guide

## One-time setup

Generate a GitHub token with `repo` scope at https://github.com/settings/tokens

```bash
echo "ghp_TU_TOKEN" > ~/.github/qfolder-token
chmod 600 ~/.github/qfolder-token
```

## Auto-update logic

The app checks for updates at startup via `api.github.com/repos/damianlezcano/qfolder/releases/latest`.

| Release assets | Type | Behavior |
|---|---|---|
| Only `qfolder.jar` | Minor update | Auto-downloads the JAR (~2MB) and replaces the current one |
| `qfolder.jar` + `*-x64.tar.gz` / `*-x64.zip` | Major update | Shows dialog that opens the browser to download the full package |

**Minor updates** (JAR-only): solo se sube `qfolder.jar` al release. El usuario lo descarga automáticamente.

**Major updates** (con paquete): se suben `qfolder.jar` + paquete (`*-x64.tar.gz` o `*-x64.zip`). El usuario es redirigido a GitHub para descargar el paquete completo (incluye JRE + cloudflared + cambios de plataforma).

---

## Linux — release automático

```bash
# Single command — does everything automatically:
./scripts/release.sh release v1.0.X "Short description of changes"
```

**What it does (all in foreground with progress):**

| Step | Action |
|------|--------|
| 1/7 | Build the project (`./build.sh`) |
| 2/7 | Commit changes on `develop`, push to GitHub |
| 3/7 | Create Pull Request `develop → master` |
| 4/7 | Auto-approve the PR |
| 5/7 | Auto-merge the PR into `master` |
| 6/7 | Tag `master` with the version, push tag |
| 7/7 | Build, package with `jpackage` (Linux app-image + tar.gz), upload all assets |

**Assets uploaded:**
- `qfolder.jar` — standalone JAR (always)
- `qfolder-linux-x64.tar.gz` — Linux app-image (JRE + cloudflared bundled) — triggers **major update**

### JAR-only release (minor update)

If you want a minor update that auto-updates on all platforms:

```bash
# 1. Build
./build.sh

# 2. Create release manually via GitHub web UI
#    - Tag: v1.0.X
#    - Upload ONLY dist/qfolder.jar
#    - Do NOT upload -x64 packages
```

This way the app will auto-download just the JAR without prompting the user to visit GitHub.

---

## Windows — release manual

`release.sh` is a bash script and does not run on Windows. Follow these steps:

### Prerequisites

- JDK 21+ with `JAVA_HOME` configured
- Maven (`mvn.cmd` in PATH or `MAVEN_HOME` configured)
- Git for Windows
- GitHub token with `repo` scope
- `cloudflared` already installed (optional, for packaging)

### Steps

```powershell
# 1. Build the JAR
cd p2p-client
mvn clean package -DskipTests

# 2. (Optional) Build full package with jpackage
#    This generates packages/windows/qfolder-windows-x64.zip
powershell -File scripts/package-windows.ps1 app-image
```

### Create GitHub release

```powershell
# 3. Create tag and push
git tag v1.0.X
git push origin v1.0.X

# 4. Create release via GitHub CLI or web UI
gh release create v1.0.X --title "v1.0.X" --notes "description"
```

**For minor update** (JAR-only): upload `p2p-client/target/p2p-client-1.0-SNAPSHOT.jar` (renamed to `qfolder.jar`) to the release. Do not upload any `*-x64.*` file.

**For major update**: upload `packages/windows/qfolder-windows-x64.zip` together with `qfolder.jar`. In this case, also create the Linux release from a Linux machine so both packages are available.

---

## Manual steps (if script fails on Linux)

```bash
# Build
./build.sh

# Commit and push develop
git checkout develop && git add -A && git commit -m "v1.0.X" && git push origin develop

# Create PR on GitHub website, review and merge

# Tag and release
git checkout master && git pull
git tag v1.0.X && git push origin v1.0.X

# Package
./scripts/package-linux.sh app-image

# Create release on GitHub website and attach:
# - dist/qfolder.jar
# - packages/linux/qfolder-linux-x64.tar.gz
```

## Dev testing (no cloudflared, no jpackage)

```bash
java -Dqfolder.tunnel.mock=true -jar dist/qfolder.jar
```
