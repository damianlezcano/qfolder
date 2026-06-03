# Plan de Trabajo — qfolder

Plan revisado el 3 de junio de 2026. **279 tests, 0 failures, BUILD SUCCESS.**

BUG-1 a BUG-6 corregidos. FASE11-FIX-2 completado. FASE11-FIX-1 documentado como futuro. DOC-1 parcialmente aplicado. Se identificaron **gaps residuales** y **nuevos pendientes menores** durante la revisión.

> **Nota para el LLM ejecutor:** Antes de cada cambio, leer el archivo completo (o la sección relevante) para confirmar que las líneas coinciden. Después de cada grupo de cambios, ejecutar `cd p2p-client && mvn test`. Ejecutar `./build.sh` al final.

## Resumen

### Bugs Fase 11 — todos corregidos, gaps residuales pendientes

| ID | Tarea | Estado | Gap residual |
|---|---|---|---|
| BUG-1 | SyncEngine efímeros P2P | ✅ Corregido | `SimulatedNetworkAdapter` no tiene bypass efímero (solo afecta tests de simulación) |
| BUG-2 | CRDT offset notas | ✅ Corregido | `deleteNoteText()` ignora `length` (solo borra línea completa); CRDT es line-based, no character-based |
| BUG-3 | EventValidator cache | ✅ Corregido | Sin test de regresión dedicado para reuso de cache |
| BUG-4 | MeshProjector isolation | ✅ Corregido | `clearLiveCache(workspaceId)` definido pero NO llamado al desconectar workspace |
| BUG-5 | Snapshot delta boundary | ✅ Corregido | — |
| BUG-6 | eventsSinceSnapshot volatile | ✅ Corregido | Sin test de regresión dedicado |

### Fase 11 — integración

| ID | Tarea | Estado |
|---|---|---|
| FASE11-FIX-1 | EventPipeline en producción | ⏸️ Documentado como futuro (Javadoc explica que producción usa `EventService` directamente) |
| FASE11-FIX-2 | ChunkReplicator en Controller | ✅ Completado (`enable()`, `onFileAvailable()`, `runStartupCache()`) |

### Documentación

| ID | Tarea | Estado | Gap residual |
|---|---|---|---|
| DOC-1 | AGENTS.md | ✅ Mayormente completado | Línea 139: `SnapshotService` aún dice "no asumir snapshot+delta" (desactualizado); Línea 220: menciona chunk replication como pendiente (ya implementado) |

### Nuevos pendientes encontrados en esta revisión

| ID | Tarea | Severidad | Estado |
|---|---|---|---|
| RESIDUAL-1 | `clearLiveCache(workspaceId)` no se llama al desconectar workspace | **Baja** | ⏳ Pendiente |
| RESIDUAL-2 | `deleteNoteText()` ignora parámetro `length` — solo borra línea completa | **Baja** | ⏳ Pendiente |
| RESIDUAL-3 | Faltan tests de regresión para BUG-3 y BUG-6 | **Baja** | ⏳ Pendiente |
| RESIDUAL-4 | `ChunkReplicator.processEvents()` no se llama para eventos `file.shared` incrementales post-startup | **Baja** | ⏳ Pendiente |
| RESIDUAL-5 | AGENTS.md líneas 139 y 220 desactualizadas (SnapshotService y chunk replication) | **Baja** | ⏳ Pendiente |
| RESIDUAL-6 | `EventValidatorTest` nombre misleading: `memberJoinApprovalEsAceptadoPorAutor` asserta `false` | **Muy baja** | ⏳ Pendiente |
| RESIDUAL-7 | `System.err.println` en producción (EmbeddedWebSocketServer, UpdateChecker, AppConfig) | **Muy baja** | ⏳ Pendiente |

### Pendientes originales

