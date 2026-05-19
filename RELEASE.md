# qfolder — Release Guide

## One-time setup

Generate a GitHub token with `repo` scope at https://github.com/settings/tokens

```bash
echo "ghp_TU_TOKEN" > ~/.github/qfolder-token
chmod 600 ~/.github/qfolder-token
```

## Release workflow

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
| 7/7 | Build, package with `jpackage` (Linux app-image), upload all assets |

**Assets uploaded to the GitHub Release:**
- `qfolder.jar` — standalone JAR
- `qfolder-linux-x64.tar.gz` — Linux app-image (JRE + cloudflared bundled)
- `qfolder-windows-x64.zip` — Windows app-image (if available)

## Auto-update

After releasing, existing users on older versions will see an update dialog on next launch. The app queries `api.github.com/repos/damianlezcano/qfolder/releases/latest` and compares the `tag_name` with the local version (`UpdateChecker.java`).

If a newer version is found, it offers to download and install it automatically.

## Manual steps (if script fails)

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
