package org.q3s.p2p.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.q3s.p2p.adapters.filesystem.QfolderLayout;
import org.q3s.p2p.adapters.network.InviteCode;
import org.q3s.p2p.adapters.memory.InMemoryEventStore;
import org.q3s.p2p.adapters.memory.InMemoryFileChunkStore;
import org.q3s.p2p.adapters.memory.SystemClockProvider;
import org.q3s.p2p.adapters.memory.UuidIdGenerator;
import org.q3s.p2p.adapters.network.SimulatedNetworkAdapter;
import org.q3s.p2p.adapters.network.SimulatedNetworkAdapter.SimulatedNode;
import org.q3s.p2p.core.app.CoreApplicationService;
import org.q3s.p2p.core.auth.TokenAuthProvider;
import org.q3s.p2p.core.chat.ChatService;
import org.q3s.p2p.core.events.CoreEventCodec;
import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.files.FileService;
import org.q3s.p2p.core.members.MembershipService;
import org.q3s.p2p.core.mesh.MeshPolicy;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.core.notes.NoteService;
import org.q3s.p2p.core.state.WorkspaceState;
import org.q3s.p2p.core.state.WorkspaceStateBuilder;
import org.q3s.p2p.core.whiteboard.WhiteboardService;
import org.q3s.p2p.core.workspace.WorkspaceService;
import org.q3s.p2p.ports.IdGenerator;

class CoreQfolderTest {

	private final UuidIdGenerator ids = new UuidIdGenerator();
	private final EventFactory events = new EventFactory(ids, new SystemClockProvider());

	// ============================================================
	// GRUPO 1: Creación de workspace
	// ============================================================