| ID | Tarea | Severidad | Estado |
|---|---|---|---|
| PENDIENTE-1 a 10 | Fixes iniciales | — | ✅ Todos completados |
| PENDIENTE-11 | Refactorización Controller.java (~6190 líneas, ~333 métodos) | Alta | ⏳ Pendiente |
| PENDIENTE-12 | Almacenamiento seguro de privateKey Ed25519 | Alta | ⏳ Pendiente |
| PENDIENTE-13 | Tests unitarios dedicados adapters/network | Media | ⏳ Pendiente |
| PENDIENTE-14 | Tests unitarios dedicados client/hub y client/ws | Media | ⏳ Pendiente |
| PENDIENTE-15 | Tests unitarios dedicados adapters/filesystem | Media | ⏳ Pendiente |
| PENDIENTE-16 | Unificación de ramas CI/release | Baja | ⏳ Pendiente |
| PENDIENTE-17 | Métricas de performance y profiling | Baja | ⏳ Pendiente |
| PENDIENTE-18 | Documentación de API pública | Muy baja | ⏳ Pendiente |
| PENDIENTE-19 | Separar tests ruidosos del suite normal | Baja | ⏳ Pendiente |
| PENDIENTE-20 | Limpiar warnings Maven Shade/SLF4J | Baja | ⏳ Pendiente |
| PENDIENTE-21 | Automatizar smoke E2E multi-instancia | Media | ⏳ Pendiente |

### Fase 11 — estado verificado

| Ítem | Estado |
|---|---|
| 11.1 EventPipeline | ⏸️ Código preparatorio; producción usa `EventService` (documentado) |
| 11.2 Proyecciones separadas | ✅ Completado |
| 11.3 PEER_STATUS efímero | ✅ Corregido (BUG-1 fix: `P2PNetworkAdapter` bypass + `onEphemeralCoreEvent` callback) |
| 11.4 SnapshotService startup | ✅ Completado |
| 11.5 CRDT notas | ✅ Corregido (BUG-2 fix: `charOffsetToLineIndex()` en Controller) |
| 11.6 EventValidator cache | ✅ Corregido (BUG-3 fix: `remoteEventService` compartido) |
| 11.7 Auto-reconnect mesh | ✅ Completado |
| 11.8 Eliminar model.Event legacy | ✅ Completado |
| Controller snapshot | ✅ Completado |
| Chunk replicator | ✅ Completado (FASE11-FIX-2: integrado en Controller) |

---

## Detalle: Bugs en código de Fase 11 (PRIORIDAD MÁXIMA)

### BUG-1: SyncEngine descarta eventos efímeros en P2P inbound (rompe mesh discovery)

**Severidad:** Crítica
**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/core/events/EventService.java` (línea 22)
- `p2p-client/src/main/java/org/q3s/p2p/core/sync/SyncEngine.java` (línea 25)
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PNetworkAdapter.java` (línea ~198)

**Problema:** `EventService.accept()` rechaza eventos efímeros (`event.isEphemeral()` retorna `true` → `return false`). `SyncEngine.receiveEvent()` usa `EventService.accept()`, y `P2PNetworkAdapter` inbound usa `sync.receiveEvent()` para eventos core recibidos. Como `PEER_STATUS_UPDATED` es ahora efímero (Fase 11.3), los eventos de estado de peers se descartan silenciosamente en el lado que **inició** la conexión WebSocket. Solo llegan por la ruta directa del `EmbeddedWebSocketServer` (inbound connections).

**Impacto:** El mesh discovery está roto en una de las dos rutas P2P. Un peer que se une puede no recibir actualizaciones de estado de peers que están conectados via outbound.

**Código problemático (`EventService.java` línea ~22):**
```java
public boolean accept(Event event) {
    if (event == null || event.isEphemeral() || store.hasEvent(event.eventId())) return false;
```

**Corrección:** En `P2PNetworkAdapter`, los eventos core inbound deben pasar por `CoreApplicationService.receiveRemoteEvent()` en lugar de `sync.receiveEvent()`, ya que `receiveRemoteEvent()` maneja correctamente eventos efímeros (los aplica al estado sin persistirlos):

Opción A — Cambiar en `P2PNetworkAdapter` la ruta inbound (alrededor de línea 198):
```java
if (CoreEnvelopeCodec.CORE_EVENT_NAME.equals(envelope.name())) {
    Event coreEvent = CoreEnvelopeCodec.decodeCoreEvent(envelope);
    if (coreEvent != null) {
        boolean accepted;
        if (coreEvent.isEphemeral()) {
            // Eventos efímeros: aplicar al estado sin persistir
            if (onCoreEventStored != null) onCoreEventStored.accept(coreEvent);
            accepted = true;
        } else {
            accepted = sync.receiveEvent(coreEvent);
            if (accepted && onCoreEventStored != null) onCoreEventStored.accept(coreEvent);
        }
    }
}
```

Opción B — Modificar `EventService.accept()` para no rechazar efímeros sino aplicarlos sin persistir:
```java
public boolean accept(Event event) {
    if (event == null || store.hasEvent(event.eventId())) return false;
    if (event.isEphemeral()) return true; // aceptar sin persistir
    // ... validación y persistencia normal
}
```

