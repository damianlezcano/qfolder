package org.q3s.p2p.model;

import java.util.HashMap;
import java.util.Map;

public class Workspace {

    private String id;
    private String name;
    private String password;
    private long date;

    public Workspace() {
        super();
    }

    public Workspace(String id) {
        super();
        this.id = id;
    }

    public Workspace(String id, String name) {
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

        if (!(obj instanceof Workspace)) {
            return false;
        }

        Workspace usr = (Workspace) obj;

        if (usr.id == null) {
            return false;
        }

        return usr.id.equals(id);
    }

    public static Workspace build(String id) {
        return new Workspace(id);
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

    public Map<String, String> buildRequestParam(){
         Map<String, String> params = new HashMap<String, String>();
         params.put("name", name);
         params.put("date", String.valueOf(date));
         params.put("password", password);
         return params;
    }
    
    public boolean requiredCredential(){
        return (password != null && !password.isEmpty());
    }

}
