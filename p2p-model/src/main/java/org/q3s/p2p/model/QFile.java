package org.q3s.p2p.model;

public class QFile {

    private String name;
    private long size;
    private long date;

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

    @Override
    public String toString() {
        // TODO Auto-generated method stub
        return String.format("QFile -> name: %s, size: %s, date: %s", name, size, date);
    }

}
