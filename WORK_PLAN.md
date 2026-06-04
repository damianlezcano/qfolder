# Plan de Trabajo — qfolder

Plan revisado el 3 de junio de 2026. **302 tests (default), 347 total, 0 failures, BUILD SUCCESS.**

> **Nota para el LLM ejecutor:** Antes de cada cambio, leer el archivo completo (o la sección relevante) para confirmar que las líneas coinciden. Después de cada grupo de cambios, ejecutar `cd p2p-client && mvn test`. Ejecutar `./build.sh` al final.

## Estado general

Se implementó exitosamente una cantidad muy significativa de trabajo:
- BUG-1 a BUG-6: todos corregidos
- RESIDUAL-1, 3, 5, 6, 7: completados
- PENDIENTE-1 a 10: completados
- PENDIENTE-12 (SecureIdentityStore): completado
- PENDIENTE-15 (tests filesystem): completado
- PENDIENTE-17 (PerformanceMetrics): completado
- PENDIENTE-18 (Javadoc): completado
- PENDIENTE-19 (@Tag performance): completado
- PENDIENTE-20 (SLF4J/Shade): completado
- PENDIENTE-21 (smoke E2E): completado
- FASE11-FIX-1: documentado como futuro (correcto)
- FASE11-FIX-2: completado
- DOC-1: mayormente completado

La revisión detectó **4 issues pendientes** (2 de integración, 1 parcial, 1 documental) y **1 issue nuevo de alta prioridad**.

---

## Pendientes activos

| ID | Tarea | Severidad | Estado |
|---|---|---|---|
| NEW-1 | `clearLiveCache` no se ejecuta en flujo de join (solo create) | **Media** | ⏳ Pendiente |
| NEW-2 | `file.shared` via sync batch no dispara ChunkReplicator | **Media** | ⏳ Pendiente |
| NEW-3 | AGENTS.md test count desactualizado (dice ~279, real es 302/347) | **Baja** | ⏳ Pendiente |
| PENDIENTE-11 | Refactorización Controller.java (parcial: NotesEditor extraído, no wireado) | Alta | 🟡 Parcial |
| PENDIENTE-13 | Tests unitarios adapters/network (parcial: InviteCodeTest + CoreChunkTransferProtocolTest) | Media | 🟡 Parcial |
| PENDIENTE-14 | Tests unitarios client/hub y client/ws | Media | ⏳ Pendiente |
| PENDIENTE-16 | Unificación ramas CI/release | Baja | ⛔ Bloqueado (requiere decisión usuario) |

### Completados (verificados en código)

| ID | Evidencia |
|---|---|
| BUG-1 a BUG-6 | Fixes verificados + tests de regresión (bug1..bug6 en CoreQfolderTest) |
| RESIDUAL-1 | `clearLiveCache(wk.getId())` en `removeAllTab()` línea 4613 |
| RESIDUAL-2 | Javadoc documenta semántica line-based; `length` ignorado por diseño |
| RESIDUAL-3 | Tests `bug3remoteEventServiceEsCompartidoEntreLlamadas` y `bug6eventsSinceSnapshotEsAtomicInteger` |
| RESIDUAL-4 | `processEvents` en callback `onCoreEventStored` (Controller línea 1868) — parcial, ver NEW-2 |
| RESIDUAL-5 | AGENTS.md línea 142 (SnapshotService), línea 215/223 (ChunkReplicator) actualizados |
| RESIDUAL-6 | Renombrado a `memberJoinApprovalEsRechazadoSiApproverNoEsAutorizado` + `memberJoinApprovalConFirmaInvalidaEsRechazado` |
| RESIDUAL-7 | `System.err.println` reemplazado por `java.util.logging.Logger` en EmbeddedWebSocketServer, UpdateChecker, AppConfig |
| PENDIENTE-12 | `SecureIdentityStore` con AES-256-GCM; `SecureIdentityStoreTest` (7 tests) |
| PENDIENTE-15 | `QfolderLayoutTest` (12), `FileSystemEventStoreTest` (9), `FileSystemFileChunkStoreTest` (8) |
| PENDIENTE-17 | `PerformanceMetrics` + `PerformanceMetricsTest` (8) + wired en publish/receive/chunk |
| PENDIENTE-18 | Javadoc en CoreApplicationService, EventStore, NetworkAdapter, FileChunkStore, AuthProvider |
| PENDIENTE-19 | `@Tag("performance")` en CoreResilienceTest + BackendExtendedSimulation; `-Pperformance-tests` |
| PENDIENTE-20 | `slf4j-nop:2.0.13` agregado; `module-info.class` excluido en shade filters |
| PENDIENTE-21 | `scripts/smoke-e2e-mock.sh` headless con 13 checks |

---

## Detalle de pendientes activos

### NEW-1: `clearLiveCache` no se ejecuta en flujo de join

