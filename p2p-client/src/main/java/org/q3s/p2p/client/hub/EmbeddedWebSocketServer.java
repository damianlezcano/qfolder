package org.q3s.p2p.client.hub;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.util.EventUtils;

public class EmbeddedWebSocketServer extends WebSocketServer {

	private final WsHubService hubService;
	private final ScheduledExecutorService scheduler;
	private final Runnable onStarted;
 	private final BiConsumer<WebSocket, Event> directMessageHandler;

	public EmbeddedWebSocketServer(int port, Runnable onStarted) {
		this(port, onStarted, null);
	}

	public EmbeddedWebSocketServer(int port, Runnable onStarted, BiConsumer<WebSocket, Event> directMessageHandler) {
		super(new InetSocketAddress(port));
		this.hubService = new WsHubService();
		this.onStarted = onStarted;
		this.directMessageHandler = directMessageHandler;
		this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "ws-verify");
			t.setDaemon(true);
			return t;
		});
		setReuseAddr(true);
	}

	@Override
	public void onStart() {
		if (onStarted != null) {
			onStarted.run();
		}
		scheduler.scheduleAtFixedRate(() -> hubService.verify(), 5, 5, TimeUnit.SECONDS);
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
			if (!direct) {
				hubService.connect(conn, wkId, userId, failover);
			}
		} else {
			err("wkId or userId is null, closing connection");
			conn.close();
		}
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		SessionInfo si = conn.getAttachment();
		if (si != null && !si.direct) {
			hubService.dispose(conn, si.wkId, si.userId);
		}
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		SessionInfo si = conn.getAttachment();
		if (si == null)
			return;

		try {
			Event event = (Event) EventUtils.toObjectBase64(message, Event.class);
			if (event == null || event.getName() == null) {
				err("invalid event: " + message.substring(0, Math.min(50, message.length())));
				return;
			}

			String name = event.getName();
			debug("onMessage: " + name);

			if (si.direct) {
				if (directMessageHandler != null) {
					directMessageHandler.accept(conn, event);
				}
				return;
			}

			if (name.startsWith("__hub:")) {
				handleHubCommand(conn, si, name, event);
			} else if (name.startsWith("__to:")) {
				String rest = name.substring(5);
				int colonIdx = rest.indexOf(':');
				if (colonIdx > 0) {
					String targetUserId = rest.substring(0, colonIdx);
					String actualName = rest.substring(colonIdx + 1);
					event.setName(actualName);
					hubService.sendToUser(si.wkId, targetUserId, event);
				}
			} else {
				hubService.sendToWk(si.wkId, event);
			}
		} catch (Exception e) {
			err("onMessage error: " + e.getMessage());
		}
	}

	private void handleHubCommand(WebSocket conn, SessionInfo si, String name, Event event) {
		if (name.equals("__hub:withoutAuth")) {
			hubService.withoutAuth(si.wkId, event.getUser(), si.failover);
		} else if (name.equals("__hub:withAuth")) {
			hubService.withAuth(si.wkId, event.getUser());
		} else if (name.startsWith("__hub:approved:")) {
			String targetUserId = name.substring(15);
			hubService.approved(si.wkId, targetUserId);
		} else if (name.startsWith("__hub:refuse:")) {
			String targetUserId = name.substring(13);
			hubService.refuse(si.wkId, targetUserId);
		}
	}

	@Override
	public void onMessage(WebSocket conn, ByteBuffer message) {
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
	}

	public WsHubService getHubService() {
		return hubService;
	}

	public void shutdown() {
		scheduler.shutdown();
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
		System.out.println("[WsServer] " + msg);
	}

	private void err(String msg) {
		System.err.println("[WsServer] " + msg);
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
