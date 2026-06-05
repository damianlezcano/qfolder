# Plan de Trabajo — Rewrite Completo de Controller.java

Plan creado el 5 de junio de 2026. **362 tests (default), 407 total, 0 failures, BUILD SUCCESS.**

> **Nota para el LLM ejecutor:** Este plan reescribe `Controller.java` (6225 líneas, ~100 campos, ~260 métodos) en 9 clases enfocadas. Antes de cada cambio, leer el archivo completo. Después de cada clase nueva, ejecutar `cd p2p-client && mvn test`. Al final ejecutar `./build.sh`.
>
> **IMPORTANTE:** La capa core (`CoreApplicationService`, `org.q3s.p2p.core.*`, `org.q3s.p2p.ports.*`, `org.q3s.p2p.adapters.*`) NO se toca. Tampoco `View.java`. Solo se reescribe `Controller.java` y se crean clases nuevas en `org.q3s.p2p.client.view` y `org.q3s.p2p.client.net`.
>
> **Build/test:** `cd p2p-client && mvn test` después de cada paso. `./build.sh` al final.

---

## Arquitectura actual vs nueva

### Actual: God Object

```
Controller.java (6225 líneas)
├── Workspace lifecycle (create, join, activate, disconnect)
├── P2P networking (mesh, bootstrap, direct events, dual-path)
├── Chat (send, receive, display, reply, pin, attachments)
├── Files (indexing, sharing, download, chunks, navigation)
├── Whiteboard (canvas 913 líneas, herramientas, broadcast)
├── Notes (CRDT, QNOTES2, RTF, imágenes, export)
├── Members (tabla, aprobación, snapshot)
├── Configuration (idioma, LAF, complementos)
├── Transfer progress (barras, retry, cancel)
├── Session persistence (save history, restore)
├── UI/Tab management (insert, find, mark, refresh)
├── Icon factory (~200 líneas de iconos programáticos)
└── Legacy notify() dispatcher (~180 líneas)
```

### Nueva: Componentes enfocados

```
AppController.java (~1000 líneas) — orquestador principal
├── P2PSessionManager.java (~500 líneas) — red P2P
├── ChatPanel.java (~600 líneas) — chat completo
├── FilePanel.java (~550 líneas) — archivos + indexación
├── WhiteboardPanel.java (~1000 líneas) — canvas + herramientas
├── NotesPanel.java (~500 líneas) — notas CRDT + RTF
├── MembersPanel.java (~350 líneas) — miembros + aprobación
├── TransferProgressBar.java (~200 líneas) — progreso transferencias
└── IconFactory.java (~220 líneas) — iconos programáticos
```

---

## Orden de ejecución

### Paso 1: Crear IconFactory (sin dependencias)
### Paso 2: Crear TransferProgressBar (sin dependencias de Controller)
### Paso 3: Crear P2PSessionManager (depende de core + adapters)
### Paso 4: Crear MembersPanel (depende de core, P2PSessionManager)
### Paso 5: Crear ChatPanel (depende de IconFactory, TransferProgressBar)
### Paso 6: Crear FilePanel (depende de core, TransferProgressBar, IconFactory)
### Paso 7: Crear WhiteboardPanel (depende de core, TransferProgressBar, IconFactory)
### Paso 8: Crear NotesPanel (depende de core, IconFactory)
### Paso 9: Reescribir Controller → AppController
### Paso 10: Verificar y limpiar

---

## Paso 1: Crear `IconFactory.java`

**Paquete:** `org.q3s.p2p.client.view.components`
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/components/IconFactory.java`

Mover estos métodos estáticos desde Controller.java:

| Método original | Líneas Controller | Nuevo nombre |
|---|---|---|
| `chatIcon()` | 3409–3420 | `IconFactory.chat()` |
| `noteIcon()` | 3422–3432 | `IconFactory.note()` |
| `groupIcon()` | 3434–3447 | `IconFactory.group()` |
| `notesImageIcon()` | 3449–3462 | `IconFactory.notesImage()` |
| `notesImageSizeIcon(plus)` | 3464–3482 | `IconFactory.notesImageSize(boolean plus)` |
| `boardIcon()` | 3484–3495 | `IconFactory.board()` |
| `toolIcon(tool)` | 3497–3534 | `IconFactory.tool(String tool)` |
| `strokeIcon(plus)` | 3536–3555 | `IconFactory.stroke(boolean plus)` |
| `colorIcon(color)` | 3557–3566 | `IconFactory.color(Color color)` |
| `clearIcon()` | 3568–3582 | `IconFactory.clear()` |
| `exportIcon()` | 3584–3598 | `IconFactory.export()` |
| `helpIcon()` | 3600–3610 | `IconFactory.help()` |
| `attachmentIcon()` | 2707–2718 | `IconFactory.attachment()` |

**Estructura:**
```java
package org.q3s.p2p.client.view.components;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class IconFactory {
    private IconFactory() {}

    public static ImageIcon chat() { /* copiar body de chatIcon() */ }
    public static ImageIcon note() { /* copiar body de noteIcon() */ }
    public static ImageIcon group() { /* copiar body de groupIcon() */ }
    // ... todos los métodos como static, copiar implementación exacta
}
```

**Test:** Ejecutar `cd p2p-client && mvn test` — no debe romper nada porque es código nuevo sin uso todavía.

---

## Paso 2: Crear `TransferProgressBar.java`

**Paquete:** `org.q3s.p2p.client.view.components`
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/components/TransferProgressBar.java`

