package org.q3s.p2p.core.files;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.EventStore;
import org.q3s.p2p.ports.FileChunkStore;
import org.q3s.p2p.ports.IdGenerator;

public class FileService {
	private static final int CHUNK_SIZE = 32 * 1024;
	private final EventStore store;
	private final FileChunkStore chunks;
	private final EventFactory events;
	private final IdGenerator ids;

	public FileService(EventStore store, FileChunkStore chunks, EventFactory events, IdGenerator ids) {
		this.store = store;
		this.chunks = chunks;
		this.events = events;
		this.ids = ids;
	}

	public Event shareFile(String workspaceId, String authorMemberId, String name, byte[] content) {
		return shareFile(workspaceId, authorMemberId, name, content, false);
	}

	public Event shareFile(String workspaceId, String authorMemberId, String name, byte[] content, boolean chatAttachment) {
		String fileId = ids.newId("file");
		List<String> chunkHashes = new ArrayList<>();
		for (int offset = 0; offset < content.length; offset += CHUNK_SIZE) {
			int len = Math.min(CHUNK_SIZE, content.length - offset);
			byte[] chunk = java.util.Arrays.copyOfRange(content, offset, offset + len);
			String hash = sha256(chunk);
			chunks.putChunk(fileId, hash, chunk);
			chunkHashes.add(hash);
		}
		Event event = events.create(workspaceId, EventTypes.FILE_SHARED, authorMemberId, Map.of(
				"file_id", fileId,
				"name", name,
				"size", content.length,
				"hash", sha256(content),
				"chunks", chunkHashes,
				"shared_by", authorMemberId,
				"chat_attachment", chatAttachment), null);
		store.append(event);
		return event;
	}

	private String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
