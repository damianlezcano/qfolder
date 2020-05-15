/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.model;

import java.io.IOException;
import org.q3s.p2p.model.util.EventUtils;

/**
 *
 * @author damianlezcano
 */
public class Event {

    private String name;
    private User user;
    private Workspace wk;
    
    
    public Event(){}
    
    public Event(String name){
        this.name = name;
    }

    public Event(String name,Workspace wk){
        this.name = name;
        this.wk = wk;
    }

    public Event(String name,User user){
        this.name = name;
        this.user = user;
    }
    
    public Event(String name,Workspace wk, User user){
        this.name = name;
        this.wk = wk;
        this.user = user;
    }
    
    public Event(Workspace wk, User user){
        this.wk = wk;
        this.user = user;
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Workspace getWk() {
        return wk;
    }

    public void setWk(Workspace wk) {
        this.wk = wk;
    }
    
    public String toJson() {
        try {
            return EventUtils.toJson(this);
        } catch (Exception e) {
            return "";
        }
    }

    public String toJsonBase64() {
        try {
            return EventUtils.toJsonBase64(this);
        } catch (Exception e) {
            return "";
        }
    }
    
    public Event build(String json){
        return EventUtils.toObject(json);
    }

    @Override
    public String toString() {
        return String.format("%s - %s - event: %s",wk,user,name);
    }
    
}