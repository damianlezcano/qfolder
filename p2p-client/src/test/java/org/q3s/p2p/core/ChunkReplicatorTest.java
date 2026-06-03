package org.q3s.p2p.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.q3s.p2p.adapters.memory.InMemoryEventStore;
import org.q3s.p2p.adapters.memory.InMemoryFileChunkStore;
import org.q3s.p2p.adapters.memory.SystemClockProvider;
import org.q3s.p2p.adapters.memory.UuidIdGenerator;
import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.files.ChunkReplicator;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.FileMetadata;

class ChunkReplicatorTest {

	private InMemoryEventStore store;
	private InMemoryFileChunkStore chunks;
	private UuidIdGenerator ids;
	private EventFactory events;
	private ChunkReplicator replicator;

	@BeforeEach
	void setup() {
		store = new InMemoryEventStore();
		chunks = new InMemoryFileChunkStore();
		ids = new UuidIdGenerator();
		events = new EventFactory(ids, new SystemClockProvider());
		replicator = new ChunkReplicator(store, chunks);
	}

	@Test void porDefaultEstaDeshabilitado() {
		assertFalse(replicator.isEnabled());
	}

	@Test void enableActivaElReplicator() {
		replicator.enable();
		assertTrue(replicator.isEnabled());
		replicator.disable();
		assertFalse(replicator.isEnabled());
	}

	@Test void processCurrentStateNotificaArchivosFaltantes() {
		byte[] content = "hello world".getBytes();
		Event shared = events.create("ws-1", "file.shared", "A",
				java.util.Map.of("file_id", "f1", "name", "hello.txt", "size", content.length,
						"hash", "abc", "chunks", List.of("h1", "h2", "h3"), "shared_by", "A"),
				null);
		store.append(shared);
		replicator.enable();
		AtomicReference<FileMetadata> captured = new AtomicReference<>();
		replicator.onFileAvailable(captured::set);

		int newFiles = replicator.processCurrentState("ws-1");
		assertEquals(1, newFiles);
		assertNotNull(captured.get());
		assertEquals("f1", captured.get().fileId());
	}

	@Test void processCurrentStateNoNotificaSiYaTenemosLosChunks() {
		byte[] content = "hello world".getBytes();
		Event shared = events.create("ws-1", "file.shared", "A",
				java.util.Map.of("file_id", "f1", "name", "hello.txt", "size", content.length,
						"hash", "abc", "chunks", List.of("h1", "h2"), "shared_by", "A"),
				null);
		store.append(shared);
		chunks.putChunk("f1", "h1", new byte[]{1});
		chunks.putChunk("f1", "h2", new byte[]{2});
		replicator.enable();

		int newFiles = replicator.processCurrentState("ws-1");
		assertEquals(0, newFiles);
	}

	@Test void processCurrentStateRespetaDisabled() {
		byte[] content = "hello".getBytes();
		Event shared = events.create("ws-1", "file.shared", "A",
				java.util.Map.of("file_id", "f1", "name", "hello.txt", "size", content.length,
						"hash", "abc", "chunks", List.of("h1"), "shared_by", "A"),
				null);
		store.append(shared);
		AtomicInteger notified = new AtomicInteger();
		replicator.onFileAvailable(metadata -> notified.incrementAndGet());

		int newFiles = replicator.processCurrentState("ws-1");
		assertEquals(0, newFiles);
		assertEquals(0, notified.get());
	}

	@Test void missingChunksDetectaHuecos() {
		chunks.putChunk("f1", "h1", new byte[]{1});
		FileMetadata meta = new FileMetadata("f1", "a.txt", 10, "abc", List.of("h1", "h2", "h3"), "A");
		List<String> missing = replicator.missingChunks(meta);
		assertEquals(Set.of("h2", "h3"), Set.copyOf(missing));
	}

	@Test void multipleListenersRecibenMismaNotificacion() {
		Event shared = events.create("ws-1", "file.shared", "A",
				java.util.Map.of("file_id", "f1", "name", "a.txt", "size", 0,
						"hash", "abc", "chunks", List.of("h1"), "shared_by", "A"),
				null);
		store.append(shared);
		replicator.enable();
		AtomicInteger countA = new AtomicInteger();
		AtomicInteger countB = new AtomicInteger();
		replicator.onFileAvailable(m -> countA.incrementAndGet());
		replicator.onFileAvailable(m -> countB.incrementAndGet());

		replicator.processCurrentState("ws-1");
		assertEquals(1, countA.get());
		assertEquals(1, countB.get());
	}
}
