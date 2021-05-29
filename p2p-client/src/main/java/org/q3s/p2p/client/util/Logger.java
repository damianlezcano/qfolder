/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.DefaultListModel;

/**
 *
 * @author damianlezcano
 */
public class Logger {
    
    private String pattern = "dd/MM/yyyy HH:mm:ss";
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
    
    private DefaultListModel listModel;
    
    public Logger(DefaultListModel model){
        this.listModel = model;
    }
    
    public void info(String msg){
        String date = simpleDateFormat.format(new Date());
        String f = String.format("%s - %s", date,msg);
        System.out.println(f);
        listModel.addElement(f);
    }
    
    public void debug(String msg){
        String date = simpleDateFormat.format(new Date());
        String f = String.format("%s - %s", date,msg);
        System.out.println(f);
    }
    
    public void err(String msg){
        String date = simpleDateFormat.format(new Date());
        String f = String.format("%s - %s", date,msg);
        System.err.println(f);
        listModel.addElement(f);
    }
}
