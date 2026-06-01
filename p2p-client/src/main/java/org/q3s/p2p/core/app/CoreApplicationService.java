package org.q3s.p2p.core.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.q3s.p2p.adapters.filesystem.FileSystemEventStore;
import org.q3s.p2p.adapters.filesystem.FileSystemFileChunkStore;
import org.q3s.p2p.core.auth.PublicKeyAuthProvider;
import org.q3s.p2p.adapters.memory.SystemClockProvider;
import org.q3s.p2p.adapters.memory.UuidIdGenerator;
import org.q3s.p2p.core.auth.TokenAuthProvider;
import org.q3s.p2p.core.chat.ChatService;
import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventService;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.files.FileService;
import org.q3s.p2p.core.members.MembershipService;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.core.notes.NoteService;
import org.q3s.p2p.core.state.WorkspaceState;
import org.q3s.p2p.core.state.WorkspaceStateBuilder;
import org.q3s.p2p.core.whiteboard.WhiteboardService;
import org.q3s.p2p.core.workspace.WorkspaceService;
import org.q3s.p2p.ports.AuthProvider;
import org.q3s.p2p.ports.EventStore;
import org.q3s.p2p.ports.FileChunkStore;
import org.q3s.p2p.ports.IdGenerator;

public class CoreApplicationService {
	private final EventStore eventStore;
	private final FileChunkStore chunkStore;
	private final IdGenerator ids;
	private final AuthProvider auth;
	private final EventFactory eventFactory;
	private final WorkspaceService workspaceService;
	private final MembershipService membershipService;
	private final ChatService chatService;
	private final FileService fileService;
	private final NoteService noteService;
	private final WhiteboardService whiteboardService;
	private String currentWorkspaceId;
	private Member currentMember;

	public CoreApplicationService(EventStore eventStore, FileChunkStore chunkStore, IdGenerator ids, boolean usePublicKeyAuth) {
		this.eventStore = eventStore;
		this.chunkStore = chunkStore;
		this.ids = ids;
		this.auth = usePublicKeyAuth ? new PublicKeyAuthProvider(ids) : new TokenAuthProvider(ids);
		this.eventFactory = new EventFactory(ids, new SystemClockProvider(), this::stampLocalEvent);
		this.workspaceService = new WorkspaceService(eventStore, auth, ids, eventFactory);
		this.membershipService = new MembershipService(eventStore, auth, eventFactory);
		this.chatService = new ChatService(eventStore, eventFactory, ids);
		this.fileService = new FileService(eventStore, chunkStore, eventFactory, ids);
		this.noteService = new NoteService(eventStore, eventFactory);
		this.whiteboardService = new WhiteboardService(eventStore, eventFactory, ids);
	}

	public CoreApplicationService(EventStore eventStore, FileChunkStore chunkStore, IdGenerator ids) {
		this(eventStore, chunkStore, ids, false);
	}

	public static CoreApplicationService filesystem(Path root) {
		return new CoreApplicationService(new FileSystemEventStore(root.resolve("workspaces")),
				new FileSystemFileChunkStore(root.resolve("chunks")), new UuidIdGenerator(), true);
	}

	public static CoreApplicationService filesystemWorkspace(Path workspaceRoot) {
		return new CoreApplicationService(new FileSystemEventStore(workspaceRoot, true),
				new FileSystemFileChunkStore(workspaceRoot.resolve("chunks")), new UuidIdGenerator(), true);
	}

	public WorkspaceService.CreatedWorkspace createWorkspace(String name, String displayName, int requiredApprovals) {
		WorkspaceService.CreatedWorkspace created = workspaceService.createWorkspace(name, displayName, requiredApprovals);
		currentWorkspaceId = created.workspaceId();
		currentMember = created.creator();
		return created;
	}

	public void attachExistingSession(String workspaceId, String memberId, String displayName, String deviceId, String token) {
		attachExistingSession(workspaceId, memberId, displayName, deviceId, token, "", "");
	}

	public void attachExistingSession(String workspaceId, String memberId, String displayName, String deviceId, String token,
			String publicKey, String privateKey) {
		currentWorkspaceId = workspaceId;
		currentMember = new Member(memberId, displayName, deviceId, token, false, publicKey == null ? "" : publicKey);
		registerPrivateKey(memberId, privateKey);
	}

	public void ensureWorkspaceSession(String workspaceId, String workspaceName, String memberId, String displayName,
			String deviceId, String token, int requiredApprovals) {
		ensureWorkspaceSession(workspaceId, workspaceName, memberId, displayName, deviceId, token, "", "", requiredApprovals);
	}