**Severidad:** Media
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`

**Problema:** RESIDUAL-1 fue implementado: `removeAllTab()` llama `MeshProjector.clearLiveCache(wk.getId())` en línea 4613. Sin embargo, `wk` (el objeto `Workspace`) solo se asigna en el flujo de **crear** workspace (`jButton4ActionPerformed`). Cuando un usuario se **une** a un workspace (join), `wk` nunca se asigna, por lo que `wk != null` es `false` y `clearLiveCache` **nunca se ejecuta** al desconectar tras un join.

**Código actual (Controller.java ~4612):**
```java
if (wk != null) {
    try { org.q3s.p2p.core.state.MeshProjector.clearLiveCache(wk.getId()); } catch (Exception ignored) {}
}
```

**Corrección:** Usar `core.currentWorkspaceId()` en lugar de `wk.getId()`, ya que `core` siempre tiene el workspace ID activo independientemente de si fue create o join:

```java
try {
    core.currentWorkspaceId().ifPresent(wsId ->
        org.q3s.p2p.core.state.MeshProjector.clearLiveCache(wsId)
    );
} catch (Exception ignored) {}
```

Esto funciona tanto para create como para join. Eliminar el check `if (wk != null)` ya que `core.currentWorkspaceId()` retorna `Optional.empty()` si no hay workspace activo.

**Test sugerido:** Verificar que `clearLiveCache` se ejecuta al desconectar después de un join (no solo create).

**Estimación:** 5 min

---

### NEW-2: `file.shared` via sync batch no dispara ChunkReplicator

**Severidad:** Media
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`

**Problema:** RESIDUAL-4 fue parcialmente implementado: `processEvents` se llama en el callback `p2pNetwork.onCoreEventStored()` (línea ~1868), que cubre eventos individuales recibidos por gossip P2P. Pero cuando eventos llegan via **sync batch** (`SyncEngine.applyReceivedEvents`), el callback `onCoreSyncApplied` **no** invoca `processEvents` para cada `file.shared`. Esto significa que archivos compartidos que solo llegan durante el sync inicial (join) no disparan replicación automática — solo se cubren por `runStartupCache()` si este ya corrió.

**Impacto:** Si un archivo se comparte justo antes de que un peer se una y llega via sync batch (no gossip individual), el replicator no lo detecta incrementalmente. `runStartupCache()` en `activateWorkspaceFromCore()` cubre el caso startup, pero hay una ventana de tiempo entre sync y startup cache donde el archivo podría perderse.

**Corrección:** En el handler de sync responses (buscar `acceptCoreSyncOnDirect` o `onCoreSyncApplied` en Controller), después de aplicar los eventos de sync, filtrar los `file.shared` y pasarlos al replicator:

```java
// Después de procesar sync batch:
List<Event> fileEvents = syncEvents.stream()
    .filter(e -> EventTypes.FILE_SHARED.equals(e.type()))
    .toList();
if (!fileEvents.isEmpty()) {
    try {
        core.chunkReplicator().processEvents(wsId, fileEvents);
    } catch (Exception ex) {
        log.debug("ChunkReplicator sync batch: " + ex.getMessage());
    }
}
```

Alternativamente, dado que `runStartupCache()` ya corre al activar workspace y procesa `processCurrentState()` (que lee todos los eventos del store), este gap solo afecta la ventana temporal entre sync completado y activación. Si la activación ocurre inmediatamente después del sync, el impacto es muy bajo.

**Estimación:** 15 min

---

### NEW-3: AGENTS.md test count desactualizado

**Severidad:** Baja
**Archivo:** `AGENTS.md`

**Problema:** AGENTS.md probablemente dice ~279 tests, pero el conteo real actual es:
- **302 tests** en suite default (`mvn test`, excluye `@Tag("performance")`)
- **347 tests** en total (incluyendo 45 de performance)
- **23 test files** (no 15)

**Corrección:** Buscar la línea que menciona el conteo de tests y actualizarla a:

```
- La suite core se ejecuta con `cd p2p-client && mvn test` (302 tests default, 0 failures; 347 total con `-Pperformance-tests`).
```

Verificar también que la lista de test files mencionados incluya los nuevos:
- `SecureIdentityStoreTest`, `NotesEditorTest`, `PerformanceMetricsTest`
- `QfolderLayoutTest`, `FileSystemEventStoreTest`, `FileSystemFileChunkStoreTest`
- `InviteCodeTest`, `CoreChunkTransferProtocolTest`

**Estimación:** 5 min

---

### PENDIENTE-11: Refactorización Controller.java (parcial)

**Estado:** `NotesEditor` fue extraído como clase separada con 5 tests (`NotesEditorTest`), pero **no está wireado** al Controller. El Controller sigue usando su propia lógica inline de notas. No se crearon `UIController`, `NetworkController`, `FileController`, etc.

**Siguiente paso:** Si se desea avanzar, integrar `NotesEditor` en Controller reemplazando la lógica inline de notas. Luego continuar con la extracción de otros controllers (chat, whiteboard, etc.) de forma incremental.

**Riesgo:** Alto. No se recomienda sin tests de integración completos.

---

### PENDIENTE-13: Tests unitarios adapters/network (parcial)

**Estado:** Se agregaron `InviteCodeTest` (7 tests) y `CoreChunkTransferProtocolTest` (10 tests). Faltan:
- `P2PNetworkAdapterTest` — routing, timeouts, ephemeral bypass
- `P2PMeshServiceTest` — formación de mesh, reconexión, failover
- `DirectBootstrapTest` — join exitoso/fallido, retry, cleanup

---

### PENDIENTE-14: Tests unitarios client/hub y client/ws

**Estado:** Sin cambios. `CoreWsClientTest` sigue siendo la única cobertura parcial. Faltan:
- `EmbeddedWebSocketServerTest`
- `CloudflareTunnelTest`
- `WsClientTest` dedicado (o ampliación de CoreWsClientTest)

---

## Tests verificados

