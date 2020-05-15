/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.util;

import java.util.Map;
import java.util.Map.Entry;
import org.q3s.p2p.model.Event;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 *
 * @author damianlezcano
 */
public class HttpClient {

    RestTemplate restTemplate = new RestTemplate();

    public String get(String url, Map<String, String> params) throws Exception {

        HttpHeaders requestHeaders = new HttpHeaders();
        //set up HTTP Basic Authentication Header
        requestHeaders.add("Accept", MediaType.APPLICATION_JSON_VALUE);

        //request entity is created with request headers
        HttpEntity<?> requestEntity = new HttpEntity<>(requestHeaders);

        //adding the query params to the URL
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url);
        for (Entry<String,String> e : params.entrySet()) {
            uriBuilder = uriBuilder.queryParam(e.getKey(), e.getValue());
        }

        ResponseEntity<String> responseEntity = restTemplate.exchange(
                uriBuilder.toUriString(),
                HttpMethod.GET,
                requestEntity,
                String.class
        );
        return responseEntity.getBody().trim();
    }

    public String get(String url) throws Exception {
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.set("Accept", MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<?> httpEntity = new HttpEntity<>(httpHeaders);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, httpEntity, String.class);
            return response.getBody().trim();            
        } catch (Exception e) {
            return "";
        }
    }

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public void post(String url, Object obj) {
        restTemplate.postForEntity(url, obj, String.class);
    }

    public void post(String url, Event event) {
        restTemplate.postForEntity(url, event, Event.class);
    }
    
}
