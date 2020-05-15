package com.example.pushserver;

import java.io.IOException;
import org.q3s.p2p.model.Event;

import org.q3s.p2p.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@RestController
public class MessageController {

    @Autowired
    private MessageProcessor processor;
    
    @ResponseBody
    @GetMapping("/wk")
    public String create(@RequestParam("name") String name, @RequestParam(value="password",required = false) String password, @RequestParam("date") Long date) {
        return processor.create(name, password, date);
    }
    
    @GetMapping(path = "/{wk}/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> connect(@PathVariable("wk") String wkId, @RequestParam("user") String userId) {
        return Flux.create(sink -> {
        	sink.onDispose(new Disposable() {
                @Override
                public void dispose() {
                    processor.disposeInProgress(userId);
                }
            });
            processor.connect(sink::next,wkId, userId);
        });
    }
    
    @GetMapping(path = "/{wk}/reconnect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> reconnect(@PathVariable("wk") String wkId, @RequestParam("user") String userId) throws Exception {
        return Flux.create(sink -> {
        	sink.onDispose(new Disposable() {
                @Override
                public void dispose() {
                    processor.disposeApproval(wkId, userId);
                }
            });
            processor.reconnect(sink::next,wkId, userId);
        });
    }
    
    @PostMapping(path = "/{wk}/connect")
    public void withoutAuth(@PathVariable("wk") String wkId, @RequestBody User user) throws Exception {
        processor.withoutAuth(wkId,user);
    }

    @PostMapping(path = "/{wk}/connect/auth")
    public void withAuth(@PathVariable("wk") String wkId, @RequestBody User user) throws Exception {
    	processor.withAuth(wkId, user);
    }

    @GetMapping(path = "/{wk}/approved/{user}")
    public void approved(@PathVariable("wk") String wkId, @PathVariable("user") String userId) throws IOException {
        processor.approved(wkId,userId);
    }

    @GetMapping(path = "/{wk}/refuse/{user}")
    public void refuse(@PathVariable("wk") String wkId, @PathVariable("user") String userId) throws IOException {
    	processor.refuse(wkId,userId);
    }
    
    @PostMapping(path = "/{wk}/{user}")
    public void sendToUser(@PathVariable("wk") String wkId, @PathVariable("user") String userId, @RequestBody Event event) throws IOException {
    	processor.sendToUser(wkId,userId,event);
    }

    @PostMapping(path = "/{wk}")
    public void sendToWk(@PathVariable("wk") String wkId, @RequestBody Event event) throws IOException {
    	processor.sendToWk(wkId,event);
    } 
}