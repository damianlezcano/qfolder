# Plan de Trabajo — qfolder

Plan actualizado el 3 de junio de 2026. **Fase 11 completada. Nuevos pendientes identificados para calidad y seguridad.**

## Resumen

| ID | Tarea | Severidad | Estado |
|---|---|---|---|
| PENDIENTE-1 | DirectBootstrap.join() WsClient leak en success | Alta | ✅ Completado |
| PENDIENTE-2 | UpdateChecker regex → JsonReader | Media | ✅ Completado |
| PENDIENTE-3 | Eliminar 35 System.out.println de Controller | Baja | ✅ Completado |
| PENDIENTE-4 | Locale deprecated en I18n.setLocale | Baja | ✅ Completado |
| PENDIENTE-5 | Dead code serializeNotesStateInBackground | Baja | ✅ Completado |
| PENDIENTE-6 | Duplicados en messages_en.properties | Baja | ✅ Completado |
| PENDIENTE-7 | Renombrar test caso25 | Muy baja | ✅ Completado |
| PENDIENTE-8 | Crear AppConfigTest | Media | ✅ Completado |
| PENDIENTE-9 | SpotBugs en CI | Media | ✅ Completado |
| PENDIENTE-10 | Actualizar maven-compiler-plugin | Baja | ✅ Completado |
| PENDIENTE-11 | Refactorización de Controller.java (God Object) | Alta | ⏳ Pendiente |
| PENDIENTE-12 | Almacenamiento seguro de privateKey Ed25519 | Alta | ⏳ Pendiente |
| PENDIENTE-13 | Tests para adapters/network | Media | ⏳ Pendiente |
| PENDIENTE-14 | Tests para client/hub y client/ws | Media | ⏳ Pendiente |
| PENDIENTE-15 | Tests para adapters/filesystem | Media | ⏳ Pendiente |
| PENDIENTE-16 | Unificación de ramas CI/release | Baja | ⏳ Pendiente |
| PENDIENTE-17 | Métricas de performance y profiling | Baja | ⏳ Pendiente |
| PENDIENTE-18 | Documentación de API pública | Muy baja | ⏳ Pendiente |
| PENDIENTE-19 | Separar tests de performance/ruidosos del suite normal | Baja | ⏳ Pendiente |
| PENDIENTE-20 | Limpiar warnings Maven Shade/SLF4J | Baja | ⏳ Pendiente |
| PENDIENTE-21 | Automatizar smoke E2E multi-instancia | Media | ⏳ Pendiente |
| Fase 11.1 | EventPipeline | — | ✅ Completado |
| Fase 11.2 | Proyecciones separadas | — | ✅ Completado |
| Fase 11.3 | PEER_STATUS efímero | — | ✅ Completado |
| Fase 11.4 | SnapshotService startup | — | ✅ Completado |
| Fase 11.5 | CRDT line-based para notas | — | ✅ Completado |
| Fase 11.6 | EventValidator cache | — | ✅ Completado |
| Fase 11.7 | Auto-reconnect mesh | — | ✅ Completado |
| Fase 11.8 | Eliminar `org.q3s.p2p.model.Event` legacy | — | ✅ Completado |
| Controller snapshot | Migrar `currentState()` a snapshot configurado | — | ✅ Completado |
| Snapshot persistence | Escritura periódica/manual de snapshots | — | ✅ Completado |
| Snapshot retention | Retención de snapshots antiguos | — | ✅ Completado |
| Chunk replicator | Replicación de chunks offline | — | ✅ Completado |

## Detalle de Cambios Realizados

### PENDIENTE-1: DirectBootstrap.join() — WsClient leak
- El `finally` ahora cierra el `bootstrap` siempre (con delay de 500ms en success para no interrumpir callbacks asincrónicos).
- **Archivos:** `DirectBootstrap.java`

### PENDIENTE-2: UpdateChecker — Migrar de regex a JsonReader
- Reemplazadas las constantes `TAG_PATTERN`/`URL_PATTERN` con `javax.json.JsonReader`.
- Eliminados imports de `java.util.regex.Matcher` y `java.util.regex.Pattern`.
- Eliminado método `collectAssetUrls`.
- **Archivos:** `UpdateChecker.java`

### PENDIENTE-3: Eliminar 35 System.out.println de debug
- Eliminadas todas las líneas `System.out.println("[Controller.*"` en los métodos `refreshLanguageTexts`, `refreshAllFileTableColumns`, `searchAndRefreshFileTables`, `refreshAllTooltips` y `updateTooltipsInContainer`.
- **Archivos:** `Controller.java`

### PENDIENTE-4: Deprecated `new Locale(String)` en I18n
- Reemplazado `new Locale(locale.getLanguage())` por `Locale.forLanguageTag(locale.getLanguage())`.
- **Archivos:** `I18n.java`

### PENDIENTE-5: Dead code serializeNotesStateInBackground
- Eliminado el método completo (34 líneas) que ya no era llamado.
- Verificado que no hay referencias al método en todo el proyecto.
- **Archivos:** `Controller.java`

### PENDIENTE-6: Duplicados en messages_en.properties
- Eliminadas las líneas duplicadas de `tab.files`, `transfer.requesting` y `transfer.retrying`.
- **Archivos:** `messages_en.properties`

### PENDIENTE-7: Renombrar test caso25
- Renombrado de `caso25eventoDeNoAprobadoNoModificaEstado` a `caso25eventoDeNoAprobadoEsRechazadoPorValidacion`.
- **Archivos:** `CoreQfolderTest.java`

