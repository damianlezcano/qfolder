package org.q3s.p2p.adapters.network;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.q3s.p2p.client.ws.WsClient;
import org.q3s.p2p.core.app.CoreApplicationService;
import org.q3s.p2p.core.codec.CoreEnvelope;
import org.q3s.p2p.core.codec.CoreEnvelopeCodec;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.state.WorkspaceState;
import org.q3s.p2p.model.User;

public class DirectBootstrap {

	private final CoreApplicationService core;
	private final P2PMeshService mesh;
	private final Consumer<CoreEnvelope> outbound;
	private final Runnable onWelcome;
	private final BiConsumer<User, String> onApprovalNeeded;
	private final Consumer<String> debug;

	public DirectBootstrap(CoreApplicationService core, P2PMeshService mesh,
			Consumer<CoreEnvelope> outbound, Runnable onWelcome,
			BiConsumer<User, String> onApprovalNeeded, Consumer<String> debug) {
		this.core = core;
		this.mesh = mesh;
		this.outbound = outbound;
		this.onWelcome = onWelcome;
		this.onApprovalNeeded = onApprovalNeeded;
		this.debug = debug == null ? ignored -> {} : debug;
	}

	public boolean join(String inviteCode, String localUserId, String localUserName) {
		return join(inviteCode, localUserId, localUserName, "", "");
	}

	public boolean join(String inviteCode, String localUserId, String localUserName, String publicKey, String privateKey) {
		InviteCode.DecodedInvite decoded = InviteCode.decode(inviteCode);
		if (decoded == null) {
			debug.accept("Invite invalido: " + inviteCode);
			return false;
		}
		String peerUrl = decoded.peerUrl();
		String wsId = decoded.workspaceId();

		core.attachExistingSession(wsId, localUserId, localUserName, "swing-device", localUserId, publicKey, privateKey);
		boolean alreadyAuthorized = false;
		try {
			alreadyAuthorized = core.currentState().isAuthorized(localUserId);
		} catch (Exception ignored) {}

		Event joinRequest = null;
		if (!alreadyAuthorized) {
			joinRequest = core.recordJoinRequest(localUserId, localUserName, "swing-device", localUserId, publicKey, privateKey);
		}

		String bootstrapUri = webSocketUriForEndpoint(peerUrl) + "/ws?wkId="
				+ java.net.URLEncoder.encode(wsId, java.nio.charset.StandardCharsets.UTF_8)
				+ "&userId=" + java.net.URLEncoder.encode(localUserId, java.nio.charset.StandardCharsets.UTF_8)
				+ "&direct=true";

		Exception lastError = null;
		for (int attempt = 1; attempt <= 3; attempt++) {
			WsClient bootstrap = null;
			boolean success = false;
			try {
				final boolean[] welcomed = {false};
				final Event pendingJoinRequest = joinRequest;
				bootstrap = new WsClient(new URI(bootstrapUri), null, envelope -> {
					if (CoreEnvelopeCodec.CORE_EVENT_NAME.equals(envelope.name())) {
						Event coreEvent = CoreEnvelopeCodec.decodeCoreEvent(envelope);
						if (coreEvent != null) {
							core.receiveRemoteEvent(coreEvent);
							welcomeIfAuthorized(localUserId, welcomed);
						}
					} else if (CoreEnvelopeCodec.CORE_SYNC_REQUEST_NAME.equals(envelope.name())) {
						handleSyncRequest(envelope);
					} else if (CoreEnvelopeCodec.CORE_SYNC_RESPONSE_NAME.equals(envelope.name())) {
						handleSyncResponse(envelope, localUserId, welcomed);
					}
				}, error -> debug.accept("Bootstrap error: " + error), () -> debug.accept("Bootstrap disconnected"), false);

				bootstrap.setConnectionLostTimeout(25);
				bootstrap.connectBlocking(6, java.util.concurrent.TimeUnit.SECONDS);
				if (pendingJoinRequest != null) {
					bootstrap.sendEnvelope(CoreEnvelope.of(
							CoreEnvelopeCodec.CORE_EVENT_NAME, localUserId,
							CoreEnvelopeCodec.encodeCoreEvent(pendingJoinRequest)));
				}
				CoreEnvelope request = CoreEnvelope.of(
						CoreEnvelopeCodec.CORE_SYNC_REQUEST_NAME,
						localUserId,
						CoreEnvelopeCodec.encodeKnownEventIds(core.eventIds()));
				bootstrap.sendEnvelope(request);
				mergeKnownPeers();
				welcomeIfAuthorized(localUserId, welcomed);

				debug.accept("Bootstrap conectado a " + peerUrl + " para ws " + wsId);
				success = true;
			} catch (Exception e) {
				lastError = e;
				debug.accept("Bootstrap fallo intento " + attempt + ": " + e.getMessage());
				try { Thread.sleep(250L * attempt); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
			} finally {
				if (bootstrap != null) {
					if (success) {
						try { Thread.sleep(500); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
					}
					try { bootstrap.close(); } catch (Exception ignored) {}
				}
			}
			if (success) return true;
		}
		debug.accept("Bootstrap fallo: " + (lastError != null ? lastError.getMessage() : "sin conexion"));
		return false;
	}

	public String inviteCode(String localPeerUrl) {
		String wsId = core.currentWorkspaceId().orElse("");
		return InviteCode.encode(localPeerUrl, wsId);
	}

	private void handleSyncRequest(CoreEnvelope envelope) {
		try {
			var knownIds = CoreEnvelopeCodec.decodeKnownEventIds(envelope);
			var missing = core.missingEvents(knownIds);
			if (!missing.isEmpty() && envelope.userId() != null) {
				String responsePayload = CoreEnvelopeCodec.encodeSyncPayload(missing);
				outbound.accept(CoreEnvelope.of(
						"__to:" + envelope.userId() + ":" + CoreEnvelopeCodec.CORE_SYNC_RESPONSE_NAME,
						core.currentMember().map(org.q3s.p2p.core.model.Member::memberId).orElse(""),
						responsePayload));
			}
		} catch (Exception e) {
			debug.accept("Sync request error: " + e.getMessage());
		}
	}

	private void handleSyncResponse(CoreEnvelope envelope, String localUserId, boolean[] welcomed) {
		try {
			var events = CoreEnvelopeCodec.decodeSyncEvents(envelope);
			int accepted = core.receiveRemoteEvents(events);
			debug.accept("Bootstrap sync: " + accepted + " eventos recibidos");
			mergeKnownPeers();
			welcomeIfAuthorized(localUserId, welcomed);
		} catch (Exception e) {
			debug.accept("Sync response error: " + e.getMessage());
		}
	}

	private void welcomeIfAuthorized(String localUserId, boolean[] welcomed) {
		if (welcomed[0]) return;
		try {
			WorkspaceState state = core.currentState();
			if (state.isAuthorized(localUserId)) {
				welcomed[0] = true;
				onWelcome.run();
			}
		} catch (Exception ignored) {}
	}

	private void mergeKnownPeers() {
		try {
			if (mesh != null) {
				mesh.mergePeerState(core.currentState());
				mesh.publishPeerStatusIfChanged();
			}
		} catch (Exception e) {
			debug.accept("No se pudo fusionar peers del bootstrap: " + e.getMessage());
		}
	}

	private String webSocketUriForEndpoint(String endpoint) {
		if (endpoint == null) return "";
		String value = endpoint.trim();
		if (value.startsWith("ws://") || value.startsWith("wss://")) return value;
		if (value.endsWith(".trycloudflare.com")) return "wss://" + value;
		return "ws://" + value;
	}
}
