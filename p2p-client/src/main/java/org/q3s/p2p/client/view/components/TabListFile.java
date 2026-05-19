/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.view.components;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.q3s.p2p.client.view.Controller;
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
    private Controller controller;

    private int tabType;
    /**
     * Creates new form JPanelListFile
     */
    public TabListFile(int tabType) {
        initComponents();
        jPanel1.setVisible(false);
        this.setTabType(tabType);
        back = jTable1.getBackground();
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem menuItemOpen = new JMenuItem("Abrir");
        menuItemOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openMenuItemActionPerformed(evt);
            }
        });
        popupMenu.add(menuItemOpen);

        JMenuItem menuItemDownload = new JMenuItem("Descargar");
        menuItemDownload.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                downloadMenuItemActionPerformed(evt);
            }
        });
        popupMenu.add(menuItemDownload);

        JMenuItem menuItemRemove = new JMenuItem("Eliminar");
        menuItemRemove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeMenuItemActionPerformed(evt);
            }
        });
        popupMenu.add(menuItemRemove);

        JMenuItem menuItemRefresh = new JMenuItem("Actualizar");
        menuItemRefresh.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshMenuItemActionPerformed(evt);
            }
        });
        popupMenu.add(menuItemRefresh);

        popupMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                QFile f = getSelectedItem();
                boolean isLocal = f != null && f.getOwner() != null
                        && controller != null && f.getOwner().equals(controller.getUser());
                menuItemOpen.setVisible(f != null);
                menuItemDownload.setVisible(f != null && !f.isDirectory() && !isLocal);
                menuItemRemove.setVisible(isLocal);
                menuItemRefresh.setVisible(isLocal);
            }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });

        jTable1.setComponentPopupMenu(popupMenu);

        jTable1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                selectPopupRow(evt);
            }

            public void mouseReleased(java.awt.event.MouseEvent evt) {
                selectPopupRow(evt);
            }

            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2 && controller != null) {
                    int row = jTable1.rowAtPoint(evt.getPoint());
                    if (row >= 0) {
                        if (jTable1.getRowSorter() != null) {
                            row = jTable1.getRowSorter().convertRowIndexToModel(row);
                        }
                        FileTableModel ftm = (FileTableModel) jTable1.getModel();
                        QFile f = (QFile) ftm.getValueAt(row, -1);
                        if (f != null && f.isDirectory() && f.getOwner() != null) {
                            controller.navigateTo(f.getOwner().getId(), f.getRelativePath());
                        } else if (f != null && !f.isDirectory() && f.getOwner() != null) {
                            controller.openFile(f.getOwner(), f);
                        }
                    }
                }
            }
        });

    }

    /**
     * @param wk
     * @param user el que notifica
     * @param httpClient
     */
    public TabListFile(Workspace wk, User user, Controller controller, int tabType) {
        this(tabType);
        this.wk = wk;
        this.user = user;
        this.controller = controller;
    }

    private void openMenuItemActionPerformed(ActionEvent evt) {
        QFile qFile = getSelectedItem();
        if (qFile == null) return;
        if (qFile != null && qFile.isDirectory() && qFile.getOwner() != null) {
            controller.navigateTo(qFile.getOwner().getId(), qFile.getRelativePath());
            return;
        }
        controller.openFile(user, qFile);
    }

    private void downloadMenuItemActionPerformed(ActionEvent evt) {
        QFile qFile = getSelectedItem();
        if (qFile == null) return;
        controller.downloadFile(user, qFile);
    }

    private void removeMenuItemActionPerformed(ActionEvent evt) {
        QFile qFile = getSelectedItem();
        if (qFile == null) return;
        controller.removeFile(user, qFile);
    }

    private void refreshMenuItemActionPerformed(ActionEvent evt) {
        controller.refreshFiles(user);
    }

    private QFile getSelectedItem() {
        int row=jTable1.getSelectedRow();
        if (row < 0) return null;
        if (jTable1.getRowSorter()!=null) {
            row = jTable1.getRowSorter().convertRowIndexToModel(row);
        }
        FileTableModel ftm = (FileTableModel) jTable1.getModel();
        QFile qFile = (QFile) ftm.getValueAt(row, -1);
        return qFile;
    }

    private void selectPopupRow(MouseEvent evt) {
        if (!evt.isPopupTrigger()) return;
        int row = jTable1.rowAtPoint(evt.getPoint());
        if (row >= 0) {
            jTable1.setRowSelectionInterval(row, row);
        }
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
        jPanel2 = new javax.swing.JPanel();
        backButton = new javax.swing.JButton("← Atrás");
        breadcrumbLabel = new javax.swing.JLabel("");

        backButton.setVisible(false);
        breadcrumbLabel.setVisible(false);
        backButton.addActionListener(e -> {
            if (controller != null && user != null) {
                controller.navigateBack(user.getId());
            }
        });

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

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addComponent(backButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(breadcrumbLabel)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(backButton)
                .addComponent(breadcrumbLabel))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 375, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
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

    public JTable getjTable1() {
        return jTable1;
    }

    private boolean offline = false;

    private Color back = null;
    
    public void disabled() {
        Color backgroud = Color.decode("#dedede");
        jTable1.setBackground(backgroud);
        offline = true;
    }

    public void enabled() {
        jTable1.setBackground(back);
        offline = false;
    }

    public boolean isOffline() {
        return offline;
    }


    public int getTabType() {
		return tabType;
	}

	public void setTabType(int tabType) {
		this.tabType = tabType;
	}

	public void updateBackButton(boolean hasNavigationPath) {
		backButton.setVisible(hasNavigationPath);
		breadcrumbLabel.setVisible(hasNavigationPath);
	}

	public void updateBreadcrumb(String path) {
		breadcrumbLabel.setText(path != null ? path : "");
	}

	public javax.swing.JButton getBackButton() {
		return backButton;
	}

	// Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JButton backButton;
    private javax.swing.JLabel breadcrumbLabel;
    // End of variables declaration//GEN-END:variables
}
