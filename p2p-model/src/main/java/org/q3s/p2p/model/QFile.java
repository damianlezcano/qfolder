package org.q3s.p2p.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class QFile {

    public static final String OPERATION_OPEN = "open";
    public static final String OPERATION_DOWNLOAD = "download";
    
	private String md5;
    private String name;
    private long size;
    private long date;
    private int parts;
    
    private String content;
    private String operation;
    
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

    public String getMD5() {
        return md5;
    }

    public void setMD5(String md5) {
        this.md5 = md5;
    }
    
    @Override
    public String toString() {
        return String.format("QFile -> name: %s, size: %s, date: %s", name, size, date);
    }

	public int getParts() {
		return parts;
	}

	public void setParts(int parts) {
		this.parts = parts;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String cont) {
		this.content = cont;
	}

	public String getOperation() {
		return operation;
	}

	public void setOperation(String operation) {
		this.operation = operation;
	}
	
}
