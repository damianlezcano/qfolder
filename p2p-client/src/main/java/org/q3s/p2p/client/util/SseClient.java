/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.util;



import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;

import org.q3s.p2p.client.Config;
import org.q3s.p2p.client.view.Controller;
import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.User;
import org.q3s.p2p.model.Workspace;
import org.q3s.p2p.model.util.EventUtils;


/**
 *
 * @author damianlezcano
 */
public class SseClient extends Thread {

    private User user;
    private Workspace wk;
    private Controller controller;
    private Logger log;
    
    private Client client = ClientBuilder.newClient();
    
    public SseClient(Workspace wk, User user, Controller controller, Logger log) {
        this.wk = wk;
        this.user = user;
        this.controller = controller;
        this.log = log;
    }

    public void run() {
    	String url = Config.buildWkConnectUri(wk, user);
    	log.debug("Estableciendo conexion SSE con el servidor -> " + url);
    	WebTarget target = client.target(url);
    	SseEventSource eventSource = SseEventSource.target(target).reconnectingEvery(3, TimeUnit.SECONDS).build();
    	eventSource.register(onEvent, onError, onComplete);
    	eventSource.open();
    }
    
    // A new event is received
    private Consumer<InboundSseEvent> onEvent = (inboundSseEvent) -> {
        log.debug("Event received: " + inboundSseEvent.getId());
        String data = inboundSseEvent.readData(String.class);
		Event event = (Event) EventUtils.toObjectBase64(data,Event.class);
		controller.notify(event);
    };

    //Error
    private Consumer<Throwable> onError = (throwable) -> {
    	System.out.println("> Error ->" + throwable.getMessage());
    	controller.notify(new Event("No es posible establecer una conexion"));
    };

    //Connection close and there is nothing to receive
    private Runnable onComplete = () -> {
    	System.out.println("> onComplete");
    	controller.notify(new Event("Se perdio la conexion con el servidor"));
    };
    
}