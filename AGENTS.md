# AGENTS.md

Contexto permanente para futuras sesiones de opencode en este repositorio.

## Qué Hace La Aplicación

qfolder es una aplicación de escritorio para workspaces colaborativos peer-to-peer. Permite compartir archivos, usar chat, pizarra colaborativa y notas compartidas sin cuentas ni servidor central fijo para el contenido. Cada peer puede exponer un endpoint WebSocket directo y compartir una invitación propia para bootstrap/reconexión.

## Stack Técnico

- Lenguaje: Java 21.
- Build: Maven.
- UI: Java Swing.
- Look and feel: FlatLaf.
- Comunicación: WebSocket con `org.java-websocket:Java-WebSocket`.
- Serialización: JSON-B con Jakarta JSON-B/Yasson y JSON-P.
- Packaging: Maven Shade Plugin para fat JAR y `jpackage` para app-image Linux/Windows.
- Túnel externo: `cloudflared` / Cloudflare Tunnel, con modo mock para desarrollo local.
- CI/release: workflow `.github/workflows/maven-publish.yml` configurado con JDK 21, módulo `p2p-client/pom.xml`, build+test en push/PR y deploy en release.

## Estructura Del Proyecto

- `README.md`: descripción del producto, quick start, opciones de lanzamiento y build.
- `RELEASE.md`: guía de release y auto-update.
- `build.sh`: compila `p2p-client`, genera `dist/qfolder.jar` y copia scripts/properties a `dist/`.
- `scripts/`: scripts de ejecución, empaquetado y release.
- `scripts/package-linux.sh`: build + `jpackage` para Linux, descarga o copia `cloudflared`.
- `scripts/package-windows.ps1`: build + `jpackage` para Windows, descarga o copia `cloudflared.exe`.
- `scripts/dev-3-instances.sh`: lanza 3 instancias locales aisladas con túnel mock para prueba manual end-to-end.
- `scripts/dev-2-realinstances.sh`: lanza 2 instancias locales aisladas con túnel real Cloudflare para validar comportamiento bajo `cloudflared`.
- `docs/MANUAL_E2E_CORE.md`: checklist manual para validar sync core, reconstrucción UI, metadata de archivos, chunks distribuidos y cache de indexación.
- `dist/`: salida generada localmente; está ignorada por Git.
- `build/`: salida temporal de packaging; ignorada por Git.
- `packages/`: paquetes generados; ignorada por Git.
- `p2p-client/`: módulo Maven principal de la aplicación.
- `p2p-client/pom.xml`: configuración Maven real del cliente.
- `p2p-client/src/main/java/org/q3s/p2p/client/`: lógica del cliente de escritorio.
- `p2p-client/src/main/java/org/q3s/p2p/model/`: modelos y utilidades compartidas para eventos, usuarios, workspaces y archivos.
- `p2p-client/src/main/resources/`: recursos gráficos, i18n y properties de ejemplo.
- `p2p-client/target/`: salida Maven generada; ignorada por Git.

## Componentes Principales

