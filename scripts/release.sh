#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_JAR="$PROJECT_DIR/dist/qfolder.jar"
PKG_LINUX_DIR="$PROJECT_DIR/packages/linux"
PKG_WIN_DIR="$PROJECT_DIR/packages/windows"
REPO="damianlezcano/qfolder"
BASE_BRANCH="master"
DEV_BRANCH="develop"
VERSION_FILE="$PROJECT_DIR/p2p-client/src/main/java/org/q3s/p2p/client/UpdateChecker.java"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
die()  { echo -e "${RED}ERROR: $*${NC}" >&2; exit 1; }
ok()   { echo -e "${GREEN}==> $*${NC}"; }
warn() { echo -e "${YELLOW}$*${NC}"; }

# ── args ─────────────────────────────────────────────────────────
CMD="${1:-}"; VERSION="${2:-}"
[ -n "$CMD" ] && [ -n "$VERSION" ] || {
	echo "Usage: $0 prepare|publish <version> [notes]"
	exit 1
}
NOTES="${3:-$VERSION}"
APP_VERSION="${VERSION#v}"

# ── token ────────────────────────────────────────────────────────
TOKEN="$(cat "$HOME/.github/qfolder-token" 2>/dev/null | tr -d '\n')"
[ -n "$TOKEN" ] || die "Token not found: ~/.github/qfolder-token"
AUTH="Authorization: token $TOKEN"
GH="https://${TOKEN}@github.com/${REPO}.git"
API="https://api.github.com/repos/$REPO"

# ── helpers ──────────────────────────────────────────────────────
upload_asset() {
	local release_id="$1" file="$2" name="$3"
	if [ -f "$file" ]; then
		ok "Uploading $name ($(du -h "$file" | cut -f1))"
		curl -s -X POST "$API/releases/$release_id/assets?name=$name" \
			-H "$AUTH" -H "Content-Type: application/octet-stream" \
			--data-binary @"$file" > /dev/null
	fi
}

# ══════════════════════════════════════════════════════════════════
cmd_prepare() {
	ok "Building $VERSION"
	cd "$PROJECT_DIR"
	bash "$PROJECT_DIR/build.sh"

	sed -i "s/VERSION = \"[^\"]*\"/VERSION = \"$APP_VERSION\"/" "$VERSION_FILE"
	ok "Version set to $APP_VERSION"

	ok "Switching to $DEV_BRANCH"
	git -c user.name=release -c user.email=release@qfolder checkout -B "$DEV_BRANCH" 2>/dev/null || true
	git -c user.name=release -c user.email=release@qfolder add -A
	git -c user.name=release -c user.email=release@qfolder commit -m "$VERSION: $NOTES" || ok "(nothing to commit)"

	ok "Pushing $DEV_BRANCH"
	git -c user.name=release -c user.email=release@qfolder push "$GH" "$DEV_BRANCH" 2>&1 | tail -1

	ok "Creating Pull Request: $DEV_BRANCH → $BASE_BRANCH"
	PR_URL=$(curl -s -X POST "$API/pulls" -H "$AUTH" -H "Content-Type: application/json" \
		-d "{\"title\":\"$VERSION: $NOTES\",\"head\":\"$DEV_BRANCH\",\"base\":\"$BASE_BRANCH\",\"body\":\"$NOTES\"}" \
		| python3 -c "import sys,json; print(json.load(sys.stdin).get('html_url','ERROR'))" 2>/dev/null)
	ok "PR: $PR_URL"
	warn "→ Merge the PR, then run: $0 publish $VERSION"
}

# ══════════════════════════════════════════════════════════════════
cmd_publish() {
	ok "Fetching $BASE_BRANCH"
	git -c user.name=release -c user.email=release@qfolder fetch origin "$BASE_BRANCH"
	git -c user.name=release -c user.email=release@qfolder checkout -B "$BASE_BRANCH" "origin/$BASE_BRANCH"

	# ── build jar ─────────────────────────────────────────────
	ok "Building JAR"
	cd "$PROJECT_DIR"
	bash "$PROJECT_DIR/build.sh"
	[ -f "$DIST_JAR" ] || die "$DIST_JAR not found"

	# ── package ───────────────────────────────────────────────
	if command -v jpackage &>/dev/null || [ -x "${JAVA_HOME:-}/bin/jpackage" ]; then
		ok "Packaging Linux (jpackage app-image)"
		bash "$SCRIPT_DIR/package-linux.sh" app-image || warn "Linux package skipped (cloudflared missing?)"
	else
		warn "jpackage not available, skipping platform packages"
	fi

	# ── tag ───────────────────────────────────────────────────
	ok "Tagging $VERSION on $BASE_BRANCH"
	git -c user.name=release -c user.email=release@qfolder tag -f "$VERSION"
	git -c user.name=release -c user.email=release@qfolder push "$GH" "$VERSION" 2>&1 | tail -1

	# ── release ───────────────────────────────────────────────
	ok "Creating GitHub Release"
	RELEASE_ID=$(curl -s -X POST "$API/releases" -H "$AUTH" -H "Content-Type: application/json" \
		-d "{\"tag_name\":\"$VERSION\",\"name\":\"$VERSION\",\"body\":\"$NOTES\",\"draft\":false,\"prerelease\":false}" \
		| python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
	ok "Release id=$RELEASE_ID"

	# ── upload JAR ────────────────────────────────────────────
	upload_asset "$RELEASE_ID" "$DIST_JAR" "qfolder.jar"

	# ── upload packages ───────────────────────────────────────
	upload_asset "$RELEASE_ID" "$PKG_LINUX_DIR/qfolder-linux-x64.tar.gz" "qfolder-linux-x64.tar.gz"
	upload_asset "$RELEASE_ID" "$PKG_WIN_DIR/qfolder-windows-x64.zip"   "qfolder-windows-x64.zip"

	# ── done ──────────────────────────────────────────────────
	ok "Done: https://github.com/$REPO/releases/tag/$VERSION"
	warn "Back on $BASE_BRANCH. Switch to develop to continue work:"
	warn "  git checkout $DEV_BRANCH"
}

case "$CMD" in
	prepare) cmd_prepare ;;
	publish) cmd_publish ;;
	*) die "Unknown: $CMD. Use prepare|publish" ;;
esac
