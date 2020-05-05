package com.example.pushserver;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.q3s.p2p.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class NotificationRestController {

	@Autowired
	private NotificationJobService service;
	
	private ObjectMapper objectMapper = new ObjectMapper();
	private CopyOnWriteArraySet<User> users = new CopyOnWriteArraySet<User>();
	private LinkedHashMap<String, LinkedHashMap<User,SseEmitter>> m = new LinkedHashMap<String, LinkedHashMap<User,SseEmitter>>();

	@ResponseBody
	@GetMapping("/uuid")
	public String uuid() {
		return UUID.randomUUID().toString();
	}
	
	@GetMapping(path = "/{uuid}/connect")
	public SseEmitter connect(@PathVariable("uuid") String uuid, @RequestParam("user") String jsonBase64) throws Exception {
		
		byte[] decodedBytes = Base64.getDecoder().decode(jsonBase64);
		String json = new String(decodedBytes);
		
		User user = objectMapper.readValue(json, User.class);
//		user.setUuid(uuid);
		
		User tUser = getUser(user);
		if(tUser != null) {
			user.setDate(tUser.getDate());			
		}
		
		SseEmitter emitter = new SseEmitter();
		this.add(uuid, user, emitter);
		
		if(user.isReconnect()) {
			System.out.println("## reconectar: " + user);
			users.remove(user);
		}else{
			if(user.getDate() == 0l) {
				one(user.getUuid(),user.getId(),"Aprobado: " + user);				
			}
		}
				
//		emitter.onCompletion(() -> {
//			if(emitters.contains(user)) {
//				System.out.println("## si: borrar onCompletion");
//			}else{
//				System.out.println("## no: borrar onCompletion");
//				this.emitters.add(user.buildRemoveDate());
//			}
//			this.remove(uuid, user, emitter);
//		});

		emitter.onTimeout(() -> {
			this.users.add(user.buildRemoveDate());
			emitter.complete();
			this.remove(uuid, user, emitter);
		});

		return emitter;
	}

	private User getUser(User user) {
		User ur = null;
		for (User u : users) {
			if(u.equals(user)) {
				ur = u;
			}
		}
		return ur;
	}

	@PostMapping(path = "/{uuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public void many(@PathVariable("uuid") String uuid, @RequestBody String body) {
		List<SseEmitter> deadEmitters = new ArrayList<>();
		this.get(uuid).forEach(emitter -> {
			try {
				System.out.println("# broadcast: " + body);
				String base64 = Base64.getEncoder().encodeToString(body.getBytes());
				emitter.send(base64);
			} catch (Exception e) {
				System.out.println("Error ->" + e.getMessage());
				deadEmitters.add(emitter);
			}
		});
		this.remove(uuid,deadEmitters);
	}

	@PostMapping(path = "/{uuid}/{user}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public void one(@PathVariable("uuid") String uuid, @PathVariable("user") String user, @RequestBody String body) {
		List<SseEmitter> deadEmitters = new ArrayList<>();
		this.get(uuid,User.build(user)).forEach(emitter -> {
			try {
				System.out.println("# say2: " + body);
				String base64 = Base64.getEncoder().encodeToString(body.getBytes());
				emitter.send(base64);
			} catch (Exception e) {
				System.out.println("Error ->" + e.getMessage());
				deadEmitters.add(emitter);
			}
		});
		this.get(uuid).remove(deadEmitters);
	}
	
	//-------------------------------------------------------------------------------------
	
	private void add(String uuid, User user, SseEmitter emitter) {
		System.out.println("# add: " + user);
		LinkedHashMap<User,SseEmitter> u = new LinkedHashMap<User,SseEmitter>();
		if (m.containsKey(uuid)) {
			u = m.get(uuid);
			u.put(user,emitter);
		}else{
			u.put(user,emitter);
			m.put(uuid,u);
		}
	}

	private void remove(String uuid, List<SseEmitter> emitter) {
		for (SseEmitter sseEmitter : emitter) {
			System.out.println("# remove1: " + sseEmitter);
			LinkedHashMap<User,SseEmitter> u = new LinkedHashMap<User,SseEmitter>();
			if (m.containsKey(uuid)) {
				u = m.get(uuid);
			}
			User t = null;
			for(Entry<User,SseEmitter> entry : u.entrySet()) {
				if(sseEmitter.equals(entry.getValue())) {
					t = entry.getKey();
				}
				break;
			}
			u.remove(t);
		}
	}

	private void remove(String uuid, User user, SseEmitter emitter) {
		System.out.println("# remove2: " + user);
		LinkedHashMap<User,SseEmitter> u = new LinkedHashMap<User,SseEmitter>();
		if (m.containsKey(uuid)) {
			u = m.get(uuid);
		}
		u.remove(user);
	}
	
	private Collection<SseEmitter> get(String uuid) {
		LinkedHashMap<User,SseEmitter> u = new LinkedHashMap<User,SseEmitter>();
		if (m.containsKey(uuid)) {
			u = m.get(uuid);
		}
		return u.values();
	}

	private CopyOnWriteArrayList<SseEmitter> get(String uuid,User user) {
		LinkedHashMap<User,SseEmitter> u = new LinkedHashMap<User,SseEmitter>();
		if (m.containsKey(uuid)) {
			u = m.get(uuid);
		}
		CopyOnWriteArrayList<SseEmitter> e = new CopyOnWriteArrayList<SseEmitter>();
		e.add(u.get(user));
		return e;
	}

    @Scheduled(fixedDelay = 3000)
    public void evict() throws InterruptedException {
        List<User> deadEmitters = new ArrayList<User>();
        for (User user : users) {
        	if(!user.isReconnect()) {
        		deadEmitters.add(user);
        	}
		}
        for (User dead : deadEmitters) {
        	this.users.remove(dead);
			many(dead.getUuid(),"Borrar usuario: " + dead + " - " + new Date(dead.getDate()) + " - " + (dead.getDate() - System.currentTimeMillis()));
		}
    }
	
//        @EventListener
//        public void onNotification(Notification notification) {
//            List<SseEmitter> deadEmitters = new ArrayList<>();
//            this.emitters.forEach(emitter -> {
//                    try {
//                           emitter.send(notification);
//                    } catch (Exception e) {
//                           deadEmitters.add(emitter);
//                    }
//            });
//            this.emitters.remove(deadEmitters);
//        }

//	private boolean isExist(String uuid, User user) {
//	LinkedHashMap<User,SseEmitter> u = new LinkedHashMap<User,SseEmitter>();
//	if (m.containsKey(uuid)) {
//		u = m.get(uuid);
//	}
//	return u.keySet().contains(user);
//}
	
}