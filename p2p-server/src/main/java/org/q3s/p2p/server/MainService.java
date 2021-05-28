package org.q3s.p2p.server;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.UUID;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;

import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.User;
import org.q3s.p2p.model.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//@Singleton
public class MainService {

    private static final Logger LOG = LoggerFactory.getLogger(MainService.class);

    private LinkedHashMap<User, SseEventSink> inProgress = new LinkedHashMap<User, SseEventSink>();

    private LinkedHashMap<Workspace, LinkedHashMap<User, SseEventSink>> approved = new LinkedHashMap<Workspace, LinkedHashMap<User, SseEventSink>>();
    
    private OutboundSseEvent.Builder eventBuilder;
    
    private static MainService instance;
    
    public static MainService getInstance(Sse sse) {
    	if(instance == null) {
    		instance = new MainService(sse);
    	}
    	return instance;
    }

    private MainService(Sse sse) {
    	this.eventBuilder = sse.newEventBuilder();
    }
    
    public String create(String name, String password, Long date) {
        String uuid = UUID.randomUUID().toString();
        Workspace wk = Workspace.build(uuid);
        wk.setDate(date);
        wk.setName(name);
        wk.setPassword(password);
        approved.put(wk, new LinkedHashMap<User, SseEventSink>());
        return uuid;
    }

    public void connect(SseEventSink emitter, String wkId, String userId) throws IOException {

        LOG.info("# connect -> " + userId + " - " + emitter);

        Workspace wk = getWorkspaceInApprobalById(wkId);
        User user = User.build(userId);

        this.inProgress.put(user, emitter);

        if (wk != null) {
            if (wk.requiredCredential()) {
                emitter.send(buildResponse(new Event("Wk existente, con credenciales")));
            } else {
                emitter.send(buildResponse(new Event("Wk existente, sin credenciales")));
            }
        } else {
            emitter.send(buildResponse(new Event("Wk no existe, desconectar")));
        }

    }

    public void withoutAuth(String wkId, User user) throws IOException {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        Collection<SseEventSink> list = approved.get(wk).values();
        if (list.isEmpty()) {
            SseEventSink emitter = getEmitterInProgressByUserId(user.getId());
            LOG.info("# withoutAuth[1] -> " + user.getId() + " - " + emitter);
            sendWelcome(wk, user, emitter);
        } else {
            for (SseEventSink emitter : approved.get(wk).values()) {
                LOG.info("# withoutAuth[2] -> " + user.getId() + " - " + emitter);
                User us = getUserInProgress(user.getId());
                us.copy(user);
                emitter.send(buildResponse(new Event("Aprobar al usuario", user)));
            }
        }
    }

    public void withAuth(String wkId, User user) throws IOException {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        SseEventSink emitter = getEmitterInProgressByUserId(user.getId());
        if (wk.getPassword().equals(user.getPassword())) {
            sendWelcome(wk, user, emitter);
        } else {
            emitter.send(buildResponse(new Event("Credenciales incorrectas")));
        }
    }

    public void approved(String wkId, String userId) throws IOException {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        User user = User.build(userId);
        SseEventSink emitter = inProgress.get(user);
        sendWelcome(wk, user, emitter);
    }

    public void refuse(String wkId, String userId) throws IOException {
        User user = User.build(userId);
        SseEventSink emitter = inProgress.get(user);
        emitter.send(buildResponse(new Event("Usuario rechazado!")));
        inProgress.remove(user);
    }

    public void sendToUser(String wkId, String userId, Event event) throws IOException {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        SseEventSink emitter = getEmitterApprovalByUserId(wk, userId);
        emitter.send(buildResponse(event));
    }

    void sendToWk(String wkId, Event event) {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        approved.get(wk).values().forEach(emitter -> {
        	emitter.send(buildResponse(event));
		});
    }

    //------------------------------------------------------------------------------------------
    private void sendWelcome(Workspace wk, User user, SseEventSink emitter) throws IOException {
        emitter.send(buildResponse(new Event("Bienvenido usuario al grupo!", wk)));
        approved.get(wk).put(user, emitter);
        inProgress.remove(user);
    }

    private Workspace getWorkspaceInApprobalById(String wkId) {
        Workspace wkf = Workspace.build(wkId);
        for (Workspace wkt : approved.keySet()) {
            if (wkf.equals(wkt)) {
                return wkt;
            }
        }
        return null;
    }

    private SseEventSink getEmitterInProgressByUserId(String userId) {
        User user = User.build(userId);
        for (Entry<User, SseEventSink> entry : inProgress.entrySet()) {
            if (user.equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private SseEventSink getEmitterApprovalByUserId(Workspace wk, String userId) {
        User user = User.build(userId);
        for (Entry<User, SseEventSink> entry : approved.get(wk).entrySet()) {
            if (user.equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public void disposeApproval(String wkId, String userId) {
        System.out.println("disposeApproval: " + userId);
        Workspace wk = getWorkspaceInApprobalById(wkId);
        User user = User.build(userId);
        this.approved.get(wk).remove(user);
    }

//    public void disposeInProgress(String userId) {
    public void disposeInProgress() {
        System.out.println("disposeInProgress: ");
//        User user = User.build(userId);
//        this.inProgress.remove(user);
    }

    private User getUserInProgress(String userId) {
        User user = User.build(userId);
        for (Entry<User, SseEventSink> entry : inProgress.entrySet()) {
            if (user.equals(entry.getKey())) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    private OutboundSseEvent buildResponse(Event event) {
    	String id = String.valueOf(System.currentTimeMillis());
        OutboundSseEvent sseEvent = this.eventBuilder
                .name("message")
                .id(id)
                .mediaType(MediaType.TEXT_PLAIN_TYPE)
                .data(event.toJsonBase64())
                .reconnectDelay(3000)
                .comment("Event type is ")
                .build();
        return sseEvent;
    }

}
