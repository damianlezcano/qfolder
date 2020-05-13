package org.q3s.p2p.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public class User {

    private String id;
    private String name;
    private long date;

    private List<QFile> files;
    
    private String password;
    
    public User() {
        super();
    }

    public User(String id) {
        super();
        this.id = id;
    }

    public User(String id, String name) {
        super();
        this.id = id;
        this.name = name;
    }

    public User(String uuid, String id, String name) {
        super();
        this.id = id;
        this.name = name;
    }

    @Override
    public int hashCode() {
        return (int) id.charAt(0);
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == null) {
            return false;
        }

        if (!(obj instanceof User)) {
            return false;
        }

        User usr = (User) obj;

        if (usr.id == null) {
            return false;
        }

        return usr.id.equals(id);
    }

    public static User build(String id) {
        return new User(id);
    }

    @Override
    public String toString() {
        return id + name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }
    
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<QFile> getFiles() {
        return files;
    }

    public void setFiles(List<QFile> files) {
        this.files = files;
    }
    
    public User buildRemoveDate() {
        long f = 1000 * 10;
        this.date = System.currentTimeMillis() + f;
        return this;
    }

    @JsonIgnore
    public boolean isReconnect() {
        if (this.date != 0l) {
            long current = System.currentTimeMillis();
            if (this.date > current) {
                return true;
            }
        }
        return false;
    }

    public User clone(List<QFile> files) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setPassword(password);
        u.setDate(date);
        u.setFiles(files);
        return u;
    }

}
