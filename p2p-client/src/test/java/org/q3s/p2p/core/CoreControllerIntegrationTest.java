package org.q3s.p2p.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.q3s.p2p.adapters.memory.InMemoryEventStore;
import org.q3s.p2p.adapters.memory.InMemoryFileChunkStore;
import org.q3s.p2p.adapters.memory.UuidIdGenerator;
import org.q3s.p2p.adapters.network.CoreChunkTransferCoordinator;
import org.q3s.p2p.adapters.network.CoreChunkTransferProtocol;
import org.q3s.p2p.adapters.network.P2PMeshService;
import org.q3s.p2p.adapters.network.P2PNetworkAdapter;
import org.q3s.p2p.adapters.network.SimulatedNetworkAdapter;
import org.q3s.p2p.adapters.network.SimulatedNetworkAdapter.SimulatedNode;
import org.q3s.p2p.core.app.CoreApplicationService;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.FileMetadata;
import org.q3s.p2p.core.mesh.MeshPolicy;
import org.q3s.p2p.core.state.WorkspaceState;
import org.q3s.p2p.model.QFile;
import org.q3s.p2p.model.User;

class CoreControllerIntegrationTest {

	@Test void fullWorkflowThroughCoreApplicationServiceTwoPeers() {
		var storeA = new InMemoryEventStore(); var chunksA = new InMemoryFileChunkStore();
		var storeB = new InMemoryEventStore(); var chunksB = new InMemoryFileChunkStore();

		var appA = new CoreApplicationService(storeA, chunksA, new UuidIdGenerator());
		var appB = new CoreApplicationService(storeB, chunksB, new UuidIdGenerator());

		var created = appA.createWorkspace("Proyecto P2P", "Damian", 1);
		var wsId = created.workspaceId();
		var token = created.creator().membershipToken();
		appA.attachExistingSession(wsId, created.creator().memberId(), "Damian", "device", token);

		appB.ensureWorkspaceSession(wsId, "Proyecto P2P", "userB", "B", "device", "tokenB", 1);
		appB.recordJoinRequest("userB", "B", "device", "tokenB");
		crossSync(storeA, storeB, wsId);
		appA.approveJoin("userB");
		appA.authorizeKnownMember("userB", "B", "device", "tokenB");
		crossSync(storeA, storeB, wsId);

		appA.sendChatMessage("Hola desde A");
		appB.sendChatMessage("Hola desde B");
		appA.shareFile("docA.pdf", "contenido A".getBytes(StandardCharsets.UTF_8));
		appB.shareFile("docB.pdf", "contenido B".getBytes(StandardCharsets.UTF_8));
		appA.updateNote("shared-notes", "Notas de A");
		appB.updateNote("shared-notes", "Notas de B");
		appA.recordWhiteboardObjectAction("add", "rect1", "S|Rectangulo|1,2,3,4|#000|1");
		appB.recordWhiteboardObjectAction("add", "circle1", "S|Circulo|5,6,7,8|#f00|2");

		crossSync(storeA, storeB, wsId);

		WorkspaceState stateA = appA.currentState();
		WorkspaceState stateB = appB.currentState();

		assertEquals(2, stateA.chatMessages().size(), "A debe tener 2 mensajes");
		assertEquals(2, stateB.chatMessages().size(), "B debe tener 2 mensajes");
		assertEquals(2, stateA.files().size(), "A debe tener 2 archivos");
		assertEquals(2, stateB.files().size(), "B debe tener 2 archivos");
		assertEquals(2, stateA.whiteboardObjects().size(), "A debe tener 2 objetos pizarra");
		assertEquals(2, stateB.whiteboardObjects().size(), "B debe tener 2 objetos pizarra");
		assertNotNull(stateA.notes().get("shared-notes"));
		assertNotNull(stateB.notes().get("shared-notes"));
	}

	@Test void controllerLikeFlowWithMeshAndGossip() {
		for (int n : new int[]{2, 4, 6}) {
			var net = new SimulatedNetworkAdapter();
			var policy = new MeshPolicy(2, 4);
			List<SimulatedNode> nodes = new ArrayList<>();
			List<CoreApplicationService> apps = new ArrayList<>();
			List<InMemoryEventStore> stores = new ArrayList<>();

			for (int i = 1; i <= n; i++) {
				var store = new InMemoryEventStore();
				stores.add(store);
				apps.add(new CoreApplicationService(store, new InMemoryFileChunkStore(), new UuidIdGenerator()));
				var node = net.createNode("P" + i);
				nodes.add(node);
				if (i == 2) net.connect(nodes.get(0), node);
				if (i > 2) net.connectUsingPolicy(node, policy);
			}
			for (var node : nodes) net.connectUsingPolicy(node, policy);

			var created = apps.get(0).createWorkspace("Mesh" + n, "Creator", 1);
			var wsId = created.workspaceId();
			apps.get(0).attachExistingSession(wsId, created.creator().memberId(), "Creator", "dev0", created.creator().membershipToken());

			for (int i = 1; i < n; i++) {
				apps.get(i).ensureWorkspaceSession(wsId, "Mesh" + n, "U" + i, "User" + i, "dev" + i, "tok" + i, 1);
				apps.get(i).recordJoinRequest("U" + i, "User" + i, "dev" + i, "tok" + i);
			}
			for (int i = 0; i < n; i++) crossSync(stores.get(0), stores.get(i), wsId);
			for (int i = 1; i < n; i++) {
				apps.get(0).approveJoin("U" + i);
				apps.get(0).authorizeKnownMember("U" + i, "User" + i, "dev" + i, "tok" + i);
			}
			for (int i = 0; i < n; i++) crossSync(stores.get(0), stores.get(i), wsId);

			for (int i = 0; i < 8; i++)
				apps.get(i % n).sendChatMessage("chat-" + i);

			for (int i = 0; i < n; i++)
				for (int j = i + 1; j < n; j++)
					crossSync(stores.get(i), stores.get(j), wsId);

			int minChat = apps.stream().mapToInt(a -> a.currentState().chatMessages().size()).min().orElse(0);
			assertTrue(minChat >= 4, "N=" + n + " cada peer debe tener al menos 4 mensajes, min=" + minChat);
		}
	}

