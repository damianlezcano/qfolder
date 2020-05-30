
package org.q3s.p2p.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class User {

    private String id;
    private String name;
    private long date;
    private boolean online = true;
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
        return String.format("user: %s", id);
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
        List<QFile> f = new ArrayList<QFile>();
        if(files != null){
            for (QFile file : files) {
                file.setOwner(this);
                f.add(file);
            }
        }
        return f;
    }
    
//    public Object[][] filesToArray(){
//        Object[][] arr = new Object[files.size()][3];
//        for (int i = 0; i < files.size(); i++) {
//            QFile file = files.get(i);
//            arr[i][0] = file.getName();
//            arr[i][1] = file.getSize();
//            arr[i][2] = file.getDate();
//        }
//        return arr;
//    }

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
    
    public void copy(User user){
        if(user.getId() != null){
            this.id = user.getId();
        }
        if(user.getName() != null){
            this.name = user.getName();
        }
        if(user.getDate() != 0){
            this.date = user.getDate();
        }
        if(user.getFiles() != null && !user.getFiles().isEmpty()){
            this.files = user.getFiles();
        }
        if(user.getPassword() != null){
            this.password = user.getPassword();
        }
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

	public boolean isOnline() {
		return online;
	}

	public void setOnline(boolean online) {
		this.online = online;
	}

}
