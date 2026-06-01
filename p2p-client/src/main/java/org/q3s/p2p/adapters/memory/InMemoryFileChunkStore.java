package org.q3s.p2p.adapters.memory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.q3s.p2p.ports.FileChunkStore;

public class InMemoryFileChunkStore implements FileChunkStore {
	private final Map<String, byte[]> chunks = new LinkedHashMap<>();
	private final Map<String, List<String>> fileChunks = new LinkedHashMap<>();

	@Override
	public synchronized void putChunk(String fileId, String hash, byte[] bytes) {
		chunks.put(hash, Arrays.copyOf(bytes, bytes.length));
		fileChunks.computeIfAbsent(fileId, ignored -> new ArrayList<>()).add(hash);
	}

	@Override
	public synchronized Optional<byte[]> getChunk(String hash) {
		byte[] bytes = chunks.get(hash);
		return bytes == null ? Optional.empty() : Optional.of(Arrays.copyOf(bytes, bytes.length));
	}

	@Override
	public synchronized boolean hasChunk(String hash) {
		return chunks.containsKey(hash);
	}

	@Override
	public synchronized List<String> listChunks(String fileId) {
		return List.copyOf(fileChunks.getOrDefault(fileId, List.of()));
	}

	@Override
	public synchronized byte[] reconstructFile(String fileId) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (String hash : listChunks(fileId)) {
			byte[] bytes = chunks.get(hash);
			if (bytes != null) out.writeBytes(bytes);
		}
		return out.toByteArray();
	}
}