	@Test void coreAppTwoPeersSyncViaEvents() {
		var storeA = new InMemoryEventStore(); var chunksA = new InMemoryFileChunkStore();
		var storeB = new InMemoryEventStore(); var chunksB = new InMemoryFileChunkStore();

		var appA = new CoreApplicationService(storeA, chunksA, new UuidIdGenerator());
		var appB = new CoreApplicationService(storeB, chunksB, new UuidIdGenerator());

		var created = appA.createWorkspace("SyncTest", "A", 1);
		var wsId = created.workspaceId();
		appA.attachExistingSession(wsId, created.creator().memberId(), "A", "devA", created.creator().membershipToken());

		appB.ensureWorkspaceSession(wsId, "SyncTest", "B", "B", "devB", "tokB", 1);
		appB.recordJoinRequest("B", "B", "devB", "tokB");
		appA.approveJoin("B");
		appA.authorizeKnownMember("B", "B", "devB", "tokB");

		crossSync(storeA, storeB, wsId);

		appA.sendChatMessage("A: hola");
		appB.sendChatMessage("B: hola");
		appA.updateNote("shared-notes", "nota A");
		appA.recordWhiteboardObjectAction("add", "obj", "S|R|0,0,10,10|#000|1");
		appB.shareFile("fileB.txt", "hello".getBytes(StandardCharsets.UTF_8));

		crossSync(storeA, storeB, wsId);

		WorkspaceState sA = appA.currentState(); WorkspaceState sB = appB.currentState();
		assertEquals(2, sA.chatMessages().size()); assertEquals(2, sB.chatMessages().size());
		assertEquals("nota A", sA.notes().get("shared-notes").text());
		assertEquals(1 + 1, sA.whiteboardObjects().size() + sA.files().size());
		assertEquals(2, sA.authorizedMembers().size()); assertEquals(2, sB.authorizedMembers().size());
	}

	@Test void threePeersFullSessionAllTabsViaControllerFacade() {
		List<CoreApplicationService> apps = new ArrayList<>();
		List<InMemoryEventStore> stores = new ArrayList<>();
		var net = new SimulatedNetworkAdapter(); var policy = new MeshPolicy(2, 4);
		List<SimulatedNode> nodes = new ArrayList<>();

		for (int i = 1; i <= 3; i++) {
			var s = new InMemoryEventStore();
			stores.add(s);
			apps.add(new CoreApplicationService(s, new InMemoryFileChunkStore(), new UuidIdGenerator()));
			var n = net.createNode("U" + i); nodes.add(n);
			if (i == 2) net.connect(nodes.get(0), n);
			if (i > 2) net.connectUsingPolicy(n, policy);
		}
		for (var n : nodes) net.connectUsingPolicy(n, policy);

		var created = apps.get(0).createWorkspace("FullSession", "A", 1);
		var wsId = created.workspaceId();
		apps.get(0).attachExistingSession(wsId, created.creator().memberId(), "A", "d0", created.creator().membershipToken());

		for (int i = 1; i < 3; i++) {
			apps.get(i).ensureWorkspaceSession(wsId, "FullSession", "U" + i, "User" + i, "d" + i, "t" + i, 1);
			apps.get(i).recordJoinRequest("U" + i, "User" + i, "d" + i, "t" + i);
		}
		for (int i = 0; i < 3; i++) crossSync(stores.get(0), stores.get(i), wsId);
		for (int i = 1; i < 3; i++) {
			apps.get(0).approveJoin("U" + i);
			apps.get(0).authorizeKnownMember("U" + i, "User" + i, "d" + i, "t" + i);
		}
		for (int i = 0; i < 3; i++) crossSync(stores.get(0), stores.get(i), wsId);

		apps.get(0).sendChatMessage("Bienvenidos");
		apps.get(1).sendChatMessage("Hola!");
		apps.get(2).sendChatMessage("Que tal?");
		apps.get(0).updateNote("shared-notes", "Notas compartidas desde A");
		apps.get(1).updateNote("shared-notes", "Notas actualizadas por B");
		apps.get(0).recordWhiteboardObjectAction("add", "shape1", "S|R|10,10,100,50|#ff0000|2");
		apps.get(2).recordWhiteboardObjectAction("add", "shape2", "S|C|20,20,80,80|#00ff00|1");
		apps.get(0).shareFile("reporte.pdf", "reporte contenido".getBytes(StandardCharsets.UTF_8));
		apps.get(2).shareFile("imagen.png", "imagen data".getBytes(StandardCharsets.UTF_8));

		for (int i = 0; i < 3; i++)
			for (int j = i + 1; j < 3; j++)
				crossSync(stores.get(i), stores.get(j), wsId);

		for (int i = 0; i < 3; i++) {
			var state = org.q3s.p2p.core.state.WorkspaceStateBuilder.fromEvents(stores.get(i).listEvents(wsId));
			assertEquals(3, state.authorizedMembers().size(), "U" + i + " debe tener 3 miembros");
			assertEquals(3, state.chatMessages().size(), "U" + i + " debe tener 3 mensajes, tiene=" + state.chatMessages().size());
			assertNotNull(state.notes().get("shared-notes"), "U" + i + " debe tener notas");
			assertEquals(2, state.whiteboardObjects().size(), "U" + i + " debe tener 2 objetos pizarra");
			assertEquals(2, state.files().size(), "U" + i + " debe tener 2 archivos");
		}
	}

	@Test void localChatMessageAppearsInStateWithoutHubEcho() {
		var store = new InMemoryEventStore();
		var app = new CoreApplicationService(store, new InMemoryFileChunkStore(), new UuidIdGenerator());
		var created = app.createWorkspace("LocalChat", "A", 1);
		app.attachExistingSession(created.workspaceId(), created.creator().memberId(), "A", "d0", created.creator().membershipToken());

		app.sendChatMessage("mensaje local");

		WorkspaceState state = app.currentState();
		assertEquals(1, state.chatMessages().size(), "mensaje local debe aparecer en el estado inmediatamente");
		assertEquals("mensaje local", state.chatMessages().values().iterator().next().text());

		app.sendChatMessage("segundo mensaje");
		assertEquals(2, app.currentState().chatMessages().size(), "segundo mensaje tambien debe aparecer");
	}