- `org.q3s.p2p.client.Main`: punto de entrada. Inicializa FlatLaf vía `LookAndFeelManager` con preferencia persistente de `UserPreferences` y arranca el `Controller` en el EDT de Swing.
- `org.q3s.p2p.client.view.Controller`: controlador principal de UI y comportamiento. Contiene gran parte de la lógica de workspace, chat, archivos, pizarra, notas, failover y sincronización.
- `org.q3s.p2p.client.view.View`: vista Swing principal.
- `org.q3s.p2p.client.view.components.TabListFile`: UI de listas/tablas de archivos.
- `org.q3s.p2p.client.view.components.FileTableModel`: modelo de tabla para archivos.
- `org.q3s.p2p.client.ws.WsClient`: cliente WebSocket que recibe/envía eventos serializados.
- `org.q3s.p2p.client.hub.EmbeddedWebSocketServer`: endpoint WebSocket embebido directo para bootstrap y conexiones P2P; ya no funciona como hub/router legacy.
- `org.q3s.p2p.client.hub.CloudflareTunnel`: inicia/detiene `cloudflared` o usa modo mock.
- `org.q3s.p2p.client.CloudflareInstaller`: instalación/disponibilidad de `cloudflared`.
- `org.q3s.p2p.client.Config` y `AppConfig`: lectura de configuración desde properties, system properties y entorno.
- `org.q3s.p2p.client.UpdateChecker`: chequeo de releases en GitHub para auto-update.
- `org.q3s.p2p.client.util.UserPreferences`: preferencias persistentes de usuario (idioma, look-and-feel) en `systemdata/preferences.properties`, con precedencia: system property > archivo preferences > default.
- `org.q3s.p2p.client.util.LookAndFeelManager`: gestión de look and feel Swing (FlatLaf, Metal, Nimbus, System) con cambio en vivo vía `SwingUtilities.updateComponentTreeUI`.
- `org.q3s.p2p.model.User`, `Workspace`, `QFile`: modelos de usuario, workspace y archivo legacy.
- `org.q3s.p2p.core.app.CoreApplicationService`: fachada nueva para conectar la UI Swing con el core descentralizado testeable.
- `org.q3s.p2p.core.codec.CoreEnvelope` y `CoreEnvelopeCodec`: wire format JSON-Base64 versionado (`core.event`, `core.sync.request`, `core.sync.response`) que reemplaza al antiguo `org.q3s.p2p.model.Event` (eliminado en Fase 11.8).
- `org.q3s.p2p.core.model.Event` (core): record inmutable con `eventId`, `workspaceId`, `type`, `authorMemberId`, `createdAt`, `parents`, `payload`, `auth`, `signature`, `persistent`. `isEphemeral()` retorna `!persistent`.
- `org.q3s.p2p.core.events.EventPipeline` y `EventService`: pipeline unificado y facade de validacion/persistencia para eventos core. `CoreApplicationService` usa `EventService` directamente con instancia compartida para preservar el cache de `EventValidator` (BUG-3 fix). `EventPipeline` es el API preferido para callers externos.
- `org.q3s.p2p.core.state.MembershipProjector`, `ContentProjector`, `MeshProjector`: proyecciones de estado separadas usadas por `WorkspaceStateBuilder`. `MeshProjector` mantiene cache de `LIVE_PEER_URLS`/`LIVE_PEER_CONNECTIONS` scoped por `workspaceId` (BUG-4 fix) y expone `clearLiveCache(workspaceId)`.
- `org.q3s.p2p.core.model.NoteLine`: record CRDT line-based para notas colaborativas. `Note` rediseñado como `Map<String, NoteLine>` con orden estable por `afterLineId` (parent/children) y `(createdAt, lineId)` para siblings. `deleteLine` produce tombstones que prevalecen sobre `insert` posteriores con el mismo `lineId`.
- `org.q3s.p2p.core.files.ChunkReplicator`: replicador opt-in de chunks para disponibilidad offline. `Controller.initializeCoreServices` lo activa con `enable()` y registra listener; `activateWorkspaceFromCore` dispara `core.runStartupCache()` para descargar archivos compartidos que aun no tenemos localmente.
- `org.q3s.p2p.core.sync.SyncEngine`: sync de eventos faltantes entre peers via `store.getMissingEvents(knownIds)`. NO procesa eventos efimeros (esos van por la ruta `onEphemeralCoreEvent` del P2PNetworkAdapter, BUG-1 fix).
- `org.q3s.p2p.core.observability.PerformanceMetrics`: contadores thread-safe para metricas en memoria (eventos publicados/recibidos/efimeros, chunks transferidos, peers conectados). Wired en `Controller.publishCoreEvent`, `Controller.onCoreEventStored`, `P2PNetworkAdapter.ephemeralEvent`, `CoreChunkTransferCoordinator.receiveChunk`. No requiere JMX/Micrometer para uso basico.
- `org.q3s.p2p.core.*`: modelos y servicios core para eventos, workspace, membresía, chat, archivos, pizarra, notas, mesh, sync y estado.
- `org.q3s.p2p.ports.*`: puertos del core (`EventStore`, `NetworkAdapter`, `AuthProvider`, `FileChunkStore`, etc.). `EventStore.listEventsAfter(workspaceId, after)` usa umbral inclusivo (`after.minusMillis(1)` y `!isBefore`) para no perder eventos con el mismo timestamp que el snapshot (BUG-5 fix).
- `org.q3s.p2p.adapters.*`: adaptadores en memoria, filesystem y red simulada/placeholder real.
- `org.q3s.p2p.adapters.network.CoreChunkTransferProtocol`: mensajes P2P para disponibilidad y transferencia explícita de chunks core (`Core chunk availability request/response`, `Core chunk request/response`). Los payloads usan encabezado versionado `QCHUNK1`.
- `org.q3s.p2p.adapters.network.CoreChunkTransferCoordinator`: coordina disponibilidad, planificación, requests, reintentos, verificación hash y fallback de descargas distribuidas por chunks.
- `org.q3s.p2p.adapters.network.SimulatedNetworkAdapter`: simulación de malla/gossip con soporte programable de packet loss y latencia para pruebas de resiliencia.
- `org.q3s.p2p.adapters.network.P2PNetworkAdapter`: implementación real de `NetworkAdapter` sobre conexiones WebSocket directas entre peers, usando `MeshPolicy` y `SyncEngine` para gossip descentralizado. Rutas P2P: `onCoreEventStored` (eventos persistentes), `onEphemeralCoreEvent` (eventos efimeros como `peer.status.updated` que no se persisten, BUG-1 fix) y `onCoreSyncApplied` (delta sync).
- `org.q3s.p2p.adapters.network.P2PMeshService`: servicio de orquestación de la malla P2P — gestiona peerAppeared, peerDisappeared, publish, peerCatalog y mergePeerCatalog. Testeable sin Controller.
- `org.q3s.p2p.adapters.network.DirectBootstrap`: join directo a un peer sin pasar por el hub, con intercambio de PeerCatalog y sync inicial.
- `org.q3s.p2p.adapters.network.InviteCode`: formato de invitación `ws://host:port?workspace=<base64>` para compartir entre peers.
- `org.q3s.p2p.core.files.DistributedChunkPlanner`: planificador básico para repartir descarga explícita de chunks entre varios miembros disponibles.

