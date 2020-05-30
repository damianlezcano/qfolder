package com.example.pushserver;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Consumer;
import org.q3s.p2p.model.Event;

import org.q3s.p2p.model.User;
import org.q3s.p2p.model.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(MessageProcessor.class);

    private LinkedHashMap<User, Consumer<String>> inProgress = new LinkedHashMap<User, Consumer<String>>();

    private LinkedHashMap<Workspace, LinkedHashMap<User, Consumer<String>>> approved = new LinkedHashMap<Workspace, LinkedHashMap<User, Consumer<String>>>();
    
    private LinkedHashMap<Workspace, LinkedHashMap<User, Long>> pong = new LinkedHashMap<Workspace, LinkedHashMap<User, Long>>();
    
    @Scheduled(fixedDelay = 1000)
    public void pong() {
        Long current = System.currentTimeMillis();
        Iterator<Entry<Workspace, LinkedHashMap<User, Long>>> iwk = pong.entrySet().iterator();
        LinkedHashMap<User,Workspace> toRemove = new LinkedHashMap<User,Workspace>();
        while (iwk.hasNext()) {
            Entry<Workspace, LinkedHashMap<User, Long>> ewk = iwk.next();
            Iterator<Entry<User, Long>> iuser = ewk.getValue().entrySet().iterator();
            while (iuser.hasNext()) {
                Entry<User, Long> euser = iuser.next();
                Workspace wk = ewk.getKey();
                User user = euser.getKey();
                Long date = euser.getValue();
                if(current > date){
                    sendToWk(wk.getId(),new Event("Usuario desconectado",user));
                    toRemove.put(user, wk);
                }
            }
        }
        for (Entry<User, Workspace> entry : toRemove.entrySet()) {
            User user = entry.getKey();
            Workspace wk = entry.getValue();
            pong.get(wk).remove(user);
            approved.get(wk).remove(user);   
        }
    }

    public String create(String name, String password, Long date) {
        String uuid = UUID.randomUUID().toString();
        Workspace wk = Workspace.build(uuid);
        wk.setDate(date);
        wk.setName(name);
        wk.setPassword(password);
        approved.put(wk, new LinkedHashMap<User, Consumer<String>>());
        return uuid;
    }

    public void connect(Consumer<String> emitter, String wkId, String userId) {

        LOG.info("# connect -> " + userId + " - " + emitter);

        Workspace wk = getWorkspaceInApprobalById(wkId);
        User user = User.build(userId);

        this.inProgress.put(user, emitter);

        if (wk != null) {
            if (wk.requiredCredential()) {
                emitter.accept(new Event("Wk existente, con credenciales").toJsonBase64());
            } else {
                emitter.accept(new Event("Wk existente, sin credenciales").toJsonBase64());
            }
        } else {
            emitter.accept(new Event("Wk no existe, desconectar").toJsonBase64());
        }

    }

    public void reconnect(Consumer<String> emitter, String wkId, String userId) {
        LOG.info("# reconnect -> " + userId + " - " + emitter);

        Workspace wk = getWorkspaceInApprobalById(wkId);
        User user = User.build(userId);

        if (this.approved.get(wk).containsKey(user)) {
            this.approved.get(wk).put(user, emitter);
        }
    }

    public void withoutAuth(String wkId, User user) throws IOException {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        Collection<Consumer<String>> list = approved.get(wk).values();
        if (list.isEmpty()) {
            Consumer<String> emitter = getEmitterInProgressByUserId(user.getId());
            LOG.info("# withoutAuth -> " + user.getId() + " - " + emitter);
            sendWelcome(wk, user, emitter);
        } else {
            for (Consumer<String> emitter : approved.get(wk).values()) {
                LOG.info("# withoutAuth -> " + user.getId() + " - " + emitter);
                User us = getUserInProgress(user.getId());
                us.copy(user);
                emitter.accept(new Event("Aprobar al usuario", user).toJsonBase64());
            }
        }
    }

    public void withAuth(String wkId, User user) throws IOException {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        Consumer<String> emitter = getEmitterInProgressByUserId(user.getId());
        if (wk.getPassword().equals(user.getPassword())) {
            sendWelcome(wk, user, emitter);
        } else {
            emitter.accept(new Event("Credenciales incorrectas").toJsonBase64());
        }
    }

    public void approved(String wkId, String userId) throws IOException {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        User user = User.build(userId);
        Consumer<String> emitter = inProgress.get(user);
        sendWelcome(wk, user, emitter);
    }

    public void refuse(String wkId, String userId) {
        User user = User.build(userId);
        Consumer<String> emitter = inProgress.get(user);
        emitter.accept(new Event("Usuario rechazado!").toJsonBase64());
        inProgress.remove(user);
    }

    public void sendToUser(String wkId, String userId, Event event) {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        Consumer<String> emitter = getEmitterApprovalByUserId(wk, userId);
        emitter.accept(event.toJsonBase64());
    }

    void sendToWk(String wkId, Event event) {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        approved.get(wk).values().forEach(emitter -> emitter.accept(event.toJsonBase64()));
    }

    //------------------------------------------------------------------------------------------
    private void sendWelcome(Workspace wk, User user, Consumer<String> emitter) throws IOException {
        emitter.accept(new Event("Bienvenido usuario al grupo!", wk).toJsonBase64());
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

    private Consumer<String> getEmitterInProgressByUserId(String userId) {
        User user = User.build(userId);
        for (Entry<User, Consumer<String>> entry : inProgress.entrySet()) {
            if (user.equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Consumer<String> getEmitterApprovalByUserId(Workspace wk, String userId) {
        User user = User.build(userId);
        for (Entry<User, Consumer<String>> entry : approved.get(wk).entrySet()) {
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
        for (Entry<User, Consumer<String>> entry : inProgress.entrySet()) {
            if (user.equals(entry.getKey())) {
                return entry.getKey();
            }
        }
        return null;
    }

    void ping(String wkId, String userId) {
        Workspace wk = Workspace.build(wkId);
        User user = User.build(userId);
        LinkedHashMap<User, Long> mwk = new LinkedHashMap<User, Long>();
        
        if(pong.containsKey(wk)){
            mwk = pong.get(wk);
        }else{
            pong.put(wk, mwk);
        }
        
        mwk.put(user, System.currentTimeMillis() + 5000);        

    }

}