	@Test void localNotesAndWhiteboardAppearInStateWithoutHubEcho() {
		var store = new InMemoryEventStore();
		var app = new CoreApplicationService(store, new InMemoryFileChunkStore(), new UuidIdGenerator());
		var created = app.createWorkspace("LocalContent", "A", 1);
		app.attachExistingSession(created.workspaceId(), created.creator().memberId(), "A", "d0", created.creator().membershipToken());

		app.updateNote("shared-notes", "nota local");
		app.recordWhiteboardObjectAction("add", "rect1", "S|R|1,2,3,4|#000|1");
		app.finishWhiteboardStroke(List.of(new int[]{0,0}, new int[]{10,10}));
		app.finishWhiteboardStroke(List.of(new int[]{5,5}, new int[]{15,15}), "#ff0000", 3);

		WorkspaceState state = app.currentState();
		assertEquals(1, state.notes().size(), "nota local debe aparecer");
		assertEquals("nota local", state.notes().get("shared-notes").text());
		assertEquals(1, state.whiteboardObjects().size(), "objeto pizarra local debe aparecer");
		assertEquals(2, state.strokes().size(), "trazos locales deben aparecer");
		var strokes = new ArrayList<>(state.strokes().values());
		assertEquals("#ff0000", strokes.get(1).color(), "segundo trazo debe tener color rojo");
		assertEquals(3, strokes.get(1).width(), "segundo trazo debe tener grosor 3");
	}

	@Test void richNotesImageSnapshotSurvivesPeerSync() {
		var storeA = new InMemoryEventStore(); var chunksA = new InMemoryFileChunkStore();
		var storeB = new InMemoryEventStore(); var chunksB = new InMemoryFileChunkStore();
		var appA = new CoreApplicationService(storeA, chunksA, new UuidIdGenerator());
		var appB = new CoreApplicationService(storeB, chunksB, new UuidIdGenerator());

		var created = appA.createWorkspace("RichNotes", "A", 1);
		String wsId = created.workspaceId();
		appA.attachExistingSession(wsId, created.creator().memberId(), "A", "devA", created.creator().membershipToken());
		appB.ensureWorkspaceSession(wsId, "RichNotes", "B", "B", "devB", "tokB", 1);
		appB.recordJoinRequest("B", "B", "devB", "tokB");
		crossSync(storeA, storeB, wsId);
		appA.approveJoin("B");
		appA.authorizeKnownMember("B", "B", "devB", "tokB");
		crossSync(storeA, storeB, wsId);

		String richSnapshot = "QNOTES2\n"
				+ "T|false|false|false|14|#000000|SG9sYQ==\n"
				+ "I|2|2|iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR4XmNgYGD4z8DAwMgABXAGAwIADe8BAd3O2jQAAAAASUVORK5CYII=\n";
		appA.updateNote("shared-notes", richSnapshot);
		crossSync(storeA, storeB, wsId);

		String synced = appB.currentState().notes().get("shared-notes").text();
		assertEquals(richSnapshot, synced);
		assertTrue(synced.contains("\nI|2|2|"), "El snapshot rico debe conservar la linea de imagen QNOTES2");
	}

	@Test void peerStatusEventsBuildDistributedConnectionGraph() {
		var storeA = new InMemoryEventStore(); var chunksA = new InMemoryFileChunkStore();
		var storeB = new InMemoryEventStore(); var chunksB = new InMemoryFileChunkStore();
		var appA = new CoreApplicationService(storeA, chunksA, new UuidIdGenerator());
		var appB = new CoreApplicationService(storeB, chunksB, new UuidIdGenerator());

		var created = appA.createWorkspace("PeerGraph", "A", 1);
		String wsId = created.workspaceId();
		appA.attachExistingSession(wsId, created.creator().memberId(), "A", "devA", created.creator().membershipToken());
		appB.ensureWorkspaceSession(wsId, "PeerGraph", "B", "B", "devB", "tokB", 1);
		appB.recordJoinRequest("B", "B", "devB", "tokB");
		crossSync(storeA, storeB, wsId);
		appA.approveJoin("B");
		appA.authorizeKnownMember("B", "B", "devB", "tokB");
		crossSync(storeA, storeB, wsId);

		appA.updatePeerStatus("ws://peer-a", Set.of("B"));
		appB.updatePeerStatus("ws://peer-b", Set.of("A"));
		crossSync(storeA, storeB, wsId);

		WorkspaceState stateA = appA.currentState();
		WorkspaceState stateB = appB.currentState();
		assertEquals("ws://peer-a", stateA.peerUrls().get(created.creator().memberId()));
		assertEquals("ws://peer-b", stateA.peerUrls().get("B"));
		assertTrue(stateA.peerConnections().get(created.creator().memberId()).contains("B"));
		assertTrue(stateB.peerConnections().get("B").contains("A"));
	}

	@Test void authorizedMemberReconnectsWithoutNewApproval() {
		var storeA = new InMemoryEventStore(); var chunksA = new InMemoryFileChunkStore();
		var storeB = new InMemoryEventStore(); var chunksB = new InMemoryFileChunkStore();
		var appA = new CoreApplicationService(storeA, chunksA, new UuidIdGenerator());
		var appB = new CoreApplicationService(storeB, chunksB, new UuidIdGenerator());

		var created = appA.createWorkspace("Reconnect", "A", 1);
		String wsId = created.workspaceId();
		appA.attachExistingSession(wsId, created.creator().memberId(), "A", "devA", created.creator().membershipToken());
		appB.attachExistingSession(wsId, "B", "B", "devB", "tokB");
		appB.recordJoinRequest("B", "B", "devB", "tokB");
		crossSync(storeA, storeB, wsId);
		appA.approveJoin("B");
		appA.authorizeKnownMember("B", "B", "devB", "tokB");
		crossSync(storeA, storeB, wsId);

		var restartedB = new CoreApplicationService(storeB, chunksB, new UuidIdGenerator());
		restartedB.attachExistingSession(wsId, "B", "B", "devB", "tokB");
		assertTrue(restartedB.canReconnect("B", "tokB"));
		int joinRequestsBefore = storeB.listEvents(wsId).stream()
				.filter(e -> "member.join.requested".equals(e.type()) && "B".equals(e.authorMemberId()))
				.toList().size();
		assertEquals(1, joinRequestsBefore, "B no debe generar otra solicitud de ingreso al reconectar");
	}

