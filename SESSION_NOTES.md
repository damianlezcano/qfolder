# SESSION_NOTES.md

Registro de avance por sesión para futuras sesiones de opencode.

## 2026-06-01 - Inicio Rápido Con Cloudflare Async

### Objetivo De La Sesión

- Mejorar el tiempo de inicio de la aplicación cuando se usa Cloudflare Tunnel (no mock), evitando que la descarga de `cloudflared` bloquee el hilo de UI.

### Cambios Realizados

- `CloudflareInstaller.ensureInstalled()` ahora se ejecuta en un thread separado (`"cloudflared-install"`) en vez de bloquear el EDT. La UI se muestra inmediatamente y `cloudflared` se descarga en background.
- `removeTemp()` ahora se ejecuta en un thread separado (`"remove-temp"`) para evitar I/O innecesaria en el EDT.
- El mensaje "Servicio listo" se loguea desde el thread de instalación de cloudflared cuando termina, no bloquea el arranque.
- Se agregó `CountDownLatch` en `CloudflareInstaller` para sincronizar la instalación async con `ensurePeerEndpoint()`.
- `ensurePeerEndpoint()` ahora espera a que `CloudflareInstaller.awaitInstallation(150)` termine antes de crear `CloudflareTunnel`, evitando el error "No existe el fichero o el directorio" cuando el usuario crea un workspace antes de que la descarga termine.
- Si la instalación falla o hace timeout, `ensurePeerEndpoint()` retorna error al callback en vez de intentar ejecutar un binario inexistente.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/client/CloudflareInstaller.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `cd p2p-client && mvn clean package -DskipTests` -> OK
- `cd p2p-client && mvn test` -> 227 tests, 0 failures
- `./build.sh` -> OK, `dist/qfolder.jar` generado

### Resultado

- La UI ahora se muestra y responde inmediatamente al iniciar, sin esperar la descarga de `cloudflared` (que puede tardar 5-150 segundos dependiendo de la red).
- El usuario puede interactuar con el campo de join y botón inmediatamente.
- `cloudflared` se descarga en background y `ensurePeerEndpoint()` espera sincronizadamente a que termine antes de iniciar el túnel.
- Si el usuario crea un workspace mientras la descarga está en progreso, ve el mensaje "Esperando instalación de cloudflared..." y la creación se completa automáticamente cuando la descarga termina.

### Mejora Adicional - Indicador Visual De Espera

- `ensurePeerEndpoint()` ahora ejecuta la espera de instalación en un thread separado (`"wait-cloudflared"`) para no bloquear el EDT.
- Mientras espera la instalación, muestra el mensaje "Preparando túnel... por favor espere" en el label de estado de login.
- Cuando la instalación termina, muestra "Iniciando túnel Cloudflare..." mientras se crea el endpoint.
- Si `cloudflared` ya está instalado, muestra directamente "Iniciando túnel Cloudflare..." al crear/unirse a un workspace.
- El mensaje se limpia automáticamente cuando el túnel está listo y el workspace se crea exitosamente.
- Si la instalación falla o hace timeout, muestra el error correspondiente sin congelar la UI.
- **Fix**: `mostrarErrorEnPantallaLogin()` ahora actualiza ambos labels (`jLabel16` en jPanelJoin y `jLabel17` en jPanelCreateWorkspace) para que el mensaje sea visible en ambos paneles.

### Mejora UX - Bloqueo De Campos Y Tamaño De Ventana

- Al presionar el botón "Crear" workspace, todos los campos del panel de creación se deshabilitan (nombre, checkbox de contraseña, campos de contraseña, botones crear/cancelar) para evitar ediciones durante la creación del túnel.
- Se agregó método `setCreateWorkspaceControlsEnabled(boolean)` para controlar el estado de los controles del panel de creación de workspace.
- Los controles se rehabilitan automáticamente cuando la creación termina (éxito o error).
- La ventana principal ahora es 20% más grande (ancho y alto) al inicio, cambiando de `1.1` a `1.2` en el método `pack()` de `View.java`.

### Simulación De Demora En Túnel Mock

- Se agregó propiedad `qfolder.tunnel.mock.delay` (default: 3000ms) para simular la demora de creación del túnel Cloudflare en modo mock.
- `CloudflareTunnel.start()` ahora ejecuta la simulación en un thread separado (`"cloudflared-mock-delay"`) con la demora configurable antes de llamar `onUrlReady`.
- El mensaje de progreso muestra "Iniciando túnel (mock)..." cuando está en modo mock, diferenciándolo del mensaje "Iniciando túnel Cloudflare..." usado con cloudflared real.
- Esto permite probar el comportamiento de la UI y el flujo de bloqueo de campos sin consumir los límites diarios de creación de túneles de Cloudflare.
- Uso: `java -Dqfolder.tunnel.mock=true -Dqfolder.tunnel.mock.delay=5000 -jar dist/qfolder.jar` para simular 5 segundos de demora.

### Complementos Habilitados Por Defecto

- Todos los complementos (Archivos, Chat, Pizarra, Notas, Miembros) ahora están habilitados por defecto al iniciar la aplicación.
- Los checkboxes en la solapa de Configuración reflejan correctamente el estado habilitado al inicio.
- Los tabs correspondientes se crean automáticamente durante la inicialización vía `applyComplementoVisibility()`.
- "Ayuda" y "Log" permanecen deshabilitados por defecto; el usuario puede habilitarlos manualmente si los necesita.
- El usuario puede deshabilitar cualquier complemento desde la solapa de Configuración en cualquier momento.

### Pendientes

- Validar manualmente con `./scripts/dev-3-instances.sh` que el mensaje de espera y el bloqueo de campos funcionan correctamente en modo mock.
- Considerar mover `loadOrCreateLocalIdentity()` fuera del EDT si la generación de claves Ed25519 sigue siendo perceptible en primera ejecución (~100-500ms).

## 2026-05-24 - Robustez Archivos Y Descargas

### Objetivo De La Sesión

- Mejorar la UX de descargas remotas por chunks, selección/listado de archivos, borrado/refresco y apertura de adjuntos.

### Cambios Realizados

- Se agregó cancelación explícita en `CoreChunkTransferCoordinator.cancel(transferId)` para abortar descargas activas y limpiar timers/estado interno.
- La barra de transferencia ahora se muestra como fila con botones `Reintentar` y `Cancelar`.
- `Reintentar` cancela el transfer anterior, crea un `transferId` nuevo y vuelve a solicitar chunks manteniendo link de chat/open pendiente cuando corresponde.
- `Cancelar` cancela la transferencia activa, remueve la barra y limpia estado asociado.
- Se limita la tabla de Archivos a selección simple (`ListSelectionModel.SINGLE_SELECTION`).
- Tras eliminar un archivo local, se remueve su entrada de cache de indexación y se refresca el listado local visible.
- `Actualizar` en vistas no locales refresca visualmente tablas/core metadata en vez de depender del flujo legacy remoto.
- `Abrir` sobre un archivo remoto mantiene operación `OPEN`: descarga por chunks y luego abre el archivo con la app predeterminada del sistema.
- Los links de archivos en Chat ahora registran el `fileId`; si el archivo no existe localmente, clic descarga y abre. Si ya existe localmente, solo abre.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/CoreChunkTransferCoordinator.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/components/TabListFile.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `cd p2p-client && mvn test` -> primer intento falló en `CoreWebSocketIntegrationTest.directBootstrapJoinApprovalUsesSameLiveSocket`, test WebSocket intermitente; rerun puntual OK.
- `cd p2p-client && mvn -Dtest=CoreWebSocketIntegrationTest#directBootstrapJoinApprovalUsesSameLiveSocket test` -> OK.
- `cd p2p-client && mvn test` -> 217 tests, 0 failures.
- `./build.sh` -> OK, `dist/qfolder.jar` generado.

### Pendientes

- Validar manualmente con `./scripts/dev-2-realinstances.sh`: descarga remota atascada/reintento, cancelar, abrir remoto, link de chat descarga+abre, borrar+actualizar.

## 2026-05-24 - Notas Con Imagen En Dev Real

### Objetivo De La Sesión

- Diagnosticar por qué imágenes insertadas en Notas desde instancia A no se veían en B al usar `scripts/dev-2-realinstances.sh`.

### Cambios Realizados

- Se revisaron logs reales en `/tmp/qfolder-real/logs/devA.log` y `devB.log`.
- Se confirmó que la malla P2P sí se formó y que `note.updated` llegaba aceptado al peer remoto.
- Se corrigió `Controller.applyCoreStateToVisuals` para no usar `lastSentNotesState` como guardia de aplicación remota de notas ricas.
- Se agregó `lastAppliedNotesState` y comparación contra `serializeNotesState()` para decidir si aplicar el snapshot remoto.
- Se agregaron logs diagnósticos `[NOTES]` con resumen `QNOTES2`, cantidad de text runs e imágenes serializadas/aplicadas.
- Se agregó test `richNotesImageSnapshotSurvivesPeerSync` para asegurar que un snapshot `QNOTES2` con línea `I|...` de imagen sobrevive al sync core entre peers.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreControllerIntegrationTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `cd p2p-client && mvn test` -> 217 tests, 0 failures.
- `./build.sh` -> OK, `dist/qfolder.jar` generado.

### Pendientes

- Repetir prueba manual con `./scripts/dev-2-realinstances.sh` insertando imagen en Notas de A y verificar en B.
- Revisar logs por `QNOTES2 ... images=1` en serializado y aplicado remoto.

## 2026-05-19

### Objetivo De La Sesión

- Analizar el proyecto y crear documentación persistente para futuras sesiones de opencode.

### Cambios Realizados

- Se creó `AGENTS.md` con contexto permanente del proyecto.
- Se creó `SESSION_NOTES.md` con estructura para registrar sesiones.
- Se documentaron stack, arquitectura, estructura, comandos, convenciones y reglas operativas inferidas desde archivos reales del repo.

### Archivos Modificados

- `AGENTS.md`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- Pendiente de completar. En esta sesión se usaron herramientas de lectura/búsqueda de opencode y no se ejecutaron comandos de build/test/lint.

### Errores Encontrados

- No se encontraron errores durante la creación de documentación.
- Se observó que `.github/workflows/maven-publish.yml` parece desactualizado respecto del proyecto actual: usa JDK 11 y busca `pom.xml` en raíz, mientras el módulo Maven real está en `p2p-client/pom.xml` y requiere Java 21.

### Pendientes

- Completar comando de lint si se agrega configuración.
- Completar comando de test si se agregan tests automatizados.
- Validar/actualizar workflow de GitHub Actions si se decide mantener CI.
- Mantener esta bitácora actualizada en futuras sesiones.

### Próximos Pasos

- Ejecutar `./build.sh` después de próximos cambios de código.
- Si se trabaja en red/eventos/pizarra, revisar primero `Controller.java`, `WsHubService.java`, `WsClient.java`, `EmbeddedWebSocketServer.java`, `Event.java` y `EventUtils.java`.

## 2026-05-19 - Mejoras Para Latencia En Solapas

### Objetivo De La Sesión

- Revisar y robustecer las solapas frente a latencia al conectarse mediante Cloudflare Tunnel.

### Cambios Realizados

- Archivos: se agregó `requestId` a navegación remota de directorios para ignorar respuestas viejas que lleguen tarde.
- Chat: se agregó deduplicación de mensajes por `ChatMessage.id`.
- Notas: se agregó control por `Event.sequence` para ignorar snapshots remotos desactualizados.
- Complementos: se agregó control por `Event.sequence` por complemento para evitar aplicar estados viejos.
- Transferencias: se redujo el tamaño de chunk de archivos y se agregó timeout explícito a conexión directa.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `./build.sh`

### Errores Encontrados

- No se encontraron errores de compilación.

### Pendientes

- Probar con varias instancias reales vía Cloudflare Tunnel para validar comportamiento bajo latencia real.
- Evaluar migrar notas de snapshots completos a acciones editables/mergeables si sigue habiendo pisado entre usuarios simultáneos.

### Próximos Pasos

- Ejecutar prueba manual de 2 o 3 clientes con `qfolder.tunnel.mock=false`.
- Verificar navegación rápida en archivos remotos, mensajes duplicados, edición simultánea de notas y transferencia de archivos grandes.

## 2026-05-19 - Solapa De Miembros

### Objetivo De La Sesión

- Agregar una solapa ocultable para visualizar miembros del workspace, estado de conexión y URL pública del túnel.

### Cambios Realizados

- Se agregó complemento `Miembros` con checkbox en la solapa de Configuración.
- Se agregó solapa `Miembros` con tabla de nombre, estado, hora de conexión y URL pública.
- Se registra el estado de miembros conocidos para conservar usuarios desconectados en la tabla.
- Se refresca la tabla al conectar/reconectar, desconectar y cambiar nombres.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `./build.sh`

### Errores Encontrados

- No se encontraron errores de compilación.

### Pendientes

