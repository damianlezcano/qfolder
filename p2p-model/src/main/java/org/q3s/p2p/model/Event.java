/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.model;

import org.q3s.p2p.model.util.EventUtils;

/**
 *
 * @author damianlezcano
 */
public class Event {

    private String name;
    private User user;
    private Workspace wk;
    private String response;
    private QFile file;
    
    public Event(){}
    
    public Event(String name){
        this.name = name;
    }

    public Event(String name,String response){
        this.name = name;
        this.response = response;
    }
    
    public Event(String name,Workspace wk){
        this.name = name;
        this.wk = wk;
    }

    public Event(String name,User user){
        this.name = name;
        this.user = user;
    }
    
    public Event(String name,User user, QFile file){
        this.name = name;
        this.user = user;
        this.file = file;
    }

    public Event(String name, QFile file){
        this.name = name;
        this.file = file;
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

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String toJsonBase64() {
        try {
            return EventUtils.toJsonBase64(this);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String toString() {
        return String.format("%s - %s - event: %s",wk,user,name);
    }

	public QFile getFile() {
		return file;
	}

	public void setFile(QFile file) {
		this.file = file;
	}
    
}