/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.DefaultListModel;
import javax.swing.SwingUtilities;

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
        addToModel(f);
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
        addToModel(f);
    }

    private void addToModel(String msg) {
        if (SwingUtilities.isEventDispatchThread()) {
            listModel.addElement(msg);
        } else {
            SwingUtilities.invokeLater(() -> listModel.addElement(msg));
        }
    }
}
