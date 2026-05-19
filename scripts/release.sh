#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_SCRIPT="$PROJECT_DIR/build.sh"
DIST_JAR="$PROJECT_DIR/dist/qfolder.jar"
REPO="damianlezcano/qfolder"
UPDATE_CHECKER="$PROJECT_DIR/p2p-client/src/main/java/org/q3s/p2p/client/UpdateChecker.java"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

die()  { echo -e "${RED}ERROR: $*${NC}" >&2; exit 1; }
info() { echo -e "${GREEN}=== $*${NC}"; }

# ── parse args ──────────────────────────────────────────────────
VERSION="${1:-}"
TITLE="${2:-}"
BODY="${3:-}"

if [ -z "$VERSION" ]; then
	echo "Usage: $0 <version> [title] [body]"
	echo ""
	echo "  version   e.g. v1.0.2"
	echo "  title     optional, defaults to version"
	echo "  body      optional, defaults to title"
	echo ""
	echo "Token is read from ~/.github/qfolder-token"
	exit 1
fi

TITLE="${TITLE:-$VERSION}"
BODY="${BODY:-$TITLE}"

# ── token ────────────────────────────────────────────────────────
GITHUB_TOKEN=""
for f in "$HOME/.github/qfolder-token" "$PROJECT_DIR/.github-token"; do
	[ -f "$f" ] && GITHUB_TOKEN="$(cat "$f" | tr -d '\n')" && break
done
[ -n "$GITHUB_TOKEN" ] || die "No token found. Save it in ~/.github/qfolder-token"

# ── build ────────────────────────────────────────────────────────
info "Building $VERSION"
cd "$PROJECT_DIR"
"$BUILD_SCRIPT"
[ -f "$DIST_JAR" ] || die "JAR not found: $DIST_JAR"

# ── version in source ────────────────────────────────────────────
APP_VERSION="${VERSION#v}"
if grep -q "private static final String VERSION" "$UPDATE_CHECKER"; then
	sed -i "s/\"[0-9]\+\.[0-9]\+\.[0-9]\+\"/\"$APP_VERSION\"/" "$UPDATE_CHECKER"
fi

# ── commit + tag ──────────────────────────────────────────────
info "Commit and tag"
cd "$PROJECT_DIR"
git -c user.name="qfolder" -c user.email="bot@qfolder.local" add -A
git -c user.name="qfolder" -c user.email="bot@qfolder.local" commit -m "$VERSION: $TITLE" || info "(nothing to commit)"

if git tag -l | grep -qxF "$VERSION"; then
	info "Tag $VERSION already exists, skipping"
else
	git -c user.name="qfolder" -c user.email="bot@qfolder.local" tag "$VERSION"
fi

# ── push ──────────────────────────────────────────────────────
info "Push to GitHub"
git fetch origin 2>/dev/null || true
git push "https://${GITHUB_TOKEN}@github.com/${REPO}.git" HEAD 2>&1 || info "(branch push skipped, continuing)"
git push "https://${GITHUB_TOKEN}@github.com/${REPO}.git" "$VERSION" 2>&1

# ── create GitHub release ────────────────────────────────────
info "Create GitHub Release"
RESPONSE=$(curl -s -X POST "https://api.github.com/repos/$REPO/releases" \
	-H "Authorization: token $GITHUB_TOKEN" \
	-H "Content-Type: application/json" \
	-d "$(printf '{"tag_name":"%s","name":"%s","body":"%s","draft":false,"prerelease":false}' \
		"$VERSION" "$TITLE" "$BODY")")

if echo "$RESPONSE" | grep -q '"id"'; then
	RELEASE_ID=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
	info "Release created (id=$RELEASE_ID)"
elif echo "$RESPONSE" | grep -q '"already_exists"'; then
	info "Release already exists, fetching ID..."
	RELEASE_ID=$(curl -s "https://api.github.com/repos/$REPO/releases/tags/$VERSION" \
		-H "Authorization: token $GITHUB_TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
else
	echo "$RESPONSE" | head -5
	die "Release creation failed"
fi

# ── upload JAR ───────────────────────────────────────────────
info "Upload qfolder.jar"
curl -s -X POST \
	"https://uploads.github.com/repos/$REPO/releases/$RELEASE_ID/assets?name=qfolder.jar" \
	-H "Authorization: token $GITHUB_TOKEN" \
	-H "Content-Type: application/octet-stream" \
	--data-binary @"$DIST_JAR" > /dev/null

echo -e "${GREEN}=== Done ===${NC}"
echo "https://github.com/$REPO/releases/tag/$VERSION"
