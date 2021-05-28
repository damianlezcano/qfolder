/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.model.util;

import java.io.IOException;
import java.util.Base64;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

/**
 *
 * @author damianlezcano
 */
public class EventUtils {

	static Jsonb jsonb = JsonbBuilder.create();
    
    public static String toJsonBase64(Object event) throws IOException {
        String json = toJson(event);
        return Base64.getEncoder().encodeToString(json.getBytes());
    }

    private static String toJson(Object event) throws IOException {
    	return jsonb.toJson(event);
    }
    
    @SuppressWarnings("unchecked")
	public static Object toObjectBase64(String dataBase64, Class clazz) {
        try {
            String jsonBase64 = dataBase64;
            if(dataBase64.startsWith("data:")){
                jsonBase64 = dataBase64.substring(5);
            }
            String json = new String(Base64.getDecoder().decode(jsonBase64));
            return jsonb.fromJson(json, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    
    
}