```
cd p2p-client && mvn test
  302 tests, 0 failures, 0 errors — BUILD SUCCESS (9s)

cd p2p-client && mvn -Pperformance-tests test
  347 tests total (incluye 45 performance)
```

Test files (23):

| File | Tests |
|---|---|
| CoreQfolderTest | 125 |
| CoreResilienceTest | 35 |
| CoreArchitectureTest | 24 |
| CoreControllerIntegrationTest | 23 |
| QfolderLayoutTest | 12 |
| BackendExtendedSimulationTest | 10 |
| CoreChunkTransferProtocolTest | 10 |
| EventValidatorTest | 10 |
| CoreWsClientTest | 9 |
| FileSystemEventStoreTest | 9 |
| FileSystemFileChunkStoreTest | 8 |
| PerformanceMetricsTest | 8 |
| ChunkReplicatorTest | 7 |
| CoreWebSocketIntegrationTest | 7 |
| InviteCodeTest | 7 |
| SecureIdentityStoreTest | 7 |
| AppConfigTest | 6 |
| ClipboardImagePerformanceTest | 6 |
| EventPipelineTest | 6 |
| I18nTest | 6 |
| NotesEditorTest | 5 |
| FileTableModelI18nTest | 4 |
| FileUtilsTest | 3 |

---

## Orden de ejecución recomendado

### Fase A — Fixes rápidos (~25 min)
1. **NEW-1** — `clearLiveCache` usar `core.currentWorkspaceId()` (5 min)
2. **NEW-3** — AGENTS.md test count (5 min)
3. **NEW-2** — ChunkReplicator sync batch (15 min, opcional si `runStartupCache` cubre)
4. Ejecutar `cd p2p-client && mvn test`

### Fase B — Tests faltantes (si se desea cobertura completa)
5. **PENDIENTE-13** — P2PNetworkAdapterTest, P2PMeshServiceTest, DirectBootstrapTest
6. **PENDIENTE-14** — EmbeddedWebSocketServerTest, CloudflareTunnelTest, WsClientTest

### Fase C — Refactoring (solo si hay scope)
7. **PENDIENTE-11** — Integrar NotesEditor, luego extraer más controllers

**Nota:** PENDIENTE-16 (unificación CI/release) sigue bloqueado — requiere decisión explícita del usuario sobre estrategia de ramas.

---

# Propuestas de Mejora — Performance, Estabilidad, Seguridad y Usabilidad

Análisis profundo realizado el 3 de junio de 2026 sobre el código actual. Cada propuesta incluye severidad, archivos a modificar y pasos de implementación detallados para otra LLM.

## PERFORMANCE

### PERF-1: Cache de WorkspaceState — evitar rebuild O(N) en cada acceso

**Severidad:** Crítica
**Impacto:** `currentState()` se llama ~2 veces por cada evento efímero, 1 vez por cada evento persistente, en cada keystroke de notas, en cada lookup de chunks, etc. Cada llamada lee todos los archivos `.evt` del disco y replay todos los eventos por 3 projectors. Con 1000+ eventos, cada acceso tarda cientos de milisegundos.

**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/state/WorkspaceStateBuilder.java`

**Implementación:**

1. Agregar campo cache en `CoreApplicationService`:
```java
private volatile WorkspaceState cachedState;
private final java.util.concurrent.atomic.AtomicLong stateVersion = new AtomicLong(0);
```

2. En `currentState()`, retornar cache si es válido:
```java
public WorkspaceState currentState() {
    requireWorkspace();
    WorkspaceState cached = this.cachedState;
    if (cached != null) return cached;
    WorkspaceState state = (snapshotPath != null)
        ? currentStateWithSnapshot(snapshotPath)
        : WorkspaceStateBuilder.fromEvents(eventStore.listEvents(currentWorkspaceId));
    this.cachedState = state;
    return state;
}
```

3. Invalidar cache en TODOS los puntos donde se persisten eventos:
```java
private void invalidateStateCache() {
    this.cachedState = null;
    stateVersion.incrementAndGet();
}
```
   Llamar `invalidateStateCache()` en:
   - `afterLocalEvent()` (~línea 439): después de `maybeSaveSnapshot`, agregar `invalidateStateCache()`.
   - `receiveRemoteEvent()` (~línea 312): después de `remoteEventService.accept(event)` exitoso, antes de `return accepted`. Agregar `if (accepted) invalidateStateCache()`.
   - `receiveRemoteEvents()` (~línea 317): este ya llama `receiveRemoteEvent` en loop, así que se invalida por cada evento individual. Pero considerar invalidar UNA sola vez al final del batch para eficiencia.
   - `initializeFromExisting()` (~línea 133): al setear workspace, invalidar por si había cache de workspace anterior.
   - `initializeCoreSession()` (~línea 108): al crear workspace, invalidar.

4. Para efímeros, aplicar sobre el cache sin rebuild:
```java
if (event.isEphemeral()) {
    WorkspaceState state = currentState(); // usa cache
    new MeshProjector().apply(state, event);
    return true;
}
```

5. Agregar método `currentStateVersion()` para que callers puedan evitar trabajo si no cambió.

**Precauciones:** `WorkspaceState` contiene maps mutables. Si callers modifican el estado retornado, pueden corromper el cache. Considerar defensive copy o hacer `WorkspaceState` inmutable.

**Test:** Verificar que `currentState()` retorna el mismo objeto entre llamadas si no hubo eventos nuevos.

**Estimación:** 45 min

---

### PERF-2: Mover file indexing fuera del EDT

**Severidad:** Crítica
**Impacto:** `indexFilesInCore()` lee cada archivo completo para SHA-256, luego `core.shareFile()` lee el archivo completo otra vez (via `Files.readAllBytes`). Todo corre en el EDT, congelando la UI.

**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` — `indexFilesInCore()`, `refreshLocalFilesAndNotify()`

