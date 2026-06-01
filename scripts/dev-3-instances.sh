#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT_DIR/dist/qfolder.jar"
BASE_DIR="${QFOLDER_DEV_BASE:-/tmp/qfolder-dev}"

safe_base_dir() {
    case "$BASE_DIR" in
        /tmp/qfolder-dev|/tmp/qfolder-dev/*|/tmp/qfolder-real|/tmp/qfolder-real/*) return 0 ;;
        *) echo "ERROR: BASE_DIR inseguro: $BASE_DIR"; exit 1 ;;
    esac
}

if [ ! -f "$JAR" ]; then
    echo "ERROR: No se encuentra $JAR. Ejecute ./build.sh primero."
    exit 1
fi

# Limpieza de ejecuciones anteriores: mata procesos viejos y borra todo
PID_FILE="$BASE_DIR/pids"
if [ -f "$PID_FILE" ]; then
    echo "Deteniendo instancias previas..."
    while IFS= read -r pid; do
        if [[ "$pid" =~ ^[0-9]+$ ]] && [ -f "/proc/$pid/cmdline" ]; then
            cmdline="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)"
            if [[ "$cmdline" == *"qfolder.snapshot.jar"* && "$cmdline" == *"$BASE_DIR"* ]]; then
                kill "$pid" 2>/dev/null || true
            fi
        fi
    done < "$PID_FILE"
    sleep 1
    rm -f "$PID_FILE"
fi
safe_base_dir
rm -rf "$BASE_DIR"

mkdir -p "$BASE_DIR"/{home-a,home-b,home-c,samples,logs}

# Copia el JAR a un snapshot inmutable para evitar el race condition
# cuando build.sh sobrescribe dist/qfolder.jar mientras las instancias
# lo tienen abierto (el cp truncaría el archivo in-situ).
SNAPSHOT_JAR="$BASE_DIR/qfolder.snapshot.jar"
cp "$JAR" "$SNAPSHOT_JAR"

echo "Archivo A" > "$BASE_DIR/samples/a.txt"
echo "Archivo B" > "$BASE_DIR/samples/b.txt"
echo "Archivo C" > "$BASE_DIR/samples/c.txt"

run_instance() {
    local name="$1"
    local port="$2"
    local shared="$3"
    local home="$4"
    local log="$5"
    java \
        -Duser.home="$home" \
        -Dqfolder.user.name="$name" \
        -Dqfolder.shared.dir="$shared" \
        -Dqfolder.ws.port="$port" \
        -Dqfolder.tunnel.mock=true \
        -Dqfolder.ui.verbose=true \
        -jar "$SNAPSHOT_JAR" > "$log" 2>&1 &
    echo "$!" >> "$PID_FILE"
}

run_instance "devA" 18765 "$BASE_DIR/home-a/qfolder" "$BASE_DIR/home-a" "$BASE_DIR/logs/devA.log"
run_instance "devB" 18766 "$BASE_DIR/home-b/qfolder" "$BASE_DIR/home-b" "$BASE_DIR/logs/devB.log"
run_instance "devC" 18767 "$BASE_DIR/home-c/qfolder" "$BASE_DIR/home-c" "$BASE_DIR/logs/devC.log"

echo "Instancias iniciadas. Base: $BASE_DIR"
echo "PIDs: $(tr '\n' ' ' < "$PID_FILE")"
echo "Logs: $BASE_DIR/logs"
echo "Samples para arrastrar a Archivos/Chat: $BASE_DIR/samples"
echo "Para detener: vuelve a ejecutar este script; detecta y mata instancias previas de $BASE_DIR"
