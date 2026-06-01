package org.q3s.p2p.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.q3s.p2p.adapters.filesystem.FileSystemEventStore;
import org.q3s.p2p.adapters.filesystem.FileSystemFileChunkStore;
import org.q3s.p2p.adapters.memory.SystemClockProvider;
import org.q3s.p2p.adapters.memory.UuidIdGenerator;
import org.q3s.p2p.adapters.network.SimulatedNetworkAdapter;
import org.q3s.p2p.core.auth.TokenAuthProvider;
import org.q3s.p2p.core.chat.ChatService;
import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.files.DistributedChunkPlanner;
import org.q3s.p2p.core.files.FileService;
import org.q3s.p2p.core.members.MembershipService;
import org.q3s.p2p.core.mesh.MeshPolicy;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.core.state.SnapshotService;
import org.q3s.p2p.core.state.WorkspaceState;
import org.q3s.p2p.core.workspace.WorkspaceService;

class BackendExtendedSimulationTest {
	@TempDir java.nio.file.Path tempDir;

	@Test
	void filesystemEventStoreAndSnapshotRecoverState() throws Exception {
		FileSystemEventStore store = new FileSystemEventStore(tempDir.resolve("workspaces"));
		UuidIdGenerator ids = new UuidIdGenerator();
		EventFactory events = new EventFactory(ids, new SystemClockProvider());
		TokenAuthProvider auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("Persistido", "A", 1);
		new ChatService(store, events, ids).sendMessage(created.workspaceId(), created.creator().memberId(), "mensaje persistido");

		FileSystemEventStore reopened = new FileSystemEventStore(tempDir.resolve("workspaces"));
		assertEquals(2, reopened.listEvents(created.workspaceId()).size());

		SnapshotService snapshots = new SnapshotService(tempDir.resolve("workspaces"));
		snapshots.save(created.workspaceId(), reopened.listEvents(created.workspaceId()));
		WorkspaceState state = snapshots.loadLatest(created.workspaceId()).orElseThrow();
		assertEquals("Persistido", state.workspace().name());
		assertEquals(1, state.chatMessages().size());
	}

	@Test
	void filesystemChunkStoreReconstructsFile() {
		FileSystemEventStore eventStore = new FileSystemEventStore(tempDir.resolve("workspaces"));
		FileSystemFileChunkStore chunkStore = new FileSystemFileChunkStore(tempDir.resolve("chunks"));
		UuidIdGenerator ids = new UuidIdGenerator();
		EventFactory events = new EventFactory(ids, new SystemClockProvider());
		byte[] content = "archivo grande simulado".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		Event event = new FileService(eventStore, chunkStore, events, ids).shareFile("ws_file", "member_a", "a.txt", content);
		String fileId = String.valueOf(event.payload().get("file_id"));
		assertArrayEquals(content, chunkStore.reconstructFile(fileId));
		assertTrue(Files.exists(tempDir.resolve("chunks").resolve(fileId)));
	}

