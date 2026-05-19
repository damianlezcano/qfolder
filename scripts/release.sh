#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_SCRIPT="$PROJECT_DIR/build.sh"
DIST_JAR="$PROJECT_DIR/dist/qfolder.jar"
REPO="damianlezcano/qfolder"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

die() { echo -e "${RED}ERROR: $*${NC}" >&2; exit 1; }
info() { echo -e "${GREEN}$*${NC}"; }
warn() { echo -e "${YELLOW}$*${NC}"; }

# --- parse args ---
VERSION="${1:-}"
if [ -z "$VERSION" ]; then
	echo "Usage: $0 <version> [release-notes]"
	echo "Example: $0 v1.0.1"
	echo "         $0 v1.0.1 'Fixed chat reconnect bug'"
	exit 1
fi
NOTES="${2:-Release $VERSION}"
TAG="$VERSION"
# strip leading v for version comparison
APP_VERSION="${VERSION#v}"

# --- github token ---
GITHUB_TOKEN="${GITHUB_TOKEN:-}"
if [ -z "$GITHUB_TOKEN" ]; then
	if [ -f "$HOME/.github/qfolder-token" ]; then
		GITHUB_TOKEN="$(cat "$HOME/.github/qfolder-token")"
	elif [ -f "$PROJECT_DIR/.github-token" ]; then
		GITHUB_TOKEN="$(cat "$PROJECT_DIR/.github-token")"
	fi
fi
if [ -z "$GITHUB_TOKEN" ]; then
	warn "GITHUB_TOKEN not set. Set it or save it in ~/.github/qfolder-token"
	warn "For local testing, export GITHUB_TOKEN=ghp_..."
	exit 1
fi

# --- build ---
info "=== Building qfolder $VERSION ==="
cd "$PROJECT_DIR"
"$BUILD_SCRIPT"

if [ ! -f "$DIST_JAR" ]; then
	die "Build failed: $DIST_JAR not found"
fi

# --- update version in source ---
UPDATE_CHECKER="$PROJECT_DIR/p2p-client/src/main/java/org/q3s/p2p/client/UpdateChecker.java"
if [ -f "$UPDATE_CHECKER" ]; then
	sed -i "s/\"[0-9]\\+\\.[0-9]\\+\\.[0-9]\\+\"/\"$APP_VERSION\"/" "$UPDATE_CHECKER"
fi

# --- commit & tag ---
info "=== Committing and tagging $TAG ==="
cd "$PROJECT_DIR"
git -c user.name="qfolder-bot" -c user.email="bot@qfolder.local" add -A
git -c user.name="qfolder-bot" -c user.email="bot@qfolder.local" commit -m "$TAG: $NOTES" || true
git -c user.name="qfolder-bot" -c user.email="bot@qfolder.local" tag -f "$TAG"

info "=== Pushing to GitHub ==="
git push "https://${GITHUB_TOKEN}@github.com/${REPO}.git" HEAD "$TAG" --force-with-lease 2>&1

# --- create release ---
info "=== Creating GitHub release ==="
RELEASE_JSON=$(curl -s -X POST "https://api.github.com/repos/$REPO/releases" \
	-H "Authorization: token $GITHUB_TOKEN" \
	-H "Content-Type: application/json" \
	-d "$(cat <<EOF
{
	"tag_name": "$TAG",
	"name": "$TAG",
	"body": "$NOTES",
	"draft": false,
	"prerelease": false
}
EOF
)")

RELEASE_ID=$(echo "$RELEASE_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null || echo "")
if [ -z "$RELEASE_ID" ]; then
	die "Failed to create release. Response: $RELEASE_JSON"
fi
info "Release $TAG created (id=$RELEASE_ID)"

# --- upload asset ---
info "=== Uploading qfolder.jar ==="
UPLOAD_URL="https://uploads.github.com/repos/$REPO/releases/$RELEASE_ID/assets?name=qfolder.jar"
curl -s -X POST "$UPLOAD_URL" \
	-H "Authorization: token $GITHUB_TOKEN" \
	-H "Content-Type: application/octet-stream" \
	--data-binary @"$DIST_JAR" > /dev/null

info "=== Done ==="
echo "Release: https://github.com/$REPO/releases/tag/$TAG"
