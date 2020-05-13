/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.util;

import java.util.Base64;

import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.MessageEvent;
import org.q3s.p2p.client.view.Controller;

public class SimpleEventHandler implements EventHandler {

    private Controller controller;

    public SimpleEventHandler(Controller controller) {
        this.controller = controller;
    }
    
    @Override
    public void onOpen() throws Exception {
        System.out.println("onOpen");
    }

    @Override
    public void onClosed() throws Exception {
        System.out.println("onClosed");
    }

    @Override
    public void onMessage(String event, MessageEvent messageEvent) throws Exception {
        byte[] decodedBytes = Base64.getDecoder().decode(messageEvent.getData());
        String body = new String(decodedBytes);
        System.out.println(body);
    }

    @Override
    public void onComment(String comment) throws Exception {
        System.out.println("onComment");
    }

    @Override
    public void onError(Throwable t) {
        try {
//            controller.notify("Wk no existe, desconectar");
        } catch (Exception e) {
        }
        
    }

}
