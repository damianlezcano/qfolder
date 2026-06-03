/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.DefaultListModel;
import javax.swing.SwingUtilities;

/**
 *
 * @author damianlezcano
 */
public class Logger {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private DefaultListModel listModel;

    public Logger(DefaultListModel model){
        this.listModel = model;
    }

    public void info(String msg){
        String date = FORMATTER.format(Instant.now());
        String f = String.format("%s - %s", date,msg);
        System.out.println(f);
        addToModel(f);
    }

    public void debug(String msg){
        String date = FORMATTER.format(Instant.now());
        String f = String.format("%s - %s", date,msg);
        System.out.println(f);
    }

    public void err(String msg){
        String date = FORMATTER.format(Instant.now());
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
