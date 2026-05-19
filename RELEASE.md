# qfolder - Release Guide

## One-time setup

Generate a GitHub token with `repo` scope at https://github.com/settings/tokens

Save it:
```bash
echo "ghp_TU_TOKEN" > ~/.github/qfolder-token
chmod 600 ~/.github/qfolder-token
```

## Making a release

```bash
cd /home/tiul/dev/github/qfolder

# 1. Make your code changes

# 2. Update the version in the source file:
vim p2p-client/src/main/java/org/q3s/p2p/client/UpdateChecker.java
# Change: private static final String VERSION = "1.0.X";

# 3. Build and release (single command):
./scripts/release.sh v1.0.X "Release notes here"
```

What `release.sh` does automatically:
- Builds with `./build.sh`
- Updates version in UpdateChecker.java
- Git commits, tags, and pushes
- Creates the GitHub Release via API
- Uploads `qfolder.jar` as the release asset

After the release, users will be notified on next launch because
`UpdateChecker` queries `api.github.com/repos/damianlezcano/qfolder/releases/latest`
and compares with the local version.

## Manual steps (if release.sh fails)

```bash
# Build
./build.sh

# Commit and tag
git add -A
git commit -m "v1.0.X: description"
git tag v1.0.X
git push origin HEAD --tags

# Create release on GitHub website:
# https://github.com/damianlezcano/qfolder/releases/new
# Choose tag v1.0.X, write notes, attach dist/qfolder.jar
```

## Dev testing (no internet needed)

```bash
java -Dqfolder.tunnel.mock=true -jar dist/qfolder.jar
```

The update check runs silently in background. If there's no internet or GitHub is unreachable,
it logs `Update check skipped` and continues normally.