**Implementación:**

1. Crear wrapper async en Controller:
```java
private void indexFilesInCoreAsync(List<QFile> files, String baseDir) {
    new Thread(() -> {
        indexFilesInCore(files, baseDir);
        SwingUtilities.invokeLater(() -> {
            refreshArchivosTable();
            refreshTables();
        });
    }, "file-indexer").start();
}
```

2. En `refreshLocalFilesAndNotify()`, reemplazar `indexFilesInCore(files, baseDir)` por `indexFilesInCoreAsync(files, baseDir)`.

3. Mover `saveIndexedCoreFilesCache()` fuera del loop — guardar una sola vez al final del batch, no después de cada archivo.

4. Evitar doble lectura: `core.shareFile(path)` ya calcula hash y chunks. Eliminar el `sha256File()` previo y usar el hash del core como clave de dedup.

**Precauciones:** El acceso a `user.setFiles()` y refresh de tabla debe sincronizarse con el EDT. El hilo de indexación no debe acceder a componentes Swing directamente.

**Estimación:** 30 min

---

### PERF-3: Debounce de `applyCoreStateToVisuals`

**Severidad:** Alta
**Impacto:** Cada evento P2P (chat, nota, whiteboard, peer status) dispara rebuild completo del state + refresh de TODOS los tabs. Un burst de 50 mensajes de chat causa 50 rebuilds + 50 refreshes de tabla de archivos, whiteboard, notas, etc.

**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PMeshService.java`

**Implementación:**

1. Agregar campo debounce en Controller:
```java
private final javax.swing.Timer visualRefreshDebounce = new javax.swing.Timer(100, e -> {
    applyCoreStateToVisualsNow(core.currentState());
});
{ visualRefreshDebounce.setRepeats(false); }
```

2. Crear wrapper que coalesce llamadas:
```java
private void scheduleVisualRefresh() {
    visualRefreshDebounce.restart(); // cada nueva llamada resetea el timer de 100ms
}
```

3. Reemplazar las llamadas directas a `applyCoreStateToVisuals(core.currentState())` en `P2PMeshService` callbacks por `scheduleVisualRefresh()`.

4. Detener el timer en `shutdown()`.

**Resultado:** Bajo burst, solo 1 refresh ocurre 100ms después del último evento.

**Estimación:** 20 min

---

### PERF-4: `FileSystemEventStore.listEventsAfter` — no leer todos los eventos

**Severidad:** Alta
**Impacto:** El método default en `EventStore` interface llama `listEvents()` (lee TODOS los archivos del disco), luego filtra en memoria. Con snapshots, solo necesitamos los eventos recientes.

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/adapters/filesystem/FileSystemEventStore.java`

**Implementación:**

1. Override `listEventsAfter` en `FileSystemEventStore`:
```java
@Override
public List<Event> listEventsAfter(String workspaceId, Instant after) {
    if (after == null) return listEvents(workspaceId);
    Path dir = eventsDir(workspaceId);
    if (!Files.isDirectory(dir)) return List.of();
    Instant threshold = after.minusMillis(1);
    try (var files = Files.list(dir)) {
        return files
            .filter(f -> f.toString().endsWith(".evt"))
            .filter(f -> fileModifiedAfter(f, threshold))
            .sorted()
            .map(this::readEvent)
            .filter(e -> e != null && e.createdAt() != null && !e.createdAt().isBefore(threshold))
            .toList();
    }
}

private boolean fileModifiedAfter(Path f, Instant threshold) {
    try {
        return Files.getLastModifiedTime(f).toInstant().isAfter(threshold.minusSeconds(60));
    } catch (Exception e) { return true; }
}
```

2. Esto filtra por fecha de archivo antes de leer contenido, reduciendo I/O drasticamente.

**Estimación:** 20 min

---

## ESTABILIDAD

### STAB-1: Limitar tamaño de payloads sync — paginación

**Severidad:** Crítica
**Impacto:** Sync request envía TODOS los event IDs conocidos. Sync response envía TODOS los eventos faltantes en un solo mensaje WebSocket. Con 1000+ eventos, los frames pueden ser de varios MB, causando OOM o timeout.