## Comandos Importantes

- Build principal: `./build.sh`
- Ejecutar build generado: `java -jar dist/qfolder.jar`
- Build Maven directo: `cd p2p-client && mvn clean package`
- Build Maven directo sin tests: `cd p2p-client && mvn clean package -DskipTests`
- Tests core: `cd p2p-client && mvn test`
- Tests performance (lentos): `cd p2p-client && mvn test -Pperformance-tests`
- Smoke test headless del JAR: `./scripts/smoke-e2e-mock.sh` (13 checks: existencia, tamano, manifest, clases criticas, recursos i18n, carga del core via reflection).
- Desarrollo local sin cloudflared: `java -Dqfolder.tunnel.mock=true -jar dist/qfolder.jar`
- Desarrollo local con 3 instancias: ver `README.md`, sección `Development (3 local instances)`.
- Desarrollo local reproducible con 3 instancias aisladas: `./scripts/dev-3-instances.sh` después de `./build.sh`.
- **Race condition conocida**: `./build.sh` sobrescribe `dist/qfolder.jar` vía `cp` (truncamiento del archivo). Si `dev-3-instances.sh` se ejecuta mientras `build.sh` aún corre o justo después, las instancias pueden ver el JAR corrupto y fallar con `NoClassDefFoundError` por clases que no logran cargarse perezosamente. El script `dev-3-instances.sh` mitiga esto copiando el JAR a un snapshot inmutable antes de iniciar instancias. No uses `dist/qfolder.jar` directamente para instancias de desarrollo; siempre usa el snapshot generado por el script.
- Package Linux: `./scripts/package-linux.sh`
- Package Linux con tipo explícito: `./scripts/package-linux.sh app-image`
- Package Windows: `powershell .\scripts\package-windows.ps1`
- Release automatizado: `./scripts/release.sh release v1.0.X "Short description of changes"`
- Static analysis: `cd p2p-client && mvn -Pstatic-analysis verify` (perfil SpotBugs con threshold High).
- Test: `cd p2p-client && mvn test`. Hay tests JUnit 5 para el core descentralizado, simulación de malla, resiliencia, WebSocket y layout.

## Configuración

- `p2p-client/src/main/resources/qfolder.properties.example`: ejemplo de configuración para túnel mock, puerto WebSocket y raíz local de qfolder.
- `dist/qfolder.properties`: configuración copiada/generada para ejecución local; `dist/` está ignorado por Git.
- Propiedades documentadas en `README.md`:
  - `qfolder.user.name`
  - `qfolder.shared.dir`
  - `qfolder.ws.port`
  - `qfolder.tunnel.mock`
  - `qfolder.tunnel.mock.delay` (milisegundos de demora simulada, default: 3000)
  - `qfolder.language` (código ISO 639-1 como `es`, `en`)
  - `qfolder.lookAndFeel` (identificadores: `flat-light`, `flat-dark`, `flat-intellij`, `flat-darcula`, `metal`, `nimbus`, `system`)
- Variables de entorno equivalentes son inferidas por `AppConfig` transformando puntos/guiones a `_` y usando mayúsculas.

## Arquitectura General

