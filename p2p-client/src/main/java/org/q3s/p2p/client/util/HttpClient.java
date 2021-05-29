/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.util;

import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.q3s.p2p.client.Config;
import org.q3s.p2p.client.view.Controller;
import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.Workspace;

/**
 *
 * @author damianlezcano
 */
public class HttpClient {

    private Controller controller;
    
    private Client client = ClientBuilder.newBuilder().build();

    public HttpClient(Controller controller) {
        this.controller = controller;
    }
    
	public void get(String uri, Event request, Map<String, String> params, String eventResponseOK, String eventResponseFail) {
		controller.getLogger().debug("> HttpClient.get -> " + uri);
        new Thread(new Runnable() {
            public void run() {
                Event eventResponse = new Event(eventResponseOK);
                try {
                	
                    WebTarget target = client.target(uri);
                    
                    if(params != null){
                    	for (Entry<String,String> entry : params.entrySet()) {
							target = target.queryParam(entry.getKey(), entry.getValue());
						}
                    }
        			
                    Response response = target.request().get();
                    
                    String body = response.readEntity(String.class);
                    
                    body = body != null ? body.trim() : body;
                    
                    eventResponse = new Event(eventResponseOK, body);
        			
                } catch (Exception e) {
                	e.printStackTrace();
                    eventResponse = new Event(eventResponseFail);
                }finally{
                    controller.notify(eventResponse);
                }
            }
        }).start();
	}
    
	public void post(String uri, Object obj, String eventResponseOK, String eventResponseFail) {
		controller.getLogger().debug("> HttpClient.post -> " + uri);
        new Thread(new Runnable() {
            public void run() {
                Event eventResponse = new Event(eventResponseOK);
                try {
                    WebTarget target = client.target(uri);
                    Response response = target.request(MediaType.APPLICATION_JSON).post(Entity.entity(obj, MediaType.APPLICATION_JSON));
        			int statusCode = response.getStatus();
        			controller.getLogger().debug("> http.statusCode: " + statusCode);
        			if (!(statusCode >= 200 && statusCode < 400)) {
        				throw new Exception();
        			}
                } catch (Exception e) {
                	e.printStackTrace();
                    eventResponse = new Event(eventResponseFail);
                }finally{
                    controller.notify(eventResponse);
                }
            }
        }).start();
	}    

    //-------------------------------------------------------------

    public void sendBroadcastWk(Workspace wk, Event e) {
        post(Config.buildWkBroadcastUri(wk), e, null, "Error al intentar conectarse con el servidor");
    }
    
    //llamar a github para obtener la URL del servidor
    public void verifyServiceGithubUp(){
    	String server = Config.URL_SERVER;
    	String eventResponseOK = "URL servicio resuelta";
    	//-----------------------------------------------
        if (server == null) {
            get(Config.URL_GITHUB_SERVER_INF, null, null, eventResponseOK, "Error al intentar recuperar la URL del servicio");
        }else {
        	controller.getLogger().debug("# Server REF definido: " + server);
        	Event event = new Event(eventResponseOK, server);
        	controller.notify(event);
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
    
}
