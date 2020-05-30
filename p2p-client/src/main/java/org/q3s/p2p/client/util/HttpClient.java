/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.util;

import java.util.Map;
import java.util.Map.Entry;
import org.q3s.p2p.client.Config;
import org.q3s.p2p.client.view.Controller;
import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.Workspace;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 *
 * @author damianlezcano
 */
public class HttpClient {

    private Controller controller;
    private RestTemplate restTemplate = new RestTemplate();
    
    private int MAX_RETRY_TO_DISCONNECT = 3;
    
    private int connectTimeoutMillis = 5000;
    private int readTimeoutMillis = 2000;
    
    private long last;
    private int cdor;

    public HttpClient(Controller controller) {
        setTimeout(restTemplate);
        this.controller = controller;
    }

    private void get(String url, Event request, Map<String, String> params, String eventResponseOK, String eventResponseFail) {
        new Thread(new Runnable() {
            public void run() {
                Event eventResponse = null;
                try {
                    String uri = url;
                    
                    HttpHeaders requestHeaders = new HttpHeaders();
                    //set up HTTP Basic Authentication Header
                    requestHeaders.add("Accept", MediaType.APPLICATION_JSON_VALUE);

                    //request entity is created with request headers
                    HttpEntity<?> requestEntity = new HttpEntity<>(requestHeaders);
                        
                    if(params != null){

                        //adding the query params to the URL
                        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url);
                        for (Entry<String, String> e : params.entrySet()) {
                            uriBuilder = uriBuilder.queryParam(e.getKey(), e.getValue());
                        }
                        
                        uri = uriBuilder.toUriString();
                    }
                    
                    ResponseEntity<String> response = restTemplate.exchange(
                            uri,
                            HttpMethod.GET,
                            requestEntity,
                            String.class
                    );
                    String body = response.getBody();
                    
                    if(body != null){body=body.trim();}
                    
                    eventResponse = new Event(eventResponseOK, body);
                    
                } catch(RestClientException e){
                    registerError();
                    eventResponse = new Event(eventResponseFail,"El servidor no esta disponible. Vuelva a intentarlo mas tarde.");
                } catch(Exception e) {
                    registerError();
                    eventResponse = new Event(eventResponseFail,e.getMessage());
                } finally {
                    controller.notify(eventResponse);
                }
            }
        }).start();

    }
    
    private void registerError(){
        long current = System.currentTimeMillis();
        if(current - last > 5000){
            cdor++;
        }else{
            cdor = 0;
            last = current;
        }
    }
    
    public boolean isDisconnect(){
        return cdor > MAX_RETRY_TO_DISCONNECT;
    }
    
    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public void post(String url, Object obj, String eventResponseOK, String eventResponseFail) {
        new Thread(new Runnable() {
            public void run() {
                Event eventResponse = new Event(eventResponseOK);
                try {
                    if(obj == null){
                        restTemplate.postForEntity(url, obj, String.class);
                    }else{
                        restTemplate.postForEntity(url, obj, obj.getClass());
                    }
                } catch (Exception e) {
                    registerError();
                    eventResponse = new Event(eventResponseFail);
                }finally{
                    controller.notify(eventResponse);
                }
            }
        }).start();
    }

    public void sendBroadcastWk(Workspace wk, Event e) {
        post(Config.buildWkBroadcastUri(wk), e, null, "Error al intentar conectarse con el servidor");
    }
    
    //llamar a github para obtener la URL del servidor
    public void verifyServiceGithubUp(){
        if (Config.URL_SERVER == null) {
//            get(Config.URL_GITHUB_SERVER_INF, null, null, "URL servicio resuelta", "Error al intentar recuperar la URL del servicio");
        	
            Event er = new Event("URL servicio resuelta","http://localhost:8080");
            controller.notify(er);
        }
    }

    //llamamos al servidor de p2p-server para pedir el workpaceId
    public void createWk(Workspace wk) {
        get(Config.buildWkCreateUri(),null, wk.buildRequestParam(), "Workspace creado", "Error al crear el workspace");
    }

    public void post(String url, Object obj) {
        post(url, obj, null, "Error al intentar conectarse con el servidor");
    }

    public void get(String url) {
        get(url, null, null, null, "Error al intentar conectarse con el servidor");
    }

    private void setTimeout(RestTemplate restTemplate) {
        restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory());
        SimpleClientHttpRequestFactory rf = (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
        rf.setReadTimeout(readTimeoutMillis);
        rf.setConnectTimeout(connectTimeoutMillis);
    }
    
}
