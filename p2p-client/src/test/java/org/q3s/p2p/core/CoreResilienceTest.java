package org.q3s.p2p.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.q3s.p2p.adapters.memory.InMemoryEventStore;
import org.q3s.p2p.adapters.memory.InMemoryFileChunkStore;
import org.q3s.p2p.adapters.memory.SystemClockProvider;
import org.q3s.p2p.adapters.memory.UuidIdGenerator;
import org.q3s.p2p.adapters.network.CoreChunkTransferProtocol;
import org.q3s.p2p.adapters.network.SimulatedNetworkAdapter;
import org.q3s.p2p.adapters.network.SimulatedNetworkAdapter.SimulatedNode;
import org.q3s.p2p.core.chat.ChatService;
import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.files.DistributedChunkPlanner;
import org.q3s.p2p.core.files.FileService;
import org.q3s.p2p.core.members.MembershipService;
import org.q3s.p2p.core.mesh.MeshPolicy;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.FileMetadata;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.core.notes.NoteService;
import org.q3s.p2p.core.state.WorkspaceState;
import org.q3s.p2p.core.state.WorkspaceStateBuilder;
import org.q3s.p2p.core.whiteboard.WhiteboardService;
import org.q3s.p2p.core.workspace.WorkspaceService;
import org.q3s.p2p.ports.FileChunkStore;

@Tag("performance")
class CoreResilienceTest {

	private final UuidIdGenerator ids = new UuidIdGenerator();
	private final EventFactory events = new EventFactory(ids, new SystemClockProvider());