### PENDIENTE-8: AppConfigTest creado
- Nuevo test con 6 casos: default values, int parsing, boolean parsing, system property precedence.
- **Archivos:** `AppConfigTest.java` (nuevo)

### PENDIENTE-9: SpotBugs listo para CI
- Corregidos los 6 hallazgos `DE_MIGHT_IGNORE` (catch blocks vacíos) en P2PNetworkAdapter, Controller, UpdateChecker, EventPipeline y ChunkReplicator usando logging donde disponible y método no-vacío donde no.
- SpotBugs pasa con 0 bugs, 0 errores.
- El perfil `-Pstatic-analysis` ya puede agregarse al CI sin romper el build.
- **Nota:** AGENTS.md prohíbe modificar `.github/workflows/maven-publish.yml` sin aviso. El cambio está listo para aplicarse cuando se decida: agregar `-Pstatic-analysis` en la línea `mvn -B verify`.

### PENDIENTE-10: Actualizar maven-compiler-plugin
- Versión actualizada de `3.8.1` a `3.13.0`.
- Reemplazados `maven.compiler.source`/`maven.compiler.target` por `maven.compiler.release` (más estricto, compatible con Java 21).
- **Archivos:** `pom.xml`

## Fase 11 completada

| Ítem | Estado |
|---|---|
| 11.1 Pipeline unificado de eventos (`EventPipeline`) | ✅ Completado en sesión previa |
| 11.2 Separar proyecciones de estado | ✅ Completado en sesión previa |
| 11.3 PEER_STATUS_UPDATED efímero | ✅ Completado en sesión previa |
| 11.4 SnapshotService startup | ✅ Completado en sesión previa |
| 11.5 CRDT para notas colaborativas | ✅ Completado |
| 11.6 EventValidator cache | ✅ Completado en sesión previa |
| 11.7 Auto-reconnect mesh | ✅ Completado en sesión previa |
| 11.8 Consolidar modelo dual de Event | ✅ Completado (`org.q3s.p2p.model.Event` eliminado; `CoreEnvelope` reemplaza wire envelope) |
| Controller snapshot integration | ✅ Completado (`CoreApplicationService.configureSnapshotPath`) |

## Pendientes a futuro (no implementados)

### Prioridad Alta (Pre-Release)

#### PENDIENTE-11: Refactorización de Controller.java (God Object)
- **Problema:** `Controller.java` tiene 6190 líneas y 373 métodos. Mezcla UI, lógica de negocio, red, archivos, chat, pizarra, notas, membresía, etc.
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

#### PENDIENTE-13: Tests para adapters/network
- **Problema:** `P2PNetworkAdapter`, `P2PMeshService`, `DirectBootstrap`, `CoreChunkTransferCoordinator` no tienen tests dedicados.
- **Impacto:** Regresiones en lógica de red P2P no se detectan automáticamente.
- **Propuesta:**
  - `P2PNetworkAdapterTest` — envío/recepción de envelopes, routing, timeouts
  - `P2PMeshServiceTest` — formación de mesh, reconexión, failover
  - `DirectBootstrapTest` — join exitoso/fallido, retry logic, cleanup de recursos
  - `CoreChunkTransferCoordinatorTest` — descarga distribuida, reintentos, cancelación
- **Archivos:** 4 nuevos archivos de test
- **Cobertura objetivo:** 80%+ para cada adapter
- **Riesgo:** Bajo (solo agrega tests)

#### PENDIENTE-14: Tests para client/hub y client/ws
- **Problema:** `EmbeddedWebSocketServer`, `CloudflareTunnel`, `WsClient` no tienen tests.
- **Impacto:** Cambios en WebSocket/Cloudflare pueden romper funcionalidad sin detección.
- **Propuesta:**
  - `EmbeddedWebSocketServerTest` — inicio/detención, manejo de conexiones, mensajes directos
  - `CloudflareTunnelTest` — inicio con cloudflared real vs mock, cleanup de procesos
  - `WsClientTest` — conexión/desconexión, reintentos, manejo de envelopes
- **Archivos:** 3 nuevos archivos de test
- **Nota:** Requiere mocking de procesos externos (cloudflared) y puertos WebSocket
- **Riesgo:** Bajo (solo agrega tests)

#### PENDIENTE-15: Tests para adapters/filesystem
- **Problema:** `FileSystemEventStore`, `FileSystemFileChunkStore`, `QfolderLayout` no tienen tests.
- **Impacto:** Cambios en persistencia de eventos/chunks pueden corromper datos.
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

- `cd p2p-client && mvn test` — **275 tests, 0 failures, 0 errors**
- `cd p2p-client && mvn -Pstatic-analysis verify` — **0 bugs, 0 errores**
- `./build.sh` — exitoso, genera `dist/qfolder.jar` (2.1M)

## Recomendación de siguiente paso

**Si el objetivo es release inminente:**
1. PENDIENTE-12 (privateKey seguro) — bloqueante de seguridad
2. PENDIENTE-13 (tests adapters/network) — robustez de red P2P

**Si el objetivo es calidad de código a mediano plazo:**
1. PENDIENTE-11 (refactor Controller) — mejora mantenibilidad
2. PENDIENTE-13/14/15 (tests) — cobertura de código crítico

**Si el objetivo es mejoras incrementales:**
1. PENDIENTE-19 (tests ruidosos) — CI más legible
2. PENDIENTE-20 (warnings Maven/SLF4J) — build más limpio
3. PENDIENTE-17 (métricas) — visibilidad de performance
4. PENDIENTE-18 (documentación) — onboarding de contribuidores

**Nota:** PENDIENTE-16 (unificación CI/release) requiere decisión explícita del usuario sobre estrategia de ramas.
