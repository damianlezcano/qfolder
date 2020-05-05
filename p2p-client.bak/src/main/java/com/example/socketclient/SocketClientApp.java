package com.example.socketclient;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.q3s.p2p.model.User;
import org.q3s.p2p.model.util.LogUtils;
import org.q3s.p2p.model.util.UUIDUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.EventSource;

//import org.springframework.web.client.RestTemplate;

//import com.launchdarkly.eventsource.EventHandler;
//import com.launchdarkly.eventsource.EventSource;

public class SocketClientApp {

	public static void main(String[] args) throws Exception	 {

		LogUtils log = new LogUtils();

		ObjectMapper objectMapper = new ObjectMapper();
		
//		RestTemplate restTemplate = new RestTemplate();
        
		String uuid = args[0];
		String id = UUIDUtils.generate();
		User user = new User(uuid,id, args[1]);
		
		System.out.println("# " + user);
		
//		BodySettingRequestCallback request = new BodySettingRequestCallback(user,objectMapper);

//		restTemplate.execute("http://localhost:8080/connect2/" + args[0] + "?userId=" + uuid, HttpMethod.GET, 
//			request -> {}, 
//			response -> {
//				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getBody()));
//				String line;
//				try {
//					while ((line = bufferedReader.readLine()) != null) {
//						if (!line.isEmpty()) {
//							log.info(line);
//						}
//					}
//				} catch (IOException e) {
//					e.printStackTrace();
//					// Something clever
//				}
//				return response;
//		});

		
		String json = objectMapper.writeValueAsString(user);
		String jsonBase64 = Base64.getEncoder().encodeToString(json.getBytes());
	    EventHandler eventHandler = new SimpleEventHandler();
	    String url = String.format("http://localhost:8080/" + uuid + "/connect?user=" + jsonBase64);
	    EventSource.Builder builder = new EventSource.Builder(eventHandler, URI.create(url)).reconnectTime(Duration.ofMillis(1));

	    try (EventSource eventSource = builder.build()) {
	      eventSource.start();

	      TimeUnit.MINUTES.sleep(10);
	    }
		
//		    ParameterizedTypeReference<ServerSentEvent<String>> typeRef = new ParameterizedTypeReference<ServerSentEvent<String>>() {};
//
//		    while (true) {
//		      try {
//		        final Flux<ServerSentEvent<String>> stream = WebClient
//		            .create("http://localhost:8080").get().uri("/connect2/" + args[0] + "?userId=" + uuid)
//		            .accept(MediaType.TEXT_EVENT_STREAM).retrieve().bodyToFlux(typeRef);
//		        stream.subscribe(sse -> log.info("Received: " + sse.toString()));
//
//		        TimeUnit.MINUTES.sleep(1);
//		      }
//		      catch (Exception e) {
//		    	  System.out.println("################# fin");
//		        e.printStackTrace();
//		      }
//		    }
	}
}
