package org.q3s.p2p.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.q3s.p2p.adapters.memory.InMemoryEventStore;
import org.q3s.p2p.adapters.memory.InMemoryFileChunkStore;
import org.q3s.p2p.adapters.memory.SystemClockProvider;
import org.q3s.p2p.adapters.memory.UuidIdGenerator;
import org.q3s.p2p.adapters.network.SimulatedNetworkAdapter;
import org.q3s.p2p.adapters.network.CoreChunkTransferProtocol;
import org.q3s.p2p.core.codec.CoreEnvelope;
import org.q3s.p2p.core.codec.CoreEnvelopeCodec;
import org.q3s.p2p.core.auth.TokenAuthProvider;
import org.q3s.p2p.core.app.CoreApplicationService;
import org.q3s.p2p.core.chat.ChatService;
import org.q3s.p2p.core.events.CoreEventCodec;
import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventService;
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

class CoreArchitectureTest {
	private InMemoryEventStore store;
	private InMemoryFileChunkStore chunks;
	private UuidIdGenerator ids;
	private EventFactory events;
	private TokenAuthProvider auth;
	private WorkspaceService workspaces;
	private MembershipService memberships;

	@BeforeEach
	void setUp() {
		store = new InMemoryEventStore();
		chunks = new InMemoryFileChunkStore();
		ids = new UuidIdGenerator();
		events = new EventFactory(ids, new SystemClockProvider());
		auth = new TokenAuthProvider(ids);
		workspaces = new WorkspaceService(store, auth, ids, events);
		memberships = new MembershipService(store, auth, events);
	}

