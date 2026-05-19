#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_JAR="$PROJECT_DIR/dist/qfolder.jar"
REPO="damianlezcano/qfolder"

RED='\033[0;31m'; GREEN='\033[0;32m'; NC='\033[0m'
die()  { echo -e "${RED}ERROR: $*${NC}" >&2; exit 1; }
ok()   { echo -e "${GREEN}==> $*${NC}"; }

# ── args ─────────────────────────────────────────────────────────
VERSION="${1:-}"; [ -n "$VERSION" ] || { echo "Usage: $0 v1.0.X [notes]"; exit 1; }
NOTES="${2:-Release $VERSION}"
APP_VERSION="${VERSION#v}"

# ── token ────────────────────────────────────────────────────────
TOKEN="$(cat "$HOME/.github/qfolder-token" 2>/dev/null | tr -d '\n')"
[ -n "$TOKEN" ] || die "Token not found: ~/.github/qfolder-token"
AUTH="Authorization: token $TOKEN"
GH="https://${TOKEN}@github.com/${REPO}.git"
API="https://api.github.com/repos/$REPO"

# ── build ────────────────────────────────────────────────────────
ok "Building $VERSION"
cd "$PROJECT_DIR"
bash "$PROJECT_DIR/build.sh"
[ -f "$DIST_JAR" ] || die "$DIST_JAR not found"

# ── version in source ────────────────────────────────────────────
UPDATE_FILE="$PROJECT_DIR/p2p-client/src/main/java/org/q3s/p2p/client/UpdateChecker.java"
sed -i "s/VERSION = \"[^\"]*\"/VERSION = \"$APP_VERSION\"/" "$UPDATE_FILE"
ok "Version set to $APP_VERSION"

# ── commit + tag + push ──────────────────────────────────────────
ok "Commit and push"
cd "$PROJECT_DIR"
git -c user.name=release -c user.email=release@qfolder add -A
git -c user.name=release -c user.email=release@qfolder commit -m "$VERSION" || ok "(nothing to commit)"
git -c user.name=release -c user.email=release@qfolder tag -f "$VERSION"
git push "$GH" HEAD       2>&1 | tail -1
git push "$GH" "$VERSION"  2>&1 | tail -1

# ── create GitHub release ────────────────────────────────────
ok "Create release"
RELEASE_ID=$(curl -s -X POST "$API/releases" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"tag_name\":\"$VERSION\",\"name\":\"$VERSION\",\"body\":\"$NOTES\",\"draft\":false,\"prerelease\":false}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
ok "Release id=$RELEASE_ID"

# ── upload jar ───────────────────────────────────────────────
ok "Upload qfolder.jar"
curl -s -X POST "$API/releases/$RELEASE_ID/assets?name=qfolder.jar" \
  -H "$AUTH" -H "Content-Type: application/octet-stream" --data-binary @"$DIST_JAR" > /dev/null

ok "Done: https://github.com/$REPO/releases/tag/$VERSION"
