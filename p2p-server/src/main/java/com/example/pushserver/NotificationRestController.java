package com.example.pushserver;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

import org.q3s.p2p.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import org.q3s.p2p.model.Workspace;

@RestController
public class NotificationRestController {

    @Autowired
    private NotificationJobService service;

    private ObjectMapper objectMapper = new ObjectMapper();

    private CopyOnWriteArraySet<User> reconnects = new CopyOnWriteArraySet<User>();

    private LinkedHashMap<User,SseEmitter> inProgress = new LinkedHashMap<User,SseEmitter>();

    private LinkedHashMap<Workspace, LinkedHashMap<User, SseEmitter>> approved = new LinkedHashMap<Workspace, LinkedHashMap<User, SseEmitter>>();
    
    @ResponseBody
    @GetMapping("/wk")
    public String create(@RequestParam("name") String name, @RequestParam("password") String password, @RequestParam("date") Long date) {
        String uuid = UUID.randomUUID().toString();
        Workspace wk = Workspace.build(uuid);
        wk.setDate(date);
        wk.setName(name);
        wk.setPassword(password);
        approved.put(wk, new LinkedHashMap<User, SseEmitter>());
        return uuid;
    }

    @GetMapping(path = "/{wk}/connect")
    public SseEmitter connect(@PathVariable("wk") String wkId, @RequestParam("user") String userId) throws Exception {

        SseEmitter emitter = new SseEmitter();
        System.out.println("# connect -> " + userId + " - " + emitter);
        
        Workspace wk = getWorkspaceInApprobalById(wkId);
        User user = User.build(userId);
        
        this.inProgress.put(user, emitter);
        emitter.onTimeout(() -> {
            System.out.println("# disconnect1 -> " + userId + " - " + emitter);
            emitter.complete();
            this.inProgress.remove(user);
        });
        
        if(wk != null){
            if(wk.requiredCredential()){
                emitter.send("Wk existente, con credenciales");
            }else{
                emitter.send("Wk existente, sin credenciales");
            }
        }else{
            emitter.send("Wk no existe, desconectar");
        }
        
        return emitter;
    }

    @GetMapping(path = "/{wk}/reconnect")
    public SseEmitter reconnect(@PathVariable("wk") String wkId, @RequestParam("user") String userId) throws Exception {

        SseEmitter emitter = new SseEmitter();
        System.out.println("# reconnect -> " + userId + " - " + emitter);
        
        Workspace wk = getWorkspaceInApprobalById(wkId);
        User user = User.build(userId);

        if (this.approved.get(wk).containsKey(user)){
            this.approved.get(wk).put(user, emitter);
            emitter.onTimeout(() -> {
                System.out.println("# disconnect2 -> " + userId + " - " + emitter);
                emitter.complete();
                this.approved.get(wk).remove(user);
            });            
        }
        return emitter;
    }
    
    @PostMapping(path = "/{wk}/connect")
    public void withoutAuth(@PathVariable("wk") String wkId, @RequestBody User user) throws Exception {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        Collection<SseEmitter> list = approved.get(wk).values();
        if(list.isEmpty()){
            SseEmitter emitter = getEmitterInProgressByUserId(user.getId());
            System.out.println("# withoutAuth -> " + user.getId() + " - " + emitter);
            sendWelcome(wk,user,emitter); 
        }else{
            for (SseEmitter emitter : approved.get(wk).values()) {
                System.out.println("# withoutAuth -> " + user.getId() + " - " + emitter);
                emitter.send("Aprobar al usuario|" + user.getId() + "|" + user.getName());
            }
        }
    }

    @PostMapping(path = "/{wk}/connect/auth")
    public void withAuth(@PathVariable("wk") String wkId, @RequestBody User user) throws Exception {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        SseEmitter emitter = getEmitterInProgressByUserId(user.getId());
        if(wk.getPassword().equals(user.getPassword())){
            sendWelcome(wk,user,emitter); 
        }else{
            emitter.send("Credenciales incorrectas");
        }
    }

    @GetMapping(path = "/{wk}/approved/{user}")
    public void approval(@PathVariable("wk") String wkId, @PathVariable("user") String userId) throws IOException {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        User user = User.build(userId);
        SseEmitter emitter = inProgress.get(user);
        sendWelcome(wk,user,emitter);
    }

    @GetMapping(path = "/{wk}/refuse/{user}")
    public void refuse(@PathVariable("wk") String wkId, @PathVariable("user") String userId) throws IOException {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        User user = User.build(userId);
        SseEmitter emitter = inProgress.get(user);
        emitter.send("Usuario rechazado!");
        inProgress.remove(user);
    }
    
    @PostMapping(path = "/{wk}/{user}")
    public void sendToUser(@PathVariable("wk") String wkId, @PathVariable("user") String userId, @RequestBody String body) throws IOException {
        Workspace wk = getWorkspaceInApprobalById(wkId);
        User user = User.build(userId);
        SseEmitter emitter = inProgress.get(user);
        emitter.send(body);
    }
    
    //------------------------------------------------------------------------------------------
    
    private void sendWelcome(Workspace wk, User user, SseEmitter emitter) throws IOException {
        emitter.send("Bienvenido usuario!|" + wk.getName());
        approved.get(wk).put(user,emitter);
        inProgress.remove(user);
    }
    
    private Workspace getWorkspaceInApprobalById(String wkId) {
        Workspace wkf = Workspace.build(wkId);
        for (Workspace wkt : approved.keySet()) {
            if(wkf.equals(wkt)){
                return wkt;
            }
        }
        return null;
    }

    private SseEmitter getEmitterInProgressByUserId(String userId) {
        User user = User.build(userId);
        for (Entry<User,SseEmitter> entry : inProgress.entrySet()) {
            if (user.equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;    
    }
    
    class Msg {
        String wkId;
        String userId;
        String value;
        public Msg(String wkId, String userId, String value){
            this.wkId = wkId; this.userId = userId; this.value = value;
        }
    }
    
}