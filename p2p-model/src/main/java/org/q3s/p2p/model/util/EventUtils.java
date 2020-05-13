/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.model.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Base64;
import org.q3s.p2p.model.Event;

/**
 *
 * @author damianlezcano
 */
public class EventUtils {

    static ObjectMapper mapper = new ObjectMapper();
    
    public static String toJsonBase64(Event event) throws IOException {
        String json = toJson(event);
        return Base64.getEncoder().encodeToString(json.getBytes());
    }

    public static String toJson(Event event) throws IOException {
        StringWriter sw = new StringWriter();
        mapper.writeValue(sw, event);
        return sw.toString();
    }
    
    public static String toJson(String title, Event event) throws IOException {
        String json = toJson(event);
        return String.format("%s|%s",title,Base64.getEncoder().encodeToString(json.getBytes()));
    }
    
    public static Event toObjectBase64(String dataBase64) throws IOException{
        try {
            String jsonBase64 = dataBase64;
            if(dataBase64.startsWith("data:")){
                jsonBase64 = dataBase64.substring(5);
            }
            String json = new String(Base64.getDecoder().decode(jsonBase64));
            return mapper.readValue(json, Event.class); 
        } catch (Exception e) {
            return null;
        }
    }

    public static Event toObject(String data){
        try {
            String json = data;
            if(data.startsWith("data:")){
                json = data.substring(5);
            }
            return mapper.readValue(json, Event.class);
        } catch (Exception e) {
            return null;
        }
        
    }    
    
}