	@Test
	void filesystemChunkStorePreservesWriteOrderWhenHashesSortDifferently() {
		FileSystemFileChunkStore chunkStore = new FileSystemFileChunkStore(tempDir.resolve("chunks-order"));
		chunkStore.putChunk("file_order", "b_hash", "primero".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		chunkStore.putChunk("file_order", "a_hash", "segundo".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		assertEquals(List.of("b_hash", "a_hash"), chunkStore.listChunks("file_order"));
		assertEquals("primerosegundo", new String(chunkStore.reconstructFile("file_order"), java.nio.charset.StandardCharsets.UTF_8));
	}

	@Test
	void filesystemChunkStoreDoesNotDuplicateRepeatedChunkHashes() {
		FileSystemFileChunkStore chunkStore = new FileSystemFileChunkStore(tempDir.resolve("chunks-dedup"));
		chunkStore.putChunk("file_dedup", "same_hash", "original".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		chunkStore.putChunk("file_dedup", "same_hash", "updated".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		assertEquals(List.of("same_hash"), chunkStore.listChunks("file_dedup"));
		assertEquals("updated", new String(chunkStore.reconstructFile("file_dedup"), java.nio.charset.StandardCharsets.UTF_8));
	}

	@Test
	void tenUsersMeshStaysUnderMaxDegreeAndGossipReachesAll() {
		SimulatedNetworkAdapter network = new SimulatedNetworkAdapter();
		MeshPolicy policy = new MeshPolicy(2, 4);
		List<SimulatedNetworkAdapter.SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 10; i++) {
			var node = network.createNode("U" + i);
			nodes.add(node);
			if (i == 2) network.connect(nodes.get(0), node);
			if (i > 2) network.connectUsingPolicy(node, policy);
		}

		EventFactory events = new EventFactory(new UuidIdGenerator(), new SystemClockProvider());
		Event event = events.create("ws_mesh", EventTypes.CHAT_MESSAGE_CREATED, "member_u1", Map.of("message_id", "m1", "text", "hola mesh"), null);
		nodes.get(0).sync().broadcastEvent(event);
		network.runGossipRounds(10);

		for (var node : nodes) {
			assertTrue(node.degree() <= policy.maxConnectionsPerPeer(), node.id() + " supera max degree");
			assertTrue(node.store().hasEvent(event.eventId()), node.id() + " no recibió gossip");
		}
		System.out.println("\nMALLA 10 USUARIOS\n" + network.topologyReport());
		System.out.println("CONEXIONES: " + network.edges());
	}

	@Test
	void tenMembersConsensusAndReconnects() {
		UuidIdGenerator ids = new UuidIdGenerator();
		EventFactory events = new EventFactory(ids, new SystemClockProvider());
		FileSystemEventStore store = new FileSystemEventStore(tempDir.resolve("workspaces"));
		TokenAuthProvider auth = new TokenAuthProvider(ids);
		var created = new WorkspaceService(store, auth, ids, events).createWorkspace("10 usuarios", "U1", 2);
		MembershipService memberships = new MembershipService(store, auth, events);

		Member u2 = memberships.createCandidate("U2");
		memberships.addAuthorizedMember(created.workspaceId(), created.creator().memberId(), u2);
		List<Member> approved = new ArrayList<>();
		approved.add(created.creator());
		approved.add(u2);

		for (int i = 3; i <= 10; i++) {
			Member candidate = memberships.createCandidate("U" + i);
			memberships.requestJoin(created.workspaceId(), candidate);
			memberships.approve(created.workspaceId(), approved.get(0).memberId(), candidate.memberId());
			assertFalse(memberships.reconnect(created.workspaceId(), candidate.memberId(), candidate.membershipToken()));
			memberships.approve(created.workspaceId(), approved.get(1).memberId(), candidate.memberId());
			assertTrue(memberships.reconnect(created.workspaceId(), candidate.memberId(), candidate.membershipToken()));
			approved.add(candidate);
		}
		assertEquals(10, org.q3s.p2p.core.state.WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId())).authorizedMembers().size());
	}

	@Test
	void tenMembersCanDisconnectGraduallyUntilWorkspaceHasNoLiveEdges() {
		SimulatedNetworkAdapter network = new SimulatedNetworkAdapter();
		MeshPolicy policy = new MeshPolicy(2, 4);
		List<SimulatedNetworkAdapter.SimulatedNode> nodes = createTenUserMesh(network, policy);

		for (int i = 0; i < nodes.size(); i++) {
			network.disconnectAll(nodes.get(i));
			assertEquals(0, nodes.get(i).degree(), nodes.get(i).id() + " debe quedar aislado");
			for (int j = 0; j <= i; j++) assertEquals(0, nodes.get(j).degree());
		}

		assertTrue(network.edges().isEmpty());
	}

	@Test
	void disconnectedMemberCanReconnectAndRecoverMissingEvents() {
		SimulatedNetworkAdapter network = new SimulatedNetworkAdapter();
		MeshPolicy policy = new MeshPolicy(2, 4);
		List<SimulatedNetworkAdapter.SimulatedNode> nodes = createTenUserMesh(network, policy);
		SimulatedNetworkAdapter.SimulatedNode reconnecting = nodes.get(9);
		network.disconnectAll(reconnecting);

		EventFactory events = new EventFactory(new UuidIdGenerator(), new SystemClockProvider());
		Event event = events.create("ws_reconnect", EventTypes.CHAT_MESSAGE_CREATED, "member_u1", Map.of("message_id", "m1", "text", "durante desconexion"), null);
		nodes.get(0).sync().broadcastEvent(event);
		network.runGossipRounds(8);
		assertFalse(reconnecting.store().hasEvent(event.eventId()));

		network.connectUsingPolicy(reconnecting, policy);
		for (String peerId : reconnecting.peers()) {
			SimulatedNetworkAdapter.SimulatedNode peer = network.node(peerId).orElseThrow();
			reconnecting.sync().applyReceivedEvents(peer.sync().missingFor("ws_reconnect", reconnecting.store().listEventIds("ws_reconnect")));
		}

		assertTrue(reconnecting.store().hasEvent(event.eventId()));
	}

	@Test
	void newMemberRequestsWorkspaceEventsFromAllMembersAndPlansParallelChunkDownload() {
		SimulatedNetworkAdapter network = new SimulatedNetworkAdapter();
		MeshPolicy policy = new MeshPolicy(2, 4);
		List<SimulatedNetworkAdapter.SimulatedNode> nodes = createTenUserMesh(network, policy);
		SimulatedNetworkAdapter.SimulatedNode newcomer = network.createNode("U11");
		network.connectUsingPolicy(newcomer, policy);

		EventFactory events = new EventFactory(new UuidIdGenerator(), new SystemClockProvider());
		List<Event> workspaceEvents = new ArrayList<>();
		for (int i = 1; i <= 6; i++) {
			workspaceEvents.add(events.create("ws_distributed", EventTypes.CHAT_MESSAGE_CREATED, "member_u" + i,
					Map.of("message_id", "m" + i, "text", "evento " + i), null));
		}
		List<String> chunks = List.of("c1", "c2", "c3", "c4", "c5", "c6");
		Event fileMetadata = events.create("ws_distributed", EventTypes.FILE_SHARED, "member_u1", Map.of(
				"file_id", "file_1", "name", "video.bin", "size", 6000, "hash", "filehash", "chunks", chunks, "shared_by", "member_u1"), null);
		workspaceEvents.add(fileMetadata);

		for (int i = 0; i < workspaceEvents.size(); i++) {
			nodes.get(i % 3).store().append(workspaceEvents.get(i));
			nodes.get((i + 3) % 10).store().append(workspaceEvents.get(i));
		}

		for (SimulatedNetworkAdapter.SimulatedNode peer : nodes) {
			newcomer.sync().applyReceivedEvents(peer.sync().missingFor("ws_distributed", newcomer.store().listEventIds("ws_distributed")));
		}

		WorkspaceState state = org.q3s.p2p.core.state.WorkspaceStateBuilder.fromEvents(newcomer.store().listEvents("ws_distributed"));
		assertEquals(6, state.chatMessages().size());
		assertTrue(state.files().containsKey("file_1"));
		assertTrue(newcomer.store().listEvents("ws_distributed").stream().noneMatch(e -> e.payload().containsKey("raw_content")));

		Map<String, java.util.Set<String>> availability = new java.util.LinkedHashMap<>();
		availability.put("c1", java.util.Set.of("U1", "U2"));
		availability.put("c2", java.util.Set.of("U2", "U3"));
		availability.put("c3", java.util.Set.of("U3", "U4"));
		availability.put("c4", java.util.Set.of("U4", "U5"));
		availability.put("c5", java.util.Set.of("U5", "U6"));
		availability.put("c6", java.util.Set.of("U6", "U1"));
		Map<String, List<String>> plan = new DistributedChunkPlanner().planDownloads(chunks, availability);

		assertTrue(plan.size() > 1, "la descarga debe repartirse entre varios miembros disponibles");
		assertEquals(chunks.size(), plan.values().stream().mapToInt(List::size).sum());
	}

	@Test
	void chunkDownloadCapacityImprovesWithMoreAvailableMembers() {
		List<String> chunks = List.of("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8");
		Map<String, java.util.Set<String>> oneOwner = new java.util.LinkedHashMap<>();
		Map<String, java.util.Set<String>> fourOwners = new java.util.LinkedHashMap<>();
		for (int i = 0; i < chunks.size(); i++) {
			oneOwner.put(chunks.get(i), java.util.Set.of("U1"));
			fourOwners.put(chunks.get(i), java.util.Set.of("U" + ((i % 4) + 1), "U" + (((i + 1) % 4) + 1)));
		}

		DistributedChunkPlanner planner = new DistributedChunkPlanner();
		Map<String, List<String>> serialPlan = planner.planDownloads(chunks, oneOwner);
		Map<String, List<String>> distributedPlan = planner.planDownloads(chunks, fourOwners);

		int serialMaxLoad = serialPlan.values().stream().mapToInt(List::size).max().orElse(0);
		int distributedMaxLoad = distributedPlan.values().stream().mapToInt(List::size).max().orElse(0);
		assertEquals(8, serialMaxLoad);
		assertTrue(distributedPlan.size() > serialPlan.size());
		assertTrue(distributedMaxLoad < serialMaxLoad, "mas miembros disponibles reducen la carga maxima por peer");
	}

	private List<SimulatedNetworkAdapter.SimulatedNode> createTenUserMesh(SimulatedNetworkAdapter network, MeshPolicy policy) {
		List<SimulatedNetworkAdapter.SimulatedNode> nodes = new ArrayList<>();
		for (int i = 1; i <= 10; i++) {
			var node = network.createNode("U" + i);
			nodes.add(node);
			if (i == 2) network.connect(nodes.get(0), node);
			if (i > 2) network.connectUsingPolicy(node, policy);
		}
		return nodes;
	}
}
