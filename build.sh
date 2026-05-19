#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
CLIENT_DIR="$PROJECT_DIR/p2p-client"
DIST_DIR="$PROJECT_DIR/dist"

if [ -z "$JAVA_HOME" ]; then
    if [ -x "$HOME/.local/jdk/bin/java" ]; then
        export JAVA_HOME="$HOME/.local/jdk"
    fi
fi

if [ -n "$MAVEN_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
elif [ -x "$HOME/.local/maven/bin/mvn" ]; then
    export MAVEN_HOME="$HOME/.local/maven"
    export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
fi

echo "=== Building qfolder ==="
echo "JAVA_HOME=$JAVA_HOME"
java -version 2>&1 | head -1

cd "$CLIENT_DIR"
mvn clean package -DskipTests -q

mkdir -p "$DIST_DIR"
cp "$CLIENT_DIR/target/p2p-client-1.0-SNAPSHOT-fat.jar" "$DIST_DIR/qfolder.jar"
cp "$PROJECT_DIR/scripts/qfolder" "$DIST_DIR/qfolder"
cp "$PROJECT_DIR/scripts/qfolder.bat" "$DIST_DIR/qfolder.bat"
if [ ! -f "$DIST_DIR/qfolder.properties" ]; then
    cp "$CLIENT_DIR/src/main/resources/qfolder.properties.example" "$DIST_DIR/qfolder.properties"
fi
chmod +x "$DIST_DIR/qfolder"

echo "=== Build completo ==="
echo "Distribucion en: $DIST_DIR"
ls -lh "$DIST_DIR"
