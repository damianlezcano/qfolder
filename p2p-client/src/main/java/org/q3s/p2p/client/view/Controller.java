/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.view;

/**
 *
 * @author damianlezcano
 */
public class Controller {

    public void start() {
        View main = new View();
        main.setVisible(true);
        main.mostarJoinPanel();
    }
    
}