Mover estos campos y métodos desde Controller:

**Campos (de Controller):**
```java
private JPanel transferPanel;                           // línea 157
private final Map<String, JProgressBar> transferBars = new LinkedHashMap<>();  // 166
private final Map<String, JPanel> transferRows = new LinkedHashMap<>();       // 167
private final Map<String, String> transferTargets = new LinkedHashMap<>();    // 168
private final Map<String, QFile> activeTransferRequests = new LinkedHashMap<>(); // 169
private final Map<String, String> transferPendingOpenLinks = new LinkedHashMap<>(); // 170
```

**Métodos (de Controller):**
| Método | Líneas Controller |
|---|---|
| `installTransferStatusBar()` | 427–446 |
| `updateTransferProgress(transferId, label, current, total)` | 4779–4811 |
| `transferRow(transferId)` | (inline en updateTransferProgress) |
| `removeTransferProgress(transferId, row)` | 4813–4840 |
| `finishTransferWithError(transferId, label, errorText)` | 4842–4850 |
| `registerActiveTransfer(transferId, qfile)` | 4852–4856 |
| `copyTransferRequest(original)` | 4858–4869 |
| `retryTransfer(transferId)` | 4871–4887 |
| `cancelTransfer(transferId)` | 4889–4893 |
| `uniqueFilePath(preferredPath)` | 4895–4913 |

**Interfaz de callbacks:**
```java
public class TransferProgressBar {
    public interface TransferActions {
        void onRetry(String transferId, QFile qfile);
        void onCancel(String transferId);
    }

    private final TransferActions actions;
    private JPanel transferPanel;
    // ... campos

    public TransferProgressBar(TransferActions actions) { ... }
    public JPanel getPanel() { return transferPanel; }
    public void install(Container contentPane) { /* body de installTransferStatusBar */ }
    public void update(String transferId, String label, int current, int total) { /* body de updateTransferProgress */ }
    public void error(String transferId, String label, String errorText) { /* body de finishTransferWithError */ }
    public void registerTransfer(String transferId, QFile qfile) { ... }
    public QFile getActiveTransfer(String transferId) { ... }
    public void removeActiveTransfer(String transferId) { ... }
    // ...
}
```

**Test:** `mvn test` después de crear.

---

## Paso 3: Crear `P2PSessionManager.java`

**Paquete:** `org.q3s.p2p.client.net`
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/net/P2PSessionManager.java`

Esta clase concentra TODA la lógica de red P2P. Absorbe los 3 CRITICAL bugs y las mejoras SEC-1, SEC-2, SEC-4.

**Campos (de Controller):**
```java
private WsClient wsClient;                    // 147
private EmbeddedWebSocketServer wsServer;     // 148
private CloudflareTunnel cloudflareTunnel;    // 149
private String peerTunnelUrl;                 // 151
private final Map<String, WebSocket> directPeerConnections = new ConcurrentHashMap<>(); // 179
private final ExecutorService outboundEventQueue = Executors.newSingleThreadExecutor(...); // 218
private P2PNetworkAdapter p2pNetwork;         // 230
private P2PMeshService p2pMesh;               // 231
private DirectBootstrap directBootstrap;      // 232
private CoreChunkTransferCoordinator coreChunkTransfer; // 229
private javax.swing.Timer joinTimeoutTimer;   // 200
```

**Interfaz de eventos hacia UI:**
```java
public class P2PSessionManager {

