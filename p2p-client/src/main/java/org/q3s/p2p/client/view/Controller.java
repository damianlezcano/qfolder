/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.view;

import org.q3s.p2p.client.view.components.TabListFile;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

import org.q3s.p2p.client.Config;
import org.q3s.p2p.client.util.FileUtils;
import org.q3s.p2p.client.util.HttpClient;
import org.q3s.p2p.client.util.Logger;
import org.q3s.p2p.client.util.SseClient;
import org.q3s.p2p.model.QFile;
import org.q3s.p2p.model.User;
import org.q3s.p2p.model.Workspace;
import org.q3s.p2p.model.util.UUIDUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import org.q3s.p2p.client.view.components.FileTableModel;
import org.q3s.p2p.model.Event;

/**
 *
 * @author damianlezcano
 */
public class Controller {

    private Workspace wk;
    private User user = User.build(UUIDUtils.generate());

    private List<User> remoteUsers = new ArrayList<User>();

    private ObjectMapper objectMapper = new ObjectMapper();

    private HttpClient httpClient = new HttpClient();

    private SseClient sseClient;

    private View view = new View();

    private Logger log;

    private boolean configChange = false;

    public void start() {

        System.out.println("# User ID : " + user.getId());

        DefaultListModel model = new DefaultListModel();
        view.getjList2().setModel(model);

        log = new Logger(model);

        String hostname = getLocalHostName();
        view.getjTextField3().setText(hostname);
        view.setTitle(hostname);
        user.setName(hostname);

        view.getjButton4().addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });

        view.getjButton5().addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton5ActionPerformed(evt);
            }
        });

        view.getjLabel4().addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                onJLabel4Click(evt);
            }
        });

        view.getjButton2().addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        view.getjCheckBox1().addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBox1ActionPerformed(evt);
            }
        });

        view.getjTextField3().addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                jTextField3KeyReleased(evt);
            }
        });

        view.getjTabbedPane().addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jTabbedPaneMouseClicked(evt);
            }
        });

        view.getjButton3().addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        loadLocalGeneralTab();
        view.pack();
        view.setVisible(true);
        view.mostarJoinPanel();
    }

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int option = fileChooser.showOpenDialog(view);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            view.getjTextField4().setText(file.getAbsolutePath());

            List<QFile> files = FileUtils.files(view.getjTextField4().getText());
            Event e = new Event("Notifico Cambio en los archivos", user.clone(files));
            httpClient.post(Config.buildWkBroadcastUri(wk), e);
        }
    }

    private void jTabbedPaneMouseClicked(java.awt.event.MouseEvent evt) {
        if (configChange) {
            Event e = new Event("Cambio de nombre", user);
            httpClient.post(Config.buildWkBroadcastUri(wk), e);
            configChange = false;
        }
    }

    private void jTextField3KeyReleased(java.awt.event.KeyEvent evt) {
        int idx = searchTabById(user.getId());
        user.setName(view.getjTextField3().getText());
        view.getjTabbedPane().setTitleAt(idx, String.format("Yo (%s)", user.getName()));
        configChange = true;
    }

    private String getLocalHostName() {
        return System.getenv("USER");
    }

    /*
    * Boton crear workspace
     */
    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {
        try {
            //Armo entidad wk
            Workspace wk = new Workspace();
            wk.setDate(new Date().getTime());
            wk.setName(view.getjTextField5().getText());
            if (view.getjCheckBox1().isSelected()) {
                if (Arrays.equals(view.getjPasswordField1().getPassword(), view.getjPasswordField2().getPassword())) {
                    wk.setPassword(String.valueOf(view.getjPasswordField1().getPassword()));
                    view.getjLabel17().setText("");
                } else {
                    view.getjLabel17().setText("Las claves no coinciden");
                    return;
                }
            }
            //llamar a github para obtener la URL del servidor
            if (Config.URL_SERVER == null) {
                Config.URL_SERVER = httpClient.get(Config.URL_GITHUB_SERVER_INF);
            }

            //llamamos al servidor de p2p-server para pedir el workpaceId
            String wkId = httpClient.get(Config.buildWkCreateUri(), wk.buildRequestParam());
            view.getjTextField2().setText(wkId);
        } catch (Exception ex) {
            mostrarErrorEnPantallaLogin("Error al crear el espacio de trabajo: " + ex.getCause().getMessage());
        }
        view.getjPanelCreateWorkspace().setVisible(false);
        view.getjPanelJoin().setVisible(true);
        view.getjTabbedPane().setVisible(false);
        view.getjPanelProxy().setVisible(false);
        view.setTitle(view.getjTextField3().getText());
    }

    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {
        view.getjPanelCreateWorkspace().setVisible(false);
        view.getjPanelJoin().setVisible(true);
        view.getjTabbedPane().setVisible(false);
        view.getjPanelProxy().setVisible(false);
        view.setTitle(view.getjTextField3().getText());
    }

    private void onJLabel4Click(java.awt.event.MouseEvent evt) {
        view.getjPanelCreateWorkspace().setVisible(true);
        view.getjPanelJoin().setVisible(false);
        view.getjTabbedPane().setVisible(false);
        view.getjPanelProxy().setVisible(false);
        view.setTitle("Crear espacio de trabajo");
        mostrarErrorEnPantallaLogin("");
    }

    private void mostrarErrorEnPantallaLogin(String message) {
        if (view.getjPanelJoin().isVisible()) {
            view.getjLabel16().setText("<html>" + message + "</html>");
        }
    }

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {
        String uuid = view.getjTextField2().getText();
        user.setName(view.getjTextField3().getText());
        wk = Workspace.build(uuid);
        if (sseClient != null) {
            try {
                sseClient.interrupt();
            } catch (Throwable e) {
            }
        }
        sseClient = new SseClient(wk, user, this, log);
        sseClient.start();

    }

    private void jCheckBox1ActionPerformed(java.awt.event.ActionEvent evt) {
        JCheckBox cb = (JCheckBox) evt.getSource();
        if (cb.isSelected()) {
            view.getjPasswordField1().setEnabled(true);
            view.getjPasswordField2().setEnabled(true);
        } else {
            view.getjPasswordField1().setEnabled(false);
            view.getjPasswordField2().setEnabled(false);
        }
    }

    public void notify(Event event) throws Exception {
        log.info(event.getName());
        if ("Wk no existe, desconectar".equals(event.getName())) {
            mostrarErrorEnPantallaLogin("El espacio de trabajo no existe");
        } else if ("Wk existente, sin credenciales".equals(event.getName())) {
            //envio datos del usuario
            httpClient.post(Config.buildWkConnectWithoutAuthUri(wk), user);
        } else if ("Wk existente, con credenciales".equals(event.getName())) {
            //Credenciales
            JPasswordField pf = new JPasswordField();
            //Create OptionPane & Dialog
            JOptionPane pane = new JOptionPane(pf, JOptionPane.INFORMATION_MESSAGE, JOptionPane.OK_OPTION);
            JDialog dialog = pane.createDialog(view, "Clave de Acceso");
            //Add a listener to the dialog to request focus of Password Field
            dialog.addComponentListener(new ComponentListener() {
                @Override
                public void componentShown(ComponentEvent e) {
                    pf.requestFocusInWindow();
                }

                @Override
                public void componentHidden(ComponentEvent e) {
                }

                @Override
                public void componentResized(ComponentEvent e) {
                }

                @Override
                public void componentMoved(ComponentEvent e) {
                }
            });

            dialog.setVisible(true);
            int okCxl = (int) pane.getValue();
            if (okCxl == JOptionPane.OK_OPTION) {
                String password = new String(pf.getPassword());
                user.setPassword(password);
                //envio datos del usuario
                httpClient.post(Config.buildWkConnectWithAuthUri(wk), user);
            }

        } else if ("Bienvenido usuario al grupo!".equals(event.getName())) {
            view.getjPanelCreateWorkspace().setVisible(false);
            view.getjPanelJoin().setVisible(false);
            view.getjTabbedPane().setVisible(true);
            view.getjPanelProxy().setVisible(false);
            view.getjTextField6().setText(view.getjTextField2().getText());

            wk = event.getWk();
            wk.setName(wk.getName());

            view.setTitle("Conectado al grupo '" + wk.getName() + "'");

            List<QFile> files = FileUtils.files(view.getjTextField4().getText());

            Event e = new Event("Gracias por la bienvenida, notifico mis archivos", user.clone(files));
            httpClient.post(Config.buildWkBroadcastUri(wk), e);

        } else if ("Gracias por la bienvenida, notifico mis archivos".equals(event.getName())) {
            if (event.getUser().equals(user)) {
                // renderizo archivos locales
                String username = String.format("Yo (%s)", user.getName());
                loadLocalUserTab(username, event.getUser());
            } else {
                loadRemoteUserTab(event.getUser().getName(), event.getUser());
                List<QFile> files = FileUtils.files(view.getjTextField4().getText());
                Event e = new Event("Estos son mis archivos", user.clone(files));
                httpClient.post(Config.buildWkToUserUri(wk, event.getUser()), e);
            }
            addUserToRemoteList(event.getUser());
        } else if ("Estos son mis archivos".equals(event.getName())) {
            loadRemoteUserTab(event.getUser().getName(), event.getUser());
            addUserToRemoteList(event.getUser());
        } else if ("Notifico Cambio en los archivos".equals(event.getName())) {
            if (event.getUser().equals(user)) {
                //loadLocalUserTab(event.getUser().getName(), event.getUser());
                addUserToRemoteList(event.getUser());
            } else {
                //loadRemoteUserTab(event.getUser().getName(), event.getUser());
                addUserToRemoteList(event.getUser());
            }
        } else if ("Cambio de nombre".equals(event.getName())) {
            if (!event.getUser().equals(user)) {
                int idx = searchTabById(event.getUser().getId());
                int idxUser = remoteUsers.indexOf(event.getUser());
                remoteUsers.get(idxUser).setName(event.getUser().getName());
                view.getjTabbedPane().setTitleAt(idx, event.getUser().getName());
            }
        } else if ("Credenciales incorrectas".equals(event.getName())) {
            mostrarErrorEnPantallaLogin("Credenciales incorrectas");
        } else if ("Aprobar al usuario".equals(event.getName())) {

            User to = event.getUser();

            int opt = JOptionPane.showConfirmDialog(view, "Permitir el acceso al usuario '" + to.getName() + "'", "Aviso!", JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION);

            if (opt == 0) {
                httpClient.get(Config.buildWkSendApprovedUserUrl(wk, to));
            } else {
                httpClient.get(Config.buildWkSendRefuseUserUrl(wk, to));
            }
        } else if ("Usuario rechazado!".equals(event.getName())) {
            try {
                sseClient.interrupt();
            } catch (Throwable e) {
            }
            mostrarErrorEnPantallaLogin("Has sido rechazado tu ingreso por los usuarios");
        }
    }

    private void addUserToRemoteList(User user) {
        int idx = remoteUsers.indexOf(user);
        if(idx != -1){
            User us = remoteUsers.get(idx);
            us.copy(user);
        }else{
            remoteUsers.add(user);
        }
    }

    private void loadLocalGeneralTab() {
        String username = "General";
        TabListFile jPanel2 = new TabListFile();
        jPanel2.setName(username);
        view.getjTabbedPane().insertTab(username, null, jPanel2, "Vista unificada de Archivos", 0); // NOI18N
        FileTableModel ftm = new FileTableModel(remoteUsers);
        jPanel2.getjTable1().setModel(ftm);
        jPanel2.getjTable1().getColumnModel().getColumn(0).setWidth(30);
        jPanel2.getjTable1().getColumnModel().getColumn(0).setMinWidth(30);
        jPanel2.getjTable1().getColumnModel().getColumn(0).setMaxWidth(30);
        jPanel2.getjTable1().getColumnModel().getColumn(2).setWidth(100);
        jPanel2.getjTable1().getColumnModel().getColumn(2).setMinWidth(100);
        jPanel2.getjTable1().getColumnModel().getColumn(2).setMaxWidth(100);
        jPanel2.getjTable1().getColumnModel().getColumn(3).setWidth(150);
        jPanel2.getjTable1().getColumnModel().getColumn(3).setMinWidth(150);
        jPanel2.getjTable1().getColumnModel().getColumn(3).setMaxWidth(150);
        jPanel2.getjTable1().getColumnModel().getColumn(4).setWidth(150);
        jPanel2.getjTable1().getColumnModel().getColumn(4).setMinWidth(150);
        jPanel2.getjTable1().getColumnModel().getColumn(4).setMaxWidth(150);
    }

    private void loadRemoteUserTab(String username, User user) {
        loadUserTab(username, user, user.getId(), "/status-ok.png", 2);
    }

    private void loadLocalUserTab(String username, User user) {
        loadUserTab(username, user, "Tus archivos locales", "/home.png", 1);
    }

    private void loadUserTab(String username, User user, String tooltip, String iconpath, int iconidx) {
        TabListFile jPanel2 = new TabListFile();
        jPanel2.setName(user.getId());
        ImageIcon icon = new javax.swing.ImageIcon(getClass().getResource(iconpath));
        view.getjTabbedPane().insertTab(username, icon, jPanel2, tooltip, iconidx); // NOI18N
        FileTableModel ftm = new FileTableModel(user);
        jPanel2.getjTable1().setModel(ftm);
        jPanel2.getjTable1().getColumnModel().getColumn(0).setWidth(30);
        jPanel2.getjTable1().getColumnModel().getColumn(0).setMinWidth(30);
        jPanel2.getjTable1().getColumnModel().getColumn(0).setMaxWidth(30);
        jPanel2.getjTable1().getColumnModel().getColumn(2).setWidth(100);
        jPanel2.getjTable1().getColumnModel().getColumn(2).setMinWidth(100);
        jPanel2.getjTable1().getColumnModel().getColumn(2).setMaxWidth(100);
        jPanel2.getjTable1().getColumnModel().getColumn(3).setWidth(150);
        jPanel2.getjTable1().getColumnModel().getColumn(3).setMinWidth(150);
        jPanel2.getjTable1().getColumnModel().getColumn(3).setMaxWidth(150);
        jPanel2.getjTable1().getColumnModel().getColumn(4).setWidth(0);
        jPanel2.getjTable1().getColumnModel().getColumn(4).setMinWidth(0);
        jPanel2.getjTable1().getColumnModel().getColumn(4).setMaxWidth(0);
    }

    private int searchTabById(String id) {
        Component[] cos = view.getjTabbedPane().getComponents();
        for (int i = 0; i < cos.length; i++) {
            if (cos[i] instanceof TabListFile) {
                if (cos[i].getName().endsWith(id)) {
                    return view.getjTabbedPane().indexOfComponent(cos[i]);
                }
            }
        }
        return -1;
    }

}
