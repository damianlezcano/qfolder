package org.q3s.p2p.client.ws;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
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
	private final boolean callbacksOnEdt;
	private final ExecutorService callbackExecutor;
	private volatile boolean closeNotified;
	private volatile boolean closed;

	public WsClient(URI uri, Logger log, Consumer<Event> onEvent, Consumer<String> onError,
			Runnable onClose) {
		this(uri, log, onEvent, onError, onClose, true);
	}

	public WsClient(URI uri, Logger log, Consumer<Event> onEvent, Consumer<String> onError,
			Runnable onClose, boolean callbacksOnEdt) {
		super(uri);
		this.log = log;
		this.onEvent = onEvent;
		this.onError = onError;
		this.onClose = onClose;
		this.callbacksOnEdt = callbacksOnEdt;
		this.callbackExecutor = callbacksOnEdt ? null : Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "ws-callbacks");
			t.setDaemon(true);
			return t;
		});
	}

	@Override
	public void onOpen(ServerHandshake handshake) {
		if (log != null) log.debug("WebSocket connected: " + handshake.getHttpStatus());
	}

	@Override
	public void onMessage(String message) {
		Event event = (Event) EventUtils.toObjectBase64(message, Event.class);
		if (event != null && onEvent != null) {
			runCallback(() -> onEvent.accept(event));
		}
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		if (log != null) log.debug("WebSocket closed: " + code + " " + reason);
		callCloseCallback();
		closed = true;
		if (callbackExecutor != null) callbackExecutor.shutdownNow();
	}

	@Override
	public void onError(Exception ex) {
		if (log != null) log.err("WebSocket error: " + ex.getMessage());
		if (onError != null) {
			runCallback(() -> onError.accept(ex.getMessage()));
		}
	}

	public void sendEvent(Event event) {
		if (isOpen()) {
			try {
				send(EventUtils.toJsonBase64(event));
			} catch (Exception e) {
				if (log != null) log.err("Error sending event: " + e.getMessage());
				callCloseCallback();
			}
		} else {
			if (log != null) log.err("Error sending event: WebSocket is not open");
			callCloseCallback();
		}
	}

	private void callCloseCallback() {
		if (onClose != null && !closeNotified) {
			closeNotified = true;
			onClose.run();
		}
	}

	private void runCallback(Runnable r) {
		if (closed) return;
		if (callbacksOnEdt) javax.swing.SwingUtilities.invokeLater(r);
		else try { callbackExecutor.execute(r); } catch (RejectedExecutionException ignored) {}
	}
}