- Cada cliente es una app Swing que puede crear o unirse a un workspace.
- La comunicación principal viaja como eventos WebSocket serializados en Base64/JSON.
- Un cliente expone `EmbeddedWebSocketServer` como endpoint directo. No hay `WsHubService` ni router `__hub`/`__to` como flujo activo.
- Los peers se conectan por invitaciones P2P directas (`peerUrl?workspace=<base64>`) para bootstrap inicial, membresía, sync y reconexión.
- Para acceso externo se usa Cloudflare Tunnel mediante `cloudflared`; para desarrollo local puede usarse `qfolder.tunnel.mock=true`.
- La UI se organiza en solapas: archivos, chat, pizarra, notas, ayuda/log/configuración según estado y complementos habilitados.
- La pizarra usa eventos específicos y estado/acciones serializadas dentro de `Controller.WhiteboardCanvas`.
- Las notas usan `JTextPane`/`StyledDocument`. Texto plano emite operaciones core (`note.insert`, `note.deleteOp`) y el formato rico/imágenes conserva snapshot `QNOTES2` mediante `note.updated` como fallback activo.
- El intercambio de archivos usa metadata core y descarga explícita por chunks distribuidos sobre conexiones P2P.
- Migración completada a core descentralizado event-sourced. Chat, membresía, archivos, pizarra y notas registran eventos en el core vía `CoreApplicationService`. Los eventos de contenido viajan por `P2PNetworkAdapter` sobre conexiones WebSocket directas entre peers. El hub WebSocket legacy fue removido como flujo activo.
- El flujo productivo usa `PublicKeyAuthProvider` con firmas Ed25519 reales. `Controller` persiste el par de claves local via `SecureIdentityStore` (AES-256-GCM + PBKDF2-HMAC-SHA256 100k iter) en `systemdata/identity.bin`; `EventValidator` verifica firmas cuando hay clave pública disponible. `TokenAuthProvider` permanece como soporte alternativo/test, no como default productivo. La passphrase se deriva de `user.name` + salt estatico + salt aleatorio por archivo.
- El flujo core→UI reconstruye notas compartidas, mensajes de chat, pizarra y metadata de archivos desde `WorkspaceState` cuando llegan eventos core/sync. La pizarra materializa objetos agregados/movidos/eliminados y trazos libres con color/grosor; archivos viajan como metadata marcada internamente como `core:<fileId>` en `QFile.md5` y la descarga de contenido es explícita por chunks. Los adjuntos de chat usan `chat_attachment=true`, se muestran como link pendiente en receptores y el link se enlaza al path final al completar chunks.
- Las conexiones P2P tienen 3 reintentos con backoff de 500ms/1000ms para tolerar el arranque asíncrono del WebSocket server.
- `qfolder.shared.dir` es la raíz local de qfolder (default `~/qfolder`). Los datos se separan en:
  - `userdata/`: datos visibles del usuario por workspace (`YYYY/MM/DD/HHmm-{id}-{slug}/` con `workspace.json`, `files/`, `chat/`, `notes/`, `whiteboard/`, `logs/`, `members/`).
  - `systemdata/`: datos técnicos internos (`identity.bin` encriptado via `SecureIdentityStore`, `workspaces/<id>/` con `events/`, `chunks/`, `snapshots/`, `state/`, `index-cache.properties`).
  - Solo `files/` se indexa/publica.
- Exportaciones manuales:
  - Pizarra: `userdata/<workspace>/whiteboard/pizarra-YYYY-MM-DD-HHmmss.png`.
  - Notas: `userdata/<workspace>/notes/notas-YYYY-MM-DD-HHmmss.rtf`.
- Guardado final de sesión:
  - Chat: `userdata/<workspace>/chat/chat.txt`
  - Notas: `userdata/<workspace>/notes/notas.rtf` y `userdata/<workspace>/notes/notas.txt`
  - Pizarra: `userdata/<workspace>/whiteboard/pizarra.png`
  - Logs: `userdata/<workspace>/logs/logs.log` y `userdata/<workspace>/logs/sesion.txt`
  - Miembros: `userdata/<workspace>/members/members.json`