**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PNetworkAdapter.java` — `sendSyncRequest()`, `handleIncomingSyncRequest()`
- `p2p-client/src/main/java/org/q3s/p2p/core/codec/CoreEnvelopeCodec.java`

**Implementación:**

1. Definir constante `MAX_SYNC_BATCH = 200` en `P2PNetworkAdapter`.

2. En `handleIncomingSyncRequest()`, paginar la respuesta:
```java
List<Event> missing = store.getMissingEvents(wsId, knownIds);
for (int i = 0; i < missing.size(); i += MAX_SYNC_BATCH) {
    List<Event> batch = missing.subList(i, Math.min(i + MAX_SYNC_BATCH, missing.size()));
    CoreEnvelope response = CoreEnvelope.of(
        CoreEnvelopeCodec.CORE_SYNC_RESPONSE_NAME, localPeerId,
        CoreEnvelopeCodec.encodeSyncPayload(batch));
    link.send(response);
}
```

3. En `handleIncomingSyncResponse()`, acumular los eventos de múltiples batches antes de notificar.

4. En `Controller.sendDirectCoreSyncSnapshot()`, paginar también:
```java
List<Event> allEvents = core.events();
for (int i = 0; i < allEvents.size(); i += 200) {
    // enviar batch
}
```

**Precauciones:** El receptor debe aceptar múltiples sync responses del mismo peer sin confusión. Agregar un sequence number o flag `final=true` en el último batch.

**Estimación:** 45 min

---

### STAB-2: Shutdown completo y ordenado

**Severidad:** Alta
**Impacto:** `Controller.shutdown()` no cierra chunk coordinator, timers, ni conexiones directas. Threads y conexiones zombie persisten.

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` — método `shutdown()`

**Implementación:**

1. Reemplazar el `Thread.sleep(350)` por un shutdown ordenado:
```java
public void shutdown() {
    // 1. Detener timers
    if (visualRefreshDebounce != null) visualRefreshDebounce.stop();
    if (notesSyncTimer != null) notesSyncTimer.stop();
    if (joinTimeoutTimer != null) { joinTimeoutTimer.stop(); joinTimeoutTimer = null; }

    // 2. Detener chunk coordinator
    if (coreChunkTransfer != null) coreChunkTransfer.shutdown();

    // 3. Desconectar mesh P2P
    if (p2pMesh != null) p2pMesh.disconnectAll();

    // 4. Cerrar conexiones directas
    for (WebSocket ws : directPeerConnections.values()) {
        try { ws.close(); } catch (Exception ignored) {}
    }
    directPeerConnections.clear();

    // 5. Limpiar mesh cache
    try { core.currentWorkspaceId().ifPresent(MeshProjector::clearLiveCache); } catch (Exception ignored) {}

    // 6. Detener tunnel
    if (cloudflareTunnel != null) cloudflareTunnel.stop();

    // 7. Detener WebSocket server
    if (wsServer != null) wsServer.shutdown();

    // 8. Cerrar WsClient legacy
    if (wsClient != null) { try { wsClient.close(); } catch (Exception ignored) {} }

    // 9. Flush y cerrar cola de envíos
    outboundEventQueue.shutdown();
    try { outboundEventQueue.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
}
```

2. Actualizar shutdown hook para incluir `saveSessionHistory`:
```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    try {
        if (p2pMesh != null) p2pMesh.disconnectAll();
        saveSessionHistory("shutdown-hook");
    } catch (Exception ignored) {}
}, "shutdown-hook"));
```

**Estimación:** 20 min

---

### STAB-3: Preservar catálogo de peers en disconnect transitorio

**Severidad:** Media
**Impacto:** `peerDisappeared()` elimina al peer del catálogo. Si el peer reconecta 5 segundos después, necesita ser redescubierto via gossip. Bajo Cloudflare flap, un peer puede salir y volver del mesh repetidamente.

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PMeshService.java`

**Implementación:**

1. En `peerDisappeared()`, NO borrar del catálogo; solo marcar como desconectado:
```java
public void peerDisappeared(String peerId) {
    if (peerId == null || shuttingDown) return;
    // No eliminar del catálogo — permitir auto-reconnect
    p2p.disconnectFrom(peerId);
    scheduleAutoReconnect(); // intentará reconectar usando la URL del catálogo
    // ...
}
```

2. Agregar TTL para entradas del catálogo — si después de 5 minutos no reconecta, recién limpiar.

**Estimación:** 15 min

---

## SEGURIDAD

### SEC-1: Validar membership antes de responder sync y chunks

**Severidad:** Crítica
**Impacto:** Cualquier persona con una URL de invite puede conectarse al WebSocket, enviar un `core.sync.request` y recibir TODO el historial del workspace (chat, notas, metadata de archivos). Luego puede solicitar chunks para descargar los archivos completos. No se verifica si el peer está autorizado.

**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` — `respondCoreSyncOnDirect()`, `handleDirectPeerEvent()`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PNetworkAdapter.java` — `handleIncomingSyncRequest()`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/CoreChunkTransferCoordinator.java` — `sendChunk()`

**Implementación:**

1. En `respondCoreSyncOnDirect()` (~línea 1080), verificar membership antes de enviar sync. **Nota:** este método usa `wk != null ? wk.getId() : null` para obtener el workspace ID — puede ser null en join (mismo issue que NEW-1). Usar `core.currentWorkspaceId()` en su lugar:
```java
private void respondCoreSyncOnDirect(CoreEnvelope envelope, WebSocket conn) {
    try {
        if (envelope.userId() == null || envelope.userId().equals(user.getId())) return;
        String wsId = core.currentWorkspaceId().orElse(null);
        if (wsId == null) return;
        // Nuevo: verificar membership
        WorkspaceState state = core.currentState();
        if (!state.isAuthorized(envelope.userId())) {
            log.debug("Sync request rechazado: peer no autorizado " + envelope.userId());
            return;
        }
        // ... continuar con sync normal (decodeKnownEventIds, missingEvents, send)
    } catch (Exception e) {
        log.debug("Error respondCoreSyncOnDirect: " + e.getMessage());
    }
}
```
**Importante:** `isAuthorized(String memberId)` en `WorkspaceState` verifica en `authorizedMembers` map. El `envelope.userId()` debe corresponder al `memberId` del peer. Verificar que los peers se registran con su user ID como member ID (este es el patrón actual en `initializeCoreSession`).

