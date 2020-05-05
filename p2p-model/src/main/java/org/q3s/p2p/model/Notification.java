package org.q3s.p2p.model;

import java.util.Date;

public class Notification {

    public Notification(String text, Date time) {
           super();  
           this.text = text;
           this.time = time;
    } 

    public static Integer getNextJobId() {
          return ++jobId;
    } 

    private String text; 
    private Date time; 
    private static Integer jobId = 0; 
    
    @Override
    public String toString() {
    	return text + time;
    }
   
}