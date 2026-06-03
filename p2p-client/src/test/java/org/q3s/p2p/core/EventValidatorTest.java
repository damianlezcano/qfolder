package org.q3s.p2p.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.q3s.p2p.adapters.memory.InMemoryEventStore;
import org.q3s.p2p.adapters.memory.SystemClockProvider;
import org.q3s.p2p.adapters.memory.UuidIdGenerator;
import org.q3s.p2p.core.auth.PublicKeyAuthProvider;
import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.events.EventValidator;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.Member;

class EventValidatorTest {

	private InMemoryEventStore store;
	private PublicKeyAuthProvider auth;
	private EventFactory events;
	private EventValidator validator;

	@BeforeEach
	void setup() {
		store = new InMemoryEventStore();
		auth = new PublicKeyAuthProvider(new UuidIdGenerator());
		events = new EventFactory(new UuidIdGenerator(), new SystemClockProvider());
		validator = new EventValidator(store);
	}

	@Test void eventoConWorkspaceIdVacioEsRechazado() {
		Event bad = events.create("", EventTypes.CHAT_MESSAGE_CREATED, "mem-1",
				Map.of("message_id", "m1", "text", "hi"), null);
		assertFalse(validator.isAcceptable(bad));
	}

	@Test void eventoSinTipoEsRechazado() {
		Event bad = new Event("evt-1", "ws-1", "", "mem-1", Instant.now(), List.of(),
				Map.of("message_id", "m1"), null, null, true);
		assertFalse(validator.isAcceptable(bad));
	}

	@Test void eventoDeMiembroNoAutorizadoEsRechazado() {
		Event msg = events.create("ws-1", EventTypes.CHAT_MESSAGE_CREATED, "ghost",
				Map.of("message_id", "m1", "text", "hi"), null);
		assertFalse(validator.isAcceptable(msg));
	}

	@Test void workSpaceCreatedEsRechazadoSiYaExiste() {
		Event created = new Event("evt-1", "ws-1", EventTypes.WORKSPACE_CREATED, "creator-1",
				Instant.now(), List.of(),
				Map.of("name", "Test", "auth_mode", "ed25519", "required_approvals", 1,
						"creator_member_id", "creator-1", "creator_display_name", "C",
						"creator_device_id", "dev", "creator_membership_token", "tok",
						"creator_public_key", ""),
				null, null, true);
		store.append(created);
		Event duplicate = new Event("evt-2", "ws-1", EventTypes.WORKSPACE_CREATED, "creator-1",
				Instant.now(), List.of(),
				Map.of("name", "Test2", "auth_mode", "ed25519", "required_approvals", 1,
						"creator_member_id", "creator-1", "creator_display_name", "C",
						"creator_device_id", "dev", "creator_membership_token", "tok",
						"creator_public_key", ""),
				null, null, true);
		assertFalse(validator.isAcceptable(duplicate));
	}

	@Test void workSpaceCreatedEsAceptadoEnWorkspaceNuevo() {
		Event created = new Event("evt-1", "ws-1", EventTypes.WORKSPACE_CREATED, "creator-1",
				Instant.now(), List.of(),
				Map.of("name", "Test", "auth_mode", "ed25519", "required_approvals", 1,
						"creator_member_id", "creator-1", "creator_display_name", "C",
						"creator_device_id", "dev", "creator_membership_token", "tok",
						"creator_public_key", ""),
				null, null, true);
		assertTrue(validator.isAcceptable(created));
	}

