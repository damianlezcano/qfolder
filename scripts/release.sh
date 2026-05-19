#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_JAR="$PROJECT_DIR/dist/qfolder.jar"
PKG_LINUX_DIR="$PROJECT_DIR/packages/linux"
REPO="damianlezcano/qfolder"
BASE_BRANCH="master"
DEV_BRANCH="develop"
VERSION_FILE="$PROJECT_DIR/p2p-client/src/main/java/org/q3s/p2p/client/UpdateChecker.java"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
die()  { echo -e "${RED}ERROR: $*${NC}" >&2; exit 1; }
ok()   { echo -e "${GREEN}==> $*${NC}"; }
warn() { echo -e "${YELLOW}   $*${NC}"; }

CMD="${1:-}"; VERSION="${2:-}"
[ -n "$CMD" ] && [ -n "$VERSION" ] || { echo "Usage: $0 release <version> [notes]"; exit 1; }
NOTES="${3:-$VERSION}"
APP_VERSION="${VERSION#v}"

TOKEN="$(cat "$HOME/.github/qfolder-token" 2>/dev/null | tr -d ' \n\r')"
[ -n "$TOKEN" ] || die "Token not found: ~/.github/qfolder-token"

GH="https://${TOKEN}@github.com/${REPO}.git"
API="https://api.github.com/repos/$REPO"
CURL_AUTH=(-H "Authorization: token ${TOKEN}")

gh_api() {
	local m="$1" p="$2" b="${3:-}"
	if [ -n "$b" ]; then
		curl -sS -X "$m" "$API/$p" "${CURL_AUTH[@]}" -H "Content-Type: application/json" -d "$b"
	else
		curl -sS -X "$m" "$API/$p" "${CURL_AUTH[@]}"
	fi
}

upload_asset() {
	local rid="$1" file="$2" name="$3"
	if [ -f "$file" ]; then
		local sz=$(du -h "$file" | cut -f1)
		ok "Uploading $name ($sz)..."
		local code=$(curl --connect-timeout 30 --max-time 1800 -sS -w "%{http_code}" \
			-X POST "https://uploads.github.com/repos/$REPO/releases/$rid/assets?name=$name" \
			"${CURL_AUTH[@]}" -H "Content-Type: application/octet-stream" \
			--data-binary @"$file" -o /dev/null)
		if [ "$code" = "201" ] || [ "$code" = "422" ]; then
			ok "$name OK"
		elif [ "$code" = "000" ]; then
			warn "$name TIMEOUT — upload manually at https://github.com/$REPO/releases/tag/$VERSION"
		else
			warn "$name FAILED (HTTP $code)"
		fi
	fi
}

cmd_release() {
	ok "1/7  Building $VERSION"
	cd "$PROJECT_DIR"
	bash "$PROJECT_DIR/build.sh"
	sed -i "s/VERSION = \"[^\"]*\"/VERSION = \"$APP_VERSION\"/" "$VERSION_FILE"

	ok "2/7  Pushing $DEV_BRANCH"
	git -c user.name=release -c user.email=release@qfolder checkout -B "$DEV_BRANCH" 2>/dev/null || true
	git -c user.name=release -c user.email=release@qfolder add -A
	git -c user.name=release -c user.email=release@qfolder commit -m "$VERSION: $NOTES" || ok "(nothing to commit)"
	git -c user.name=release -c user.email=release@qfolder push "$GH" "$DEV_BRANCH" 2>&1 | tail -1
	git -c user.name=release -c user.email=release@qfolder branch --set-upstream-to="origin/$DEV_BRANCH" "$DEV_BRANCH" 2>/dev/null || true

	ok "3/7  Creating Pull Request"
	local pr=$(gh_api POST "pulls" "{\"title\":\"$VERSION: $NOTES\",\"head\":\"$DEV_BRANCH\",\"base\":\"$BASE_BRANCH\",\"body\":\"$NOTES\"}")
	local pr_num=$(echo "$pr" | python3 -c "import sys,json; print(json.load(sys.stdin)['number'])")
	local pr_url=$(echo "$pr" | python3 -c "import sys,json; print(json.load(sys.stdin)['html_url'])")
	ok "PR #$pr_num  $pr_url"

	ok "4/7  Approving PR #$pr_num"
	gh_api POST "pulls/$pr_num/reviews" '{"event":"APPROVE"}' > /dev/null
	sleep 1

	ok "5/7  Merging PR #$pr_num"
	gh_api PUT "pulls/$pr_num/merge" '{"merge_method":"merge"}' > /dev/null
	ok "PR #$pr_num merged into $BASE_BRANCH"
	sleep 1

	ok "6/7  Tagging $VERSION"
	git -c user.name=release -c user.email=release@qfolder fetch origin "$BASE_BRANCH"
	git -c user.name=release -c user.email=release@qfolder checkout -B "$BASE_BRANCH" "origin/$BASE_BRANCH"
	git -c user.name=release -c user.email=release@qfolder tag -f "$VERSION"
	git -c user.name=release -c user.email=release@qfolder push "$GH" "$VERSION" 2>&1 | tail -1

	ok "7/7  Packaging and uploading"
	bash "$PROJECT_DIR/build.sh"
	if command -v jpackage &>/dev/null || [ -x "${JAVA_HOME:-}/bin/jpackage" ]; then
		ok "Running jpackage (Linux app-image)"
		bash "$SCRIPT_DIR/package-linux.sh" app-image || warn "jpackage skipped"
	fi

	local rel=$(gh_api POST "releases" "{\"tag_name\":\"$VERSION\",\"name\":\"$VERSION\",\"body\":\"$NOTES\",\"draft\":false,\"prerelease\":false}")
	local rid=$(echo "$rel" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

	upload_asset "$rid" "$DIST_JAR" "qfolder.jar"
	upload_asset "$rid" "$PKG_LINUX_DIR/qfolder-linux-x64.tar.gz" "qfolder-linux-x64.tar.gz"

	ok "Back to $DEV_BRANCH"
	git -c user.name=release -c user.email=release@qfolder checkout "$DEV_BRANCH"

	ok "Done: https://github.com/$REPO/releases/tag/$VERSION"
}

case "$CMD" in
	release) cmd_release ;;
	*) die "Unknown: $CMD" ;;
esac