	@Test void chunkCoordinatorUsesCoreFileIdWhenNameAndSizeCollide() {
		var store = new InMemoryEventStore();
		var app = new CoreApplicationService(store, new InMemoryFileChunkStore(), new UuidIdGenerator());
		var created = app.createWorkspace("Files", "A", 1);
		app.attachExistingSession(created.workspaceId(), created.creator().memberId(), "A", "devA", created.creator().membershipToken());

		app.shareFile("same.txt", "aaaa".getBytes(StandardCharsets.UTF_8));
		Event second = app.shareFile("same.txt", "bbbb".getBytes(StandardCharsets.UTF_8));
		String secondFileId = String.valueOf(second.payload().get("file_id"));

		QFile file = new QFile();
		file.setName("same.txt");
		file.setSize(4);
		file.setMd5("core:" + secondFileId);
		file.setTransferId("transfer-1");
		file.setOwner(User.build(created.creator().memberId()));

		AtomicReference<FileMetadata> completedMetadata = new AtomicReference<>();
		AtomicReference<byte[]> completedBytes = new AtomicReference<>();
		var coordinator = new CoreChunkTransferCoordinator(app, () -> User.build(created.creator().memberId()), event -> {},
				(id, label, current, total) -> {},
				(id, metadata, request, bytes) -> {
					completedMetadata.set(metadata);
					completedBytes.set(bytes);
				},
				(request, reason) -> fail(reason),
				(message, detail) -> {});

		assertTrue(coordinator.request(file));
		assertNotNull(completedMetadata.get());
		assertEquals(secondFileId, completedMetadata.get().fileId());
		assertArrayEquals("bbbb".getBytes(StandardCharsets.UTF_8), completedBytes.get());
	}

	@Test void chunkCoordinatorIsIdempotentOnDuplicateChunks() {
		var store = new InMemoryEventStore();
		var app = new CoreApplicationService(store, new InMemoryFileChunkStore(), new UuidIdGenerator());
		var created = app.createWorkspace("Files", "A", 1);
		app.attachExistingSession(created.workspaceId(), created.creator().memberId(), "A", "devA", created.creator().membershipToken());

		byte[] content = new byte[100_000];
		new java.util.Random(42).nextBytes(content);
		app.shareFile("large.bin", content);
		Event shareEvent = app.events().get(app.events().size() - 1);
		String fileId = String.valueOf(shareEvent.payload().get("file_id"));

		QFile file = new QFile();
		file.setName("large.bin");
		file.setSize(100_000);
		file.setMd5("core:" + fileId);
		file.setTransferId("transfer-dup");
		file.setOwner(User.build(created.creator().memberId()));

		AtomicInteger completionCount = new AtomicInteger(0);
		AtomicInteger fallbackCount = new AtomicInteger(0);

		var coordinator = new CoreChunkTransferCoordinator(app, () -> User.build(created.creator().memberId()), event -> {},
				(id, label, current, total) -> {},
				(id, meta, request, bytes) -> completionCount.incrementAndGet(),
				(request, reason) -> fallbackCount.incrementAndGet(),
				(message, detail) -> {});

		assertTrue(coordinator.request(file));
		assertEquals(1, completionCount.get(), "debe completarse una sola vez");
		assertEquals(0, fallbackCount.get());

		FileMetadata metadata = app.currentState().files().values().stream()
				.filter(m -> m.fileId().equals(fileId)).findFirst().orElseThrow();

		for (String chunkHash : metadata.chunks()) {
			byte[] chunkBytes = app.readChunk(chunkHash).orElse(new byte[0]);
			String payload = CoreChunkTransferProtocol.chunkResponse("transfer-dup", fileId, chunkHash, chunkBytes);
			coordinator.handle(new org.q3s.p2p.model.Event(
					CoreChunkTransferProtocol.CHUNK_RESPONSE,
					org.q3s.p2p.model.User.build("other"),
					payload), null);
		}

		assertEquals(1, completionCount.get(), "debe seguir siendo 1 tras duplicados");
		assertEquals(0, fallbackCount.get());
	}

	@Test void meshServiceIgnoresNewPeersAfterDisconnectAll() {
		var store = new InMemoryEventStore();
		var app = new CoreApplicationService(store, new InMemoryFileChunkStore(), new UuidIdGenerator());
		var adapter = new P2PNetworkAdapter("local", () -> "ws://localhost:0", () -> "ws_test",
				store, (conn, evt) -> {}, msg -> {});
		List<String> debugMessages = new ArrayList<>();
		var service = new P2PMeshService(adapter, app, () -> "ws_test", () -> "ws://localhost:0",
				state -> {}, event -> {}, msg -> debugMessages.add(msg));

		service.disconnectAll();

		User peer = User.build("peer1");
		peer.setPeerUrl("ws://localhost:9999");
		service.peerAppeared(peer);

		assertTrue(debugMessages.stream().noneMatch(msg -> msg.contains("conectando a")),
				"No se debe conectar nuevos peers tras disconnectAll");

		adapter.disconnectAll();
	}