	@Test void memberJoinRequestedEsRechazadoSiRevocado() {
		Event created = new Event("evt-1", "ws-1", EventTypes.WORKSPACE_CREATED, "creator-1",
				Instant.now(), List.of(),
				Map.of("name", "Test", "auth_mode", "ed25519", "required_approvals", 1,
						"creator_member_id", "creator-1", "creator_display_name", "C",
						"creator_device_id", "dev", "creator_membership_token", "tok",
						"creator_public_key", ""),
				null, null, true);
		store.append(created);

		Event revoke = events.create("ws-1", EventTypes.MEMBER_REVOKED, "creator-1",
				Map.of("member_id", "ghost", "reason", "test"), null);
		store.append(revoke);

		Event joinReq = events.create("ws-1", EventTypes.MEMBER_JOIN_REQUESTED, "ghost",
				Map.of("candidate_member_id", "ghost", "candidate_display_name", "Ghost",
						"candidate_device_id", "dev", "membership_token", "tok"), null);
		assertFalse(validator.isAcceptable(joinReq));
	}

	@Test void memberJoinApprovalEsRechazadoSiApproverNoEsAutorizado() {
		Event created = new Event("evt-1", "ws-1", EventTypes.WORKSPACE_CREATED, "creator-1",
				Instant.now(), List.of(),
				Map.of("name", "Test", "auth_mode", "ed25519", "required_approvals", 1,
						"creator_member_id", "creator-1", "creator_display_name", "C",
						"creator_device_id", "dev", "creator_membership_token", "tok",
						"creator_public_key", ""),
				null, null, true);
		store.append(created);

		Event approval = events.create("ws-1", EventTypes.MEMBER_JOIN_APPROVAL, "ghost",
				Map.of("candidate_member_id", "ghost", "approved_by", "ghost"), null);
		assertFalse(validator.isAcceptable(approval));
	}

	@Test void memberJoinApprovalEsAceptadoPorAutor() {
		Event created = new Event("evt-1", "ws-1", EventTypes.WORKSPACE_CREATED, "creator-1",
				Instant.now(), List.of(),
				Map.of("name", "Test", "auth_mode", "ed25519", "required_approvals", 1,
						"creator_member_id", "creator-1", "creator_display_name", "C",
						"creator_device_id", "dev", "creator_membership_token", "tok",
						"creator_public_key", ""),
				null, null, true);
		store.append(created);

		Map<String, Object> approvalPayload = new LinkedHashMap<>();
		approvalPayload.put("candidate_member_id", "ghost");
		approvalPayload.put("approved_by", "creator-1");
		Event approval = new Event("appr-1", "ws-1", EventTypes.MEMBER_JOIN_APPROVAL, "creator-1",
				Instant.now(), List.of(), approvalPayload,
				new org.q3s.p2p.core.model.AuthInfo("ed25519"), "fake-sig", true);
		assertFalse(validator.isAcceptable(approval));
	}

	@Test void eventoConFirmaInvalidaEsRechazado() {
		String publicKey = "MCowBQYDK2VwAyEAR9pyz0zjJ1qBb2vT3rFsPd9wBdYqMxV3XmGTk3sXnqE=";
		Event created = new Event("evt-1", "ws-1", EventTypes.WORKSPACE_CREATED, "creator-1",
				Instant.now(), List.of(),
				Map.of("name", "Test", "auth_mode", "ed25519", "required_approvals", 1,
						"creator_member_id", "creator-1", "creator_display_name", "C",
						"creator_device_id", "dev", "creator_membership_token", "tok",
						"creator_public_key", publicKey),
				new org.q3s.p2p.core.model.AuthInfo("ed25519"), "AAAAinvalid", true);
		assertFalse(validator.isAcceptable(created));
	}

	@Test void eventoSinFirmaCuandoHayPublicKeyEsRechazado() {
		String publicKey = "MCowBQYDK2VwAyEAR9pyz0zjJ1qBb2vT3rFsPd9wBdYqMxV3XmGTk3sXnqE=";
		Event created = new Event("evt-1", "ws-1", EventTypes.WORKSPACE_CREATED, "creator-1",
				Instant.now(), List.of(),
				Map.of("name", "Test", "auth_mode", "ed25519", "required_approvals", 1,
						"creator_member_id", "creator-1", "creator_display_name", "C",
						"creator_device_id", "dev", "creator_membership_token", "tok",
						"creator_public_key", publicKey),
				null, null, true);
		assertFalse(validator.isAcceptable(created));
	}
}
