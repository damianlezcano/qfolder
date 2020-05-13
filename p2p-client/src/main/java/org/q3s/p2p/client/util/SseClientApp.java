package org.q3s.p2p.client.util;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;

public class SseClientApp {

    private static final String url = "http://localhost:8080/922923d6-f4b3-48d8-a434-1fbbe812afee/connect?user=4ac1973c-4a7b-4630-8c15-c08da869a748";

    public static void main(String... args) throws Exception {
    	
  	
    	ClientBuilder cb = ClientBuilder.newBuilder();
    	cb.connectTimeout(100000, TimeUnit.MINUTES);
    	cb.readTimeout(100000, TimeUnit.MINUTES);
    	
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(url);
        
        try (SseEventSource eventSource = SseEventSource.target(target).build()) {

            eventSource.register(onEvent, onError, onComplete);
            eventSource.open();

            //Consuming events for one hour
            Thread.sleep(60 * 60 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        client.close();
        System.out.println("End");
    }

    // A new event is received
    private static Consumer<InboundSseEvent> onEvent = (inboundSseEvent) -> {
        String data = inboundSseEvent.readData();
        System.out.println(data);
    };

    //Error
    private static Consumer<Throwable> onError = (throwable) -> {
        throwable.printStackTrace();
    };

    //Connection close and there is nothing to receive
    private static Runnable onComplete = () -> {
        System.out.println("Done!");
    };

}