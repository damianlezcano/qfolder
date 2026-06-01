package org.q3s.p2p.core.state;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.q3s.p2p.core.model.ChatMessage;
import org.q3s.p2p.core.model.FileMetadata;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.core.model.Note;
import org.q3s.p2p.core.model.WhiteboardStroke;
import org.q3s.p2p.core.model.Workspace;

public class WorkspaceState implements Serializable {
	private Workspace workspace;
	private final Map<String, Member> authorizedMembers = new LinkedHashMap<>();
	private final Map<String, Member> pendingMembers = new LinkedHashMap<>();
	private final Set<String> revokedMembers = new java.util.LinkedHashSet<>();
	private final Map<String, Set<String>> approvals = new LinkedHashMap<>();
	private final Map<String, ChatMessage> chatMessages = new LinkedHashMap<>();
	private final Map<String, FileMetadata> files = new LinkedHashMap<>();
	private final Map<String, Note> notes = new LinkedHashMap<>();
	private final Map<String, WhiteboardStroke> strokes = new LinkedHashMap<>();
	private final Map<String, String> whiteboardObjects = new LinkedHashMap<>();
	private final Map<String, String> peerUrls = new LinkedHashMap<>();
	private final Map<String, Set<String>> peerConnections = new LinkedHashMap<>();

	public Workspace workspace() { return workspace; }
	public void workspace(Workspace workspace) { this.workspace = workspace; }
	public Map<String, Member> authorizedMembers() { return authorizedMembers; }
	public Map<String, Member> pendingMembers() { return pendingMembers; }
	public Set<String> revokedMembers() { return revokedMembers; }
	public Map<String, Set<String>> approvals() { return approvals; }
	public Map<String, ChatMessage> chatMessages() { return chatMessages; }
	public Map<String, FileMetadata> files() { return files; }
	public Map<String, Note> notes() { return notes; }
	public Map<String, WhiteboardStroke> strokes() { return strokes; }
	public Map<String, String> whiteboardObjects() { return whiteboardObjects; }
	public Map<String, String> peerUrls() { return peerUrls; }
	public Map<String, Set<String>> peerConnections() { return peerConnections; }
	public boolean isAuthorized(String memberId) { return authorizedMembers.containsKey(memberId) && !revokedMembers.contains(memberId); }
}
