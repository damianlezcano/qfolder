/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.view.components;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.q3s.p2p.client.Config;
import org.q3s.p2p.client.util.HttpClient;
import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.QFile;
import org.q3s.p2p.model.User;
import org.q3s.p2p.model.Workspace;

/**
 *
 * @author damianlezcano
 */
public class TabListFile extends javax.swing.JPanel {

	private Workspace wk;
	private User user;
	private HttpClient httpClient;
	
    /**
     * Creates new form JPanelListFile
     */
    public TabListFile() {
        initComponents();
        jPanel1.setVisible(false);
        
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem menuItemAdd = new JMenuItem("Abrir");
        JMenuItem menuItemRemove = new JMenuItem("Descargar");

        menuItemAdd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openMenuItemActionPerformed(evt);
            }
        });

        menuItemRemove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                downloadMenuItemActionPerformed(evt);
            }
        });
        
        popupMenu.add(menuItemAdd);
        popupMenu.add(menuItemRemove);
                
        jTable1.setComponentPopupMenu(popupMenu);
        
    }
    
    /**
     * @param wk
     * @param user el que notifica
     * @param httpClient
     */
    public TabListFile(Workspace wk, User user, HttpClient httpClient) {
    	this();
    	this.wk = wk;
    	this.user = user;
    	this.httpClient = httpClient;
    }

    private void openMenuItemActionPerformed(ActionEvent evt) {
        System.out.println("abrir");
    }

    private void downloadMenuItemActionPerformed(ActionEvent evt) {
        System.out.println("descargar");
        FileTableModel ftm = (FileTableModel) jTable1.getModel();
        QFile qFile = (QFile) ftm.getValueAt(jTable1.getSelectedRow(), -1);
        Event event = new Event("Quiero descargar el archivo", user,qFile);
        httpClient.post(Config.buildWkToUserUri(wk, qFile.getOwner()), event);
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        jPanel1 = new javax.swing.JPanel();
        jTextField1 = new javax.swing.JTextField();

        jTable1.setAutoCreateRowSorter(true);
        jTable1.setBackground(new java.awt.Color(255, 255, 242));
        jTable1.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                jTable1KeyReleased(evt);
            }
        });
        jScrollPane1.setViewportView(jTable1);

        jTextField1.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                jTextField1KeyReleased(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTextField1)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 6, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 375, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 114, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jTable1KeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTable1KeyReleased
        if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_F) {
            jPanel1.setVisible(true);
            //getJFrame().pack();
            jTextField1.requestFocus();
            jTable1.setRowSorter(null);
        }
    }//GEN-LAST:event_jTable1KeyReleased

    private void jTextField1KeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTextField1KeyReleased
        if (evt.getKeyCode() == KeyEvent.VK_ESCAPE) {
            //borrarFiltro();
            jPanel1.setVisible(false);
            jTextField1.setText("");
            //getJFrame().pack();
            jTable1.requestFocus();
        } 
        TableRowSorter<TableModel> sorter = new TableRowSorter<TableModel>((jTable1.getModel())); 
        sorter.setRowFilter(RowFilter.regexFilter(jTextField1.getText()));
        jTable1.setRowSorter(sorter);
    }//GEN-LAST:event_jTextField1KeyReleased

    
//    public JFrame getJFrame() {
//        Component co = super.getParent();
//        do {
//            if (co instanceof JFrame) {
//                return (JFrame) co;
//            } else if (co == null) {
//                return null;
//            } else {
//                co = co.getParent();
//            }
//        } while (true);
//    }

    public JTable getjTable1() {
        return jTable1;
    }

    private boolean offline = false;
    
    public void disabled(){
        Color backgroud = Color.decode("#dedede");
        jTable1.setBackground(backgroud);
        offline = true;
    }

    public void enabled(){
        jTable1.setBackground(null);
        offline = false;
    }

    public boolean isOffline() {
        return offline;
    }
    
    

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JTextField jTextField1;
    // End of variables declaration//GEN-END:variables
}
