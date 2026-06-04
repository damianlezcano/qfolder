package org.q3s.p2p.ports;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de almacenamiento de chunks de archivos. Los chunks se identifican
 * por hash (SHA-256) y se organizan por fileId. La reconstruccion
 * (reconstructFile) preserva el orden de insercion original. Implementaciones:
 * InMemoryFileChunkStore (tests) y FileSystemFileChunkStore (produccion).
 */
public interface FileChunkStore {
	void putChunk(String fileId, String hash, byte[] bytes);
	Optional<byte[]> getChunk(String hash);
	boolean hasChunk(String hash);
	List<String> listChunks(String fileId);
	byte[] reconstructFile(String fileId);
}