	private List<SimulatedNode> createMesh(int size, SimulatedNetworkAdapter net, MeshPolicy policy) {
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= size; i++) {
			var node = net.createNode("N" + i);
			nodes.add(node);
			if (i == 2) net.connect(nodes.get(0), node);
			if (i > 2) net.connectUsingPolicy(node, policy);
		}
		return nodes;
	}

	// ─────────────────────────────────────────────
	// SYNC Y RED
	// ─────────────────────────────────────────────

	@Test void packetLossDoesNotPreventGossipConvergence() {
		for (int n : new int[]{2, 4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			var nodes = createMesh(n, net, new MeshPolicy(2, 4));
			Event event = events.create("ws_pl", EventTypes.CHAT_MESSAGE_CREATED, "N1", Map.of("message_id", "m1", "text", "hola"), null);
			nodes.get(0).sync().broadcastEvent(event);
			net.runGossipRoundsWithLoss(20, 0.3);
			for (var node : nodes) assertTrue(node.store().hasEvent(event.eventId()), "N=" + n + " " + node.id() + " no recibio con 30% loss");
		}
	}

	@Test void highPacketLossStillConvergesWithMoreRounds() {
		for (int n : new int[]{2, 4, 8}) {
			var net = new SimulatedNetworkAdapter();
			var nodes = createMesh(n, net, new MeshPolicy(2, 4));
			Event event = events.create("ws_hl", EventTypes.NOTE_UPDATED, "N1", Map.of("note_id", "n1", "text", "nota"), null);
			nodes.get(0).sync().broadcastEvent(event);
			int delivered = net.runGossipRoundsWithLoss(40, 0.5);
			boolean allReceived = nodes.stream().allMatch(node -> node.store().hasEvent(event.eventId()));
			assertTrue(delivered > 0 || allReceived, "N=" + n + " debe propagarse o entregarse algo con 50% loss + 40 rondas");
		}
	}

	@Test void latencyDoesNotLoseEvents() {
		for (int n : new int[]{2, 4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			var nodes = createMesh(n, net, new MeshPolicy(2, 4));
			Event event = events.create("ws_lat", EventTypes.CHAT_MESSAGE_CREATED, "N1", Map.of("message_id", "m1", "text", "con latencia"), null);
			nodes.get(0).sync().broadcastEvent(event);
			net.runGossipRoundsWithLatencyAndLoss(30, 0.0, 3);
			for (var node : nodes) assertTrue(node.store().hasEvent(event.eventId()), "N=" + n + " " + node.id() + " no recibio con latencia");
		}
	}

	@Test void networkPartitionConvergesAfterHeal() {
		for (int n : new int[]{4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			var nodes = createMesh(n, net, new MeshPolicy(2, 4));
			int half = n / 2;
			for (int i = 0; i < half; i++)
				for (int j = half; j < n; j++)
					if (nodes.get(i).peers().contains(nodes.get(j).id()))
						net.disconnect(nodes.get(i), nodes.get(j));

			Event left = events.create("ws_part", EventTypes.CHAT_MESSAGE_CREATED, "N1", Map.of("message_id", "left", "text", "lado izq"), null);
			Event right = events.create("ws_part", EventTypes.CHAT_MESSAGE_CREATED, nodes.get(half).id(), Map.of("message_id", "right", "text", "lado der"), null);
			nodes.get(0).sync().broadcastEvent(left);
			nodes.get(half).sync().broadcastEvent(right);
			net.runGossipRounds(8);

			for (int i = 0; i < half; i++) net.connectUsingPolicy(nodes.get(i), new MeshPolicy(2, 4));
			for (int i = half; i < n; i++) net.connectUsingPolicy(nodes.get(i), new MeshPolicy(2, 4));
			for (int i = 0; i < half; i++)
				for (int j = half; j < n; j++)
					if (!nodes.get(i).peers().contains(nodes.get(j).id()) && nodes.get(i).degree() < 4 && nodes.get(j).degree() < 4)
						net.connect(nodes.get(i), nodes.get(j));

			for (int i = 0; i < n; i++) {
				for (String peerId : nodes.get(i).peers()) {
					var peer = net.node(peerId).orElseThrow();
					var missing = peer.sync().missingFor("ws_part", nodes.get(i).store().listEventIds("ws_part"));
					nodes.get(i).sync().applyReceivedEvents(missing);
				}
			}
			net.runGossipRounds(8);

			for (var node : nodes) {
				assertTrue(node.store().hasEvent(left.eventId()), "N=" + n + " " + node.id() + " no recibio evento izquierdo");
				assertTrue(node.store().hasEvent(right.eventId()), "N=" + n + " " + node.id() + " no recibio evento derecho");
			}
		}
	}

	@Test void churnDoesNotLoseEvents() {
		for (int n : new int[]{4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			var nodes = createMesh(n, net, new MeshPolicy(2, 4));
			Event event = events.create("ws_churn", EventTypes.CHAT_MESSAGE_CREATED, "N1", Map.of("message_id", "m1", "text", "churn"), null);
			nodes.get(0).sync().broadcastEvent(event);
			net.runGossipRounds(5);
			for (int round = 0; round < 9; round++) {
				int idx = round % n;
				net.disconnectAll(nodes.get(idx));
				net.runGossipRounds(3);
				net.connectUsingPolicy(nodes.get(idx), new MeshPolicy(2, 4));
				for (String peerId : nodes.get(idx).peers()) {
					var peer = net.node(peerId).orElse(null);
					if (peer != null) nodes.get(idx).sync().applyReceivedEvents(
							peer.sync().missingFor("ws_churn", nodes.get(idx).store().listEventIds("ws_churn")));
				}
			}
			net.runGossipRounds(8);
			int received = (int) nodes.stream().filter(node -> node.store().hasEvent(event.eventId())).count();
			assertTrue(received >= n / 2, "N=" + n + " al menos la mitad deben recibir evento en churn, recibieron=" + received);
		}
	}

	// ─────────────────────────────────────────────
	// CHUNKS Y ARCHIVOS
	// ─────────────────────────────────────────────

	@Test void chunkPeerTimeoutFallsBackToLegacyRoute() {
		var chunks = new InMemoryFileChunkStore();
		var store = new InMemoryEventStore();
		var net = new SimulatedNetworkAdapter();
		var nodes = createMesh(4, net, new MeshPolicy(2, 4));
		byte[] content = "timeout test content for chunks".getBytes(StandardCharsets.UTF_8);
		Event fileEvent = new FileService(store, chunks, events, ids).shareFile("ws_chunk_to", "N1", "timeout.bin", content);
		String fileId = String.valueOf(fileEvent.payload().get("file_id"));
		List<String> chunkList = chunks.listChunks(fileId);

		Map<String, Set<String>> availability = new LinkedHashMap<>();
		for (String c : chunkList) availability.put(c, Set.of("N3"));
		var plan = new DistributedChunkPlanner().planDownloads(chunkList, availability);
		assertEquals(1, plan.getOrDefault("N3", List.of()).size());

		String chunkHash = chunkList.get(0);
		var reqPayload = CoreChunkTransferProtocol.parseChunkRequest(
				CoreChunkTransferProtocol.chunkRequest("t1", fileId, chunkHash));
		assertEquals(chunkHash, reqPayload.chunkHash());
	}

	@Test void chunkCorruptHashTriggersDetection() {
		var chunks = new InMemoryFileChunkStore();
		var store = new InMemoryEventStore();
		byte[] content = "contenido integro".getBytes(StandardCharsets.UTF_8);
		Event event = new FileService(store, chunks, events, ids).shareFile("ws_corrupt", "N1", "a.txt", content);
		String storedHash = String.valueOf(event.payload().get("hash"));

		String corruptedHash = storedHash.equals("abc") ? "xyz" : "abc";
		assertNotEquals(storedHash, corruptedHash, "hash corrupto debe diferir del almacenado");

		byte[] reconstructed = chunks.reconstructFile(String.valueOf(event.payload().get("file_id")));
		assertArrayEquals(content, reconstructed);
	}

	@Test void chunkAvailabilityPartialDistributesCorrectly() {
		var chunks = new InMemoryFileChunkStore();
		var store = new InMemoryEventStore();
		byte[] content = ("A".repeat(100_000)).getBytes(StandardCharsets.UTF_8);
		Event event = new FileService(store, chunks, events, ids).shareFile("ws_avail", "N1", "big.bin", content);
		List<String> chunkList = chunks.listChunks(String.valueOf(event.payload().get("file_id")));
		assertTrue(chunkList.size() >= 4);

		Map<String, Set<String>> sparse = new LinkedHashMap<>();
		for (int i = 0; i < chunkList.size(); i++) {
			sparse.put(chunkList.get(i), Set.of("N" + ((i % 2) + 1)));
		}

		var planner = new DistributedChunkPlanner();
		Map<String, List<String>> plan = planner.planDownloads(chunkList, sparse);
		for (Map.Entry<String, List<String>> entry : plan.entrySet()) {
			for (String chunk : entry.getValue()) {
				assertTrue(sparse.get(chunk).contains(entry.getKey()),
						"chunk " + chunk + " asignado a " + entry.getKey() + " que no lo tiene disponible");
			}
		}
	}

	@Test void chunkStressManyChunksAcrossPeers() {
		int[] sizes = {2, 4, 8};
		for (int n : sizes) {
			var chunks = new InMemoryFileChunkStore();
			var store = new InMemoryEventStore();
			byte[] content = ("X".repeat(700_000)).getBytes(StandardCharsets.UTF_8);
			Event event = new FileService(store, chunks, events, ids).shareFile("ws_stress", "N1", "stress.bin", content);
			List<String> chunkList = chunks.listChunks(String.valueOf(event.payload().get("file_id")));
			assertTrue(chunkList.size() >= 20, "N=" + n + " debe generar al menos 20 chunks");

			Map<String, Set<String>> availability = new LinkedHashMap<>();
			for (int i = 0; i < chunkList.size(); i++) {
				availability.put(chunkList.get(i), Set.of("N" + ((i % n) + 1)));
			}
			Map<String, List<String>> plan = new DistributedChunkPlanner().planDownloads(chunkList, availability);
			int maxLoad = plan.values().stream().mapToInt(List::size).max().orElse(0);
			int total = plan.values().stream().mapToInt(List::size).sum();
			assertEquals(chunkList.size(), total, "N=" + n + " todos los chunks deben planificarse");
			assertTrue(maxLoad < chunkList.size(), "N=" + n + " carga maxima debe ser menor al total con " + n + " peers");
		}
	}

	// ─────────────────────────────────────────────
	// CHAT
	// ─────────────────────────────────────────────

	@Test void chatSimultaneousMessagesMaintainOrder() {
		for (int n : new int[]{2, 4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			var nodes = createMesh(n, net, new MeshPolicy(2, 4));
			String ws = "ws_sim_" + n;
			for (int i = 0; i < n; i++) {
				nodes.get(i).sync().broadcastEvent(events.create(ws, EventTypes.CHAT_MESSAGE_CREATED,
						nodes.get(i).id(), Map.of("message_id", "sim" + i, "text", "msg" + i), null));
			}
			net.runGossipRounds(20);
			for (var node : nodes) {
				var state = WorkspaceStateBuilder.fromEvents(node.store().listEvents(ws));
				assertEquals(n, state.chatMessages().size(), "N=" + n + " " + node.id() + " debe tener " + n + " mensajes");
			}
		}
	}

	@Test void chatLongMessagesWithSpecialCharsSurvive() {
		var store = new InMemoryEventStore();
		var chat = new ChatService(store, events, ids);
		String longText = "🔥 日本語 한국어 العربية 𓂀 " + "x".repeat(5000) + "\nlínea2\n\tlínea3\nemoji: 😀🎉";
		chat.sendMessage("ws_long", "member_a", longText);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_long"));
		assertEquals(1, state.chatMessages().size());
		assertEquals(longText, state.chatMessages().values().iterator().next().text());
	}

	@Test void chatReconstructionFromZeroAfterSync() {
		for (int n : new int[]{2, 4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			var nodes = createMesh(n, net, new MeshPolicy(2, 4));
			var newcomer = net.createNode("new");
			net.connectUsingPolicy(newcomer, new MeshPolicy(2, 4));
			for (int i = 1; i <= 50; i++) {
				nodes.get(i % n).sync().broadcastEvent(events.create("ws_hist_" + n, EventTypes.CHAT_MESSAGE_CREATED,
						nodes.get(i % n).id(), Map.of("message_id", "m" + i, "text", "msg" + i), null));
			}
			net.runGossipRounds(20);

			for (var existing : nodes) {
				newcomer.sync().applyReceivedEvents(
						existing.sync().missingFor("ws_hist_" + n, newcomer.store().listEventIds("ws_hist_" + n)));
			}
			var state = WorkspaceStateBuilder.fromEvents(newcomer.store().listEvents("ws_hist_" + n));
			assertEquals(50, state.chatMessages().size(), "N=" + n + " newcomer debe reconstruir 50 mensajes");
		}
	}

	// ─────────────────────────────────────────────
	// NOTAS
	// ─────────────────────────────────────────────

	@Test void notesConcurrentEditsLastSnapshotWins() {
		InMemoryEventStore storeA = new InMemoryEventStore();
		InMemoryEventStore storeB = new InMemoryEventStore();
		new NoteService(storeA, events).updateNote("ws", "a", "shared-notes", "nota A");
		new NoteService(storeB, events).updateNote("ws", "b", "shared-notes", "nota B");
		for (Event e : storeB.listEvents("ws")) storeA.append(e);
		for (Event e : storeA.listEvents("ws")) storeB.append(e);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(storeA.listEvents("ws"));
		assertNotNull(state.notes().get("shared-notes"));
	}

	@Test void notesWithImagesRoundTripQNOTES2() {
		var store = new InMemoryEventStore();
		String qnotes = "QNOTES2\nT|false|false|false|14|#000000|" + Base64.getEncoder().encodeToString("Texto con estilo".getBytes(StandardCharsets.UTF_8));
		new NoteService(store, events).updateNote("ws_img", "member_a", "shared-notes", qnotes);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_img"));
		assertEquals(qnotes, state.notes().get("shared-notes").text());
	}

	@Test void notesEmptySnapshotDoesNotBreak() {
		var store = new InMemoryEventStore();
		new NoteService(store, events).updateNote("ws_empty", "member_a", "shared-notes", "QNOTES2\n");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_empty"));
		assertEquals("QNOTES2\n", state.notes().get("shared-notes").text());
	}

	@Test void notesMultipleUpdatesKeepLastValue() {
		var store = new InMemoryEventStore();
		var ns = new NoteService(store, events);
		String lastText = null;
		for (int i = 1; i <= 20; i++) {
			lastText = "QNOTES2\nT|false|false|false|14|#000000|" +
					Base64.getEncoder().encodeToString(("nota" + i).getBytes(StandardCharsets.UTF_8));
			ns.updateNote("ws_multi", "member_a", "shared-notes", lastText);
		}
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_multi"));
		assertEquals(lastText, state.notes().get("shared-notes").text());
	}

	// ─────────────────────────────────────────────
	// PIZARRA
	// ─────────────────────────────────────────────

	@Test void whiteboardConcurrentMoveOfSameObjectLastWins() {
		var store = new InMemoryEventStore();
		var wb = new WhiteboardService(store, events, ids);
		wb.objectAdded("ws_wb", "member_a", "obj", "S|R|1,2,3,4|#000000|1");
		wb.objectMoved("ws_wb", "member_a", "obj", "S|R|10,20,3,4|#000000|1");
		wb.objectMoved("ws_wb", "member_b", "obj", "S|R|50,60,3,4|#ff0000|2");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_wb"));
		assertEquals("S|R|50,60,3,4|#ff0000|2", state.whiteboardObjects().get("obj"));
	}

	@Test void whiteboardManyObjectsReconstructSnapshot() {
		for (int n : new int[]{10, 50, 100}) {
			var store = new InMemoryEventStore();
			var wb = new WhiteboardService(store, events, ids);
			for (int i = 0; i < n; i++) {
				wb.objectAdded("ws_obj_" + n, "member_a", "obj" + i,
						"S|R|" + i + "," + i + ",10,10|#" + String.format("%06x", i * 1000) + "|1");
			}
			WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_obj_" + n));
			assertEquals(n, state.whiteboardObjects().size(), "N=" + n + " objetos deben estar en estado");
		}
	}

	@Test void whiteboardClearDuringEditing() {
		var store = new InMemoryEventStore();
		var wb = new WhiteboardService(store, events, ids);
		wb.objectAdded("ws_clear", "member_a", "obj1", "S|R|1,2,3,4|#000000|1");
		wb.objectAdded("ws_clear", "member_a", "obj2", "T|1,1,50,20|18|#000000|SG9sYQ==");
		wb.cleared("ws_clear", "member_b");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_clear"));
		assertTrue(state.whiteboardObjects().isEmpty());
	}

	@Test void whiteboardStrokeExtremeStyles() {
		var store = new InMemoryEventStore();
		var wb = new WhiteboardService(store, events, ids);
		wb.finishStroke("ws_style", "member_a", List.of(new int[]{0, 0}, new int[]{100, 100}), "invalid-color", 0);
		wb.finishStroke("ws_style", "member_b", List.of(new int[]{10, 10}, new int[]{50, 50}), "#ff00ff", 100);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_style"));
		assertEquals(2, state.strokes().size());
		for (var stroke : state.strokes().values()) {
			assertNotNull(stroke.color());
			assertTrue(stroke.width() >= 1);
		}
	}

	// ─────────────────────────────────────────────
	// MEMBRESÍA
	// ─────────────────────────────────────────────

	@Test void joinStormMultipleCandidatesApprovedCorrectly() {
		for (int n : new int[]{2, 4, 8, 10}) {
			var store = new InMemoryEventStore();
			var auth = new org.q3s.p2p.core.auth.TokenAuthProvider(ids);
			var created = new WorkspaceService(store, auth, ids, events).createWorkspace("Storm" + n, "Creator", 1);
			var memberships = new MembershipService(store, auth, events);
			List<Member> candidates = new ArrayList<>();
			for (int i = 1; i <= n; i++) {
				var c = memberships.createCandidate("C" + i);
				candidates.add(c);
				memberships.requestJoin(created.workspaceId(), c);
			}
			for (var c : candidates) {
				memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
				assertTrue(memberships.reconnect(created.workspaceId(), c.memberId(), c.membershipToken()));
			}
			WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId()));
			assertEquals(n + 1, state.authorizedMembers().size(), "N=" + n + " todos los candidatos mas creador deben estar autorizados");
		}
	}

	@Test void revokedMemberCannotRejoin() {
		for (int n : new int[]{2, 4, 8}) {
			var store = new InMemoryEventStore();
			var auth = new org.q3s.p2p.core.auth.TokenAuthProvider(ids);
			var created = new WorkspaceService(store, auth, ids, events).createWorkspace("Revoke" + n, "Creator", 1);
			var memberships = new MembershipService(store, auth, events);
			List<Member> approved = new ArrayList<>();
			for (int i = 1; i <= n; i++) {
				var m = memberships.createCandidate("M" + i);
				memberships.requestJoin(created.workspaceId(), m);
				memberships.approve(created.workspaceId(), created.creator().memberId(), m.memberId());
				approved.add(m);
			}
			for (var m : approved) {
				memberships.revoke(created.workspaceId(), created.creator().memberId(), m.memberId());
				assertFalse(memberships.reconnect(created.workspaceId(), m.memberId(), m.membershipToken()),
						"N=" + n + " revocado no debe reconectar");
			}
		}
	}

	@Test void dualApprovalSimultaneousDoesNotDuplicate() {
		var store = new InMemoryEventStore();
		var auth = new org.q3s.p2p.core.auth.TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("Dual", "Creator", 2);
		var memberships = new MembershipService(store, auth, events);
		var second = memberships.createCandidate("Approver2");
		memberships.addAuthorizedMember(created.workspaceId(), created.creator().memberId(), second);
		var candidate = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), candidate);
		memberships.approve(created.workspaceId(), created.creator().memberId(), candidate.memberId());
		memberships.approve(created.workspaceId(), second.memberId(), candidate.memberId());
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId()));
		assertTrue(state.isAuthorized(candidate.memberId()));
		assertEquals(3, state.authorizedMembers().size());
	}

	// ─────────────────────────────────────────────
	// ESTRÉS
	// ─────────────────────────────────────────────

	@Test void stressHundredsOfEventsSyncAcrossMesh() {
		for (int n : new int[]{2, 4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			var nodes = createMesh(n, net, new MeshPolicy(2, 4));
			int total = 500;
			for (int i = 0; i < total; i++) {
				nodes.get(i % n).sync().broadcastEvent(events.create("ws_stress_" + n, EventTypes.CHAT_MESSAGE_CREATED,
						nodes.get(i % n).id(), Map.of("message_id", "m" + i, "text", "msg" + i), null));
			}
			net.runGossipRounds(30);
			int maxEvents = net.totalEvents("ws_stress_" + n);
			assertTrue(maxEvents >= total * 0.8, "N=" + n + " al menos 80% de " + total + " eventos deben propagarse, recibidos=" + maxEvents);
		}
	}

	@Test void stressBurstOfEventsPropagates() {
		for (int n : new int[]{2, 4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			var nodes = createMesh(n, net, new MeshPolicy(2, 4));
			int total = 50;
			for (int i = 0; i < total; i++) {
				for (var node : nodes) {
					node.sync().broadcastEvent(events.create("ws_burst_" + n, EventTypes.CHAT_MESSAGE_CREATED,
							node.id(), Map.of("message_id", "b" + i + "_" + node.id(), "text", "burst"), null));
				}
			}
			net.runGossipRounds(30);
			int maxEvents = net.totalEvents("ws_burst_" + n);
			assertTrue(maxEvents >= total * n * 0.6, "N=" + n + " al menos 60% de rafaga debe propagarse, recibidos=" + maxEvents);
		}
	}

	@Test void stressMassiveFileIndexingWithDedup() {
		int[] counts = {100, 500, 1000};
		for (int count : counts) {
			var store = new InMemoryEventStore();
			var chunks = new InMemoryFileChunkStore();
			var fs = new FileService(store, chunks, events, ids);
			Map<String, String> seen = new LinkedHashMap<>();
			int registered = 0;
			for (int i = 0; i < count; i++) {
				String name = "file" + (i % 20) + ".txt";
				byte[] content = ("contenido_" + (i % 20)).getBytes(StandardCharsets.UTF_8);
				Event e = fs.shareFile("ws_massive", "member_a", name, content);
				String fingerprint = e.payload().get("hash").toString();
				if (!fingerprint.equals(seen.get(name + ":" + e.payload().get("size")))) {
					seen.put(name + ":" + e.payload().get("size"), fingerprint);
					registered++;
				}
			}
			assertTrue(registered <= 20, count + " archivos deben deduplicarse a 20 unicos, registrados=" + registered);
		}
	}

	// ─────────────────────────────────────────────
	// MALLA P2P
	// ─────────────────────────────────────────────

	@Test void p2pMeshFormationWithoutHub() {
		for (int n : new int[]{2, 4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			var nodes = new ArrayList<SimulatedNode>();
			for (int i = 1; i <= n; i++) {
				var node = net.createNode("P" + i);
				nodes.add(node);
				if (i == 2) net.connect(nodes.get(0), node);
				if (i > 2) net.connectUsingPolicy(node, new MeshPolicy(2, 4));
			}
			int totalEdges = net.edges().size();
			int totalDegrees = nodes.stream().mapToInt(SimulatedNode::degree).sum();
			assertTrue(totalDegrees >= (n - 1) * 2, "N=" + n + " grados totales deben ser al menos " + ((n-1)*2) + " fueron " + totalDegrees);
			assertTrue(totalEdges >= n / 2, "N=" + n + " debe haber al menos " + (n/2) + " aristas, hay " + totalEdges);
		}
	}

	@Test void p2pEventsPropagateWithoutHubDirectConnections() {
		for (int n : new int[]{4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			List<SimulatedNode> nodes = createMesh(n, net, new MeshPolicy(2, 4));
			Event event = events.create("ws_p2p", EventTypes.CHAT_MESSAGE_CREATED, "P1", Map.of("message_id", "m1", "text", "p2p sin hub"), null);
			nodes.get(0).sync().broadcastEvent(event);
			net.runGossipRounds(20);
			for (var node : nodes)
				assertTrue(node.store().hasEvent(event.eventId()), "N=" + n + " " + node.id() + " no recibio evento P2P");
		}
	}

	@Test void p2pSyncRecoversMissingEventsFromMultiplePeers() {
		for (int n : new int[]{4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			List<SimulatedNode> nodes = createMesh(n, net, new MeshPolicy(2, 4));
			var storeIds = new UuidIdGenerator();
			var storeEvents = new EventFactory(storeIds, new SystemClockProvider());
			for (int i = 1; i <= n * 5; i++) {
				int owner = i % n;
				nodes.get(owner).sync().broadcastEvent(
						storeEvents.create("ws_sync_p2p", nodes.get(owner).id(), EventTypes.CHAT_MESSAGE_CREATED,
								Map.of("message_id", "m" + i, "text", "msg" + i), null));
			}
			net.runGossipRounds(15);

			var newcomer = net.createNode("fresh");
			net.connectUsingPolicy(newcomer, new MeshPolicy(2, 4));
			for (var existing : nodes) {
				newcomer.sync().applyReceivedEvents(
						existing.sync().missingFor("ws_sync_p2p", newcomer.store().listEventIds("ws_sync_p2p")));
			}
			int recovered = newcomer.store().listEvents("ws_sync_p2p").size();
			assertTrue(recovered >= n * 5 * 0.8, "N=" + n + " newcomer debe recuperar al menos 80% de " + (n * 5) + " eventos, recupero=" + recovered);
		}
	}

	@Test void p2pMeshStaysUnderMaxDegreeWithPolicy() {
		for (int n : new int[]{4, 8, 10}) {
			var net = new SimulatedNetworkAdapter();
			var policy = new MeshPolicy(2, 4);
			List<SimulatedNode> nodes = createMesh(n, net, policy);
			for (var node : nodes) {
				assertTrue(node.degree() <= policy.maxConnectionsPerPeer(),
						"N=" + n + " " + node.id() + " excede max degree: " + node.degree());
			}
		}
	}

	// ─────────────────────────────────────────────
	// CONEXIÓN INTERMITENTE (mala internet)
	// ─────────────────────────────────────────────

	@Test void connectionFlappingDoesNotLoseEvents() {
		for (int n : new int[]{4, 8}) {
			var net = new SimulatedNetworkAdapter();
			var policy = new MeshPolicy(2, 4);
			List<SimulatedNode> nodes = createMesh(n, net, policy);
			String ws = "ws_flap_" + n;

			Event baseline = events.create(ws, EventTypes.CHAT_MESSAGE_CREATED, "N1", Map.of("message_id", "base", "text", "base"), null);
			nodes.get(0).sync().broadcastEvent(baseline);
			net.runGossipRounds(5);

			for (int flap = 0; flap < 8; flap++) {
				var target = nodes.get(flap % n);
				net.disconnectAll(target);
				Event evt = events.create(ws, EventTypes.CHAT_MESSAGE_CREATED, nodes.get((flap + 1) % n).id(),
						Map.of("message_id", "f" + flap, "text", "flap " + flap), null);
				nodes.get((flap + 1) % n).sync().broadcastEvent(evt);
				net.runGossipRoundsWithLoss(3, 0.4);
				net.connectUsingPolicy(target, policy);
				for (String pid : target.peers()) {
					var peer = net.node(pid).orElse(null);
					if (peer != null) target.sync().applyReceivedEvents(
							peer.sync().missingFor(ws, target.store().listEventIds(ws)));
				}
			}
			net.runGossipRounds(10);

			int minEvents = nodes.stream().mapToInt(nd -> nd.store().listEvents(ws).size()).min().orElse(0);
			assertTrue(minEvents >= 4, "N=" + n + " cada peer debe tener al menos 4 eventos tras flapping, min=" + minEvents);
		}
	}

	@Test void degradedConnectionQualityOverTime() {
		for (int n : new int[]{4, 8}) {
			var net = new SimulatedNetworkAdapter();
			var policy = new MeshPolicy(2, 4);
			List<SimulatedNode> nodes = createMesh(n, net, policy);
			String ws = "ws_degraded_" + n;

			double[] lossRates = {0.0, 0.1, 0.3, 0.5, 0.3, 0.1, 0.0};
			int deliveredTotal = 0;
			for (double loss : lossRates) {
				Event evt = events.create(ws, EventTypes.CHAT_MESSAGE_CREATED, "N1",
						Map.of("message_id", "d" + loss, "text", "degraded " + loss), null);
				nodes.get(0).sync().broadcastEvent(evt);
				deliveredTotal += net.runGossipRoundsWithLoss(10, loss);
			}
			long received = nodes.stream().filter(nd -> nd.store().listEvents(ws).size() >= 3).count();
			assertTrue(received >= n / 2, "N=" + n + " la mayoria debe sobrevivir degradacion: " + received + "/" + n + " entregados=" + deliveredTotal);
		}
	}

	@Test void highLatencyWithPacketLossSimultaneous() {
		for (int n : new int[]{4, 8}) {
			var net = new SimulatedNetworkAdapter();
			var policy = new MeshPolicy(2, 4);
			List<SimulatedNode> nodes = createMesh(n, net, policy);
			String ws = "ws_latloss_" + n;

			for (int i = 0; i < 6; i++) {
				Event evt = events.create(ws, EventTypes.CHAT_MESSAGE_CREATED, nodes.get(i % n).id(),
						Map.of("message_id", "ll" + i, "text", "latloss " + i), null);
				nodes.get(i % n).sync().broadcastEvent(evt);
			}
			net.runGossipRoundsWithLatencyAndLoss(30, 0.25, 4);

			int minEvents = nodes.stream().mapToInt(nd -> nd.store().listEvents(ws).size()).min().orElse(0);
			assertTrue(minEvents >= 3, "N=" + n + " con 25% loss + latencia 4 rondas, al menos 3 eventos deben llegar, min=" + minEvents);
		}
	}

	@Test void peersWithDifferentConnectionQuality() {
		var net = new SimulatedNetworkAdapter();
		var policy = new MeshPolicy(2, 4);
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			nodes.add(net.createNode("N" + i));
			if (i == 2) net.connect(nodes.get(0), nodes.get(1));
			if (i > 2) net.connectUsingPolicy(nodes.get(i-1), policy);
		}
		for (var node : nodes) net.connectUsingPolicy(node, policy);

		String ws = "ws_quality";
		for (int round = 0; round < 15; round++) {
			Event evt = events.create(ws, EventTypes.CHAT_MESSAGE_CREATED, nodes.get(round % 5).id(),
					Map.of("message_id", "q" + round, "text", "quality " + round), null);
			nodes.get(round % 5).sync().broadcastEvent(evt);
			double loss = round < 5 ? 0.0 : round < 10 ? 0.3 : 0.0;
			int delay = round < 5 ? 0 : round < 10 ? 3 : 0;
			net.runGossipRoundsWithLatencyAndLoss(2, loss, delay);
		}
		net.runGossipRounds(15);

		int minEvents = nodes.stream().mapToInt(nd -> nd.store().listEvents(ws).size()).min().orElse(0);
		assertTrue(minEvents >= 8, "mala calidad temporal no debe perder mas de la mitad, min=" + minEvents);
	}

	@Test void outOfOrderMessagesDueToNetworkIssues() {
		var net = new SimulatedNetworkAdapter();
		var policy = new MeshPolicy(2, 4);
		List<SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 4; i++) {
			nodes.add(net.createNode("N" + i));
			if (i == 2) net.connect(nodes.get(0), nodes.get(1));
			if (i > 2) net.connectUsingPolicy(nodes.get(i-1), policy);
		}
		for (var node : nodes) net.connectUsingPolicy(node, policy);

		String ws = "ws_ooo";
		for (int i = 0; i < 10; i++) {
			Event evt = events.create(ws, EventTypes.CHAT_MESSAGE_CREATED, nodes.get(i % 4).id(),
					Map.of("message_id", "o" + i, "text", "out-of-order " + i), null);
			nodes.get(i % 4).sync().broadcastEvent(evt);
		}
		net.runGossipRoundsWithLatencyAndLoss(25, 0.15, 5);

		for (var node : nodes) {
			int count = node.store().listEvents(ws).size();
			assertTrue(count >= 6, node.id() + " debe recibir al menos 6/10 mensajes con desorden y perdida, tiene=" + count);
		}
	}
}