	@Test void chunkCoordinatorFallsBackWhenFinalHashMismatch() {
		var storeA = new InMemoryEventStore(); var chunksA = new InMemoryFileChunkStore();
		var storeB = new InMemoryEventStore(); var chunksB = new InMemoryFileChunkStore();
		var appA = new CoreApplicationService(storeA, chunksA, new UuidIdGenerator());
		var appB = new CoreApplicationService(storeB, chunksB, new UuidIdGenerator());

		var created = appA.createWorkspace("Files", "A", 1);
		appA.attachExistingSession(created.workspaceId(), created.creator().memberId(), "A", "devA", created.creator().membershipToken());

		byte[] content = new byte[100_000];
		new java.util.Random(42).nextBytes(content);
		appA.shareFile("large.bin", content);
		Event shareEvent = appA.events().get(appA.events().size() - 1);
		String fileId = String.valueOf(shareEvent.payload().get("file_id"));

		crossSync(storeA, storeB, created.workspaceId());

		appB.attachExistingSession(created.workspaceId(), "B", "B", "devB", "B");

		QFile file = new QFile();
		file.setName("large.bin");
		file.setSize(100_000);
		file.setMd5("core:" + fileId);
		file.setTransferId("transfer-badhash");
		file.setOwner(User.build("B"));

		AtomicInteger completionCount = new AtomicInteger(0);
		AtomicInteger fallbackCount = new AtomicInteger(0);
		List<org.q3s.p2p.model.Event> outboundEvents = new ArrayList<>();

		var coordinator = new CoreChunkTransferCoordinator(appB, () -> User.build("B"), outboundEvents::add,
				(id, label, cur, tot) -> {},
				(id, meta, req, bytes) -> completionCount.incrementAndGet(),
				(req, reason) -> fallbackCount.incrementAndGet(),
				(msg, detail) -> {});

		assertTrue(coordinator.request(file));
		assertEquals(0, completionCount.get());
		assertEquals(0, fallbackCount.get());

		FileMetadata metadata = appB.currentState().files().values().stream()
				.filter(m -> m.fileId().equals(fileId)).findFirst().orElseThrow();

		byte[] corruptedBytes = new byte[1];
		for (String chunkHash : metadata.chunks()) {
			String payload = CoreChunkTransferProtocol.chunkResponse("transfer-badhash", fileId, chunkHash, corruptedBytes);
			coordinator.handle(new org.q3s.p2p.model.Event(
					CoreChunkTransferProtocol.CHUNK_RESPONSE,
					org.q3s.p2p.model.User.build("peer"),
					payload), null);
		}

		for (String chunkHash : metadata.chunks()) {
			byte[] chunkBytes = appA.readChunk(chunkHash).orElse(new byte[0]);
			String payload = CoreChunkTransferProtocol.chunkResponse("transfer-badhash", fileId, chunkHash, chunkBytes);
			coordinator.handle(new org.q3s.p2p.model.Event(
					CoreChunkTransferProtocol.CHUNK_RESPONSE,
					org.q3s.p2p.model.User.build("peer"),
					payload), null);
		}

		assertEquals(1, completionCount.get(), "debe completarse con chunks correctos");
		assertEquals(0, fallbackCount.get(), "no debe llamar fallback si chunks correctos llegan");
	}

	@Test void chunkCoordinatorRejectsChunkWithInvalidHash() {
		var storeA = new InMemoryEventStore(); var chunksA = new InMemoryFileChunkStore();
		var storeB = new InMemoryEventStore(); var chunksB = new InMemoryFileChunkStore();
		var appA = new CoreApplicationService(storeA, chunksA, new UuidIdGenerator());
		var appB = new CoreApplicationService(storeB, chunksB, new UuidIdGenerator());

		var created = appA.createWorkspace("Files", "A", 1);
		appA.attachExistingSession(created.workspaceId(), created.creator().memberId(), "A", "devA", created.creator().membershipToken());

		byte[] content = new byte[100_000];
		new java.util.Random(42).nextBytes(content);
		appA.shareFile("large.bin", content);
		Event shareEvent = appA.events().get(appA.events().size() - 1);
		String fileId = String.valueOf(shareEvent.payload().get("file_id"));

		crossSync(storeA, storeB, created.workspaceId());
		appB.attachExistingSession(created.workspaceId(), "B", "B", "devB", "B");

		QFile file = new QFile();
		file.setName("large.bin");
		file.setSize(100_000);
		file.setMd5("core:" + fileId);
		file.setTransferId("transfer-badhash2");
		file.setOwner(User.build("B"));

		AtomicInteger completionCount = new AtomicInteger(0);
		AtomicInteger fallbackCount = new AtomicInteger(0);
		List<org.q3s.p2p.model.Event> outboundEvents = new ArrayList<>();

		var coordinator = new CoreChunkTransferCoordinator(appB, () -> User.build("B"), outboundEvents::add,
				(id, label, cur, tot) -> {},
				(id, meta, req, bytes) -> completionCount.incrementAndGet(),
				(req, reason) -> fallbackCount.incrementAndGet(),
				(msg, detail) -> {});

		assertTrue(coordinator.request(file));

		FileMetadata metadata = appB.currentState().files().values().stream()
				.filter(m -> m.fileId().equals(fileId)).findFirst().orElseThrow();
		String firstChunk = metadata.chunks().get(0);

		byte[] corruptedBytes = new byte[]{0, 1, 2, 3};
		String payload = CoreChunkTransferProtocol.chunkResponse("transfer-badhash2", fileId, firstChunk, corruptedBytes);
		coordinator.handle(new org.q3s.p2p.model.Event(
				CoreChunkTransferProtocol.CHUNK_RESPONSE,
				org.q3s.p2p.model.User.build("peer"),
				payload), null);

		assertEquals(0, completionCount.get(), "no debe completar con chunk invalido");
		assertEquals(0, fallbackCount.get());

		byte[] correctBytes = appA.readChunk(firstChunk).orElseThrow();
		payload = CoreChunkTransferProtocol.chunkResponse("transfer-badhash2", fileId, firstChunk, correctBytes);
		coordinator.handle(new org.q3s.p2p.model.Event(
				CoreChunkTransferProtocol.CHUNK_RESPONSE,
				org.q3s.p2p.model.User.build("peer"),
				payload), null);

		for (int i = 1; i < metadata.chunks().size(); i++) {
			byte[] chunkBytes = appA.readChunk(metadata.chunks().get(i)).orElse(new byte[0]);
			payload = CoreChunkTransferProtocol.chunkResponse("transfer-badhash2", fileId, metadata.chunks().get(i), chunkBytes);
			coordinator.handle(new org.q3s.p2p.model.Event(
					CoreChunkTransferProtocol.CHUNK_RESPONSE,
					org.q3s.p2p.model.User.build("peer"),
					payload), null);
		}

		assertEquals(1, completionCount.get(), "debe completar tras enviar chunks correctos");
	}

