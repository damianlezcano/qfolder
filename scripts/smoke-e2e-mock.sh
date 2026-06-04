#!/bin/bash
# Smoke test headless del JAR generado.
# Verifica que dist/qfolder.jar es un JAR ejecutable valido y contiene
# las clases criticas para arranque y creacion de workspace. No requiere
# display X11 (no lanza la UI Swing).
#
# Uso: ./scripts/smoke-e2e-mock.sh
# Salida: exit 0 si todo OK, exit != 0 con detalle si falla.

set -eu

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT_DIR/dist/qfolder.jar"
JAR_LIST="/tmp/qfolder-jar-list-$$.txt"

PASS=0
FAIL=0

check() {
    local desc="$1"
    local result="$2"
    if [ "$result" = "0" ]; then
        echo "  [PASS] $desc"
        PASS=$((PASS+1))
    else
        echo "  [FAIL] $desc"
        FAIL=$((FAIL+1))
    fi
}

cleanup() {
    rm -f "$JAR_LIST" 2>/dev/null || true
    [ -n "${TMPDIR_TEST:-}" ] && rm -rf "$TMPDIR_TEST" 2>/dev/null || true
}
trap cleanup EXIT

echo "=== Smoke test qfolder ==="
echo "JAR: $JAR"
echo

if [ ! -f "$JAR" ]; then
    echo "ERROR: JAR no encontrado. Ejecute ./build.sh primero."
    exit 2
fi
check "JAR existe" 0

# Tamaño razonable (>= 1MB, <= 50MB)
SIZE=$(stat -c %s "$JAR" 2>/dev/null || stat -f %z "$JAR" 2>/dev/null)
if [ "$SIZE" -ge 1000000 ] && [ "$SIZE" -le 50000000 ]; then
    check "Tamano razonable (${SIZE} bytes)" 0
else
    check "Tamano razonable (${SIZE} bytes fuera de rango 1MB-50MB)" 1
fi

# Captura la lista del JAR en un archivo temporal (evita SIGPIPE con grep -q)
unzip -l "$JAR" 2>/dev/null > "$JAR_LIST" || true

# Manifest valido con Main-Class
if grep -q "Main-Class: org.q3s.p2p.client.Main" "$JAR_LIST"; then
    check "Manifest declara Main-Class (Main en META-INF/MANIFEST.MF)" 0
else
    MAIN_MANIFEST=$(unzip -p "$JAR" META-INF/MANIFEST.MF 2>/dev/null | head -5)
    if echo "$MAIN_MANIFEST" | grep -q "Main-Class"; then
        check "Manifest declara Main-Class" 0
    else
        check "Manifest declara Main-Class" 1
    fi
fi

# Clases criticas presentes
for cls in \
    "org/q3s/p2p/client/Main.class" \
    "org/q3s/p2p/client/view/Controller.class" \
    "org/q3s/p2p/client/SecureIdentityStore.class" \
    "org/q3s/p2p/core/app/CoreApplicationService.class" \
    "org/q3s/p2p/core/codec/CoreEnvelopeCodec.class" \
    "org/q3s/p2p/adapters/network/P2PNetworkAdapter.class" \
    "org/q3s/p2p/adapters/filesystem/FileSystemEventStore.class"; do
    if grep -q "$cls" "$JAR_LIST"; then
        check "Clase presente: $cls" 0
    else
        check "Clase presente: $cls" 1
    fi
done

# Recursos i18n presentes
for res in \
    "i18n/messages.properties" \
    "i18n/messages_es.properties"; do
    if grep -q "$res" "$JAR_LIST"; then
        check "Recurso i18n presente: $res" 0
    else
        check "Recurso i18n presente: $res" 1
    fi
done

# Verificar que el JAR arranca clases estaticas sin lanzar la UI.
# Compilamos y ejecutamos un programa chico que solo carga SecureIdentityStore
# para validar que las clases del core/identity son usables fuera de Swing.
TMPDIR_TEST=$(mktemp -d)

cat > "$TMPDIR_TEST/SmokeTest.java" <<'EOF'
public class SmokeTest {
    public static void main(String[] args) throws Exception {
        Class<?> cls = Class.forName("org.q3s.p2p.client.SecureIdentityStore");
        java.lang.reflect.Constructor<?> ctor = cls.getConstructor(java.io.File.class, String.class);
        java.io.File f = new java.io.File(System.getProperty("java.io.tmpdir"), "smoke-id-" + System.nanoTime() + ".bin");
        Object store = ctor.newInstance(f, "smoke-user");
        java.lang.reflect.Method m = cls.getMethod("loadOrCreate");
        m.invoke(store);
        String pub = (String) cls.getMethod("getPublicKeyBase64").invoke(store);
        if (pub == null || pub.isBlank()) {
            throw new IllegalStateException("public key vacia");
        }
        f.delete();
        System.out.println("OK smoke " + pub.length() + " chars pubkey");
    }
}
EOF

if javac -cp "$JAR" -d "$TMPDIR_TEST" "$TMPDIR_TEST/SmokeTest.java" 2>/dev/null; then
    if java -cp "$TMPDIR_TEST:$JAR" SmokeTest 2>/dev/null | grep -q "^OK smoke"; then
        check "SecureIdentityStore carga y genera claves" 0
    else
        check "SecureIdentityStore carga y genera claves" 1
    fi
else
    check "Compilacion y carga basica de clases del core" 1
fi

echo
echo "=== Resultado: $PASS pasaron, $FAIL fallaron ==="
if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
exit 0