**Test de regresión:** Agregado `bug1receiveRemoteEventAplicaEphemeralSinPersistir` que verifica que `core.receiveRemoteEvent(ephemeral)` aplica el peerUrl a state sin persistir. `P2PNetworkAdapter` ahora diferencia efímeros vs persistentes con `onEphemeralCoreEvent` callback.

---

### BUG-2: CRDT notes — character offset tratado como line index en UI

**Severidad:** Alta
**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java` — método `findLineIdAtPosition()`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` — líneas ~3688, ~3703

**Problema:** `Controller` pasa `DocumentEvent.getOffset()` (un **offset de carácter** en el documento) a `core.insertNoteText()` y `core.deleteNoteText()`, que internamente llaman `findLineIdAtPosition()`. Este método compara el `position` contra un **índice de línea** (`i`), no contra un offset de carácter. Además, `deleteNoteText()` ignora el parámetro `length` — solo borra líneas completas.

**Código problemático (Controller.java ~3688):**
```java
publishCoreEvent(core.insertNoteText("shared-notes", e.getOffset(), text));
```

```java
publishCoreEvent(core.deleteNoteText("shared-notes", e.getOffset(), e.getLength()));
```

**Impacto:** Las operaciones CRDT de notas desde la UI producen inserción/eliminación en posiciones incorrectas. El path de rich-text via `NOTE_UPDATED` (snapshot completo) sigue funcionando como fallback.

**Corrección:** Hay dos opciones:

Opción A (recomendada) — Mapear el character offset a un line index en Controller antes de llamar al core:
```java
// En el document listener de notas en Controller:
int lineIndex = notesPane.getStyledDocument().getDefaultRootElement()
    .getElementIndex(e.getOffset());
publishCoreEvent(core.insertNoteText("shared-notes", lineIndex, text));
```

Opción B — Cambiar `findLineIdAtPosition()` en CoreApplicationService para aceptar character offsets y convertirlos internamente, calculando la posición acumulada de caracteres por línea.

**Test de regresión:** Agregado `bug2insertNoteTextAceptaLineIndexYConcatenaEnOrden` y helper `Controller.charOffsetToLineIndex(StyledDocument, int)` que usa `doc.getDefaultRootElement().getElementIndex(offset)`.

---

### BUG-3: EventValidator cache no es efectivo

**Severidad:** Media
**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java` (línea ~286)
- `p2p-client/src/main/java/org/q3s/p2p/core/events/EventPipeline.java` (línea ~89)

**Problema:** `CoreApplicationService.receiveRemoteEvent()` crea un **nuevo** `EventService` por cada llamada. `EventPipeline.acceptRemote()` crea un **nuevo** `EventValidator` por cada llamada. Ambos eliminan el beneficio del cache de `WorkspaceState` en `EventValidator`.

**Código problemático (CoreApplicationService.java ~286):**
```java
boolean accepted = new EventService(eventStore, true).accept(event);
```

**Corrección (aplicada):** `CoreApplicationService` mantiene `private final EventService remoteEventService` instanciado una vez. `EventPipeline.DefaultEventPipeline` reusa `EventValidator` como campo en vez de crearlo en cada `acceptRemote()`.

```java
// En CoreApplicationService, como campo:
private final EventService remoteEventService;

// En constructor:
this.remoteEventService = new EventService(eventStore, true);