2. En `P2PNetworkAdapter.handleIncomingSyncRequest()`, aplicar la misma verificación.

3. En `CoreChunkTransferCoordinator.sendChunk(CoreEnvelope event, WebSocket directConn)`, verificar membership. El ID del solicitante está en `event.userId()`:
```java
private void sendChunk(CoreEnvelope event, WebSocket directConn) {
    try {
        if (event.userId() == null) return;
        // Verificar membership antes de enviar datos
        WorkspaceState state = core.currentState();
        if (!state.isAuthorized(event.userId())) {
            debug.accept("[CHUNK] Chunk request rechazado: peer no autorizado", event.userId());
            return;
        }
        // ... resto del código existente (parse request, read chunk, send response)
```
**Nota:** `CoreChunkTransferCoordinator` no tiene referencia directa a `core`. Inyectar un `Supplier<WorkspaceState>` en el constructor, o pasar un `Predicate<String> isAuthorized` como parámetro funcional.

**Precauciones:** Los peers en proceso de join (pre-aprobación) necesitan recibir al menos el evento de join request y la respuesta de aprobación. Considerar un "handshake mínimo" vs sync completo.

**Estimación:** 30 min

---

### SEC-2: Validar eventos efímeros (firma o membership check)

**Severidad:** Crítica
**Impacto:** Los eventos efímeros como `PEER_STATUS_UPDATED` no se validan. Un atacante puede inyectar un `peer.status.updated` con el `member_id` de cualquier peer legítimo y redirigir su `peer_url` al endpoint del atacante. Otros peers conectarán al host malicioso.

**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java` — `receiveRemoteEvent()` branch efímero
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PMeshService.java` — `applyEphemeralState()`

**Implementación:**

1. En `receiveRemoteEvent()`, verificar que el autor del efímero es miembro autorizado:
```java
if (event.isEphemeral()) {
    WorkspaceState state = currentState();
    if (!state.isAuthorized(event.authorMemberId())) {
        return false; // rechazar efímeros de no-miembros
    }
    new MeshProjector().apply(state, event);
    return true;
}
```

2. Opcionalmente, firmar eventos efímeros también (requiere cambio en `EventFactory` para que stamp funcione con efímeros).

**Estimación:** 15 min

---

### SEC-3: Protección contra path traversal en downloads

**Severidad:** Alta
**Impacto:** Un miembro malicioso puede publicar un `file.shared` con `name: "../../.ssh/authorized_keys"`. Cuando otros peers descargan el archivo, se escribe fuera del directorio `files/`.

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` — `completeCoreChunkDownload()`, `completeFileDownload()`

**Implementación:**

1. Crear helper de sanitización:
```java
private String sanitizeFileName(String name) {
    if (name == null) return "unnamed";
    // Eliminar path traversal
    String safe = name.replace("..", "").replace("/", "_").replace("\\", "_");
    // Eliminar caracteres de control
    safe = safe.replaceAll("[\\x00-\\x1f]", "");
    if (safe.isBlank()) safe = "unnamed";
    return safe;
}
```

2. Aplicar en `completeCoreChunkDownload()` antes de construir el path:
```java
String safeName = sanitizeFileName(metadata.name());
String localPath = uniqueFilePath(getSessionFilesDir().getAbsolutePath() + File.separator + safeName);
```

3. Validar que el path resuelto está dentro del directorio esperado:
```java
Path target = Path.of(localPath).normalize();
Path base = getSessionFilesDir().toPath().normalize();
if (!target.startsWith(base)) {
    log.err("Path traversal detectado: " + metadata.name());
    return;
}
```

4. Aplicar lo mismo en `completeFileDownload()` y `openFile()`.

**Estimación:** 15 min

---

### SEC-4: Límite de tamaño de mensajes WebSocket

**Severidad:** Alta
**Impacto:** No hay límite en el tamaño de frames WebSocket. Un atacante puede enviar un frame de varios GB, causando OOM durante Base64 decode antes de cualquier validación.

**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/hub/EmbeddedWebSocketServer.java`

**Implementación:**

1. Usar `setMaxFrameSize` en el constructor del server (Java-WebSocket soporta esto — buscar en javadoc de la librería la constante correcta).

2. Si `setMaxFrameSize` no está disponible en la versión actual, validar en `onMessage`:
```java
@Override
public void onMessage(WebSocket conn, String message) {
    if (message.length() > 10_000_000) { // 10MB max
        err("Message too large: " + message.length() + " bytes, closing connection");
        conn.close(1009, "Message too large");
        return;
    }
    // ... procesamiento normal
}
```

3. Aplicar lo mismo en `WsClient` incoming messages.

**Estimación:** 10 min

---

## USABILIDAD

### UX-1: Feedback de conexión/reconexión visible

**Severidad:** Alta
**Impacto:** Cuando un peer se desconecta o reconecta, no hay notificación en chat ni indicador visual persistente. El usuario no sabe si sus mensajes están llegando a otros peers.

