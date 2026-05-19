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
	echo "Usage: $0 release <version> [notes]"
	echo "  release v1.0.3    Full pipeline: build, PR, merge, package, upload"
	exit 1
}
NOTES="${3:-$VERSION}"
APP_VERSION="${VERSION#v}"

# ── token ────────────────────────────────────────────────────────
TOKEN="$(cat "$HOME/.github/qfolder-token" 2>/dev/null | tr -d '\n')"
[ -n "$TOKEN" ] || die "Token not found: ~/.github/qfolder-token"
AUTH="-H Authorization: token $TOKEN"
GH="https://${TOKEN}@github.com/${REPO}.git"
API="https://api.github.com/repos/$REPO"

# ── helpers ──────────────────────────────────────────────────────
gh_api() {
	# $1 = METHOD (GET/POST/PUT), $2 = path, $3 = optional body
	local method="$1" path="$2" body="${3:-}"
	if [ -n "$body" ]; then
		curl -sS -X "$method" "$API/$path" $AUTH -H "Content-Type: application/json" -d "$body"
	else
		curl -sS -X "$method" "$API/$path" $AUTH
	fi
}

upload_asset() {
	local release_id="$1" file="$2" name="$3"
	if [ -f "$file" ]; then
		local size=$(du -h "$file" | cut -f1)
		ok "Uploading $name ($size)"
		curl -# -X POST "$API/releases/$release_id/assets?name=$name" \
			$AUTH -H "Content-Type: application/octet-stream" \
			--data-binary @"$file" -o /dev/null
		ok "$name uploaded"
	fi
}

# ══════════════════════════════════════════════════════════════════
cmd_release() {
	# ── build ─────────────────────────────────────────────────
	ok "1/7  Building $VERSION"
	cd "$PROJECT_DIR"
	bash "$PROJECT_DIR/build.sh"

	sed -i "s/VERSION = \"[^\"]*\"/VERSION = \"$APP_VERSION\"/" "$VERSION_FILE"

	# ── commit & push develop ─────────────────────────────────
	ok "2/7  Pushing $DEV_BRANCH"
	git -c user.name=release -c user.email=release@qfolder checkout -B "$DEV_BRANCH" 2>/dev/null || true
	git -c user.name=release -c user.email=release@qfolder add -A
	git -c user.name=release -c user.email=release@qfolder commit -m "$VERSION: $NOTES" || ok "(nothing to commit)"
	git -c user.name=release -c user.email=release@qfolder push "$GH" "$DEV_BRANCH" 2>&1 | tail -1

	# ── create PR ─────────────────────────────────────────────
	ok "3/7  Creating Pull Request"
	local pr_json=$(gh_api POST "pulls" \
		"{\"title\":\"$VERSION: $NOTES\",\"head\":\"$DEV_BRANCH\",\"base\":\"$BASE_BRANCH\",\"body\":\"$NOTES\"}")
	local pr_num=$(echo "$pr_json" | python3 -c "import sys,json; print(json.load(sys.stdin)['number'])")
	local pr_url=$(echo "$pr_json" | python3 -c "import sys,json; print(json.load(sys.stdin)['html_url'])")
	ok "PR #$pr_num: $pr_url"

	# ── approve PR ────────────────────────────────────────────
	ok "4/7  Approving PR #$pr_num"
	gh_api POST "pulls/$pr_num/reviews" '{"event":"APPROVE"}' > /dev/null

	# ── merge PR ──────────────────────────────────────────────
	ok "5/7  Merging PR #$pr_num"
	sleep 2  # let GitHub process the review
	gh_api PUT "pulls/$pr_num/merge" '{"merge_method":"merge"}' > /dev/null
	ok "PR #$pr_num merged into $BASE_BRANCH"

	# ── tag on master ─────────────────────────────────────────
	ok "6/7  Tagging $VERSION"
	git -c user.name=release -c user.email=release@qfolder fetch origin "$BASE_BRANCH"
	git -c user.name=release -c user.email=release@qfolder checkout -B "$BASE_BRANCH" "origin/$BASE_BRANCH"
	git -c user.name=release -c user.email=release@qfolder tag -f "$VERSION"
	git -c user.name=release -c user.email=release@qfolder push "$GH" "$VERSION" 2>&1 | tail -1

	# ── package ───────────────────────────────────────────────
	ok "7/7  Packaging and uploading"
	bash "$PROJECT_DIR/build.sh"

	if command -v jpackage &>/dev/null || [ -x "${JAVA_HOME:-}/bin/jpackage" ]; then
		ok "  Running jpackage (Linux app-image)"
		bash "$SCRIPT_DIR/package-linux.sh" app-image || warn "  Linux package skipped (cloudflared missing?)"
	else
		warn "  jpackage not available, uploading JAR only"
	fi

	# ── GitHub release ────────────────────────────────────────
	local release_json=$(gh_api POST "releases" \
		"{\"tag_name\":\"$VERSION\",\"name\":\"$VERSION\",\"body\":\"$NOTES\",\"draft\":false,\"prerelease\":false}")
	local release_id=$(echo "$release_json" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

	upload_asset "$release_id" "$DIST_JAR" "qfolder.jar"
	upload_asset "$release_id" "$PKG_LINUX_DIR/qfolder-linux-x64.tar.gz" "qfolder-linux-x64.tar.gz"
	upload_asset "$release_id" "$PKG_WIN_DIR/qfolder-windows-x64.zip"   "qfolder-windows-x64.zip"

	# ── back to develop ───────────────────────────────────────
	ok "Switching back to $DEV_BRANCH"
	git -c user.name=release -c user.email=release@qfolder checkout "$DEV_BRANCH"

	ok "Done: https://github.com/$REPO/releases/tag/$VERSION"
}

case "$CMD" in
	release) cmd_release ;;
	*) die "Unknown: $CMD. Use: $0 release v1.0.X [notes]" ;;
esac