	public void ensureWorkspaceSession(String workspaceId, String workspaceName, String memberId, String displayName,
			String deviceId, String token, String publicKey, String privateKey, int requiredApprovals) {
		attachExistingSession(workspaceId, memberId, displayName, deviceId, token, publicKey, privateKey);
		if (eventStore.containsType(workspaceId, EventTypes.WORKSPACE_CREATED)) return;
		Event created = eventFactory.create(workspaceId, EventTypes.WORKSPACE_CREATED, memberId, Map.of(
				"name", workspaceName == null || workspaceName.isBlank() ? workspaceId : workspaceName,
				"required_approvals", requiredApprovals,
				"auth_mode", auth.authMode(),
				"creator_member_id", memberId,
				"creator_display_name", displayName == null ? memberId : displayName,
				"creator_device_id", deviceId == null ? "swing-device" : deviceId,
				"creator_membership_token", token == null ? memberId : token,
				"creator_public_key", publicKey == null ? "" : publicKey), null);
		eventStore.append(auth.stampEvent(created));
	}

	public Event recordJoinRequest(String candidateMemberId, String candidateDisplayName, String candidateDeviceId, String token) {
		return recordJoinRequest(candidateMemberId, candidateDisplayName, candidateDeviceId, token, "", "");
	}

	public Event recordJoinRequest(String candidateMemberId, String candidateDisplayName, String candidateDeviceId, String token,
			String publicKey, String privateKey) {
		requireWorkspace();
		registerPrivateKey(candidateMemberId, privateKey);
		Member candidate = new Member(candidateMemberId, candidateDisplayName, candidateDeviceId, token, false, publicKey == null ? "" : publicKey);
		return membershipService.requestJoin(currentWorkspaceId, candidate);
	}

	public Event approveJoin(String candidateMemberId) {
		requireSession();
		return membershipService.approve(currentWorkspaceId, currentMember.memberId(), candidateMemberId);
	}

	public Event authorizeKnownMember(String memberId, String displayName, String deviceId, String token) {
		return authorizeKnownMember(memberId, displayName, deviceId, token, "");
	}

	public Event authorizeKnownMember(String memberId, String displayName, String deviceId, String token, String publicKey) {
		requireSession();
		return membershipService.addAuthorizedMember(currentWorkspaceId, currentMember.memberId(),
				new Member(memberId, displayName, deviceId, token, false, publicKey == null ? "" : publicKey));
	}

	public boolean canReconnect(String memberId, String token) {
		requireWorkspace();
		return membershipService.reconnect(currentWorkspaceId, memberId, token);
	}

	public Event sendChatMessage(String text) {
		requireSession();
		return chatService.sendMessage(currentWorkspaceId, currentMember.memberId(), text);
	}

	public Event shareFile(String name, byte[] content) {
		requireSession();
		return fileService.shareFile(currentWorkspaceId, currentMember.memberId(), name, content);
	}

	public Event shareChatFile(String name, byte[] content) {
		requireSession();
		return fileService.shareFile(currentWorkspaceId, currentMember.memberId(), name, content, true);
	}

	public Event shareFile(Path path) {
		requireSession();
		try {
			return shareFile(path.getFileName().toString(), Files.readAllBytes(path));
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo registrar archivo en core: " + path, e);
		}
	}

	public Event shareChatFile(Path path) {
		requireSession();
		try {
			return shareChatFile(path.getFileName().toString(), Files.readAllBytes(path));
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo registrar adjunto de chat en core: " + path, e);
		}
	}

	public int indexSharedDirectory(Path dir) {
		requireSession();
		if (dir == null || !Files.isDirectory(dir)) return 0;
		try (var paths = Files.walk(dir)) {
			int[] count = {0};
			paths.filter(Files::isRegularFile).forEach(path -> {
				shareFile(path);
				count[0]++;
			});
			return count[0];
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo indexar directorio en core: " + dir, e);
		}
	}

	public Event finishWhiteboardStroke(List<int[]> points) {
		requireSession();
		return whiteboardService.finishStroke(currentWorkspaceId, currentMember.memberId(), points);
	}

	public Event finishWhiteboardStroke(List<int[]> points, String color, int width) {
		requireSession();
		return whiteboardService.finishStroke(currentWorkspaceId, currentMember.memberId(), points, color, width);
	}