	@Test
	void createWorkspaceCreatesIdEventCreatorAndPolicy() {
		WorkspaceService.CreatedWorkspace created = workspaces.createWorkspace("Reunión Proyecto P2P", "Damian", 2);
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId()));
		assertTrue(created.workspaceId().startsWith("ws_"));
		assertTrue(store.containsType(created.workspaceId(), EventTypes.WORKSPACE_CREATED));
		assertTrue(state.isAuthorized(created.creator().memberId()));
		assertEquals(2, state.workspace().requiredApprovals());
	}

	@Test
	void lightConsensusApprovesOnlyAfterRequiredApprovals() {
		var created = workspaces.createWorkspace("W", "A", 2);
		Member b = memberships.createCandidate("B");
		memberships.addAuthorizedMember(created.workspaceId(), created.creator().memberId(), b);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), c);
		memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
		WorkspaceState halfway = WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId()));
		assertFalse(halfway.isAuthorized(c.memberId()));
		memberships.approve(created.workspaceId(), b.memberId(), c.memberId());
		WorkspaceState finalState = WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId()));
		assertTrue(finalState.isAuthorized(c.memberId()));
	}

	@Test
	void approvedMemberReconnectsWithToken() {
		var created = workspaces.createWorkspace("W", "A", 1);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), c);
		memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
		assertTrue(memberships.reconnect(created.workspaceId(), c.memberId(), c.membershipToken()));
	}

	@Test
	void meshSelectsLessLoadedPeer() {
		SimulatedNetworkAdapter network = new SimulatedNetworkAdapter();
		var a = network.createNode("A");
		var b = network.createNode("B");
		var c = network.createNode("C");
		var d = network.createNode("D");
		network.connect(a, b);
		network.connect(b, c);
		MeshPolicy policy = new MeshPolicy(2, 2);
		String selected = network.selectPeerFor(d, policy).orElseThrow().peerId();
		assertNotEquals("B", selected);
		assertTrue(Set.of("A", "C").contains(selected));
	}

	@Test
	void gossipPropagatesEventsAcrossChain() {
		SimulatedNetworkAdapter network = new SimulatedNetworkAdapter();
		var a = network.createNode("A");
		var b = network.createNode("B");
		var c = network.createNode("C");
		var d = network.createNode("D");
		network.connect(a, b);
		network.connect(b, c);
		network.connect(c, d);
		Event event = events.create("ws_1", EventTypes.CHAT_MESSAGE_CREATED, "member_a", Map.of("message_id", "m1", "text", "Hola"), null);
		a.sync().broadcastEvent(event);
		network.runGossipRounds(4);
		assertTrue(b.store().hasEvent(event.eventId()));
		assertTrue(c.store().hasEvent(event.eventId()));
		assertTrue(d.store().hasEvent(event.eventId()));
	}

	@Test
	void eventStoreDeduplicatesByEventId() {
		Event event = events.create("ws_1", EventTypes.CHAT_MESSAGE_CREATED, "member_a", Map.of("message_id", "m1", "text", "Hola"), null);
		store.append(event);
		store.append(event);
		assertEquals(1, store.listEvents("ws_1").size());
	}

	@Test
	void stateRecoversFromEvents() {
		var created = workspaces.createWorkspace("W", "A", 1);
		new ChatService(store, events, ids).sendMessage(created.workspaceId(), created.creator().memberId(), "Hola");
		new NoteService(store, events).updateNote(created.workspaceId(), created.creator().memberId(), "note_1", "Nota");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents(created.workspaceId()));
		assertEquals("W", state.workspace().name());
		assertEquals(1, state.chatMessages().size());
		assertEquals("Nota", state.notes().get("note_1").text());
	}

	@Test
	void applicationServiceStoresSharedNotesUpdate() {
		CoreApplicationService app = new CoreApplicationService(store, chunks, ids);
		app.createWorkspace("W", "A", 1);
		app.updateNote("shared-notes", "QNOTES2\nT|false|false|false|14|#000000|Tm90YQ==\n");
		WorkspaceState state = app.currentState();
		assertEquals("QNOTES2\nT|false|false|false|14|#000000|Tm90YQ==\n", state.notes().get("shared-notes").text());
	}

	@Test
	void coreEnvelopeCodecRoundTripsCoreEvent() {
		Event event = events.create("ws_1", EventTypes.CHAT_MESSAGE_CREATED, "member_a", Map.of("message_id", "m1", "text", "Hola"), null);
		CoreEnvelope envelope = CoreEnvelope.of(CoreEnvelopeCodec.CORE_EVENT_NAME, "U1",
				CoreEnvelopeCodec.encodeCoreEvent(event));
		assertTrue(envelope.response().startsWith(CoreEnvelopeCodec.CORE_PAYLOAD_VERSION + "\n"));
		Event decoded = CoreEnvelopeCodec.decodeCoreEvent(envelope);
		assertEquals(event.eventId(), decoded.eventId());
		assertEquals(EventTypes.CHAT_MESSAGE_CREATED, decoded.type());
	}

	@Test
	void coreEnvelopeCodecRoundTripsCoreSyncRequestAndResponse() {
		Event event = events.create("ws_1", EventTypes.NOTE_UPDATED, "member_a", Map.of("note_id", "shared-notes", "text", "Nota"), null);

		CoreEnvelope request = CoreEnvelope.of(CoreEnvelopeCodec.CORE_SYNC_REQUEST_NAME, "U1",
				CoreEnvelopeCodec.encodeKnownEventIds(Set.of("evt_known")));
		CoreEnvelope response = CoreEnvelope.of(CoreEnvelopeCodec.CORE_SYNC_RESPONSE_NAME, "U1",
				CoreEnvelopeCodec.encodeSyncPayload(List.of(event)));

		assertTrue(request.response().startsWith(CoreEnvelopeCodec.CORE_SYNC_PAYLOAD_VERSION + "\n"));
		assertTrue(response.response().startsWith(CoreEnvelopeCodec.CORE_SYNC_PAYLOAD_VERSION + "\n"));
		assertEquals(Set.of("evt_known"), CoreEnvelopeCodec.decodeKnownEventIds(request));
		assertEquals(event.eventId(), CoreEnvelopeCodec.decodeSyncEvents(response).get(0).eventId());
	}

	@Test
	void coreChunkTransferProtocolRoundTripsMessages() {
		assertTrue(CoreChunkTransferProtocol.availabilityRequest("t1", "file_1", List.of("c1", "c2"))
				.startsWith(CoreChunkTransferProtocol.PAYLOAD_VERSION + "\n"));
		var availabilityRequest = CoreChunkTransferProtocol.parseAvailabilityRequest(
				CoreChunkTransferProtocol.availabilityRequest("t1", "file_1", List.of("c1", "c2")));
		assertEquals("t1", availabilityRequest.transferId());
		assertEquals(List.of("c1", "c2"), availabilityRequest.chunks());

		var availabilityResponse = CoreChunkTransferProtocol.parseAvailabilityResponse(
				CoreChunkTransferProtocol.availabilityResponse("t1", "U2", List.of("c1")));
		assertEquals("U2", availabilityResponse.peerId());
		assertEquals(List.of("c1"), availabilityResponse.chunks());

		byte[] bytes = "chunk-data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		var chunkRequest = CoreChunkTransferProtocol.parseChunkRequest(CoreChunkTransferProtocol.chunkRequest("t1", "file_1", "c1"));
		var chunkResponse = CoreChunkTransferProtocol.parseChunkResponse(CoreChunkTransferProtocol.chunkResponse("t1", "file_1", "c1", bytes));
		assertEquals("c1", chunkRequest.chunkHash());
		assertArrayEquals(bytes, chunkResponse.bytes());
	}

	@Test
	void ephemeralEventsAreNotPersisted() {
		Event typing = events.create("ws_1", EventTypes.USER_TYPING, "member_a", Map.of(), null);
		Event cursor = events.create("ws_1", EventTypes.CURSOR_MOVED, "member_a", Map.of(), null);
		store.append(typing);
		store.append(cursor);
		assertTrue(store.listEvents("ws_1").isEmpty());
	}

	@Test
	void whiteboardPersistsOnlyFinishedStroke() {
		WhiteboardService whiteboard = new WhiteboardService(store, events, ids);
		Event preview = whiteboard.preview("ws_1", "member_a");
		store.append(preview);
		assertTrue(store.listEvents("ws_1").isEmpty());
		whiteboard.finishStroke("ws_1", "member_a", List.of(new int[]{10, 20}, new int[]{20, 30}), "#ff0000", 4);
		assertTrue(store.containsType("ws_1", EventTypes.WHITEBOARD_STROKE_ADDED));
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_1"));
		var stroke = state.strokes().values().iterator().next();
		assertEquals("#ff0000", stroke.color());
		assertEquals(4, stroke.width());
	}

	@Test
	void whiteboardObjectActionsArePersistentEvents() {
		WhiteboardService whiteboard = new WhiteboardService(store, events, ids);
		whiteboard.objectAdded("ws_1", "member_a", "obj_1", "S|Rectángulo|1,2,3,4|#000000|1");
		whiteboard.objectMoved("ws_1", "member_a", "obj_1", "S|Rectángulo|5,6,3,4|#000000|1");
		whiteboard.objectDeleted("ws_1", "member_a", "obj_1");
		assertTrue(store.containsType("ws_1", EventTypes.WHITEBOARD_OBJECT_ADDED));
		assertTrue(store.containsType("ws_1", EventTypes.WHITEBOARD_OBJECT_MOVED));
		assertTrue(store.containsType("ws_1", EventTypes.WHITEBOARD_OBJECT_DELETED));
	}

	@Test
	void whiteboardObjectsRecoverCurrentStateFromEvents() {
		WhiteboardService whiteboard = new WhiteboardService(store, events, ids);
		whiteboard.objectAdded("ws_1", "member_a", "obj_1", "S|Rectángulo|1,2,3,4|#000000|1");
		whiteboard.objectMoved("ws_1", "member_a", "obj_1", "S|Rectángulo|5,6,3,4|#000000|1");
		whiteboard.objectAdded("ws_1", "member_a", "obj_2", "T|1,1,50,20|18|#000000|SG9sYQ==");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_1"));
		assertEquals("S|Rectángulo|5,6,3,4|#000000|1", state.whiteboardObjects().get("obj_1"));
		assertTrue(state.whiteboardObjects().containsKey("obj_2"));

		whiteboard.objectDeleted("ws_1", "member_a", "obj_1");
		whiteboard.cleared("ws_1", "member_a");
		WorkspaceState cleared = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_1"));
		assertTrue(cleared.whiteboardObjects().isEmpty());
	}

	@Test
	void whiteboardImageMovePreservesImageDataForLateJoiners() {
		WhiteboardService whiteboard = new WhiteboardService(store, events, ids);
		whiteboard.objectAdded("ws_1", "member_a", "img_1", "I|10,10,100,80|BASE64DATA");
		whiteboard.objectMoved("ws_1", "member_a", "img_1", "I|20,30,100,80");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_1"));
		assertEquals("I|20,30,100,80|BASE64DATA", state.whiteboardObjects().get("img_1"));
	}

	@Test
	void whiteboardImageMoveWithFullDataKeepsUpdatedData() {
		WhiteboardService whiteboard = new WhiteboardService(store, events, ids);
		whiteboard.objectAdded("ws_1", "member_a", "img_1", "I|10,10,100,80|OLD");
		whiteboard.objectMoved("ws_1", "member_a", "img_1", "I|20,30,100,80|NEW");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_1"));
		assertEquals("I|20,30,100,80|NEW", state.whiteboardObjects().get("img_1"));
	}

	@Test
	void whiteboardShapeMoveStillUsesLatestOperation() {
		WhiteboardService whiteboard = new WhiteboardService(store, events, ids);
		whiteboard.objectAdded("ws_1", "member_a", "shape_1", "S|Rect%C3%A1ngulo|10,10,100,80|#000000|1");
		whiteboard.objectMoved("ws_1", "member_a", "shape_1", "S|Rect%C3%A1ngulo|20,30,100,80|#000000|1");
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents("ws_1"));
		assertEquals("S|Rect%C3%A1ngulo|20,30,100,80|#000000|1", state.whiteboardObjects().get("shape_1"));
	}

	@Test
	void fileSharedStoresMetadataAndChunksSeparately() {
		byte[] content = "contenido del archivo".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		Event event = new FileService(store, chunks, events, ids).shareFile("ws_1", "member_a", "manual.pdf", content);
		assertEquals(EventTypes.FILE_SHARED, event.type());
		assertFalse(event.payload().containsKey("raw_content"));
		assertFalse(chunks.listChunks(String.valueOf(event.payload().get("file_id"))).isEmpty());
		assertEquals(event.payload().get("hash"), sha256(chunks.reconstructFile(String.valueOf(event.payload().get("file_id")))));
	}

	private String sha256(byte[] bytes) {
		try {
			return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void missingEventsAreSynchronized() {
		InMemoryEventStore a = new InMemoryEventStore();
		InMemoryEventStore b = new InMemoryEventStore();
		for (int i = 1; i <= 4; i++) {
			Event event = events.create("ws_1", EventTypes.CHAT_MESSAGE_CREATED, "member_a", Map.of("message_id", "m" + i, "text", "m" + i), null);
			a.append(event);
			if (i <= 2) b.append(event);
		}
		for (Event missing : a.getMissingEvents("ws_1", b.listEventIds("ws_1"))) b.append(missing);
		assertEquals(4, b.listEvents("ws_1").size());
	}

	@Test
	void revokedMemberCannotReconnect() {
		var created = workspaces.createWorkspace("W", "A", 1);
		Member c = memberships.createCandidate("C");
		memberships.requestJoin(created.workspaceId(), c);
		memberships.approve(created.workspaceId(), created.creator().memberId(), c.memberId());
		memberships.revoke(created.workspaceId(), created.creator().memberId(), c.memberId());
		assertFalse(memberships.reconnect(created.workspaceId(), c.memberId(), c.membershipToken()));
	}

	@Test
	void remoteEventValidationRejectsUnauthorizedContent() {
		var created = workspaces.createWorkspace("W", "A", 1);
		Event forged = events.create(created.workspaceId(), EventTypes.CHAT_MESSAGE_CREATED, "intruder",
				Map.of("message_id", "forged", "text", "nope"), null);

		assertFalse(new EventService(store, true).accept(forged));
		assertFalse(store.hasEvent(forged.eventId()));
	}

	@Test
	void coreEventCodecRoundTripsEventAndSyncPayloadsAsJson() {
		Event event = events.create("ws_codec", EventTypes.FILE_SHARED, "member_a",
				Map.of("file_id", "f1", "name", "a.txt", "size", 7L, "hash", "h", "chunks", List.of("c2", "c1"), "shared_by", "member_a"), null);

		Event decoded = CoreEventCodec.decodeEventPayload(CoreEventCodec.encodeEventPayload(event));
		assertEquals(event.eventId(), decoded.eventId());
		assertEquals(event.type(), decoded.type());
		assertEquals(List.of("c2", "c1"), decoded.payload().get("chunks"));

		List<Event> sync = CoreEventCodec.decodeSyncEvents(CoreEventCodec.encodeSyncEvents(List.of(event)));
		assertEquals(1, sync.size());
		assertEquals(event.eventId(), sync.get(0).eventId());
	}

	@Test
	void lateJoinerReceivesWhiteboardImageDataAfterImageMove() {
		InMemoryEventStore storeA = new InMemoryEventStore();
		InMemoryEventStore storeC = new InMemoryEventStore();
		InMemoryFileChunkStore chunksA = new InMemoryFileChunkStore();
		InMemoryFileChunkStore chunksC = new InMemoryFileChunkStore();

		CoreApplicationService appA = new CoreApplicationService(storeA, chunksA, ids);
		CoreApplicationService appC = new CoreApplicationService(storeC, chunksC, ids);

		var created = appA.createWorkspace("WB", "A", 1);
		appA.recordWhiteboardObjectAction("add", "img_1", "I|10,10,100,80|BASE64DATA");
		appA.recordWhiteboardObjectAction("update", "img_1", "I|20,30,100,80");

		for (var e : storeA.listEvents(created.workspaceId())) {
			storeC.append(e);
		}

		appC.attachExistingSession(created.workspaceId(), "C", "C", "devC", "tokC");
		WorkspaceState stateC = appC.currentState();

		assertEquals("I|20,30,100,80|BASE64DATA", stateC.whiteboardObjects().get("img_1"));
	}
}
