# Plan de Trabajo — qfolder

Plan detallado de correcciones de bugs y mejoras para el proyecto qfolder.
Generado el 2 de junio de 2026. Cada tarea incluye contexto, archivos afectados, líneas exactas, código problemático y la corrección propuesta.

> **Nota para el LLM ejecutor:** Antes de cada cambio, leer el archivo completo (o la sección relevante) para confirmar que las líneas coinciden. Después de cada grupo de cambios, ejecutar `cd p2p-client && mvn test` para validar que los tests pasan. Ejecutar `./build.sh` al final de cada fase.

---

## Índice

- [Fase 1: Bugs Críticos](#fase-1-bugs-críticos)
- [Fase 2: Bugs de Alta Severidad — Thread Safety](#fase-2-bugs-de-alta-severidad--thread-safety)
- [Fase 3: Bugs de Alta Severidad — Ciclo de Vida de Conexiones](#fase-3-bugs-de-alta-severidad--ciclo-de-vida-de-conexiones)
- [Fase 4: Bugs de Alta Severidad — Core y Seguridad](#fase-4-bugs-de-alta-severidad--core-y-seguridad)
- [Fase 5: Bugs de Severidad Media — Controller](#fase-5-bugs-de-severidad-media--controller)
- [Fase 6: Bugs de Severidad Media — Utilidades](#fase-6-bugs-de-severidad-media--utilidades)
- [Fase 7: Limpieza de Código y Debug Noise](#fase-7-limpieza-de-código-y-debug-noise)
- [Fase 8: Internacionalización (i18n)](#fase-8-internacionalización-i18n)
- [Fase 9: Tests](#fase-9-tests)
- [Fase 10: Build, Packaging y Configuración](#fase-10-build-packaging-y-configuración)
- [Fase 11: Mejoras Arquitectónicas](#fase-11-mejoras-arquitectónicas)

---

## Fase 1: Bugs Críticos

### 1.1 Whiteboard strokes se rompen tras persistencia/sync (ClassCastException)

**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/core/events/CoreEventCodec.java` (líneas 170–186)
- `p2p-client/src/main/java/org/q3s/p2p/core/state/WorkspaceStateBuilder.java` (líneas 121–125)

**Problema:** Los trazos de pizarra guardan `List<int[]>` en el payload. `CoreEventCodec.javaValue()` deserializa arrays JSON como `List<Object>` genéricos (líneas 170–175), convirtiendo `[[10,20],[30,40]]` en `List<List<Long>>`. El cast a `List<int[]>` en `WorkspaceStateBuilder` (línea 123) causa `ClassCastException` tras restart o sync P2P.

**Código problemático (`CoreEventCodec.java` líneas 170–175):**
```java
case ARRAY -> {
    List<Object> list = new ArrayList<>();
    for (JsonValue item : value.asJsonArray()) list.add(javaValue(item));
    yield list;
}
```

**Corrección:** En `CoreEventCodec.javaValue()`, detectar arrays de números (coordinate pairs) y devolver `int[]`:
```java
case ARRAY -> {
    var arr = value.asJsonArray();
    boolean allNumbers = !arr.isEmpty() && arr.stream().allMatch(v -> v.getValueType() == JsonValue.ValueType.NUMBER);
    if (allNumbers) {
        int[] point = new int[arr.size()];
        for (int i = 0; i < arr.size(); i++) point[i] = arr.getInt(i);
        yield point;
    }
    List<Object> list = new ArrayList<>();
    for (JsonValue item : arr) list.add(javaValue(item));
    yield list;
}
```

**Test:** Agregar test en `CoreQfolderTest` que cree un stroke, lo serialice con `CoreEventCodec.toJson()`, lo deserialice con `CoreEventCodec.eventFromJson()`, y verifique que los `points` son `List<int[]>`.

---

### 1.2 UI updates fuera del EDT en `completeCoreChunkDownload`

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` (líneas 4725–4750)

**Problema:** Este callback se invoca desde `CoreChunkTransferCoordinator.finish()` en un hilo WebSocket/P2P. La llamada a `refreshArchivosTable()` (línea 4744) muta el modelo de `JTable` directamente desde fuera del EDT.

**Código problemático (línea ~4744):**
```java
refreshArchivosTable();
```

**Corrección:** Envolver en `invokeLater` o eliminar la línea (ya que `publishCoreEvent(reShareEvent)` en línea 4743 schedule `applyCoreFilesToVisuals` en el EDT):
```java
javax.swing.SwingUtilities.invokeLater(this::refreshArchivosTable);
```

---

### 1.3 `showApprovalDialog` creado fuera del EDT

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- Definición: líneas 1285–1327
- Call site principal (off-EDT): línea 1080 — llamado desde `handleDirectPeerEvent` → `acceptCoreEventOnDirect`, que corre en el hilo del `EmbeddedWebSocketServer`

**Problema:** Crea y muestra un `JDialog` desde un hilo de red.

**Corrección:** Agregar guard al inicio de `showApprovalDialog`:
```java
private void showApprovalDialog(User to) {
    if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
        javax.swing.SwingUtilities.invokeLater(() -> showApprovalDialog(to));
        return;
    }
    if (to == null || to.getId() == null) return;
    // ... resto del método sin cambios
}
```

---

### 1.4 StyledDocument leído fuera del EDT en `broadcastNotes`

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `broadcastNotes()`: líneas 3698–3717
- `serializeNotesStateInBackground()`: líneas 3719–3752

**Problema:** `broadcastNotes()` captura `StyledDocument doc` en el EDT y luego un thread background (`"notes-serialize"`) llama `doc.getCharacterElement(i)` y `doc.getText(i, 1)`. `StyledDocument` no es thread-safe.

**Corrección:** Usar el método de serialización que ya existe en el EDT (`serializeNotesState()` en líneas 3776–3810) en lugar del thread background. Reemplazar `broadcastNotes()`:
```java
private void broadcastNotes() {
    if (notesPane == null || applyingRemoteNotes || System.currentTimeMillis() < suppressNotesBroadcastUntil) {
        return;
    }
    String state = serializeNotesState();
    if (state != null && !state.equals(lastSentNotesState)) {
        lastSentNotesState = state;
        recordNotesUpdateInCore(state);
    }
}
```
Si el rendimiento es un problema (serialización pesada), hacer snapshot inmutable en EDT y luego codificar off-EDT.

---

### 1.5 NPE en `handleDirectPeerEvent` cuando `event == null`

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` (líneas 1057–1066)

**Problema:** Si `event` es null y `coreChunkTransfer.handle(null, conn)` retorna `false`, la línea `event.getName()` lanza NPE.

**Corrección:** Agregar null guard al inicio del método (alrededor de línea 1046):
```java
private void handleDirectPeerEvent(WebSocket conn, Event event) {
    if (event == null || event.getName() == null) return;
    // ... resto sin cambios
}
```

---

### 1.6 Membership replay ordering — aprobaciones se pierden

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/core/state/WorkspaceStateBuilder.java` (líneas 58–94)

**Problema:** Si `MEMBER_JOIN_APPROVAL` se procesa antes de `MEMBER_JOIN_REQUESTED` (por clock skew entre peers), el approval se descarta silenciosamente porque `pendingMembers().containsKey(candidateId)` es `false`.

**Corrección:** Ordenar eventos antes de replay. Modificar `fromEvents()` (alrededor de línea 58):
```java
public static WorkspaceState fromEvents(List<Event> events) {
    WorkspaceState state = new WorkspaceState();
    List<Event> ordered = new ArrayList<>(events);
    ordered.sort(Comparator.comparing(Event::createdAt).thenComparing(Event::eventId));
    for (Event event : ordered) {
        // ... switch existente
    }
    return state;
}
```

**Nota:** Esto también requiere alinear `InMemoryEventStore.listEvents()` para que ordene igual que `FileSystemEventStore` (ver tarea 4.3).

---

### 1.7 `PublicKeyAuthProvider.validateMemberReconnect` no verifica el token

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/core/auth/PublicKeyAuthProvider.java` (líneas 54–58)

**Problema:** Acepta **cualquier string no vacío** como token de reconexión.

**Código problemático:**
```java
public boolean validateMemberReconnect(String workspaceId, String memberId, String membershipToken, WorkspaceState state) {
    if (state == null || memberId == null) return false;
    return state.isAuthorized(memberId) && membershipToken != null && !membershipToken.isBlank();
}
```

**Corrección:**
```java
public boolean validateMemberReconnect(String workspaceId, String memberId, String membershipToken, WorkspaceState state) {
    if (state == null || memberId == null || membershipToken == null || membershipToken.isBlank()) return false;
    Member member = state.authorizedMembers().get(memberId);
    return member != null && !member.revoked() && membershipToken.equals(member.membershipToken());
}
```

---

## Fase 2: Bugs de Alta Severidad — Thread Safety

### 2.1 `directPeerConnections` no es thread-safe

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- Declaración: línea 179
- Escrituras: líneas 1054 (`put`), 1121 (`remove`)
- Lecturas: líneas 1338, 1350, 1932, 1937, 1959, 2421–2422

**Problema:** `LinkedHashMap` accedido desde hilos WebSocket, P2P callback, y EDT sin sincronización.

**Corrección:** Reemplazar la declaración en línea 179:
```java
// Antes:
private final Map<String, WebSocket> directPeerConnections = new LinkedHashMap<>();
// Después:
private final Map<String, WebSocket> directPeerConnections = new java.util.concurrent.ConcurrentHashMap<>();
```

---

### 2.2 `WsClient.callCloseCallback` — check-then-set no atómico

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/ws/WsClient.java` (líneas 89–94)

**Problema:** Dos hilos pueden pasar `!closeNotified` y ejecutar `onClose.run()` dos veces.

**Corrección:** Reemplazar `closeNotified` (línea 23) con `AtomicBoolean`:
```java
// Declaración (reemplazar línea 23):
private final java.util.concurrent.atomic.AtomicBoolean closeNotified = new java.util.concurrent.atomic.AtomicBoolean(false);

// Método callCloseCallback (reemplazar líneas 89–94):
private void callCloseCallback() {
    if (onClose != null && closeNotified.compareAndSet(false, true)) {
        onClose.run();
    }
}
```

---

### 2.3 `P2PMeshService.peerCatalog` no es thread-safe

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PMeshService.java`
- Declaración: línea 28
- Accesos: líneas 73, 113, 144, 152, 190, 203, 210–211, 279

**Corrección:** Reemplazar en línea 28:
```java
// Antes:
private final java.util.Map<String, String> peerCatalog = new java.util.LinkedHashMap<>();
// Después:
private final java.util.Map<String, String> peerCatalog = new java.util.concurrent.ConcurrentHashMap<>();
```

---

### 2.4 `P2PMeshService` status publish fields no son `volatile`

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PMeshService.java` (líneas 30–31)

**Corrección:**
```java
// Antes:
private String lastPublishedPeerUrl = null;
private Set<String> lastPublishedConnections = Set.of();
// Después:
private volatile String lastPublishedPeerUrl = null;
private volatile Set<String> lastPublishedConnections = Set.of();
```

---

### 2.5 `applyingRemoteNotes` no es `volatile`

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` (línea 199)

**Corrección:**
```java
// Antes:
private boolean applyingRemoteNotes;
// Después:
private volatile boolean applyingRemoteNotes;
```

---

### 2.6 `CloudflareTunnel` fields no son `volatile`

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/hub/CloudflareTunnel.java` (líneas 15–17)

**Corrección:**
```java
// Antes:
private Process process;
private String tunnelUrl;
private boolean running;
// Después:
private volatile Process process;
private volatile String tunnelUrl;
private volatile boolean running;
```

---

### 2.7 `Logger.SimpleDateFormat` no es thread-safe

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/util/Logger.java` (líneas 19–20)

**Corrección:** Reemplazar con `DateTimeFormatter` (thread-safe):
```java
// Antes:
private String pattern = "dd/MM/yyyy HH:mm:ss";
private SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);

// Después:
private static final java.time.format.DateTimeFormatter FORMATTER =
    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        .withZone(java.time.ZoneId.systemDefault());
```

Y en los métodos `info()`, `err()`, `debug()` reemplazar:
```java
// Antes:
String date = simpleDateFormat.format(new Date());
// Después:
String date = FORMATTER.format(java.time.Instant.now());
```

Eliminar los imports de `SimpleDateFormat` y `Date` si quedan sin uso.

---

## Fase 3: Bugs de Alta Severidad — Ciclo de Vida de Conexiones

### 3.1 `PeerLink.connect()` — leak de WsClient en reintentos

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PNetworkAdapter.java` (líneas 177–232)

**Problema:** Cada reintento crea `client = new WsClient(...)` sin cerrar el anterior. Leak de executors y sockets.

**Corrección:** Cerrar el cliente anterior antes de cada reintento. En el loop (alrededor de línea 180):
```java
void connect(Runnable onReady) {
    boolean connected = false;
    for (int attempt = 1; attempt <= 3; attempt++) {
        if (shuttingDown) break;
        // Cerrar intento anterior si existe
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
            client = null;
        }
        try {
            client = new WsClient(/* ... params existentes ... */);
            // ... resto del intento sin cambios
```

---

### 3.2 `DirectBootstrap.join()` — WsClient nunca cerrado; leak en reintentos

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/adapters/network/DirectBootstrap.java` (líneas 65–98)

**Problema:**
1. Join exitoso: `return true` sin cerrar el socket bootstrap
2. Reintentos fallidos: el `WsClient` del intento anterior no se cierra

**Corrección:** Usar try/finally por intento:
```java
for (int attempt = 1; attempt <= 3; attempt++) {
    WsClient bootstrap = null;
    try {
        bootstrap = new WsClient(/* ... */);
        bootstrap.connectBlocking(6, java.util.concurrent.TimeUnit.SECONDS);
        // ... envío de join + sync request ...
        return true;
    } catch (Exception e) {
        lastError = e;
        debug.accept("Bootstrap fallo intento " + attempt + ": " + e.getMessage());
        try { Thread.sleep(250L * attempt); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
    } finally {
        if (bootstrap != null) {
            try { bootstrap.close(); } catch (Exception ignored) {}
        }
    }
}
```

---

### 3.3 `connectTo` — PeerLink inactivo bloquea reconexión

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PNetworkAdapter.java` (líneas 81–86)

**Problema:** `putIfAbsent` retorna el link existente aun si está inactivo/zombie, y llama `onReady` sin reconectar.

**Código problemático:**
```java
PeerLink existing = peers.putIfAbsent(peerId, link);
if (existing != null) {
    if (onReady != null) onReady.run();
    return;
}
```

**Corrección:**
```java
PeerLink link = new PeerLink(peerId, uri);
PeerLink existing = peers.putIfAbsent(peerId, link);
if (existing != null) {
    if (existing.active()) {
        if (onReady != null) onReady.run();
        return;
    }
    // Link inactivo/zombie: reemplazar
    peers.remove(peerId, existing);
    try { existing.close(); } catch (Exception ignored) {}
    if (peers.putIfAbsent(peerId, link) != null) {
        if (onReady != null) onReady.run();
        return;
    }
}
link.connect(onReady);
```

Verificar que `PeerLink` tenga un método `close()` (si no existe, crearlo para cerrar `client`).

---

### 3.4 `CloudflareTunnel.isRunning()` roto en mock mode

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/hub/CloudflareTunnel.java` (líneas 93–95)

**Problema:** Mock pone `running=true` pero `process` queda null → `isRunning()` siempre retorna false.

**Código problemático:**
```java
public boolean isRunning() {
    return running && process != null && process.isAlive();
}
```

**Corrección:**
```java
public boolean isRunning() {
    if (Config.isTunnelMockEnabled()) {
        return running;
    }
    return running && process != null && process.isAlive();
}
```

---

### 3.5 `EmbeddedWebSocketServer.onError` vacío

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/hub/EmbeddedWebSocketServer.java` (líneas 99–101)

**Corrección:**
```java
@Override
public void onError(WebSocket conn, Exception ex) {
    err("onError: " + (ex != null ? ex.getMessage() : "unknown"));
    if (conn != null) {
        try { conn.close(); } catch (Exception ignored) {}
    }
}
```

Nota: `err(...)` es el método de log ya existente en la clase.

---

### 3.6 `handleIncomingSyncRequest` — sin check de `active()` ni try/catch en send

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PNetworkAdapter.java` (líneas 288–307)

**Problema:** Accede a `link.client.send(...)` directamente sin verificar `link.active()` ni manejar errores de envío.

**Corrección:** Agregar checks (alrededor de línea 296):
```java
PeerLink link = clientForPeer(event.getUser() != null ? event.getUser().getId() : null);
if (link != null && link.active()) {
    try {
        org.q3s.p2p.model.Event response = new org.q3s.p2p.model.Event(/* ... */);
        link.client.send(org.q3s.p2p.model.util.EventUtils.toJsonBase64(response));
    } catch (Exception e) {
        debug.accept("P2P error respondiendo sync: " + e.getMessage());
    }
}
```

---

### 3.7 `initializeCoreServices` no cierra servicios previos

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` (líneas 1871–1891)

**Problema:** Llamado en startup (línea 257) y al preparar session dirs (línea 1722). Cada llamada crea nuevos `P2PNetworkAdapter`, `P2PMeshService`, `CoreChunkTransferCoordinator` sin cerrar los anteriores.

**Corrección:** Al inicio de `initializeCoreServices()`, cerrar los servicios anteriores:
```java
private void initializeCoreServices() {
    // Cerrar servicios anteriores
    if (p2pMesh != null) {
        try { p2pMesh.disconnectAll(); } catch (Exception ignored) {}
    }
    if (coreChunkTransfer != null) {
        try { coreChunkTransfer.shutdown(); } catch (Exception ignored) {}
    }
    // ... resto del método
}
```

Verificar que `CoreChunkTransferCoordinator` tenga un método `shutdown()` que cancele el `retryScheduler`. Si no existe, crearlo.

---

## Fase 4: Bugs de Alta Severidad — Core y Seguridad

### 4.1 `EventValidator` — firmas opcionales cuando public key es vacía

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/core/events/EventValidator.java` (líneas 56–59)

**Problema:** Retorna `true` cuando `publicKey` es null o blank, permitiendo eventos sin firma en workspaces Ed25519.

**Código problemático:**
```java
private boolean validSignatureIfPresent(Event event, String publicKey) {
    if (publicKey == null || publicKey.isBlank()) return true;
    return PublicKeyAuthProvider.verifyEventSignature(event, publicKey);
}
```

**Corrección (modo estricto):**
```java
private boolean validSignatureIfPresent(Event event, String publicKey) {
    if (publicKey == null || publicKey.isBlank()) {
        // En modo estricto, requerir firma cuando el evento la tiene
        return event.signature() == null || event.signature().isBlank();
    }
    return PublicKeyAuthProvider.verifyEventSignature(event, publicKey);
}
```

**Nota:** Evaluar si conviene `return false` directamente para forzar que todos los miembros tengan public key.

---

### 4.2 `EventUtils.toObjectBase64` — parsing de `data:` URL roto

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/model/util/EventUtils.java` (líneas 33–40)

**Problema:** `substring(5)` sobre `"data:..."` deja `application/json;base64,XXXX` en lugar del payload Base64.

**Código problemático:**
```java
if(dataBase64.startsWith("data:")){
    jsonBase64 = dataBase64.substring(5);
}
```

**Corrección:**
```java
if (dataBase64.startsWith("data:")) {
    int comma = dataBase64.indexOf(',');
    if (comma >= 0) {
        jsonBase64 = dataBase64.substring(comma + 1);
    }
}
```

---

### 4.3 `InMemoryEventStore.listEvents` — orden inconsistente con `FileSystemEventStore`

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/adapters/memory/InMemoryEventStore.java` (líneas 26–28)

**Problema:** Retorna en orden de inserción, mientras `FileSystemEventStore` ordena por `createdAt` + `eventId`. Los tests se comportan distinto que producción.

**Corrección:**
```java
public synchronized List<Event> listEvents(String workspaceId) {
    return byWorkspace.getOrDefault(workspaceId, List.of()).stream()
            .map(byId::get)
            .sorted(java.util.Comparator.comparing(Event::createdAt).thenComparing(Event::eventId))
            .toList();
}
```

**Nota:** Después de este cambio, ejecutar toda la suite de tests para detectar tests que dependían del orden de inserción.

---

### 4.4 `FileSystemFileChunkStore.resolveChunkPath` — O(n) walk

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/adapters/filesystem/FileSystemFileChunkStore.java` (líneas 54–60)

**Problema:** Cada lectura de chunk hace `Files.walk()` del árbol entero.

**Corrección:** Cambiar `getChunk` y `hasChunk` para buscar directamente bajo `fileDir`:
```java
private Path resolveChunkPath(String hash) throws java.io.IOException {
    if (!Files.isDirectory(root)) return null;
    // Buscar en todos los subdirectorios de fileId
    try (var dirs = Files.list(root)) {
        for (Path dir : dirs.toList()) {
            Path candidate = dir.resolve(hash + ".chunk");
            if (Files.isRegularFile(candidate)) return candidate;
        }
    }
    return null;
}
```

**Mejora ideal (si se puede cambiar la interfaz `FileChunkStore`):** Pasar `fileId` como parámetro para ir directo a `root/<fileId>/<hash>.chunk`.

---

### 4.5 `PublicKeyAuthProvider.signEvent` — firma vacía silenciosa

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/core/auth/PublicKeyAuthProvider.java` (líneas 91–101)

**Problema:** Si la firma falla, retorna `""`. El evento se almacena localmente con firma vacía. Peers remotos con la public key lo rechazan → divergencia local/remota.

**Corrección:** Propagar la excepción o al menos logear y no almacenar:
```java
} catch (Exception e) {
    throw new IllegalStateException("Failed to sign event: " + e.getMessage(), e);
}
```

---

### 4.6 `User.getFiles()` retorna copias — core files nunca se persisten

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` (líneas 2227–2240)

**Problema:** `us.getFiles().add(coreFile)` muta una copia desechable. Los archivos core nunca se agregan al user.

**Corrección:** Construir una lista y usar `setFiles`:
```java
List<QFile> coreFiles = new ArrayList<>();
for (QFile existing : us.getFiles()) {
    if (isCoreMetadataFile(existing)) coreFiles.add(existing);
}
us.copy(user);
us.setOnline(true);
// Agregar core files que no venían en el user nuevo
List<QFile> merged = new ArrayList<>(us.getFiles());
for (QFile coreFile : coreFiles) {
    boolean alreadyPresent = merged.stream()
            .anyMatch(f -> f.getName().equals(coreFile.getName()) && f.getSize() == coreFile.getSize());
    if (!alreadyPresent) merged.add(coreFile);
}
us.setFiles(merged);
```

---

## Fase 5: Bugs de Severidad Media — Controller

### 5.1 `removeTransferProgress` — NPE cuando `transferPanel` es null

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` (líneas 4809–4819)

**Corrección:** Agregar null guard al inicio:
```java
private void removeTransferProgress(String transferId, JPanel row) {
    transferBars.remove(transferId);
    transferRows.remove(transferId);
    activeTransferRequests.remove(transferId);
    transferPendingOpenLinks.remove(transferId);
    chatTransferLinks.remove(transferId);
    if (transferPanel == null) return;
    if (row != null) transferPanel.remove(row);
    transferPanel.setVisible(transferPanel.getComponentCount() > 0);
    transferPanel.revalidate();
    transferPanel.repaint();
}
```

---

### 5.2 `historySaved` se setea antes del I/O

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` (líneas 4894–4940)

**Corrección:** Mover `historySaved = true` al final del try, después de todo el I/O. En el catch, dejarlo en `false`:
```java
private void saveSessionHistory(String reason) {
    if (historySaved || wk == null) return;
    try {
        // ... todo el I/O existente ...
        historySaved = true;  // MOVER aquí (era línea 4896)
    } catch (Exception e) {
        log.err("No se pudo guardar el historial de sesion: " + e.getMessage());
    }
}
```

---

### 5.3 Join timeout timer nunca se cancela

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` (líneas 1243–1250)

**Corrección:**
1. Agregar field (junto a las declaraciones, ~línea 200):
```java
private javax.swing.Timer joinTimeoutTimer;
```

2. En `jButton2ActionPerformed` (línea 1243), asignar al field:
```java
joinTimeoutTimer = new Timer(30000, e -> { /* ... existente ... */ });
joinTimeoutTimer.setRepeats(false);
joinTimeoutTimer.start();
```

3. Cancelar en `activateWorkspaceFromCore` y `showJoinAfterWorkspaceLost`:
```java
if (joinTimeoutTimer != null) {
    joinTimeoutTimer.stop();
    joinTimeoutTimer = null;
}
```

---

### 5.4 Chat "Fijar" menu item es no-op

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` (líneas 2945–2946)

**Código problemático:**
```java
JMenuItem pin = new JMenuItem("Fijar");
pin.addActionListener(ev -> {});
```

**Corrección:**
```java
JMenuItem pin = new JMenuItem(I18n.get("chat.pin", "Fijar"));
pin.addActionListener(ev -> applyPinnedChatMessage(message));
```

Agregar key `chat.pin` a los bundles i18n.

---

### 5.5 `searchTabById` usa `endsWith` en vez de `equals`

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- Línea 4553 en `searchTabById`
- Línea 4604 en `findTableByUserId` (mismo patrón)

**Corrección en ambos métodos:**
```java
// Antes:
if (cos[i].getName().endsWith(id)) {
// Después:
if (id != null && id.equals(cos[i].getName())) {
```

---

### 5.6 `notify()` — `wsClient.close()` sin null check

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` (línea 1416)

**Corrección:**
```java
// Antes:
wsClient.close();
// Después:
if (wsClient != null) wsClient.close();
```

---

### 5.7 Notes document listener re-added sin removal

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` (líneas 3877–3885)

**Corrección:** Antes de `setDocument`, remover el listener del documento viejo:
```java
// Agregar antes de notesPane.setDocument(doc):
if (notesDocumentListener != null && notesPane.getDocument() != null) {
    notesPane.getDocument().removeDocumentListener(notesDocumentListener);
}
notesPane.setDocument(doc);
attachNotesDocumentListener();
```

---

### 5.8 `removeAllTab` no limpia todo el estado de sesión

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` (líneas 4561–4586)

**Corrección:** Extender el método para limpiar los campos faltantes:
```java
// Al final de removeAllTab(), agregar:
appliedCoreChatIds.clear();
chatMessages.clear();
chatMessageRanges.clear();
chatFileLinks.clear();
chatTransferLinks.clear();
fileRegistry.clear();
filePeers.clear();
knownMembers.clear();
memberConnectedAt.clear();
corePeerUrls.clear();
corePeerConnections.clear();
pendingMemberPublicKeys.clear();
indexedCoreFiles.clear();
indexedCoreFilesWorkspaceId = null;
if (notesSyncTimer != null) {
    notesSyncTimer.stop();
    notesSyncTimer = null;
}
for (JDialog d : approvalDialogs.values()) {
    try { d.dispose(); } catch (Exception ignored) {}
}
approvalDialogs.clear();
lastSentNotesState = null;
lastAppliedNotesState = null;
lastAppliedCoreWhiteboardState = null;
historySaved = false;
```

---

## Fase 6: Bugs de Severidad Media — Utilidades

### 6.1 `ExecutorFactoryBean.create()` retorna null en Windows

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/exec/ExecutorFactoryBean.java` (líneas 5–13)

**Corrección:** Agregar soporte Windows usando `Desktop` API o `cmd /c start`:
```java
public static Executor create() {
    String os = getOperatingSystem();
    if (os.startsWith("Linux")) {
        return new LinuxExecutorBean();
    } else if (os.startsWith("Mac")) {
        return new MacExecutorBean();
    } else if (os.startsWith("Windows")) {
        return new WindowsExecutorBean();
    }
    throw new IllegalStateException("Unsupported OS: " + os);
}
```

Crear `WindowsExecutorBean.java`:
```java
package org.q3s.p2p.client.exec;

public class WindowsExecutorBean implements Executor {
    @Override
    public void open(String fullname) throws InterruptedException {
        try {
            new ProcessBuilder("cmd", "/c", "start", "", fullname).start();
        } catch (Exception e) {
            // Fallback: usar Desktop API
            try {
                java.awt.Desktop.getDesktop().open(new java.io.File(fullname));
            } catch (Exception ex) {
                throw new InterruptedException("No se pudo abrir: " + fullname);
            }
        }
    }
}
```

---

### 6.2 `Config.USER_NAME` null en Windows

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/Config.java` (línea 15)

**Corrección:**
```java
// Antes:
public static final String USER_NAME = AppConfig.get("qfolder.user.name", System.getenv("USER"));
// Después:
public static final String USER_NAME = AppConfig.get("qfolder.user.name",
    firstNonBlank(System.getenv("USER"), System.getenv("USERNAME"), System.getProperty("user.name")));

private static String firstNonBlank(String... values) {
    for (String v : values) {
        if (v != null && !v.isBlank()) return v;
    }
    return null;
}
```

---

### 6.3 `Config.TEMP_PATH` es relativo

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/Config.java` (línea 11)

**Corrección:**
```java
// Antes:
public static final String TEMP_PATH = "temp";
// Después:
public static final String TEMP_PATH = resolveHome(AppConfig.get("qfolder.temp.dir",
    "~/qfolder/temp"));
```

---

### 6.4 `UpdateChecker.VERSION` hardcodeado

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/UpdateChecker.java` (línea 26)

**Corrección:** Leer la versión desde el POM/Manifest:
```java
private static final String VERSION = loadVersion();

private static String loadVersion() {
    try (InputStream is = UpdateChecker.class.getResourceAsStream("/META-INF/maven/org.q3s/p2p-client/pom.properties")) {
        if (is != null) {
            java.util.Properties props = new java.util.Properties();
            props.load(is);
            return props.getProperty("version", "0.0.0");
        }
    } catch (Exception ignored) {}
    return "0.0.0";
}
```

---

### 6.5 `UpdateChecker` — JSON parsing con regex

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/UpdateChecker.java` (líneas 29–30, 50–58)

**Corrección:** Usar `jakarta.json.JsonReader`:
```java
try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new java.io.StringReader(body))) {
    jakarta.json.JsonObject release = reader.readObject();
    String latestTag = release.getString("tag_name").replace("v", "").trim();
    jakarta.json.JsonArray assets = release.getJsonArray("assets");
    List<String> urls = new ArrayList<>();
    for (jakarta.json.JsonValue asset : assets) {
        urls.add(asset.asJsonObject().getString("browser_download_url"));
    }
    // ... continuar con lógica existente
}
```

---

### 6.6 `CloudflareInstaller` — ARM64 no soportado

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/CloudflareInstaller.java` (líneas 18–20)

**Corrección:** Detectar arquitectura y seleccionar URL:
```java
private static String getDownloadUrl(String os) {
    String arch = System.getProperty("os.arch", "").toLowerCase();
    boolean isArm = arch.contains("aarch64") || arch.contains("arm");
    if (os.contains("linux")) {
        return isArm ? "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64"
                      : "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64";
    } else if (os.contains("mac")) {
        return isArm ? "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-arm64.tgz"
                      : "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-amd64.tgz";
    } else if (os.contains("windows")) {
        return "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe";
    }
    return null;
}
```

---

### 6.7 `CloudflareInstaller` — race condition en install concurrente

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/CloudflareInstaller.java` (líneas 22–23, 39–40)

**Corrección:** Hacer `installed` volatile y sincronizar `ensureInstalled`:
```java
private static volatile boolean installed = false;
private static final Object INSTALL_LOCK = new Object();

public static void ensureInstalled(Logger log) {
    if (installed) return;
    synchronized (INSTALL_LOCK) {
        if (installed) return;
        // ... lógica de instalación existente ...
    }
}
```

---

## Fase 7: Limpieza de Código y Debug Noise

### 7.1 Eliminar `System.out.println` de debug en producción

**44 instancias en código de producción (no tests):**

| Archivo | Líneas | Acción |
|---|---|---|
| `Controller.java` | 3156, 3166, 3170, 3172, 3195, 3198, 3211, 3219, 3226, 3230, 3234, 3238, 3241, 3245, 3247, 3253, 3256, 3259, 3262, 3265, 3268, 3271, 3274, 3277, 3280, 3283, 3286, 3289, 3292, 3295, 3298, 3301, 3304, 3312, 3320 | Eliminar o reemplazar con `log.debug(...)` si es útil |
| `I18n.java` | 29, 34, 38 | Eliminar |
| `UserPreferences.java` | 96 | Eliminar |
| `FileTableModel.java` | 69 | Eliminar |
| `EmbeddedWebSocketServer.java` | 136 | Convertir a `debug(...)` si tiene logger |

**Nota:** `Logger.java` líneas 31, 38 son **intencionales** (es el logger stdout de la app). NO eliminar.

---

### 7.2 Eliminar campos no usados

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `latestWhiteboardSequence` (línea 223): declarado pero nunca leído ni escrito
- `latestNotesSequence` (línea 224): declarado pero nunca leído ni escrito

**Corrección:** Eliminar ambas declaraciones.

---

### 7.3 Eliminar `application.properties` vacío

**Archivo:** `p2p-client/src/main/resources/application.properties` — 0 bytes, sin uso.

**Corrección:** Eliminar el archivo.

---

### 7.4 Eliminar o renombrar `load.giif.gif`

**Archivo:** `p2p-client/src/main/resources/load.giif.gif` — nombre con typo, no referenciado en código.

Verificar si `load.gif` (el otro archivo) tampoco se usa. Si ninguno se referencia, eliminar ambos. Si `load.gif` se usa, eliminar solo `load.giif.gif`.

---

### 7.5 Eliminar keys i18n no usadas

**Archivos:** `messages.properties`, `messages_es.properties`, `messages_en.properties`

Keys que existen pero NO se referencian en código:
- `disconnected`
- `reconnecting`
- `reconnecting.trying`
- `reconnecting.ok`
- `reconnecting.fail`

**Corrección:** Eliminar de los 3 bundles, o conservar si se planea usarlas pronto.

---

### 7.6 Fix keys duplicadas en `messages.properties`

**Archivo:** `p2p-client/src/main/resources/i18n/messages.properties`

Keys duplicadas (la última gana, la primera es dead code):
- `tab.files` (aparece en líneas ~77 y ~104)
- `transfer.requesting` (líneas ~108 y ~113)
- `transfer.retrying` (líneas ~109 y ~114)

**Corrección:** Eliminar las duplicadas, conservando la versión correcta.

---

### 7.7 Deprecated `new Locale(String)` en `I18n.java`

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/util/I18n.java` (líneas 17, 26)

**Corrección:**
```java
// Antes:
Locale langOnly = new Locale(locale.getLanguage());
// Después:
Locale langOnly = Locale.forLanguageTag(locale.getLanguage());
```

---

## Fase 8: Internacionalización (i18n)

### 8.1 Completar `messages_en.properties`

**Archivo:** `p2p-client/src/main/resources/i18n/messages_en.properties`

**35 keys faltantes** (presentes en `messages.properties` pero ausentes de `messages_en.properties`):

```properties
# Table columns
col.file=File
col.size=Size
col.modDate=Modified
col.owner=Owner
col.peers=Peers

# Context menus
menu.open=Open
menu.download=Download
menu.delete=Delete
menu.refresh=Refresh
menu.paste=Paste

# Notes
notes.translucent=Translucent

# Tooltips
tooltip.attachFile=Attach file
tooltip.currentColor=Current color
tooltip.insertImage=Insert image
tooltip.shrinkImage=Shrink image
tooltip.growImage=Grow image
tooltip.reduceText=Reduce text size
tooltip.increaseText=Increase text size
tooltip.fontColor=Font color
tooltip.translucent=Translucent
tooltip.exportNotes=Export notes
tooltip.configureProxy=Configure proxy

# Whiteboard tooltips
tooltip.whiteboard.select=Select
tooltip.whiteboard.pencil=Pencil
tooltip.whiteboard.text=Text
tooltip.whiteboard.image=Image
tooltip.whiteboard.arrow=Arrow
tooltip.whiteboard.circle=Circle
tooltip.whiteboard.square=Square
tooltip.whiteboard.rectangle=Rectangle
tooltip.whiteboard.triangle=Triangle
tooltip.whiteboard.reduceSize=Reduce size
tooltip.whiteboard.increaseSize=Increase size
tooltip.whiteboard.clear=Clear
tooltip.whiteboard.save=Save
```

**Decisión alternativa:** Eliminar `messages_en.properties` ya que `messages.properties` ya es en inglés y sirve como fallback.

---

### 8.2 i18n de Controller — tooltips de tabs

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`

Strings hardcodeados que necesitan keys i18n:

| Línea(s) | String actual | Key propuesta |
|---|---|---|
| 768, 2690 | `"Chat grupal del workspace"` | `tooltip.tab.chat` |
| 775, 4013 | `"Pizarra colaborativa simple"` | `tooltip.tab.whiteboard` |
| 781, 4137 | `"Notas compartidas con formato básico"` | `tooltip.tab.notes` |
| 787, 2306 | `"Miembros del workspace"` | `tooltip.tab.members` |
| 2416 | `"Vista unificada de archivos"` | `tooltip.tab.files` |

---

### 8.3 i18n de Controller — mensajes de túnel/login

| Línea | String actual | Key propuesta |
|---|---|---|
| 990 | `"Preparando túnel... por favor espere"` | `tunnel.preparing` |
| 1014 | `"Iniciando túnel Cloudflare..."` | `tunnel.starting` |
| 1020 | `"Iniciando túnel (mock)..."` | `tunnel.starting.mock` |
| 1185 | `"Error al iniciar el tunel..."` | `tunnel.error` |
| 1246 | `"Solicitud enviada, pero no llego autorizacion..."` | `join.timeout` |
| 1259 | `"Error al iniciar endpoint local: "` | `endpoint.error` |

---

### 8.4 i18n de Controller — members table headers

**Línea 2312:** Headers de tabla hardcodeados.

Agregar keys: `col.members.name`, `col.members.status`, `col.members.connectedSince`, `col.members.peers`, `col.members.connectedWith`, `col.members.publicUrl`.

Y status values (línea 2334): `members.connected`, `members.disconnected`.

---

### 8.5 i18n de Controller — chat context menu

| Línea | String | Key |
|---|---|---|
| 2937 | `"Responder"` | `chat.reply` |
| 2945 | `"Fijar"` | `chat.pin` |
| 2616 | `"Re:"` | `chat.replyPrefix` |
| 2619 | `"Enviar"` | `chat.send` (ya existe) |

---

### 8.6 i18n de Controller — whiteboard tool names y diálogos

**Línea 3958:** Array de tool names hardcodeados en español.

Los tool names se usan como **identificadores internos** en comparaciones de strings (líneas 3475–3499, 5155+). Hay dos opciones:
- Opción A: Mantener IDs internos en inglés y usar i18n solo para tooltips
- Opción B: Usar constantes para los IDs y traducir los labels

**Recomendación:** Opción A — separar ID de label.

---

### 8.7 i18n de Controller — diálogos de export

| Línea | String | Key |
|---|---|---|
| 4054–4056 | `"Pizarra guardada correctamente en:\n..."` | `whiteboard.saved` |
| 4059–4061 | `"No se pudo guardar la pizarra:\n..."` | `whiteboard.saveError` |
| 5020–5022 | `"Notas guardadas correctamente en:\n..."` | `notes.saved` |
| 5025–5027 | `"No se pudieron guardar las notas:\n..."` | `notes.saveError` |

---

### 8.8 i18n de `UpdateChecker.java`

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/UpdateChecker.java`

Todas las UI strings están hardcoded en inglés. Agregar keys:
- `update.major.message`, `update.major.title`
- `update.available.message`, `update.available.title`
- `update.downloading`, `update.success`, `update.failed`

---

### 8.9 i18n de `CloudflareInstaller.java`

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/CloudflareInstaller.java`

Mensajes de log hardcodeados en español. Reemplazar con `I18n.get(...)`:
- Línea 35: `"Modo tunnel mock activo..."` → `cloudflare.mock`
- Línea 63: `"Sistema operativo no soportado..."` → `cloudflare.unsupportedOs`
- Línea 77: `"Descargando cloudflared..."` → `cloudflare.downloading`
- Línea 101: `"cloudflared instalado en..."` → `cloudflare.installed`
- Líneas 104–105: `"Error al descargar..."` → `cloudflare.downloadError`

---

## Fase 9: Tests

### 9.1 Tests para `EventValidator`

**Archivo a crear:** `p2p-client/src/test/java/org/q3s/p2p/core/EventValidatorTest.java`

Tests prioritarios:
- Evento de miembro no autorizado es rechazado
- Evento con firma Ed25519 válida es aceptado
- Evento con firma Ed25519 inválida es rechazado
- Evento sin firma cuando public key existe es rechazado
- `WORKSPACE_CREATED` forjado es rechazado
- Evento de miembro revocado es rechazado
- Cada `EventTypes` branch en el switch
- Evento con tipo desconocido de miembro autorizado

---

### 9.2 Tests para `AppConfig`

**Archivo a crear:** `p2p-client/src/test/java/org/q3s/p2p/client/AppConfigTest.java`

Tests prioritarios:
- Precedencia: system property > archivo > default
- `getInt` con valor inválido retorna default
- `getBoolean` con string vacío
- Load de archivo properties inexistente (fallback correcto)

---

### 9.3 Tests para `I18n`

**Archivo a crear:** `p2p-client/src/test/java/org/q3s/p2p/client/util/I18nTest.java`

Tests prioritarios:
- Carga de bundle español
- Carga de bundle inglés
- Key inexistente retorna fallback
- Cambio de locale funciona
- Paridad de keys entre `messages.properties` y `messages_es.properties`

---

### 9.4 Tests para `FileUtils`

**Archivo a crear:** `p2p-client/src/test/java/org/q3s/p2p/client/util/FileUtilsTest.java`

Tests prioritarios (usar `@TempDir`):
- Listar archivos de directorio
- `remove` recursivo
- `move` de archivo

---

### 9.5 Tests para `CoreEventCodec` roundtrip de whiteboard

**Archivo existente:** `p2p-client/src/test/java/org/q3s/p2p/core/CoreQfolderTest.java` (o nuevo test dedicado)

Test: Crear stroke event con `List<int[]>` points → `toJson()` → `eventFromJson()` → verificar que `points` sigue siendo `List<int[]>`.

---

### 9.6 Fix `ClipboardImagePerformanceTest` — path hardcodeado

**Archivo:** `p2p-client/src/test/java/org/q3s/p2p/client/view/ClipboardImagePerformanceTest.java` (línea 27)

**Problema:** Path hardcodeado `/home/tiul/full_after_misfire.png`.

**Corrección:** Usar `@TempDir` y generar una imagen de test programáticamente:
```java
@TempDir
Path tempDir;

private Path testImagePath;

@BeforeEach
void setup() throws IOException {
    BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
    testImagePath = tempDir.resolve("test_image.png");
    javax.imageio.ImageIO.write(img, "png", testImagePath.toFile());
}
```

---

### 9.7 Fix `caso25` test misleading

**Archivo:** `p2p-client/src/test/java/org/q3s/p2p/core/CoreQfolderTest.java` (líneas 355–363)

**Problema:** Nombre dice "evento de no aprobado no modifica estado" pero assertion espera `chatMessages().size() == 1` (el mensaje SÍ aparece, porque se bypasea validación con `store.append` directo).

**Corrección:** Renombrar el test o cambiar para usar `receiveRemoteEvent` (que valida):
```java
@Test void caso25eventoDeNoAprobadoEsRechazadoPorValidacion() {
    // ... setup ...
    boolean accepted = core.receiveRemoteEvent(msg);
    assertFalse(accepted);
    assertEquals(0, WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId())).chatMessages().size());
}
```

---

## Fase 10: Build, Packaging y Configuración

### 10.1 Sincronizar versiones

**Archivos:**
- `p2p-client/pom.xml` (línea 10): `1.0-SNAPSHOT`
- `UpdateChecker.java` (línea 26): `1.0.7`
- `build.sh` (línea 34): hardcoded `p2p-client-1.0-SNAPSHOT-fat.jar`

**Corrección:**
1. Definir la versión una sola vez en `pom.xml`
2. `UpdateChecker` la lee desde manifest/pom.properties (ver tarea 6.4)
3. `build.sh`: parametrizar el nombre del artefacto:
```bash
VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec -f "$CLIENT_DIR/pom.xml" 2>/dev/null || echo "1.0-SNAPSHOT")
cp "$CLIENT_DIR/target/p2p-client-${VERSION}-fat.jar" "$DIST_DIR/qfolder.jar"
```

---

### 10.2 Ejecutar SpotBugs en CI

**Archivo:** `.github/workflows/maven-publish.yml` (línea 29)

**Corrección:**
```yaml
- name: Build and test with Maven
  run: mvn -B -Pstatic-analysis verify --file p2p-client/pom.xml
```

---

### 10.3 Actualizar `maven-compiler-plugin`

**Archivo:** `p2p-client/pom.xml` (línea 16)

**Corrección:** Actualizar a la última versión estable:
```xml
<compiler-plugin.version>3.13.0</compiler-plugin.version>
```

Y considerar usar `<release>21</release>` en lugar de `<source>`/`<target>`.

---

### 10.4 Completar `qfolder.properties.example`

**Archivo:** `p2p-client/src/main/resources/qfolder.properties.example`

**Agregar las keys documentadas que faltan:**
```properties
# User name (default: system user)
#qfolder.user.name=MyName

# Shared directory (default: ~/qfolder)
#qfolder.shared.dir=~/qfolder

# Mock tunnel delay in milliseconds (default: 3000)
#qfolder.tunnel.mock.delay=3000

# Language: es, en (default: system locale)
#qfolder.language=es

# Look and Feel: flat-light, flat-dark, flat-intellij, flat-darcula, metal, nimbus, system
#qfolder.lookAndFeel=flat-light

# Verbose UI logging (default: false)
#qfolder.ui.verbose=false

# Custom cloudflared path
#qfolder.cloudflared.path=/usr/local/bin/cloudflared
```

---

### 10.5 Habilitar tests por defecto en `build.sh`

**Archivo:** `build.sh` (líneas 27–31)

**Corrección:** Invertir el default para que los tests corran a menos que se pida lo contrario:
```bash
if [ "${QFOLDER_SKIP_TESTS:-false}" = "true" ]; then
    mvn clean package -DskipTests -q
else
    mvn clean package -q
fi
```

---

## Fase 11: Mejoras Arquitectónicas

> Estas son mejoras de mayor alcance que requieren planificación adicional. Se listan como referencia para futuros sprints.

### 11.1 Pipeline unificado de eventos

**Estado actual:** Múltiples paths para append (local → `store.append`, remoto → `EventService.accept`, sync → `SyncEngine.receiveEvent`, mesh → `P2PNetworkAdapter`).

**Propuesta:** Crear `EventPipeline` como único punto de entrada:
```java
interface EventPipeline {
    AppendResult appendLocal(Event draft);
    boolean acceptRemote(Event event);
}
```

Beneficios: stamping, validación, métricas y broadcast consistentes.

---

### 11.2 Separar proyecciones de estado

**Estado actual:** `WorkspaceStateBuilder` mezcla membresía, contenido, mesh y archivos en un switch de 160 líneas.

**Propuesta:** Separar en:
- `MembershipProjector`
- `ContentProjector` (chat, notas, archivos, whiteboard)
- `MeshProjector` (peer status)

---

### 11.3 Hacer `PEER_STATUS_UPDATED` efímero

**Estado actual:** Cada cambio de malla genera un evento durable. Sesiones largas generan historial sin límite.

**Propuesta:** Marcar como efímero (como `USER_TYPING`) o almacenar en cache separada.

---

### 11.4 Integrar `SnapshotService` en startup

**Estado actual:** Existe y está testeado, pero el startup siempre reconstruye desde todos los eventos.

**Propuesta:** Cargar último snapshot + replay delta desde eventos posteriores.

---

### 11.5 CRDT para notas colaborativas

**Estado actual:** Operaciones posicionales sin IDs de operación ni transformación. Ediciones concurrentes pueden divergir permanentemente.

**Propuesta a largo plazo:** Implementar un CRDT de texto (e.g., RGA o YATA) o usar operational transformation.

---

### 11.6 Optimizar `EventValidator` — eliminar O(n²) en bulk sync

**Estado actual:** Cada validación de evento remoto reconstruye el estado completo desde todos los eventos.

**Propuesta:** Cachear workspace state en `EventValidator` y actualizar incrementalmente:
```java
WorkspaceState state = cachedState.getOrBuild(workspaceId, () -> 
    WorkspaceStateBuilder.fromEvents(store.listEvents(workspaceId)));
```

---

### 11.7 Auto-reconnect en mesh tras fallo silencioso

**Estado actual:** Cuando `PeerLink` se desconecta, el entry se remueve pero no se intenta reconectar automáticamente hasta el siguiente evento de discovery/status.

**Propuesta:** Agregar health check periódico o reintento automático en `P2PMeshService` cuando un peer desaparece pero sigue en el catálogo.

---

### 11.8 Consolidar modelo dual de `Event`

**Estado actual:** Dos clases `Event` (`org.q3s.p2p.core.model.Event` y `org.q3s.p2p.model.Event`) con dos stacks de serialización distintos.

**Propuesta:** Unificar gradualmente hacia el modelo core, adaptando los wire protocols.

---

## Resumen de Prioridades

| Prioridad | Tareas | Estimación |
|---|---|---|
| **P0 — Crítico** | 1.1–1.7 | 1–2 días |
| **P1 — Alta** | 2.1–2.7, 3.1–3.7, 4.1–4.6 | 2–3 días |
| **P2 — Media** | 5.1–5.8, 6.1–6.7 | 2 días |
| **P3 — Limpieza** | 7.1–7.7 | 0.5 día |
| **P4 — i18n** | 8.1–8.9 | 2–3 días |
| **P5 — Tests** | 9.1–9.7 | 2–3 días |
| **P6 — Build** | 10.1–10.5 | 0.5 día |
| **P7 — Arquitectura** | 11.1–11.8 | Planificar por separado |

**Total estimado (P0–P6):** ~10–14 días de trabajo.

---

## Notas para el LLM Ejecutor

1. **Orden de ejecución:** Seguir las fases en orden (P0 primero, luego P1, etc.). Dentro de cada fase, las tareas pueden hacerse en cualquier orden.
2. **Validación:** Después de cada fase, ejecutar `cd p2p-client && mvn test`. Si algún test falla, corregir antes de avanzar.
3. **Build final:** Al terminar cada fase, ejecutar `./build.sh` para verificar que el fat JAR se genera correctamente.
4. **No refactorizar `Controller`:** Salvo las correcciones específicas listadas, no hacer refactors grandes de `Controller.java`.
5. **Archivos a revisar antes de cambios:** Ver sección "Archivos A Revisar" en `AGENTS.md`.
6. **i18n:** Al agregar keys nuevas, agregarlas a los 3 bundles (`messages.properties`, `messages_es.properties`, `messages_en.properties` si se mantiene).
7. **Commits:** Un commit por fase completada, con mensaje descriptivo.
