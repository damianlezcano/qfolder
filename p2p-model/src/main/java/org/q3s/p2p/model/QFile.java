package org.q3s.p2p.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class QFile {

    private String id;
    private String name;
    private long size;
    private long date;
    
    @JsonIgnore
    private User owner;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public long getDate() {
        return date;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    
    @Override
    public String toString() {
        // TODO Auto-generated method stub
        return String.format("QFile -> name: %s, size: %s, date: %s", name, size, date);
    }

}