	@Test void chunkCoordinatorRejectsChunkOutsideMetadata() {
		var storeA = new InMemoryEventStore(); var chunksA = new InMemoryFileChunkStore();
		var storeB = new InMemoryEventStore(); var chunksB = new InMemoryFileChunkStore();
		var appA = new CoreApplicationService(storeA, chunksA, new UuidIdGenerator());

		var created = appA.createWorkspace("Files", "A", 1);
		appA.attachExistingSession(created.workspaceId(), created.creator().memberId(), "A", "devA", created.creator().membershipToken());
		appA.shareFile("test.txt", "hello".getBytes(StandardCharsets.UTF_8));
		crossSync(storeA, storeB, created.workspaceId());

		var appB = new CoreApplicationService(storeB, chunksB, new UuidIdGenerator());
		appB.attachExistingSession(created.workspaceId(), "B", "B", "devB", "B");
		String fileId = appB.currentState().files().values().iterator().next().fileId();

		QFile file = new QFile();
		file.setName("test.txt");
		file.setSize(5);
		file.setMd5("core:" + fileId);
		file.setTransferId("transfer-outside");
		file.setOwner(User.build("B"));

		AtomicInteger completionCount = new AtomicInteger(0);
		var coordinator = new CoreChunkTransferCoordinator(appB, () -> User.build("B"), event -> {},
				(id, label, cur, tot) -> {},
				(id, meta, req, bytes) -> completionCount.incrementAndGet(),
				(req, reason) -> {},
				(msg, detail) -> {});

		assertTrue(coordinator.request(file));

		String fakeChunk = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
		String payload = CoreChunkTransferProtocol.chunkResponse("transfer-outside", fileId, fakeChunk, new byte[]{1});
		coordinator.handle(new org.q3s.p2p.model.Event(
				CoreChunkTransferProtocol.CHUNK_RESPONSE,
				org.q3s.p2p.model.User.build("peer"),
				payload), null);

		FileMetadata metadata = appB.currentState().files().values().iterator().next();
		for (String chunkHash : metadata.chunks()) {
			byte[] chunkBytes = appA.readChunk(chunkHash).orElse(new byte[0]);
			payload = CoreChunkTransferProtocol.chunkResponse("transfer-outside", fileId, chunkHash, chunkBytes);
			coordinator.handle(new org.q3s.p2p.model.Event(
					CoreChunkTransferProtocol.CHUNK_RESPONSE,
					org.q3s.p2p.model.User.build("peer"),
					payload), null);
		}

		assertEquals(1, completionCount.get(), "debe completar con chunks reales ignorando fake");
	}

	@Test void chunkCoordinatorDoesNotSendChunkForWrongFileId() {
		var storeA = new InMemoryEventStore(); var chunksA = new InMemoryFileChunkStore();
		var appA = new CoreApplicationService(storeA, chunksA, new UuidIdGenerator());

		var created = appA.createWorkspace("Files", "A", 1);
		appA.attachExistingSession(created.workspaceId(), created.creator().memberId(), "A", "devA", created.creator().membershipToken());
		appA.shareFile("a.txt", "contentA".getBytes(StandardCharsets.UTF_8));
		appA.shareFile("b.txt", "contentB".getBytes(StandardCharsets.UTF_8));
		List<Event> events = appA.events();
		String fileIdA = String.valueOf(events.get(events.size() - 2).payload().get("file_id"));
		String fileIdB = String.valueOf(events.get(events.size() - 1).payload().get("file_id"));

		FileMetadata metaA = appA.currentState().files().values().stream()
				.filter(m -> m.fileId().equals(fileIdA)).findFirst().orElseThrow();
		String chunkOfA = metaA.chunks().get(0);

		List<org.q3s.p2p.model.Event> sent = new ArrayList<>();
		var coordinator = new CoreChunkTransferCoordinator(appA, () -> User.build(created.creator().memberId()), sent::add,
				(id, label, cur, tot) -> {},
				(id, meta, req, bytes) -> {},
				(req, reason) -> {},
				(msg, detail) -> {});

		String payload = CoreChunkTransferProtocol.chunkRequest("some-transfer", fileIdB, chunkOfA);
		coordinator.handle(new org.q3s.p2p.model.Event(
				CoreChunkTransferProtocol.CHUNK_REQUEST,
				org.q3s.p2p.model.User.build("peer"),
				payload), null);

		boolean hasResponse = sent.stream().anyMatch(e -> CoreChunkTransferProtocol.CHUNK_RESPONSE.equals(e.getName()));
		assertFalse(hasResponse, "No debe responder chunk de archivo B con hash del archivo A");
	}

