/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

import org.q3s.p2p.client.Config;
import org.q3s.p2p.client.view.Controller;
import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.User;
import org.q3s.p2p.model.Workspace;
import org.q3s.p2p.model.util.EventUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 *
 * @author damianlezcano
 */
public class SseClient extends Thread {

    private User user;
    private Workspace wk;
    private Controller controller;
    private Logger log;

    private int MAX_RETRY_RECONNECT = 2;
    private long DELAY_RETRY_RECONNECT = 1000;
    
    private int connectTimeoutMillis = 4000;
    private int readTimeoutMillis = 0;

    private int cdor = 0;
    
    private RestTemplate restTemplate = new RestTemplate() /*new RestTemplateBuilder()
        .setConnectTimeout(Duration.ofMillis(connectTimeoutMillis))
        .setReadTimeout(Duration.ofMillis(readTimeoutMillis))
        .build()*/;

    private AtomicBoolean interrupt = new AtomicBoolean(false);

    public void interrupt() {
        interrupt.set(true);
    }

    public SseClient(Workspace wk, User user, Controller controller, Logger log) {
        this.wk = wk;
        this.user = user;
        this.controller = controller;
        this.log = log;
        setTimeout(restTemplate);
    }

    public void run() {
        System.out.println("Thread has started");
        do{
            try {
                String url = cdor++ > 0 ? Config.buildWkReconnectUri(wk, user) : Config.buildWkConnectUri(wk, user);
                restTemplate.execute(url, HttpMethod.GET,
                        request -> {},
                        response -> {
                            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getBody()));
                            String line; cdor = 0;
                            while ((line = bufferedReader.readLine()) != null) {
                                if(interrupt.get()){
                                    break;
                                }else{
                                    if (!line.isEmpty()) {
                                        Event event = EventUtils.toObjectBase64(line);
                                        controller.notify(event);
                                    }
                                }
                            }
                            return response;
                        });
            } catch (Exception e) {
                System.out.println("SSECLient ERROR 2");
            }
            
            try {Thread.sleep(DELAY_RETRY_RECONNECT);} catch (InterruptedException ex) {}
            
            if(cdor > MAX_RETRY_RECONNECT){
                Event event = new Event("No es posible establecer una conexion","El servidor no esta disponible. Vuelva a intentarlo mas tarde.");
                controller.notify(event);            
            }
            
        }while (!interrupt.get() && cdor <= MAX_RETRY_RECONNECT);

        System.out.println("Thread has ended");

    }

    public boolean isClosed(){
        return interrupt.get();
    }
    
    private void setTimeout(RestTemplate restTemplate) {
        restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory());
        SimpleClientHttpRequestFactory rf = (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
        rf.setReadTimeout(readTimeoutMillis);
        rf.setConnectTimeout(connectTimeoutMillis);
    }
    
//    public String get(String url) throws Exception {
//        HttpHeaders httpHeaders = new HttpHeaders();
//        httpHeaders.set("Accept", MediaType.APPLICATION_JSON_VALUE);
//        HttpEntity<?> httpEntity = new HttpEntity<>(httpHeaders);
//        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, httpEntity, String.class);
//        return response.getBody().trim();
//    }
}
