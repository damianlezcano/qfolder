package org.q3s.p2p.adapters.filesystem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemFileChunkStoreTest {

	@Test
	void putChunkYGetChunkRoundTrip(@TempDir Path tmp) {
		FileSystemFileChunkStore store = new FileSystemFileChunkStore(tmp);
		store.putChunk("f1", "h1", new byte[]{1, 2, 3});
		Optional<byte[]> got = store.getChunk("h1");
		assertTrue(got.isPresent());
		assertArrayEquals(new byte[]{1, 2, 3}, got.get());
	}

	@Test
	void hasChunkVerdaderoSiEstaYFalseSiNo(@TempDir Path tmp) {
		FileSystemFileChunkStore store = new FileSystemFileChunkStore(tmp);
		store.putChunk("f1", "h1", new byte[]{1});
		assertTrue(store.hasChunk("h1"));
		assertFalse(store.hasChunk("missing"));
	}

	@Test
	void listChunksOrdenPorInsercion(@TempDir Path tmp) {
		FileSystemFileChunkStore store = new FileSystemFileChunkStore(tmp);
		store.putChunk("f1", "h-c", new byte[]{3});
		store.putChunk("f1", "h-a", new byte[]{1});
		store.putChunk("f1", "h-b", new byte[]{2});
		assertEquals(List.of("h-c", "h-a", "h-b"), store.listChunks("f1"));
	}

	@Test
	void listChunksVacioParaFileIdInexistente(@TempDir Path tmp) {
		FileSystemFileChunkStore store = new FileSystemFileChunkStore(tmp);
		assertEquals(0, store.listChunks("nope").size());
	}

	@Test
	void reconstructFileCombinaChunksEnOrden(@TempDir Path tmp) {
		FileSystemFileChunkStore store = new FileSystemFileChunkStore(tmp);
		store.putChunk("f1", "h1", new byte[]{1, 2});
		store.putChunk("f1", "h2", new byte[]{3, 4});
		store.putChunk("f1", "h3", new byte[]{5});
		byte[] reconstructed = store.reconstructFile("f1");
		assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, reconstructed);
	}

	@Test
	void chunksEnDistintosFileIdsConHashDistinto(@TempDir Path tmp) {
		FileSystemFileChunkStore store = new FileSystemFileChunkStore(tmp);
		store.putChunk("f1", "h-f1", new byte[]{1});
		store.putChunk("f2", "h-f2", new byte[]{2});
		assertArrayEquals(new byte[]{1}, store.reconstructFile("f1"));
		assertArrayEquals(new byte[]{2}, store.reconstructFile("f2"));
	}

	@Test
	void reconstructDeFileIdVacioRetornaVacio(@TempDir Path tmp) {
		FileSystemFileChunkStore store = new FileSystemFileChunkStore(tmp);
		assertEquals(0, store.reconstructFile("nonexistent").length);
	}

	@Test
	void chunkDuplicadoEnMismoFileNoProduceCopia(@TempDir Path tmp) throws Exception {
		FileSystemFileChunkStore store = new FileSystemFileChunkStore(tmp);
		store.putChunk("f1", "h1", new byte[]{1});
		store.putChunk("f1", "h1", new byte[]{1});
		assertEquals(List.of("h1"), store.listChunks("f1"));
		Path manifest = tmp.resolve("f1/chunks.order");
		assertEquals(1, Files.readAllLines(manifest).size());
	}
}
