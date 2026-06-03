package org.q3s.p2p.client.ws;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.q3s.p2p.client.util.Logger;
import org.q3s.p2p.core.codec.CoreEnvelope;

public class WsClient extends WebSocketClient {

	private final Logger log;
	private final Consumer<CoreEnvelope> onEvent;
	private final Consumer<String> onError;
	private final Runnable onClose;
	private final boolean callbacksOnEdt;
	private final ExecutorService callbackExecutor;
	private final java.util.concurrent.atomic.AtomicBoolean closeNotified = new java.util.concurrent.atomic.AtomicBoolean(false);
	private volatile boolean closed;

	public WsClient(URI uri, Logger log, Consumer<CoreEnvelope> onEvent, Consumer<String> onError,
			Runnable onClose) {
		this(uri, log, onEvent, onError, onClose, true);
	}

	public WsClient(URI uri, Logger log, Consumer<CoreEnvelope> onEvent, Consumer<String> onError,
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
		CoreEnvelope envelope = CoreEnvelope.fromJsonBase64(message);
		if (envelope != null && onEvent != null) {
			runCallback(() -> onEvent.accept(envelope));
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

	public void sendEnvelope(CoreEnvelope envelope) {
		if (isOpen()) {
			try {
				send(envelope.toJsonBase64());
			} catch (Exception e) {
				if (log != null) log.err("Error sending envelope: " + e.getMessage());
				callCloseCallback();
			}
		} else {
			if (log != null) log.err("Error sending envelope: WebSocket is not open");
			callCloseCallback();
		}
	}

	private void callCloseCallback() {
		if (onClose != null && closeNotified.compareAndSet(false, true)) {
			onClose.run();
		}
	}

	private void runCallback(Runnable r) {
		if (closed) return;
		if (callbacksOnEdt) javax.swing.SwingUtilities.invokeLater(r);
		else try { callbackExecutor.execute(r); } catch (RejectedExecutionException ignored) {}
	}
}
