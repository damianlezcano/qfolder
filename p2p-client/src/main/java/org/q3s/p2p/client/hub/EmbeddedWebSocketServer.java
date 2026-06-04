package org.q3s.p2p.client.hub;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.q3s.p2p.core.codec.CoreEnvelope;

public class EmbeddedWebSocketServer extends WebSocketServer {

	private final Runnable onStarted;
 	private final BiConsumer<WebSocket, CoreEnvelope> directMessageHandler;
	private final Consumer<String> onPeerDisconnected;

	public EmbeddedWebSocketServer(int port, Runnable onStarted) {
		this(port, onStarted, null, null);
	}

	public EmbeddedWebSocketServer(int port, Runnable onStarted, BiConsumer<WebSocket, CoreEnvelope> directMessageHandler) {
		this(port, onStarted, directMessageHandler, null);
	}

	public EmbeddedWebSocketServer(int port, Runnable onStarted, BiConsumer<WebSocket, CoreEnvelope> directMessageHandler,
			Consumer<String> onPeerDisconnected) {
		super(new InetSocketAddress(port));
		this.onStarted = onStarted;
		this.directMessageHandler = directMessageHandler;
		this.onPeerDisconnected = onPeerDisconnected;
		setReuseAddr(true);
		setConnectionLostTimeout(25);
	}

	@Override
	public void onStart() {
		if (onStarted != null) {
			onStarted.run();
		}
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		String query = handshake.getResourceDescriptor();
		debug("onOpen: query=" + query);
		String wkId = urlDecode(getParam(query, "wkId"));
		String userId = getParam(query, "userId");
		boolean direct = "true".equals(getParam(query, "direct"));
		boolean failover = "true".equals(getParam(query, "failover"));
		debug("wkId=" + wkId + " userId=" + userId);

		if (wkId != null && userId != null) {
			conn.setAttachment(new SessionInfo(wkId, userId, direct, failover));
		} else {
			err("wkId or userId is null, closing connection");
			conn.close();
		}
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		SessionInfo si = conn.getAttachment();
		if (si != null && si.direct && onPeerDisconnected != null && si.userId != null) {
			onPeerDisconnected.accept(si.userId);
		}
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		SessionInfo si = conn.getAttachment();
		if (si == null)
			return;

		try {
			CoreEnvelope envelope = CoreEnvelope.fromJsonBase64(message);
			if (envelope == null || envelope.name() == null) {
				err("invalid envelope: " + message.substring(0, Math.min(50, message.length())));
				return;
			}

			String name = envelope.name();
			debug("onMessage: " + name);

			if (directMessageHandler != null) {
				directMessageHandler.accept(conn, envelope);
			}
		} catch (Exception e) {
			err("onMessage error: " + e.getMessage());
		}
	}

	@Override
	public void onMessage(WebSocket conn, ByteBuffer message) {
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		err("onError: " + (ex != null ? ex.getMessage() : "unknown"));
		if (conn != null) {
			try { conn.close(); } catch (Exception ignored) {}
		}
	}

	public void shutdown() {
		try {
			stop(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String getParam(String query, String param) {
		if (query == null)
			return null;
		int qIdx = query.indexOf('?');
		String qs = qIdx >= 0 ? query.substring(qIdx + 1) : query;
		for (String pair : qs.split("&")) {
			String[] kv = pair.split("=", 2);
			if (kv.length == 2 && kv[0].equals(param)) {
				return kv[1];
			}
		}
		return null;
	}

	private String urlDecode(String value) {
		if (value == null)
			return null;
		try {
			return java.net.URLDecoder.decode(value, "UTF-8");
		} catch (Exception e) {
			return value;
		}
	}

	private void debug(String msg) {
	}

	private void err(String msg) {
		java.util.logging.Logger.getLogger("qfolder.WsServer").warning(msg);
	}

	public static class SessionInfo {
		final String wkId;
		final String userId;
		final boolean direct;
		final boolean failover;

		SessionInfo(String wkId, String userId, boolean direct, boolean failover) {
			this.wkId = wkId;
			this.userId = userId;
			this.direct = direct;
			this.failover = failover;
		}
	}
}
