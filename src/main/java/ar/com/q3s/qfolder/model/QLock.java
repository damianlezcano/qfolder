package ar.com.q3s.qfolder.model;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.bind.annotation.XmlTransient;


public class QLock {

	private String user;
	private Date date;
	
	//----------------------------
	
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}
	@XmlTransient
	public Date getDate() {
		return date;
	}
	public void setDate(Date date) {
		this.date = date;
	}
	public String getDateAsString(){
        SimpleDateFormat dt1 = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        return dt1.format(getDate());
	}
	
}