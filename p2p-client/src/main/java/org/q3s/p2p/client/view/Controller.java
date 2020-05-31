/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.view;

import java.awt.Component;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTable;

import org.q3s.p2p.client.Config;
import org.q3s.p2p.client.exec.Executor;
import org.q3s.p2p.client.exec.ExecutorFactoryBean;
import org.q3s.p2p.client.util.FileUtils;
import org.q3s.p2p.client.util.HttpClient;
import org.q3s.p2p.client.util.Logger;
import org.q3s.p2p.client.util.SseClient;
import org.q3s.p2p.client.view.components.FileTableModel;
import org.q3s.p2p.client.view.components.TabListFile;
import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.QFile;
import org.q3s.p2p.model.User;
import org.q3s.p2p.model.Workspace;
import org.q3s.p2p.model.util.UUIDUtils;

/**
 *
 * @author damianlezcano
 */
public class Controller {

    private Workspace wk;
    private User user = User.build(UUIDUtils.generate());

    private List<User> remoteUsers = new ArrayList<User>();

    private HttpClient httpClient = new HttpClient(this);

    private SseClient sseClient;

    private View view = new View();

    private Logger log;

    private boolean configChange = false;

    private Executor exec = ExecutorFactoryBean.create();

    @SuppressWarnings({ "rawtypes", "unchecked" })
	public void start() {

        removeTemp();

        DefaultListModel model = new DefaultListModel();
        view.getjList2().setModel(model);

        log = new Logger(model);

        log.info("Usuario ID: " + user.getId());
        
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

        view.getjTextField3().addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jTextField3FocusLost(evt);
            }
        });

        view.getjButton3().addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        view.getjTabbedPane().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    JTabbedPane tp = (JTabbedPane) evt.getSource();
                    Component co = tp.getSelectedComponent();
                    if (co instanceof TabListFile) {
                        TabListFile tlf = (TabListFile) co;
                        if (tlf.isOffline()) {
                            tp.removeTabAt(tp.getSelectedIndex());
                        }
                    }
                }
            }
        });

        view.getjLabel6().addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                Jabel6ActionPerformed(evt);
            }
        });
        
        view.getjCheckBox3().addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBox3ActionPerformed(evt);
            }
        });
        
        view.getjButton7().addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton7ActionPerformed(evt);
            }
        });
        
        view.getjButton8().addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton8ActionPerformed(evt);
            }
        });
        
        view.getjCheckBox2().addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBox2ActionPerformed(evt);
            }
        });
        
        view.pack();
        view.setVisible(true);
        view.mostarJoinPanel();
        
        httpClient.verifyServiceGithubUp();
        
    }
    
    private void jCheckBox3ActionPerformed(java.awt.event.ActionEvent evt) {                                           
        JCheckBox cb = (JCheckBox) evt.getSource();
        if (cb.isSelected()) {
            view.getjTextField9().setEnabled(true);
            view.getjTextField8().setEnabled(true);
            view.getjCheckBox2().setEnabled(true);
            if(view.getjCheckBox2().isSelected()){
                view.getjTextField7().setEnabled(true);
                view.getjPasswordField4().setEnabled(true);
            }
        }else{
            view.getjTextField9().setEnabled(false);
            view.getjTextField8().setEnabled(false);
            view.getjCheckBox2().setEnabled(false);
            view.getjTextField7().setEnabled(false);
            view.getjPasswordField4().setEnabled(false);
        }
    } 
    
    private void jCheckBox2ActionPerformed(java.awt.event.ActionEvent evt) {                                           
        JCheckBox cb = (JCheckBox) evt.getSource();
        if (cb.isSelected()) {
            view.getjTextField7().setEnabled(true);
            view.getjPasswordField4().setEnabled(true);
        }else{
            view.getjTextField7().setEnabled(false);
            view.getjPasswordField4().setEnabled(false);
        }
    } 
    
    //Proxy boton volver
    private void jButton7ActionPerformed(java.awt.event.ActionEvent evt) { 
        mostrarErrorEnPantallaLogin("");
        view.getjPanelCreateWorkspace().setVisible(false);
        view.getjPanelJoin().setVisible(true);
        view.getjTabbedPane().setVisible(false);
        view.getjPanelProxy().setVisible(false);
        view.setTitle(getLocalHostName());
    } 
    
    //Proxy boton aceptar
    private void jButton8ActionPerformed(java.awt.event.ActionEvent evt) {                                         
        mostrarErrorEnPantallaLogin("");
        view.getjPanelCreateWorkspace().setVisible(false);
        view.getjPanelJoin().setVisible(true);
        view.getjTabbedPane().setVisible(false);
        view.getjPanelProxy().setVisible(false);
        view.setTitle(getLocalHostName());
        
        Properties props = System.getProperties();
        if(view.getjCheckBox3().isSelected()){
            props.put("http.proxyHost", view.getjTextField9().getText());
            props.put("http.proxyPort", view.getjTextField8().getText());
        }else{
            props.remove("http.proxyHost");
            props.remove("http.proxyPort");
        }
        httpClient.verifyServiceGithubUp();
    }  

    private void Jabel6ActionPerformed(java.awt.event.MouseEvent evt) {                                       
        view.getjTextField4().setText(".");
        refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
    }  
    
    //Evento pierdo foco cambio de nombre
    private void jTextField3FocusLost(java.awt.event.FocusEvent evt) {
        if (configChange) {
            Event e = new Event("Cambio de nombre", user);
            httpClient.sendBroadcastWk(wk, e);
            configChange = false;
        }
    }

    private void removeTemp() {
        try {
            FileUtils.remove(Paths.get(Config.TEMP_PATH));
        } catch (Exception e) {
            System.err.println("Error al borrar el directorio temporal -> " + e.getMessage());
        }
    }

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int option = fileChooser.showOpenDialog(view);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            view.getjTextField4().setText(file.getAbsolutePath());
            refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
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
        httpClient.createWk(wk);
    }

    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {
        view.getjPanelCreateWorkspace().setVisible(false);
        view.getjPanelJoin().setVisible(true);
        view.getjTabbedPane().setVisible(false);
        view.getjPanelProxy().setVisible(false);
        view.setTitle(view.getjTextField3().getText());
    }

    private void onJLabel4Click(java.awt.event.MouseEvent evt) {
        if(view.getjLabel4().isEnabled()){
            mostrarErrorEnPantallaLogin("");
            view.getjPanelCreateWorkspace().setVisible(true);
            view.getjPanelJoin().setVisible(false);
            view.getjTabbedPane().setVisible(false);
            view.getjPanelProxy().setVisible(false);
            view.setTitle("Crear espacio de trabajo");
        }
    }

    private void mostrarErrorEnPantallaLogin(String message) {
//        if (view.getjPanelJoin().isVisible()) {
            view.getjLabel16().setText("<html>" + message + "</html>");
//        }
    }

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {
        String uuid = view.getjTextField2().getText();
        
        if(uuid.isEmpty()){
            mostrarErrorEnPantallaLogin("Falta completar el ID del grupo de trabajo");
        }else{
            user.setName(view.getjTextField3().getText());
            wk = Workspace.build(uuid);
            view.getjTextField2().setEnabled(false);
            view.getjButton2().setEnabled(false);
            view.getjLabel4().setEnabled(false);
            view.getjButton6().setEnabled(false);
            mostrarErrorEnPantallaLogin("");
            sseClient = new SseClient(wk, user, this, log);
            sseClient.start();
        }
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

    public void notify(Event event) {
        try {
            if(event.getName() != null){
                log.info(event.getName());
                if ("Wk no existe, desconectar".equals(event.getName())) {
                    sseClient.interrupt();
                    view.getjButton2().setEnabled(true);
                    view.getjLabel4().setEnabled(true);
                    view.getjTextField2().setEnabled(true);
                    view.getjTextField2().setFocusable(true);
                    view.getjButton6().setEnabled(true);
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
                        public void componentHidden(ComponentEvent e) {}

                        @Override
                        public void componentResized(ComponentEvent e) {}

                        @Override
                        public void componentMoved(ComponentEvent e) {}
                    });

                    dialog.setVisible(true);
                    int okCxl = (int) pane.getValue();
                    if (okCxl == JOptionPane.OK_OPTION) {
                        String password = new String(pf.getPassword());
                        user.setPassword(password);
                        //envio datos del usuario
                        httpClient.post(Config.buildWkConnectWithAuthUri(wk), user);
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
                    sseClient.interrupt();
                    mostrarErrorEnPantallaLogin("Has sido rechazado tu ingreso por los usuarios");
                } else if ("Bienvenido usuario al grupo!".equals(event.getName())) {
                    loadLocalGeneralTab();
                    view.getjPanelCreateWorkspace().setVisible(false);
                    view.getjPanelJoin().setVisible(false);
                    view.getjTabbedPane().setVisible(true);
                    view.getjPanelProxy().setVisible(false);
                    view.getjTextField6().setText(view.getjTextField2().getText());

                    wk = event.getWk();
                    wk.setName(wk.getName());

                    view.setTitle("Conectado al grupo '" + decodeValue(wk.getName()) + "'");

                    refreshLocalFilesAndNotify("Gracias por la bienvenida, notifico mis archivos");

                    ping();//TODO: borrar cuando se resuelva el issue SSE con webflux

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
                    notifyChangeFiles(event);
                } else if ("Se borro un archivo".equals(event.getName())) {
                    notifyChangeFiles(event);
                } else if ("Cambio de nombre".equals(event.getName())) {
                	int idx = searchTabById(event.getUser().getId());
                    int idxUser = remoteUsers.indexOf(event.getUser());
                    remoteUsers.get(idxUser).setName(event.getUser().getName());
                    refreshTables();
                    if (!event.getUser().equals(user)) {
                        view.getjTabbedPane().setTitleAt(idx, event.getUser().getName());                        
                    }
                } else if ("Usuario desconectado".equals(event.getName())) {
                	TabListFile tlf = findTableByUserId(event.getUser().getId());
                	if(tlf.getTabType() == 2) {
	                	int idx = searchTabById(event.getUser().getId());
	                    ImageIcon icon = new javax.swing.ImageIcon(getClass().getResource("/status-fail.png"));
	                    view.getjTabbedPane().setIconAt(idx, icon);
	                    view.getjTabbedPane().setToolTipTextAt(idx, "Última vez conectado " + new Date() + " (Doble click para cerrar)");
	                    tlf.disabled();
	                    int idxUser = remoteUsers.indexOf(event.getUser());
	                    remoteUsers.get(idxUser).setOnline(false);
	                    refreshTables();
                	}
                } else if ("Quiero descargar el archivo".equals(event.getName())) {
                    String filename = event.getFile().getName();
                    String filepath = view.getjTextField4().getText();
                    String fullname = filepath + File.separator + filename;
                    //-----------------------------------------------------------
                    QFile qfile = event.getFile();
                    String content = FileUtils.encoder(fullname);
                    Path path = FileUtils.saveInTempDirectory(filename, content);
                    String md5 = FileUtils.md5(path.toString());
                    qfile.setMD5(md5);
                    File fus = FileUtils.md5Folder(wk,event.getFile().getMD5(), "out");
                    int parts = FileUtils.splitFile(path.toString(), Config.FILE_PART_SIZE_IN_KB, fus.getAbsolutePath());
                    qfile.setParts(parts);
                    Event e = new Event("Envio el detalle de las partes del archivo", user, qfile);
                    httpClient.post(Config.buildWkToUserUri(wk, event.getUser()), e);
                } else if ("Envio el detalle de las partes del archivo".equals(event.getName())) {
                    File fus = FileUtils.md5Folder(wk,event.getFile().getMD5(), "in");
                    for (int i = 0; i < event.getFile().getParts(); i++) {
                        File t = new File(fus.getAbsolutePath() + File.separator + i + Config.SUFFIX_PENDING);
                        t.createNewFile();
                    }
                    retriveAnyPendingFileAndRequest(event, fus);
                } else if ("Dame la parte numero".equals(event.getName())) {
                    QFile file = event.getFile();
                    File md5Dir = FileUtils.md5Folder(wk,file.getMD5(), "out");
                    String md5Part = md5Dir.getAbsolutePath() + File.separator + file.getParts() + Config.SUFFIX_PART;
                    String content = FileUtils.read(md5Part);
                    file.setContent(content);
                    Event e = new Event("Esta es la parte que me pedistes", user, file);
                    httpClient.post(Config.buildWkToUserUri(wk, event.getUser()), e);
                } else if ("Esta es la parte que me pedistes".equals(event.getName())) {
                    QFile file = event.getFile();
                    File md5Dir = FileUtils.md5Folder(wk,file.getMD5(), "in");
                    FileUtils.save(md5Dir.getAbsolutePath() + File.separator + Config.PREFFIX_ENCODE + Config.SUFFIX_ENCODE, file.getContent().getBytes());
                    String md5Part = md5Dir.getAbsolutePath() + File.separator + file.getParts();
                    FileUtils.remove(Paths.get(md5Part + Config.SUFFIX_PENDING));
                    retriveAnyPendingFileAndRequest(event, md5Dir);
                }else if("Error al intentar conectarse con el servidor".equals(event.getName())){

                }else if("URL servicio resuelta".equals(event.getName())){
                    view.getjButton2().setEnabled(true);
                    view.getjLabel4().setEnabled(true);
                    view.getjTextField2().setEnabled(true);
                    view.getjTextField2().setFocusable(true);
                    Config.URL_SERVER = event.getResponse();
                }else if("Error al intentar recuperar la URL del servicio".equals(event.getName())){
                    mostrarErrorEnPantallaLogin("Verifique su conexion a internet o configure el proxy.");
                }else if("Workspace creado".equals(event.getName())){
                    view.getjTextField2().setText(event.getResponse());
                    view.getjPanelCreateWorkspace().setVisible(false);
                    view.getjPanelJoin().setVisible(true);
                    view.getjTabbedPane().setVisible(false);
                    view.getjPanelProxy().setVisible(false);
                    view.setTitle(view.getjTextField3().getText());
                    mostrarErrorEnPantallaLogin("");
                }else if("Error al crear el workspace".equals(event.getName())){
                    view.getjPanelCreateWorkspace().setVisible(false);
                    view.getjPanelJoin().setVisible(true);
                    view.getjTabbedPane().setVisible(false);
                    view.getjPanelProxy().setVisible(false);
                    view.setTitle(user.getName());
                    mostrarErrorEnPantallaLogin("Error al crear el espacio de trabajo: " + event.getResponse());
                }else if("No es posible establecer una conexion".equals(event.getName())){
                    view.getjPanelCreateWorkspace().setVisible(false);
                    view.getjPanelJoin().setVisible(true);
                    view.getjTabbedPane().setVisible(false);
                    view.getjPanelProxy().setVisible(false);
                    view.setTitle(user.getName());
                    
                    view.getjButton2().setEnabled(true);
                    view.getjLabel4().setEnabled(true);
                    view.getjTextField2().setEnabled(true);
                    view.getjTextField2().setFocusable(true);
                    view.getjButton6().setEnabled(true);
                    mostrarErrorEnPantallaLogin("No es posible conectarse al grupo de trabajo. Vuelva a intentarlo mas tarde.");
                }else if("Se perdio la conexion con el servidor".equals(event.getName())){
                    view.getjPanelCreateWorkspace().setVisible(false);
                    view.getjPanelJoin().setVisible(true);
                    view.getjTabbedPane().setVisible(false);
                    view.getjPanelProxy().setVisible(false);
                    view.setTitle(user.getName());
                    
                    view.getjButton2().setEnabled(true);
                    view.getjLabel4().setEnabled(true);
                    view.getjTextField2().setEnabled(true);
                    view.getjTextField2().setFocusable(true);
                    view.getjButton6().setEnabled(true);
                    mostrarErrorEnPantallaLogin("Se perdio la conexion con el servidor");
                    
                    remoteUsers = new ArrayList<User>();
                    httpClient = new HttpClient(this);
                    removeAllTab();
                    //JOptionPane.showMessageDialog(view, "Se perdio la conexion con el servidor", "Error de conexion", JOptionPane.ERROR_MESSAGE);
                    //System.exit(0);
                }
            }
        } catch (Exception e) {
            
        }
    }

	public static String decodeValue(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }
    
    private void notifyChangeFiles(Event event) {
    	addUserToRemoteList(event.getUser());
        refreshTables();
    }

    private void refreshLocalFilesAndNotify(String msg) {
        List<QFile> files = FileUtils.files(view.getjTextField4().getText());
        Event e = new Event(msg, user.clone(files));
        httpClient.post(Config.buildWkBroadcastUri(wk), e);
    }

    private void retriveAnyPendingFileAndRequest(Event event, File md5Dir) throws Exception {
        File file = FileUtils.md5Folder(wk,event.getFile().getMD5(), "in");
        int part = FileUtils.anyPending(file.getAbsolutePath());
        if (part == -1) {
            String from = md5Dir.getAbsolutePath() + File.separator + Config.PREFFIX_ENCODE;
            String to = null;
            byte[] dec = FileUtils.decode(from + Config.SUFFIX_ENCODE);
            FileUtils.save(from + Config.SUFFIX_DECODE, dec);
            if (QFile.OPERATION_DOWNLOAD.equals(event.getFile().getOperation())) {
                to = view.getjTextField4().getText() + File.separator + event.getFile().getName();
                FileUtils.move(from + Config.SUFFIX_DECODE, to);
            } else {
                to = md5Dir.getAbsolutePath() + File.separator + event.getFile().getName();
                FileUtils.move(from + Config.SUFFIX_DECODE, to);
                exec.open(to);
            }
            refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
        } else {
            event.getFile().setParts(part);
            Event e = new Event("Dame la parte numero", user, event.getFile());
            httpClient.post(Config.buildWkToUserUri(wk, event.getUser()), e);
        }
    }

    private void addUserToRemoteList(User user) {
        int idx = remoteUsers.indexOf(user);
        if (idx != -1) {
            User us = remoteUsers.get(idx);
            us.copy(user);
            if(user.getFiles().isEmpty()) {
            	us.setFiles(new ArrayList<QFile>());
            }
        } else {
            remoteUsers.add(user);
        }
    }

    private void loadLocalGeneralTab() {
        String username = "General";
        TabListFile jPanel2 = new TabListFile(wk, user, this, 0);
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
    	TabListFile tlf = findTableByUserId(user.getId());
    	if(tlf != null) {
        	int idx = searchTabById(user.getId());
            ImageIcon icon = new javax.swing.ImageIcon(getClass().getResource("/status-ok.png"));
            view.getjTabbedPane().setIconAt(idx, icon);
            view.getjTabbedPane().setToolTipTextAt(idx, "Última vez conectado " + new Date() + " (Doble click para cerrar)");
            tlf.enabled();
            int idxUser = remoteUsers.indexOf(user);
            remoteUsers.get(idxUser).setOnline(true);
            refreshTables();
    	}else{
    		loadUserTab(username, user, user.getId(), "/status-ok.png", 2);    		
    	}
    }

    private void loadLocalUserTab(String username, User user) {
        loadUserTab(username, user, "Tus archivos locales", "/home.png", 1);
    }

    private void loadUserTab(String username, User user, String tooltip, String iconpath, int iconidx) {
        TabListFile jPanel2 = new TabListFile(wk, this.user, this, iconidx);

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

    private void removeAllTab() {
        int idx = -1;
        do {            
            idx = -1;
            Component[] cos = view.getjTabbedPane().getComponents();
            for (int i = 0; i < cos.length; i++) {
                if (cos[i] instanceof TabListFile) {
                    idx = view.getjTabbedPane().indexOfComponent(cos[i]);
                    break;
                }
            }
            if(idx != -1){
                view.getjTabbedPane().removeTabAt(idx);
            }
        } while (idx != -1);
    }
    
    private void refreshTables() {
        Component[] cos = view.getjTabbedPane().getComponents();
        for (int i = 0; i < cos.length; i++) {
            if (cos[i] instanceof TabListFile) {
                TabListFile tlf = (TabListFile) cos[i];
                JTable table = tlf.getjTable1();
                FileTableModel dm = (FileTableModel) table.getModel();
                dm.fireTableDataChanged();
            }
        }
    }

    private TabListFile findTableByUserId(String id) {
        Component[] cos = view.getjTabbedPane().getComponents();
        for (int i = 0; i < cos.length; i++) {
            if (cos[i] instanceof TabListFile) {
                if (cos[i].getName().endsWith(id)) {
                    return (TabListFile) cos[i];
                }
            }
        }
        return null;
    }

    private void ping() {
        final Controller controller = this;
        new Thread(new Runnable() {
            public void run() {
                try {
                    do {
                        httpClient.get(Config.buildPingUserUrl(wk, user));
                        Thread.sleep(2000);
                    } while (sseClient.isAlive() && !httpClient.isDisconnect());
                } catch (Exception ex) {
                } finally {
                    Event event = new Event("Se perdio la conexion con el servidor");
                    controller.notify(event);
                }
            }
        }).start();
    }

    public void downloadFile(User user2, QFile qFile) {
        qFile.setOperation(QFile.OPERATION_DOWNLOAD);
        Event event = new Event("Quiero descargar el archivo", user, qFile);
        httpClient.post(Config.buildWkToUserUri(wk, qFile.getOwner()), event);
    }

    public void openFile(User user2, QFile qFile) {
        qFile.setOperation(QFile.OPERATION_OPEN);
        Event event = new Event("Quiero descargar el archivo", user, qFile);
        httpClient.post(Config.buildWkToUserUri(wk, qFile.getOwner()), event);
    }

    public Component getView() {
        return view;
    }

    public void removeFile(User user2, QFile qFile) {
        try {
            String filename = qFile.getName();
            String filepath = view.getjTextField4().getText();
            String fullname = filepath + File.separator + filename;
            //-----------------------------------------------------------
            FileUtils.remove(Paths.get(fullname));
            refreshLocalFilesAndNotify("Se borro un archivo");
        } catch (Exception e) {
            System.out.println("Error al intentar borrar el archivo: " + qFile.getName());
        }
    }

    public void refreshFiles(User user2) {
        refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
    }

}