- `members/members.json` es snapshot legible, no fuente autoritativa. La fuente autoritativa siguen siendo eventos core en `systemdata/workspaces/<id>/events/`.
- `SnapshotService` esta integrado en el startup via `CoreApplicationService.configureSnapshotPath()` y `currentStateWithSnapshot()`. Auto-save cada N eventos (`configureSnapshotPolicy`, default 50) y retencion de los ultimos M snapshots (`configureSnapshotRetention`, default 5). `Controller` configura el path en `initializeCoreServices` y la deduplicacion por eventId evita duplicados snapshot+delta.
- El `WsClient` tolera `log=null` para conexiones P2P creadas sin logger.
- Handlers legacy de contenido (chat, pizarra, notas, listados/navegación/transferencia completa) quedan en `notify()` solo como compatibilidad defensiva o logs de ignorado; el flujo activo va por core/P2P.
- Los mensajes de chat locales se pintan con id visual `core:<message_id>`/`core-<message_id>` derivado del evento core y se marcan como aplicados para evitar duplicados cuando vuelven por sync/P2P.
- La solapa `Miembros` no debe depender de `wsClient` legacy para el estado del usuario local; el usuario local se considera conectado mientras la sesión P2P local está activa.
- Hay logs de diagnóstico a nivel `debug` para publicar eventos core, aplicar estado a UI, y recibir eventos P2P.
- La formación de malla tras join directo depende de `peer.status.updated`. Como los eventos de candidatos no autorizados pueden rechazarse por validación estricta, `Controller` fuerza `peer.status.updated` tras aprobación y tras activar workspace; `P2PMeshService.applyPeerDiscoveryState` fusiona URLs y rebalancea conexiones sin callback UI para evitar recursión.
- **Ruta dual para mensajes `__to:`**: `sendP2PProtocolEvent` envía cada mensaje dirigido (`__to:peerId`) por AMBAS vías — conexión directa WebSocket Y `p2pNetwork.sendProtocolEvent`. Bajo Cloudflare Tunnel las conexiones directas pueden quedar zombie (TCP half-open) donde `isClosed()` retorna `false` pero los mensajes nunca llegan; la vía P2P provee respaldo confiable.
- **Deduplicación de respuestas de disponibilidad**: `receiveAvailability` mantiene un set `seenAvailabilityResponses` con clave `transferId:peerId` para ignorar respuestas duplicadas del dual-path broadcast.
- **Cancelación de reintentos duplicados**: `scheduleRetry` cancela cualquier `ScheduledFuture` pendiente del mismo `transferId` antes de programar uno nuevo, asegurando que solo haya un timer de reintento activo por transferencia.
- `CoreChunkTransferCoordinator` tiene logs `[CHUNK]` para diagnóstico de disponibilidad, planificación, progreso y reintentos. `Controller` tiene logs `[P2P SEND]`/`[P2P RECV]` para trazabilidad de eventos core P2P.

## Convenciones De Código

- Mantener Java 21 como nivel de compilación salvo decisión explícita del proyecto.
- Conservar el estilo existente: clases Java con indentación por tabs en varios archivos, nombres en español e inglés mezclados, y lógica principal concentrada en `Controller`.
- Evitar refactors grandes de `Controller` salvo que el objetivo lo justifique: es el núcleo de UI, eventos y estado.
- Usar `sendEvent(...)` para envíos por WebSocket desde `Controller` cuando esté disponible, para preservar la cola de salida.
- Para nueva lógica de negocio, preferir servicios del core y exponerlos a la UI mediante `CoreApplicationService`; no agregar más reglas de negocio directamente a `Controller` salvo puente temporal.
- Las actualizaciones de UI Swing deben ejecutarse en el EDT cuando vienen de hilos externos o callbacks WebSocket.
- No introducir dependencias nuevas sin revisar impacto en el fat JAR y packaging.
- Mantener compatibilidad con recursos existentes en `src/main/resources` e i18n (`messages.properties`, `messages_es.properties`).
- Si se agregan textos visibles, revisar ambos archivos de i18n cuando corresponda.
- No asumir que hay tests automatizados; si se agregan, documentar comando y dependencias en este archivo.

## Reglas Para Futuras Sesiones

- Antes de cambiar comportamiento de red/eventos, revisar `core/codec/CoreEnvelope`, `core/codec/CoreEnvelopeCodec`, `core/model/Event`, `core/events/EventService`, `core/events/EventPipeline`, `WsClient`, `EmbeddedWebSocketServer`, `P2PNetworkAdapter`, `P2PMeshService`, `DirectBootstrap` y las ramas relevantes de `Controller.notify(...)`.
- Antes de cambiar UI, revisar `Controller`, `View`, componentes en `view/components` y recursos gráficos/i18n.
- Antes de cambiar archivos, revisar `FileUtils`, `QFile`, `TabListFile`, `FileTableModel` y métodos de transferencia en `Controller`.
- Antes de cambiar lógica core, revisar `CoreApplicationService`, servicios en `org.q3s.p2p.core`, puertos en `org.q3s.p2p.ports` y tests en `p2p-client/src/test`.
- Antes de cambiar configuración, revisar `Config`, `AppConfig`, `qfolder.properties.example`, `README.md` y scripts de package.
- Antes de tocar packaging/release, revisar `build.sh`, `scripts/package-linux.sh`, `scripts/package-windows.ps1`, `scripts/release.sh` y `RELEASE.md`.
- Ejecutar como mínimo `./build.sh` después de cambios de Java o recursos, salvo que haya una razón explícita para no hacerlo.
- Registrar avances relevantes en `SESSION_NOTES.md` al cerrar una sesión de trabajo.
- No revertir cambios existentes del usuario u otros agentes sin permiso explícito.