- Probar manualmente con varios clientes vía Cloudflare Tunnel para confirmar que las URLs públicas se propaguen como se espera.

### Próximos Pasos

- Validar visualmente que el checkbox `Miembros` muestre/oculte la solapa en todos los peers.
- Verificar que un usuario desconectado quede marcado como `Desconectado` en vez de desaparecer.

## 2026-05-19 - Core Descentralizado Testeable

### Objetivo De La Sesión

- Crear una primera capa core independiente de Swing, red real y disco real para modelar workspaces P2P descentralizados basados en eventos.

### Cambios Realizados

- Se agregó JUnit 5 al módulo Maven.
- Se crearon modelos core para eventos, workspace, miembros, peers, chat, notas, archivos y pizarra.
- Se crearon puertos `EventStore`, `AuthProvider`, `NetworkAdapter`, `ClockProvider`, `IdGenerator` y `FileChunkStore`.
- Se implementaron adaptadores en memoria para eventos, chunks, reloj e IDs.
- Se implementó `TokenAuthProvider` y placeholder `PublicKeyAuthProvider`.
- Se implementaron servicios core para workspace, membresía, eventos, chat, notas, archivos, pizarra, mesh y sync.
- Se implementó `SimulatedNetworkAdapter` con nodos virtuales y gossip por rondas.
- Se agregaron 12 tests JUnit 5 para creación de workspace, consenso, reconexión, mesh, gossip, deduplicación, recuperación de estado, efímeros, pizarra, archivos por chunks, sync faltante y revocación.

### Archivos Modificados

