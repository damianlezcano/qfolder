package org.q3s.p2p.server;

import java.io.IOException;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;

import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.User;

@Path("/")
@Produces("application/json")
@Consumes("application/json")
@ApplicationScoped
public class MainResource {

    private MainService processor;
	
    @Context
    public void setSse(Sse sse) {
        processor = MainService.getInstance(sse);
    }
	
    @GET //TODO esto es un POST/PUT
    @Path("/wk")
    public String create(@QueryParam("name") String name, @QueryParam(value="password") String password, @QueryParam("date") Long date) {
//    	System.out.println("# create -> name: " + name + " - password: " + password + " - date: " + date);
        return processor.create(name, password, date);
    }
    
    @GET
    @Path("/{wk}/connect")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void connect(@Context SseEventSink sseEventSink, @PathParam("wk") String wkId, @QueryParam("user") String userId) throws IOException {
//    	System.out.println("# connect -> wkId: " + wkId + " - userId: " + userId);
    	processor.connect(sseEventSink,wkId,userId);
    }
    
    @POST
    @Path("/{wk}/connect")
    public void withoutAuth(@PathParam("wk") String wkId, User user) throws Exception {
//    	System.out.println("# withoutAuth -> wkId: " + wkId + " - userId: " + user);
        processor.withoutAuth(wkId,user);
    }

    @POST
    @Path("/{wk}/connect/auth")
    public void withAuth(@PathParam("wk") String wkId, User user) throws Exception {
//    	System.out.println("# withAuth -> wkId: " + wkId + " - userId: " + user);
    	processor.withAuth(wkId, user);
    }

    @GET
    @Path("/{wk}/approved/{user}")
    public void approved(@PathParam("wk") String wkId, @PathParam("user") String userId) throws IOException {
//    	System.out.println("# approved -> wkId: " + wkId + " - userId: " + userId);
        processor.approved(wkId,userId);
    }

    @GET
    @Path("/{wk}/refuse/{user}")
    public void refuse(@PathParam("wk") String wkId, @PathParam("user") String userId) throws IOException {
//    	System.out.println("# refuse -> wkId: " + wkId + " - userId: " + userId);
    	processor.refuse(wkId,userId);
    }
    
    @POST
    @Path("/{wk}/{user}")
    public void sendToUser(@PathParam("wk") String wkId, @PathParam("user") String userId, Event event) throws IOException {
//    	System.out.println("# sendToUser -> wkId: " + wkId + " - userId: " + userId + " - event: " + event);
    	processor.sendToUser(wkId,userId,event);
    }

    @POST
    @Path("/{wk}")
    public void sendToWk(@PathParam("wk") String wkId, Event event) throws IOException {
//    	System.out.println("# sendToWk -> wkId: " + wkId + " - event: " + event);
    	processor.sendToWk(wkId,event);
    }

}