## Archivos A Revisar Antes De Hacer Cambios

- General: `README.md`, `p2p-client/pom.xml`, `build.sh`.
- App start/config: `Main.java`, `Config.java`, `AppConfig.java`, `qfolder.properties.example`.
- Eventos/red: `core/codec/CoreEnvelope.java`, `core/codec/CoreEnvelopeCodec.java`, `core/model/Event.java`, `core/events/EventService.java`, `core/events/EventPipeline.java`, `WsClient.java`, `EmbeddedWebSocketServer.java`, `P2PNetworkAdapter.java`, `P2PMeshService.java`, `DirectBootstrap.java`, `client/SecureIdentityStore.java`.
- UI principal: `Controller.java`, `View.java`.
- Archivos: `QFile.java`, `FileUtils.java`, `TabListFile.java`, `FileTableModel.java`, `core/files/ChunkReplicator.java`, `core/files/DistributedChunkPlanner.java`.
- Core nuevo: `CoreApplicationService.java`, `CoreChunkTransferProtocol.java`, `CoreChunkTransferCoordinator.java`, `EventStore.java`, `NetworkAdapter.java`, `AuthProvider.java`, `FileChunkStore.java`, `WorkspaceStateBuilder.java`, `SyncEngine.java`, `MeshPolicy.java`, `MembershipProjector.java`, `ContentProjector.java`, `MeshProjector.java`, `SnapshotService.java`, `NoteService.java`, `NoteLine.java`, `Note.java`, `core/observability/PerformanceMetrics.java`.
- Packaging: `scripts/package-linux.sh`, `scripts/package-windows.ps1`, `RELEASE.md`.
- Util/preferencias: `UserPreferences.java`, `LookAndFeelManager.java`, `I18n.java`.
- i18n: `p2p-client/src/main/resources/i18n/messages.properties`, `p2p-client/src/main/resources/i18n/messages_es.properties`.

## Qué No Modificar Sin Avisar

- `scripts/release.sh` y flujo de release.
- `.github/workflows/maven-publish.yml`, porque requiere actualización coordinada con el flujo de ramas real (`main` en workflow vs `develop`/`master` en `scripts/release.sh`/`RELEASE.md`).
- Versiones de Java/Maven/dependencias en `pom.xml`.
- Protocolo de eventos (`CoreEnvelope.name`/`CoreEnvelopeCodec`, payloads en `response`, serialización Base64/JSON) sin revisar compatibilidad entre clientes.
- Formato de estado/acciones de pizarra y notas si puede afectar sesiones mixtas entre versiones.
- Rutas de packaging y descarga/ubicación de `cloudflared`.
- Recursos binarios en `src/main/resources` salvo que el cambio sea intencional.
- Salidas generadas en `dist/`, `build/`, `packages/` o `p2p-client/target/`, salvo que el usuario pida build/package explícitamente.
- Archivos que puedan contener datos locales o credenciales, como properties locales fuera del ejemplo.

## Estado Actual Del Desarrollo