	@Test void meshServiceRetriesKnownPeerAfterDisconnected() throws Exception {
		var store = new InMemoryEventStore();
		var app = new CoreApplicationService(store, new InMemoryFileChunkStore(), new UuidIdGenerator());
		var created = app.createWorkspace("Retry", "local", 2);
		app.attachExistingSession(created.workspaceId(), created.creator().memberId(), "local", "dev", created.creator().membershipToken());

		var adapter = new P2PNetworkAdapter("local", () -> "ws://local", () -> created.workspaceId(),
				store, (conn, evt) -> {}, msg -> {}) {
			int connectAttempts = 0;
			@Override public void connectTo(String peerId, String peerUrl, String workspaceId, Runnable onReady) {
				connectAttempts++;
				if (onReady != null) onReady.run();
			}
			@Override public boolean hasPeer(String peerId) { return false; }
			@Override public Set<String> connectedPeers() { return Set.of(); }
		};

		var service = new P2PMeshService(adapter, app, () -> created.workspaceId(), () -> "ws://local",
				state -> {}, event -> {}, msg -> {}, new MeshPolicy(1, 2));

		User peer = User.build("peer1");
		peer.setPeerUrl("ws://localhost:9999");

		service.peerAppeared(peer);
		waitUntil(() -> adapter.connectAttempts >= 1, "Primer peerAppeared debe intentar conectar");
		int firstAttempts = adapter.connectAttempts;
		assertTrue(firstAttempts >= 1, "Primer peerAppeared debe intentar conectar");

		service.peerAppeared(peer);
		waitUntil(() -> adapter.connectAttempts >= 2, "Debe reintentar conectar peer ya conocido pero inactivo");
		assertTrue(adapter.connectAttempts >= 2, "Debe reintentar conectar peer ya conocido pero inactivo");
		service.disconnectAll();
	}

	@Test void meshServiceDoesNotStartMoreThanTargetPendingConnections() throws Exception {
		var store = new InMemoryEventStore();
		var app = new CoreApplicationService(store, new InMemoryFileChunkStore(), new UuidIdGenerator());
		var created = app.createWorkspace("Pending", "local", 2);
		app.attachExistingSession(created.workspaceId(), created.creator().memberId(), "local", "dev", created.creator().membershipToken());

		for (int i = 1; i <= 10; i++) {
			workspaceWithRemotePeer(store, created.workspaceId(), "peer" + i, "ws://localhost:" + (9000 + i));
		}

		var adapter = new P2PNetworkAdapter("local", () -> "ws://local", () -> created.workspaceId(),
				store, (conn, evt) -> {}, msg -> {}) {
			int connectAttempts = 0;
			@Override public void connectTo(String peerId, String peerUrl, String workspaceId, Runnable onReady) {
				connectAttempts++;
			}
			@Override public boolean hasPeer(String peerId) { return false; }
			@Override public Set<String> connectedPeers() { return Set.of(); }
		};

		var service = new P2PMeshService(adapter, app, () -> created.workspaceId(), () -> "ws://local",
				state -> {}, event -> {}, msg -> {}, new MeshPolicy(2, 4));

		service.applyState(app.currentState());
		waitUntil(() -> adapter.connectAttempts >= 2, "Debe iniciar conexiones pendientes hasta target");
		assertEquals(2, adapter.connectAttempts,
				"Debe iniciar exactamente targetConnectionsPerPeer conexiones pendientes");
		service.disconnectAll();
	}

	@Test void meshServicePeerDiscoveryStateRebalancesWithoutUiCallback() throws Exception {
		var store = new InMemoryEventStore();
		var app = new CoreApplicationService(store, new InMemoryFileChunkStore(), new UuidIdGenerator());
		var created = app.createWorkspace("Discovery", "local", 1);
		app.attachExistingSession(created.workspaceId(), created.creator().memberId(), "local", "dev", created.creator().membershipToken());
		workspaceWithRemotePeer(store, created.workspaceId(), "peer1", "ws://localhost:9101");

		var callbacks = new AtomicInteger();
		var adapter = new P2PNetworkAdapter("local", () -> "ws://local", () -> created.workspaceId(),
				store, (conn, evt) -> {}, msg -> {}) {
			int connectAttempts = 0;
			@Override public void connectTo(String peerId, String peerUrl, String workspaceId, Runnable onReady) {
				connectAttempts++;
			}
			@Override public boolean hasPeer(String peerId) { return false; }
			@Override public Set<String> connectedPeers() { return Set.of(); }
		};

		var service = new P2PMeshService(adapter, app, () -> created.workspaceId(), () -> "ws://local",
				state -> callbacks.incrementAndGet(), event -> {}, msg -> {}, new MeshPolicy(1, 2));

		service.applyPeerDiscoveryState(app.currentState());

		waitUntil(() -> adapter.connectAttempts >= 1, "Debe conectar usando discovery sin callback UI");
		assertEquals(0, callbacks.get(), "Discovery usado desde UI no debe invocar onStateChanged ni recursar");
		service.disconnectAll();
	}

	@Test void meshServiceForcePublishesStatusEvenIfUnchanged() {
		var store = new InMemoryEventStore();
		var app = new CoreApplicationService(store, new InMemoryFileChunkStore(), new UuidIdGenerator());
		var created = app.createWorkspace("ForceStatus", "local", 1);
		app.attachExistingSession(created.workspaceId(), created.creator().memberId(), "local", "dev", created.creator().membershipToken());
		var published = new ArrayList<Event>();

		var adapter = new P2PNetworkAdapter("local", () -> "ws://local", () -> created.workspaceId(),
				store, (conn, evt) -> {}, msg -> {}) {
			@Override public Set<String> connectedPeers() { return Set.of(); }
		};

		var service = new P2PMeshService(adapter, app, () -> created.workspaceId(), () -> "ws://local",
				state -> {}, published::add, msg -> {}, new MeshPolicy(1, 2));

		service.publishPeerStatusIfChanged();
		service.publishPeerStatusIfChanged();
		Event forced = service.forcePublishPeerStatus();

		assertEquals(2, published.size(), "Debe publicar inicial y forzada, no la repetida sin cambios");
		assertNotNull(forced, "La publicacion forzada debe devolver el evento para envio directo");
		assertEquals(org.q3s.p2p.core.events.EventTypes.PEER_STATUS_UPDATED, forced.type());
		service.disconnectAll();
	}

