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
    
    public void verify() {
    	//System.out.println("# verify[1] ->  approved: " + approved.size() + " - inProgress: " + inProgress.size());
    	LinkedHashMap<User,Workspace> toRemove = new LinkedHashMap<User,Workspace>();
        for (Entry<Workspace, LinkedHashMap<User, SseEventSink>> we : approved.entrySet()) {
        	Workspace wk = we.getKey();
        	LinkedHashMap<User, SseEventSink> ss = we.getValue();
			for (Entry<User, SseEventSink> user : ss.entrySet()) {
				User us = user.getKey();
				SseEventSink sink = user.getValue();
				//System.out.println(">> wk: " + wk + " - user: " + us + " - ss: " + ss.size() + " - isClosed: " + sink.isClosed());
				try {
					sink.send(buildEmptyEvent());					
				} catch (Exception e) {
					sendToWk(wk.getId(),new Event("Usuario desconectado",us));
					toRemove.put(us, wk);
				}
			}
		}
        for (Entry<User, Workspace> entry : toRemove.entrySet()) {
            User user = entry.getKey();
            Workspace wk = entry.getValue();
            approved.get(wk).remove(user);   
        }
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
        Workspace wk = getWorkspaceInApprobalById(wkId);
        User user = User.build(userId);
        
        SseEventSink es = getEmitterApprovalByUserId(wk, userId);
        
        if(es == null) {
	        this.inProgress.put(user, emitter);
	
	        if (wk != null) {
	            if (wk.requiredCredential()) {
	                send(emitter,buildResponse(new Event("Wk existente, con credenciales")));
	            } else {
	            	send(emitter,buildResponse(new Event("Wk existente, sin credenciales")));
	            }
	        } else {
	        	send(emitter,buildResponse(new Event("Wk no existe, desconectar")));
	        }
        }else {
        	LOG.info("# reconect user: " + userId + "en wk: " + wkId);
        	LinkedHashMap<User, SseEventSink> wkl = approved.get(wk);
        	wkl.put(user, emitter);
        }
        
        System.out.println("# verify[2] ->  approved: " + approved.size() + " - inProgress: " + inProgress.size());
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
                send(emitter,buildResponse(new Event("Aprobar al usuario", user)));
            }
        }
    }

    public void withAuth(String wkId, User user) throws IOException {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        SseEventSink emitter = getEmitterInProgressByUserId(user.getId());
        if (wk.getPassword().equals(user.getPassword())) {
            sendWelcome(wk, user, emitter);
        } else {
        	send(emitter,buildResponse(new Event("Credenciales incorrectas")));
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
        send(emitter,buildResponse(new Event("Usuario rechazado!")));
        inProgress.remove(user);
    }

    public void sendToUser(String wkId, String userId, Event event) throws IOException {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        SseEventSink emitter = getEmitterApprovalByUserId(wk, userId);
        send(emitter,buildResponse(event));
    }

    void sendToWk(String wkId, Event event) {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        approved.get(wk).values().forEach(emitter -> {
        	send(emitter,buildResponse(event));
		});
    }

    //------------------------------------------------------------------------------------------
    
    private void sendWelcome(Workspace wk, User user, SseEventSink emitter) throws IOException {
    	send(emitter,buildResponse(new Event("Bienvenido usuario al grupo!", wk)));
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
        LinkedHashMap<User, SseEventSink> ap = approved.get(wk);
        if(ap != null) {
		    for (Entry<User, SseEventSink> entry : ap.entrySet()) {
		        if (user.equals(entry.getKey())) {
		            return entry.getValue();
		        }
		    }
        }
        return null;
    }

    public void disposeApproval(String wkId, String userId) {
    	LOG.info("disposeApproval: " + userId);
        Workspace wk = getWorkspaceInApprobalById(wkId);
        User user = User.build(userId);
        this.approved.get(wk).remove(user);
    }

//    public void disposeInProgress(String userId) {
    public void disposeInProgress() {
    	LOG.info("disposeInProgress: ");
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
    
    private void send(SseEventSink emitter, OutboundSseEvent event) {
    	if(!emitter.isClosed()) {
    		emitter.send(event);
    	}else {
    		LOG.error("# No es posible enviar el evento, la conexion SSE esta cerrada -> " + emitter);
    	}
	}

    private OutboundSseEvent buildEmptyEvent() {
		return buildEvent(":");
	}
    
    private OutboundSseEvent buildResponse(Event event) {
        return buildEvent(event.toJsonBase64());
    }
    
    private OutboundSseEvent buildEvent(String message) {
    	String id = String.valueOf(System.currentTimeMillis());
        OutboundSseEvent sseEvent = this.eventBuilder
                .name("message")
                .id(id)
                .mediaType(MediaType.TEXT_PLAIN_TYPE)
                .data(message)
                .reconnectDelay(3000)
                .comment("Event type is ")
                .build();
        return sseEvent;
    }

}