- La aplicación se compila con `./build.sh` en este entorno.
- La suite core se ejecuta con `cd p2p-client && mvn test` (302 tests default, 0 failures; 45 adicionales con `-Pperformance-tests`). Incluye tests de i18n para columnas de tabla (`FileTableModelI18nTest`), tests de regresión para los BUGS críticos (BUG-1 a BUG-5), `SecureIdentityStoreTest` (7), `InviteCodeTest` (7), `NotesEditorTest` (5, PENDIENTE-11 parcial), `QfolderLayoutTest` (12), `FileSystemEventStoreTest` (9), `FileSystemFileChunkStoreTest` (8), `CoreChunkTransferProtocolTest` (10) y `PerformanceMetricsTest` (8).
- Static analysis configurado con SpotBugs: `cd p2p-client && mvn -Pstatic-analysis verify`.
- `dist/`, `build/`, `packages/` y `p2p-client/target/` son artefactos generados/ignorados.
- Migración completada a core descentralizado event-sourced. El transporte real usa `P2PNetworkAdapter` sobre conexiones WebSocket directas entre peers; `CoreSyncBridge` y `WsHubService` fueron removidos del flujo activo.
- Existe `DirectBootstrap` para join directo sin hub, `InviteCode` con formato `ws://host:port?workspace=<base64>`, y `PeerCatalog` con intercambio de URLs durante sync.
- Los payloads core/sync usan JSON Base64 versionado (`QCOREJSON1`, `QCORESYNCJSON1`). Chunks usa formato string/Base64 propio con encabezado versionado `QCHUNK1`. Wire format completo encapsulado en `CoreEnvelope`/`CoreEnvelopeCodec` (Fase 11.8).
- `MeshProjector` mantiene cache de peer URLs/connections scoped por `workspaceId` (BUG-4 fix). `EventStore.listEventsAfter` usa umbral inclusivo para no perder eventos con el mismo timestamp que el snapshot (BUG-5 fix). `P2PNetworkAdapter` maneja eventos efímeros vía `onEphemeralCoreEvent` callback separado de `onCoreEventStored` (BUG-1 fix).
- `CoreApplicationService` mantiene un `EventService` compartido para validar eventos remotos (BUG-3 fix, preserva cache de `EventValidator`). `eventsSinceSnapshot` es `AtomicInteger` (BUG-6 fix). `Note` CRDT line-based con tombstones que bloquean resurrección por inserts tardíos. `Controller.insertNoteText`/`deleteNoteText` ahora reciben line index calculado desde character offset via `charOffsetToLineIndex` (BUG-2 fix).
- `EventPipeline` (Fase 11.6) está implementado y testeado pero el código de producción usa `EventService` directamente con instancia compartida; el pipeline queda como API preparado para migración futura.
- `ChunkReplicator` (Fase 11.7) ahora se activa vía `Controller.initializeCoreServices` (`enable()` + listener que dispara descargas). `Controller.activateWorkspaceFromCore` corre `core.runStartupCache()` para descargar archivos compartidos que aún no tenemos localmente (FASE11-FIX-2 resuelto).
- El flujo productivo usa firmas Ed25519 reales con almacenamiento encriptado via `SecureIdentityStore`. Pendiente de seguridad: migrar a un almacén seguro de plataforma (Keychain, libsecret, Windows Credential Manager) para distribución pública amplia.
- Hay simulaciones para malla de 10 usuarios, packet loss, latencia, particiones, churn, y estrés (500 eventos).
- La descarga de archivos es distribuida por chunks con verificación SHA-256, reintentos y fallback de error si no hay chunks P2P disponibles. Para evitar colisiones nombre/tamaño, `CoreChunkTransferCoordinator` prioriza el `fileId` de `QFile.md5=core:<fileId>`.
- El indexado local evita repetir `file.shared` si path/tamaño/mtime no cambiaron, con cache persistente por workspace.
- Hay tests de resiliencia de conexión intermitente (flapping, degradación, latencia+loss simultáneo).
- La suite incluye `CoreResilienceTest`, `CoreQfolderTest`, `CoreArchitectureTest`, `CoreWsClientTest`, `CoreControllerIntegrationTest`, `BackendExtendedSimulationTest`, `CoreWebSocketIntegrationTest`, `EventPipelineTest`, `EventValidatorTest`, `ChunkReplicatorTest`, `FileTableModelI18nTest`, `SecureIdentityStoreTest`, `InviteCodeTest` y `NotesEditorTest`. Tests de performance (`CoreResilienceTest`, `BackendExtendedSimulationTest`) estan etiquetados `@Tag("performance")` y excluidos por default; usar `mvn test -Pperformance-tests` para incluirlos.
- El workflow de GitHub Actions fue corregido para usar JDK 21 y `p2p-client/pom.xml`.
- Pendientes de migración: formalizar `QCHUNK1` a JSON/CBOR, proteger mejor la clave privada local. La replicación de chunks (PENDIENTE-12 en WORK_PLAN) ya esta integrada via `ChunkReplicator` + `Controller.runStartupCache()`.
- Existe guía manual `docs/MANUAL_E2E_CORE.md`, launcher mock `scripts/dev-3-instances.sh` y launcher Cloudflare real `scripts/dev-2-realinstances.sh`.
- Pendiente documental/coordinación: `RELEASE.md` y `scripts/release.sh` describen `develop`→`master`, mientras `.github/workflows/maven-publish.yml` corre sobre `main`; no cambiar el flujo sin decisión explícita.
- Configuración de qfolder: el panel de configuración incluye link `Abrir directorio de trabajo` (JLabel clickeable con cursor de mano), selector de idioma (español/inglés) persistente vía `UserPreferences`, y selector de look-and-feel Swing con cambio en vivo vía `LookAndFeelManager` + `SwingUtilities.updateComponentTreeUI`.
- Cambio de idioma en vivo: `Controller.refreshLanguageTexts()` retraduce textos estáticos (`View.applyI18nTexts()`), renombra tabs con `refreshTabTitles()`, reconstruye panel de configuración y tab de ayuda sin reiniciar. También llama `refreshAllFileTableColumns()` (que busca `TabListFile` recursivamente con `searchAndRefreshFileTables()`), `refreshAllTooltips()` (que recorre todos los contenedores de notas y pizarra), y actualiza el link `openWorkDirLabel`. `findTabByTitle()` tiene soporte bilingüe (español/inglés) via `getTabAlternatives()`. Los tabs se crean con `insertSystemTab()` que usa I18n para el título visible. Todos los textos visibles principales tienen keys i18n.
- I18n keys para tooltips de pizarra: `tooltip.whiteboard.select`, `.pencil`, `.text`, `.image`, `.arrow`, `.circle`, `.square`, `.rectangle`, `.triangle`, `.reduceSize`, `.increaseSize`, `.clear`, `.save`.
- Menú contextual en campo "Unir" (`jTextField2`) con opción "Pegar" (`menu.paste`) del portapapeles.
- Chat con input adaptativo: `scroll.setMinimumSize(0, 50)`, `panel.setMinimumSize(0, 40)` para que el chat y sus botones sean visibles sin agrandar la ventana. Botón adjuntar (`fileButton`) tiene la misma altura que el input (`chatInput.getPreferredSize().height`).
- Pegado de imágenes del portapapeles en Notas y Pizarra corre en thread separado (`Thread "clipboard-paste"` / `"notes-clipboard-paste"`) con mensajes de progreso vía `updateTransferProgress` antes de procesar ("Preparando pegado...", "Preparando imagen para notas...") para evitar bloqueo de UI.
- Cloudflare `ensureInstalled()` se ejecuta async en thread `"cloudflared-install"` para no bloquear el EDT durante la descarga (timeouts: 30s connect, 120s read). La UI se muestra inmediatamente y `cloudflared` está disponible cuando el usuario crea/se une a un workspace. `CloudflareInstaller` usa `CountDownLatch` para sincronizar; `ensurePeerEndpoint()` ejecuta la espera en thread `"wait-cloudflared"` y muestra mensajes de progreso ("Preparando túnel..." durante descarga, "Iniciando túnel Cloudflare..." durante creación del endpoint) sin congelar la UI. `mostrarErrorEnPantallaLogin()` actualiza ambos labels (`jLabel16` en jPanelJoin y `jLabel17` en jPanelCreateWorkspace) para que el mensaje sea visible en ambos paneles.
- Al crear workspace, todos los campos del panel de creación se deshabilitan durante la creación del túnel vía `setCreateWorkspaceControlsEnabled(false)` y se rehabilitan al terminar (éxito o error).
- La ventana principal es 20% más grande (ancho y alto) al inicio, usando factor `1.2` en `View.pack()`.
- Modo mock con demora configurable: `qfolder.tunnel.mock.delay` (default: 3000ms) simula la demora de creación del túnel Cloudflare. `CloudflareTunnel.start()` ejecuta la simulación en thread `"cloudflared-mock-delay"` antes de llamar `onUrlReady`. El mensaje muestra "Iniciando túnel (mock)..." para diferenciarlo del modo real. Útil para probar UX sin consumir límites diarios de Cloudflare.
- Complementos habilitados por defecto: Archivos, Chat, Pizarra, Notas y Miembros se habilitan automáticamente al iniciar la aplicación. Los checkboxes en Configuración reflejan este estado y los tabs se crean vía `applyComplementoVisibility()`. Ayuda y Log permanecen deshabilitados; el usuario puede habilitarlos manualmente si los necesita.
- `FileTableModel.getColumnName()` usa `I18n.get()` directamente en cada llamada (no cache array). `TabListFile` tiene métodos `updateBackButtonText()` y `updateMenuTexts()` para retraducir UI en cambio de idioma.
