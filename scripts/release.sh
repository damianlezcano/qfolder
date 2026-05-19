#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_JAR="$PROJECT_DIR/dist/qfolder.jar"
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
	echo ""
	echo "  prepare v1.0.3   Build, commit on develop, push, create PR → $BASE_BRANCH"
	echo "  publish v1.0.3   After PR is merged: tag $BASE_BRANCH, release, upload JAR"
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

# ══════════════════════════════════════════════════════════════════
#  PREPARE
# ══════════════════════════════════════════════════════════════════
cmd_prepare() {
	ok "Building $VERSION"
	cd "$PROJECT_DIR"
	bash "$PROJECT_DIR/build.sh"
	[ -f "$DIST_JAR" ] || die "$DIST_JAR not found"

	sed -i "s/VERSION = \"[^\"]*\"/VERSION = \"$APP_VERSION\"/" "$VERSION_FILE"
	ok "Version updated to $APP_VERSION in UpdateChecker.java"

	ok "Switching to $DEV_BRANCH"
	git -c user.name=release -c user.email=release@qfolder checkout -B "$DEV_BRANCH" 2>/dev/null || true
	git -c user.name=release -c user.email=release@qfolder add -A
	git -c user.name=release -c user.email=release@qfolder commit -m "$VERSION: $NOTES" || ok "(nothing to commit)"

	ok "Pushing $DEV_BRANCH"
	git -c user.name=release -c user.email=release@qfolder push "$GH" "$DEV_BRANCH" --force-with-lease 2>&1 | tail -1

	ok "Creating Pull Request: $DEV_BRANCH → $BASE_BRANCH"
	PR_URL=$(curl -s -X POST "$API/pulls" -H "$AUTH" -H "Content-Type: application/json" \
		-d "{\"title\":\"$VERSION: $NOTES\",\"head\":\"$DEV_BRANCH\",\"base\":\"$BASE_BRANCH\",\"body\":\"$NOTES\"}" \
		| python3 -c "import sys,json; print(json.load(sys.stdin).get('html_url','ERROR'))" 2>/dev/null)
	ok "PR created: $PR_URL"
	warn "Review and merge the PR, then run: $0 publish $VERSION"
}

# ══════════════════════════════════════════════════════════════════
#  PUBLISH  (run after PR is merged)
# ══════════════════════════════════════════════════════════════════
cmd_publish() {
	ok "Fetching latest $BASE_BRANCH"
	git -c user.name=release -c user.email=release@qfolder fetch origin "$BASE_BRANCH" 2>/dev/null
	git -c user.name=release -c user.email=release@qfolder checkout -B "$BASE_BRANCH" "origin/$BASE_BRANCH"

	ok "Tagging $VERSION"
	git -c user.name=release -c user.email=release@qfolder tag -f "$VERSION"
	git -c user.name=release -c user.email=release@qfolder push "$GH" "$VERSION" 2>&1 | tail -1

	ok "Creating GitHub Release"
	RELEASE_ID=$(curl -s -X POST "$API/releases" -H "$AUTH" -H "Content-Type: application/json" \
		-d "{\"tag_name\":\"$VERSION\",\"name\":\"$VERSION\",\"body\":\"$NOTES\",\"draft\":false,\"prerelease\":false}" \
		| python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
	ok "Release id=$RELEASE_ID"

	ok "Uploading qfolder.jar"
	curl -s -X POST "$API/releases/$RELEASE_ID/assets?name=qfolder.jar" \
		-H "$AUTH" -H "Content-Type: application/octet-stream" --data-binary @"$DIST_JAR" > /dev/null

	ok "Done: https://github.com/$REPO/releases/tag/$VERSION"

	ok "Back to $DEV_BRANCH"
	git -c user.name=release -c user.email=release@qfolder checkout "$DEV_BRANCH" 2>/dev/null || true
}

# ══════════════════════════════════════════════════════════════════
case "$CMD" in
	prepare) cmd_prepare ;;
	publish) cmd_publish ;;
	*) die "Unknown command: $CMD" ;;
esac