	@Test void meshServiceDoesNotConnectAboveMaxConnections() throws Exception {
		var store = new InMemoryEventStore();
		var app = new CoreApplicationService(store, new InMemoryFileChunkStore(), new UuidIdGenerator());
		var created = app.createWorkspace("Max", "local", 2);
		app.attachExistingSession(created.workspaceId(), created.creator().memberId(), "local", "dev", created.creator().membershipToken());

		var adapter = new P2PNetworkAdapter("local", () -> "ws://local", () -> created.workspaceId(),
				store, (conn, evt) -> {}, msg -> {}) {
			int connectAttempts = 0;
			@Override public void connectTo(String peerId, String peerUrl, String workspaceId, Runnable onReady) {
				connectAttempts++;
				if (onReady != null) onReady.run();
			}
			@Override public boolean hasPeer(String peerId) {
				return connectedPeers().contains(peerId);
			}
			Set<String> activePeers = new java.util.LinkedHashSet<>();
			@Override public Set<String> connectedPeers() { return activePeers; }
		};

		var service = new P2PMeshService(adapter, app, () -> created.workspaceId(), () -> "ws://local",
				state -> {}, event -> {}, msg -> {}, new MeshPolicy(2, 4));

		User peer5 = User.build("peer5");
		peer5.setPeerUrl("ws://localhost:9999");

		adapter.activePeers.addAll(Set.of("p1", "p2", "p3", "p4"));
		int before = adapter.connectAttempts;
		service.peerAppeared(peer5);
		assertEquals(before, adapter.connectAttempts, "No debe conectar cuando activePeers ya alcanzo maxConnectionsPerPeer");
		service.disconnectAll();
	}

	@Test void meshServiceDoesNotStartDuplicatePendingConnectionsForSamePeer() throws Exception {
		var store = new InMemoryEventStore();
		var app = new CoreApplicationService(store, new InMemoryFileChunkStore(), new UuidIdGenerator());
		var created = app.createWorkspace("Race", "local", 2);
		app.attachExistingSession(created.workspaceId(), created.creator().memberId(), "local", "dev", created.creator().membershipToken());

		var adapter = new P2PNetworkAdapter("local", () -> "ws://local", () -> created.workspaceId(),
				store, (conn, evt) -> {}, msg -> {}) {
			int connectAttempts = 0;
			@Override public void connectTo(String peerId, String peerUrl, String workspaceId, Runnable onReady) {
				connectAttempts++;
			}
			@Override public boolean hasPeer(String peerId) { return false; }
			@Override public Set<String> connectedPeers() { return Set.of(); }
		};

		var service = new P2PMeshService(adapter, app, () -> created.workspaceId(), () -> "ws://local",
				state -> {}, event -> {}, msg -> {}, new MeshPolicy(2, 4));

		User peer = User.build("peer1");
		peer.setPeerUrl("ws://localhost:9999");

		Thread t1 = new Thread(() -> service.peerAppeared(peer));
		Thread t2 = new Thread(() -> service.peerAppeared(peer));
		t1.start();
		t2.start();
		t1.join();
		t2.join();

		waitUntil(() -> adapter.connectAttempts >= 1, "Debe intentar una conexion");
		assertEquals(1, adapter.connectAttempts, "No debe iniciar conexiones duplicadas al mismo peer");
		service.disconnectAll();
	}

	@Test void meshServiceTrimDoesNotRemovePeerFromCatalog() throws Exception {
		var store = new InMemoryEventStore();
		var app = new CoreApplicationService(store, new InMemoryFileChunkStore(), new UuidIdGenerator());
		var created = app.createWorkspace("TrimCatalog", "local", 2);
		app.attachExistingSession(created.workspaceId(), created.creator().memberId(), "local", "dev", created.creator().membershipToken());

		for (int i = 1; i <= 5; i++) {
			workspaceWithRemotePeer(store, created.workspaceId(), "peer" + i, "ws://localhost:" + (9100 + i));
		}

		var active = new java.util.LinkedHashSet<>(Set.of("peer1", "peer2", "peer3", "peer4", "peer5"));
		var disconnected = new java.util.concurrent.atomic.AtomicReference<String>();
		var adapter = new P2PNetworkAdapter("local", () -> "ws://local", () -> created.workspaceId(),
				store, (conn, evt) -> {}, msg -> {}) {
			@Override public void disconnectFrom(String peerId) {
				disconnected.set(peerId);
				active.remove(peerId);
			}
			@Override public boolean hasPeer(String peerId) {
				return active.contains(peerId);
			}
			@Override public Set<String> connectedPeers() {
				return new java.util.LinkedHashSet<>(active);
			}
		};

		var service = new P2PMeshService(adapter, app, () -> created.workspaceId(), () -> "ws://local",
				state -> {}, event -> {}, msg -> {}, new MeshPolicy(2, 4));

		service.applyState(app.currentState());

		assertTrue(adapter.connectedPeers().size() <= 4, "Debe recortar conexiones hasta maxConnectionsPerPeer");
		String trimmedPeer = disconnected.get();
		assertNotNull(trimmedPeer, "Debe desconectar al menos un peer durante trim");
		assertTrue(service.catalog().containsKey(trimmedPeer),
				"El peer recortado debe mantenerse en el catalogo: " + trimmedPeer);
		service.disconnectAll();
	}

	private void workspaceWithRemotePeer(InMemoryEventStore store, String wsId, String peerId, String peerUrl) {
		var f = new org.q3s.p2p.core.events.EventFactory(
				new org.q3s.p2p.adapters.memory.UuidIdGenerator(),
				new org.q3s.p2p.adapters.memory.SystemClockProvider());
		store.append(f.create(wsId, org.q3s.p2p.core.events.EventTypes.PEER_STATUS_UPDATED, peerId,
				Map.of("member_id", peerId, "peer_url", peerUrl, "connected_peers", new java.util.ArrayList<String>()), null));
	}

	private void waitUntil(java.util.function.BooleanSupplier condition, String message) throws Exception {
		long deadline = System.currentTimeMillis() + 2_000;
		while (System.currentTimeMillis() < deadline) {
			if (condition.getAsBoolean()) return;
			Thread.sleep(20);
		}
		fail(message);
	}

	private void crossSync(InMemoryEventStore a, InMemoryEventStore b, String wsId) {
		for (var e : a.listEvents(wsId)) if (!b.hasEvent(e.eventId())) b.append(e);
		for (var e : b.listEvents(wsId)) if (!a.hasEvent(e.eventId())) a.append(e);
	}
}