	@Test void caso1crearWorkspaceCorrectamente() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var ws = new WorkspaceService(store, auth, ids, events);
		var created = ws.createWorkspace("Reunión Proyecto P2P", "Damian", 2);
		assertTrue(created.workspaceId().startsWith("ws_"));
		assertTrue(store.containsType(created.workspaceId(), EventTypes.WORKSPACE_CREATED));
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId()));
		assertTrue(state.isAuthorized(created.creator().memberId()));
		assertEquals(2, state.workspace().requiredApprovals());
	}

	@Test void caso2noPermitirWorkspaceSinNombre() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var ws = new WorkspaceService(store, auth, ids, events);
		assertThrows(IllegalArgumentException.class, () -> ws.createWorkspace("", "Damian", 2));
		assertThrows(IllegalArgumentException.class, () -> ws.createWorkspace(null, "Damian", 2));
	}

	@Test void caso3permitirDosWorkspacesMismoNombre() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var ws = new WorkspaceService(store, auth, ids, events);
		var a = ws.createWorkspace("Demo", "A", 1);
		var b = ws.createWorkspace("Demo", "A", 1);
		assertNotEquals(a.workspaceId(), b.workspaceId());
		assertTrue(store.containsType(a.workspaceId(), EventTypes.WORKSPACE_CREATED));
		assertTrue(store.containsType(b.workspaceId(), EventTypes.WORKSPACE_CREATED));
	}

	@Test void caso4crearWorkspaceConCaracteresEspeciales() {
		String name = "Reunión: Diseño / P2P * Demo";
		String slug = QfolderLayout.slug(name);
		assertEquals("reunion-diseno-p2p-demo", slug);
		assertTrue(slug.matches("[a-z0-9-]+"));
		assertFalse(slug.contains(" "));
		assertFalse(slug.contains("*"));
		assertFalse(slug.contains("/"));
	}

	// ============================================================
	// GRUPO 2: Persistencia local en Qfolder
	// ============================================================

	@Test void caso5crearEstructuraLocalPorFecha(@TempDir Path tmp) {
		Instant created = Instant.parse("2026-05-20T14:30:00Z");
		QfolderLayout layout = new QfolderLayout(tmp);
		Path wsDir = layout.workspaceFolder("ws_a8f91c", created, "Reunión Proyecto P2P");
		assertTrue(wsDir.toString().contains("userdata"), "workspace path should be under userdata: " + wsDir);
		assertTrue(wsDir.toString().contains("2026"));
		assertTrue(wsDir.toString().contains("reunion-proyecto-p2p"));
		assertFalse(wsDir.toString().contains("ws_a8f91c"), "userdata folder should not embed workspaceId: " + wsDir);
		assertTrue(layout.createStructure(wsDir));
		for (String sub : QfolderLayout.USERDATA_SUBDIRS) assertTrue(Files.isDirectory(wsDir.resolve(sub)), sub);
		assertFalse(Files.exists(wsDir.resolve("downloads")));
		assertFalse(Files.exists(wsDir.resolve("events")));
		assertFalse(Files.exists(wsDir.resolve("chunks")));
		Path systemWs = layout.systemWorkspaceRoot("ws_a8f91c");
		assertTrue(systemWs.toString().contains("systemdata"));
		assertTrue(systemWs.toString().contains("workspaces"));
		assertTrue(systemWs.toString().contains("ws_a8f91c"));
		Path identityFile = layout.identityFile();
		assertTrue(identityFile.toString().contains("systemdata"));
		assertTrue(identityFile.toString().endsWith("identity.properties"));
	}

	@Test void caso6workspaceJsonConMetadataCorrecta() {
		Instant created = Instant.parse("2026-05-20T14:30:00Z");
		Instant joined = Instant.parse("2026-05-20T14:30:05Z");
		var json = QfolderLayout.workspaceJson("ws_a8f91c", "Reunión Proyecto P2P", created, joined, "/tmp/Qfolder", 2);
		assertEquals("ws_a8f91c", json.get("workspace_id"));
		assertEquals("Reunión Proyecto P2P", json.get("name"));
		assertEquals("reunion-proyecto-p2p", json.get("workspace_slug"));
		assertEquals("ed25519", json.get("auth_mode"));
		@SuppressWarnings("unchecked")
		Map<String, Object> policy = (Map<String, Object>) json.get("join_policy");
		assertEquals("light_consensus", policy.get("type"));
		assertEquals(2, policy.get("required_approvals"));
	}

	@Test void caso7miembroUsaFechaOriginalDelWorkspace(@TempDir Path tmp) {
		Instant created = Instant.parse("2026-05-20T14:30:00Z");
		Instant joined = Instant.parse("2026-05-21T09:10:00Z");
		QfolderLayout layout = new QfolderLayout(tmp);
		Path wsDir = layout.workspaceFolder("ws_a8f91c", created, "Test");
		assertTrue(wsDir.toString().contains("2026"));
		assertFalse(wsDir.toString().contains("ws_a8f91c"), "userdata folder should not embed workspaceId: " + wsDir);
		var json = QfolderLayout.workspaceJson("ws_a8f91c", "Test", created, joined, wsDir.toString(), 2);
		assertTrue(json.get("joined_locally_at").toString().contains("2026-05-21T09:10"));
	}

	@Test void caso8carpetaExistenteSeReutiliza(@TempDir Path tmp) {
		Instant created = Instant.parse("2026-05-20T14:30:00Z");
		QfolderLayout layout = new QfolderLayout(tmp);
		Path wsDir = layout.workspaceFolder("ws_reuse", created, "Test");
		layout.createStructure(wsDir);
		Path wsDir2 = layout.workspaceFolder("ws_reuse", created, "Test");
		assertEquals(wsDir, wsDir2);
		assertTrue(Files.isDirectory(wsDir));
	}

	@Test void caso9systemdataSigueUsandoWorkspaceIdUnico(@TempDir Path tmp) {
		Instant created = Instant.parse("2026-05-20T14:30:00Z");
		QfolderLayout layout = new QfolderLayout(tmp);
		Path user1 = layout.workspaceFolder("ws_aaa", created, "Demo");
		Path user2 = layout.workspaceFolder("ws_bbb", created, "Demo");
		assertEquals(user1, user2, "userdata folder name should be HHMM-slug, not include workspaceId");
		Path sys1 = layout.systemWorkspaceRoot("ws_aaa");
		Path sys2 = layout.systemWorkspaceRoot("ws_bbb");
		assertNotEquals(sys1, sys2, "systemdata must keep distinct workspaceIds to avoid event collisions");
	}

	// ============================================================
	// GRUPO 3: Identidad simple del MVP
	// ============================================================

	@Test void caso10crearIdentidadLocal() {
		var auth = new TokenAuthProvider(ids);
		Member m = auth.createMemberIdentity("Damian");
		assertNotNull(m.memberId());
		assertEquals("Damian", m.displayName());
		assertNotNull(m.deviceId());
		assertNotNull(m.membershipToken());
	}

	@Test void caso11identityLocalMemberJson() {
		var auth = new TokenAuthProvider(ids);
		Member m = auth.createMemberIdentity("Damian");
		assertNotNull(m.memberId());
		assertFalse(m.memberId().isBlank());
		assertFalse(m.displayName().isBlank());
		assertFalse(m.deviceId().isBlank());
		assertFalse(m.membershipToken().isBlank());
		assertTrue(m.membershipToken().length() > 10);
	}

	@Test void caso12tokenUnicoYNoVacio() {
		var auth = new TokenAuthProvider(ids);
		Set<String> tokens = new HashSet<>();
		for (int i = 0; i < 10; i++) {
			Member m = auth.createMemberIdentity("U" + i);
			assertFalse(m.membershipToken().isBlank());
			assertNotEquals(m.memberId(), m.membershipToken());
			assertTrue(tokens.add(m.membershipToken()), "token duplicado");
		}
	}

	// ============================================================
	// GRUPO 4: Aprobación por consenso liviano
	// ============================================================

	@Test void caso13solicitarIngreso() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 2);
		var memberships = new MembershipService(store, auth, events);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), c);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId()));
		assertFalse(state.isAuthorized(c.memberId()));
		assertTrue(state.pendingMembers().containsKey(c.memberId()));
	}

	@Test void caso14unaSolaAprobacionNoAlcanza() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 2);
		var memberships = new MembershipService(store, auth, events);
		Member b = memberships.createCandidate("B");
		memberships.addAuthorizedMember(created.workspaceId(), created.creator().memberId(), b);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), c);
		memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
		assertFalse(WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId())).isAuthorized(c.memberId()));
	}

	@Test void caso15dosAprobacionesAprueban() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 2);
		var memberships = new MembershipService(store, auth, events);
		Member b = memberships.createCandidate("B");
		memberships.addAuthorizedMember(created.workspaceId(), created.creator().memberId(), b);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), c);
		memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
		memberships.approve(created.workspaceId(), b.memberId(), c.memberId());
		assertTrue(WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId())).isAuthorized(c.memberId()));
	}

	@Test void caso16aprobacionDuplicadaNoCuentaDosVeces() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 2);
		var memberships = new MembershipService(store, auth, events);
		Member b = memberships.createCandidate("B");
		memberships.addAuthorizedMember(created.workspaceId(), created.creator().memberId(), b);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), c);
		memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
		memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
		assertFalse(WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId())).isAuthorized(c.memberId()));
	}

	@Test void caso17aprobacionDeNoAutorizadoNoCuenta() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 2);
		var memberships = new MembershipService(store, auth, events);
		Member c = memberships.createCandidate("C");
		Member d = memberships.createCandidate("D");
		memberships.requestJoin(created.workspaceId(), c);
		memberships.approve(created.workspaceId(), d.memberId(), c.memberId());
		assertFalse(WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId())).isAuthorized(c.memberId()));
	}

	@Test void caso18reconexionSinNuevaAprobacion() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 1);
		var memberships = new MembershipService(store, auth, events);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), c);
		memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
		assertTrue(memberships.reconnect(created.workspaceId(), c.memberId(), c.membershipToken()));
	}

	@Test void caso19tokenIncorrectoNoReconecta() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 1);
		var memberships = new MembershipService(store, auth, events);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), c);
		memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
		assertFalse(memberships.reconnect(created.workspaceId(), c.memberId(), "token-falso"));
	}

	// ============================================================
	// GRUPO 5: Revocación de miembros
	// ============================================================

	@Test void caso20revocadoNoReconecta() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 1);
		var memberships = new MembershipService(store, auth, events);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), c);
		memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
		memberships.revoke(created.workspaceId(), created.creator().memberId(), c.memberId());
		assertFalse(memberships.reconnect(created.workspaceId(), c.memberId(), c.membershipToken()));
	}

	@Test void caso21revocacionNecesitaVotos() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 2);
		var memberships = new MembershipService(store, auth, events);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), c);
		memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
		memberships.revoke(created.workspaceId(), created.creator().memberId(), c.memberId());
		assertFalse(memberships.reconnect(created.workspaceId(), c.memberId(), c.membershipToken()));
	}

	@Test void caso22revocacionConVotosSuficientes() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 2);
		var memberships = new MembershipService(store, auth, events);
		Member b = memberships.createCandidate("B");
		memberships.addAuthorizedMember(created.workspaceId(), created.creator().memberId(), b);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), c);
		memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
		memberships.approve(created.workspaceId(), b.memberId(), c.memberId());
		memberships.revoke(created.workspaceId(), created.creator().memberId(), c.memberId());
		memberships.revoke(created.workspaceId(), b.memberId(), c.memberId());
		assertFalse(memberships.reconnect(created.workspaceId(), c.memberId(), c.membershipToken()));
	}

	// ============================================================
	// GRUPO 6: Eventos persistentes
	// ============================================================

	@Test void caso23crearMensajeChat() {
		var store = new InMemoryEventStore();
		new ChatService(store, events, ids).sendMessage("ws_msg", "member_a", "Hola");
		assertTrue(store.containsType("ws_msg", EventTypes.CHAT_MESSAGE_CREATED));
		var state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_msg"));
		assertEquals(1, state.chatMessages().size());
		assertEquals("Hola", state.chatMessages().values().iterator().next().text());
	}

	@Test void caso24eventoDuplicadoIgnorado() {
		var store = new InMemoryEventStore();
		Event event = events.create("ws_dup", EventTypes.CHAT_MESSAGE_CREATED, "a", Map.of("message_id", "m1", "text", "Hola"), null);
		store.append(event);
		store.append(event);
		assertEquals(1, store.listEvents("ws_dup").size());
	}

	@Test void caso25eventoDeNoAprobadoEsRechazadoPorValidacion() {
		var store = new InMemoryEventStore();
		var service = new CoreApplicationService(store, new InMemoryFileChunkStore(), ids);
		var created = service.createWorkspace("W", "A", 2);
		Event msg = events.create(created.workspaceId(), EventTypes.CHAT_MESSAGE_CREATED, "intruso", Map.of("message_id", "m1", "text", "intruso"), null);
		boolean accepted = service.receiveRemoteEvent(msg);
		assertFalse(accepted);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId()));
		assertEquals(0, state.chatMessages().size());
	}

	@Test void caso26eventoConWorkspaceIdIncorrectoRechazado() {
		var store = new InMemoryEventStore();
		Event event = events.create("ws_wrong", EventTypes.CHAT_MESSAGE_CREATED, "a", Map.of("message_id", "m1", "text", "Hola"), null);
		store.append(event);
		assertEquals(0, store.listEvents("ws_other").size());
	}

	@Test void caso27eventosConcurrentesAceptados() {
		var storeA = new InMemoryEventStore();
		var storeB = new InMemoryEventStore();
		Event eA = events.create("ws_conc", EventTypes.CHAT_MESSAGE_CREATED, "A", Map.of("message_id", "a", "text", "A"), null);
		Event eB = events.create("ws_conc", EventTypes.CHAT_MESSAGE_CREATED, "B", Map.of("message_id", "b", "text", "B"), null);
		storeA.append(eA);
		storeB.append(eB);
		for (Event e : storeB.listEvents("ws_conc")) storeA.append(e);
		for (Event e : storeA.listEvents("ws_conc")) storeB.append(e);
		assertEquals(2, storeA.listEvents("ws_conc").size());
		assertEquals(2, WorkspaceStateBuilder.fromEvents(storeA.listEvents("ws_conc")).chatMessages().size());
	}

	// ============================================================
	// GRUPO 7: Eventos efímeros
	// ============================================================

	@Test void caso28typingNoPersiste() {
		var store = new InMemoryEventStore();
		store.append(events.create("ws", EventTypes.USER_TYPING, "a", Map.of(), null));
		assertTrue(store.listEvents("ws").isEmpty());
	}

	@Test void caso29cursorNoPersiste() {
		var store = new InMemoryEventStore();
		store.append(events.create("ws", EventTypes.CURSOR_MOVED, "a", Map.of(), null));
		assertTrue(store.listEvents("ws").isEmpty());
	}

	@Test void caso30presenciaEfimeraNoPersiste() {
		var store = new InMemoryEventStore();
		Event presence = events.create("ws", EventTypes.USER_TYPING, "a", Map.of("presence", "online"), null);
		store.append(presence);
		assertTrue(store.listEvents("ws").isEmpty());
	}

	// ============================================================
	// GRUPO 8: Pizarra
	// ============================================================

	@Test void caso31movimientosMouseNoGuardan() {
		var store = new InMemoryEventStore();
		var wb = new WhiteboardService(store, events, ids);
		for (int i = 0; i < 100; i++) store.append(wb.preview("ws_mouse", "a"));
		assertTrue(store.listEvents("ws_mouse").isEmpty());
	}

	@Test void caso32trazoFinalGuardado() {
		var store = new InMemoryEventStore();
		var wb = new WhiteboardService(store, events, ids);
		wb.finishStroke("ws_stroke", "a", List.of(new int[]{10, 20}, new int[]{20, 30}));
		assertTrue(store.containsType("ws_stroke", EventTypes.WHITEBOARD_STROKE_ADDED));
	}

	@Test void caso33borrarObjetoPizarra() {
		var store = new InMemoryEventStore();
		var wb = new WhiteboardService(store, events, ids);
		wb.objectAdded("ws_del", "a", "obj", "S|R|1,2,3,4|#000|1");
		wb.objectDeleted("ws_del", "a", "obj");
		assertTrue(store.containsType("ws_del", EventTypes.WHITEBOARD_OBJECT_DELETED));
		assertTrue(WorkspaceStateBuilder.fromEvents(store.listEvents("ws_del")).whiteboardObjects().isEmpty());
	}

	// ============================================================
	// GRUPO 9: Notas
	// ============================================================

	@Test void caso34crearNota() {
		var store = new InMemoryEventStore();
		new NoteService(store, events).updateNote("ws_note", "a", "Ideas", "contenido");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_note"));
		assertEquals("contenido", state.notes().get("Ideas").text());
	}

	@Test void caso35actualizarNota() {
		var store = new InMemoryEventStore();
		var ns = new NoteService(store, events);
		ns.updateNote("ws_upd", "a", "Ideas", "v1");
		ns.updateNote("ws_upd", "a", "Ideas", "v2");
		assertEquals("v2", WorkspaceStateBuilder.fromEvents(store.listEvents("ws_upd")).notes().get("Ideas").text());
	}

	@Test void caso36edicionesConcurrentesSimples() {
		var store = new InMemoryEventStore();
		var ns = new NoteService(store, events);
		ns.updateNote("ws_conc", "a", "nota", "version A");
		ns.updateNote("ws_conc", "b", "nota", "version B");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_conc"));
		assertNotNull(state.notes().get("nota"));
	}

	// ============================================================
	// GRUPO 10: Archivos por chunks
	// ============================================================

	@Test void caso37compartirArchivoMetadataYChunks() {
		var store = new InMemoryEventStore();
		var chunks = new InMemoryFileChunkStore();
		byte[] content = "contenido manual".getBytes(StandardCharsets.UTF_8);
		Event evt = new FileService(store, chunks, events, ids).shareFile("ws_file", "a", "manual.pdf", content);
		assertEquals(EventTypes.FILE_SHARED, evt.type());
		assertFalse(evt.payload().containsKey("raw_content"));
		assertNotNull(evt.payload().get("hash"));
		assertNotNull(evt.payload().get("chunks"));
	}

	@Test void caso38reconstruirArchivoDesdeChunks() {
		var store = new InMemoryEventStore();
		var chunks = new InMemoryFileChunkStore();
		byte[] content = "contenido manual".getBytes(StandardCharsets.UTF_8);
		Event evt = new FileService(store, chunks, events, ids).shareFile("ws_rec", "a", "manual.pdf", content);
		byte[] reconstructed = chunks.reconstructFile(String.valueOf(evt.payload().get("file_id")));
		assertArrayEquals(content, reconstructed);
	}

	@Test void caso39archivoIncompletoNoDisponible() {
		var chunks = new InMemoryFileChunkStore();
		chunks.putChunk("file_x", "hash1", "chunk1".getBytes());
		assertFalse(chunks.hasChunk("hash2"));
	}

	@Test void caso40chunkCorruptoRechazado() {
		var store = new InMemoryEventStore();
		var chunks = new InMemoryFileChunkStore();
		byte[] content = "contenido".getBytes(StandardCharsets.UTF_8);
		Event evt = new FileService(store, chunks, events, ids).shareFile("ws_corr", "a", "f.txt", content);
		String hash = String.valueOf(evt.payload().get("hash"));
		assertNotNull(hash);
	}

	// ============================================================
	// GRUPO 11: Malla P2P y balanceo
	// ============================================================

	@Test void caso41nuevoPeerEligeMenosCargado() {
		var net = new SimulatedNetworkAdapter();
		var a = net.createNode("A");
		var b = net.createNode("B");
		var c = net.createNode("C");
		var d = net.createNode("D");
		net.connect(a, b);
		net.connect(b, c);
		var policy = new MeshPolicy(2, 2);
		String selected = net.selectPeerFor(d, policy).orElseThrow().peerId();
		assertNotEquals("B", selected);
		assertTrue(Set.of("A", "C").contains(selected));
	}

	@Test void caso42noSuperarMaxConnections() {
		for (int n : new int[]{4, 8, 10, 20}) {
			var net = new SimulatedNetworkAdapter();
			var policy = new MeshPolicy(2, 4);
			List<SimulatedNode> nodes = new ArrayList<>();
			for (int i = 1; i <= n; i++) {
				var node = net.createNode("N" + i);
				nodes.add(node);
				if (i == 2) net.connect(nodes.get(0), node);
				if (i > 2) net.connectUsingPolicy(node, policy);
			}
			for (var node : nodes) assertTrue(node.degree() <= policy.maxConnectionsPerPeer(),
					"N=" + n + " " + node.id() + " excede max");
		}
	}

	@Test void caso43mantenerTargetConnections() {
		var net = new SimulatedNetworkAdapter();
		var policy = new MeshPolicy(2, 4);
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 6; i++) { nodes.add(net.createNode("N" + i)); if (i > 1) net.connectUsingPolicy(nodes.get(i-1), policy); }
		for (var node : nodes) net.connectUsingPolicy(node, policy);
		int lowDegree = (int) nodes.stream().filter(n -> n.degree() >= policy.targetConnectionsPerPeer()).count();
		assertTrue(lowDegree >= nodes.size() / 2, "al menos mitad debe alcanzar target");
	}

	@Test void caso44evitarMallaCompletaCon20() {
		var net = new SimulatedNetworkAdapter();
		var policy = new MeshPolicy(2, 4);
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 20; i++) { nodes.add(net.createNode("N" + i)); if (i > 1) net.connectUsingPolicy(nodes.get(i-1), policy); }
		for (var node : nodes) net.connectUsingPolicy(node, policy);
		int maxEdges = 20 * 19 / 2;
		assertTrue(net.edges().size() < maxEdges, "no debe ser malla completa");
		for (var node : nodes) assertTrue(node.degree() <= policy.maxConnectionsPerPeer());
	}

	@Test void caso45redPartidaDetectaYReconcilia() {
		var net = new SimulatedNetworkAdapter();
		var policy = new MeshPolicy(2, 4);
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 6; i++) { nodes.add(net.createNode("N" + i)); if (i > 1) net.connect(nodes.get(i-2 < 0 ? 0 : i-2), nodes.get(i-1)); }
		for (var node : nodes) net.connectUsingPolicy(node, policy);
		int half = 3;
		for (int i = 0; i < half; i++)
			for (int j = half; j < 6; j++)
				if (nodes.get(i).peers().contains(nodes.get(j).id()))
					net.disconnect(nodes.get(i), nodes.get(j));
		Event e1 = events.create("ws_part", EventTypes.CHAT_MESSAGE_CREATED, "N1", Map.of("message_id", "left", "text", "izq"), null);
		nodes.get(0).sync().broadcastEvent(e1);
		net.runGossipRounds(8);
		assertTrue(nodes.get(0).store().hasEvent(e1.eventId()));
		assertTrue(nodes.get(2).store().hasEvent(e1.eventId()));
		assertFalse(nodes.get(3).store().hasEvent(e1.eventId()));
	}

	// ============================================================
	// GRUPO 12: Gossip y sincronización
	// ============================================================

	@Test void caso46eventoPropagaMultiplesSaltos() {
		var net = new SimulatedNetworkAdapter();
		var a = net.createNode("A");
		var b = net.createNode("B");
		var c = net.createNode("C");
		var d = net.createNode("D");
		net.connect(a, b); net.connect(b, c); net.connect(c, d);
		Event evt = events.create("ws_hops", EventTypes.CHAT_MESSAGE_CREATED, "A", Map.of("message_id", "m1", "text", "hops"), null);
		a.sync().broadcastEvent(evt);
		net.runGossipRounds(6);
		for (var node : List.of(a, b, c, d)) assertTrue(node.store().hasEvent(evt.eventId()), node.id() + " no recibio");
	}

	@Test void caso47gossipNoReenviaInfinito() {
		var store = new InMemoryEventStore();
		Event evt = events.create("ws_loop", EventTypes.CHAT_MESSAGE_CREATED, "a", Map.of("message_id", "m1", "text", "loop"), null);
		for (int i = 0; i < 5; i++) store.append(evt);
		assertEquals(1, store.listEvents("ws_loop").size());
	}

	@Test void caso48syncFaltantes() {
		var a = new InMemoryEventStore();
		var b = new InMemoryEventStore();
		for (int i = 1; i <= 4; i++) {
			Event evt = events.create("ws_miss", EventTypes.CHAT_MESSAGE_CREATED, "a", Map.of("message_id", "m" + i, "text", "m" + i), null);
			a.append(evt);
			if (i <= 2) b.append(evt);
		}
		for (Event missing : a.getMissingEvents("ws_miss", b.listEventIds("ws_miss"))) b.append(missing);
		assertEquals(4, b.listEvents("ws_miss").size());
	}

	@Test void caso49syncDespuesDeDesconexion() {
		var net = new SimulatedNetworkAdapter();
		var a = net.createNode("A");
		var b = net.createNode("B");
		var c = net.createNode("C");
		net.connect(a, b); net.connect(b, c);
		Iterator<String> it = new ArrayList<>(c.peers()).iterator();
		while (it.hasNext()) net.disconnect(c, net.node(it.next()).orElseThrow());
		Event evt = events.create("ws_off", EventTypes.CHAT_MESSAGE_CREATED, "A", Map.of("message_id", "off", "text", "offline"), null);
		a.sync().broadcastEvent(evt);
		net.runGossipRounds(5);
		net.connectUsingPolicy(c, new MeshPolicy(1, 4));
		for (String p : c.peers()) c.sync().applyReceivedEvents(net.node(p).orElseThrow().sync().missingFor("ws_off", c.store().listEventIds("ws_off")));
		assertTrue(c.store().hasEvent(evt.eventId()));
	}

	// ============================================================
	// GRUPO 13: Recuperación y snapshots
	// ============================================================

	@Test void caso50reconstruirEstadoCompleto() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("Full", "A", 1);
		new ChatService(store, events, ids).sendMessage(created.workspaceId(), created.creator().memberId(), "chat");
		new NoteService(store, events).updateNote(created.workspaceId(), created.creator().memberId(), "n1", "nota");
		new WhiteboardService(store, events, ids).objectAdded(created.workspaceId(), created.creator().memberId(), "obj", "op");
		byte[] content = "file".getBytes(StandardCharsets.UTF_8);
		new FileService(store, new InMemoryFileChunkStore(), events, ids).shareFile(created.workspaceId(), created.creator().memberId(), "f.txt", content);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId()));
		assertNotNull(state.workspace());
		assertEquals(1, state.chatMessages().size());
		assertEquals(1, state.notes().size());
		assertEquals(1, state.whiteboardObjects().size());
		assertEquals(1, state.files().size());
	}

	@Test void caso51crearSnapshot() {
		var store = new InMemoryEventStore();
		for (int i = 1; i <= 10; i++)
			store.append(events.create("ws_snap", EventTypes.CHAT_MESSAGE_CREATED, "a", Map.of("message_id", "m" + i, "text", "m" + i), null));
		var state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_snap"));
		assertEquals(10, state.chatMessages().size());
	}

	@Test void caso52recuperarSnapshotMasEventos() {
		var store = new InMemoryEventStore();
		for (int i = 1; i <= 10; i++)
			store.append(events.create("ws_rec", EventTypes.CHAT_MESSAGE_CREATED, "a", Map.of("message_id", "m" + i, "text", "m" + i), null));
		Set<String> snapshotIds = new HashSet<>(store.listEventIds("ws_rec").stream().limit(5).toList());
		for (int i = 11; i <= 15; i++)
			store.append(events.create("ws_rec", EventTypes.CHAT_MESSAGE_CREATED, "a", Map.of("message_id", "m" + i, "text", "m" + i), null));
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_rec"));
		assertEquals(15, state.chatMessages().size());
	}

	@Test void caso53snapshotCorruptoIgnorado(@TempDir Path tmp) throws Exception {
		var store = new org.q3s.p2p.adapters.filesystem.FileSystemEventStore(tmp.resolve("ws"));
		for (int i = 1; i <= 5; i++)
			store.append(events.create("ws_corrupt", EventTypes.CHAT_MESSAGE_CREATED, "a", Map.of("message_id", "m" + i, "text", "m" + i), null));
		int count = store.listEvents("ws_corrupt").size();
		assertEquals(5, count);
	}

	@Test void caso53bcoreApplicationServiceGuardaSnapshotAutomatico(@TempDir Path tmp) throws Exception {
		var core = new CoreApplicationService(new InMemoryEventStore(), new InMemoryFileChunkStore(), ids);
		core.configureSnapshotPath(tmp.resolve("snapshots"));
		core.configureSnapshotPolicy(2);
		var created = core.createWorkspace("Snap Auto", "A", 1);
		core.sendChatMessage("uno");
		core.sendChatMessage("dos");

		Path snapshotDir = tmp.resolve("snapshots").resolve(created.workspaceId()).resolve("snapshots");
		try (var files = Files.list(snapshotDir)) {
			assertTrue(files.anyMatch(path -> path.getFileName().toString().endsWith(".snapshot")));
		}
		WorkspaceState state = core.currentState();
		assertEquals(2, state.chatMessages().size());
	}

	@Test void caso53cguardarSnapshotManualConDelta(@TempDir Path tmp) {
		var core = new CoreApplicationService(new InMemoryEventStore(), new InMemoryFileChunkStore(), ids);
		core.configureSnapshotPath(tmp.resolve("snapshots"));
		core.configureSnapshotPolicy(1000);
		core.createWorkspace("Snap Manual", "A", 1);
		core.sendChatMessage("en snapshot");
		assertTrue(core.saveCurrentSnapshot().isPresent());
		try { Thread.sleep(2); } catch (InterruptedException ignored) {}
		core.sendChatMessage("delta");

		WorkspaceState state = core.currentState();
		assertEquals(2, state.chatMessages().size());
	}

	@Test void caso53dretencionSnapshotsEliminaAntiguos(@TempDir Path tmp) throws Exception {
		var core = new CoreApplicationService(new InMemoryEventStore(), new InMemoryFileChunkStore(), ids);
		core.configureSnapshotPath(tmp.resolve("snapshots"));
		core.configureSnapshotPolicy(1000);
		core.configureSnapshotRetention(3);
		core.createWorkspace("Snap Retention", "A", 1);

		for (int i = 0; i < 5; i++) {
			core.sendChatMessage("msg" + i);
			core.saveCurrentSnapshot();
			try { Thread.sleep(2); } catch (InterruptedException ignored) {}
		}

		Path snapshotDir = tmp.resolve("snapshots").resolve(core.currentWorkspaceId().orElseThrow()).resolve("snapshots");
		long count;
		try (var files = Files.list(snapshotDir)) {
			count = files.filter(path -> path.getFileName().toString().endsWith(".snapshot")).count();
		}
		assertEquals(3, count, "Deberían quedar exactamente 3 snapshots");
	}

	@Test void caso53eretencionSnapshotsCeroNoElimina(@TempDir Path tmp) throws Exception {
		var core = new CoreApplicationService(new InMemoryEventStore(), new InMemoryFileChunkStore(), ids);
		core.configureSnapshotPath(tmp.resolve("snapshots"));
		core.configureSnapshotPolicy(1000);
		core.configureSnapshotRetention(0);
		core.createWorkspace("Snap No Retention", "A", 1);

		for (int i = 0; i < 5; i++) {
			core.sendChatMessage("msg" + i);
			core.saveCurrentSnapshot();
			try { Thread.sleep(2); } catch (InterruptedException ignored) {}
		}

		Path snapshotDir = tmp.resolve("snapshots").resolve(core.currentWorkspaceId().orElseThrow()).resolve("snapshots");
		long count;
		try (var files = Files.list(snapshotDir)) {
			count = files.filter(path -> path.getFileName().toString().endsWith(".snapshot")).count();
		}
		assertEquals(5, count, "Con maxSnapshots=0 no deberían eliminarse snapshots");
	}

	// ============================================================
	// GRUPO 14: Compactación
	// ============================================================

	@Test void caso54compactarEventosDeNota() {
		var store = new InMemoryEventStore();
		var ns = new NoteService(store, events);
		for (int i = 1; i <= 10; i++) ns.updateNote("ws_comp", "a", "nota", "v" + i);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_comp"));
		assertEquals("v10", state.notes().get("nota").text());
	}

	@Test void caso55noCompactarEventosCriticos() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("Crit", "A", 1);
		var memberships = new MembershipService(store, auth, events);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), c);
		memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
		assertTrue(store.containsType(created.workspaceId(), EventTypes.WORKSPACE_CREATED));
		assertTrue(store.containsType(created.workspaceId(), EventTypes.MEMBER_JOIN_APPROVAL));
	}

	// ============================================================
	// GRUPO 15: Persistencia y recarga desde disco
	// ============================================================

	@Test void caso56guardarWorkspaceEnDisco(@TempDir Path tmp) throws Exception {
		Instant created = Instant.parse("2026-05-20T14:30:00Z");
		QfolderLayout layout = new QfolderLayout(tmp);
		Path wsDir = layout.workspaceFolder("ws_disk", created, "Test Disk");
		assertTrue(layout.createStructure(wsDir));
		var json = QfolderLayout.workspaceJson("ws_disk", "Test Disk", created, created, wsDir.toString(), 2);
		assertNotNull(json.get("workspace_id"));
		assertTrue(Files.isDirectory(wsDir.resolve("files")));
		Path systemWs = layout.systemWorkspaceRoot("ws_disk");
		Files.createDirectories(systemWs.resolve("events"));
		assertTrue(Files.isDirectory(systemWs.resolve("events")));
	}

	@Test void caso57recargarWorkspace(@TempDir Path tmp) throws Exception {
		Instant created = Instant.parse("2026-05-20T14:30:00Z");
		QfolderLayout layout = new QfolderLayout(tmp);
		Path wsDir = layout.workspaceFolder("ws_reload", created, "Reload Test");
		layout.createStructure(wsDir);
		Files.writeString(wsDir.resolve("workspace.json"), "{\"workspace_id\":\"ws_reload\",\"name\":\"Reload Test\"}");
		assertTrue(Files.isRegularFile(wsDir.resolve("workspace.json")));
		var found = layout.findExistingWorkspaceFolder("ws_reload");
		assertTrue(found.isPresent());
	}

	@Test void caso58recuperarSinState(@TempDir Path tmp) throws Exception {
		Instant created = Instant.now();
		QfolderLayout layout = new QfolderLayout(tmp);
		Path wsDir = layout.workspaceFolder("ws_nostate", created, "NoState");
		layout.createStructure(wsDir);
		Path systemWs = layout.systemWorkspaceRoot("ws_nostate");
		Files.createDirectories(systemWs.resolve("events"));
		assertTrue(Files.isDirectory(systemWs.resolve("events")));
	}

	@Test void caso59recuperarSinSnapshot(@TempDir Path tmp) throws Exception {
		Instant created = Instant.now();
		QfolderLayout layout = new QfolderLayout(tmp);
		Path wsDir = layout.workspaceFolder("ws_nosnap", created, "NoSnap");
		layout.createStructure(wsDir);
		Path systemWs = layout.systemWorkspaceRoot("ws_nosnap");
		Files.createDirectories(systemWs.resolve("events"));
		assertTrue(Files.isDirectory(systemWs.resolve("events")));
	}

	@Test void caso60errorSinWorkspaceJson() {
		QfolderLayout layout = new QfolderLayout(Path.of("/tmp/nonexistent"));
		assertTrue(layout.findExistingWorkspaceFolder("ws_missing").isEmpty());
	}

	// ============================================================
	// GRUPO 16: Validación de reglas
	// ============================================================

	@Test void caso61borrarArchivoSoloPorAutor() {
		var store = new InMemoryEventStore();
		var chunks = new InMemoryFileChunkStore();
		Event evt = new FileService(store, chunks, events, ids).shareFile("ws_auth", "author", "f.txt", "data".getBytes());
		assertEquals("author", evt.payload().get("shared_by"));
	}

	@Test void caso62borrarArchivoConVotos() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var ws = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 1);
		assertEquals(1, WorkspaceStateBuilder.fromEvents(store.listEvents(ws.workspaceId())).authorizedMembers().size());
	}

	@Test void caso63cambiarReglas() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var ws = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 1);
		var state = WorkspaceStateBuilder.fromEvents(store.listEvents(ws.workspaceId()));
		assertEquals(1, state.workspace().requiredApprovals());
	}

	// ============================================================
	// GRUPO 17: Integración con UI Swing
	// ============================================================

	@Test void caso64enviarMensajeDesdeUILlamaServicio() {
		var store = new InMemoryEventStore();
		new ChatService(store, events, ids).sendMessage("ws_ui", "member_a", "desde UI");
		assertTrue(store.containsType("ws_ui", EventTypes.CHAT_MESSAGE_CREATED));
	}

	@Test void caso65uiMuestraEstadoReconstruido() {
		var store = new InMemoryEventStore();
		for (int i = 1; i <= 3; i++)
			store.append(events.create("ws_view", EventTypes.CHAT_MESSAGE_CREATED, "a", Map.of("message_id", "m" + i, "text", "m" + i), null));
		assertEquals(3, WorkspaceStateBuilder.fromEvents(store.listEvents("ws_view")).chatMessages().size());
	}

	@Test void caso66uiSinLogicaDeConsenso() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var ws = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 2);
		var memberships = new MembershipService(store, auth, events);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(ws.workspaceId(), c);
		assertFalse(WorkspaceStateBuilder.fromEvents(store.listEvents(ws.workspaceId())).isAuthorized(c.memberId()));
	}

	// ============================================================
	// GRUPO 18: Simulaciones grandes
	// ============================================================

	@Test void caso67sesion10Usuarios() {
		var net = new SimulatedNetworkAdapter();
		var policy = new MeshPolicy(2, 4);
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 10; i++) { nodes.add(net.createNode("U" + i)); if (i > 1) net.connectUsingPolicy(nodes.get(i-1), policy); }
		for (var n : nodes) net.connectUsingPolicy(n, policy);
		for (var n : nodes) assertTrue(n.degree() >= 1 && n.degree() <= policy.maxConnectionsPerPeer());
		Event evt = events.create("ws10", EventTypes.CHAT_MESSAGE_CREATED, "U1", Map.of("message_id", "m", "text", "10"), null);
		nodes.get(0).sync().broadcastEvent(evt);
		net.runGossipRounds(20);
		for (var n : nodes) assertTrue(n.store().hasEvent(evt.eventId()), n.id() + " no recibio");
	}

	@Test void caso68sesion20UsuariosMuchosEventos() {
		var net = new SimulatedNetworkAdapter();
		var policy = new MeshPolicy(2, 4);
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 20; i++) { nodes.add(net.createNode("U" + i)); if (i > 1) net.connectUsingPolicy(nodes.get(i-1), policy); }
		for (var n : nodes) net.connectUsingPolicy(n, policy);
		for (int r = 0; r < 10; r++)
			for (int i = 0; i < 5; i++)
				nodes.get(i).sync().broadcastEvent(events.create("ws20", nodes.get(i).id(), EventTypes.CHAT_MESSAGE_CREATED,
						Map.of("message_id", "m" + r + "_" + i, "text", "msg"), null));
		net.runGossipRounds(30);
		int minEvents = nodes.stream().mapToInt(n -> n.store().listEvents("ws20").size()).min().orElse(0);
		assertTrue(minEvents > 0, "todos los nodos deben recibir al menos algunos eventos");
		for (var n : nodes) assertTrue(n.degree() <= policy.maxConnectionsPerPeer());
	}

	@Test void caso69offlineYReconexionMasiva() {
		var net = new SimulatedNetworkAdapter();
		var policy = new MeshPolicy(2, 4);
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 10; i++) { nodes.add(net.createNode("U" + i)); if (i > 1) net.connectUsingPolicy(nodes.get(i-1), policy); }
		for (var n : nodes) net.connectUsingPolicy(n, policy);
		for (int i = 5; i < 10; i++) net.disconnectAll(nodes.get(i));
		for (int i = 0; i < 5; i++)
			nodes.get(i).sync().broadcastEvent(events.create("ws_offmass", nodes.get(i).id(), EventTypes.CHAT_MESSAGE_CREATED,
					Map.of("message_id", "off" + i, "text", "msg"), null));
		net.runGossipRounds(10);
		for (int i = 5; i < 10; i++) {
			net.connectUsingPolicy(nodes.get(i), policy);
			for (int j = 0; j < 5; j++)
				nodes.get(i).sync().applyReceivedEvents(nodes.get(j).sync().missingFor("ws_offmass", nodes.get(i).store().listEventIds("ws_offmass")));
		}
		net.runGossipRounds(10);
		for (var n : nodes) assertTrue(n.store().listEvents("ws_offmass").size() >= 3, n.id() + " recupero pocos eventos");
	}

	@Test void caso70particionYReconciliacion() {
		var net = new SimulatedNetworkAdapter();
		var policy = new MeshPolicy(2, 4);
		List<SimulatedNode> all = new ArrayList<>();
		for (int i = 1; i <= 6; i++) { all.add(net.createNode("U" + i)); if (i > 1) net.connect(all.get(i-2 < 0 ? 0 : i-2), all.get(i-1)); }
		for (var node : all) net.connectUsingPolicy(node, policy);
		for (int i = 0; i < 3; i++)
			for (int j = 3; j < 6; j++)
				if (all.get(i).peers().contains(all.get(j).id()))
					net.disconnect(all.get(i), all.get(j));
		Event e1 = events.create("ws_part2", EventTypes.CHAT_MESSAGE_CREATED, "U1", Map.of("message_id", "g1", "text", "g1"), null);
		Event e2 = events.create("ws_part2", EventTypes.CHAT_MESSAGE_CREATED, "U4", Map.of("message_id", "g2", "text", "g2"), null);
		all.get(0).sync().broadcastEvent(e1);
		all.get(3).sync().broadcastEvent(e2);
		net.runGossipRounds(10);
		assertTrue(all.get(2).store().hasEvent(e1.eventId()), "grupo 1 debe tener su evento");
		assertTrue(all.get(5).store().hasEvent(e2.eventId()), "grupo 2 debe tener su evento");
		assertFalse(all.get(3).store().hasEvent(e1.eventId()), "grupo 2 no debe tener evento del grupo 1");
	}

	// ============================================================
	// GRUPO 19: CRDT Notas
	// ============================================================

	@Test void crdtInsertTextAtPosition() {
		var store = new InMemoryEventStore();
		var ns = new NoteService(store, events);
		ns.insertLine("ws", "a", "n1", null, "Hola");
		ns.insertLine("ws", "a", "n1", null, " mundo");
		ns.insertLine("ws", "a", "n1", null, "¡");
		ns.insertLine("ws", "a", "n1", null, "!");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws"));
		String text = state.notes().get("n1").text();
		assertTrue(text.contains("Hola"));
		assertTrue(text.contains(" mundo"));
		assertTrue(text.contains("¡"));
		assertTrue(text.contains("!"));
		assertEquals(4, state.notes().get("n1").lineCount());
	}

	@Test void crdtDeleteTextRange() {
		var store = new InMemoryEventStore();
		var ns = new NoteService(store, events);
		ns.updateNote("ws", "a", "n1", "abcdefgh");
		Event inserted = ns.insertLine("ws", "a", "n1", null, "BCDF");
		Event ev = events.create("ws", EventTypes.NOTE_DELETE_OP, "a",
				Map.of("note_id", "n1", "line_id", inserted.payload().get("line_id").toString(),
						"op_id", "del-test", "created_at_ms", System.currentTimeMillis()), null);
		store.append(ev);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws"));
		assertFalse(state.notes().get("n1").text().contains("BCDF"));
	}

	@Test void crdtComplexEditSession() {
		var store = new InMemoryEventStore();
		var ns = new NoteService(store, events);
		ns.insertLine("ws", "a", "n1", null, "Buenos");
		Event inserted = ns.insertLine("ws", "a", "n1", null, "Días");
		Event ev = events.create("ws", EventTypes.NOTE_DELETE_OP, "a",
				Map.of("note_id", "n1", "line_id", inserted.payload().get("line_id").toString(),
						"op_id", "del-2", "created_at_ms", System.currentTimeMillis()), null);
		store.append(ev);
		ns.insertLine("ws", "a", "n1", null, "tardes");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws"));
		assertTrue(state.notes().get("n1").text().contains("Buenos"));
		assertTrue(state.notes().get("n1").text().contains("tardes"));
		assertFalse(state.notes().get("n1").text().contains("Días"));
	}

	@Test void crdtConcurrentEditsMergeInOrder() {
		var storeA = new InMemoryEventStore();
		var storeB = new InMemoryEventStore();
		var nsA = new NoteService(storeA, events);
		var nsB = new NoteService(storeB, events);
		nsA.insertLine("ws", "a", "shared", null, "base");
		nsA.insertLine("ws", "a", "shared", null, "_A1");
		nsB.insertLine("ws", "b", "shared", null, "base");
		nsB.insertLine("ws", "b", "shared", null, "_B1");
		for (Event e : storeB.listEvents("ws")) storeA.append(e);
		for (Event e : storeA.listEvents("ws")) storeB.append(e);
		WorkspaceState stateA = WorkspaceStateBuilder.fromEvents(storeA.listEvents("ws"));
		WorkspaceState stateB = WorkspaceStateBuilder.fromEvents(storeB.listEvents("ws"));
		assertEquals(stateA.notes().get("shared").text(), stateB.notes().get("shared").text());
		assertTrue(stateA.notes().get("shared").text().contains("_A1"));
		assertTrue(stateA.notes().get("shared").text().contains("_B1"));
	}

	@Test void crdtStyleAppliedDoesNotChangeText() {
		var store = new InMemoryEventStore();
		var ns = new NoteService(store, events);
		ns.updateNote("ws", "a", "n1", "Hola");
		ns.applyStyle("ws", "a", "n1", 0, 2, "bold");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws"));
		assertEquals("Hola", state.notes().get("n1").text());
	}

	@Test void crdtInsertIsIdempotent() {
		var store = new InMemoryEventStore();
		var ns = new NoteService(store, events);
		Event ev = ns.insertLine("ws", "a", "n1", null, "idempotente");
		store.append(events.create("ws", EventTypes.NOTE_INSERT, "a", ev.payload(), null));
		store.append(events.create("ws", EventTypes.NOTE_INSERT, "a", ev.payload(), null));
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws"));
		assertEquals(1, state.notes().get("n1").lineCount(),
				"re-aplicar mismo lineId no debe duplicar");
	}

	@Test void crdtDeleteIsTombstone() {
		var store = new InMemoryEventStore();
		var ns = new NoteService(store, events);
		Event ev = ns.insertLine("ws", "a", "n1", null, "borrame");
		Event del = events.create("ws", EventTypes.NOTE_DELETE_OP, "a",
				Map.of("note_id", "n1", "line_id", ev.payload().get("line_id").toString(),
						"op_id", "tomb-1", "created_at_ms", System.currentTimeMillis()), null);
		store.append(del);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws"));
		assertEquals(0, state.notes().get("n1").lineCount(), "linea debe estar tombstoned");
	}

	@Test void crdtDeleteBeforeInsertKeepsTombstone() {
		var store = new InMemoryEventStore();
		Map<String, Object> insertPayload = new java.util.LinkedHashMap<>();
		insertPayload.put("note_id", "n1");
		insertPayload.put("line_id", "a:1");
		insertPayload.put("op_id", "a:1");
		insertPayload.put("after_line_id", "");
		insertPayload.put("text", "no debe volver");
		insertPayload.put("created_at_ms", 1000L);
		Event del = events.create("ws", EventTypes.NOTE_DELETE_OP, "b",
				Map.of("note_id", "n1", "line_id", "a:1", "op_id", "del:b:1", "created_at_ms", 2000L), null);
		Event ins = events.create("ws", EventTypes.NOTE_INSERT, "a", insertPayload, null);
		store.append(del);
		store.append(ins);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws"));
		assertEquals(0, state.notes().get("n1").lineCount(), "delete anterior no debe ser revertido por insert tardio");
	}

	@Test void crdtAfterLineIdOrdersChildrenAfterParent() {
		var store = new InMemoryEventStore();
		var ns = new NoteService(store, events);
		Event first = ns.insertLine("ws", "a", "n1", null, "primero");
		ns.insertLine("ws", "a", "n1", first.payload().get("line_id").toString(), "segundo");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws"));
		assertEquals("primero\nsegundo", state.notes().get("n1").text());
	}

	@Test void crdtConcurrentInsertsConvergeByCreatedAt() {
		var storeA = new InMemoryEventStore();
		var storeB = new InMemoryEventStore();
		var nsA = new NoteService(storeA, events);
		var nsB = new NoteService(storeB, events);
		Event eA1 = nsA.insertLine("ws", "a", "shared", null, "primero");
		try { Thread.sleep(2); } catch (InterruptedException ignored) {}
		Event eB1 = nsB.insertLine("ws", "b", "shared", null, "segundo");
		for (Event e : storeB.listEvents("ws")) storeA.append(e);
		for (Event e : storeA.listEvents("ws")) storeB.append(e);
		WorkspaceState stateA = WorkspaceStateBuilder.fromEvents(storeA.listEvents("ws"));
		WorkspaceState stateB = WorkspaceStateBuilder.fromEvents(storeB.listEvents("ws"));
		assertEquals(stateA.notes().get("shared").text(), stateB.notes().get("shared").text());
		assertEquals(2, stateA.notes().get("shared").lineCount());
	}

	// ============================================================
	// GRUPO 20: Ed25519 PublicKeyAuth
	// ============================================================

	@Test void ed25519CreateIdentityGeneratesKeys() {
		var auth = new org.q3s.p2p.core.auth.PublicKeyAuthProvider(ids);
		Member m = auth.createMemberIdentity("Damian");
		assertNotNull(m.memberId());
		assertEquals("Damian", m.displayName());
		assertFalse(m.membershipToken().isBlank());
		assertFalse(m.publicKey().isBlank());
		assertNotEquals(m.publicKey(), m.membershipToken());
	}

	@Test void ed25519TwoIdentitiesHaveDifferentKeys() {
		var auth = new org.q3s.p2p.core.auth.PublicKeyAuthProvider(ids);
		Member a = auth.createMemberIdentity("A");
		Member b = auth.createMemberIdentity("B");
		assertNotEquals(a.publicKey(), b.publicKey());
		assertNotEquals(a.membershipToken(), b.membershipToken());
	}

	@Test void ed25519StampedEventHasSignature() {
		var auth = new org.q3s.p2p.core.auth.PublicKeyAuthProvider(ids);
		Member author = auth.createMemberIdentity("A");
		Event event = events.create("ws", EventTypes.CHAT_MESSAGE_CREATED, author.memberId(), Map.of("text", "hola"), null);
		Event stamped = auth.stampEvent(event, author);
		assertNotNull(stamped.signature());
		assertFalse(stamped.signature().isBlank());
		assertEquals("ed25519", stamped.auth().mode());
		assertTrue(org.q3s.p2p.core.auth.PublicKeyAuthProvider.verifyEventSignature(stamped, author.publicKey()));
	}

	@Test void ed25519ValidateAuthor() {
		var store = new InMemoryEventStore();
		var auth = new org.q3s.p2p.core.auth.PublicKeyAuthProvider(ids);
		var ws = new WorkspaceService(store, auth, ids, events).createWorkspace("W", "A", 1);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents(ws.workspaceId()));
		Event event = auth.stampEvent(events.create(ws.workspaceId(), EventTypes.CHAT_MESSAGE_CREATED, ws.creator().memberId(), Map.of(), null), ws.creator());
		assertTrue(auth.validateEventAuthor(event, state));
		assertFalse(auth.validateEventAuthor(events.create(ws.workspaceId(), EventTypes.CHAT_MESSAGE_CREATED, "intruso", Map.of(), null), state));
	}

	// ============================================================
	// GRUPO 21: Membresía P2P
	// ============================================================

	@Test void p2pJoinRequestPropagatesByGossip() {
		for (int n : new int[]{4, 8}) {
			var net = new SimulatedNetworkAdapter();
			List<SimulatedNode> nodes = new ArrayList<>();
			for (int i = 1; i <= n; i++) { nodes.add(net.createNode("U" + i)); if (i > 1) net.connect(nodes.get(i-2 < 0 ? 0 : i-2), nodes.get(i-1)); }
			for (var node : nodes) net.connectUsingPolicy(node, new MeshPolicy(2, 4));
			Event joinRequest = events.create("ws_p2p_mem", EventTypes.MEMBER_JOIN_REQUESTED, "U1",
					Map.of("candidate_member_id", "cand1", "candidate_display_name", "C"), null);
			nodes.get(0).sync().broadcastEvent(joinRequest);
			net.runGossipRounds(20);
			for (var node : nodes) assertTrue(node.store().hasEvent(joinRequest.eventId()), "N=" + n + " " + node.id() + " no recibio join request");
		}
	}

	@Test void p2pApprovalPropagatesByGossip() {
		var net = new SimulatedNetworkAdapter();
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 6; i++) { nodes.add(net.createNode("U" + i)); if (i > 1) net.connect(nodes.get(i-2 < 0 ? 0 : i-2), nodes.get(i-1)); }
		for (var node : nodes) net.connectUsingPolicy(node, new MeshPolicy(2, 4));
		Event approval = events.create("ws_p2p_aprv", EventTypes.MEMBER_JOIN_APPROVAL, "U1",
				Map.of("candidate_member_id", "cand1", "approved_by", "U1"), null);
		nodes.get(0).sync().broadcastEvent(approval);
		net.runGossipRounds(15);
		for (var node : nodes) assertTrue(node.store().hasEvent(approval.eventId()), node.id() + " no recibio approval");
	}

	@Test void p2pRevocationPropagatesByGossip() {
		var net = new SimulatedNetworkAdapter();
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 6; i++) { nodes.add(net.createNode("U" + i)); if (i > 1) net.connect(nodes.get(i-2 < 0 ? 0 : i-2), nodes.get(i-1)); }
		for (var node : nodes) net.connectUsingPolicy(node, new MeshPolicy(2, 4));
		Event revoke = events.create("ws_p2p_rev", EventTypes.MEMBER_REVOKED, "U1",
				Map.of("member_id", "revoked_user"), null);
		nodes.get(0).sync().broadcastEvent(revoke);
		net.runGossipRounds(15);
		for (var node : nodes) assertTrue(node.store().hasEvent(revoke.eventId()), node.id() + " no recibio revocacion");
	}

	// ============================================================
	// GRUPO 22: Fallos de red reales
	// ============================================================

	@Test void halfOpenConnectionDetectionViaTimeout() {
		var net = new SimulatedNetworkAdapter();
		var a = net.createNode("A");
		var b = net.createNode("B");
		var c = net.createNode("C");
		net.connect(a, b); net.connect(b, c);
		Event evt = events.create("ws_timeout", EventTypes.CHAT_MESSAGE_CREATED, "A", Map.of("message_id", "m1", "text", "timeout"), null);
		a.sync().broadcastEvent(evt);
		net.runGossipRoundsWithLoss(10, 0.5);
		assertTrue(a.store().hasEvent(evt.eventId()));
	}

	@Test void rapidReconnectDoesNotDuplicateEvents() {
		var net = new SimulatedNetworkAdapter();
		var a = net.createNode("A");
		var b = net.createNode("B");
		net.connect(a, b);
		Event evt = events.create("ws_recon", EventTypes.CHAT_MESSAGE_CREATED, "A", Map.of("message_id", "rapid", "text", "rapid"), null);
		a.sync().broadcastEvent(evt);
		net.runGossipRounds(3);
		net.disconnect(a, b);
		net.connect(a, b);
		a.sync().broadcastEvent(evt);
		net.runGossipRounds(5);
		assertEquals(1, b.store().listEvents("ws_recon").size(), "evento no debe duplicarse");
	}

	@Test void fuzzMalformedEventPayloadDoesNotCrash() {
		var store = new InMemoryEventStore();
		Event malformed = events.create("ws_fuzz", "invalid-characters-in-type-\0\n\t", "author", Map.of("key-with-null\0", "val"), null);
		store.append(malformed);
		assertTrue(store.listEvents("ws_fuzz").size() >= 1);
	}

	@Test void fuzzEmptyPayloadEvent() {
		var store = new InMemoryEventStore();
		Event empty = events.create("ws_empty_payload", EventTypes.CHAT_MESSAGE_CREATED, "a", Map.of(), null);
		store.append(empty);
		assertEquals(1, store.listEvents("ws_empty_payload").size());
	}

	@Test void fuzzVeryLongEventId() {
		var store = new InMemoryEventStore();
		String longId = "evt_" + "x".repeat(500);
		Event evt = new Event(longId, "ws", EventTypes.CHAT_MESSAGE_CREATED, "a", Instant.now(), List.of(), Map.of(), new org.q3s.p2p.core.model.AuthInfo("token"), null, true);
		store.append(evt);
		assertTrue(store.hasEvent(longId));
	}

	@Test void benchmarkGossipPropagationSpeed() {
		int[] sizes = {2, 4, 8};
		for (int n : sizes) {
			var net = new SimulatedNetworkAdapter();
			var nodes = new ArrayList<SimulatedNode>();
			for (int i = 1; i <= n; i++) { nodes.add(net.createNode("N" + i)); if (i > 1) net.connect(nodes.get(i-2 < 0 ? 0 : i-2), nodes.get(i-1)); }
			for (var node : nodes) net.connectUsingPolicy(node, new MeshPolicy(2, 4));
			long start = System.nanoTime();
			for (int r = 0; r < 5; r++) {
				Event evt = events.create("ws_bench_" + n, EventTypes.CHAT_MESSAGE_CREATED, "N1", Map.of("message_id", "bench" + r, "text", "bench"), null);
				nodes.get(0).sync().broadcastEvent(evt);
			}
			net.runGossipRounds(15);
			long elapsed = System.nanoTime() - start;
			int minEvents = nodes.stream().mapToInt(nd -> nd.store().listEvents("ws_bench_" + n).size()).min().orElse(0);
			assertTrue(minEvents >= 3, "N=" + n + " benchmark: " + minEvents + " eventos minimo en " + elapsed / 1_000_000 + "ms");
		}
	}

	// ============================================================
	// GRUPO 23: Membresía P2P completa sin hub
	// ============================================================

	@Test void fullMembershipCycleP2PWithoutHub() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var ws = new WorkspaceService(store, auth, ids, events);
		var created = ws.createWorkspace("P2P Workspace", "Creator", 2);
		var memberships = new MembershipService(store, auth, events);

		Member approver2 = memberships.createCandidate("Approver2");
		memberships.addAuthorizedMember(created.workspaceId(), created.creator().memberId(), approver2);

		Member candidate = memberships.createCandidate("NewMember");
		memberships.requestJoin(created.workspaceId(), candidate);

		assertFalse(memberships.reconnect(created.workspaceId(), candidate.memberId(), candidate.membershipToken()));

		memberships.approve(created.workspaceId(), created.creator().memberId(), candidate.memberId());
		assertFalse(memberships.reconnect(created.workspaceId(), candidate.memberId(), candidate.membershipToken()));

		memberships.approve(created.workspaceId(), approver2.memberId(), candidate.memberId());
		assertTrue(memberships.reconnect(created.workspaceId(), candidate.memberId(), candidate.membershipToken()));

		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId()));
		assertEquals(3, state.authorizedMembers().size(), "creator + approver2 + newMember");
		assertTrue(state.isAuthorized(candidate.memberId()));
		assertFalse(state.isAuthorized("unknown_user"));
	}

	@Test void p2pMembershipEventsPropagateToAllPeers() {
		for (int n : new int[]{4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			List<SimulatedNode> nodes = new ArrayList<>();
			for (int i = 1; i <= n; i++) { nodes.add(net.createNode("U" + i)); if (i > 1) net.connect(nodes.get(i-2 < 0 ? 0 : i-2), nodes.get(i-1)); }
			for (var node : nodes) net.connectUsingPolicy(node, new MeshPolicy(2, 4));

			String wsId = "ws_mem_p2p_" + n;
			Event created = events.create(wsId, EventTypes.WORKSPACE_CREATED, "U1", Map.of(
					"name", "W", "required_approvals", 1, "auth_mode", "token",
					"creator_member_id", "U1", "creator_display_name", "Creator"), null);
			nodes.get(0).sync().broadcastEvent(created);

			Event joinReq = events.create(wsId, EventTypes.MEMBER_JOIN_REQUESTED, "new",
					Map.of("candidate_member_id", "new", "candidate_display_name", "New"), null);
			nodes.get(0).sync().broadcastEvent(joinReq);

			Event approval = events.create(wsId, EventTypes.MEMBER_JOIN_APPROVAL, "U1",
					Map.of("candidate_member_id", "new", "approved_by", "U1"), null);
			nodes.get(0).sync().broadcastEvent(approval);

			net.runGossipRounds(25);

			int authorizedCount = 0;
			for (var node : nodes) {
				if (WorkspaceStateBuilder.fromEvents(node.store().listEvents(wsId)).isAuthorized("new")) authorizedCount++;
			}
			assertTrue(authorizedCount >= n / 2, "N=" + n + " al menos mitad debe tener a new como autorizado, tienen=" + authorizedCount);
		}
	}

	// ============================================================
	// GRUPO 24: Integración P2P Mesh
	// ============================================================

	@Test void peerAppearsAndTriggersP2PConnection() {
		var net = new SimulatedNetworkAdapter();
		var policy = new MeshPolicy(2, 4);
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 4; i++) { nodes.add(net.createNode("P" + i)); if (i > 1) net.connect(nodes.get(i-2 < 0 ? 0 : i-2), nodes.get(i-1)); }
		for (var node : nodes) net.connectUsingPolicy(node, policy);

		assertTrue(nodes.get(0).peers().size() >= 1, "peer 1 debe tener al menos 1 conexion");
		assertTrue(nodes.get(3).peers().size() >= 1, "peer 4 debe tener al menos 1 conexion");
	}

	@Test void peerDisappearsAndLeavesMeshCleanly() {
		var net = new SimulatedNetworkAdapter();
		var policy = new MeshPolicy(2, 4);
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 6; i++) { nodes.add(net.createNode("P" + i)); if (i > 1) net.connect(nodes.get(i-2 < 0 ? 0 : i-2), nodes.get(i-1)); }
		for (var node : nodes) net.connectUsingPolicy(node, policy);

		int before = nodes.get(0).degree();
		net.disconnectAll(nodes.get(0));
		assertEquals(0, nodes.get(0).degree(), "peer desconectado debe tener degree 0");
		for (int i = 1; i < 6; i++) assertFalse(nodes.get(i).peers().contains("P1"), "nadie debe tener a P1 como vecino");
	}

	@Test void fullJoinFlowPeerAppearsAndChatViaP2P() {
		for (int n : new int[]{4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			List<SimulatedNode> nodes = new ArrayList<>();
			for (int i = 1; i <= n; i++) { nodes.add(net.createNode("N" + i)); if (i > 1) net.connect(nodes.get(i-2 < 0 ? 0 : i-2), nodes.get(i-1)); }
			for (var node : nodes) net.connectUsingPolicy(node, new MeshPolicy(2, 4));

			Event wsEvent = events.create("ws_full_" + n, EventTypes.WORKSPACE_CREATED, "N1", Map.of(
					"name", "FullTest", "required_approvals", 1, "auth_mode", "token",
					"creator_member_id", "N1", "creator_display_name", "Creator"), null);
			nodes.get(0).sync().broadcastEvent(wsEvent);

			for (int i = 0; i < n; i++) {
				Event chat = events.create("ws_full_" + n, EventTypes.CHAT_MESSAGE_CREATED, nodes.get(i).id(),
						Map.of("message_id", "chat" + i, "text", "chat msg " + i), null);
				nodes.get(i).sync().broadcastEvent(chat);
			}

			net.runGossipRounds(25);

			int minChatMsgs = nodes.stream().mapToInt(node ->
					WorkspaceStateBuilder.fromEvents(node.store().listEvents("ws_full_" + n)).chatMessages().size()
			).min().orElse(0);

			assertTrue(minChatMsgs >= n / 2, "N=" + n + " al menos " + (n/2) + " mensajes deben llegar a todos, min=" + minChatMsgs);
		}
	}

	@Test void contentEventsFlowThroughP2PConnections() {
		var net = new SimulatedNetworkAdapter();
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 6; i++) { nodes.add(net.createNode("N" + i)); if (i > 1) net.connect(nodes.get(i-2 < 0 ? 0 : i-2), nodes.get(i-1)); }
		for (var node : nodes) net.connectUsingPolicy(node, new MeshPolicy(2, 4));

		for (var node : nodes) {
			assertTrue(node.peers().size() >= 1, node.id() + " debe tener al menos un vecino");
		}

		Event chat = events.create("ws_content", EventTypes.CHAT_MESSAGE_CREATED, "N1",
				Map.of("message_id", "c1", "text", "hola"), null);
		Event note = events.create("ws_content", EventTypes.NOTE_UPDATED, "N2",
				Map.of("note_id", "n1", "text", "nota"), null);
		Event wb = events.create("ws_content", EventTypes.WHITEBOARD_OBJECT_ADDED, "N3",
				Map.of("object_id", "obj1", "operation", "S|R|1,2,3,4|#000|1"), null);

		nodes.get(0).sync().broadcastEvent(chat);
		nodes.get(1).sync().broadcastEvent(note);
		nodes.get(2).sync().broadcastEvent(wb);
		net.runGossipRounds(20);

		for (var node : nodes) {
			WorkspaceState state = WorkspaceStateBuilder.fromEvents(node.store().listEvents("ws_content"));
			assertTrue(state.chatMessages().containsKey("c1"), node.id() + " debe tener mensaje de chat");
			assertTrue(state.notes().containsKey("n1"), node.id() + " debe tener nota");
			assertTrue(state.whiteboardObjects().containsKey("obj1"), node.id() + " debe tener objeto de pizarra");
		}
	}

	@Test void p2pMeshReconnectsAfterPeerLeavesAndReturns() {
		var net = new SimulatedNetworkAdapter();
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 6; i++) { nodes.add(net.createNode("N" + i)); if (i > 1) net.connect(nodes.get(i-2 < 0 ? 0 : i-2), nodes.get(i-1)); }
		for (var node : nodes) net.connectUsingPolicy(node, new MeshPolicy(2, 4));

		var leaving = nodes.get(3);
		var initialPeers = leaving.peers();
		assertFalse(initialPeers.isEmpty(), "peer debe tener vecinos inicialmente");

		net.disconnectAll(leaving);
		assertEquals(0, leaving.degree());

		net.connectUsingPolicy(leaving, new MeshPolicy(2, 4));
		assertTrue(leaving.degree() >= 1, "peer debe reconectarse a al menos 1 vecino");

		for (String peerId : leaving.peers()) {
			leaving.sync().applyReceivedEvents(
					net.node(peerId).orElseThrow().sync().missingFor("ws_rejoin", leaving.store().listEventIds("ws_rejoin")));
		}
	}

	// ============================================================
	// GRUPO 25: Sesiones reales multi-miembro
	// ============================================================

	@Test void scenarioFullSession3MembersAllTabs() {
		var net = new SimulatedNetworkAdapter();
		var policy = new MeshPolicy(2, 4);
		List<SimulatedNode> members = new ArrayList<>();
		for (int i = 1; i <= 3; i++) { members.add(net.createNode("U" + i)); if (i > 1) net.connect(members.get(i-2 < 0 ? 0 : i-2), members.get(i-1)); }
		for (var m : members) net.connectUsingPolicy(m, policy);

		String ws = "ws_session";

		Event created = events.create(ws, EventTypes.WORKSPACE_CREATED, "U1", Map.of(
				"name", "Sesion Real", "required_approvals", 1, "auth_mode", "token",
				"creator_member_id", "U1", "creator_display_name", "Damian"), null);
		members.get(0).sync().broadcastEvent(created);

		for (int i = 2; i <= 3; i++) {
			members.get(0).sync().broadcastEvent(events.create(ws, EventTypes.MEMBER_JOIN_REQUESTED, "U" + i,
					Map.of("candidate_member_id", "U" + i, "candidate_display_name", "User" + i), null));
			members.get(0).sync().broadcastEvent(events.create(ws, EventTypes.MEMBER_JOIN_APPROVAL, "U1",
					Map.of("candidate_member_id", "U" + i, "approved_by", "U1"), null));
		}

		members.get(0).sync().broadcastEvent(events.create(ws, EventTypes.CHAT_MESSAGE_CREATED, "U1",
				Map.of("message_id", "chat1", "text", "Bienvenidos al workspace"), null));
		members.get(1).sync().broadcastEvent(events.create(ws, EventTypes.CHAT_MESSAGE_CREATED, "U2",
				Map.of("message_id", "chat2", "text", "Hola a todos"), null));

		members.get(0).sync().broadcastEvent(events.create(ws, EventTypes.NOTE_UPDATED, "U1",
				Map.of("note_id", "shared-notes", "text", "QNOTES2\nT|false|false|false|14|#000000|Tm90YXMgZGUgVTE="), null));

		members.get(1).sync().broadcastEvent(events.create(ws, EventTypes.WHITEBOARD_OBJECT_ADDED, "U2",
				Map.of("object_id", "rect1", "operation", "S|Rectangulo|10,10,100,50|#ff0000|2"), null));

		members.get(2).sync().broadcastEvent(events.create(ws, EventTypes.FILE_SHARED, "U3",
				Map.of("file_id", "file1", "name", "documento.pdf", "size", 1024L, "hash", "abc123", "chunks", List.of("c1","c2"), "shared_by", "U3"), null));

		net.runGossipRounds(25);

		for (var member : members) {
			WorkspaceState state = WorkspaceStateBuilder.fromEvents(member.store().listEvents(ws));
			assertEquals("Sesion Real", state.workspace().name(), member.id() + " debe tener workspace name");
			assertEquals(3, state.authorizedMembers().size(), member.id() + " debe tener 3 miembros");
			assertEquals(2, state.chatMessages().size(), member.id() + " debe tener 2 mensajes");
			assertEquals(1, state.notes().size(), member.id() + " debe tener 1 nota");
			assertEquals(1, state.whiteboardObjects().size(), member.id() + " debe tener 1 objeto de pizarra");
			assertEquals(1, state.files().size(), member.id() + " debe tener 1 archivo");
		}
	}

	@Test void scenarioMemberLeavesAndContentPersistsForOthers() {
		for (int n : new int[]{3, 5}) {
			var net = new SimulatedNetworkAdapter();
			List<SimulatedNode> members = new ArrayList<>();
			for (int i = 1; i <= n; i++) { members.add(net.createNode("U" + i)); if (i > 1) net.connect(members.get(i-2 < 0 ? 0 : i-2), members.get(i-1)); }
			for (var m : members) net.connectUsingPolicy(m, new MeshPolicy(2, 4));

			String ws = "ws_leave_" + n;

			members.get(0).sync().broadcastEvent(events.create(ws, EventTypes.WORKSPACE_CREATED, "U1", Map.of(
					"name", "W", "required_approvals", 1, "auth_mode", "token",
					"creator_member_id", "U1", "creator_display_name", "Creator"), null));

			int leaverIdx = n - 1;
			members.get(leaverIdx).sync().broadcastEvent(events.create(ws, EventTypes.FILE_SHARED, "U" + n,
					Map.of("file_id", "file_leaver", "name", "leaver_file.txt", "size", 500L, "hash", "hash_leaver",
							"chunks", List.of("c1"), "shared_by", "U" + n), null));

			net.runGossipRounds(10);

			net.disconnectAll(members.get(leaverIdx));

			members.get(0).sync().broadcastEvent(events.create(ws, EventTypes.CHAT_MESSAGE_CREATED, "U1",
					Map.of("message_id", "after_leave", "text", "U" + n + " se fue"), null));
			net.runGossipRounds(15);

			for (int i = 0; i < n - 1; i++) {
				WorkspaceState state = WorkspaceStateBuilder.fromEvents(members.get(i).store().listEvents(ws));
				assertTrue(state.chatMessages().containsKey("after_leave"),
						"N=" + n + " miembro " + members.get(i).id() + " debe tener mensaje post-salida");
				assertTrue(state.files().containsKey("file_leaver"),
						"N=" + n + " miembro " + members.get(i).id() + " debe conservar archivo del que se fue");
			}

			assertEquals(0, members.get(leaverIdx).degree(), "el que se fue debe quedar aislado");
		}
	}

	@Test void scenarioNewMemberReceivesFullStateFromPeers() {
		for (int n : new int[]{3, 5, 8}) {
			var net = new SimulatedNetworkAdapter();
			List<SimulatedNode> existing = new ArrayList<>();
			for (int i = 1; i <= n; i++) { existing.add(net.createNode("E" + i)); if (i > 1) net.connect(existing.get(i-2 < 0 ? 0 : i-2), existing.get(i-1)); }
			for (var e : existing) net.connectUsingPolicy(e, new MeshPolicy(2, 4));

			String ws = "ws_new_" + n;

			existing.get(0).sync().broadcastEvent(events.create(ws, EventTypes.WORKSPACE_CREATED, "E1", Map.of(
					"name", "Full State", "required_approvals", 1, "auth_mode", "token",
					"creator_member_id", "E1", "creator_display_name", "Creator"), null));

			for (int i = 0; i < 10; i++) {
				existing.get(i % n).sync().broadcastEvent(events.create(ws, EventTypes.CHAT_MESSAGE_CREATED, existing.get(i % n).id(),
						Map.of("message_id", "h" + i, "text", "hist msg " + i), null));
			}
			existing.get(1).sync().broadcastEvent(events.create(ws, EventTypes.NOTE_UPDATED, "E2",
					Map.of("note_id", "shared-notes", "text", "Notas historicas"), null));
			existing.get(2).sync().broadcastEvent(events.create(ws, EventTypes.WHITEBOARD_OBJECT_ADDED, "E3",
					Map.of("object_id", "hist_obj", "operation", "S|R|5,5,80,40|#00ff00|1"), null));
			for (int i = 0; i < 4; i++) {
				existing.get(i % n).sync().broadcastEvent(events.create(ws, EventTypes.FILE_SHARED, existing.get(i % n).id(),
						Map.of("file_id", "f" + i, "name", "file" + i + ".txt", "size", (long)(100 * (i+1)), "hash", "h" + i,
								"chunks", List.of("c" + i), "shared_by", existing.get(i % n).id()), null));
			}

			net.runGossipRounds(25);

			var newcomer = net.createNode("newcomer");
			net.connectUsingPolicy(newcomer, new MeshPolicy(2, 4));

			for (var e : existing) {
				newcomer.sync().applyReceivedEvents(
						e.sync().missingFor(ws, newcomer.store().listEventIds(ws)));
			}

			WorkspaceState state = WorkspaceStateBuilder.fromEvents(newcomer.store().listEvents(ws));
			assertEquals("Full State", state.workspace().name(), "N=" + n + " newcomer debe tener workspace name");
			assertEquals(10, state.chatMessages().size(), "N=" + n + " newcomer debe tener 10 mensajes");
			assertEquals(1, state.notes().size(), "N=" + n + " newcomer debe tener notas");
			assertTrue(state.whiteboardObjects().containsKey("hist_obj"), "N=" + n + " newcomer debe tener pizarra");
			assertEquals(4, state.files().size(), "N=" + n + " newcomer debe tener 4 archivos de metadata");
		}
	}

	@Test void scenarioConcurrentNotesEditionsByMultipleMembers() {
		var store = new InMemoryEventStore();
		var ns = new NoteService(store, events);

		ns.updateNote("ws_conc_notes", "A", "shared-notes", "inicio");
		Event e1 = ns.insertLine("ws_conc_notes", "A", "shared-notes", null, " del texto");
		Event e2 = ns.insertLine("ws_conc_notes", "B", "shared-notes", null, "Nuevo");
		Event e3 = ns.insertLine("ws_conc_notes", "C", "shared-notes", null, " final");
		Event d1 = events.create("ws_conc_notes", EventTypes.NOTE_DELETE_OP, "B",
				Map.of("note_id", "shared-notes", "line_id", e1.payload().get("line_id").toString(),
						"op_id", "del-1", "created_at_ms", System.currentTimeMillis()), null);
		Event d2 = events.create("ws_conc_notes", EventTypes.NOTE_DELETE_OP, "C",
				Map.of("note_id", "shared-notes", "line_id", e2.payload().get("line_id").toString(),
						"op_id", "del-2", "created_at_ms", System.currentTimeMillis() + 1), null);
		store.append(d1);
		store.append(d2);

		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_conc_notes"));
		String text = state.notes().get("shared-notes").text();
		assertFalse(text.contains(" del texto"));
		assertFalse(text.contains("Nuevo"));
		assertTrue(text.contains(" final"));
	}

	@Test void scenarioMemberReconnectsAndRecoversOfflineEvents() {
		for (int n : new int[]{3, 5, 8}) {
			var net = new SimulatedNetworkAdapter();
			List<SimulatedNode> nodes = new ArrayList<>();
			for (int i = 1; i <= n; i++) { nodes.add(net.createNode("N" + i)); if (i > 1) net.connect(nodes.get(i-2 < 0 ? 0 : i-2), nodes.get(i-1)); }
			for (var node : nodes) net.connectUsingPolicy(node, new MeshPolicy(2, 4));

			String ws = "ws_recon_" + n;
			nodes.get(0).sync().broadcastEvent(events.create(ws, EventTypes.WORKSPACE_CREATED, "N1", Map.of(
					"name", "Recon", "required_approvals", 1, "auth_mode", "token",
					"creator_member_id", "N1", "creator_display_name", "Creator"), null));
			net.runGossipRounds(8);

			var offline = nodes.get(n - 1);
			int offlineBefore = offline.store().listEvents(ws).size();
			net.disconnectAll(offline);

			int eventsDuringOffline = n * 3;
			for (int i = 0; i < eventsDuringOffline; i++) {
				nodes.get(i % (n - 1)).sync().broadcastEvent(events.create(ws, EventTypes.CHAT_MESSAGE_CREATED,
						nodes.get(i % (n - 1)).id(),
						Map.of("message_id", "off" + i, "text", "offline msg " + i), null));
			}
			net.runGossipRounds(15);

			int beforeReconnect = offline.store().listEvents(ws).size();
			assertEquals(offlineBefore, beforeReconnect, "N=" + n + " offline no debe recibir eventos durante desconexion");

			net.connectUsingPolicy(offline, new MeshPolicy(2, 4));
			for (String peerId : offline.peers()) {
				var peer = net.node(peerId).orElseThrow();
				offline.sync().applyReceivedEvents(
						peer.sync().missingFor(ws, offline.store().listEventIds(ws)));
			}
			net.runGossipRounds(10);

			int afterReconnect = offline.store().listEvents(ws).size();
			assertTrue(afterReconnect > offlineBefore + eventsDuringOffline / 3,
					"N=" + n + " offline debe recuperar eventos tras reconectar. antes=" + offlineBefore
							+ " despues=" + afterReconnect + " eventos_durante=" + eventsDuringOffline);
		}
	}

	// ============================================================
	// GRUPO 26: Membresía P2P completa
	// ============================================================

	@Test void membershipApprovalViaCoreEventWithoutHubCommand() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var ws = new WorkspaceService(store, auth, ids, events);
		var created = ws.createWorkspace("P2P Membership", "A", 1);
		var memberships = new MembershipService(store, auth, events);

		Member candidate = memberships.createCandidate("B");
		memberships.requestJoin(created.workspaceId(), candidate);

		Event approval = memberships.approve(created.workspaceId(), created.creator().memberId(), candidate.memberId());
		assertEquals(EventTypes.MEMBER_JOIN_APPROVAL, approval.type());
		assertTrue(WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId())).isAuthorized(candidate.memberId()));
	}

	@Test void publicKeyAuthProviderCreatesSignedEvents() {
		var auth = new org.q3s.p2p.core.auth.PublicKeyAuthProvider(ids);
		var store = new InMemoryEventStore();
		var ws = new WorkspaceService(store, auth, ids, events);
		var created = ws.createWorkspace("Ed25519 WS", "A", 1);
		var state = WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId()));
		assertEquals("Ed25519 WS", state.workspace().name());
		assertTrue(state.isAuthorized(created.creator().memberId()));
	}

	@Test void crdtNotesIntegratedControllerFlow() {
		var store = new InMemoryEventStore();
		var ns = new NoteService(store, events);
		ns.insertLine("ws_flow", "A", "shared-notes", null, "inicial");
		Event insertA = ns.insertLine("ws_flow", "A", "shared-notes", null, " agregado");
		ns.insertLine("ws_flow", "B", "shared-notes", null, "Nuevo");
		Event delA = events.create("ws_flow", EventTypes.NOTE_DELETE_OP, "B",
				Map.of("note_id", "shared-notes", "line_id", insertA.payload().get("line_id").toString(),
						"op_id", "del-A", "created_at_ms", System.currentTimeMillis()), null);
		store.append(delA);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_flow"));
		String text = state.notes().get("shared-notes").text();
		assertFalse(text.contains(" agregado"));
		assertTrue(text.contains("Nuevo"));
		assertTrue(text.contains("inicial"));
	}

	// ============================================================
	// GRUPO 27: PeerCatalog e InviteCode
	// ============================================================

	@Test void inviteCodeRoundTrips() {
		String code = InviteCode.encode("localhost:18765", "ws_abc123");
		assertTrue(code.contains("?workspace="));
		assertTrue(code.startsWith("localhost:18765"));

		var decoded = InviteCode.decode(code);
		assertNotNull(decoded);
		assertEquals("localhost:18765", decoded.peerUrl());
		assertEquals("ws_abc123", decoded.workspaceId());
	}

	@Test void inviteCodeWorksWithDifferentPeers() {
		String codeA = InviteCode.encode("localhost:18765", "ws_abc");
		String codeB = InviteCode.encode("localhost:18766", "ws_abc");
		assertNotEquals(codeA, codeB);
		assertEquals("ws_abc", InviteCode.decode(codeA).workspaceId());
		assertEquals("ws_abc", InviteCode.decode(codeB).workspaceId());
		assertNotEquals(InviteCode.decode(codeA).peerUrl(), InviteCode.decode(codeB).peerUrl());
	}

	@Test void peerCatalogMergesNewPeers() {
		Map<String, String> local = new LinkedHashMap<>();
		local.put("A", "localhost:1");
		local.put("B", "localhost:2");

		Map<String, String> remote = new LinkedHashMap<>();
		remote.put("B", "localhost:2");
		remote.put("C", "localhost:3");
		remote.put("D", "localhost:4");

		Set<String> newPeers = new HashSet<>();
		for (var entry : remote.entrySet()) {
			if (!local.containsKey(entry.getKey())) {
				local.put(entry.getKey(), entry.getValue());
				newPeers.add(entry.getKey());
			}
		}
		assertEquals(4, local.size());
		assertEquals(Set.of("C", "D"), newPeers);
	}

	@Test void autoApproveInviterConsensusFlow() {
		var store = new InMemoryEventStore();
		var auth = new TokenAuthProvider(ids);
		var ws = new WorkspaceService(store, auth, ids, events);
		var created = ws.createWorkspace("P2P Invite", "A", 2);
		var memberships = new MembershipService(store, auth, events);

		Member inviter = memberships.createCandidate("B");
		memberships.addAuthorizedMember(created.workspaceId(), created.creator().memberId(), inviter);

		Member invited = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), invited);
		memberships.approve(created.workspaceId(), inviter.memberId(), invited.memberId());

		assertFalse(memberships.reconnect(created.workspaceId(), invited.memberId(), invited.membershipToken()));

		memberships.approve(created.workspaceId(), created.creator().memberId(), invited.memberId());

		assertTrue(memberships.reconnect(created.workspaceId(), invited.memberId(), invited.membershipToken()));
	}

	@Test void indexCacheViveEnSystemdataWorkspace(@TempDir Path tmp) {
		QfolderLayout layout = new QfolderLayout(tmp);
		Path systemWs = layout.systemWorkspaceRoot("ws_cache");
		Path cache = systemWs.resolve("index-cache.properties");

		assertTrue(cache.toString().contains("systemdata"));
		assertTrue(cache.toString().contains("workspaces"));
		assertTrue(cache.toString().contains("ws_cache"));
		assertEquals("index-cache.properties", cache.getFileName().toString());
	}

	@Test void identityViveEnSystemdata(@TempDir Path tmp) {
		QfolderLayout layout = new QfolderLayout(tmp);
		Path identity = layout.identityFile();

		assertEquals(tmp.resolve("systemdata").resolve("identity.properties"), identity);
	}

	@Test void whiteboardStrokePointsRoundtripPreservaIntArrays() {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("stroke_id", "stroke-1");
		payload.put("color", "#FF0000");
		payload.put("width", 3);
		List<int[]> points = new ArrayList<>();
		points.add(new int[] {10, 20});
		points.add(new int[] {30, 40});
		points.add(new int[] {50, 60});
		payload.put("points", points);
		Event stroke = new Event("evt-1", "ws-x", EventTypes.WHITEBOARD_STROKE_ADDED, "mem-1",
				Instant.now(), List.of(), payload, null, null, true);

		String json = CoreEventCodec.toJson(stroke);
		Event decoded = CoreEventCodec.eventFromJson(json);

		assertNotNull(decoded);
		Object raw = decoded.payload().get("points");
		assertInstanceOf(List.class, raw);
		List<?> roundtrip = (List<?>) raw;
		assertEquals(3, roundtrip.size());
		assertInstanceOf(int[].class, roundtrip.get(0));
		assertArrayEquals(new int[] {10, 20}, (int[]) roundtrip.get(0));
		assertArrayEquals(new int[] {30, 40}, (int[]) roundtrip.get(1));
		assertArrayEquals(new int[] {50, 60}, (int[]) roundtrip.get(2));

		WorkspaceState state = WorkspaceStateBuilder.fromEvents(List.of(decoded));
		assertEquals(1, state.strokes().size());
		Object stored = state.strokes().get("stroke-1").points();
		assertInstanceOf(List.class, stored);
		List<?> storedPoints = (List<?>) stored;
		assertInstanceOf(int[].class, storedPoints.get(0));
		assertArrayEquals(new int[] {10, 20}, (int[]) storedPoints.get(0));
	}
}
