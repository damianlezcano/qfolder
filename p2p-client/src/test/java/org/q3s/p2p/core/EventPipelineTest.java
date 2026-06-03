package org.q3s.p2p.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.q3s.p2p.adapters.memory.InMemoryEventStore;
import org.q3s.p2p.adapters.memory.SystemClockProvider;
import org.q3s.p2p.adapters.memory.UuidIdGenerator;
import org.q3s.p2p.core.auth.TokenAuthProvider;
import org.q3s.p2p.core.events.EventPipeline;
import org.q3s.p2p.core.events.EventPipeline.AppendResult;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.Member;

class EventPipelineTest {

	private InMemoryEventStore store;
	private EventPipeline pipeline;
	private UuidIdGenerator ids;

	@BeforeEach
	void setup() {
		store = new InMemoryEventStore();
		ids = new UuidIdGenerator();
		pipeline = EventPipeline.create(store, new TokenAuthProvider(ids));
	}

	@Test void appendLocalGuardaYNotifica() {
		Member author = new Member("m1", "M1", "dev", "tok", false, "");
		((EventPipeline.DefaultEventPipeline) pipeline).bindCurrentAuthor(author);
		AtomicInteger notified = new AtomicInteger();
		pipeline.onEventStored(e -> notified.incrementAndGet());

		Event draft = new Event("evt-1", "ws-1", EventTypes.CHAT_MESSAGE_CREATED, "m1",
				Instant.now(), List.of(), Map.of("message_id", "m1", "text", "hi"),
				null, null, true);
		AppendResult result = pipeline.appendLocal(draft);

		assertTrue(result.accepted());
		assertEquals(result.event().eventId(), "evt-1");
		assertEquals(1, notified.get());
	}

	@Test void appendLocalRechazaDuplicados() {
		Member author = new Member("m1", "M1", "dev", "tok", false, "");
		((EventPipeline.DefaultEventPipeline) pipeline).bindCurrentAuthor(author);
		Event draft = new Event("evt-dup", "ws-1", EventTypes.CHAT_MESSAGE_CREATED, "m1",
				Instant.now(), List.of(), Map.of("message_id", "m1", "text", "hi"),
				null, null, true);

		AppendResult first = pipeline.appendLocal(draft);
		AppendResult second = pipeline.appendLocal(draft);

		assertTrue(first.accepted());
		assertEquals(EventPipeline.AppendStatus.DUPLICATE, second.status());
	}

	@Test void acceptRemoteValidaAntesDeGuardar() {
		Event invalid = new Event("evt-1", "ws-1", EventTypes.CHAT_MESSAGE_CREATED, "intruso",
				Instant.now(), List.of(), Map.of("message_id", "m1", "text", "hi"),
				null, null, true);
		assertFalse(pipeline.acceptRemote(invalid));
	}

	@Test void acceptRemoteNotificaSiEsEphemeral() {
		AtomicInteger notified = new AtomicInteger();
		pipeline.onEventStored(e -> notified.incrementAndGet());

		Event ephemeral = new Event("evt-eph", "ws-1", "peer.status.updated", "m1",
				Instant.now(), List.of(), Map.of(), null, null, false);
		assertTrue(pipeline.acceptRemote(ephemeral));
		assertEquals(1, notified.get());
	}

	@Test void acceptRemoteBatchCuentaAceptados() {
		Event invalid = new Event("evt-1", "ws-1", EventTypes.CHAT_MESSAGE_CREATED, "intruso",
				Instant.now(), List.of(), Map.of("message_id", "m1", "text", "hi"),
				null, null, true);
		Event ephemeral = new Event("evt-eph", "ws-1", "peer.status.updated", "m1",
				Instant.now(), List.of(), Map.of(), null, null, false);

		int accepted = pipeline.acceptRemoteBatch(List.of(invalid, ephemeral));
		assertEquals(1, accepted);
	}

	@Test void appendLocalRechazaEventIdVacio() {
		Event bad = new Event("", "ws-1", EventTypes.CHAT_MESSAGE_CREATED, "m1",
				Instant.now(), List.of(), Map.of("message_id", "m1", "text", "hi"),
				null, null, true);
		AppendResult result = pipeline.appendLocal(bad);
		assertEquals(EventPipeline.AppendStatus.INVALID, result.status());
	}
}