    public interface Callbacks {
        void onCoreStateChanged(WorkspaceState state);
        void onJoinApprovalNeeded(User candidate);
        void onPeerDisconnected(String peerId);
        void onPeerConnected(String peerId);
        void onWorkspaceActivated();
        void onJoinTimeout();
        void onLoginMessage(String message);
        void onChunkDownloadComplete(String transferId, FileMetadata metadata, QFile original, byte[] bytes);
        void onChunkDownloadFailed(QFile original, String reason);
        void onTransferProgress(String transferId, String label, int current, int total);
    }
```

**Constructor:**
```java
    public P2PSessionManager(
        CoreApplicationService core,
        User localUser,
        Supplier<String> localPublicKey,
        Supplier<String> localPrivateKey,
        Logger log,
        Callbacks callbacks
    )
```

**Métodos públicos (de Controller):**

| Método nuevo | Método(s) original(es) | Líneas Controller |
|---|---|---|
| `initializeCoreServices(Path root)` | `initializeCoreServices` | 1832–1883 |
| `ensurePeerEndpoint(Runnable onReady, Consumer<String> onError)` | `ensurePeerEndpoint` | 968–1010 |
| `startPeerEndpointServer(Runnable onReady, Consumer<String> onError)` | `startPeerEndpointServer` | 1012–1031 |
| `joinWorkspace(String invite, String userId, String displayName, String pubKey, String privKey)` | body de `jButton2ActionPerformed` | 1219–1250 |
| `publishCoreEvent(Event event)` | `publishCoreEvent` | 1911–1926 |
| `sendDirectCoreEvent(String peerId, Event event)` | `sendDirectCoreEvent` | 1329–1339 |
| `sendDirectCoreSyncSnapshot(String peerId)` | `sendDirectCoreSyncSnapshot` | 1341–1351 |
| `connectToPeer(User peer)` | `connectP2PTo` | 2259–2269 |
| `disconnectedPeers()` | expose directPeerConnections state | — |
| `connectedPeerCount()` | `directPeerConnections.size()` | — |
| `isDirectPeerConnected(String peerId)` | `isDirectPeerConnected` | 2425–2428 |
| `connectedPeers()` | `p2pMesh.connectedPeers()` | — |
| `localInviteCode(String workspaceId)` | `localInviteCode` | 1579–1583 |
| `forcePublishPeerStatus()` | delega a `p2pMesh` | — |
| `shutdown()` | parte de `shutdown` | 6206–6224 |

**Métodos privados (de Controller):**

| Método | Líneas Controller |
|---|---|
| `handleDirectPeerEvent(WebSocket conn, CoreEnvelope envelope)` | 1033–1055 |
| `acceptCoreEventOnDirect(CoreEnvelope envelope)` | 1057–1078 |
| `respondCoreSyncOnDirect(CoreEnvelope envelope, WebSocket conn)` | 1080–1095 |
| `acceptCoreSyncOnDirect(CoreEnvelope envelope)` | 1097–1105 |
| `handlePeerDisconnected(String peerId)` | 1107–1118 |
| `sendP2PProtocolEvent(CoreEnvelope envelope)` | 1928–1968 |
| `sendEvent(CoreEnvelope e)` | 1826–1830 |

**CRITICAL-1 fix (bootstrap WebSocket abierto):** En `joinWorkspace()`, NO cerrar el bootstrap socket en 500ms. En su lugar, dejar la conexión abierta y checkear `isAuthorized` cuando llegan sync responses. Timeout de 60s. Detalle:

```java
public boolean joinWorkspace(String invite, ...) {
    // directBootstrap.join() necesita ser modificado para NO cerrar el socket
    // Alternativa: el bootstrap queda abierto, y cuando el creador aprueba,
    // el approval llega por el mismo socket.
    // El directBootstrap.join() retorna true si conectó; la autorización
    // llega async via handleDirectPeerEvent → acceptCoreEventOnDirect
}
```

**CRITICAL-2 fix (wk null):** En TODA esta clase, usar `core.currentWorkspaceId().orElse(null)` en vez de depender de un campo `wk`. El workspace ID supplier para P2PNetworkAdapter y P2PMeshService es:
```java
() -> core.currentWorkspaceId().orElse(null)
```

**SEC-1 fix (membership check en sync/chunks):** En `respondCoreSyncOnDirect()`:
```java
WorkspaceState state = core.currentState();
if (!state.isAuthorized(envelope.userId())) {
    log.debug("Sync rechazado: peer no autorizado " + envelope.userId());
    return;
}
```
Lo mismo en el handler de chunks (inyectar `Predicate<String> isAuthorized` en CoreChunkTransferCoordinator).

**SEC-2 fix (validar efímeros):** Ya se maneja en CoreApplicationService.receiveRemoteEvent(), agregar:
```java
if (event.isEphemeral()) {
    WorkspaceState state = currentState();
    if (!state.isAuthorized(event.authorMemberId())) return false;
    // ...
}
```

**SEC-4 fix (límite tamaño WS):** En `EmbeddedWebSocketServer.onMessage()`:
```java
if (message.length() > 10_000_000) {
    conn.close(1009, "Message too large");
    return;
}
```

**Test:** `mvn test` después de crear.

---

## Paso 4: Crear `MembersPanel.java`

**Paquete:** `org.q3s.p2p.client.view.components`
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/components/MembersPanel.java`

**Campos (de Controller):**
```java
private JPanel membersContainerPanel;
private JTable membersTable;
private final Map<String, User> knownMembers = new LinkedHashMap<>();
private final Map<String, Long> memberConnectedAt = new LinkedHashMap<>();
private final Map<String, String> corePeerUrls = new LinkedHashMap<>();
private final Map<String, Set<String>> corePeerConnections = new LinkedHashMap<>();
private final Map<String, JDialog> approvalDialogs = new LinkedHashMap<>();
private final Map<String, String> pendingMemberPublicKeys = new LinkedHashMap<>();
```

**Constructor:**
```java
public MembersPanel(
    CoreApplicationService core,
    P2PSessionManager session,
    User localUser,
    JFrame parentFrame,
    Logger log
)
```

**Métodos públicos:**

| Método nuevo | Método original | Líneas |
|---|---|---|
| `JPanel createTab()` | `loadMembersTab()` | 2297–2311 |
| `void applyState(WorkspaceState state)` | `applyCoreMembersToVisuals` + `applyCorePeerStateToMembers` | 2036–2084 |
| `void refresh()` | `refreshMembersTable()` | 2313–2356 |
| `void showApprovalDialog(User candidate)` | `showApprovalDialog` | 1273–1319 |
| `void closeApprovalDialog(String userId)` | `closeApprovalDialog` | 1321–1327 |
| `void trackMember(User member, boolean online)` | `trackMember` | 2271–2288 |
| `String displayName(String memberId)` | `displayNameForCoreMember` | 2212–2216 |
| `User getKnownMember(String id)` | `knownMembers.get(id)` | — |
| `Map<String, User> knownMembers()` | field access | — |
| `void saveMembersSnapshot(File dir, String wsName)` | `saveMembersSnapshot` | 4963–5022 |
| `void clear()` | parte de `removeAllTab` | 4577–4598 |

**Callback de aprobación:** Cuando el usuario aprueba, `MembersPanel` llama:
```java
Event approval = core.approveJoin(candidate.getId());
session.publishCoreEvent(approval);
session.sendDirectCoreEvent(candidate.getId(), approval);
session.forcePublishPeerStatus();
session.sendDirectCoreSyncSnapshot(candidate.getId());
```

**Test:** `mvn test`.

---

## Paso 5: Crear `ChatPanel.java`

**Paquete:** `org.q3s.p2p.client.view.components`
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/components/ChatPanel.java`

**Campos (de Controller):**
```java
private JTextPane chatArea;
private JTextField chatInput;
private JLabel chatReplyLabel;
private JLabel pinnedChatLabel;
private String pinnedChatMessageId;
private JPanel chatContainerPanel;
private final Map<String, ChatMessage> chatMessages = new LinkedHashMap<>();
private final Map<String, int[]> chatMessageRanges = new LinkedHashMap<>();
private final Map<String, String> chatFileLinks = new LinkedHashMap<>();
private final Map<String, String> chatTransferLinks = new LinkedHashMap<>();
private final Map<String, String> pendingChatDownloads = new LinkedHashMap<>();
private final Set<String> chatActiveUserIds = new HashSet<>();
private final Set<String> appliedCoreChatIds = new HashSet<>();
private final Set<String> appliedCoreChatFileIds = new HashSet<>();
private ChatMessage replyingToChatMessage;
```

**Inner class:** Mover `ChatMessage` (4431–4496 de Controller) como inner static class.

**Constructor:**
```java
public ChatPanel(
    CoreApplicationService core,
    P2PSessionManager session,
    MembersPanel members,
    User localUser,
    Executor fileOpener,
    Logger log,
    Runnable onChatChanged  // para markTabIfInactive
)
```

**Métodos públicos:**

| Método nuevo | Método original | Líneas |
|---|---|---|
| `JPanel createTab()` | `loadChatTab` | 2578–2697 |
| `void applyState(WorkspaceState state)` | chat branch de `applyCoreStateToVisuals` | 1994–2010 |
| `void applyFileAttachments(WorkspaceState state)` | chat attachment branch de `applyCoreFilesToVisuals` | 2099–2121 |
| `void sendMessage()` | `sendChatMessage` | 2854–2891 |
| `void sendFile(File file)` | `sendChatFile` | 2720–2749 |
| `void sendImageFromClipboard()` | `sendChatImageFromClipboard` | 2775–2797 |
| `void appendSystemMessage(String msg)` | `appendChatSystemMessage` | 2924–2933 |
| `void appendSystemMessage(String msg, Color c)` | overload | 2928–2933 |
| `String getText()` | `chatArea.getText()` | — |
| `void updateFileLink(String transferId, String localPath)` | parte de `completeCoreChunkDownload` | — |
| `void clear()` | parte de `removeAllTab` | — |

**Métodos privados:** `appendChatMessage`, `appendChatFileMessage`, `registerChatFileLink`, `appendChatText`, `mentionsCurrentUser`, `chatMessageAt`, `chatLinkAt`, `openChatLinkAt`, `showChatMessageMenu`, `applyPinnedChatMessage`, `jumpToPinnedChatMessage`, `installChatInputPasteImageBinding`, `toBufferedImage`, `copyImageBytesToSessionFiles`, `copyFileToSessionFiles`, `importFilesToChat`.

**Test:** `mvn test`.

---

## Paso 6: Crear `FilePanel.java`

**Paquete:** `org.q3s.p2p.client.view.components`
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/components/FilePanel.java`

**Campos (de Controller):**
```java
private final Map<String, String> indexedCoreFiles = new LinkedHashMap<>();
private String indexedCoreFilesWorkspaceId;
private final Map<String, String> navigationPaths = new LinkedHashMap<>();
private TabListFile archivosTab;
private JPanel archivosContainerPanel;
private final Map<String, FileRegistryEntry> fileRegistry = new LinkedHashMap<>();
private final Map<String, Set<String>> filePeers = new LinkedHashMap<>();
private final List<FileTabInfo> fileTabs = new ArrayList<>();
```

**Constructor:**
```java
public FilePanel(
    CoreApplicationService core,
    P2PSessionManager session,
    TransferProgressBar transferBar,
    User localUser,
    Supplier<File> sessionFilesDir,
    Executor fileOpener,
    Logger log,
    Runnable onFilesChanged
)
```

**Métodos públicos principales:**

| Método nuevo | Método original | Líneas |
|---|---|---|
| `JPanel createTab(Workspace wk)` | `ensureArchivosTab` | 2385–2416 |
| `void applyState(WorkspaceState state)` | `applyCoreFilesToVisuals` | 2086–2130 |
| `void refreshLocalFiles(String msg)` | `refreshLocalFilesAndNotify` | 1729–1738 |
| `void indexFilesInCoreAsync(List<QFile> files, String baseDir)` | `indexFilesInCore` async wrapper | **PERF-2 fix** |
| `void downloadFile(User user, QFile qfile)` | `downloadFile` | 5985–5999 |
| `void openFile(User user, QFile qfile)` | `openFile` | 6001–6024 |
| `void removeFile(User user, QFile qfile)` | `removeFile` | 6056–6082 |
| `void navigateTo(String userId, String path)` | `navigateTo` | 6120–6127 |
| `void navigateBack(String userId)` | `navigateBack` | 6129–6139 |
| `QFile qFileForCoreFileId(String fileId)` | `qFileForCoreFileId` | 2142–2162 |
| `void completeCoreChunkDownload(...)` | `completeCoreChunkDownload` | 4745–4770 |
| `void clear()` | parte de `removeAllTab` | — |

**PERF-2 fix:** `indexFilesInCoreAsync` corre en thread `"file-indexer"`, NO en el EDT:
```java
public void indexFilesInCoreAsync(List<QFile> files, String baseDir) {
    new Thread(() -> {
        indexFilesInCore(files, baseDir);
        SwingUtilities.invokeLater(() -> { refresh(); });
    }, "file-indexer").start();
}
```

**SEC-3 fix (path traversal):** En `completeCoreChunkDownload`:
```java
String safeName = sanitizeFileName(metadata.name());
Path target = Path.of(localPath).normalize();
Path base = sessionFilesDir.get().toPath().normalize();
if (!target.startsWith(base)) {
    log.err("Path traversal detectado: " + metadata.name());
    return;
}
```

**Test:** `mvn test`.

---

## Paso 7: Crear `WhiteboardPanel.java`

**Paquete:** `org.q3s.p2p.client.view.components`
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/components/WhiteboardPanel.java`

Mover la inner class `WhiteboardCanvas` (5070–5983) aquí como inner class o clase separada. También mover:

| Método original | Líneas |
|---|---|
| `loadWhiteboardTab()` | 3947–4013 |
| `whiteboardToolRow()` | 4015–4021 |
| `whiteboardToolButton(...)` | 4023–4033 |
| `updateColorButton(color)` | 4035–4040 |
| `exportWhiteboardImage()` | 4042–4064 |
| `broadcastWhiteboard()` | 3612–3616 |
| `broadcastWhiteboardAction(...)` | 3618–3629 |
| `recordWhiteboardStrokeInCore(stroke)` | 3640–3661 |
| `recordWhiteboardActionInCore(...)` | 3631–3638 |
| `coreWhiteboardState(state)` | 2190–2210 |
| `applyCoreEventIncremental(event)` | 2164–2188 |
| `WhiteboardCanvas` inner class | 5070–5983 |

**Constructor:**
```java
public WhiteboardPanel(
    CoreApplicationService core,
    P2PSessionManager session,
    TransferProgressBar transferBar,
    Supplier<File> sessionDir,
    JFrame parentFrame,
    Logger log,
    Runnable onWhiteboardChanged
)
```

**Métodos públicos:**
- `JPanel createTab()` — construye canvas + toolbar
- `void applyState(WorkspaceState state)` — serializa estado core → `canvas.applyState()`
- `void applyEventIncremental(Event event)` — aplica evento individual
- `void exportImage()` — guarda PNG
- `BufferedImage toImage()` — para session save
- `String serialize()` — para session save
- `void clear()` — limpia canvas

**WhiteboardCanvas:** Pasa a acceder a `WhiteboardPanel.this` en vez de `Controller.this`. Los 3 outer fields accedidos (`log`, `view`, `transferRows`) se reemplazan por parámetros del constructor o callbacks.

**Test:** `mvn test`.

---

## Paso 8: Crear `NotesPanel.java`

**Paquete:** `org.q3s.p2p.client.view.components`
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/components/NotesPanel.java`

Reemplaza `NotesEditor.java` existente (que tiene 193 líneas pero no está wireado). NotesPanel es la versión completa.

**Campos (de Controller):**
```java
private JTextPane notesPane;
private Timer notesSyncTimer;
private int notesFontSize = 14;
private volatile boolean applyingRemoteNotes;
private String lastSentNotesState = "";
private String lastAppliedNotesState = "";
private DocumentListener notesDocumentListener;
private long suppressNotesBroadcastUntil;
```

**Métodos (de Controller):**

| Método | Líneas |
|---|---|
| `loadNotesTab()` | 4066–4139 |
| `attachNotesDocumentListener()` | 3669–3679 |
| `recordNotesInsert(e)` | 3681–3697 |
| `recordNotesDelete(e)` | 3699–3713 |
| `charOffsetToLineIndex(doc, offset)` | 3715–3722 |
| `isPlainTextNotesDocument()` | 3724–3737 |
| `scheduleNotesBroadcast()` | 3663–3667 |
| `broadcastNotes()` | 3739–3748 |
| `recordNotesUpdateInCore(state)` | 3750–3759 |
| `serializeNotesState()` | 3772–3806 |
| `applyRemoteNotes(text)` | 3865–3890 |
| `parseNotesState(state)` | 3909–3945 |
| `pasteImageIntoNotes()` | 4208–4331 |
| `insertImageIntoNotes(image)` | 4333–4357 |
| `exportNotesRtf()` | 5028–5052 |
| `changeNotesFontSize(delta)` | 4157–4167 |
| `chooseNotesFontColor()` | 4169–4184 |
| `chooseNotesImage()` | 4186–4206 |
| helpers: `appendNotesTextRun`, `iconInfo`, `sameNoteStyle`, `colorToHex`, `noteForeground`, `findNotesIconNear`, `setNotesOverlayMode`, `resizeSelectedNotesImage`, `toolbarButton` | 3808–4403 |

**Inner static class:** `IconInfo` (4405–4415 de Controller).

**Constructor:**
```java
public NotesPanel(
    CoreApplicationService core,
    P2PSessionManager session,
    Supplier<File> sessionDir,
    JFrame parentFrame,
    Logger log,
    Runnable onNotesChanged
)
```

**Métodos públicos:**
- `JPanel createTab()` — editor + toolbar
- `void applyState(WorkspaceState state)` — note "shared-notes" → `applyRemoteNotes`
- `String serialize()` — `serializeNotesState()` para session save
- `StyledDocument getDocument()` — para RTF export
- `void exportRtf()` — manual export
- `void stopTimer()` — para shutdown
- `void clear()` — limpia editor

**Test:** `mvn test`.

---

## Paso 9: Reescribir `Controller.java` → `AppController`

**Archivo:** MISMO archivo `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`
**Mantener el nombre de clase `Controller`** para no romper `Main.java` y tests existentes.

El Controller reescrito mantiene SOLO:

### Campos que quedan en Controller (~25 campos vs 100 actuales)

```java
// Componentes
private final View view = new View();
private P2PSessionManager session;
private ChatPanel chatPanel;
private FilePanel filePanel;
private WhiteboardPanel whiteboardPanel;
private NotesPanel notesPanel;
private MembersPanel membersPanel;
private TransferProgressBar transferBar;

// Workspace / session
private Workspace wk;
private final User user = User.build(UUIDUtils.generate());
private String localPublicKey = "";
private String localPrivateKey = "";
private CoreApplicationService core;
private long sessionCreatedAt;
private boolean historySaved;
private File qfolderRootDir;
private File currentSessionDir;
private File currentSessionFilesDir;

// UI state
private final Set<String> enabledComplementos = new HashSet<>();
private final Map<String, JCheckBox> complementoChecks = new LinkedHashMap<>();
private final Set<String> markedTabs = new HashSet<>();
private Logger log;
private Executor exec;
private boolean configChange;
private List<User> remoteUsers = new ArrayList<>();
```

### Métodos que quedan en Controller

**Lifecycle:**
- `start()` — simplificado: init core, crea componentes, wires UI, show window
- `shutdown()` — delega a cada componente
- `windowClosing()` — shutdown + save + exit

**Workspace lifecycle:**
- `jButton4ActionPerformed` (create) — simplificado
- `jButton2ActionPerformed` (join) — delega a `session.joinWorkspace()`
- `activateWorkspaceFromCore()` — **CRITICAL-3 fix**: `showWorkspaceMainUI()` en vez de `notify("Bienvenido")`
- `showWorkspaceMainUI()` — **NUEVO**: muestra tabs, oculta login
- `showJoinAfterWorkspaceLost()` — simplificado

**State dispatch (simplificado):**
```java
private void applyCoreStateToVisuals(WorkspaceState state) {
    if (state == null) return;
    // PERF-3 fix: debounce
    visualRefreshDebounce.restart();
}

private void applyCoreStateToVisualsNow(WorkspaceState state) {
    SwingUtilities.invokeLater(() -> {
        membersPanel.applyState(state);
        chatPanel.applyState(state);
        chatPanel.applyFileAttachments(state);
        notesPanel.applyState(state);
        whiteboardPanel.applyState(state);
        filePanel.applyState(state);
    });
}
```

**PERF-3 fix (debounce):**
```java
private final javax.swing.Timer visualRefreshDebounce = new javax.swing.Timer(100, e -> {
    applyCoreStateToVisualsNow(core.currentState());
});
{ visualRefreshDebounce.setRepeats(false); }
```

**Configuration:**
- `installConfigEnhancements()` — queda pero usa `IconFactory.xxx()`
- `toggleComplemento()`, `applyComplementoVisibility()`
- `refreshLanguageTexts()`, `refreshTabTitles()`, `refreshAllTooltips()`

**Tab management:**
- `insertSystemTab()`, `findTabByTitle()`, `getTabAlternatives()`
- `markTabIfInactive()`, `clearSelectedTabMark()`

**Session persistence:**
- `saveSessionHistory()` — delega a componentes:
  ```java
  chatPanel.getText() → chat.txt
  notesPanel.getDocument() → notas.rtf
  whiteboardPanel.toImage() → pizarra.png
  membersPanel.saveMembersSnapshot() → members.json
  ```

**Login UI:**
- `mostrarErrorEnPantallaLogin()`
- `setJoinControlsEnabled()`, `setCreateWorkspaceControlsEnabled()`

**Session directories:**
- `prepareWorkspaceSessionDirectories()`, `writeWorkspaceJson()`
- `getSessionFilesDir()`, `buildSessionDir()`

**Identity:**
- `loadOrCreateLocalIdentity()`

**Legacy `notify()`:** ELIMINAR completamente. Todo el switch statement de 180 líneas. Los handlers legacy ya no son el flujo activo (confirmado en AGENTS.md). Si queda algún código que llama `notify()`, reemplazar por el handler directo correspondiente.

### CRITICAL-3 fix detallado

En `activateWorkspaceFromCore()`, reemplazar:
```java
// ANTES (línea ~1898):
notify("Bienvenido usuario al grupo!");
```
por:
```java
// DESPUÉS:
showWorkspaceMainUI();
```

Nuevo método:
```java
private void showWorkspaceMainUI() {
    SwingUtilities.invokeLater(() -> {
        view.getjPanelJoin().setVisible(false);
        // Mostrar tabbed pane (buscar el componente correcto en View)
        view.getjTabbedPane().setVisible(true);

        // Asignar wk si es null (flujo join)
        if (wk == null) {
            core.currentWorkspaceId().ifPresent(wsId -> {
                WorkspaceState state = core.currentState();
                String name = state.workspace() != null ? state.workspace().name() : wsId;
                wk = new Workspace(wsId, name);
            });
        }

        // Habilitar complementos por defecto
        for (String comp : List.of("Archivos", "Chat", "Pizarra", "Notas", "Miembros")) {
            enabledComplementos.add(comp);
            applyComplementoVisibility(comp, true);
        }

        // Actualizar título
        String wsName = wk != null ? wk.getName() : "workspace";
        view.setTitle("'" + user.getName() + "' conectado al grupo '" + wsName + "'");

        // Aplicar estado visual
        applyCoreStateToVisualsNow(core.currentState());
    });
}
```

### P2PSessionManager.Callbacks wiring

```java
session = new P2PSessionManager(core, user, () -> localPublicKey, () -> localPrivateKey, log,
    new P2PSessionManager.Callbacks() {
        public void onCoreStateChanged(WorkspaceState state) { applyCoreStateToVisuals(state); }
        public void onJoinApprovalNeeded(User candidate) { membersPanel.showApprovalDialog(candidate); }
        public void onPeerDisconnected(String peerId) {
            membersPanel.trackMember(membersPanel.getKnownMember(peerId), false);
            membersPanel.refresh();
            filePanel.refresh();
            chatPanel.appendSystemMessage(I18n.get("chat.userDisconnected", membersPanel.displayName(peerId)));
        }
        public void onPeerConnected(String peerId) {
            chatPanel.appendSystemMessage(I18n.get("chat.userReconnected", membersPanel.displayName(peerId)));
        }
        public void onWorkspaceActivated() { activateWorkspaceFromCore(); }
        public void onJoinTimeout() {
            setJoinControlsEnabled(true);
            mostrarErrorEnPantallaLogin(I18n.get("join.timeout"));
        }
        public void onLoginMessage(String msg) { mostrarErrorEnPantallaLogin(msg); }
        public void onChunkDownloadComplete(...) { filePanel.completeCoreChunkDownload(...); }
        public void onChunkDownloadFailed(...) { transferBar.error(...); }
        public void onTransferProgress(...) { transferBar.update(...); }
    });
```

**Test:** `mvn test`. Esto es el paso más crítico — muchos tests pueden romperse. Corregir uno por uno.

---

## Paso 10: Verificar y limpiar

1. Ejecutar `cd p2p-client && mvn test` — todos los 362+ tests deben pasar.
2. Ejecutar `cd p2p-client && mvn -Pperformance-tests test` — 407+ tests.
3. Ejecutar `./build.sh`.
4. Ejecutar `./scripts/dev-3-instances.sh` y validar:
   - Crear workspace en instancia 1
   - Copiar invite
   - Unirse desde instancia 2
   - Aprobar desde instancia 1
   - Enviar mensajes de chat
   - Compartir archivos
   - Dibujar en pizarra
   - Escribir notas
5. Eliminar `NotesEditor.java` y `NotesEditorTest.java` originales (reemplazados por NotesPanel).
6. Eliminar código muerto: `FileNavigationPayload`, `webSocketUriForEndpoint`, `currentCoreEventsOrEmpty`, `Notification.java`, `LogUtils.java` del model package.
7. Actualizar `AGENTS.md`:
   - Documentar nueva estructura de componentes
   - Actualizar lista de archivos a revisar
   - Actualizar conteo de tests

---

## Mejoras incorporadas en el rewrite

| ID | Dónde se implementa | Cómo |
|---|---|---|
| **CRITICAL-1** | `P2PSessionManager.joinWorkspace()` + `DirectBootstrap` | Bootstrap socket permanece abierto hasta autorización |
| **CRITICAL-2** | `P2PSessionManager` | `core.currentWorkspaceId()` en todas partes |
| **CRITICAL-3** | `Controller.showWorkspaceMainUI()` | Reemplaza handler "Bienvenido" |
| **SEC-1** | `P2PSessionManager.respondCoreSyncOnDirect()` | `isAuthorized` check |
| **SEC-2** | `CoreApplicationService.receiveRemoteEvent()` | `isAuthorized` check en efímeros |
| **SEC-3** | `FilePanel.completeCoreChunkDownload()` | `sanitizeFileName` + path normalization |
| **SEC-4** | `EmbeddedWebSocketServer.onMessage()` | 10MB limit |
| **PERF-1** | `CoreApplicationService.currentState()` | Cache `cachedState` + `invalidateStateCache()` |
| **PERF-2** | `FilePanel.indexFilesInCoreAsync()` | Thread `"file-indexer"` fuera del EDT |
| **PERF-3** | `Controller.visualRefreshDebounce` | Timer 100ms, `setRepeats(false)` |
| **PERF-4** | `FileSystemEventStore.listEventsAfter()` | Override con filtro por fecha de archivo |
| **STAB-2** | `Controller.shutdown()` | Delega a cada componente en orden |
| **UX-1** | `P2PSessionManager.Callbacks.onPeerDisconnected/Connected` | Chat system messages |

**Nota:** STAB-1 (paginación sync), STAB-3 (preservar catálogo), UX-2 (typing), UX-3 (sync progress), UX-4 (i18n hardcoded) quedan como mejoras futuras post-rewrite. El rewrite establece la arquitectura limpia donde implementarlas es trivial.

---

## Estimaciones

| Paso | Tiempo estimado |
|---|---|
| 1. IconFactory | 20 min |
| 2. TransferProgressBar | 30 min |
| 3. P2PSessionManager | 90 min |
| 4. MembersPanel | 40 min |
| 5. ChatPanel | 60 min |
| 6. FilePanel | 60 min |
| 7. WhiteboardPanel | 60 min |
| 8. NotesPanel | 50 min |
| 9. Reescribir Controller | 120 min |
| 10. Verificar y limpiar | 60 min |
| **Total** | **~10 horas** |