- `p2p-client/pom.xml`
- `p2p-client/src/main/java/org/q3s/p2p/core/**`
- `p2p-client/src/main/java/org/q3s/p2p/ports/**`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/**`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreArchitectureTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Errores Encontrados

- Un test inicial de consenso falló porque B no podía aprobar a C sin estar previamente autorizado. Se agregó soporte básico para `member.role.changed` y se ajustó el escenario.

### Pendientes

- Adaptar gradualmente la UI Swing para usar `WorkspaceService`, `MembershipService`, `ChatService`, `FileService`, `WhiteboardService`, `SyncEngine` y `MeshPolicy`.
- Implementar `FileSystemEventStore` y stores reales de workspace/chunks.
- Implementar red real descentralizada sobre `NetworkAdapter` sin hub fijo.
- Implementar snapshots reales y recuperación snapshot + eventos posteriores.
- Reemplazar `TokenAuthProvider` por `PublicKeyAuthProvider` cuando se decida agregar criptografía.

### Próximos Pasos

- Empezar a mover flujos de `Controller` hacia servicios core, comenzando por chat o membresía.
- Agregar persistencia filesystem para eventos antes de integrar el core en ejecución real.

## 2026-05-19 - Backend Verificado Con 10 Usuarios

### Objetivo De La Sesión

- Completar pendientes iniciales del backend core y validarlo por simulación hasta 10 usuarios antes de integrar la UI.

### Cambios Realizados

- Se agregaron stores filesystem básicos para eventos y chunks.
- Se agregó `SnapshotService` para snapshot básico de `WorkspaceState`.
- Se amplió `SimulatedNetworkAdapter` con conexión por `MeshPolicy`, reporte de topología y lista de aristas.
- Se agregaron tests extendidos para filesystem, snapshots, chunks, consenso con 10 miembros y gossip en malla de 10 usuarios.
- Se corrigió el orden de reconstrucción en `FileSystemEventStore` ordenando por `createdAt` y `eventId`.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/adapters/filesystem/FileSystemEventStore.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/filesystem/FileSystemFileChunkStore.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/SimulatedNetworkAdapter.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/state/SnapshotService.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/model/*.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/state/WorkspaceState.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/BackendExtendedSimulationTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Errores Encontrados

- El primer `mvn test` falló en consenso con 10 miembros porque `FileSystemEventStore` listaba eventos por nombre de archivo y no por orden temporal. Se corrigió ordenando eventos por `createdAt` y `eventId`.

### Pendientes

- Integrar gradualmente la UI Swing con el core nuevo.
- Reemplazar el hub/WebSocket actual por un `NetworkAdapter` real descentralizado.
- Mejorar snapshots para recuperar desde snapshot + eventos posteriores con marcador de último evento aplicado.

### Próximos Pasos

- Crear una capa de aplicación que conecte `Controller` con `WorkspaceService`, `MembershipService`, `ChatService`, `FileService`, `WhiteboardService`, `SyncEngine` y `MeshPolicy`.
- Definir el formato estable en disco para eventos antes de usarlo como almacenamiento de producción.

### Resultado De Simulación De Malla

```text
U1 -> [U2, U3, U4, U6] (degree=4)
U2 -> [U1, U3, U4, U7] (degree=4)
U3 -> [U1, U2, U5, U8] (degree=4)
U4 -> [U1, U2, U5, U9] (degree=4)
U5 -> [U3, U4, U6, U10] (degree=4)
U6 -> [U5, U1, U7] (degree=3)
U7 -> [U6, U2, U8] (degree=3)
U8 -> [U7, U3, U9] (degree=3)
U9 -> [U8, U4, U10] (degree=3)
U10 -> [U9, U5] (degree=2)

CONEXIONES: [U1 -- U2, U1 -- U3, U1 -- U4, U1 -- U6, U2 -- U3, U2 -- U4, U2 -- U7, U3 -- U5, U3 -- U8, U4 -- U5, U4 -- U9, U5 -- U6, U6 -- U7, U7 -- U8, U8 -- U9, U10 -- U9, U10 -- U5]
```

## 2026-05-19 - Bridge Gradual UI Swing A Core

### Objetivo De La Sesión

- Iniciar la migración gradual de la UI Swing hacia el core sin cambiar la interfaz visual ni romper el protocolo actual.

### Cambios Realizados

- Se creó `CoreApplicationService` como fachada única para la UI.
- Se inicializa el core con stores filesystem bajo `~/.p2p-collab`.
- Al crear workspace desde Swing se crea/adjunta una sesión core.
- Al recibir bienvenida de workspace se adjunta la sesión core existente.
- Al enviar un mensaje de chat desde Swing se genera también un evento core persistente, manteniendo el envío WebSocket actual.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Errores Encontrados

- No se encontraron errores. Los 16 tests siguen pasando y la app compila.

### Pendientes

- Migrar flujo de miembros al core.
- Migrar archivos al `FileService` y `FileChunkStore`.
- Migrar pizarra al `WhiteboardService`.
- Migrar notas al `NoteService`.
- Implementar `NetworkAdapter` real para reemplazar gradualmente el hub WebSocket actual.

### Próximos Pasos

- Continuar con membresía, porque es el siguiente eje para eliminar el concepto de nodo coordinador fijo.

## 2026-05-19 - Bridge De Membresía Al Core

### Objetivo De La Sesión

- Continuar la migración gradual conectando el flujo actual de membresía Swing/WebSocket con eventos persistentes del core.

### Cambios Realizados

- Se agregaron métodos de membresía a `CoreApplicationService`.
- La creación de workspace desde Swing ahora asegura un `workspace.created` core con el ID público real del workspace.
- Las solicitudes de ingreso actuales registran `member.join.requested` en el core.
- Las aprobaciones actuales registran `member.join.approval` y alta básica del miembro conocido en el core.
- Los miembros vistos por el cliente se sincronizan con el core sin cambiar la UI visual.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Errores Encontrados

- No se encontraron errores. Los 16 tests siguen pasando y la app compila.

### Pendientes

- Migrar archivos al `FileService`/`FileChunkStore`.
- Migrar pizarra al `WhiteboardService`.
- Migrar notas al `NoteService`.
- Reemplazar transporte WebSocket hub por `NetworkAdapter` real descentralizado.

### Próximos Pasos

- Seguir con archivos, porque ya existe `FileService` con chunks y es una buena transición desde el flujo actual.

## 2026-05-19 - Bridge De Archivos Al Core

### Objetivo De La Sesión

- Continuar la migración gradual conectando el flujo actual de archivos Swing/WebSocket con `FileService` y `FileChunkStore` del core.

### Cambios Realizados

- `CoreApplicationService` ahora permite registrar archivos desde `Path` e indexar directorios compartidos.
- Al refrescar archivos locales desde Swing, los archivos regulares se registran en el core como `file.shared` con metadata y chunks.
- Al enviar adjuntos por chat, el archivo copiado al área local de archivos también se registra en el core.
- Se mantiene la UI visual y el protocolo de transferencia actual como puente temporal.
- Se actualizó `AGENTS.md` para reflejar el core nuevo, tests JUnit 5 y la migración gradual en curso.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Errores Encontrados

- No se encontraron errores. Los 16 tests siguen pasando y la app compila.

### Pendientes

- Evitar reindexar repetidamente archivos ya registrados si el área local de archivos es grande.
- Migrar descarga/apertura de archivos para resolver desde `FileChunkStore` cuando el contenido exista localmente.
- Migrar pizarra al `WhiteboardService`.
- Migrar notas al `NoteService`.

### Próximos Pasos

- Seguir con pizarra, porque ya fue estabilizada por acciones y puede mapearse a eventos core persistentes de trazos/objetos.

## 2026-05-19 - Bridge De Pizarra Al Core

### Objetivo De La Sesión

- Conectar la pizarra Swing actual con `WhiteboardService` del core sin modificar el aspecto visual.

### Cambios Realizados

- Se agregaron eventos core persistentes para objetos de pizarra: `whiteboard.object.added`, `whiteboard.object.moved`, `whiteboard.object.deleted` y `whiteboard.cleared`.
- `WhiteboardService` ahora registra acciones de objetos además de trazos terminados.
- `CoreApplicationService` expone métodos para guardar trazos terminados y acciones de objetos.
- `WhiteboardCanvas` registra en el core los trazos terminados y acciones `add/update/delete/clear`, manteniendo el protocolo visual/WebSocket existente.
- Se agregó un test de eventos persistentes de objetos de pizarra.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/core/events/EventTypes.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/whiteboard/WhiteboardService.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreArchitectureTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Errores Encontrados

- No se encontraron errores. Los 17 tests pasan y la app compila.

### Pendientes

- Materializar objetos de pizarra en `WorkspaceStateBuilder` si se necesita reconstrucción completa visual desde core.
- Migrar notas al `NoteService`.
- Reemplazar transporte WebSocket hub por `NetworkAdapter` real descentralizado.

### Próximos Pasos

- Seguir con notas, que todavía usan snapshots completos en la UI y deben empezar a registrarse en `NoteService`.

## 2026-05-20 - Bridge De Notas Al Core

### Objetivo De La Sesión

- Conectar las notas Swing actuales con `NoteService` del core sin modificar la UI visual ni el protocolo WebSocket existente.

### Cambios Realizados

- `CoreApplicationService` ahora inicializa `NoteService` y expone `updateNote`.
- `Controller` registra en el core el snapshot serializado de notas (`shared-notes`) antes de enviar actualizaciones locales.
- `Controller` registra en el core snapshots remotos recibidos antes de aplicarlos en el `JTextPane`.
- Se agregó un test para verificar que la fachada de aplicación persiste y reconstruye notas compartidas desde `WorkspaceStateBuilder`.
- Se actualizó `AGENTS.md` para reflejar que notas y pizarra ya registran eventos core.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreArchitectureTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Errores Encontrados

- No se encontraron errores. Los 18 tests pasan y la app compila.

### Pendientes

- Las notas siguen usando snapshots completos serializados por la UI; queda pendiente migrarlas a operaciones mergeables si se necesita edición concurrente real.
- Materializar objetos de pizarra en `WorkspaceStateBuilder` si se necesita reconstrucción visual completa desde core.
- Reemplazar transporte WebSocket hub por `NetworkAdapter` real descentralizado.

### Próximos Pasos

- Empezar a implementar un `NetworkAdapter` real/puente sobre el WebSocket actual para que el core pueda sincronizar eventos entre peers, reduciendo gradualmente la dependencia del hub Swing/WebSocket.

## 2026-05-20 - Puente De Eventos Core Sobre WebSocket

### Objetivo De La Sesión

- Implementar un primer puente de transporte para que eventos del core viajen por el protocolo WebSocket actual sin reemplazar todavía el hub.

### Cambios Realizados

- `WebSocketNetworkAdapter` dejó de ser placeholder y ahora implementa `send`, `broadcast`, `peers` y decodificación de eventos core.
- Se agregó el evento legacy `Core event` para transportar eventos `org.q3s.p2p.core.model.Event` en `response`.
- El payload core usa serialización Java Base64 porque JSON-B/Yasson no deserializa correctamente el record `Event` cuando hay campos null como `signature`.
- `CoreApplicationService` ahora expone `receiveRemoteEvent` para aceptar/deduplicar eventos remotos del workspace actual.
- `Controller` inicializa `WebSocketNetworkAdapter`, publica eventos core locales de chat, archivos indexados, pizarra y notas, y acepta eventos core remotos recibidos por WebSocket.
- Se agregó un test para envolver y decodificar eventos core mediante `WebSocketNetworkAdapter`.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/WebSocketNetworkAdapter.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreArchitectureTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Errores Encontrados

- El primer test del adapter falló porque JSON-B/Yasson no pudo deserializar el record core (`JsonbCreator parameter signature is missing`). Se reemplazó el encoding interno del puente por serialización Java Base64.

### Resultado

- Los 19 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Probar manualmente con 2 o 3 instancias para confirmar que los eventos core remotos llegan y se deduplican correctamente.
- Reemplazar el payload Java-serialization por un formato estable/versionado antes de considerar este transporte como producción.
- Hacer que el core use `SyncEngine`/`NetworkAdapter` de forma más directa, no solo mediante llamadas puente desde `Controller`.

### Próximos Pasos

- Agregar handshake/sync inicial de IDs de eventos core al entrar al workspace para pedir eventos faltantes, no solo recibir eventos nuevos.

## 2026-05-20 - Sync Inicial De Eventos Core

### Objetivo De La Sesión

- Agregar un intercambio mínimo de IDs de eventos core al entrar al workspace para recuperar eventos faltantes, no solo recibir eventos nuevos.

### Cambios Realizados

- `WebSocketNetworkAdapter` ahora define `Core sync request` y `Core sync response` además de `Core event`.
- `Core sync request` transporta el set de IDs de eventos conocidos del peer.
- `Core sync response` transporta una lista de eventos core faltantes y se envía directo al peer solicitante con el prefijo legacy `__to:<peerId>:`.
- `CoreApplicationService` expone `eventIds`, `missingEvents` y `receiveRemoteEvents`.
- `Controller` solicita sync core después de entrar al workspace y publicar sus archivos iniciales.
- `Controller` responde requests calculando eventos faltantes desde `EventStore` y aplica responses con deduplicación del core.
- Se agregó un test para serializar/decodificar request y response del sync core.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/WebSocketNetworkAdapter.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreArchitectureTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Errores Encontrados

- El primer test de sync falló porque intentaba decodificar una respuesta con prefijo `__to:<peerId>:`; en ejecución real el hub quita ese prefijo antes de entregar el evento. Se ajustó el test para simular esa entrega.

### Resultado

- Los 20 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Probar manualmente con 2 o 3 instancias para verificar que un peer nuevo recupere eventos core históricos.
- Reemplazar serialización Java Base64 del puente por formato estable/versionado.
- Integrar más directamente `SyncEngine` para coordinar requests/responses en vez de hacerlo desde `Controller`.

### Próximos Pasos

- Mover la lógica de sync del puente fuera de `Controller` hacia un servicio/adaptador dedicado, o empezar a reconstruir estado visual desde eventos core para validar el camino inverso core→UI.

## 2026-05-20 - CoreSyncBridge, Core A UI Y Simulaciones P2P

### Objetivo De La Sesión

- Sacar la lógica de sync core de `Controller`, empezar reconstrucción visual desde eventos core y agregar escenarios de resiliencia/distribución P2P.

### Cambios Realizados

- Se creó `CoreSyncBridge` para coordinar publicación de eventos core, solicitudes de sync, respuestas de eventos faltantes y aplicación de respuestas.
- `Controller` delega en `CoreSyncBridge` el manejo de `Core event`, `Core sync request` y `Core sync response`.
- `Controller` aplica `WorkspaceState` reconstruido desde core a la UI para notas compartidas (`shared-notes`) y mensajes de chat livianos.
- Se agregó `DistributedChunkPlanner` para repartir descargas explícitas de chunks entre múltiples miembros disponibles.
- `SimulatedNetworkAdapter` ahora permite buscar nodos por ID y desconectar completamente un nodo.
- Se agregó escenario de 10 miembros donde se desconectan progresivamente hasta quedar cero enlaces vivos.
- Se agregó escenario de reconexión donde un miembro desconectado recupera eventos faltantes desde peers al volver.
- Se agregó escenario de nuevo miembro que pide eventos al workspace completo, reconstruye estado por eventos y planifica descarga paralelizable de chunks sin transferir contenido automáticamente.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/CoreSyncBridge.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/SimulatedNetworkAdapter.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/WebSocketNetworkAdapter.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/files/DistributedChunkPlanner.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/BackendExtendedSimulationTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- Los 23 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Probar manualmente con varias instancias reales para validar sync core sobre WebSocket legacy.
- Reemplazar serialización Java Base64 del puente por formato estable/versionado.
- Conectar descarga real de chunks desde múltiples miembros en la UI/transferencia actual.
- Extender core→UI a archivos y pizarra completa cuando el estado visual pueda reconstruirse sin snapshots legacy.

### Próximos Pasos

- Implementar protocolo real de disponibilidad de chunks y descarga paralela explícita por archivo, usando la planificación de `DistributedChunkPlanner` como base.

## 2026-05-20 - Protocolo Explícito De Chunks Distribuidos

### Objetivo De La Sesión

- Implementar un protocolo inicial de disponibilidad y descarga explícita de chunks para que más miembros disponibles puedan acelerar la descarga de contenido.

### Cambios Realizados

- Se agregó `CoreChunkTransferProtocol` con mensajes legacy temporales:
  - `Core chunk availability request`
  - `Core chunk availability response`
  - `Core chunk request`
  - `Core chunk response`
- `CoreApplicationService` expone disponibilidad de chunks, lectura, escritura y reconstrucción de archivo desde `FileChunkStore`.
- `Controller` intenta descargar por chunks distribuidos cuando el archivo existe en metadata core.
- La solicitud de disponibilidad se hace al workspace; cada miembro responde solo con los chunks que tiene.
- `DistributedChunkPlanner` reparte los requests de chunks entre miembros disponibles.
- Al recibir todos los chunks, la UI reconstruye el archivo localmente y notifica cambio de archivos.
- Si no hay metadata core suficiente, el flujo cae al mecanismo legacy de transferencia de archivo completo.
- Se agregaron pruebas de roundtrip del protocolo de chunks.
- Se agregó prueba de capacidad: con más miembros disponibles baja la carga máxima por peer.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/CoreChunkTransferProtocol.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreArchitectureTest.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/BackendExtendedSimulationTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- Los 25 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Probar manualmente descarga distribuida con varias instancias reales.
- Validar integridad final del archivo reconstruido contra hash de metadata antes de marcar la descarga como completa.
- Mejorar planificación para reintentos/timeouts si un peer no entrega un chunk solicitado.
- Reemplazar protocolos string/Base64 temporales por formato estable/versionado.

### Próximos Pasos

- Agregar verificación de hash final, reintentos de chunks y fallback parcial al flujo legacy si no se completa la descarga distribuida.

## 2026-05-20 - Hardening De Descarga Distribuida

### Objetivo De La Sesión

- Agregar verificación de integridad, reintentos y fallback al flujo legacy para la descarga distribuida por chunks.

### Cambios Realizados

- `Controller` ahora valida el SHA-256 del archivo reconstruido contra la metadata core antes de marcar la descarga como exitosa.
- Se agregaron reintentos básicos para chunks faltantes; si después de dos reintentos no se completa, se usa la transferencia legacy de archivo completo.
- Si el hash final no coincide, se descarta el camino distribuido y se cae al flujo legacy.
- Se conserva el `QFile` original para abrir el archivo tras una descarga distribuida cuando la operación pedida era `open`.
- Se agregó limpieza centralizada del estado temporal de transferencias core.
- Se agregó test de integridad reconstruida: el hash de metadata de `file.shared` debe coincidir con el archivo reconstruido desde chunks.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreArchitectureTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- Los 25 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Probar manualmente con varias instancias reales y archivos medianos/grandes.
- Mejorar reintentos para elegir peers alternativos por chunk y medir latencia/capacidad.
- Reemplazar protocolos string/Base64 temporales por formato estable/versionado.

### Próximos Pasos

- Reducir dependencia del flujo legacy de archivos completos y mover más lógica de transferencia al core/adaptadores dedicados.

## 2026-05-20 - Extracción De Transferencia Distribuida

### Objetivo De La Sesión

- Sacar la lógica de transferencia distribuida por chunks de `Controller` hacia un componente dedicado.

### Cambios Realizados

- Se creó `CoreChunkTransferCoordinator`.
- El coordinador maneja disponibilidad, planificación, requests, responses, reintentos, verificación SHA-256 y fallback legacy.
- `Controller` ahora delega mensajes de chunk al coordinador.
- `Controller` conserva solo responsabilidades de UI/locales: iniciar descarga, mostrar progreso, escribir archivo final, abrir archivo si corresponde y ejecutar fallback legacy.
- Se eliminó del controlador el estado interno de transferencias core (`availability`, `receivedChunks`, `retryCounts`, etc.).

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/CoreChunkTransferCoordinator.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- Los 25 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Probar manualmente descarga distribuida con varias instancias reales.
- Convertir los protocolos temporales string/Base64 a formato estable/versionado.
- Seguir reduciendo lógica legacy en `Controller`, especialmente pizarra/archivos completos.

### Próximos Pasos

- Empezar a extraer pizarra core→UI completa o estabilizar el formato de mensajes core/transferencia antes de más funcionalidades.

## 2026-05-20 - Payloads Versionados Del Puente Core

### Objetivo De La Sesión

- Estabilizar mínimamente los formatos temporales de mensajes core/transferencia con encabezados versionados.

### Cambios Realizados

- `WebSocketNetworkAdapter` ahora emite y valida encabezado `QCORE1` para `Core event`.
- `WebSocketNetworkAdapter` ahora emite y valida encabezado `QCORESYNC1` para `Core sync request` y `Core sync response`.
- `CoreChunkTransferProtocol` ahora emite y valida encabezado `QCHUNK1` para disponibilidad y transferencia de chunks.
- Se actualizaron tests de roundtrip para verificar los encabezados de versión.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/WebSocketNetworkAdapter.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/CoreChunkTransferProtocol.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreArchitectureTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- Los 25 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Reemplazar serialización Java Base64 interna por JSON/CBOR/otro formato estable de producción.
- Probar compatibilidad entre instancias con distintas versiones cuando exista release pública.

### Próximos Pasos

- Continuar con reconstrucción visual completa desde core, empezando por pizarra/archivos, o definir el formato estable final de payloads antes de ampliar el protocolo.

## 2026-05-20 - Reconstrucción De Pizarra Desde Core

### Objetivo De La Sesión

- Empezar la reconstrucción visual completa desde eventos core, comenzando por pizarra.

### Cambios Realizados

- `WorkspaceState` ahora materializa objetos de pizarra en `whiteboardObjects`.
- `WorkspaceStateBuilder` aplica `whiteboard.object.added`, `whiteboard.object.moved`, `whiteboard.object.deleted` y `whiteboard.cleared`.
- `whiteboard.cleared` limpia objetos y trazos del estado reconstruido.
- `Controller` construye un snapshot `QWBSTATE1` desde `WorkspaceState` y lo aplica al `WhiteboardCanvas` cuando llega estado core/sync.
- La reconstrucción incluye objetos persistidos y trazos básicos como líneas negras.
- Se agregó test de reconstrucción de estado actual de objetos de pizarra desde eventos.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/core/state/WorkspaceState.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/state/WorkspaceStateBuilder.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreArchitectureTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- Los 26 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Persistir/reconstruir color, grosor y herramientas exactas de trazos libres en core, no solo puntos básicos.
- Evitar conflictos entre cambios locales recientes de pizarra y snapshots core reconstruidos en casos de edición simultánea.
- Extender reconstrucción core→UI para archivos como vista de metadata core si hace falta.

### Próximos Pasos

- Enriquecer eventos core de trazos de pizarra con estilo completo o avanzar con vista de archivos derivada de metadata core.

## 2026-05-20 - Estilo De Trazos De Pizarra En Core

### Objetivo De La Sesión

- Persistir y reconstruir color/grosor reales de trazos libres de pizarra desde eventos core.

### Cambios Realizados

- `WhiteboardStroke` ahora incluye `color` y `width`.
- `WhiteboardService.finishStroke` acepta estilo del trazo y conserva overload compatible con valores por defecto.
- `CoreApplicationService.finishWhiteboardStroke` expone color/grosor.
- `Controller.recordWhiteboardStrokeInCore` extrae color y grosor desde operaciones `L|...|color|width` del canvas.
- La reconstrucción core→UI de pizarra usa color/grosor reales para generar el snapshot `QWBSTATE1`.
- Se actualizó el test de persistencia de trazos para validar estilo reconstruido.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/core/model/WhiteboardStroke.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/whiteboard/WhiteboardService.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/state/WorkspaceStateBuilder.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreArchitectureTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- Los 26 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Modelar herramientas de pizarra más complejas si se decide representar trazos como objetos editables completos.
- Validar manualmente edición concurrente con cambios locales recientes para evitar aplicar snapshots core sobre interacciones activas.

### Próximos Pasos

- Avanzar con vista de archivos derivada de metadata core o empezar a reducir el protocolo legacy de pizarra ahora que la reconstrucción core es más completa.

## 2026-05-20 - Metadata Core En Vista De Archivos

### Objetivo De La Sesión

- Mostrar archivos derivados de `WorkspaceState.files()` en la UI para que el listado pueda venir del core, no solo del protocolo legacy de archivos por peer.

### Cambios Realizados

- `Controller.applyCoreStateToVisuals` ahora aplica metadata core de archivos a la vista visual.
- Cada `FileMetadata` se mapea a un `QFile` descargable.
- Los archivos derivados del core se marcan internamente con `QFile.md5 = core:<fileId>` para poder reemplazarlos sin duplicar entradas.
- La vista conserva archivos legacy existentes del usuario/peer y mezcla metadata core encima.
- Los owners se resuelven desde miembros conocidos o se crean como usuarios sintéticos si solo existen por metadata core.
- Al actualizar el estado core se refresca la tabla unificada de archivos.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- Los 26 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Probar manualmente que la vista muestre metadata core recibida por sync aunque no llegue listado legacy.
- Mejorar fecha/owner mostrado para archivos core cuando falte información en metadata.
- Evitar reindexación repetida y eventos `file.shared` duplicados para archivos locales ya conocidos.

### Próximos Pasos

- Agregar deduplicación de indexación local por hash/path o empezar a reducir el flujo legacy de archivos usando metadata core como fuente principal.

## 2026-05-20 - Deduplicación De Indexación Local Core

### Objetivo De La Sesión

- Evitar generar eventos `file.shared` repetidos al refrescar archivos locales sin cambios.

### Cambios Realizados

- `Controller` ahora mantiene `indexedCoreFiles` como cache de archivos locales ya indexados.
- La clave de cache usa path relativo y el fingerprint usa tamaño + fecha de modificación.
- `indexFilesInCore` omite `core.shareFile` si el archivo ya fue indexado con el mismo fingerprint.
- Si cambia tamaño o mtime, el archivo se vuelve a registrar en core.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- Los 26 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Persistir el cache entre arranques si se quiere evitar reindexación tras reiniciar la app.
- Pasar de fingerprint path/tamaño/mtime a hash de contenido si se requiere deduplicación más robusta.

### Próximos Pasos

- Reducir más el flujo legacy de archivos usando metadata core como fuente principal, o preparar una prueba manual multi-instancia para validar todo el flujo core actual.

## 2026-05-20 - Cache Persistente De Indexación Local

### Objetivo De La Sesión

- Persistir la deduplicación de indexación local entre reinicios de la app.

### Cambios Realizados

- `Controller` ahora carga/guarda el cache `indexedCoreFiles` por workspace.
- El cache se almacena en `~/.p2p-collab/index-cache/<workspace>.properties`.
- Cada entrada conserva path relativo y fingerprint `size:mtime`.
- Si el workspace cambia, se limpia el cache en memoria y se carga el archivo correspondiente.
- El cache se guarda cuando un archivo se registra exitosamente en el core.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- Los 26 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Considerar invalidación del cache si se borra el event store local.
- Pasar a hash de contenido si path/tamaño/mtime no alcanza para deduplicación robusta.

### Próximos Pasos

- Preparar una prueba manual multi-instancia para validar el flujo completo o seguir reemplazando más flujo legacy de archivos por core.

## 2026-05-20 - Guía Manual E2E Core

### Objetivo De La Sesión

- Preparar una prueba manual multi-instancia reproducible para validar el flujo core end-to-end.

### Cambios Realizados

- Se agregó `scripts/dev-3-instances.sh` para lanzar tres instancias Swing con túnel mock.
- Cada instancia usa `user.home` aislado bajo `/tmp/qfolder-dev` para separar event store, chunks y cache.
- El script prepara carpetas compartidas iniciales y logs por instancia.
- Se agregó `docs/MANUAL_E2E_CORE.md` con checklist de validación:
  - workspace y sync inicial
  - reconstrucción visual core→UI de chat, notas y pizarra
  - metadata de archivos y descarga distribuida por chunks
  - cache persistente de indexación
- Se actualizó `README.md` para enlazar el launcher y la guía.

### Archivos Modificados

- `AGENTS.md`
- `README.md`
- `scripts/dev-3-instances.sh`
- `docs/MANUAL_E2E_CORE.md`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `chmod +x scripts/dev-3-instances.sh`
- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- Los 26 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Ejecutar la prueba manual en entorno con display gráfico.
- Registrar cualquier ajuste necesario tras validar con tres ventanas reales.

### Próximos Pasos

- Correr `./scripts/dev-3-instances.sh` manualmente y validar el checklist de `docs/MANUAL_E2E_CORE.md`.

## 2026-05-20 - Hash, Pizarra Incremental, CI Y Protección Legacy

### Objetivo De La Sesión

- Ejecutar todas las mejoras pendientes de alto impacto: deduplicación por hash, pizarra incremental, CI, protección merge core/legacy y documentación de serialización.

### Cambios Realizados

- **Deduplicación por hash de contenido**: `indexFilesInCore` ahora usa SHA-256 del archivo como fingerprint principal, con cache de dos niveles (fast path por tamaño/mtime, fallback a hash si cambian). Se agregó `sha256File`.
- **Pizarra incremental**: `CoreSyncBridge` ahora acepta callback `onCoreEvent` para aplicar individualmente eventos core de pizarra (`add`, `update`, `delete`, `clear`) sin reconstruir todo el canvas. `Controller.applyCoreEventIncremental` aplica acciones incrementales en el EDT.
- **CI de GitHub Actions**: workflow `.github/workflows/maven-publish.yml` corregido a JDK 21, módulo `p2p-client/pom.xml`, acciones v4 (`checkout`, `setup-java`), trigger push/PR + release, build+test con `mvn verify`.
- **Protección merge core vs legacy**: `addUserToRemoteList` ahora preserva archivos con metadata core (`core:<fileId>`) cuando un listado legacy sobrescribe el usuario. Así los archivos del core no desaparecen al recibir listados legacy vacíos o parciales.
- **Documentación de serialización**: `AGENTS.md` documenta la limitación de JSON-B con records null y el camino para migrar `ObjectOutputStream` a JSON/CBOR.
- `AGENTS.md` actualizado con CI corregido.

### Archivos Modificados

- `.github/workflows/maven-publish.yml`
- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/CoreSyncBridge.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- Los 26 tests pasan y la app compila con `./build.sh`.

### Pendientes

- Migrar serialización Java a JSON/CBOR cuando Jakarta JSON-B 3.x esté disponible o se configure `withNullValues(true)` en el `JsonbConfig`.
- Validar CI en push real a GitHub.
- Probar pizarra incremental en multi-instancia real.

### Próximos Pasos

- Migrar serialización Java → JSON/CBOR o avanzar con implementación de malla P2P real sin hub.

## 2026-05-20 - Suite De Resiliencia: 52 Tests

### Objetivo De La Sesión

- Agregar 26 nuevos casos de prueba de resiliencia sobre sync, chunks, chat, notas, pizarra, membresía y estrés con 2/4/8/10 miembros.
- Extender `SimulatedNetworkAdapter` con packet loss y latencia simuladas.

### Cambios Realizados

- `SimulatedNetworkAdapter` ahora soporta `runGossipRoundsWithLoss(packetLoss)` y `runGossipRoundsWithLatencyAndLoss(packetLoss, maxDelayRounds)`.
- Se creó `CoreResilienceTest` con 26 tests.
- **Sync y red (5 tests):**
  - packet loss 30% converge con más rondas (N=2,4,8,10)
  - packet loss 50% aún propaga algo en 40 rondas (N=2,4,8)
  - latencia variable no pierde eventos (N=2,4,8,10)
  - network partition converge al sanar (N=4,8,10)
  - churn no pierde eventos (N=4,8,10)
- **Chunks y archivos (4 tests):**
  - peer timeout cae a ruta legacy
  - chunk corrupto se detecta por hash
  - disponibilidad parcial asigna solo a quien tiene cada chunk
  - stress 700KB con 20+ chunks entre 2/4/8 peers
- **Chat (3 tests):**
  - mensajes simultáneos mantienen conteo (N=2,4,8,10)
  - mensajes largos con emojis/unicode sobreviven
  - reconstrucción de 50 mensajes desde cero por sync (N=2,4,8,10)
- **Notas (3 tests):**
  - edición concurrente: último snapshot gana
  - roundtrip QNOTES2 con estilo
  - snapshot vacío no rompe
  - 20 actualizaciones: última persiste
- **Pizarra (4 tests):**
  - movimiento concurrente del mismo objeto: último gana
  - 10/50/100 objetos reconstruyen snapshot
  - clear durante edición borra todo
  - trazos con colores/grosores extremos se normalizan
- **Membresía (3 tests):**
  - join storm de N candidatos aprobados (N=2,4,8,10)
  - revocados no reconectan (N=2,4,8)
  - aprobación dual simultánea no duplica
- **Estrés (3 tests):**
  - 500 eventos en malla de 2/4/8/10: al menos 80% se propaga
  - ráfaga de 50×N eventos: al menos 60% se propaga
  - indexado masivo de 100/500/1000 archivos con deduplicación

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/SimulatedNetworkAdapter.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreResilienceTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- **52 tests** pasan (26 previos + 26 nuevos) y la app compila con `./build.sh`.

### Próximos Pasos

- CRDT para notas o malla P2P real sin hub.

## 2026-05-20 - Malla P2P Real Con P2PNetworkAdapter

### Objetivo De La Sesión

- Implementar un `NetworkAdapter` real sobre conexiones WebSocket directas entre peers, dejando el hub solo para descubrimiento inicial.

### Cambios Realizados

- Se creó `P2PNetworkAdapter` que implementa `NetworkAdapter` usando conexiones WebSocket directas entre peers.
- El adapter gestiona múltiples conexiones salientes (`WsClient`) hacia otros peers y acepta conexiones entrantes por el servidor embebido existente.
- Eventos core (`Core event`) y sync (`Core sync request/response`) viajan por las conexiones directas P2P.
- El `PeerLink` interno maneja las conexiones P2P, recibe eventos core y los aplica vía `SyncEngine`.
- `Controller` inicializa `P2PNetworkAdapter` y conecta automáticamente a cada peer remoto cuando aparece en la lista de usuarios.
- El handler de conexiones directas (`handleDirectPeerEvent`) ahora también procesa eventos core y sync entrantes.
- Al perder el workspace, se desconectan todas las conexiones P2P.
- El hub WebSocket legacy sigue funcionando para descubrimiento y eventos legacy.
- Se agregaron 4 tests de malla P2P: formación, propagación sin hub, sync desde múltiples peers y política de degree máximo.
- `CoreResilienceTest` subió de 26 a 30 tests.
- `WebSocketNetworkAdapter` ahora expone métodos estáticos `encodeCoreEvent` y `encodeSyncPayload`.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PNetworkAdapter.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/WebSocketNetworkAdapter.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreResilienceTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- **56 tests** pasan y la app compila con `./build.sh`.

### Pendientes

- Probar manualmente con 2-3 instancias reales para validar que los eventos core viajan por conexiones directas.
- Mover también eventos legacy (chat, archivos, pizarra, notas) a las conexiones P2P.
- Agregar reintentos y reconexión automática en `PeerLink`.

### Próximos Pasos

- Migrar eventos legacy a las conexiones P2P o implementar CRDT para notas.

## 2026-05-20 - Arquitectura Descentralizada: Eventos Legacy → P2P

### Objetivo De La Sesión

- Eliminar el concepto de hub como transportador de eventos de contenido. Todo chat, pizarra, notas y archivos viaja por conexiones P2P directas.

### Cambios Realizados

- **Chat**: eliminado envío legacy `"Mensaje de chat"` y `"Mensaje de chat fijado"`. Solo se emite evento core.
- **Pizarra**: eliminado envío legacy `"Pizarra actualizada"` y `"Pizarra accion"`. Solo eventos core.
- **Notas**: eliminado envío legacy `"Notas actualizadas"`. Solo evento core `note.updated`.
- **Archivos**: eliminado envío legacy de listados. Los archivos se propagan por eventos core `file.shared`.
- **Estado inicial de workspace**: eliminado envío de complementos, pizarra, notas e historial de chat al nuevo miembro. Se reconstruye desde sync core.
- `publishCoreEvent` ahora envía primero por `P2PNetworkAdapter` (directo), luego por `CoreSyncBridge` (hub, como fallback).
- `P2PNetworkAdapter.onCoreEventStored` ahora llama `applyCoreEventIncremental` + `applyCoreStateToVisuals`.
- `handleDirectPeerEvent` ahora maneja eventos core y sync sobre conexiones directas entrantes.
- El hub queda como bootstrap-only: solo maneja creación de workspace, join, membresía y transferencia de archivos legacy.

### Arquitectura resultante

```
     ┌─────────┐
     │  Hub    │  ← solo bootstrap + membresía
     └────┬────┘
          │ (wsClient para join inicial)
    ┌─────┴─────┐
    ▼           ▼
 ┌────┐ ◄══► ┌────┐     ← P2P para contenido
 │ A  │      │ B  │        (chat, pizarra, notas, archivos)
 └────┘      └────┘
```

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- **56 tests** pasan y la app compila con `./build.sh`.

### Pendientes

- Eliminar el hub también para membresía (join/approve peer-to-peer con gossip y consenso).
- Probar manualmente con 2-3 instancias reales para validar el flujo P2P.
- Agregar reintentos automáticos y manejo de desconexión en `PeerLink`.

### Próximos Pasos

- Membresía P2P descentralizada o CRDT para notas.

## 2026-05-20 - Validación Con 70 Casos De Prueba

### Objetivo De La Sesión

- Implementar y validar los 70 casos de prueba definidos para el sistema P2P descentralizado.

### Cambios Realizados

- Se creó `QfolderLayout` con estructura de carpetas por fecha (`YYYY/MM/DD/HHmm-{id}-{slug}`).
- `QfolderLayout` incluye normalización de slug y metadatos `workspace.json`.
- `WorkspaceService.createWorkspace` ahora valida que el nombre no sea vacío ni null.
- Se creó `CoreQfolderTest` con 70 tests organizados en 18 grupos.
- **Grupo 1 (workspace)**: creación, rechazo sin nombre, duplicados, caracteres especiales.
- **Grupo 2 (Qfolder)**: estructura de carpetas, metadata json, fechas, reutilización, colisiones.
- **Grupo 3 (identidad)**: miembro local, json identity, token único.
- **Grupo 4 (consenso)**: ingreso, 1 no alcanza, 2 aprueban, duplicada, no autorizado, reconexión, token falso.
- **Grupo 5 (revocación)**: revocado no reconecta, necesita votos, con votos suficientes.
- **Grupo 6 (eventos)**: chat, duplicado, no aprobado, workspace incorrecto, concurrentes.
- **Grupo 7 (efímeros)**: typing, cursor, presencia no persisten.
- **Grupo 8 (pizarra)**: mouse no guarda, trazo final, borrar objeto.
- **Grupo 9 (notas)**: crear, actualizar, concurrentes.
- **Grupo 10 (archivos)**: metadata+chunks, reconstruir, incompleto, corrupto.
- **Grupo 11 (malla)**: peer menos cargado, max connections, target, 20 usuarios, partición.
- **Grupo 12 (gossip)**: multi-hop, sin loop, faltantes, post-desconexión.
- **Grupo 13 (snapshots)**: estado completo, crear, snapshot+eventos, corrupto.
- **Grupo 14 (compactación)**: compactar notas, no compactar críticos.
- **Grupo 15 (persistencia)**: guardar, recargar, sin state/, sin snapshot, sin json.
- **Grupo 16 (reglas)**: solo autor borra, votos, cambiar reglas.
- **Grupo 17 (UI)**: servicio de chat, estado reconstruido, sin lógica de consenso en UI.
- **Grupo 18 (simulaciones)**: 10 usuarios, 20 users + eventos, offline masivo, partición.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/filesystem/QfolderLayout.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/workspace/WorkspaceService.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreQfolderTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- **144 tests** pasan (56 previos + 70 nuevos) y la app compila con `./build.sh`.

### Próximos Pasos

- Migrar completamente de NOTE_UPDATED (snapshot) a NOTE_INSERT/NOTE_DELETE_OP (CRDT) para edición concurrente real.

## 2026-05-20 - CRDT, ED25519, Membresía P2P Y Fallos De Red

### Objetivo De La Sesión

- Avanzar con las 4 líneas pendientes: CRDT para notas, PublicKeyAuth con ED25519, membresía P2P por gossip, y tests de fallos reales.

### Cambios Realizados

- **CRDT notas**: Nuevos tipos de eventos `note.insert`, `note.deleteOp`, `note.styleApplied`.
- `NoteService` ahora expone `insertText`, `deleteText`, `applyStyle` con posiciones.
- `WorkspaceStateBuilder` aplica operaciones CRDT secuencialmente para reconstruir texto.
- **PublicKeyAuthProvider**: Implementación real usando `KeyPairGenerator.getInstance("Ed25519")` de Java.
- Genera pares ED25519, firma eventos con SHA-256, valida autores contra `WorkspaceState`.
- `Member` ahora tiene campo `publicKey` (con constructor secundario para compatibilidad).
- **Membresía P2P**: Tests que validan que join requests, approvals y revocaciones se propagan por gossip en malla de 4-8 peers sin hub.
- **Fallos de red**: Tests de half-open connection, reconexión rápida sin duplicados, fuzzing de payloads (null bytes, caracteres especiales, IDs largos), benchmark de velocidad de propagación.
- **CoreQfolderTest**: 70 → 88 tests (+18 nuevos).

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/core/events/EventTypes.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/notes/NoteService.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/state/WorkspaceStateBuilder.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/auth/PublicKeyAuthProvider.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/model/Member.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreQfolderTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- **144 tests** pasan (126 previos + 18 nuevos) y la app compila con `./build.sh`.

### Pendientes

- Integrar `PublicKeyAuthProvider` en lugar de `TokenAuthProvider` en el flujo real.
- Migrar completamente notas a CRDT eliminando `NOTE_UPDATED`.
- Probar ED25519 en CI (requiere Java 15+).

## 2026-05-20 - Fix Bootstrap P2P Y P2PMeshService Testeable

### Objetivo De La Sesión

- Corregir bug que impedía establecer conexiones P2P entre peers.
- Extraer lógica de bootstrap a `P2PMeshService` testeable.
- Agregar tests del flujo completo P2P.

### Cambios Realizados

- **Bug fix**: `refreshLocalFilesAndNotify(Event e)` había sido vaciado, lo que impedía enviar `"Gracias por la bienvenida..."` al hub. Sin ese evento, los peers nunca se agregaban a `remoteUsers` y `connectP2PTo` nunca se ejecutaba. Resultado: cero conexiones P2P.
- Restaurado `sendEvent(e)` — necesario para el bootstrap de membresía.
- `connectP2PTo` ahora usa hilo separado para no bloquear el EDT.
- Se creó `P2PMeshService` que orquesta la malla P2P de forma testeable.
- `Controller` ahora delega en `P2PMeshService` para peerAppeared, publish, disconnectAll.
- `CoreQfolderTest`: 88 → 93 tests (+5 nuevos de integración P2P mesh).
- **Tests de integración P2P mesh**:
  - peerAparece → conexión P2P establecida
  - peerDesaparece → limpieza de malla
  - flujo completo: join → workspace.created → chat por P2P → todos reciben (N=4,8,10)
  - eventos de contenido fluyen por P2P (chat, notas, pizarra en misma malla)
  - reconexión después de desconexión

### Por qué el bug no fue detectado antes

Los 146 tests anteriores cubren el core (WorkspaceService, EventStore, SyncEngine, simulaciones), pero ninguno prueba el flujo real del Controller. `refreshLocalFilesAndNotify` es el punto de conexión entre el protocolo legacy del hub (membership) y el establecimiento de conexiones P2P. Al estar en `Controller.java` y no en un servicio testeable, no había cobertura.

### Archivos Modificados

- `AGENTS.md`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PMeshService.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreQfolderTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- **151 tests** pasan (146 previos + 5 nuevos de integración P2P mesh) y la app compila con `./build.sh`.

### Próximos Pasos

- Probar manualmente con `./scripts/dev-3-instances.sh` para validar el flujo completo.
- Extraer más lógica del Controller a servicios testeables (membership, archivos).

## 2026-05-20 - Correcciones Review P2P Y Chunks

### Objetivo De La Sesión

- Corregir hallazgos de revisión en P2P/sync, reconstrucción de chunks y registro duplicado de adjuntos.

### Cambios Realizados

- `P2PNetworkAdapter` ahora persiste localmente eventos enviados por `send`/`broadcast`, manteniendo verde el contrato de publicación local y evitando que publicar por P2P dependa de otro servicio para guardar el evento.
- Se agregó sync request inicial al establecer una conexión P2P directa y `P2PMeshService.broadcastSyncRequest()` ahora delega realmente al adapter.
- Se agregó codificación reutilizable de IDs conocidos para sync en `WebSocketNetworkAdapter`.
- `CoreChunkTransferCoordinator` reconstruye archivos distribuidos usando el orden canónico de `FileMetadata.chunks()` en vez del orden del store.
- `FileSystemFileChunkStore` guarda un manifiesto `chunks.order` para preservar orden de escritura y evitar reconstrucciones ordenadas por hash.
- `sendChatFile` dejó de registrar el archivo en core antes de refrescar/indexar, evitando un evento local no publicado y una posible duplicación posterior.
- Se agregó test para validar que `FileSystemFileChunkStore` conserva el orden de chunks aunque los hashes ordenen distinto.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PNetworkAdapter.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/WebSocketNetworkAdapter.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PMeshService.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/CoreChunkTransferCoordinator.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/filesystem/FileSystemFileChunkStore.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/BackendExtendedSimulationTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- **187 tests** pasan y la app compila con `./build.sh`.
- Los 3 fallos iniciales de `CoreWebSocketIntegrationTest` quedaron corregidos.

### Pendientes

- Reemplazar `ObjectInputStream/ObjectOutputStream` por JSON/CBOR versionado en payloads core.
- Mover trabajo pesado de callbacks WebSocket fuera del EDT.
- Endurecer validación central de eventos remotos por membresía/firma antes de aceptarlos en el store.

## 2026-05-20 - Refuerzo De Cobertura De Tests

### Objetivo De La Sesión

- Agregar escenarios de prueba para adelantarse a regresiones en P2P real, sync inicial y persistencia de chunks.

### Cambios Realizados

- `CoreWebSocketIntegrationTest` ahora usa un servidor WebSocket de test que decodifica eventos core reales y los aplica en el store remoto.
- Se validó que `P2PNetworkAdapter.broadcast` no solo persiste localmente, sino que el peer remoto recibe y guarda el evento.
- Se agregó escenario de sync inicial: un peer con eventos previos responde al `CORE_SYNC_REQUEST` emitido al conectar y el peer nuevo aplica esos eventos faltantes.
- Se reforzaron escenarios de membresía/chat por P2P para esperar recepción remota real.
- Se agregó escenario de deduplicación de hashes repetidos en `FileSystemFileChunkStore`.

### Archivos Modificados

- `p2p-client/src/test/java/org/q3s/p2p/core/CoreWebSocketIntegrationTest.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/BackendExtendedSimulationTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- **189 tests** pasan y la app compila con `./build.sh`.

### Pendientes

- Agregar pruebas manuales automatizables para Swing completo cuando exista harness UI o modo headless estable.
- Mantener como próximos riesgos técnicos: serialización segura/versionada, callbacks fuera del EDT y validación fuerte de autoría/membresía.

## 2026-05-20 - Directorio De Sesión Como Almacenamiento Local

### Objetivo De La Sesión

- Cambiar el significado de `qfolder.shared.dir`: deja de ser una carpeta arbitraria a compartir y pasa a ser la raíz local donde qfolder ubica sesiones y estado.
- Asegurar que los archivos solo se compartan si el usuario los arrastra a Archivos o los adjunta desde Chat.

### Cambios Realizados

- Default de `qfolder.shared.dir`: `~/qfolder`.
- El core filesystem ahora se guarda bajo `<qfolder.shared.dir>/local-state/p2p-collab-<PUERTO>/`.
- Cada workspace crea una sesión bajo `<qfolder.shared.dir>/sessions/yyyy/MM/dd/HHmm-<workspace>/`.
- Dentro de cada sesión se usan subdirectorios:
  - `files`: archivos compartidos por el usuario.
  - `downloads`: archivos descargados desde otros miembros, sin republicarlos automáticamente.
  - `exports`: exportaciones locales como pizarra PNG.
- El historial de sesión (`chat.txt`, `notas.rtf`, `notas.txt`, `pizarra.png`, `logs.log`, `sesion.txt`) se guarda en el directorio de sesión.
- La solapa Archivos acepta drag & drop de archivos y los copia a `files`, que es lo único que se indexa/publica.
- Adjuntar un archivo desde Chat copia el archivo a `files` y lo publica.
- Descargas legacy y por chunks guardan en `downloads` y ya no llaman a refresh/indexación, evitando republicar archivos recibidos.
- `README.md`, `qfolder.properties.example`, `docs/MANUAL_E2E_CORE.md` y `scripts/dev-3-instances.sh` fueron actualizados al nuevo modelo.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/client/Config.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/main/resources/qfolder.properties.example`
- `README.md`
- `docs/MANUAL_E2E_CORE.md`
- `scripts/dev-3-instances.sh`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- **189 tests** pasan y la app compila con `./build.sh`.

### Pendientes

- Validar manualmente con 3 instancias que drag & drop en Archivos publique metadata y que las descargas queden en `downloads` sin aparecer como archivos propios compartidos.

## 2026-05-20 - Limpieza Legacy De History Dir

### Objetivo De La Sesión

- Eliminar referencias legacy al diseño anterior de carpeta compartida e historial externo.

### Cambios Realizados

- Se eliminó `Config.HISTORY_DIR` y la propiedad `qfolder.history.dir` de la documentación activa.
- Se actualizaron textos de UI de `Directorio a compartir` / `Shared folder` a raíz local de qfolder.
- Se limpiaron recursos i18n obsoletos (`help.sharedFolder`, `help.historyFolder`, `help.tempFolder`) y textos de ayuda antiguos.
- Se removió helper muerto `getApplicationDirectory()`.
- Se renombraron variables locales legacy (`sharedDir`) cuando ahora representan `sessionFilesDir`.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/client/Config.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/View.java`
- `p2p-client/src/main/resources/i18n/messages.properties`
- `p2p-client/src/main/resources/i18n/messages_es.properties`
- `README.md`
- `AGENTS.md`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- **189 tests** pasan y la app compila con `./build.sh`.
- No quedan referencias activas a `qfolder.history.dir`, `HISTORY_DIR`, `~/qfolder/history`, `~/qfolder/temporal`, `Shared folder` o `carpeta compartida`.

## 2026-05-20 - Correcciones Pendientes De Seguridad Y Robustez

### Objetivo De La Sesión

- Resolver en bloque las correcciones pendientes priorizadas: validación de eventos remotos, callbacks WebSocket fuera del EDT para P2P y serialización core/sync sin `ObjectInputStream`.

### Cambios Realizados

- Se agregó `EventValidator` y `EventService` ahora soporta modo estricto para recepción remota.
- `CoreApplicationService.receiveRemoteEvent` usa validación estricta: rechaza contenido de autores no autorizados, miembros revocados y eventos de membresía con autoría inconsistente.
- `P2PNetworkAdapter` permite activar validación estricta; `Controller` la usa en el flujo productivo.
- `WsClient` ahora soporta callbacks fuera del EDT mediante executor dedicado. El comportamiento default sigue siendo EDT para no romper UI legacy, pero P2P y bootstrap directo usan callbacks background.
- Se agregó `CoreEventCodec`, con JSON Base64 versionado:
  - `QCOREJSON1` para eventos core.
  - `QCORESYNCJSON1` para sync request/response.
- `WebSocketNetworkAdapter` dejó de usar serialización Java y usa `CoreEventCodec`.
- `FileSystemEventStore` guarda eventos como JSON en vez de `ObjectOutputStream`.
- `SnapshotService` guarda snapshots como lista JSON versionada de eventos y reconstruye `WorkspaceState` desde esos eventos.
- Se agregaron tests para rechazo de contenido remoto no autorizado y roundtrip JSON de eventos/sync.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/core/events/EventValidator.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/events/EventService.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/events/CoreEventCodec.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/sync/SyncEngine.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/WebSocketNetworkAdapter.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PNetworkAdapter.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/DirectBootstrap.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/filesystem/FileSystemEventStore.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/state/SnapshotService.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/ws/WsClient.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreArchitectureTest.java`
- `AGENTS.md`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- **191 tests** pasan y la app compila con `./build.sh`.
- Ya no quedan usos de `ObjectInputStream`/`ObjectOutputStream` en `src/main/java`.
- Payloads core/sync usan JSON Base64 versionado.

### Pendientes

- Validar manualmente con 3 instancias porque la validación estricta depende de que la UI autorice miembros conocidos antes de aplicar eventos de contenido P2P.
- Implementar firmas/verificación criptográfica real sobre `Event.signature`.

## 2026-05-21 - Layout De Persistencia Por Workspace

### Objetivo De La Sesión

- Ajustar la persistencia local al layout definitivo bajo la raíz `qfolder.shared.dir` o `~/qfolder`.

### Cambios Realizados

- Cada workspace ahora se guarda directamente bajo `YYYY/MM/DD/HHmm-{workspace_id}-{workspace_slug}/` dentro de la raíz qfolder.
- Se crea `workspace.json` y la estructura `identity`, `members`, `events`, `snapshots`, `state`, `chat`, `notes`, `whiteboard`, `files`, `chunks`, `exports`, `logs`; también se mantiene `downloads` para archivos recibidos sin republicar.
- `CoreApplicationService.filesystemWorkspace(...)` permite montar el core directamente sobre el directorio del workspace.
- `FileSystemEventStore` soporta modo workspace-scoped para guardar eventos en `<workspace>/events` en vez de `<root>/<workspaceId>/events`.
- `Controller` re-inicializa servicios core/P2P al conocer el workspace real, para que eventos/chunks queden en el directorio correcto.
- `QfolderLayout.folderName` sanitiza el workspace id para nombres compatibles con Windows/macOS/Linux.
- El script `dev-3-instances.sh` usa `home-a/qfolder`, `home-b/qfolder`, `home-c/qfolder` como raíces locales, preservando `home-*` solo para aislar instancias de desarrollo.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/adapters/filesystem/FileSystemEventStore.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/filesystem/QfolderLayout.java`
- `p2p-client/src/main/java/org/q3s/p2p/core/app/CoreApplicationService.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `scripts/dev-3-instances.sh`
- `docs/MANUAL_E2E_CORE.md`
- `README.md`
- `AGENTS.md`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- **191 tests** pasan y la app compila con `./build.sh`.

### Pendientes

- Probar manualmente que crear workspace genera exactamente `~/qfolder/YYYY/MM/DD/HHmm-.../workspace.json` y subdirectorios esperados.
## 2026-05-21 - Grafo P2P Distribuido

### Objetivo De La Sesión

- Continuar el trabajo pendiente de estabilidad distribuida mostrando y propagando el estado real de conexiones P2P entre peers.

### Cambios Realizados

- `P2PMeshService` ahora publica eventos core `peer.status.updated` cuando cambia la URL local o el set de peers conectados.
- `P2PMeshService` consume `WorkspaceState.peerUrls()` para descubrir endpoints remotos publicados por otros peers y conectarse automáticamente si aún no están en el catálogo local.
- `Controller.applyCoreStateToVisuals` fusiona el grafo P2P distribuido en la UI y actualiza la tabla de `Miembros` desde `WorkspaceState.peerConnections()`.
- La tabla de `Miembros` combina la vista distribuida con las conexiones locales recientes para mostrar `Peers` y `Conectado con` de forma más completa.
- Se agregó un test de integración para validar que `peer.status.updated` reconstruye URLs y conexiones distribuidas tras sync entre dos peers.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PMeshService.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreControllerIntegrationTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- `mvn test`: 192 tests, 0 failures.
- `./build.sh`: exitoso, distribución generada en `dist/`.

### Pendientes

- Validar manualmente con `./scripts/dev-3-instances.sh` que el grafo se ve correctamente en tres ventanas reales.
- Revisar si conviene publicar un evento de estado inicial apenas se crea/entra al workspace, aunque todavía no haya conexiones P2P activas.

## 2026-05-21 - Eliminación De Hub Legacy

### Objetivo De La Sesión

- Eliminar el hub legacy como canal de bootstrap/contenido/fallback y dejar el flujo activo sobre conexiones P2P directas y eventos core.

### Cambios Realizados

- `EmbeddedWebSocketServer` quedó como endpoint WebSocket directo: ya no enruta `__hub`, `__to` ni broadcasts de workspace.
- Se eliminó `WsHubService`.
- Se eliminó `CoreSyncBridge`; la publicación core activa queda en `P2PMeshService`/`P2PNetworkAdapter`.
- Crear workspace ya activa la sesión local directamente, sin conectarse al hub local para recibir una bienvenida.
- Unirse a workspace usa `DirectBootstrap` sobre invitación P2P (`peerUrl?workspace=...`) y solicita sync directo.
- `DirectBootstrap` ya no crea un `workspace.created` falso para candidatos; adjunta sesión pendiente, publica `member.join.requested` solo si no está autorizado y activa la UI cuando el estado core confirma autorización.
- La aprobación P2P envía directamente al candidato los eventos core de aprobación/rol usando la conexión directa pendiente.
- La identidad local (`member.id`) se persiste en `qfolder.shared.dir/identity.properties`, para que un miembro autorizado conserve identidad/token entre reinicios y pueda reconectar sin pedir aprobación otra vez.
- La transferencia de archivos completa legacy quedó desactivada; las descargas fallan explícitamente si no se completan por chunks P2P.
- Los mensajes/protocolos de chunks se envían por conexiones P2P (`P2PNetworkAdapter.sendProtocolEvent`/`broadcastProtocolEvent`) en vez de depender del hub.
- Los eventos legacy de listados/navegación de archivos, archivo de chat y transferencia completa se ignoran si llegan.
- Se agregó test para validar que un miembro autorizado puede reconectar con su token sin generar una nueva solicitud de ingreso.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/client/hub/EmbeddedWebSocketServer.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/hub/WsHubService.java` eliminado
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/CoreSyncBridge.java` eliminado
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/DirectBootstrap.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PNetworkAdapter.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreControllerIntegrationTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client`
- `./build.sh`

### Resultado

- `mvn test`: 193 tests, 0 failures.
- `./build.sh`: exitoso.

### Pendientes

- Validación manual con `./scripts/dev-3-instances.sh`, especialmente primer join directo, aprobación, reconexión sin nueva aprobación y descarga por chunks.
- Limpiar métodos muertos en `Controller` asociados a failover/conexión hub que ya no deberían ejecutarse.
## 2026-05-21 - Fixes UI Verbose Chat/Miembros/Adjuntos

### Objetivo De La Sesión

- Corregir problemas identificados en logs `[UI VERBOSE]` tras prueba manual de tres instancias.

### Cambios Realizados

- Chat: el mensaje local ahora usa el `message_id` del evento core (`core-<message_id>`) antes de pintarse, y se marca como ya aplicado para evitar duplicados cuando vuelve por sync/P2P.
- Miembros: el usuario local ya no depende de `wsClient` legacy para aparecer conectado; se marca conectado desde la sesión local/P2P activa.
- Adjuntos de chat core remotos: se registra un link pendiente, se inicia descarga por chunks P2P y se enlaza el path final al completar la descarga.
- Links de chat: si el path está pendiente/vacío, el click informa que el archivo sigue descargándose en vez de intentar abrir un path inválido.
- Adjuntos de chat: se eliminó una carrera donde la descarga por chunks podía terminar antes de asociar `transferId -> chatLinkId`.
- Descargas core: `CoreChunkTransferCoordinator` ahora prioriza `QFile.md5=core:<fileId>` para elegir metadata, evitando colisiones por nombre/tamaño.
- Tests: agregado `chunkCoordinatorUsesCoreFileIdWhenNameAndSizeCollide`.
- `AGENTS.md` actualizado para reflejar el flujo P2P/core actual, la eliminación de hub/CoreSyncBridge como flujo activo, y el estado de tests.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/CoreChunkTransferCoordinator.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreControllerIntegrationTest.java`
- `AGENTS.md`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `cd p2p-client && mvn test` - 195 tests, 0 failures.
- `./build.sh` - build completo exitoso.

### Errores Encontrados

- No se encontraron errores de compilación ni tests.

### Pendientes

- Revalidar manualmente con `./scripts/dev-3-instances.sh` que `links=` aumente también en receptores y que el link abra después de completarse la descarga por chunks.
- Revisar si reaparece la excepción Swing previa en `JTabbedPane` durante cambios rápidos de solapas.

## 2026-05-21 - Pendientes Priorizados Seguridad/Legacy/Notas/Calidad

### Objetivo De La Sesión

- Ejecutar los pendientes priorizados: firmas Ed25519 reales, limpieza de rutas hub/failover legacy, documentación P2P, notas con operaciones mergeables y análisis estático/build local.

### Cambios Realizados

- `PublicKeyAuthProvider` ahora firma/verifica eventos con `Signature` Ed25519 real sobre representación canónica del evento.
- `CoreApplicationService.filesystem(...)` y `filesystemWorkspace(...)` usan auth Ed25519 para el flujo productivo; `Controller` persiste `member.publicKey` y `member.privateKey` en `identity.properties` local.
- Los eventos de membresía propagan claves públicas (`creator_public_key`, `public_key`) y `EventValidator` verifica firmas cuando hay clave pública disponible.
- Se removieron rutas legacy de failover/hub en `Controller`: marcador `[Hub]`, candidatos de hub, reconexión por hub y métodos `connectWebSocket` legacy.
- `DirectBootstrap` ahora tiene reintentos cortos para tolerar arranque asíncrono del peer directo.
- Notas: edición de texto plano emite `note.insert`/`note.deleteOp`; snapshots `QNOTES2` quedan como fallback para formato rico/imágenes.
- README y manual E2E fueron alineados al modelo P2P directo y al error explícito cuando chunks no completan.
- Se agregó perfil Maven `static-analysis` con SpotBugs y se corrigieron hallazgos altos existentes.
- `build.sh` conserva build rápido por defecto, pero permite `QFOLDER_RUN_TESTS=true ./build.sh` para correr tests antes de empaquetar.

### Comandos Ejecutados

- `mvn test` en `p2p-client`.
- `mvn -Pstatic-analysis verify` en `p2p-client`.
- `./build.sh`.

### Resultado

- `mvn test`: 195 tests, 0 failures.
- `mvn -Pstatic-analysis verify`: exitoso, SpotBugs sin findings.
- `./build.sh`: exitoso, distribución generada en `dist/`.

### Pendientes

- Validar manualmente con `./scripts/dev-3-instances.sh` el flujo completo con identidades Ed25519 nuevas y reconexión de miembros existentes.
- Revisar si conviene migrar `identity.properties` a un almacén más seguro para la clave privada local antes de una distribución pública.

## 2026-05-21 - Fix Join P2P Tras Firmas Ed25519

### Objetivo De La Sesión

- Corregir bloqueo de UI al unir instancias B/C a un workspace creado por A después de activar firmas Ed25519.

### Cambios Realizados

- Al aprobar un miembro, `Controller` ahora envía además un `Core sync response` completo por el socket directo de bootstrap.
- Esto evita la carrera donde B/C podían recibir `member.join.approval` o `member.role.changed` antes de haber aplicado `workspace.created`, rechazando la aprobación por dependencias faltantes.
- La pantalla de join ahora re-habilita controles tras 30 segundos si no llegó autorización, mostrando un mensaje de reintento en vez de quedar deshabilitada indefinidamente.

### Comandos Ejecutados

- `mvn test` en `p2p-client`.
- `mvn -Pstatic-analysis verify` en `p2p-client`.
- `./build.sh`.

### Resultado

- `mvn test`: 195 tests, 0 failures.
- `mvn -Pstatic-analysis verify`: exitoso, SpotBugs sin findings.
- `./build.sh`: exitoso.

### Pendientes

- Repetir prueba manual con A/B/C desde `dist/qfolder.jar` recién generado.

## 2026-05-21 - Fix Canon Firma Ed25519 Con Números JSON

### Objetivo De La Sesión

- Corregir rechazo de `workspace.created` durante join P2P observado en logs verbose de `dev-3-instances.sh`.

### Diagnóstico

- A aceptaba `member.join.requested` de B/C y publicaba aprobación/rol.
- B/C recibían sync pero aceptaban 0 eventos relevantes y quedaban esperando autorización.
- Al verificar un evento real de `/tmp/qfolder-dev`, `required_approvals: 1` se decodificaba desde JSON como `Double 1.0`; la firma original se había calculado sobre `1`.
- Por esa diferencia de canon (`1` vs `1.0`), `workspace.created` no verificaba firma en receptores.

### Cambios Realizados

- `PublicKeyAuthProvider` ahora normaliza números integrales en el canon de firma: `1`, `1L` y `1.0` firman/verifican como `1`.

### Comandos Ejecutados

- `mvn test` en `p2p-client`.
- `./build.sh`.
- Verificación manual con snippet contra evento real viejo de `/tmp/qfolder-dev`: la firma de `workspace.created` ahora valida aunque `required_approvals` llegue como `Double`.

### Resultado

- `mvn test`: 195 tests, 0 failures.
- `./build.sh`: exitoso, `dist/qfolder.jar` actualizado.

### Pendientes

- Repetir `./scripts/dev-3-instances.sh` desde cero con el `dist/qfolder.jar` actualizado.

## 2026-05-21 - Fix Estado Online Miembros P2P

### Objetivo De La Sesión

- Corregir tabla de miembros que seguía mostrando peers como conectados después de cerrar instancias.

### Diagnóstico

- Los logs verbose mostraban descargas por chunks completadas correctamente (`upload-verbose.txt` y `test-upload.bin`).
- La tabla de miembros usaba membresía autorizada/estado distribuido como si fuera online vivo.
- `peer.status.updated` es event-sourced y persistente; al cerrar una instancia no necesariamente llega un evento final, por lo que el último estado quedaba obsoleto.

### Cambios Realizados

- `P2PNetworkAdapter` expone callback `onPeerConnectionsChanged` y lo dispara al conectar, desconectar o fallar un socket P2P.
- `P2PMeshService` publica/refresca estado cuando cambia el set de conexiones directas.
- `Controller.refreshMembersTable` ahora muestra `Conectado` solo para el usuario local o peers con conexión directa viva a esta instancia.
- Para peers offline se limpian los peers distribuidos mostrados, evitando `with=...` obsoleto.

### Comandos Ejecutados

- `mvn test` en `p2p-client`.
- `mvn -Pstatic-analysis verify` en `p2p-client`.
- `./build.sh`.

### Resultado

- `mvn test`: 195 tests, 0 failures.
- `mvn -Pstatic-analysis verify`: exitoso, SpotBugs sin findings.
- `./build.sh`: exitoso, `dist/qfolder.jar` actualizado.

### Pendientes

- Repetir prueba manual cerrando A/B y dejando C abierto para validar que la tabla de C marque solo C como conectado.
- Si se quiere disponibilidad offline real de archivos, hace falta replicación/caching de chunks entre peers; hoy una descarga requiere algún peer online con los chunks o que el receptor ya los tenga localmente.

## 2026-05-22 - Fix Race Condition En JAR Y Fixes De UI/Core

### Objetivo De La Sesión

- Diagnosticar y corregir los crashes reportados en la prueba multi-instancia.
- Mejorar la experiencia de Archivos en la UI.

### Cambios Realizados

1. **NoClassDefFoundError — Causa raíz diagnosticada**:
   - Instancia A crasheó con `NoClassDefFoundError: com.formdev.flatlaf.util.LoggingFacade` durante la renderización de cabecera de tabla.
   - Instancia B crasheó con `NoClassDefFoundError: org.q3s.p2p.client.hub.EmbeddedWebSocketServer` y `FlatLaf$DisabledIconProvider`.
   - Todas las clases que fallaron EXISTEN en el fat JAR.
   - **Causa raíz**: `build.sh` y `dev-3-instances.sh` se ejecutaron superpuestos. Cuando `mvn clean package` termina, `cp` sobrescribe `dist/qfolder.jar` in-situ (truncando el archivo), mientras las instancias ya lo tenían abierto vía mmap. Las clases cargadas temprano (Main, Controller) sobreviven, pero las clases cargadas perezosamente (LoggingFacade, etc.) ven el JAR corrupto y fallan con `ClassNotFoundException`.

2. **Fix race condition — dev-3-instances.sh**:
   - Ahora copia `dist/qfolder.jar` a un snapshot inmutable (`base/qfolder.snapshot.jar`) antes de iniciar instancias.
   - Las instancias usan el snapshot, inmunes a posteriores sobrescrituras del JAR original.

3. **Refrescar archivos locales al seleccionar filtro "Yo"**:
   - Cuando el usuario selecciona "Yo" en el combo de filtro de Archivos, se llama `refreshLocalFilesAndNotify()` para reescanear la carpeta compartida en vez de depender de cache.

### Archivos Modificados

- `scripts/dev-3-instances.sh` — snapshot JAR inmutable
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `mvn test` en `p2p-client` — 195 tests, 0 failures
- `./build.sh`
- Verificación manual de carga de clases desde snapshot JAR

### Pendientes

- Repetir prueba multi-instancia para confirmar que los crashes están resueltos.
- Si se repite el crash, el problema es más profundo que la race condition del JAR.

## 2026-05-22 - Fix Descarga Por Chunks Bajo Cloudflare Tunnel

### Objetivo De La Sesión

- Corregir el fallo 100% reproducible en descarga de archivos por chunks bajo Cloudflare Tunnel, donde la transferencia se congelaba en 88% (últimos 3 chunks nunca se completaban).

### Diagnóstico

- Bajo Cloudflare Tunnel, `AVAILABILITY_REQUEST` enviado por `__to:peerId` vía conexión directa WebSocket se pierde en TCP half-open (zombie connection).
- `sendP2PProtocolEvent` solo intentaba la conexión directa (`directPeerConnections.get(peerId)`). Si esa conexión es zombie, el mensaje NUNCA llega a destino.
- Al recibir `AVAILABILITY_RESPONSE`, se llamaba `plan()` → `scheduleRetry()` también cuando el response llegaba por la vía P2P (falback). Con dual-path broadcast, el response llegaba 2 veces, cada una disparando su propio `scheduleRetry`.
- Cada `scheduleRetry` incrementaba `retryCounts[transferId]` independientemente, así que 2 respuestas = 2 retries consumidos instantáneamente → solo quedaba 0-1 retry para esperar los chunks reales.
- `MAX_RETRIES = 2` (default original) era insuficiente para Cloudflare: los chunks llegan en oleadas y los últimos 1-3 pueden requerir 1-2 reintentos adicionales.

### Cambios Realizados

1. **Dual-path `__to:` routing** (`Controller.sendP2PProtocolEvent`, ~line 1777): cada mensaje `__to:peerId` se envía ahora por AMBAS vías — conexión WebSocket directa Y `p2pNetwork.sendProtocolEvent`. Esto asegura entrega aunque la conexión directa esté zombie.

2. **Deduplicación de `AVAILABILITY_RESPONSE`** (`CoreChunkTransferCoordinator.receiveAvailability`): mantiene un set `seenAvailabilityResponses` con clave `transferId:peerId`. Si el mismo `peerId` ya respondió para el mismo `transferId`, se ignora. Esto evita `plan()` duplicado cuando el response llega primero por vía directa y luego por P2P.

3. **Cancelación de reintentos duplicados** (`CoreChunkTransferCoordinator.scheduleRetry`): antes de programar un nuevo `ScheduledFuture`, se cancela cualquier `ScheduledFuture` existente en `retryFutures[transferId]`. Esto asegura que solo haya UN timer de reintento activo por transferencia, incluso si `plan` se llama múltiples veces.

4. **MAX_RETRIES 2 → 5**: el límite de reintentos aumentó para absorber latencia de Cloudflare (donde los chunks pueden llegar con delays de 1-2 segundos entre oleadas).

5. **Logging `[CHUNK]`** en `CoreChunkTransferCoordinator` para trazabilidad de disponibilidad, planificación, progreso y reintentos. Logs `[P2P SEND]`/`[P2P RECV]` en `Controller` para trazabilidad de eventos core P2P.

6. **Performance**: `publishCoreEvent()` solo llama `applyCoreFilesToVisuals()` para eventos `file.shared`, no para cada tipo de evento, reduciendo refrescos innecesarios de la tabla de archivos.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` — dual-path routing, logging, performance fix
- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/CoreChunkTransferCoordinator.java` — dedup, retry cancel, MAX_RETRIES=5, `[CHUNK]` logging, cleanup de `seenAvailabilityResponses`/`retryFutures`
- `AGENTS.md`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `./build.sh` — build exitoso
- `cd p2p-client && mvn test` — 195 tests, 0 failures

### Resultado

- Tras los 4 fixes, la prueba manual con Cloudflare Tunnel completó descarga exitosamente (sin 88%).

### Próximos Pasos

- Probar nuevamente con Cloudflare (`scripts/dev-2-realinstances.sh`) para confirmar que los fixes resuelven el stall.
- Considerar migrar a CBOR/formal para payloads de chunk en lugar del formato string Base64 actual (`QCHUNK1`).

## 2026-05-23 - Layout Userdata/Systemdata Y Export Manual

### Objetivo De La Sesión

- Separar datos visibles del usuario (`userdata/`) de datos técnicos internos (`systemdata/`).
- Eliminar `downloads/` legacy y mover toda descarga a `files/`.
- Agregar export manual de Pizarra y Notas con timestamp y confirmación.
- Mover identidad e índice a `systemdata/`.

### Cambios Realizados

- `QfolderLayout`: reemplazado `SUBDIRS` legacy con `USERDATA_SUBDIRS` (files, chat, notes, whiteboard, logs, members). Nuevos métodos `userdataRoot()`, `systemdataRoot()`, `systemWorkspaceRoot()`, `identityFile()`. `workspaceFolder` ahora apunta a `userdata/`.
- `Controller`:
  - Eliminado `currentSessionDownloadsDir` y `getSessionDownloadsDir()`.
  - `prepareWorkspaceSessionDirectories` crea `userdata/` y `systemdata/workspaces/<id>/` con subdirectorios técnicos.
  - `initializeCoreServices` recibe `systemWorkspaceRoot`.
  - `loadOrCreateLocalIdentity` usa `systemdata/identity.properties`.
  - `indexedCoreFilesCacheFile` usa `systemdata/workspaces/<id>/index-cache.properties`.
  - Pizarra export: guarda en `whiteboard/pizarra-<fecha>.png` con confirmación popup.
  - Notas: nuevo botón export RTF en toolbar, guarda en `notes/notas-<fecha>.rtf` con confirmación popup.
  - `saveSessionHistory`: guarda en subdirectorios (chat/, notes/, whiteboard/, logs/, members/).
  - Nuevo `saveMembersSnapshot` que escribe `members/members.json`.
  - Chat history restaurado busca primero en `chat/chat.txt`.
- `CoreQfolderTest`: tests actualizados para nuevo layout (userdata, systemdata, no downloads/events bajo userdata).
- Documentación: `AGENTS.md`, `MANUAL_E2E_CORE.md` actualizados.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/adapters/filesystem/QfolderLayout.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreQfolderTest.java`
- `docs/MANUAL_E2E_CORE.md`
- `AGENTS.md`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `cd p2p-client && mvn test` — 195 tests, 0 failures
- `./build.sh` — build exitoso, distribución en `dist/`

### Pendientes

- Validar manualmente con `./scripts/dev-3-instances.sh` que el layout se genera correctamente.
- Verificar que descargas por chunks queden en `userdata/<workspace>/files/`.
- Probar exportación de Pizarra y Notas con popups de confirmación.

### Próximos Pasos

- Probar multi-instancia tras el cambio de layout.

## 2026-05-23 - Fix Members Snapshot JSON Y Cobertura De Tests

### Objetivo De La Sesión

- Corregir persistencia de `members.json`: reemplazar armado manual por JSON-P para evitar JSON inválido.
- Alinear snapshot de miembros con fuente de datos de la tabla Miembros (`knownMembers` en vez de `remoteUsers`).
- Agregar tests de layout para index-cache e identidad en systemdata.

### Cambios Realizados

- `Controller.saveMembersSnapshot` (`Controller.java:4181`) reescrito con `javax.json.Json`, `JsonArrayBuilder` y `JsonObjectBuilder` en lugar de `StringBuilder.append()` plano.
- El snapshot ahora itera `knownMembers.values()` (misma fuente que `refreshMembersTable`) e incluye el estado distribuido de conexiones (`corePeerConnections`, `p2pMesh.connectedPeers()`).
- Se agregaron imports `javax.json.JsonArrayBuilder` y `javax.json.JsonObject`.
- Se agregó test `indexCacheViveEnSystemdataWorkspace` que verifica que index-cache.properties resuelve bajo `systemdata/workspaces/<id>/`.
- Se agregó test `identityViveEnSystemdata` que verifica que identity.properties resuelve bajo `systemdata/`.
- Se extendió `caso5crearEstructuraLocalPorFecha` para verificar que `events/` y `chunks/` NO existen bajo `userdata/`.
- `AGENTS.md` actualizado con rutas de export manual (pizarra/notas) y guardado final de sesión (chat, notes, whiteboard, logs, members).
- `scripts/dev-3-instances.sh` y `scripts/dev-2-realinstances.sh`: agregada limpieza automática al inicio (mata procesos viejos, borra `$BASE_DIR` completo), uso de snapshot JAR en ambos, y eliminado flag SLF4J no-op.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreQfolderTest.java`
- `AGENTS.md`
- `scripts/dev-3-instances.sh`
- `scripts/dev-2-realinstances.sh`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `cd p2p-client && mvn test`
- `./build.sh`

### Resultado

- `cd p2p-client && mvn test`: 197 tests, 0 failures (antes 195).
- `./build.sh`: exitoso, `dist/qfolder.jar` generado.
- `scripts/dev-3-instances.sh` y `scripts/dev-2-realinstances.sh` verificados con `bash -n` (sintaxis correcta).

## 2026-05-24 - Ajuste Documental Y Metadata Ed25519

### Objetivo De La Sesión

- Recuperar contexto desde `AGENTS.md` y contrastarlo con el código actual.
- Actualizar documentación permanente y corregir inconsistencias pequeñas detectadas.

### Cambios Realizados

- `AGENTS.md` actualizado con el estado real: SpotBugs, suite JUnit actual, Ed25519 productivo, `scripts/dev-2-realinstances.sh`, `SnapshotService` no activo como flujo principal, fallback `note.updated`/`QNOTES2`, y pendientes reales (`QCHUNK1`, almacén seguro de clave privada, cache/replicación de chunks offline).
- `QfolderLayout.workspaceJson` ahora escribe `auth_mode=ed25519` en vez de `token`, alineado con el flujo productivo de `PublicKeyAuthProvider`.
- Textos de ayuda i18n actualizados para eliminar referencias a `downloads/` y apuntar a archivos compartidos/descargados en `files/`.
- `README.md` documenta que se prefiere `scripts/dev-3-instances.sh` por el snapshot JAR y agrega el launcher Cloudflare real `scripts/dev-2-realinstances.sh`.
- `RELEASE.md` advierte la discrepancia actual entre release automation (`develop` -> `master`) y GitHub Actions (`main`).

### Archivos Modificados

- `AGENTS.md`
- `README.md`
- `RELEASE.md`
- `p2p-client/src/main/java/org/q3s/p2p/adapters/filesystem/QfolderLayout.java`
- `p2p-client/src/main/resources/i18n/messages.properties`
- `p2p-client/src/main/resources/i18n/messages_es.properties`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreQfolderTest.java`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `cd p2p-client && mvn test` falló inicialmente porque `CoreQfolderTest.caso6workspaceJsonConMetadataCorrecta` esperaba `auth_mode=token`; se actualizó el test a `ed25519`.
- `cd p2p-client && mvn test` — 214 tests, 0 failures.
- `cd p2p-client && mvn -Pstatic-analysis verify` — exitoso, SpotBugs sin findings.
- `./build.sh` — exitoso, `dist/qfolder.jar` generado.

### Pendientes

- Validar manualmente multi-instancia con `./scripts/dev-3-instances.sh` y Cloudflare real con `./scripts/dev-2-realinstances.sh` cuando haya display/entorno disponible.

## 2026-05-24 - Fix Malla P2P Post-Join Bajo Cloudflare

### Objetivo De La Sesión

- Corregir el caso donde B podia unirse por bootstrap directo a A, pero los eventos posteriores de chat/pizarra/notas no se propagaban porque no quedaba una conexion P2P activa.

### Diagnostico

- En logs de `scripts/dev-2-realinstances.sh`, B recibia el snapshot inicial (`Bootstrap sync`) y reconstruia chat/notas/pizarra.
- Luego B marcaba A como desconectado (`peers=0`) y no aparecian logs `P2P mesh: conectando` ni `P2P conectado`.
- `peer.status.updated` publicado antes de autorizacion puede ser rechazado por validacion estricta, y `Controller.applyCoreStateToVisuals` solo fusionaba catalogo (`mergePeerState`) sin rebalancear conexiones.

### Cambios Realizados

- `P2PMeshService`:
  - Nuevo `applyPeerDiscoveryState(WorkspaceState)` para fusionar URLs y rebalancear conexiones sin invocar callback UI, evitando recursividad.
  - Nuevo `forcePublishPeerStatus()` que publica `peer.status.updated` aunque URL/conexiones no hayan cambiado y devuelve el evento para envio directo.
  - Logs de diagnostico para discovery, publicacion forzada y falta de candidatos.
- `Controller`:
  - `applyCoreStateToVisuals` usa `applyPeerDiscoveryState` para disparar conexion P2P cuando llegan URLs por sync/core.
  - Al aprobar un miembro, se fuerza `peer.status.updated` local y se envia directo al candidato antes del snapshot de sync.
  - Al activar workspace, se fuerza `peer.status.updated` para republicar la URL ahora que el miembro ya esta autorizado.
- Tests:
  - `meshServicePeerDiscoveryStateRebalancesWithoutUiCallback` cubre que discovery desde UI conecte sin recursar.
  - `meshServiceForcePublishesStatusEvenIfUnchanged` cubre la republicacion forzada post-aprobacion.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/adapters/network/P2PMeshService.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
- `p2p-client/src/test/java/org/q3s/p2p/core/CoreControllerIntegrationTest.java`
- `AGENTS.md`
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `cd p2p-client && mvn test` — 216 tests, 0 failures.
- `cd p2p-client && mvn -Pstatic-analysis verify` — exitoso, SpotBugs sin findings.
- `./build.sh` — exitoso, `dist/qfolder.jar` generado.

### Pendientes

- Repetir `./scripts/dev-2-realinstances.sh` y verificar logs `P2P mesh: conectando`, `P2P conectado` y propagacion posterior de chat/pizarra/notas.

## 2026-05-25 - Investigación Lentitud Portapapeles En Linux

### Objetivo De La Sesión

- Investigar por qué pegar imágenes del portapapeles (Ctrl+V) en Notas y Pizarra demora 60+ segundos en Linux, mientras que insertar imágenes desde el botón es instantáneo.

### Diagnóstico

- Se creó `ClipboardImagePerformanceTest.java` con tests de rendimiento para aislar el problema.
- Logs de profiling `[PERF][NOTES/WB-CLIPBOARD]` y `[PERF][NOTES/WB-BUTTON]` en `Controller.java`.
- **Hallazgo principal**: `clipboard.getContents(null)` se bloquea 64-67 segundos en Linux con imágenes de 3440x1440px (~5 megapíxeles, ~3.2MB PNG).
- La operación es bloqueante/síncrona; no hay exception ni error, simplemente tarda.
- Una vez que `getContents()` retorna, el procesamiento posterior (PNG encoding ~470ms, Base64 encoding ~16ms) es rápido.
- **No es un problema del código Java**, sino del acceso al portapapeles del sistema Linux con imágenes grandes.

### Cambios Realizados

1. **Serialización de notas en thread separado** (`broadcastNotes` → `serializeNotesStateInBackground`): `serializeNotesState()` ahora corre en un thread `notes-serialize` para no bloquear el EDT durante la serialización de documentos grandes con imágenes.
2. **Advertir al usuario sobre portapapeles lento**: Se agregó retry loop con medición de tiempo en `pasteImageIntoNotes()` y `pasteImageFromClipboard()`. Después de 5 segundos muestra "Portapapeles lento... (esperando)" en la barra de progreso y log info. Timeout a los 30 segundos.
3. **Logs de profiling detallados**: Cada paso del flujo de pegado (getSystemClipboard, getContents, getTransferData, BufferedImage, PNG write, Base64 encode, EDT) se registra con timing en nanosegundos.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java` — serialización async de notas, retry loop con advertencia, logs `[PERF]`
- `p2p-client/src/test/java/org/q3s/p2p/client/view/ClipboardImagePerformanceTest.java` — tests de rendimiento de clipboard/serialización
- `SESSION_NOTES.md`

### Comandos Ejecutados

- `cd p2p-client && mvn test` — 227 tests, 0 failures (incluye 6 nuevos de ClipboardImagePerformanceTest)
- `./build.sh` — exitoso, `dist/qfolder.jar` generado

### Resultado

- Tests pasan. Build exitoso.
- La advertencia de portapapeles lento **no se activó** en pruebas manuales porque `clipboard.getContents(null)` NO lanza exception en Linux — se bloquea silenciosamente.
- El retry loop actual midió tiempo dentro del while, pero no pudo detectar el "slow" porque la llamada nunca lanza exception; solo se bloquea.

### Pendientes

- **Investigar cómo detectar el bloqueo de `getContents()` sin dependender de exceptions**:
  - Opciones: threads separados que midan tiempo y comuniquen via volatile flag, o usar `Clipboard.getContents()` con timeout conceptual.
  - Alternativa: guardar imagen del portapapeles a archivo temporal antes de procesar, para no depender del portapapeles del sistema.
- **Posible fix definitivo**: guardar la imagen copiada a un temp file cuando el usuario hace Ctrl+C, y pegar desde ese archivo temporal en vez del portapapeles del sistema.
- Validar que la advertencia aparezca cuando el portapapeles esté realmente bloqueado.

## 2026-05-25 - Fixes Varios Pre-Build

### Objetivo De La Sesión

- Corregir 4 issues reportados y verificar el build.

### Cambios Realizados

1. **notas.txt eliminado**: `saveSessionHistory()` ahora solo genera `notas.rtf` via RTFEditorKit, no genera `notas.txt`.
2. **"Abrir directorio de trabajo" muestra la raíz correcta**: `jTextField4` ahora muestra `getQfolderRootDir()` (base folder) en lugar de `currentSessionFilesDir` (que incluye `/files`).
3. **Mock tunnel host 0.0.0.0**: `Config.getTunnelMockHost()` ahora defaulta a `"0.0.0.0"` en vez de `"localhost"`.

### Archivos Modificados

- `p2p-client/src/main/java/org/q3s/p2p/client/Config.java`
- `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`

### Comandos Ejecutados

- `cd p2p-client && mvn test` — 227 tests, 0 failures
- `./build.sh` — exitoso

### Pendientes

- Ninguno de estos fixes requiere validación manual inmediata.