	public Event recordWhiteboardObjectAction(String action, String objectId, String operation) {
		requireSession();
		return switch (action == null ? "" : action) {
			case "add" -> whiteboardService.objectAdded(currentWorkspaceId, currentMember.memberId(), objectId, operation);
			case "update" -> whiteboardService.objectMoved(currentWorkspaceId, currentMember.memberId(), objectId, operation);
			case "delete" -> whiteboardService.objectDeleted(currentWorkspaceId, currentMember.memberId(), objectId);
			case "clear" -> whiteboardService.cleared(currentWorkspaceId, currentMember.memberId());
			default -> throw new IllegalArgumentException("Accion de pizarra desconocida: " + action);
		};
	}

	public Event updateNote(String noteId, String text) {
		requireSession();
		return noteService.updateNote(currentWorkspaceId, currentMember.memberId(), noteId, text == null ? "" : text);
	}

	public Event insertNoteText(String noteId, int position, String text) {
		requireSession();
		return noteService.insertText(currentWorkspaceId, currentMember.memberId(), noteId, position, text);
	}

	public Event deleteNoteText(String noteId, int position, int length) {
		requireSession();
		return noteService.deleteText(currentWorkspaceId, currentMember.memberId(), noteId, position, length);
	}

	public Event updatePeerStatus(String peerUrl, Set<String> connectedPeers) {
		requireSession();
		Event event = eventFactory.create(currentWorkspaceId, EventTypes.PEER_STATUS_UPDATED, currentMember.memberId(), Map.of(
				"member_id", currentMember.memberId(),
				"peer_url", peerUrl == null ? "" : peerUrl,
				"connected_peers", connectedPeers == null ? List.of() : List.copyOf(connectedPeers)), null);
		eventStore.append(event);
		return event;
	}

	public boolean receiveRemoteEvent(Event event) {
		requireWorkspace();
		if (event == null || !currentWorkspaceId.equals(event.workspaceId())) return false;
		return new EventService(eventStore, true).accept(event);
	}

	public int receiveRemoteEvents(List<Event> events) {
		requireWorkspace();
		int accepted = 0;
		if (events == null) return accepted;
		for (Event event : events) {
			if (receiveRemoteEvent(event)) accepted++;
		}
		return accepted;
	}

	public List<Event> missingEvents(Set<String> knownEventIds) {
		requireWorkspace();
		return eventStore.getMissingEvents(currentWorkspaceId, knownEventIds == null ? Set.of() : knownEventIds);
	}

	public Set<String> eventIds() {
		requireWorkspace();
		return eventStore.listEventIds(currentWorkspaceId);
	}

	public Set<String> availableChunks(List<String> chunkHashes) {
		if (chunkHashes == null) return Set.of();
		Set<String> available = new java.util.LinkedHashSet<>();
		for (String hash : chunkHashes) {
			if (chunkStore.hasChunk(hash)) available.add(hash);
		}
		return available;
	}

	public Optional<byte[]> readChunk(String hash) {
		return chunkStore.getChunk(hash);
	}

	public void storeChunk(String fileId, String hash, byte[] bytes) {
		chunkStore.putChunk(fileId, hash, bytes);
	}

	public byte[] reconstructFile(String fileId) {
		return chunkStore.reconstructFile(fileId);
	}

	public WorkspaceState currentState() {
		requireWorkspace();
		return WorkspaceStateBuilder.fromEvents(eventStore.listEvents(currentWorkspaceId));
	}

	public List<Event> events() {
		requireWorkspace();
		return eventStore.listEvents(currentWorkspaceId);
	}

	public Optional<Member> currentMember() {
		return Optional.ofNullable(currentMember);
	}

	private Event stampLocalEvent(Event event) {
		if (event == null || currentMember == null || !currentMember.memberId().equals(event.authorMemberId())) return event;
		return auth.stampEvent(event, currentMember);
	}

	private void registerPrivateKey(String memberId, String privateKey) {
		if (auth instanceof PublicKeyAuthProvider publicKeyAuth) publicKeyAuth.registerPrivateKey(memberId, privateKey);
	}

	public Optional<String> currentWorkspaceId() {
		return Optional.ofNullable(currentWorkspaceId);
	}

	public MembershipService membershipService() { return membershipService; }
	public EventStore eventStore() { return eventStore; }
	public FileChunkStore chunkStore() { return chunkStore; }

	private void requireSession() {
		requireWorkspace();
		if (currentMember == null) throw new IllegalStateException("No hay miembro core activo");
	}

	private void requireWorkspace() {
		if (currentWorkspaceId == null || currentWorkspaceId.isBlank()) throw new IllegalStateException("No hay workspace core activo");
	}
}