// En receiveRemoteEvent:
boolean accepted = remoteEventService.accept(event);
```

Hacer lo mismo en `EventPipeline` — reusar el `EventValidator` como campo en vez de crearlo en cada `acceptRemote()`.

---

### BUG-4: MeshProjector — cache estático global sin aislamiento por workspace

**Severidad:** Media
**Archivos:**
- `p2p-client/src/main/java/org/q3s/p2p/core/state/MeshProjector.java` (líneas 18–19)

**Problema:** `LIVE_PEER_URLS` y `LIVE_PEER_CONNECTIONS` son `static ConcurrentHashMap` compartidos entre todos los workspaces del mismo JVM. URLs de peers del workspace A pueden filtrarse al workspace B via `hydrate()`. `clearLiveCache()` existe pero nunca se llama desde producción.

**Código problemático:**
```java
private static final Map<String, String> LIVE_PEER_URLS = new ConcurrentHashMap<>();
private static final Map<String, java.util.Set<String>> LIVE_PEER_CONNECTIONS = new ConcurrentHashMap<>();
```

**Corrección (aplicada):** `LIVE_PEER_URLS` y `LIVE_PEER_CONNECTIONS` ahora son `Map<workspaceId, Map<memberId, ...>>` con `clearLiveCache(workspaceId)`. Keys determinan el workspace via `state.workspace().workspaceId()`. Tests: `bug4meshProjectorScopePorWorkspaceAislandoEstados`.

```java
private static final Map<String, Map<String, String>> LIVE_PEER_URLS = new ConcurrentHashMap<>();
private static final Map<String, Map<String, java.util.Set<String>>> LIVE_PEER_CONNECTIONS = new ConcurrentHashMap<>();
```

Y en `apply()` / `hydrate()`, usar `workspaceId` como clave exterior. Llamar `clearLiveCache(workspaceId)` en `removeAllTab()` / disconnect de Controller.

**Alternativa más simple:** Llamar `MeshProjector.clearLiveCache()` al desconectar de un workspace (en `Controller.removeAllTab()` o `showJoinAfterWorkspaceLost()`).

---

### BUG-5: Snapshot delta boundary — eventos con mismo milisegundo pueden perderse

**Severidad:** Media
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/ports/EventStore.java` — método default `listEventsAfter()`

**Problema:** El threshold para delta es `max(createdAt)` de los eventos del snapshot. Usa `isAfter(threshold)` (estricto), lo que excluye eventos con el **mismo timestamp** que el último evento del snapshot pero que **no estaban incluidos** en él (race de mismo milisegundo).

**Corrección:** Cambiar `isAfter` a `!isBefore` (inclusivo), y luego filtrar los que ya están en el snapshot por `eventId`:

```java
default List<Event> listEventsAfter(String workspaceId, Instant after) {
    if (after == null) return listEvents(workspaceId);
    Instant threshold = after.minusMillis(1); // margen de 1ms
    return listEvents(workspaceId).stream()
            .filter(e -> e.createdAt() != null && e.createdAt().isAfter(threshold))
            .toList();
}
```

O mejor, pasar el set de eventIds del snapshot y filtrar por exclusión. **Aplicado:** `EventStore.listEventsAfter` ahora usa `threshold = after.minusMillis(1)` y `!e.createdAt().isBefore(threshold)`. Ademas `CoreApplicationService.currentStateWithSnapshot` dedupica por eventId antes de combinar. Test: `bug5listEventsAfterInclusivoCapturaEventosMismoMs`.

---

### BUG-6: CoreApplicationService.eventsSinceSnapshot no es volatile

**Severidad:** Baja
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java` (campo ~58, uso ~418–420)

**Problema:** El contador `eventsSinceSnapshot` se incrementa desde hilos P2P/UI sin `volatile` ni sincronización. Puede causar snapshots no guardados o duplicados.

**Corrección:**
```java
// Antes:
private int eventsSinceSnapshot = 0;
// Después:
private final java.util.concurrent.atomic.AtomicInteger eventsSinceSnapshot = new java.util.concurrent.atomic.AtomicInteger(0);
```

Actualizar incrementos a `eventsSinceSnapshot.incrementAndGet()` y comparaciones a `eventsSinceSnapshot.get() >= threshold`. **Aplicado:** `CoreApplicationService.eventsSinceSnapshot` ahora es `AtomicInteger` con `incrementAndGet()` y `set(0)`.

---

## Detalle: Fase 11 items con implementación incompleta

### FASE11-FIX-1: EventPipeline no conectado a producción

**Severidad:** Media
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/core/events/EventPipeline.java`

**Estado:** La clase existe y tiene tests (`EventPipelineTest.java`), pero **ningún código de producción la usa**. `CoreApplicationService` y los domain services siguen usando `eventStore.append()` directo y `new EventService(store, true)` para validación remota.

**Opciones:**
1. **Integrar:** Reemplazar los paths de append/accept en `CoreApplicationService` con `EventPipeline`
2. **Documentar:** Si es código preparatorio para el futuro, marcarlo explícitamente como tal

**Resolución (opción 2):** Javadoc de `EventPipeline` documenta su rol preparatorio y que producción sigue usando `EventService` con instancia compartida. BUG-3 fix parcial: el `EventValidator` ya es reusado en `DefaultEventPipeline`.