**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/main/resources/i18n/messages.properties` y `messages_es.properties`

**Implementación:**

1. En `handlePeerDisconnected()` (Controller.java ~línea 1107), agregar notificación dentro del `invokeLater` existente (~1111). El método existente para mensajes de sistema es `appendChatSystemMessage(String)`:
```java
private void handlePeerDisconnected(String peerId) {
    if (peerId == null || peerId.isBlank()) return;
    directPeerConnections.remove(peerId);
    if (p2pMesh != null) p2pMesh.peerDisappeared(peerId);
    javax.swing.SwingUtilities.invokeLater(() -> {
        User stored = knownMembers.get(peerId);
        if (stored != null) stored.setOnline(false);
        // Nuevo: notificación en chat
        String name = stored != null && stored.getName() != null ? stored.getName() : peerId;
        appendChatSystemMessage(I18n.get("chat.peerDisconnected", name));
        refreshMembersTable();
        refreshArchivosTable();
    });
    log.info("Peer desconectado del endpoint local: " + peerId);
}
```
**Nota:** Ya existen keys similares — `chat.userDisconnected` (línea 1464) y `chat.userReconnected` (línea 2251) usadas en el flujo legacy. Reutilizar esas mismas keys si aplican, o crear nuevas para P2P. Verificar nombres existentes en `messages.properties` antes de agregar.

2. En `P2PMeshService`, cuando la reconexión exitosa ocurre (dentro del `peerAppeared` → `connectTo` → callback onOpen), notificar mediante un callback de UI. Agregar un `Consumer<String> onPeerReconnected` al constructor de `P2PMeshService` y llamarlo cuando reconecta un peer del catálogo. En Controller, registrar el callback para mostrar `appendChatSystemMessage(I18n.get("chat.userReconnected", name))`.

3. Agregar keys i18n si no existen aún (verificar primero `messages.properties`):
```properties
# messages.properties (español) — pueden ya existir como chat.userDisconnected / chat.userReconnected
chat.peerDisconnected={0} se desconectó
chat.peerReconnected={0} se reconectó
```

4. Agregar indicador de peers conectados en la barra de título. Buscar dónde se actualiza el título de la ventana (buscar `setTitle` en Controller) y agregar count:
```java
int connectedPeerCount = directPeerConnections.size();
view.setTitle(user.getName() + " - " + workspaceName + " (" + connectedPeerCount + " peers)");
```

**Estimación:** 25 min

---

### UX-2: Indicador de "typing" en chat

**Severidad:** Media
**Impacto:** `EventTypes.USER_TYPING` existe como tipo efímero en el core pero no está implementado ni en envío ni en recepción UI. Es una feature esperada en cualquier chat colaborativo.

**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/events/EventTypes.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java`

**Implementación:**

1. En Controller, agregar debounce para enviar typing. El campo `chatInput` es un `JTextField` declarado en línea ~153, inicializado en ~2620. Agregar listener en el mismo lugar donde se configura el chat (~2635):
```java
private javax.swing.Timer typingDebounce;
// En el método donde se inicializa chatInput (~2620-2640):
typingDebounce = new javax.swing.Timer(3000, e -> { /* auto-stop */ });
typingDebounce.setRepeats(false);
chatInput.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
    public void insertUpdate(javax.swing.event.DocumentEvent e) { sendTypingEvent(); }
    public void removeUpdate(javax.swing.event.DocumentEvent e) { sendTypingEvent(); }
    public void changedUpdate(javax.swing.event.DocumentEvent e) {}
});
```
```java
private void sendTypingEvent() {
    if (core == null) return;
    if (!typingDebounce.isRunning()) {
        publishCoreEvent(core.publishTypingEvent());
    }
    typingDebounce.restart();
}
```

2. En `CoreApplicationService`, agregar factory method. Seguir el patrón de `publishPeerStatus()` (~línea 292) que ya crea eventos efímeros:
```java
public Event publishTypingEvent() {
    requireSession();
    return eventFactory.create(currentWorkspaceId, EventTypes.USER_TYPING,
        currentMember.memberId(),
        Map.of("user_name", currentMember.displayName()),
        null);  // parents=null → List.of() en EventFactory
    // NOTA: EventTypes.isPersistent("user.typing") retorna false automáticamente
    // porque USER_TYPING está en EPHEMERAL set. No pasar boolean.
}
```
**Importante:** `EventFactory.create` firma: `(String workspaceId, String type, String authorMemberId, Map<String,Object> payload, List<String> parents)`. El flag `persistent` se infiere de `EventTypes.isPersistent(type)`. NO pasar boolean.

3. En el handler de efímeros. **Detalle arquitectural importante:** `P2PNetworkAdapter.onEphemeralCoreEvent` es un callback de un solo slot (single `Consumer<Event>`), y `P2PMeshService` ya lo registra en su constructor (línea 54: `p2p.onEphemeralCoreEvent(e -> applyEphemeralState(e))`). Controller NO puede registrar otro callback directamente sin sobrescribirlo.

   **Solución:** Agregar un callback secundario en `P2PMeshService`:
   ```java
   // En P2PMeshService, agregar campo:
   private Consumer<Event> ephemeralForwarder;
   public void onEphemeralForwarded(Consumer<Event> callback) { this.ephemeralForwarder = callback; }
   // En applyEphemeralState(), después de procesar:
   if (ephemeralForwarder != null) ephemeralForwarder.accept(event);
   ```
   Luego en Controller, después de crear `p2pMesh`, registrar:
   ```java
   p2pMesh.onEphemeralForwarded(event -> { ... });
   ```
   Dentro de ese callback, agregar branch para `USER_TYPING`:
```java
if (EventTypes.USER_TYPING.equals(event.type())) {
    String userName = String.valueOf(event.payload().getOrDefault("user_name", ""));
    if (!userName.isBlank() && !userName.equals(user.getName())) {
        SwingUtilities.invokeLater(() -> showTypingIndicator(userName));
    }
}
```

4. `showTypingIndicator()`: crear un `JLabel` debajo del chat area con auto-hide de 3 segundos:
```java
private JLabel typingLabel; // inicializar junto al chat panel
private javax.swing.Timer typingHideTimer;
private void showTypingIndicator(String userName) {
    if (typingLabel == null) return;
    typingLabel.setText(I18n.get("chat.typing", userName));
    typingLabel.setVisible(true);
    if (typingHideTimer != null) typingHideTimer.stop();
    typingHideTimer = new javax.swing.Timer(3000, e -> typingLabel.setVisible(false));
    typingHideTimer.setRepeats(false);
    typingHideTimer.start();
}
```

5. Keys i18n:
```properties
# messages.properties (español)
chat.typing={0} está escribiendo...

# messages_es.properties — verificar nombre correcto del archivo
chat.typing={0} está escribiendo...
```
```properties
# messages_en.properties (o messages.properties si es el inglés)
chat.typing={0} is typing...
```
**Nota:** Verificar cuál archivo es español y cuál inglés revisando los archivos existentes en `p2p-client/src/main/resources/i18n/`.

**Estimación:** 30 min

---

### UX-3: Progress y estado durante sync inicial (post-join)

**Severidad:** Media
**Impacto:** Después de unirse a un workspace, no hay feedback visible durante la sincronización de eventos. El usuario ve la pantalla de login hasta que el workspace se activa silenciosamente.

**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/main/resources/i18n/messages.properties` y `messages_es.properties`

**Implementación:**

1. Mostrar progreso durante el join/sync en los labels de login:
```java
// En el handler de join exitoso (DirectBootstrap onWelcome):
SwingUtilities.invokeLater(() -> {
    mostrarErrorEnPantallaLogin(I18n.get("sync.inProgress"));
});
```

2. Actualizar cuando el sync completa:
```java
// En activateWorkspaceFromCore:
SwingUtilities.invokeLater(() -> {
    mostrarErrorEnPantallaLogin(I18n.get("sync.complete"));
});
```

3. Keys i18n:
```properties
sync.inProgress=Sincronizando workspace...
sync.complete=Sincronización completada
sync.waitingApproval=Esperando aprobación del workspace...
```

**Estimación:** 15 min

---

### UX-4: i18n — eliminar strings hardcodeados en español

**Severidad:** Baja
**Impacto:** Hay mensajes visibles al usuario en español hardcodeado que no usan i18n, causando inconsistencia cuando el idioma está en inglés.

**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/CoreChunkTransferCoordinator.java`
- `p2p-client/src/main/resources/i18n/messages.properties` y `messages_es.properties`

**Implementación:**

1. Buscar strings hardcodeados en Controller.java que son visibles al usuario:
   - `"Error al iniciar el tunel..."` → key `tunnel.error`
   - `"Las claves no coinciden"` → key `password.mismatch`
   - `"Buscando chunks de..."` → key `transfer.searchingChunks`
   - `"Descargando chunks de..."` → key `transfer.downloadingChunks`
   - Cualquier otro string visible en UI que no use `I18n.get()`

2. Para cada uno, crear key en `messages.properties` y `messages_es.properties`, y reemplazar el string por `I18n.get("key")`.

3. Para `CoreChunkTransferCoordinator`, pasar las labels como parámetro desde Controller (que tiene acceso a i18n) en vez de hardcodear en el coordinator.

**Estimación:** 20 min

---

## Resumen de prioridades

### Inmediatas (impacto crítico, esfuerzo moderado)

| ID | Área | Severidad | Estimación |
|---|---|---|---|
| SEC-1 | Validar membership en sync/chunks | **Crítica** | 30 min |
| SEC-2 | Validar efímeros (membership check) | **Crítica** | 15 min |
| SEC-4 | Límite tamaño mensajes WebSocket | **Alta** | 10 min |
| SEC-3 | Path traversal en downloads | **Alta** | 15 min |
| PERF-1 | Cache de WorkspaceState | **Crítica** | 45 min |
| STAB-1 | Paginación de sync | **Crítica** | 45 min |

### Corto plazo (mejora significativa)

| ID | Área | Severidad | Estimación |
|---|---|---|---|
| PERF-2 | File indexing fuera del EDT | **Crítica** | 30 min |
| PERF-3 | Debounce visual refresh | **Alta** | 20 min |
| STAB-2 | Shutdown completo | **Alta** | 20 min |
| UX-1 | Feedback conexión/reconexión | **Alta** | 25 min |

### Medio plazo

| ID | Área | Severidad | Estimación |
|---|---|---|---|
| PERF-4 | listEventsAfter optimizado | **Alta** | 20 min |
| STAB-3 | Preservar catálogo en disconnect | **Media** | 15 min |
| UX-2 | Typing indicator | **Media** | 30 min |
| UX-3 | Progress durante sync/join | **Media** | 15 min |
| UX-4 | Eliminar hardcoded español | **Baja** | 20 min |

**Tiempo total estimado: ~5.5 horas**
