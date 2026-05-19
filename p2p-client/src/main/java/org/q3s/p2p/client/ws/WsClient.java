package org.q3s.p2p.client.ws;

import java.net.URI;
import java.util.function.Consumer;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.q3s.p2p.client.util.Logger;
import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.util.EventUtils;

public class WsClient extends WebSocketClient {

	private final Logger log;
	private final Consumer<Event> onEvent;
	private final Consumer<String> onError;
	private final Runnable onClose;

	public WsClient(URI uri, Logger log, Consumer<Event> onEvent, Consumer<String> onError,
			Runnable onClose) {
		super(uri);
		this.log = log;
		this.onEvent = onEvent;
		this.onError = onError;
		this.onClose = onClose;
	}

	@Override
	public void onOpen(ServerHandshake handshake) {
		log.debug("WebSocket connected: " + handshake.getHttpStatus());
	}

	@Override
	public void onMessage(String message) {
		Event event = (Event) EventUtils.toObjectBase64(message, Event.class);
		if (event != null) {
			javax.swing.SwingUtilities.invokeLater(() -> onEvent.accept(event));
		}
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		log.debug("WebSocket closed: " + code + " " + reason);
		if (onClose != null) {
			javax.swing.SwingUtilities.invokeLater(onClose);
		}
	}

	@Override
	public void onError(Exception ex) {
		log.err("WebSocket error: " + ex.getMessage());
		if (onError != null) {
			javax.swing.SwingUtilities.invokeLater(() -> onError.accept(ex.getMessage()));
		}
	}

	public void sendEvent(Event event) {
		if (isOpen()) {
			try {
				send(EventUtils.toJsonBase64(event));
			} catch (Exception e) {
				log.err("Error sending event: " + e.getMessage());
			}
		}
	}
}
