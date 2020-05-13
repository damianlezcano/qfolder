/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.q3s.p2p.client.Config;
import org.q3s.p2p.client.view.Controller;
import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.User;
import org.q3s.p2p.model.Workspace;
import org.q3s.p2p.model.util.EventUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    
    private int connectTimeoutMillis = 100000;
    private int readTimeoutMillis = 100000;
    
    private RestTemplate restTemplate = new RestTemplateBuilder()
        .setConnectTimeout(Duration.ofMillis(connectTimeoutMillis))
        .setReadTimeout(Duration.ofMillis(readTimeoutMillis))
        .build();
    
    private AtomicBoolean interrupt = new AtomicBoolean(false); 
 
    public void interrupt() {
        interrupt.set(true);
    }

    public SseClient(Workspace wk, User user, Controller controller, Logger log) {
        this.wk = wk;
        this.user = user;
        this.controller = controller;
        this.log = log;
    }

    public void run() {
        System.out.println("Thread has started");
        try {
            if (Config.URL_SERVER == null) {
                Config.URL_SERVER = get(Config.URL_GITHUB_SERVER_INF);
            }
        } catch (Exception e) {
            System.out.println("SSECLient ERROR 0");
        }

        boolean reconnect = false;
        
        while (!interrupt.get()) {
            try {
                
                String url = reconnect ? Config.buildWkReconnectUri(wk, user) : Config.buildWkConnectUri(wk, user);

                restTemplate.execute(url, HttpMethod.GET,
                    request -> {
                    },
                    response -> {
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getBody()));
                        String line;
                        try {
                            while ((line = bufferedReader.readLine()) != null) {
                                if (!line.isEmpty()) {
                                    System.out.println(">>>>>>> SseClient -> " + line);
                                    Event event = EventUtils.toObjectBase64(line);
                                    controller.notify(event);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("SSECLient ERROR 1");
                        }
                        return response;
                    });
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("SSECLient ERROR 2");
            }
            reconnect = true;
        };
        
        System.out.println("Thread has ended");

    }
    
    public String get(String url) throws Exception {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<?> httpEntity = new HttpEntity<>(httpHeaders);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, httpEntity, String.class);
        return response.getBody().trim();
    }

}
