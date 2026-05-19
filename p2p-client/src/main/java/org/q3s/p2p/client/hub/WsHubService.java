package org.q3s.p2p.client.hub;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.java_websocket.WebSocket;
import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.User;
import org.q3s.p2p.model.Workspace;
import org.q3s.p2p.model.util.EventUtils;

public class WsHubService {

	private final LinkedHashMap<User, WebSocket> inProgress = new LinkedHashMap<>();
	private final LinkedHashMap<Workspace, LinkedHashMap<User, WebSocket>> approved = new LinkedHashMap<>();

	public synchronized void create(String wkId, String name, String password, Long date) {
		Workspace existing = getWorkspaceInApprovalById(wkId);
		if (existing != null) {
			existing.setName(name);
			existing.setDate(date != null ? date : existing.getDate());
			existing.setPassword(password);
			debug("workspace already exists, preserving users=" + approved.get(existing).size());
			return;
		}
		Workspace wk = new Workspace(wkId, name);
		wk.setDate(date);
		wk.setPassword(password);
		approved.put(wk, new LinkedHashMap<>());
	}

	public synchronized void connect(WebSocket conn, String wkId, String userId, boolean failover) {
		debug("connect: wkId=" + wkId + " userId=" + userId);
		Workspace wk = getWorkspaceInApprovalById(wkId);
		User user = User.build(userId);
		debug("wk found: " + (wk != null) + " approvedCount=" + (wk != null ? approved.get(wk).size() : 0));

		WebSocket es = getEmitterApprovalByUserId(wk, userId);

		if (es == null) {
			inProgress.put(user, conn);
			debug("user in progress. closed=" + conn.isClosed());

			if (wk != null) {
				boolean isEmpty = approved.get(wk).isEmpty();
				if (failover) {
					debug("sending failover without credentials");
					send(conn, new Event("Wk existente, sin credenciales"));
				} else if (wk.requiredCredential() && !isEmpty) {
					debug("sending 'con credenciales'");
					send(conn, new Event("Wk existente, con credenciales"));
				} else {
					debug("sending 'sin credenciales'");
					send(conn, new Event("Wk existente, sin credenciales"));
				}
			} else {
				debug("sending 'no existe'");
				send(conn, new Event("Wk no existe, desconectar"));
			}
		} else {
			LinkedHashMap<User, WebSocket> wkl = approved.get(wk);
			wkl.put(user, conn);
			debug("reconnected existing user");
		}
	}

	public synchronized void withoutAuth(String wkId, User user) {
		withoutAuth(wkId, user, false);
	}

	public synchronized void withoutAuth(String wkId, User user, boolean failover) {
		Workspace wk = getWorkspaceInApprovalById(wkId);
		if (wk == null) {
			debug("withoutAuth: wk not found for " + wkId);
			return;
		}
		LinkedHashMap<User, WebSocket> map = approved.get(wk);
		if (failover || map == null || map.isEmpty()) {
			WebSocket emitter = getEmitterInProgressByUserId(user.getId());
			if (emitter != null) {
				sendWelcome(wk, user, emitter);
			} else {
				debug("withoutAuth: emitter not found for " + user.getId());
			}
		} else {
			User us = getUserInProgress(user.getId());
			if (us != null) us.copy(user);
			for (WebSocket emitter : map.values()) {
				send(emitter, new Event("Aprobar al usuario", user));
			}
		}
	}

	public synchronized void withAuth(String wkId, User user) {
		Workspace wk = getWorkspaceInApprovalById(wkId);
		if (wk == null) return;
		WebSocket emitter = getEmitterInProgressByUserId(user.getId());
		if (emitter != null && wk.getPassword() != null && wk.getPassword().equals(user.getPassword())) {
			sendWelcome(wk, user, emitter);
		} else if (emitter != null) {
			send(emitter, new Event("Credenciales incorrectas"));
		}
	}

	public synchronized void approved(String wkId, String userId) {
		Workspace wk = getWorkspaceInApprovalById(wkId);
		if (wk == null) return;
		User user = User.build(userId);
		WebSocket emitter = inProgress.get(user);
		sendApprovalResolved(wk, userId, "approved");
		sendWelcome(wk, user, emitter);
	}

	public synchronized void refuse(String wkId, String userId) {
		User user = User.build(userId);
		WebSocket emitter = inProgress.get(user);
		Workspace wk = getWorkspaceInApprovalById(wkId);
		if (wk != null) sendApprovalResolved(wk, userId, "refused");
		send(emitter, new Event("Usuario rechazado!"));
		inProgress.remove(user);
	}

