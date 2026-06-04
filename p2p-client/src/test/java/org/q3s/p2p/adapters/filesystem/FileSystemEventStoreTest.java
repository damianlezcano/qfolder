package org.q3s.p2p.adapters.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.q3s.p2p.core.model.AuthInfo;
import org.q3s.p2p.core.model.Event;

class FileSystemEventStoreTest {

	private static Event makeEvent(String id, String ws, String type, Instant t, boolean persistent) {
		return new Event(id, ws, type, "alice", t, List.of(), Map.of("k", "v"),
				new AuthInfo("ed25519"), "sig", persistent);
	}

	@Test
	void appendYListRecuperanEventos(@TempDir Path tmp) {
		FileSystemEventStore store = new FileSystemEventStore(tmp.resolve("ws-1"));
		Event e1 = makeEvent("ev-1", "ws-1", "chat.sent", Instant.parse("2024-01-01T00:00:00Z"), true);
		Event e2 = makeEvent("ev-2", "ws-1", "chat.sent", Instant.parse("2024-01-01T00:00:01Z"), true);
		store.append(e1);
		store.append(e2);
		List<Event> events = store.listEvents("ws-1");
		assertEquals(2, events.size());
		assertEquals("ev-1", events.get(0).eventId());
		assertEquals("ev-2", events.get(1).eventId());
	}

	@Test
	void appendIgnoraDuplicados(@TempDir Path tmp) {
		FileSystemEventStore store = new FileSystemEventStore(tmp.resolve("ws-1"));
		Event e1 = makeEvent("ev-1", "ws-1", "chat.sent", Instant.parse("2024-01-01T00:00:00Z"), true);
		store.append(e1);
		store.append(e1);
		assertEquals(1, store.listEvents("ws-1").size());
	}

	@Test
	void appendIgnoraEventosEphemeros(@TempDir Path tmp) {
		FileSystemEventStore store = new FileSystemEventStore(tmp.resolve("ws-1"));
		Event e = makeEvent("ev-1", "ws-1", "peer.status.updated", Instant.now(), false);
		store.append(e);
		assertEquals(0, store.listEvents("ws-1").size());
	}

	@Test
	void hasEventEncuentraYNoEncuentra(@TempDir Path tmp) {
		FileSystemEventStore store = new FileSystemEventStore(tmp.resolve("ws-1"));
		Event e = makeEvent("ev-1", "ws-1", "chat.sent", Instant.now(), true);
		store.append(e);
		assertTrue(store.hasEvent("ev-1"));
		assertFalse(store.hasEvent("missing"));
	}

	@Test
	void getMissingEventsFiltraPorConocidos(@TempDir Path tmp) {
		FileSystemEventStore store = new FileSystemEventStore(tmp.resolve("ws-1"));
		store.append(makeEvent("ev-1", "ws-1", "a", Instant.parse("2024-01-01T00:00:00Z"), true));
		store.append(makeEvent("ev-2", "ws-1", "a", Instant.parse("2024-01-01T00:00:01Z"), true));
		store.append(makeEvent("ev-3", "ws-1", "a", Instant.parse("2024-01-01T00:00:02Z"), true));
		List<Event> missing = store.getMissingEvents("ws-1", Set.of("ev-1", "ev-2"));
		assertEquals(1, missing.size());
		assertEquals("ev-3", missing.get(0).eventId());
	}

	@Test
	void listEventIdsDevuelveSet(@TempDir Path tmp) {
		FileSystemEventStore store = new FileSystemEventStore(tmp.resolve("ws-1"));
		store.append(makeEvent("ev-1", "ws-1", "a", Instant.now(), true));
		store.append(makeEvent("ev-2", "ws-1", "b", Instant.now(), true));
		assertEquals(2, store.listEventIds("ws-1").size());
	}

	@Test
	void containsTypeVerdaderoSiHay(@TempDir Path tmp) {
		FileSystemEventStore store = new FileSystemEventStore(tmp.resolve("ws-1"));
		store.append(makeEvent("ev-1", "ws-1", "chat.sent", Instant.now(), true));
		assertTrue(store.containsType("ws-1", "chat.sent"));
		assertFalse(store.containsType("ws-1", "other"));
	}

	@Test
	void getEventEncuentraArchivo(@TempDir Path tmp) {
		FileSystemEventStore store = new FileSystemEventStore(tmp.resolve("ws-1"));
		store.append(makeEvent("ev-1", "ws-1", "chat.sent", Instant.parse("2024-01-01T00:00:00Z"), true));
		assertTrue(store.getEvent("ev-1").isPresent());
		assertTrue(store.getEvent("missing").isEmpty());
	}

	@Test
	void listEventsVacioSiNoHayWorkspace(@TempDir Path tmp) {
		FileSystemEventStore store = new FileSystemEventStore(tmp.resolve("ws-1"));
		assertEquals(0, store.listEvents("ws-1").size());
	}
}
