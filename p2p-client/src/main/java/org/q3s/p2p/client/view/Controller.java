/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.view;

import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
import java.util.ArrayList;
import org.q3s.p2p.model.util.EventUtils;
import org.q3s.p2p.model.Event;


/**
 *
 * @author damianlezcano
 */
public class Controller {

    private Workspace wk;
    private User user = User.build(UUIDUtils.generate());

    private ObjectMapper objectMapper = new ObjectMapper();

    private HttpClient httpClient = new HttpClient();

    private SseClient sseClient;

    private View view = new View();

    private Logger log;
    
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
        
        view.getjTabbedPane().setTitleAt(1, String.format("Yo (%s)", hostname));
        
        view.getjPanel4().setVisible(false);

        view.setVisible(true);
        view.mostarJoinPanel();
    }

    private String getLocalHostName() {
        String hostname = "Unknown";
        try {
            InetAddress addr;
            addr = InetAddress.getLocalHost();
            hostname = addr.getHostName();
        } catch (UnknownHostException ex) {
            System.out.println("Hostname can not be resolved");
        }
        return hostname;
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
            
            Event e = new Event("Soy nuevo, notifico mis archivos",user.clone(files));
            httpClient.post(Config.buildWkNewUserNotifyFilesUri(wk), e);

        } else if ("Soy nuevo, notifico mis archivos".equals(event.getName())) {
            
            if(event.getUser().equals(user)){
                // renderizo archivos locales
            }else{
//            List<QFile> remoteFiles = (List<QFile>) jsonUtils.object(event.getName(), Event.class);
            List<QFile> files = FileUtils.files(view.getjTextField4().getText());
//            String jsonBase64 = jsonUtils.jsonBase64("Bienvenido usuario, te notifico mis archivos",files);
//            httpClient.post(Config.buildWkNewUserNotifyFilesUri(wk), jsonBase64);
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

}
