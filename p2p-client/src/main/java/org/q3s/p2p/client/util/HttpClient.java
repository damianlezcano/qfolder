/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.util;

import java.net.ConnectException;
import java.util.Map;
import java.util.Map.Entry;
import org.q3s.p2p.client.Config;
import org.q3s.p2p.client.view.Controller;
import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.User;
import org.q3s.p2p.model.Workspace;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public HttpClient(Controller controller) {
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
                    eventResponse = new Event(eventResponseFail,"El servidor no esta disponible. Vuelva a intentarlo mas tarde.");
                } catch(Exception e) {
                    eventResponse = new Event(eventResponseFail,e.getMessage());
                } finally {
                    controller.notify(eventResponse);
                }
            }
        }).start();

    }

//    public String get(String url) throws Exception {
//        try {
//            HttpHeaders httpHeaders = new HttpHeaders();
//            httpHeaders.set("Accept", MediaType.APPLICATION_JSON_VALUE);
//            HttpEntity<?> httpEntity = new HttpEntity<>(httpHeaders);
//            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, httpEntity, String.class);
//            return response.getBody().trim();
//        } catch (Exception e) {
//            return "";
//        }
//    }
    
    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

//    public void post(String url, Object obj) {
//        restTemplate.postForEntity(url, obj, String.class);
//    }

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
            get(Config.URL_GITHUB_SERVER_INF, null, null, "URL servicio resuelta", "Error al intentar recuperar la URL del servicio");
            
        }
    }

    //llamamos al servidor de p2p-server para pedir el workpaceId
    public void createWk(Workspace wk) {
        get(Config.buildWkCreateUri(),null, wk.buildRequestParam(), "Workspace creado", "Error al crear el workspace");
    }

//    public void sendUserData(Workspace wk, User user) {
//        //httpClient.post(Config.buildWkConnectWithAuthUri(wk), user);
//        post(Config.buildWkConnectWithoutAuthUri(wk), user,null,"Error al intentar conectarse con el servidor");
//    }

    public void post(String url, Object obj) {
        post(url, obj, null, "Error al intentar conectarse con el servidor");
    }

    public void get(String url) {
        get(url, null, null, null, "Error al intentar conectarse con el servidor");
    }
}