	private void sendApprovalResolved(Workspace wk, String userId, String decision) {
		LinkedHashMap<User, WebSocket> map = approved.get(wk);
		if (map == null) return;
		Event event = new Event("Solicitud de ingreso resuelta", User.build(userId), decision);
		for (WebSocket emitter : map.values()) {
			send(emitter, event);
		}
	}

	public synchronized void sendToUser(String wkId, String userId, Event event) {
		Workspace wk = getWorkspaceInApprovalById(wkId);
		WebSocket emitter = getEmitterApprovalByUserId(wk, userId);
		send(emitter, event);
	}

	public synchronized void sendToWk(String wkId, Event event) {
		Workspace wk = getWorkspaceInApprovalById(wkId);
		LinkedHashMap<User, WebSocket> map = wk != null ? approved.get(wk) : null;
		if (map != null) {
			debug("broadcast '" + event.getName() + "' to " + map.size() + " users");
			for (WebSocket emitter : map.values()) {
				send(emitter, event);
			}
		} else {
			debug("broadcast skipped, workspace not found: " + wkId + " event=" + event.getName());
		}
	}

	public synchronized void dispose(WebSocket conn, String wkId, String userId) {
		Workspace wk = getWorkspaceInApprovalById(wkId);
		User user = User.build(userId);
		if (wk != null && approved.containsKey(wk)) {
			approved.get(wk).remove(user);
		}
		inProgress.remove(user);
		sendToWk(wkId, new Event("Usuario desconectado", user));
	}

	public synchronized void verify() {
		LinkedHashMap<User, Workspace> toRemove = new LinkedHashMap<>();
		for (Entry<Workspace, LinkedHashMap<User, WebSocket>> we : approved.entrySet()) {
			Workspace wk = we.getKey();
			for (Entry<User, WebSocket> entry : we.getValue().entrySet()) {
				User us = entry.getKey();
				WebSocket conn = entry.getValue();
				if (conn.isClosed()) {
					sendToWk(wk.getId(), new Event("Usuario desconectado", us));
					toRemove.put(us, wk);
				} else {
					try {
						conn.sendPing();
					} catch (Exception e) {
						toRemove.put(us, wk);
					}
				}
			}
		}
		for (Entry<User, Workspace> entry : toRemove.entrySet()) {
			User user = entry.getKey();
			Workspace wk = entry.getValue();
			approved.get(wk).remove(user);
		}
	}

	private void sendWelcome(Workspace wk, User user, WebSocket conn) {
		if (wk == null || user == null || conn == null) return;
		LinkedHashMap<User, WebSocket> map = approved.get(wk);
		if (map != null) {
			map.put(user, conn);
		}
		inProgress.remove(user);
		send(conn, new Event("Bienvenido usuario al grupo!", wk));
		debug("sendWelcome done, users=" + (map != null ? map.size() : 0) + " workspaces=" + approved.size());
	}

	private Workspace getWorkspaceInApprovalById(String wkId) {
		Workspace wkf = Workspace.build(wkId);
		for (Workspace wkt : approved.keySet()) {
			if (wkf.equals(wkt)) {
				return wkt;
			}
		}
		if (approved.size() == 1) {
			Workspace only = approved.keySet().iterator().next();
			debug("workspace fallback: requested=" + wkId + " using=" + only.getId());
			return only;
		}
		return null;
	}

	private WebSocket getEmitterInProgressByUserId(String userId) {
		User user = User.build(userId);
		for (Entry<User, WebSocket> entry : inProgress.entrySet()) {
			if (user.equals(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	private WebSocket getEmitterApprovalByUserId(Workspace wk, String userId) {
		User user = User.build(userId);
		LinkedHashMap<User, WebSocket> map = approved.get(wk);
		if (map != null) {
			for (Entry<User, WebSocket> entry : map.entrySet()) {
				if (user.equals(entry.getKey())) {
					return entry.getValue();
				}
			}
		}
		return null;
	}

	private User getUserInProgress(String userId) {
		User user = User.build(userId);
		for (Entry<User, WebSocket> entry : inProgress.entrySet()) {
			if (user.equals(entry.getKey())) {
				return entry.getKey();
			}
		}
		return null;
	}

	private void send(WebSocket conn, Event event) {
		if (conn != null && !conn.isClosed()) {
			try {
				String data = EventUtils.toJsonBase64(event);
				conn.send(data);
				debug("sent: " + event.getName());
			} catch (Exception e) {
				err("send error: " + e.getMessage());
			}
		} else {
			debug("send skipped: conn=" + conn + " closed=" + (conn != null && conn.isClosed()));
		}
	}

	private void debug(String msg) {
		System.out.println("[Hub] " + msg);
	}

	private void err(String msg) {
		System.err.println("[Hub] " + msg);
	}
}
