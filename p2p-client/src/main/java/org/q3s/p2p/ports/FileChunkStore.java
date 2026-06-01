package org.q3s.p2p.ports;

import java.util.List;
import java.util.Optional;

public interface FileChunkStore {
	void putChunk(String fileId, String hash, byte[] bytes);
	Optional<byte[]> getChunk(String hash);
	boolean hasChunk(String hash);
	List<String> listChunks(String fileId);
	byte[] reconstructFile(String fileId);
}
