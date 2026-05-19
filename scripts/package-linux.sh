#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_SCRIPT="$PROJECT_DIR/build.sh"
PACKAGE_ROOT="$PROJECT_DIR/build/jpackage/linux"
INPUT_DIR="$PACKAGE_ROOT/input"
CONTENT_DIR="$PACKAGE_ROOT/content"
OUTPUT_DIR="$PROJECT_DIR/packages/linux"
APP_NAME="qfolder"
APP_VERSION="1.0.0"
PACKAGE_TYPE="${1:-app-image}"

if [ -z "${JAVA_HOME:-}" ] && [ -x "$HOME/.local/jdk/bin/java" ]; then
    export JAVA_HOME="$HOME/.local/jdk"
fi

JPACKAGE="${JAVA_HOME:-}/bin/jpackage"
if [ ! -x "$JPACKAGE" ]; then
    if command -v jpackage >/dev/null 2>&1; then
        JPACKAGE="jpackage"
    else
        echo "ERROR: jpackage no encontrado. Use JDK 21+ o configure JAVA_HOME."
        exit 1
    fi
fi

echo "=== Build app ==="
"$BUILD_SCRIPT"

rm -rf "$PACKAGE_ROOT" "$OUTPUT_DIR"
mkdir -p "$INPUT_DIR" "$CONTENT_DIR/bin" "$OUTPUT_DIR"

cp "$PROJECT_DIR/dist/qfolder.jar" "$INPUT_DIR/qfolder.jar"
cp "$PROJECT_DIR/dist/qfolder.properties" "$CONTENT_DIR/qfolder.properties"

if [ -x "$HOME/.local/bin/cloudflared" ]; then
    cp "$HOME/.local/bin/cloudflared" "$CONTENT_DIR/bin/cloudflared"
elif [ -x "$HOME/.local/qfolder/bin/cloudflared" ]; then
    cp "$HOME/.local/qfolder/bin/cloudflared" "$CONTENT_DIR/bin/cloudflared"
elif command -v cloudflared >/dev/null 2>&1; then
    cp "$(command -v cloudflared)" "$CONTENT_DIR/bin/cloudflared"
else
    echo "=== Download cloudflared linux-amd64 ==="
    curl -L --fail --connect-timeout 20 --max-time 240 \
        "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64" \
        -o "$CONTENT_DIR/bin/cloudflared"
fi
chmod +x "$CONTENT_DIR/bin/cloudflared"

echo "=== jpackage ($PACKAGE_TYPE) ==="
JPACKAGE_ARGS=(
    --type "$PACKAGE_TYPE" \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --vendor "qfolder" \
    --description "qfolder peer-to-peer workspace client" \
    --input "$INPUT_DIR" \
    --main-jar "qfolder.jar" \
    --main-class "org.q3s.p2p.client.Main" \
    --dest "$OUTPUT_DIR" \
    --app-content "$CONTENT_DIR" \
    --java-options "-Dqfolder.packaged=true"
)

if [ "$PACKAGE_TYPE" != "app-image" ]; then
    JPACKAGE_ARGS+=(
    --linux-shortcut \
    --linux-menu-group "Network" \
    --linux-app-category "network"
    )
fi

"$JPACKAGE" "${JPACKAGE_ARGS[@]}"

if [ "$PACKAGE_TYPE" = "app-image" ]; then
    tar -czf "$OUTPUT_DIR/qfolder-linux-x64.tar.gz" -C "$OUTPUT_DIR" "$APP_NAME"
fi

echo "=== Package listo ==="
ls -lh "$OUTPUT_DIR"
