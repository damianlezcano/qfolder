package ar.com.q3s.qfolder.model;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;


public class QFile implements Comparable<QFile> {

	private String name;
	private long size;
	private Date lastModified;
	private QLock lock;
	
	//------------------------------------
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	@XmlTransient
	public long getSize() {
		return size;
	}
	public void setSize(long size) {
		this.size = size;
	}
	@XmlElement(name="size")
	public String getSizeAsString(){
		return humanReadableByteCount(getSize(), true);
	}
	
	public QLock getLock() {
		return lock;
	}
	public void setLock(QLock lock) {
		this.lock = lock;
	}
	@XmlTransient
	public Date getLastModified() {
		return lastModified;
	}
	public void setLastModified(Date lastModified) {
		this.lastModified = lastModified;
	}
	@XmlElement(name="lastModified")
	public String getLastModifiedAsString() {
        SimpleDateFormat dt1 = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        return dt1.format(getLastModified());
	}
	@Override
	public int compareTo(QFile o) {
		return name.compareTo(o.getName());
	}
	
	public static String humanReadableByteCount(long bytes, boolean si) {
	    int unit = si ? 1000 : 1024;
	    if (bytes < unit) return bytes + " bytes";
	    int exp = (int) (Math.log(bytes) / Math.log(unit));
	    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
	    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
}