**Si se elige integrar:** Reemplazar en `CoreApplicationService`:
- Los `eventStore.append(event)` locales → `pipeline.appendLocal(event)`
- El `new EventService(eventStore, true).accept(event)` remoto → `pipeline.acceptRemote(event)`
- Registrar listeners en el pipeline para broadcast P2P y UI refresh

---

### FASE11-FIX-2: ChunkReplicator no integrado en Controller

**Severidad:** Media
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/core/files/ChunkReplicator.java`

**Estado:** La clase existe y tiene tests (`ChunkReplicatorTest.java`), y está referenciada en `CoreApplicationService`, pero `Controller` nunca llama `chunkReplicator.enable()` ni `runStartupCache()`.

**Corrección (aplicada):** En `Controller.initializeCoreServices` se llama `core.chunkReplicator().onFileAvailable(this::requestReplicatorDownload)` y `core.chunkReplicator().enable()`. En `Controller.activateWorkspaceFromCore` se llama `core.runStartupCache()` para descargar archivos compartidos que aún no tenemos. Helper `requestReplicatorDownload(FileMetadata)` convierte metadata a QFile y delega en `coreChunkTransfer.request()`.
```java
if (core.getChunkReplicator() != null) {
    core.getChunkReplicator().enable();
    core.getChunkReplicator().runStartupCache();
}
```

Verificar que `CoreApplicationService` expone el `ChunkReplicator` (agregar getter si no existe).

---

## Detalle: Actualización de AGENTS.md

### DOC-1: AGENTS.md desactualizado

**Severidad:** Alta (afecta a todos los LLMs que lean el archivo)
**Archivo:** `AGENTS.md`

**Cambios necesarios:**

1. **Test count:** Cambiar "221 tests, 0 failures" → obtener el count real ejecutando `cd p2p-client && mvn test` y actualizar. Actualmente ~275 tests.

2. **Eliminar referencias a clases borradas:**
   - `org.q3s.p2p.model.Event` — ya no existe. Reemplazar por `org.q3s.p2p.core.model.Event` (modelo core) y `CoreEnvelope` (wire format)
   - `org.q3s.p2p.model.util.EventUtils` — ya no existe. Reemplazar por `CoreEventCodec` y `CoreEnvelopeCodec`
   - En "Archivos A Revisar": `Event.java` → `core/model/Event.java`; `EventUtils.java` → `CoreEventCodec.java`, `CoreEnvelopeCodec.java`

3. **Agregar clases nuevas a "Componentes Principales":**
   - `org.q3s.p2p.core.events.EventPipeline`: pipeline unificado de ingesta de eventos (local y remoto)
   - `org.q3s.p2p.core.state.MembershipProjector`, `ContentProjector`, `MeshProjector`: proyecciones de estado separadas usadas por `WorkspaceStateBuilder`
   - `org.q3s.p2p.core.codec.CoreEnvelope` y `CoreEnvelopeCodec`: formato wire JSON-Base64 que reemplaza al antiguo `model.Event`
   - `org.q3s.p2p.core.files.ChunkReplicator`: replicación proactiva de chunks para disponibilidad offline
   - `org.q3s.p2p.core.model.NoteLine`: línea CRDT para notas colaborativas

4. **Actualizar "SnapshotService":** Ya no es solo para guardar/cargar snapshots — ahora está integrado en el startup via `CoreApplicationService.configureSnapshotPath()` y `currentState()` usa snapshot+delta. Auto-save cada N eventos.

5. **Actualizar "Estado Actual Del Desarrollo":**
   - Tests: actualizar conteo
   - Agregar: EventPipeline, proyecciones separadas, CRDT line-based para notas, auto-reconnect mesh, CoreEnvelope como wire format
   - Actualizar: `org.q3s.p2p.model.Event` eliminado, wire protocol usa `CoreEnvelope`

6. **En "Qué No Modificar Sin Avisar":** Agregar `CoreEnvelope` y `CoreEnvelopeCodec` ya que son parte del protocolo wire.

**Resolución (DOC-1 aplicado):** AGENTS.md actualizado con conteo de tests (279), referencias a `CoreEnvelope`/`CoreEnvelopeCodec`/`core/model/Event`, separación clara de BUGS corregidos (BUG-1 a BUG-6) y FASE11-FIX-1/2, secciones de "Archivos A Revisar" y "Qué No Modificar" actualizadas, descripción de `EventPipeline`/`ChunkReplicator`/`MeshProjector` con sus fixes.

---

## Detalle de Pendientes Originales (sin cambios en implementación)

### Completados (PENDIENTE-1 a PENDIENTE-10)

Todos completados en sesiones anteriores. Ver historial en SESSION_NOTES.md.

## Pendientes a futuro (no implementados)

### Prioridad Alta (Pre-Release)

#### PENDIENTE-11: Refactorización de Controller.java (God Object)
- **Problema:** `Controller.java` tiene ~6190 líneas y ~333 métodos. Mezcla UI, lógica de negocio, red, archivos, chat, pizarra, notas, membresía, etc.
- **Impacto:** Mantenibilidad baja, difícil de testear, alto acoplamiento.
- **Propuesta:** Separar en controllers especializados:
  - `UIController` — gestión de vistas, tabs, i18n, look-and-feel
  - `NetworkController` — P2P, WebSocket, Cloudflare, mesh
  - `FileController` — transferencia de archivos, chunks, indexación
  - `ChatController` — mensajes, adjuntos, historial
  - `WhiteboardController` — pizarra colaborativa, strokes, objetos
  - `NotesController` — notas CRDT, snapshots, formato rico
  - `MembershipController` — aprobación de peers, identidad, auth
- **Archivos:** `Controller.java` → 6-7 archivos nuevos
- **Tests:** Agregar tests unitarios para cada controller extraído
- **Riesgo:** Alto (refactor masivo), requiere migración incremental

#### PENDIENTE-12: Almacenamiento seguro de privateKey Ed25519
- **Problema:** `Controller.java` almacena `member.privateKey` en `identity.properties` como texto plano.
- **Código actual:** `identity.setProperty("member.privateKey", localPrivateKey)`
- **Impacto:** Riesgo de seguridad si el archivo es accesible por otros procesos/usuarios.
- **Propuesta:** Usar Java KeyStore (JKS) o plataforma-specific secure storage:
  - Linux: `libsecret` via D-Bus
  - Windows: Windows Credential Manager
  - macOS: Keychain
  - Fallback: JKS con passphrase derivada de hardware ID
- **Archivos:** `Controller.java`, nuevo `SecureIdentityStore.java`
- **Tests:** Verificar que privateKey no aparece en logs ni en plaintext en disco
- **Riesgo:** Medio (cambio de API de identidad)

### Prioridad Media (Calidad y Robustez)

#### PENDIENTE-13: Tests unitarios dedicados para adapters/network
- **Problema:** `P2PNetworkAdapter`, `P2PMeshService`, `DirectBootstrap`, `CoreChunkTransferCoordinator` **no tienen test files dedicados**. Sin embargo, ya existe cobertura parcial de integración:
  - `CoreWsClientTest` (3 tests para P2PNetworkAdapter)
  - `CoreWebSocketIntegrationTest` (6 tests, incluye DirectBootstrap)
  - `CoreControllerIntegrationTest` (9 tests de mesh, 6 de chunks)
- **Gap real:** Faltan tests **unitarios aislados** para routing, timeouts, retry logic, cancelación y edge cases que no se cubren en integración.
- **Propuesta:**
  - `P2PNetworkAdapterTest` — envío/recepción de envelopes, routing por peerId, timeouts
  - `P2PMeshServiceTest` — formación de mesh, reconexión, failover, `applyPeerDiscoveryState`
  - `DirectBootstrapTest` — join exitoso/fallido, retry logic, cleanup de recursos (WsClient leak)
  - `CoreChunkTransferCoordinatorTest` — descarga distribuida, reintentos, cancelación, deduplicación
- **Archivos:** 4 nuevos archivos de test
- **Riesgo:** Bajo (solo agrega tests)

#### PENDIENTE-14: Tests unitarios dedicados para client/hub y client/ws
- **Problema:** `EmbeddedWebSocketServer`, `CloudflareTunnel`, `WsClient` no tienen test files dedicados. Existe cobertura parcial:
  - `CoreWsClientTest` — 6 tests de WsClient (null-logger, onClose, sendEnvelope) + 3 de P2PNetworkAdapter
  - `CoreWebSocketIntegrationTest` — usa `EmbeddedWebSocketServer` internamente pero no lo testea en aislamiento
  - `CloudflareTunnel` — zero cobertura de test
- **Gap real:** `WsClient` retries, `EmbeddedWebSocketServer` lifecycle, `CloudflareTunnel` mock/real.
- **Propuesta:**
  - `EmbeddedWebSocketServerTest` — inicio/detención, manejo de conexiones, mensajes directos
  - `CloudflareTunnelTest` — inicio con cloudflared real vs mock, cleanup de procesos
  - `WsClientTest` — conexión/desconexión, reintentos, manejo de envelopes completo
- **Archivos:** 3 nuevos archivos de test
- **Nota:** `CoreWsClientTest` ya cubre edge cases — evaluar si renombrarlo a `WsClientTest` y ampliarlo en lugar de crear uno nuevo
- **Riesgo:** Bajo (solo agrega tests)

#### PENDIENTE-15: Tests unitarios dedicados para adapters/filesystem
- **Problema:** `FileSystemEventStore`, `FileSystemFileChunkStore`, `QfolderLayout` no tienen test files dedicados. Existe cobertura parcial:
  - `BackendExtendedSimulationTest` — 1 test de FileSystemEventStore + snapshots, 3 de chunk store
  - `CoreQfolderTest` — ~15 tests usan `QfolderLayout` indirectamente, incluye test de snapshot corrupto
- **Gap real:** Concurrent access, corruption recovery, format migration, edge cases de filesystem.
- **Propuesta:**
  - `FileSystemEventStoreTest` — append, list, concurrent access, corruption recovery
  - `FileSystemFileChunkStoreTest` — put/get chunks, reconstruct file, hash verification
  - `QfolderLayoutTest` — folder naming, workspace discovery, migration de formatos antiguos
- **Archivos:** 3 nuevos archivos de test
- **Riesgo:** Bajo (solo agrega tests)

### Prioridad Baja (Mejoras Opcionales)

#### PENDIENTE-16: Unificación de ramas CI/release
- **Problema:** `RELEASE.md` y `scripts/release.sh` usan flujo `develop → master`, pero `.github/workflows/maven-publish.yml` corre sobre `main`.
- **Impacto:** Confusión en releases, posible desincronización.
- **Propuesta:** Decidir una de 3 opciones:
  1. Cambiar RELEASE.md/release.sh para usar `main`
  2. Cambiar workflow para usar `master`
  3. Mantener dual con documentación explícita (estado actual)
- **Archivos:** `RELEASE.md`, `scripts/release.sh`, `.github/workflows/maven-publish.yml`
- **Nota:** AGENTS.md prohíbe modificar workflow sin aviso explícito
- **Riesgo:** Medio (cambio de flujo de release)

#### PENDIENTE-17: Métricas de performance y profiling
- **Problema:** No hay métricas de performance para operaciones críticas (sync, chunk transfer, CRDT merge).
- **Impacto:** Regresiones de performance no se detectan hasta que son evidentes en producción.
- **Propuesta:**
  - Agregar métricas JMX o Micrometer para: eventos/segundo, chunks transferidos, tiempo de sync, tamaño de snapshots
  - Dashboard simple en UI (tab "Performance" opcional)
  - Alertas si operaciones críticas superan umbrales (ej: sync > 5s)
- **Archivos:** Nuevo `PerformanceMetrics.java`, integración en `CoreApplicationService`
- **Riesgo:** Bajo (adición, no modificación)

#### PENDIENTE-18: Documentación de API pública
- **Problema:** Clases públicas (`CoreApplicationService`, `P2PNetworkAdapter`, `EventStore`, etc.) no tienen Javadoc completo.
- **Impacto:** Difícil para nuevos contribuidores entender contratos y uso correcto.
- **Propuesta:**
  - Agregar Javadoc a todas las clases/interfaces públicas
  - Generar sitio con `mvn site` (plugin maven-javadoc-plugin)
  - Incluir ejemplos de uso en Javadoc
- **Archivos:** Todos los `.java` en `src/main/java`
- **Riesgo:** Muy bajo (solo documentación)

#### PENDIENTE-19: Separar tests de performance/ruidosos del suite normal
- **Problema:** `ClipboardImagePerformanceTest` imprime 25+ líneas `[PERF]` en cada `mvn test`; `BackendExtendedSimulationTest` imprime topología de malla. El suite normal queda ruidoso y menos apto para CI limpio.
- **Impacto:** Dificulta leer fallos reales en CI y mezcla tests funcionales con mediciones de performance.
- **Propuesta:**
  - Marcar tests de performance con `@Tag("performance")`.
  - Configurar Surefire para excluir `performance` por default.
  - Agregar perfil Maven `-Pperformance-tests` para ejecutarlos explícitamente.
  - Reemplazar `System.out.println` por logs condicionales o `TestReporter`.
- **Archivos:** `ClipboardImagePerformanceTest.java`, `BackendExtendedSimulationTest.java`, `pom.xml`
- **Riesgo:** Bajo

#### PENDIENTE-20: Limpiar warnings Maven Shade/SLF4J
- **Problema:** `mvn -Pstatic-analysis verify` muestra warnings recurrentes:
  - `SLF4J: No SLF4J providers were found`.
  - `maven-shade-plugin` reporta `module-info.class` y clases duplicadas por `jakarta.json-api`, `org.glassfish:jakarta.json` y la variante `module` transitiva.
- **Impacto:** Build ruidoso; warnings reales pueden pasar desapercibidos. El fat JAR incluye recursos/clases duplicados.
- **Propuesta:**
  - Agregar binding SLF4J explícito (`slf4j-nop` o `slf4j-simple`) según decisión de logging.
  - Revisar árbol de dependencias JSON-B/Yasson y excluir duplicados transitivos cuando sea seguro.
  - Ajustar filtros del shade plugin para `module-info.class` y duplicados conocidos.
- **Archivos:** `pom.xml`
- **Riesgo:** Medio (tocar dependencias/shade afecta packaging)

#### PENDIENTE-21: Automatizar smoke E2E multi-instancia
- **Problema:** El flujo completo P2P sigue validándose principalmente por scripts manuales (`dev-3-instances.sh`, `dev-2-realinstances.sh`) y checklist `docs/MANUAL_E2E_CORE.md`.
- **Impacto:** Regresiones de UI/red reales pueden escapar aunque los tests unitarios pasen.
- **Propuesta:**
  - Crear smoke test headless/scriptable para 2-3 instancias con túnel mock.
  - Validar creación/join, chat, metadata de archivos, transferencia por chunks y notas básicas.
  - Guardar logs por instancia y fallar si aparecen errores críticos.
  - Mantener Cloudflare real como validación manual/semi-manual separada.
- **Archivos:** `scripts/dev-3-instances.sh`, nuevo script `scripts/smoke-e2e-mock.sh`, posible doc en `docs/`.
- **Riesgo:** Medio (automatización con Swing/procesos puede ser frágil)

## Tests

- `cd p2p-client && mvn test` — verificar count actualizado (último conocido: ~275 tests)
- `cd p2p-client && mvn -Pstatic-analysis verify` — 0 bugs, 0 errores
- `./build.sh` — exitoso, genera `dist/qfolder.jar`

## Orden de ejecución recomendado

### Fase A — Bugs críticos (ejecutar primero)
1. **BUG-1** — SyncEngine descarta efímeros P2P (15 min) — rompe mesh
2. **BUG-2** — CRDT offset en notas (20 min) — rompe edición colaborativa
3. **BUG-6** — eventsSinceSnapshot volatile (5 min) — fix trivial
4. **BUG-5** — Snapshot delta boundary (10 min)
5. **BUG-3** — EventValidator cache (15 min)
6. **BUG-4** — MeshProjector scope por workspace (15 min)
7. Ejecutar `cd p2p-client && mvn test` para verificar

### Fase B — Integración de Fase 11
8. **FASE11-FIX-1** — EventPipeline integrar o documentar (30 min)
9. **FASE11-FIX-2** — ChunkReplicator integrar en Controller (15 min)
10. Ejecutar tests

### Fase C — Documentación
11. **DOC-1** — Actualizar AGENTS.md (30 min)

### Fase D — Pre-release (si aplica)
12. **PENDIENTE-12** — privateKey seguro
13. **PENDIENTE-13/14/15** — Tests unitarios dedicados
14. **PENDIENTE-11** — Refactor Controller (solo si hay tiempo/scope)

### Fase E — Calidad y mejoras
15. **PENDIENTE-19** — Separar tests ruidosos
16. **PENDIENTE-20** — Warnings Maven/SLF4J
17. **PENDIENTE-9** — Habilitar SpotBugs en CI (requiere aviso al usuario, AGENTS.md prohíbe cambiar workflow sin permiso)
18. **PENDIENTE-17** — Métricas de performance
19. **PENDIENTE-18** — Javadoc
20. **PENDIENTE-21** — Smoke E2E automatizado

**Nota:** PENDIENTE-16 (unificación CI/release) requiere decisión explícita del usuario sobre estrategia de ramas.
