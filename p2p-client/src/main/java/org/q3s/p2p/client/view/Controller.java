package org.q3s.p2p.client.view;

import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.StyleConstants;
import javax.swing.text.rtf.RTFEditorKit;

import org.java_websocket.WebSocket;
import org.q3s.p2p.client.CloudflareInstaller;
import org.q3s.p2p.client.Config;
import org.q3s.p2p.client.UpdateChecker;
import org.q3s.p2p.client.exec.Executor;
import org.q3s.p2p.client.exec.ExecutorFactoryBean;
import org.q3s.p2p.client.hub.CloudflareTunnel;
import org.q3s.p2p.client.hub.EmbeddedWebSocketServer;
import org.q3s.p2p.client.util.FileUtils;
import org.q3s.p2p.client.util.I18n;
import org.q3s.p2p.client.util.Logger;
import org.q3s.p2p.client.ws.WsClient;
import org.q3s.p2p.client.view.components.FileTableModel;
import org.q3s.p2p.client.view.components.TabListFile;
import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.QFile;
import org.q3s.p2p.model.User;
import org.q3s.p2p.model.Workspace;
import org.q3s.p2p.model.util.EventUtils;
import org.q3s.p2p.model.util.UUIDUtils;

public class Controller {

	private Workspace wk;
	private User user = User.build(UUIDUtils.generate());

	private List<User> remoteUsers = new ArrayList<User>();

	private WsClient wsClient;
	private EmbeddedWebSocketServer wsServer;
	private CloudflareTunnel cloudflareTunnel;
	private boolean isHub = false;
	private static final int FILE_CHUNK_SIZE = 48 * 1024;
	private String peerTunnelUrl;
	private JTextPane chatArea;
	private JTextField chatInput;
	private JLabel chatReplyLabel;
	private JLabel pinnedChatLabel;
	private String pinnedChatMessageId;
	private JPanel transferPanel;
	private JPanel chatContainerPanel;
	private JPanel helpContainerPanel;
	private JPanel whiteboardContainerPanel;
	private JPanel notesContainerPanel;
	private final Map<String, JProgressBar> transferBars = new LinkedHashMap<>();
	private final Map<String, String> transferTargets = new LinkedHashMap<>();
	private final Map<String, String> chatFileLinks = new LinkedHashMap<>();
	private final Map<String, String> chatTransferLinks = new LinkedHashMap<>();
	private final Map<String, ChatMessage> chatMessages = new LinkedHashMap<>();
	private final Map<String, int[]> chatMessageRanges = new LinkedHashMap<>();
	private final Map<String, User> hubCandidates = new LinkedHashMap<>();
	private final Map<String, JDialog> approvalDialogs = new LinkedHashMap<>();
	private final Map<String, String> navigationPaths = new LinkedHashMap<>();
	private final Map<String, JCheckBox> complementoChecks = new LinkedHashMap<>();
	private JButton archivosBackBtn;
	private JLabel archivosBreadcrumb;
	private JButton archivosFilterBtn;
	private TabListFile archivosTab;
	private JPanel archivosContainerPanel;
	private String selectedArchivosUserId;
	private WhiteboardCanvas whiteboardCanvas;
	private JTextField whiteboardTextInput;
	private JButton whiteboardColorButton;
	private JTextPane notesPane;
	private boolean applyingRemoteNotes;
	private Timer notesSyncTimer;
	private int notesFontSize = 14;
	private String lastSentNotesState = "";
	private DocumentListener notesDocumentListener;
	private long suppressNotesBroadcastUntil;
	private final Set<String> markedTabs = new HashSet<>();
	private final Set<String> enabledComplementos = new HashSet<>();
	private final Set<String> chatActiveUserIds = new HashSet<>();
	private final Set<String> disconnectedChatUserNames = new HashSet<>();
	private final List<FileTabInfo> fileTabs = new ArrayList<>();
	private long sessionCreatedAt;
	private boolean historySaved;
	private String cachedSessionWorkspaceId;
	private long cachedSessionCreatedAt;
	private String cachedSessionChatText;
	private ChatMessage replyingToChatMessage;
	private String currentHubUri;
	private volatile boolean failoverInProgress;
	private boolean isActiveHub;

	private View view = new View();

	private Logger log;

	private boolean configChange = false;

	private Executor exec = ExecutorFactoryBean.create();

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void start() {

		DefaultListModel model = new DefaultListModel();
		view.getjList2().setModel(model);

		log = new Logger(model);

		removeTemp();

		log.info("Usuario ID: " + user.getId());

		String hostname = Config.USER_NAME != null ? Config.USER_NAME : getLocalHostName();
		view.getjTextField3().setText(hostname);
		view.getjTextField5().setText(defaultWorkspaceName());
		view.setTitle(hostname);
		user.setName(hostname);
		view.getjTextField4().setText(Config.SHARED_DIR);

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

		view.getjTabbedPane().addChangeListener(e -> clearSelectedTabMark());
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				if (wsServer != null && wk != null && user != null && cloudflareTunnel != null) {
					wsServer.getHubService().sendToWk(wk.getId(), new Event("Usuario desconectado", user));
					Thread.sleep(200);
				}
			} catch (Exception ignored) {}
		}, "shutdown-hook"));
		view.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
		view.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				shutdown();
				saveSessionHistory("cierre");
				view.dispose();
				System.exit(0);
			}
		});

		view.pack();
		view.setVisible(true);
		view.mostarJoinPanel();
		installTransferStatusBar();
		installConfigEnhancements();

		view.getjButton2().setEnabled(true);
		view.getjLabel4().setEnabled(true);
		view.getjTextField2().setEnabled(true);
		view.getjTextField2().setFocusable(true);

		CloudflareInstaller.ensureInstalled(log);

		log.info("Servicio listo. Puede crear o unirse a un espacio de trabajo.");
		UpdateChecker.checkForUpdates(view, log);
	}

	private void installTransferStatusBar() {
		if (transferPanel != null) {
			return;
		}
		transferPanel = new JPanel();
		transferPanel.setLayout(new javax.swing.BoxLayout(transferPanel, javax.swing.BoxLayout.Y_AXIS));
		transferPanel.setVisible(false);

		java.awt.Container content = view.getContentPane();
		Component[] components = content.getComponents();
		if (components.length == 0) {
			return;
		}
		content.removeAll();
		content.setLayout(new BorderLayout());
		content.add(components[0], BorderLayout.CENTER);
		content.add(transferPanel, BorderLayout.SOUTH);
		content.revalidate();
		view.pack();
	}

	private void installConfigEnhancements() {
		int configIdx = findTabByTitle("Configuración");
		if (configIdx >= 0) {
			view.getjTabbedPane().setToolTipTextAt(configIdx,
					"Configuración local: usuario, carpeta compartida, workspace y opciones de ventana");
		}

		JCheckBox alwaysOnTop = new JCheckBox("Mantener ventana siempre visible");
		alwaysOnTop.setOpaque(false);
		alwaysOnTop.setToolTipText("Mantiene qfolder por encima de otras ventanas");
		alwaysOnTop.addActionListener(e -> view.setAlwaysOnTop(alwaysOnTop.isSelected()));

		JButton copyWsId = new JButton("Copiar");
		copyWsId.setToolTipText("Copiar ID del workspace en Base64 al portapapeles");
		copyWsId.setFocusable(false);
		copyWsId.addActionListener(e -> {
			String id = view.getjTextField6().getText();
			if (id != null && !id.isEmpty()) {
				java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
						.setContents(new java.awt.datatransfer.StringSelection(id), null);
				log.info("ID del workspace copiado al portapapeles");
			}
		});

		JLabel complementosLabel = new JLabel("Complementos");
		complementosLabel.setFont(complementosLabel.getFont().deriveFont(java.awt.Font.BOLD));

		JCheckBox cbChat = new JCheckBox("Chat");
		cbChat.setOpaque(false);
		cbChat.setSelected(enabledComplementos.contains("Chat"));
		cbChat.addActionListener(e -> toggleComplemento("Chat", cbChat.isSelected()));

		JCheckBox cbPizarra = new JCheckBox("Pizarra");
		cbPizarra.setOpaque(false);
		cbPizarra.setSelected(enabledComplementos.contains("Pizarra"));
		cbPizarra.addActionListener(e -> toggleComplemento("Pizarra", cbPizarra.isSelected()));

		JCheckBox cbNotas = new JCheckBox("Notas");
		cbNotas.setOpaque(false);
		cbNotas.setSelected(enabledComplementos.contains("Notas"));
		cbNotas.addActionListener(e -> toggleComplemento("Notas", cbNotas.isSelected()));

		JCheckBox cbArchivos = new JCheckBox("Archivos");
		cbArchivos.setOpaque(false);
		cbArchivos.setSelected(enabledComplementos.contains("Archivos"));
		cbArchivos.addActionListener(e -> toggleComplemento("Archivos", cbArchivos.isSelected()));

		JCheckBox cbLog = new JCheckBox("Log");
		cbLog.setOpaque(false);
		cbLog.setSelected(enabledComplementos.contains("Log"));
		cbLog.addActionListener(e -> toggleComplemento("Log", cbLog.isSelected()));

		JCheckBox cbAyuda = new JCheckBox("Ayuda");
		cbAyuda.setOpaque(false);
		cbAyuda.setSelected(enabledComplementos.contains("Ayuda"));
		cbAyuda.addActionListener(e -> toggleComplemento("Ayuda", cbAyuda.isSelected()));
		complementoChecks.clear();
		complementoChecks.put("Archivos", cbArchivos);
		complementoChecks.put("Chat", cbChat);
		complementoChecks.put("Pizarra", cbPizarra);
		complementoChecks.put("Notas", cbNotas);
		complementoChecks.put("Log", cbLog);
		complementoChecks.put("Ayuda", cbAyuda);

		JPanel complementosPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 2));
		complementosPanel.setOpaque(false);
		complementosPanel.add(cbArchivos);
		complementosPanel.add(cbChat);
		complementosPanel.add(cbPizarra);
		complementosPanel.add(cbNotas);
		complementosPanel.add(cbLog);
		complementosPanel.add(cbAyuda);

		JPanel panel = view.getjPanel3();
		panel.removeAll();
		javax.swing.GroupLayout layout = new javax.swing.GroupLayout(panel);
		panel.setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);

		layout.setHorizontalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
				.addComponent(view.getjLabel2())
				.addComponent(view.getjTextField3())
				.addComponent(view.getjLabel3())
				.addGroup(layout.createSequentialGroup()
						.addComponent(view.getjTextField4())
						.addComponent(view.getjButton3()))
				.addComponent(view.getjLabel6())
				.addComponent(alwaysOnTop)
				.addComponent(view.getjLabel10())
				.addGroup(layout.createSequentialGroup()
						.addComponent(view.getjTextField6())
						.addComponent(copyWsId))
				.addGap(12)
				.addComponent(complementosLabel)
				.addComponent(complementosPanel));

		layout.setVerticalGroup(layout.createSequentialGroup()
				.addComponent(view.getjLabel2())
				.addComponent(view.getjTextField3(), javax.swing.GroupLayout.PREFERRED_SIZE,
						javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
				.addGap(18)
				.addComponent(view.getjLabel3())
				.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
						.addComponent(view.getjTextField4(), javax.swing.GroupLayout.PREFERRED_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
						.addComponent(view.getjButton3()))
				.addComponent(view.getjLabel6())
				.addGap(18)
				.addComponent(alwaysOnTop)
				.addGap(18)
				.addComponent(view.getjLabel10())
				.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
						.addComponent(view.getjTextField6(), javax.swing.GroupLayout.PREFERRED_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
						.addComponent(copyWsId))
				.addGap(18)
				.addComponent(complementosLabel)
				.addComponent(complementosPanel));
		view.getjPanel3().revalidate();
		view.getjPanel3().repaint();
	}

	private void toggleComplemento(String name, boolean enabled) {
		if (enabled) {
			enabledComplementos.add(name);
		} else {
			enabledComplementos.remove(name);
		}
		applyComplementoVisibility(name, enabled);
		if (wsClient != null) {
			String eventName = enabled ? "Complemento habilitado" : "Complemento deshabilitado";
			wsClient.sendEvent(new Event(eventName, user, name));
		}
	}

	private void applyComplementoVisibility(String name, boolean visible) {
		updateComplementoCheck(name, visible);
		if (visible) {
			if ("Chat".equals(name)) { loadChatTab(); ensureChatTabVisible(); }
			if ("Pizarra".equals(name)) { loadWhiteboardTab(); ensureWhiteboardTabVisible(); }
			if ("Notas".equals(name)) { loadNotesTab(); ensureNotesTabVisible(); }
			if ("Log".equals(name) && findTabByTitle("Log") < 0) restoreLogTab();
			if ("Ayuda".equals(name)) { loadHelpTab(); }
			if ("Archivos".equals(name)) { showArchivosTab(); }
		} else {
			int idx;
			if ("Chat".equals(name) && (idx = findTabByTitle("Chat")) >= 0) {
				view.getjTabbedPane().removeTabAt(idx);
			}
			if ("Pizarra".equals(name) && (idx = findTabByTitle("Pizarra")) >= 0) {
				view.getjTabbedPane().removeTabAt(idx);
			}
			if ("Notas".equals(name) && (idx = findTabByTitle("Notas")) >= 0) {
				view.getjTabbedPane().removeTabAt(idx);
			}
			if ("Log".equals(name) && (idx = findTabByTitle("Log")) >= 0) {
				view.getjTabbedPane().removeTabAt(idx);
			}
			if ("Archivos".equals(name) && (idx = findTabByTitle("Archivos")) >= 0) {
				view.getjTabbedPane().removeTabAt(idx);
			}
			if ("Ayuda".equals(name) && (idx = findTabByTitle("Ayuda")) >= 0) {
				view.getjTabbedPane().removeTabAt(idx);
			}
		}
	}

	private void updateComplementoCheck(String name, boolean enabled) {
		JCheckBox check = complementoChecks.get(name);
		if (check != null && check.isSelected() != enabled) {
			check.setSelected(enabled);
		}
	}

	private void showFileTabs() {
		int pos = 0;
		for (FileTabInfo info : fileTabs) {
			if (findTabByTitle(info.title) >= 0) continue;
			view.getjTabbedPane().insertTab(info.title, info.icon, info.component, info.tooltip, pos);
			pos++;
		}
	}

	private void hideFileTabs() {
		for (FileTabInfo info : fileTabs) {
			int idx = findTabByTitle(info.title);
			if (idx >= 0) {
				view.getjTabbedPane().removeTabAt(idx);
			}
		}
	}

	private void registerFileTab(String title, String tooltip, Component component, ImageIcon icon) {
		for (FileTabInfo info : fileTabs) {
			if (info.title.equals(title)) return;
		}
		fileTabs.add(new FileTabInfo(title, tooltip, component, icon));
	}

	private void ensureChatTabVisible() {
		if (chatContainerPanel != null && findTabByTitle("Chat") < 0) {
			insertSystemTab("Chat", chatIcon(), chatContainerPanel, "Chat grupal del workspace");
			chatContainerPanel.revalidate();
		}
	}

	private void ensureWhiteboardTabVisible() {
		if (whiteboardContainerPanel != null && findTabByTitle("Pizarra") < 0) {
			insertSystemTab("Pizarra", boardIcon(), whiteboardContainerPanel, "Pizarra colaborativa simple");
		}
	}

	private void ensureNotesTabVisible() {
		if (notesContainerPanel != null && findTabByTitle("Notas") < 0) {
			insertSystemTab("Notas", noteIcon(), notesContainerPanel, "Notas compartidas con formato básico");
		}
	}

	private void loadHelpTab() {
		if (helpContainerPanel != null) {
			if (findTabByTitle("Ayuda") < 0) {
				insertSystemTab("Ayuda", helpIcon(), helpContainerPanel, I18n.get("help.title"));
			}
			return;
		}
		String sharedDir = Config.SHARED_DIR;
		String historyDir = Config.HISTORY_DIR;
		JTextPane help = new JTextPane();
		help.setContentType("text/html");
		help.setEditable(false);
		help.setText("<html><body style='padding:8px;font-family:sans-serif'>"
				+ "<h2>qfolder <small>v" + UpdateChecker.getVersion() + "</small></h2>"
				+ "<p><b>" + I18n.get("author") + "</b></p>"
				+ "<p><a href='https://github.com/damianlezcano/qfolder'>github.com/damianlezcano/qfolder</a></p>"
				+ "<p>" + I18n.get("app.description") + "</p>"
				+ "<h3>Highlights</h3>"
				+ "<ul>"
				+ "<li>Zero registration. No accounts, no servers.</li>"
				+ "<li>Distributed: if the hub disconnects, another member takes over automatically.</li>"
				+ "<li>Ephemeral: sessions save locally in <code>" + historyDir + "</code>.</li>"
				+ "<li>Shared files sync automatically between members.</li>"
				+ "<li>Clickable file links in chat with one-click download.</li>"
				+ "<li>Reply and pin messages in chat.</li>"
				+ "<li>Collaborative whiteboard with draw, text, images and shapes.</li>"
				+ "<li>Shared rich-text notes with images and formatting.</li>"
				+ "<li>Workspace complements toggle individually.</li>"
				+ "<li>Auto-detects English/Spanish from system locale.</li>"
				+ "</ul>"
				+ "<h3>File locations</h3>"
				+ "<table>"
				+ "<tr><td>Shared:</td><td><code>" + sharedDir + "</code></td></tr>"
				+ "<tr><td>History:</td><td><code>" + historyDir + "</code></td></tr>"
				+ "</table>"
				+ "<h3>Launch options</h3>"
				+ "<table>"
				+ "<tr><td><code>-Dqfolder.user.name=X</code></td><td>User name</td></tr>"
				+ "<tr><td><code>-Dqfolder.shared.dir=X</code></td><td>Shared folder (<code>~</code> ok)</td></tr>"
				+ "<tr><td><code>-Dqfolder.history.dir=X</code></td><td>History folder (<code>~</code> ok)</td></tr>"
				+ "<tr><td><code>-Dqfolder.ws.port=N</code></td><td>WebSocket port (18765)</td></tr>"
				+ "<tr><td><code>-Dqfolder.tunnel.mock=true</code></td><td>Local dev mode</td></tr>"
				+ "</table>"
				+ "<p>qfolder 2020-2026</p>"
				+ "</body></html>");
		helpContainerPanel = new JPanel(new BorderLayout(8, 8));
		helpContainerPanel.add(new JScrollPane(help), BorderLayout.CENTER);
		insertSystemTab("Ayuda", helpIcon(), helpContainerPanel, I18n.get("help.title"));
	}

	private void restoreLogTab() {
		ImageIcon icon = new javax.swing.ImageIcon(getClass().getResource("/tab-log.png"));
		int idx = findTabByTitle("Configuración");
		if (idx < 0) {
			view.getjTabbedPane().addTab("Log", icon, view.getjScrollPane2(), "Registros de eventos");
		} else {
			view.getjTabbedPane().insertTab("Log", icon, view.getjScrollPane2(), "Registros de eventos", idx);
		}
	}

	private void removeLogTab() {
		int idx = findTabByTitle("Log");
		if (idx >= 0) {
			view.getjTabbedPane().removeTabAt(idx);
		}
	}

	private void hideInitialComplementos() {
		for (String name : new String[]{"Archivos", "Chat", "Pizarra", "Notas", "Ayuda"}) {
			int idx = findTabByTitle(name);
			if (idx >= 0) {
				view.getjTabbedPane().removeTabAt(idx);
			}
		}
	}

	private void restoreEnabledComplementoTabs() {
		for (String name : new ArrayList<>(enabledComplementos)) {
			applyComplementoVisibility(name, true);
		}
	}

	private void jCheckBox3ActionPerformed(java.awt.event.ActionEvent evt) {
		JCheckBox cb = (JCheckBox) evt.getSource();
		if (cb.isSelected()) {
			view.getjTextField9().setEnabled(true);
			view.getjTextField8().setEnabled(true);
			view.getjCheckBox2().setEnabled(true);
			if (view.getjCheckBox2().isSelected()) {
				view.getjTextField7().setEnabled(true);
				view.getjPasswordField4().setEnabled(true);
			}
		} else {
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
		} else {
			view.getjTextField7().setEnabled(false);
			view.getjPasswordField4().setEnabled(false);
		}
	}

	private void jButton7ActionPerformed(java.awt.event.ActionEvent evt) {
		mostrarErrorEnPantallaLogin("");
		view.getjPanelCreateWorkspace().setVisible(false);
		view.getjPanelJoin().setVisible(true);
		view.getjTabbedPane().setVisible(false);
		view.getjPanelProxy().setVisible(false);
		view.setTitle(getLocalHostName());
	}

	private void jButton8ActionPerformed(java.awt.event.ActionEvent evt) {
		mostrarErrorEnPantallaLogin("");
		view.getjPanelCreateWorkspace().setVisible(false);
		view.getjPanelJoin().setVisible(true);
		view.getjTabbedPane().setVisible(false);
		view.getjPanelProxy().setVisible(false);
		view.setTitle(getLocalHostName());

		Properties props = System.getProperties();
		if (view.getjCheckBox3().isSelected()) {
			props.put("http.proxyHost", view.getjTextField9().getText());
			props.put("http.proxyPort", view.getjTextField8().getText());
		} else {
			props.remove("http.proxyHost");
			props.remove("http.proxyPort");
		}
	}

	private void Jabel6ActionPerformed(java.awt.event.MouseEvent evt) {
		view.getjTextField4().setText(Config.SHARED_DIR);
		refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
	}

	private void jTextField3FocusLost(java.awt.event.FocusEvent evt) {
		if (configChange) {
			Event e = new Event("Cambio de nombre", user);
			sendEvent(e);
			configChange = false;
		}
	}

	private void removeTemp() {
		try {
			FileUtils.remove(Paths.get(Config.TEMP_PATH));
		} catch (Exception e) {
			log.debug("Error al borrar el directorio temporal -> " + e.getMessage());
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
		user.setName(view.getjTextField3().getText());
		updateWindowTitle();
		configChange = true;
	}

	private void updateWindowTitle() {
		String username = user.getName() != null && !user.getName().trim().isEmpty()
				? user.getName().trim() : getLocalHostName();
		if (wk != null && view.getjTabbedPane().isVisible()) {
			String hubMarker = isThisInstanceHub() ? " [Hub]" : "";
			view.setTitle("'" + username + "' conectado al grupo '" + decodeValue(wk.getName()) + "'" + hubMarker);
		} else {
			view.setTitle(username);
		}
	}

	private boolean isThisInstanceHub() {
		return isActiveHub;
	}

	private boolean attemptHubFailover() {
		if (wk == null || failoverInProgress) return false;
		List<HubCandidate> candidates = hubFailoverCandidates();
		if (candidates.isEmpty()) return false;

		failoverInProgress = true;
		log.info(I18n.get("reconnecting"));
		new Thread(() -> {
			boolean connected = false;
			for (HubCandidate candidate : candidates) {
				if (candidate.uri.equals(currentHubUri)) continue;
				log.info(I18n.get("reconnecting.trying", candidate.name));
				if (connectWebSocket(candidate.uri, true) && waitForFailoverWelcome(candidate.uri)) {
					connected = true;
					log.info(I18n.get("reconnecting.ok", candidate.name));
					break;
				}
				closeFailedFailoverConnection(candidate.uri);
			}
			if (!connected) {
				javax.swing.SwingUtilities.invokeLater(() -> {
					failoverInProgress = false;
					showJoinAfterWorkspaceLost(I18n.get("reconnecting.fail"));
				});
			}
		}, "hub-failover").start();
		return true;
	}

	private boolean waitForFailoverWelcome(String candidateUri) {
		long deadline = System.currentTimeMillis() + 8000;
		while (failoverInProgress && candidateUri.equals(currentHubUri) && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(150);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		return !failoverInProgress && candidateUri.equals(currentHubUri);
	}

	private void closeFailedFailoverConnection(String candidateUri) {
		try {
			if (wsClient != null && candidateUri.equals(currentHubUri)) {
				wsClient.close();
			}
		} catch (Exception ignored) {}
	}

	private void showJoinAfterWorkspaceLost(String message) {
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

		mostrarErrorEnPantallaLogin(message);

		wsClient = null;
		remoteUsers = new ArrayList<User>();
		removeAllTab();
	}

	private List<HubCandidate> hubFailoverCandidates() {
		LinkedHashMap<String, HubCandidate> candidates = new LinkedHashMap<>();
		if (peerTunnelUrl != null && !peerTunnelUrl.isBlank()) {
			candidates.put(user.getId(), new HubCandidate(user.getId(), user.getName(), "ws://localhost:" + Config.WS_SERVER_PORT));
		}
		for (User candidate : hubCandidates.values()) {
			if (candidate == null || candidate.equals(user) || candidate.getPeerUrl() == null || candidate.getPeerUrl().isBlank()) continue;
			candidates.put(candidate.getId(), new HubCandidate(candidate.getId(), candidate.getName(), "ws://" + candidate.getPeerUrl()));
		}
		List<HubCandidate> sorted = new ArrayList<>(candidates.values());
		sorted.sort((a, b) -> a.userId.compareTo(b.userId));
		return sorted;
	}

	private String defaultWorkspaceName() {
		return "Espacio de trabajo del dia " + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
	}

	private String getLocalHostName() {
		return System.getenv("USER");
	}

	private void ensurePeerEndpoint(Runnable onReady, java.util.function.Consumer<String> onError) {
		if (peerTunnelUrl != null && wsServer != null) {
			onReady.run();
			return;
		}

		log.info("Iniciando endpoint local del peer...");
		final int port = Config.WS_SERVER_PORT;
		wsServer = new EmbeddedWebSocketServer(port, () -> {
			log.info("Servidor WebSocket iniciado en puerto " + port);
			cloudflareTunnel = new CloudflareTunnel(log);
			cloudflareTunnel.start(port, tunnelUrl -> {
				peerTunnelUrl = tunnelUrl.replace("https://", "").replace("http://", "").trim();
				user.setPeerUrl(peerTunnelUrl);
				log.info("Endpoint del peer listo: " + peerTunnelUrl);
				onReady.run();
			}, error -> {
				log.err("Error al iniciar cloudflared: " + error);
				if (onError != null) {
					onError.accept(error);
				}
			});
		}, this::handleDirectPeerEvent);
		wsServer.start();
	}

	private void handleDirectPeerEvent(WebSocket conn, Event event) {
		if ("Quiero descargar el archivo".equals(event.getName())) {
			log.info("Pedido directo de archivo desde '" + event.getUser().getName() + "': "
					+ event.getFile().getName());
			sendFileDirect(event, conn);
		}
	}

	private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {
		final String wsName = view.getjTextField5().getText();
		final String wsPassword;
		if (view.getjCheckBox1().isSelected()) {
			if (Arrays.equals(view.getjPasswordField1().getPassword(), view.getjPasswordField2().getPassword())) {
				wsPassword = String.valueOf(view.getjPasswordField1().getPassword());
				view.getjLabel17().setText("");
			} else {
				view.getjLabel17().setText("Las claves no coinciden");
				return;
			}
		} else {
			wsPassword = null;
		}

		view.getjButton4().setEnabled(false);
		ensurePeerEndpoint(() -> {
			final String cleanUrl = peerTunnelUrl;
			long date = new Date().getTime();
			sessionCreatedAt = date;
			historySaved = false;
			wsServer.getHubService().create(cleanUrl, wsName, wsPassword, date);

			isHub = true;
			isActiveHub = true;
			user.setPeerUrl(cleanUrl);

			javax.swing.SwingUtilities.invokeLater(() -> {
				view.getjTextField2().setText(encodeWkId(cleanUrl));
				log.info("Workspace '" + wsName + "' creado. URL: " + cleanUrl);
				mostrarErrorEnPantallaLogin("");
				log.info("Conectando al espacio de trabajo...");

				view.getjPanelCreateWorkspace().setVisible(false);
				view.getjTabbedPane().setVisible(false);
				view.getjPanelProxy().setVisible(false);
				view.getjButton4().setEnabled(true);
				view.setTitle(view.getjTextField3().getText());

				wk = Workspace.build(cleanUrl);
				connectLocalWebSocket(Config.WS_SERVER_PORT, cleanUrl);
			});
		}, error -> {
			javax.swing.SwingUtilities.invokeLater(() -> {
				view.getjButton4().setEnabled(true);
				mostrarErrorEnPantallaLogin("Error al iniciar el tunel. Verifique que cloudflared este instalado.");
			});
		});
	}

	private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {
		view.getjPanelCreateWorkspace().setVisible(false);
		view.getjPanelJoin().setVisible(true);
		view.getjTabbedPane().setVisible(false);
		view.getjPanelProxy().setVisible(false);
		view.setTitle(view.getjTextField3().getText());
	}

	private void onJLabel4Click(java.awt.event.MouseEvent evt) {
		if (view.getjLabel4().isEnabled()) {
			mostrarErrorEnPantallaLogin("");
			view.getjPanelCreateWorkspace().setVisible(true);
			view.getjPanelJoin().setVisible(false);
			view.getjTabbedPane().setVisible(false);
			view.getjPanelProxy().setVisible(false);
			view.setTitle("Crear espacio de trabajo");
		}
	}

	private void mostrarErrorEnPantallaLogin(String message) {
		view.getjLabel16().setText(message);
	}

	private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {
		String uuid = decodeWkId(view.getjTextField2().getText());

		if (uuid.isEmpty()) {
			mostrarErrorEnPantallaLogin(I18n.get("workspace.emptyId"));
		} else {
		user.setName(view.getjTextField3().getText());
		isActiveHub = false;
		wk = Workspace.build(uuid);
			view.getjTextField2().setEnabled(false);
			view.getjButton2().setEnabled(false);
			view.getjLabel4().setEnabled(false);
			view.getjButton6().setEnabled(false);
			mostrarErrorEnPantallaLogin("");
			connectWebSocket();
		}
	}

	private void connectWebSocket() {
		connectWebSocket("ws://" + wk.getId());
	}

	private void connectLocalWebSocket(int port, String wkId) {
		connectWebSocket("ws://localhost:" + port);
	}

	private void connectWebSocket(String serverUri) {
		connectWebSocket(serverUri, false);
	}

	private boolean connectWebSocket(String serverUri, boolean blocking) {
		try {
			String uri = serverUri + "/ws?wkId=" + java.net.URLEncoder.encode(wk.getId(), "UTF-8")
					+ "&userId=" + java.net.URLEncoder.encode(user.getId(), "UTF-8")
					+ (failoverInProgress ? "&failover=true" : "");
			currentHubUri = serverUri;
			wsClient = new WsClient(new java.net.URI(uri), log, this::notify,
					error -> notify(new Event("No es posible establecer una conexion")),
					() -> notify(new Event("Se perdio la conexion con el servidor")));
			wsClient.setConnectionLostTimeout(0);
			if (blocking) {
				return wsClient.connectBlocking(6, TimeUnit.SECONDS);
			}
			wsClient.connect();
			return true;
		} catch (Exception e) {
			notify(new Event("No es posible establecer una conexion"));
			return false;
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

	private void showApprovalDialog(User to) {
		if (to == null || to.getId() == null) return;
		closeApprovalDialog(to.getId());
		JDialog dialog = new JDialog(view, I18n.get("approve.title"), false);
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
		panel.add(new JLabel(I18n.get("approve.message", to.getName())), BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		JButton approve = new JButton(I18n.get("approve.accept"));
		JButton refuse = new JButton(I18n.get("approve.cancel"));
		approve.addActionListener(e -> {
			if (wsClient != null) wsClient.sendEvent(new Event("__hub:approved:" + to.getId()));
			log.info(I18n.get("approve.approved", to.getName()));
			closeApprovalDialog(to.getId());
		});
		refuse.addActionListener(e -> {
			if (wsClient != null) wsClient.sendEvent(new Event("__hub:refuse:" + to.getId()));
			log.info(I18n.get("approve.refused", to.getName()));
			closeApprovalDialog(to.getId());
		});
		buttons.add(refuse);
		buttons.add(approve);
		panel.add(buttons, BorderLayout.SOUTH);
		dialog.setContentPane(panel);
		dialog.pack();
		dialog.setLocationRelativeTo(view);
		approvalDialogs.put(to.getId(), dialog);
		dialog.setVisible(true);
	}

	private void closeApprovalDialog(String userId) {
		if (userId == null) return;
		JDialog dialog = approvalDialogs.remove(userId);
		if (dialog != null) {
			dialog.dispose();
		}
	}

	public void notify(Event event) {
		try {
			if (event != null && event.getName() != null) {
				log.debug(event.getName());
				if ("Wk no existe, desconectar".equals(event.getName())) {
					if (failoverInProgress) {
						log.info("Nodo alternativo sin workspace activo, pruebo otro...");
						if (wsClient != null) wsClient.close();
						return;
					}
					wsClient.close();
					view.getjButton2().setEnabled(true);
					view.getjLabel4().setEnabled(true);
					view.getjTextField2().setEnabled(true);
					view.getjTextField2().setFocusable(true);
					view.getjButton6().setEnabled(true);
					mostrarErrorEnPantallaLogin(I18n.get("ws.notExists"));
				} else if ("Wk existente, sin credenciales".equals(event.getName())) {
					wsClient.sendEvent(new Event("__hub:withoutAuth", user));
				} else if ("Wk existente, con credenciales".equals(event.getName())) {
					JPasswordField pf = new JPasswordField();
					JOptionPane pane = new JOptionPane(pf, JOptionPane.INFORMATION_MESSAGE, JOptionPane.OK_OPTION);
					JDialog dialog = pane.createDialog(view, "Clave de Acceso");
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
						wsClient.sendEvent(new Event("__hub:withAuth", user));
					}

				} else if ("Credenciales incorrectas".equals(event.getName())) {
					log.info("Credenciales de acceso incorrectas");
					mostrarErrorEnPantallaLogin(I18n.get("ws.wrongCredentials"));
				} else if ("Aprobar al usuario".equals(event.getName())) {
					markLogIfInactive();
					log.info("El usuario '" + event.getUser().getName() + "' solicita ingresar al workspace");
					showApprovalDialog(event.getUser());
				} else if ("Usuario rechazado!".equals(event.getName())) {
					log.info("Tu ingreso fue rechazado por los usuarios del workspace");
					wsClient.close();
					mostrarErrorEnPantallaLogin(I18n.get("approve.rejected"));
				} else if ("Bienvenido usuario al grupo!".equals(event.getName())) {
					boolean reconnectingByFailover = failoverInProgress;
					failoverInProgress = false;
					log.info("Bienvenido al grupo");
					wk = event.getWk();
					if (reconnectingByFailover) {
						isActiveHub = currentHubUri != null && currentHubUri.contains("localhost:" + Config.WS_SERVER_PORT);
					}
					log.info("Miembro activo - wkId=" + wk.getId() + " hub=" + (currentHubUri != null ? currentHubUri : "local"));
					ensureArchivosTab();
					loadChatTab();
					loadWhiteboardTab();
					loadNotesTab();
					if (!reconnectingByFailover) {
						removeLogTab();
						hideInitialComplementos();
					} else {
						restoreEnabledComplementoTabs();
					}
					view.getjPanelCreateWorkspace().setVisible(false);
					view.getjPanelJoin().setVisible(false);
					view.getjTabbedPane().setVisible(true);
					view.getjPanelProxy().setVisible(false);
					view.revalidate();
					view.repaint();

					sessionCreatedAt = wk != null && wk.getDate() > 0 ? wk.getDate() : System.currentTimeMillis();
					historySaved = false;
					wk.setName(wk.getName());
					view.getjTextField6().setText(encodeWkId(wk.getId()));
					if (reconnectingByFailover && currentHubUri != null && !currentHubUri.isBlank()) {
						String hubUrl = currentHubUri.replace("ws://", "").replace("wss://", "");
						view.getjTextField6().setText(encodeWkId(hubUrl));
					}
					if (!reconnectingByFailover) {
						restoreCachedChatIfSameSession();
					}

					updateWindowTitle();

					ensurePeerEndpoint(() -> {
						if (!isHub && wsServer != null && !reconnectingByFailover) {
							wsServer.getHubService().create(wk.getId(), wk.getName(), wk.getPassword(), wk.getDate());
						}
						refreshLocalFilesAndNotify("Gracias por la bienvenida, notifico mis archivos");
					}, error -> log.err("No se pudo iniciar endpoint peer: " + error));

				} else if ("Gracias por la bienvenida, notifico mis archivos".equals(event.getName())) {
					if (event.getUser().equals(user)) {
						log.info("Notificaste tus archivos al grupo");
					} else {
						markLogIfInactive();
						log.info("El usuario '" + event.getUser().getName() + "' notifico sus archivos.");
						List<QFile> files = FileUtils.files(view.getjTextField4().getText());
						String targetId = event.getUser().getId();
						wsClient.sendEvent(new Event("__to:" + targetId + ":Estos son mis archivos", user.clone(files)));
						sendInitialWorkspaceState(targetId);
					}
					addUserToRemoteList(event.getUser());
				} else if ("Estos son mis archivos".equals(event.getName())) {
					markLogIfInactive();
					log.info("El usuario '" + event.getUser().getName() + "' notifico sus archivos.");
					addUserToRemoteList(event.getUser());
					refreshArchivosUserFilter();
					refreshTables();
				} else if ("Notifico Cambio en los archivos".equals(event.getName())) {
					if (event.getUser() == null || !event.getUser().equals(user)) markLogIfInactive();
					notifyChangeFiles(event);
				} else if ("Mensaje de chat".equals(event.getName())) {
					appendChatMessage(event.getUser(), event.getResponse());
					if (event.getUser() == null || !event.getUser().equals(user)) {
						markTabIfInactive("Chat");
						log.info("Chat: " + event.getUser().getName() + " escribio un mensaje");
					} else {
						log.debug("Mensaje propio recibido (eco)");
					}
				} else if ("Mensaje de chat fijado".equals(event.getName())) {
					applyPinnedChatMessage(ChatMessage.fromPinnedPayload(event.getResponse(), event.getUser()));
				} else if ("Archivo de chat enviado".equals(event.getName())) {
					handleChatFileEvent(event);
				} else if ("Historial de chat".equals(event.getName())) {
					applyRemoteChatHistory(event.getResponse());
				} else if ("Pizarra actualizada".equals(event.getName())) {
					if (whiteboardCanvas != null) {
						whiteboardCanvas.applyState(event.getResponse());
						if (event.getUser() == null || !event.getUser().equals(user)) {
							markTabIfInactive("Pizarra");
							log.info("Pizarra actualizada por " + event.getUser().getName());
						}
					}
				} else if ("Notas actualizadas".equals(event.getName())) {
					if (event.getUser() == null || !event.getUser().equals(user)) {
						applyRemoteNotes(event.getResponse());
						markTabIfInactive("Notas");
						log.info("Notas actualizadas por " + event.getUser().getName());
					}
				} else if ("Se borro un archivo".equals(event.getName())) {
					if (event.getUser() == null || !event.getUser().equals(user)) markLogIfInactive();
					if (event.getUser().equals(user)) {
						log.info("Borraste el archivo '" + event.getFile().getName());
					} else {
						log.info("El usuario '" + event.getUser().getName() + "' borro el archivo '"
								+ event.getFile().getName());
					}
					notifyChangeFiles(event);
				} else if ("Solicitar archivos de directorio".equals(event.getName())) {
					String relativePath = event.getResponse();
					String baseDir = view.getjTextField4().getText();
					List<QFile> files = FileUtils.files(baseDir, relativePath);
					String targetId = event.getUser().getId();
					wsClient.sendEvent(new Event("__to:" + targetId + ":Respuesta archivos de directorio",
							this.user.clone(files), relativePath));
				} else if ("Respuesta archivos de directorio".equals(event.getName())) {
					String relativePath = event.getResponse();
					User remoteUser = event.getUser();
					List<QFile> files = remoteUser.getFiles();
					updateRemoteUserFiles(remoteUser.getId(), files);
					navigationPaths.put(remoteUser.getId(), relativePath);
					refreshArchivosTable();
				} else if ("Cambio de nombre".equals(event.getName())) {
					int idx = searchTabById(event.getUser().getId());
					int idxUser = remoteUsers.indexOf(event.getUser());
					User oldUser = remoteUsers.get(idxUser);
					String oldUserName = oldUser.getName();
					oldUser.setName(event.getUser().getName());
					refreshTables();
					if (!event.getUser().equals(user)) {
						markLogIfInactive();
						log.info("El usuario '" + oldUserName + "' cambio de nombre a '" + event.getUser().getName()
								+ "'");
						view.getjTabbedPane().setTitleAt(idx, event.getUser().getName());
					} else {
						log.info("Cambiaste de nombre a '" + event.getUser().getName() + "'");
					}
				} else if ("Usuario desconectado".equals(event.getName())) {
					markLogIfInactive();
					User disconnectedUser = findKnownUser(event.getUser());
					String disconnectedName = disconnectedUser != null && disconnectedUser.getName() != null
							? disconnectedUser.getName() : "Usuario";
					String disconnectedId = event.getUser() != null ? event.getUser().getId() : null;
					log.info("Usuario '" + disconnectedName + "' desconectado");
					if (disconnectedId != null && chatActiveUserIds.contains(disconnectedId)) {
						disconnectedChatUserNames.add(disconnectedName);
						appendChatSystemMessage(I18n.get("chat.userDisconnected", disconnectedName));
						markTabIfInactive("Chat");
					}
					if (disconnectedUser != null) {
						remoteUsers.remove(disconnectedUser);
					} else if (event.getUser() != null) {
						remoteUsers.remove(event.getUser());
					}
					if (disconnectedId != null && disconnectedId.equals(selectedArchivosUserId)) {
						selectedArchivosUserId = null;
					}
					refreshArchivosUserFilter();
					refreshTables();
				} else if ("Quiero descargar el archivo".equals(event.getName())) {
					markLogIfInactive();
					log.info("El usuario '" + event.getUser().getName() + "' solicito el archivo '"
							+ event.getFile().getName() + "'");
					sendFileDirect(event);

				} else if ("Parte de archivo".equals(event.getName())) {
					receiveFilePart(event.getFile());
				} else if ("Error al transferir archivo".equals(event.getName())) {
					markLogIfInactive();
					log.err(event.getResponse());

				} else if ("Complemento habilitado".equals(event.getName())) {
					String name = event.getResponse();
					enabledComplementos.add(name);
					applyComplementoVisibility(name, true);
					log.info("Complemento '" + name + "' habilitado" +
							(event.getUser() != null && !event.getUser().equals(user) ? " por " + event.getUser().getName() : ""));

				} else if ("Complemento deshabilitado".equals(event.getName())) {
					String name = event.getResponse();
					enabledComplementos.remove(name);
					applyComplementoVisibility(name, false);
					log.info("Complemento '" + name + "' deshabilitado" +
							(event.getUser() != null && !event.getUser().equals(user) ? " por " + event.getUser().getName() : ""));

				} else if ("Solicitud de ingreso resuelta".equals(event.getName())) {
					closeApprovalDialog(event.getUser() != null ? event.getUser().getId() : null);

				} else if ("Error al intentar conectarse con el servidor".equals(event.getName())) {
				} else if ("No es posible establecer una conexion".equals(event.getName())) {
					if (failoverInProgress) {
						log.info("Intentando otro nodo del workspace...");
						return;
					}
					log.info("No se pudo conectar al workspace");
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
				mostrarErrorEnPantallaLogin(I18n.get("workspace.cannotConnect"));
				} else if ("Se perdio la conexion con el servidor".equals(event.getName())) {
					if (failoverInProgress) {
						log.info("Nodo alternativo no disponible, pruebo otro...");
						return;
					}
					log.info("Se perdio la conexion con el workspace");
					cacheCurrentSessionForReconnect();
					saveSessionHistory("desconexion");
					if (attemptHubFailover()) {
						return;
					}
					showJoinAfterWorkspaceLost(event.getName());
				}
			}
		} catch (Exception e) {
			log.debug("Error notify -> " + e.getMessage());
		}
	}

	public static String decodeValue(String value) {
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException ex) {
			throw new RuntimeException(ex.getCause());
		}
	}

	private String encodeWkId(String wkId) {
		if (wkId == null || wkId.trim().isEmpty()) return "";
		return Base64.getEncoder().encodeToString(wkId.trim().getBytes(StandardCharsets.UTF_8));
	}

	private String decodeWkId(String encodedWkId) {
		if (encodedWkId == null) return "";
		String value = encodedWkId.trim();
		if (value.isEmpty()) return "";
		try {
			return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8).trim();
		} catch (IllegalArgumentException e) {
			return value;
		}
	}

	private void cacheCurrentSessionForReconnect() {
		if (wk == null || chatArea == null) return;
		cachedSessionWorkspaceId = wk.getId();
		cachedSessionCreatedAt = sessionCreatedAt;
		cachedSessionChatText = chatArea.getText();
	}

	private void restoreCachedChatIfSameSession() {
		if (wk == null || chatArea == null) return;

		// try in-memory cache first
		if (cachedSessionChatText != null
				&& wk.getId().equals(cachedSessionWorkspaceId)
				&& cachedSessionCreatedAt == sessionCreatedAt) {
			if (chatArea.getDocument().getLength() > 0) return;
			SimpleAttributeSet attrs = new SimpleAttributeSet();
			StyleConstants.setForeground(attrs, Color.BLACK);
			appendChatText(cachedSessionChatText, attrs);
			log.info("Chat restored from memory cache");
			return;
		}

		// try file-based history folder
		File sessionDir = buildSessionHistoryDir();
		if (sessionDir == null || !sessionDir.exists()) return;
		File chatFile = new File(sessionDir, "chat.txt");
		if (!chatFile.exists()) return;
		if (chatArea.getDocument().getLength() > 0) return;
		try {
			String history = Files.readString(chatFile.toPath(), StandardCharsets.UTF_8);
			if (history != null && !history.isEmpty()) {
				SimpleAttributeSet attrs = new SimpleAttributeSet();
				StyleConstants.setForeground(attrs, Color.BLACK);
				appendChatText(history, attrs);
				appendChatSystemMessage(I18n.get("chat.historyLoaded"));
				log.info("Chat restored from local history: " + sessionDir.getAbsolutePath());
			}
		} catch (Exception e) {
			log.err("Failed to load local chat history: " + e.getMessage());
		}
	}

	private File buildSessionHistoryDir() {
		if (wk == null) return null;
		long createdAt = sessionCreatedAt > 0 ? sessionCreatedAt : System.currentTimeMillis();
		Date createdDate = new Date(createdAt);
		String workspaceName = wk.getName() != null ? decodeValue(wk.getName()) : defaultWorkspaceName();
		String folderName = new SimpleDateFormat("HHmm").format(createdDate) + "-" + safeFileName(workspaceName);
		File dir = new File(new File(new File(Config.HISTORY_DIR,
				new SimpleDateFormat("yyyy").format(createdDate)),
				new SimpleDateFormat("MM").format(createdDate)),
				new SimpleDateFormat("dd").format(createdDate));
		return new File(dir, folderName);
	}

	private void notifyChangeFiles(Event event) {
		addUserToRemoteList(event.getUser());
		refreshArchivosUserFilter();
		refreshTables();
	}

	private void refreshArchivosUserFilter() {
		if (archivosFilterBtn == null) return;
		archivosFilterBtn.setText(getArchivosFilterLabel() + " ▼");
		refreshArchivosTable();
	}

	private void refreshLocalFilesAndNotify(String msg) {
		String baseDir = view.getjTextField4().getText();
		String navPath = navigationPaths.getOrDefault(user.getId(), "");
		List<QFile> files = FileUtils.files(baseDir, navPath);
		this.user.setFiles(files);
		refreshLocalFilesAndNotify(new Event(msg, user.clone(files)));
	}

	private void refreshLocalFilesAndNotify(Event e) {
		sendEvent(e);
	}

	private void sendEvent(Event e) {
		if (wsClient != null) {
			wsClient.sendEvent(e);
		}
	}

	private void addUserToRemoteList(User user) {
		if (user.equals(this.user)) return;
		if (user.getPeerUrl() != null && !user.getPeerUrl().isBlank()) {
			hubCandidates.put(user.getId(), user);
		}
		int idx = remoteUsers.indexOf(user);
		if (idx != -1) {
			User us = remoteUsers.get(idx);
			us.copy(user);
			if (user.getFiles().isEmpty()) {
				us.setFiles(new ArrayList<QFile>());
			}
		} else {
			remoteUsers.add(user);
		}
		if (disconnectedChatUserNames.remove(user.getName())) {
			String name = user.getName() != null ? user.getName() : "Usuario";
			appendChatSystemMessage(I18n.get("chat.userReconnected", name), new Color(0, 128, 0));
			markTabIfInactive("Chat");
		}
	}

	private User findKnownUser(User candidate) {
		if (candidate == null) return null;
		int idx = remoteUsers.indexOf(candidate);
		if (idx >= 0) return remoteUsers.get(idx);
		return candidate;
	}

	private void ensureArchivosTab() {
		if (archivosTab != null) return;

		selectedArchivosUserId = null;

		archivosFilterBtn = new JButton(getArchivosFilterLabel() + " ▼");
		archivosFilterBtn.setFocusable(false);
		archivosFilterBtn.addActionListener(e -> showArchivosFilterMenu());

		archivosBackBtn = new JButton("← Atrás");
		archivosBackBtn.setVisible(false);
		archivosBackBtn.addActionListener(e -> navigateBack(getSelectedFileUserId()));

		archivosBreadcrumb = new JLabel("");

		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		topPanel.add(archivosFilterBtn);
		topPanel.add(archivosBackBtn);
		topPanel.add(archivosBreadcrumb);
		topPanel.setPreferredSize(new java.awt.Dimension(100, 32));

		archivosTab = new TabListFile(wk, this.user, this, 0);
		archivosTab.setName("Archivos");

		JPanel panel = new JPanel(new BorderLayout(4, 4));
		panel.add(topPanel, BorderLayout.NORTH);
		panel.add(archivosTab, BorderLayout.CENTER);
		archivosContainerPanel = panel;

		refreshArchivosTable();
	}

	private void showArchivosFilterMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem allItem = new JMenuItem("Todos");
		allItem.addActionListener(e -> selectArchivosUser(null));
		menu.add(allItem);
		menu.addSeparator();

		JMenuItem yoItem = new JMenuItem("Yo");
		yoItem.addActionListener(e -> selectArchivosUser(this.user.getId()));
		menu.add(yoItem);

		for (User u : remoteUsers) {
			if (u.equals(this.user)) continue;
			JMenuItem item = new JMenuItem(u.getName());
			item.addActionListener(e -> selectArchivosUser(u.getId()));
			menu.add(item);
		}
		menu.show(archivosFilterBtn, 0, archivosFilterBtn.getHeight());
	}

	private void selectArchivosUser(String userId) {
		selectedArchivosUserId = userId;
		refreshArchivosUserFilter();
	}

	private String getArchivosFilterLabel() {
		if (selectedArchivosUserId == null) return "Todos";
		if (selectedArchivosUserId.equals(this.user.getId())) return "Yo";
		for (User u : remoteUsers) {
			if (selectedArchivosUserId.equals(u.getId())) return u.getName();
		}
		return "Todos";
	}

	private String getSelectedFileUserId() {
		return selectedArchivosUserId != null ? selectedArchivosUserId : this.user.getId();
	}

	private void showArchivosTab() {
		ensureArchivosTab();
		if (findTabByTitle("Archivos") < 0) {
			insertSystemTab("Archivos", boardIcon(), archivosContainerPanel, "Vista unificada de archivos");
		}
	}

	private void refreshArchivosTable() {
		if (archivosTab == null) return;
		List<User> users = new ArrayList<>();
		if (selectedArchivosUserId == null || selectedArchivosUserId.equals(this.user.getId())) {
			users.add(this.user);
		}
		for (User u : remoteUsers) {
			if (u.equals(this.user)) continue;
			if (selectedArchivosUserId == null || selectedArchivosUserId.equals(u.getId())) {
				users.add(u);
			}
		}
		if (users.isEmpty()) {
			users.add(this.user);
			selectedArchivosUserId = this.user.getId();
		}
		FileTableModel ftm = new FileTableModel(users);
		archivosTab.getjTable1().setModel(ftm);
		archivosTab.getjTable1().setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		setFixedColumnWidth(0, 28);
		setFixedColumnWidth(2, 80);
		setFixedColumnWidth(3, 125);
		setFixedColumnWidth(4, 105);
		refreshArchivosBreadcrumb();
	}

	private void setFixedColumnWidth(int column, int width) {
		archivosTab.getjTable1().getColumnModel().getColumn(column).setPreferredWidth(width);
		archivosTab.getjTable1().getColumnModel().getColumn(column).setMinWidth(width);
		archivosTab.getjTable1().getColumnModel().getColumn(column).setMaxWidth(width);
	}

	private void refreshArchivosBreadcrumb() {
		String userId = getSelectedFileUserId();
		String navPath = navigationPaths.getOrDefault(userId, "");
		boolean hasPath = !navPath.isEmpty();
		archivosBackBtn.setVisible(hasPath);
		archivosBreadcrumb.setText(hasPath ? navPath : "");
	}

	private void loadChatTab() {
		if (chatArea != null) {
			return;
		}

		chatArea = new JTextPane();
		chatArea.setEditable(false);
		chatArea.setBackground(Color.WHITE);
		chatArea.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (e.isPopupTrigger()) showChatMessageMenu(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger()) showChatMessageMenu(e);
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
					showChatMessageMenu(e);
					return;
				}
				openChatLinkAt(e.getPoint());
			}

			@Override
			public void mouseExited(MouseEvent e) {
				chatArea.setCursor(java.awt.Cursor.getDefaultCursor());
			}
		});
		chatArea.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				chatArea.setCursor(chatLinkAt(e.getPoint()) != null
						? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
						: java.awt.Cursor.getDefaultCursor());
			}
		});

		chatInput = new JTextField();
		chatReplyLabel = new JLabel("Re:");
		chatReplyLabel.setForeground(Color.GRAY);
		chatReplyLabel.setVisible(false);
		JButton fileButton = new JButton(attachmentIcon());
		fileButton.setToolTipText("Adjuntar archivo");
		fileButton.setFocusable(false);
		fileButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
		JButton sendButton = new JButton("Enviar");
		Runnable send = () -> sendChatMessage();
		chatInput.addActionListener(e -> send.run());
		fileButton.addActionListener(e -> chooseAndSendChatFile());
		sendButton.addActionListener(e -> send.run());

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		buttonPanel.add(fileButton);
		buttonPanel.add(sendButton);

		JPanel inputPanel = new JPanel(new BorderLayout(4, 4));
		inputPanel.setPreferredSize(new java.awt.Dimension(10, 32));
		inputPanel.setMinimumSize(new java.awt.Dimension(10, 28));
		inputPanel.add(chatReplyLabel, BorderLayout.WEST);
		inputPanel.add(chatInput, BorderLayout.CENTER);
		inputPanel.add(buttonPanel, BorderLayout.EAST);

		JScrollPane scroll = new JScrollPane(chatArea);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scroll.setMinimumSize(new java.awt.Dimension(0, 0));
		scroll.setPreferredSize(new java.awt.Dimension(100, 100));

		JPanel panel = new JPanel(new BorderLayout(4, 4));
		pinnedChatLabel = new JLabel();
		pinnedChatLabel.setOpaque(true);
		pinnedChatLabel.setBackground(new Color(245, 245, 245));
		pinnedChatLabel.setForeground(Color.DARK_GRAY);
		pinnedChatLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6));
		pinnedChatLabel.setVisible(false);
		pinnedChatLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		pinnedChatLabel.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) { jumpToPinnedChatMessage(); }
		});
		panel.add(pinnedChatLabel, BorderLayout.NORTH);
		panel.add(scroll, BorderLayout.CENTER);
		panel.add(inputPanel, BorderLayout.SOUTH);
		TransferHandler chatFileTransferHandler = new TransferHandler() {
			@Override
			public boolean canImport(TransferHandler.TransferSupport support) {
				return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
			}

			@Override
			public boolean importData(TransferHandler.TransferSupport support) {
				if (!canImport(support)) return false;
				try {
					Transferable transferable = support.getTransferable();
					@SuppressWarnings("unchecked")
					List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
					for (File file : files) {
						sendChatFile(file);
					}
					return true;
				} catch (Exception e) {
					log.err("Error al recibir archivo arrastrado al chat: " + e.getMessage());
					return false;
				}
			}
		};
		panel.setTransferHandler(chatFileTransferHandler);
		chatArea.setTransferHandler(chatFileTransferHandler);
		scroll.setTransferHandler(chatFileTransferHandler);

		chatContainerPanel = panel;
		insertSystemTab("Chat", chatIcon(), chatContainerPanel, "Chat grupal del workspace");
	}

	private void chooseAndSendChatFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (chooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
			sendChatFile(chooser.getSelectedFile());
		}
	}

	private ImageIcon attachmentIcon() {
		BufferedImage img = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawArc(6, 5, 9, 12, 35, 275);
		g.drawArc(9, 7, 5, 8, 35, 275);
		g.drawLine(12, 7, 9, 14);
		g.dispose();
		return new ImageIcon(img);
	}

	private void sendChatFile(File sourceFile) {
		if (sourceFile == null || !sourceFile.isFile()) {
			log.err("Solo se pueden enviar archivos desde el chat");
			return;
		}
		String baseDir = view.getjTextField4().getText();
		if (baseDir == null || baseDir.trim().isEmpty()) {
			log.err("No hay directorio compartido configurado para enviar archivos");
			return;
		}
		if (wsClient == null) {
			log.err("No hay conexion al workspace para enviar archivos por chat");
			return;
		}

		try {
			File sharedDir = new File(baseDir);
			sharedDir.mkdirs();
			String targetPath = uniqueFilePath(new File(sharedDir, sourceFile.getName()).getAbsolutePath());
			File targetFile = new File(targetPath);
			Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);

			QFile qfile = new QFile();
			qfile.setName(targetFile.getName());
			qfile.setSize(targetFile.length());
			qfile.setDate(targetFile.lastModified());
			qfile.setRelativePath(targetFile.getName());
			qfile.setOperation(QFile.OPERATION_DOWNLOAD);

			refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
			wsClient.sendEvent(new Event("Archivo de chat enviado", user, qfile));
		} catch (Exception e) {
			log.err("Error al enviar archivo por chat: " + e.getMessage());
		}
	}

	private void sendChatMessage() {
		if (chatInput == null || wsClient == null) {
			return;
		}
		String message = chatInput.getText().trim();
		if (message.isEmpty()) {
			return;
		}
		chatInput.setText("");
		ChatMessage chatMessage = new ChatMessage();
		chatMessage.id = UUIDUtils.generate();
		chatMessage.senderName = user.getName();
		chatMessage.text = message;
		if (replyingToChatMessage != null) {
			chatMessage.replyToName = replyingToChatMessage.senderName;
			chatMessage.replyToText = replyingToChatMessage.text;
			replyingToChatMessage = null;
			chatInput.setToolTipText(null);
			if (chatReplyLabel != null) chatReplyLabel.setVisible(false);
		}
		wsClient.sendEvent(new Event("Mensaje de chat", user, chatMessage.toPayload()));
	}

	private void appendChatMessage(User sender, String message) {
		if (chatArea == null || message == null) {
			return;
		}
		ChatMessage chatMessage = ChatMessage.fromPayload(message, sender);
		if (sender != null && sender.getId() != null && message != null && !message.trim().isEmpty()) {
			chatActiveUserIds.add(sender.getId());
		}
		String name = chatMessage.senderName != null ? chatMessage.senderName : "Usuario";
		int start = chatArea.getStyledDocument().getLength();
		int alignment = sender != null && sender.equals(user) ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT;
		if (chatMessage.replyToText != null && !chatMessage.replyToText.isBlank()) {
			SimpleAttributeSet quote = new SimpleAttributeSet();
			StyleConstants.setForeground(quote, Color.GRAY);
			appendChatText("  Respondiendo a " + safeChatPreview(chatMessage.replyToName) + ": "
					+ safeChatPreview(chatMessage.replyToText) + System.lineSeparator(), quote, alignment);
		}
		String line = String.format("[%tH:%<tM] %s: %s%n", new Date(), name, chatMessage.text);
		SimpleAttributeSet attrs = new SimpleAttributeSet();
		StyleConstants.setForeground(attrs, mentionsCurrentUser(chatMessage.text) ? Color.RED : Color.BLACK);
		appendChatText(line, attrs, alignment);
		int end = chatArea.getStyledDocument().getLength();
		chatMessages.put(chatMessage.id, chatMessage);
		chatMessageRanges.put(chatMessage.id, new int[]{start, end});
	}

	private void appendChatSystemMessage(String message) {
		appendChatSystemMessage(message, Color.GRAY);
	}

	private void appendChatSystemMessage(String message, Color color) {
		if (chatArea == null || message == null) return;
		SimpleAttributeSet attrs = new SimpleAttributeSet();
		StyleConstants.setForeground(attrs, color);
		appendChatText(String.format("[%tH:%<tM] %s%n", new Date(), message), attrs, StyleConstants.ALIGN_LEFT);
	}

	private String appendChatFileMessage(User sender, QFile qfile, String localPath) {
		if (chatArea == null || sender == null || qfile == null) return null;
		String name = sender.getName() != null ? sender.getName() : "Usuario";
		int alignment = sender.equals(user) ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT;
		SimpleAttributeSet normal = new SimpleAttributeSet();
		StyleConstants.setForeground(normal, Color.GRAY);
		String header = I18n.get("chat.fileSent", name, qfile.getName());
		appendChatText(String.format("[%tH:%<tM] %s%n", new Date(), header), normal, alignment);
		String linkId = UUIDUtils.generate();
		if (localPath != null) {
			chatFileLinks.put(linkId, localPath);
		}
		if (qfile.getTransferId() != null) {
			chatTransferLinks.put(qfile.getTransferId(), linkId);
		}
		SimpleAttributeSet link = new SimpleAttributeSet();
		StyleConstants.setForeground(link, new Color(0, 85, 170));
		StyleConstants.setUnderline(link, true);
		link.addAttribute("chatLink", linkId);
		appendChatText(qfile.getName(), link, alignment);
		appendChatText(System.lineSeparator(), normal, alignment);
		return linkId;
	}

	private void appendChatText(String text, AttributeSet attrs) {
		appendChatText(text, attrs, StyleConstants.ALIGN_LEFT);
	}

	private void appendChatText(String text, AttributeSet attrs, int alignment) {
		try {
			StyledDocument doc = chatArea.getStyledDocument();
			int start = doc.getLength();
			doc.insertString(start, text, attrs);
			SimpleAttributeSet paragraph = new SimpleAttributeSet();
			StyleConstants.setAlignment(paragraph, alignment);
			doc.setParagraphAttributes(start, text.length(), paragraph, false);
			chatArea.setCaretPosition(doc.getLength());
		} catch (Exception e) {
			log.err("Error al escribir en chat: " + e.getMessage());
		}
	}

	private boolean mentionsCurrentUser(String message) {
		String username = user.getName() != null ? user.getName().trim() : "";
		return message.contains("@all") || (!username.isEmpty() && message.contains("@" + username));
	}

	private String safeChatPreview(String text) {
		if (text == null || text.isBlank()) return "mensaje";
		String singleLine = text.replace('\n', ' ').replace('\r', ' ').trim();
		return singleLine.length() > 80 ? singleLine.substring(0, 77) + "..." : singleLine;
	}

	private ChatMessage chatMessageAt(Point point) {
		try {
			int pos = chatArea.viewToModel2D(point);
			for (Map.Entry<String, int[]> entry : chatMessageRanges.entrySet()) {
				int[] range = entry.getValue();
				if (pos >= range[0] && pos < range[1]) {
					return chatMessages.get(entry.getKey());
				}
			}
		} catch (Exception ignored) {}
		return null;
	}

	private void showChatMessageMenu(MouseEvent e) {
		ChatMessage message = chatMessageAt(e.getPoint());
		if (message == null) return;
		JPopupMenu menu = new JPopupMenu();
		JMenuItem reply = new JMenuItem("Responder");
		reply.addActionListener(ev -> {
			replyingToChatMessage = message;
			chatInput.setText("");
			chatInput.setToolTipText(I18n.get("chat.responding") + " " + safeChatPreview(message.text));
			if (chatReplyLabel != null) chatReplyLabel.setVisible(true);
			chatInput.requestFocusInWindow();
		});
		JMenuItem pin = new JMenuItem("Fijar");
		pin.addActionListener(ev -> {
			if (wsClient != null) {
				wsClient.sendEvent(new Event("Mensaje de chat fijado", user, message.toPinnedPayload()));
			}
		});
		menu.add(reply);
		menu.add(pin);
		menu.show(chatArea, e.getX(), e.getY());
	}

	private void applyPinnedChatMessage(ChatMessage message) {
		if (message == null || pinnedChatLabel == null) return;
		String name = message.senderName != null ? message.senderName : "Usuario";
		pinnedChatMessageId = message.id;
		pinnedChatLabel.setText("Fijado: " + name + ": " + safeChatPreview(message.text));
		pinnedChatLabel.setVisible(true);
	}

	private void jumpToPinnedChatMessage() {
		if (pinnedChatMessageId == null || chatArea == null) return;
		int[] range = chatMessageRanges.get(pinnedChatMessageId);
		if (range == null) return;
		chatArea.requestFocusInWindow();
		chatArea.setCaretPosition(Math.max(0, Math.min(range[0], chatArea.getDocument().getLength())));
	}

	private String chatLinkAt(Point point) {
		try {
			int pos = chatArea.viewToModel2D(point);
			if (pos < 0) return null;
			Object link = chatArea.getStyledDocument().getCharacterElement(pos).getAttributes().getAttribute("chatLink");
			return link != null ? String.valueOf(link) : null;
		} catch (Exception e) {
			return null;
		}
	}

	private void openChatLinkAt(Point point) {
		String linkId = chatLinkAt(point);
		if (linkId == null) return;
		String path = chatFileLinks.get(linkId);
		if (path == null) {
			log.info("El archivo aun se esta descargando");
			return;
		}
		try {
			exec.open(path);
		} catch (Exception e) {
			log.err("No se pudo abrir el archivo adjunto: " + e.getMessage());
		}
	}

	private void handleChatFileEvent(Event event) {
		QFile qfile = event.getFile();
		User sender = event.getUser();
		if (qfile == null || sender == null) return;
		if (sender.equals(user)) {
			appendChatFileMessage(sender, qfile, new File(view.getjTextField4().getText(), qfile.getRelativePath()).getAbsolutePath());
			return;
		}
		markTabIfInactive("Chat");
		log.info("Chat: " + sender.getName() + " envio el archivo " + qfile.getName());
		qfile.setOwner(sender);
		String linkId = appendChatFileMessage(sender, qfile, null);
		downloadFile(sender, qfile);
		if (qfile.getTransferId() != null && linkId != null) {
			chatTransferLinks.put(qfile.getTransferId(), linkId);
		}
	}

	private void applyRemoteChatHistory(String history) {
		if (chatArea == null || history == null || history.isEmpty()) return;
		if (chatArea.getDocument().getLength() > 0) return;
		try {
			SimpleAttributeSet attrs = new SimpleAttributeSet();
			StyleConstants.setForeground(attrs, Color.BLACK);
			appendChatText(history, attrs, StyleConstants.ALIGN_LEFT);
			appendChatSystemMessage(I18n.get("chat.historyLoaded"));
			markTabIfInactive("Chat");
		} catch (Exception e) {
			log.err("Error al cargar historial de chat remoto: " + e.getMessage());
		}
	}

	private void insertSystemTab(String title, ImageIcon icon, Component component, String tooltip) {
		if (findTabByTitle(title) >= 0) {
			return;
		}
		int idx = findTabByTitle("Log");
		if (idx < 0) {
			idx = findTabByTitle("Configuración");
		}
		if (idx < 0) {
			view.getjTabbedPane().addTab(title, icon, component, tooltip);
		} else {
			view.getjTabbedPane().insertTab(title, icon, component, tooltip, idx);
		}
	}

	private int findTabByTitle(String title) {
		String cleanTitle = cleanTabTitle(title);
		for (int i = 0; i < view.getjTabbedPane().getTabCount(); i++) {
			if (cleanTitle.equals(cleanTabTitle(view.getjTabbedPane().getTitleAt(i)))) {
				return i;
			}
		}
		return -1;
	}

	private String cleanTabTitle(String title) {
		return title != null && title.startsWith("* ") ? title.substring(2) : title;
	}

	private void markTabIfInactive(String title) {
		int idx = findTabByTitle(title);
		if (idx < 0 || idx == view.getjTabbedPane().getSelectedIndex()) return;
		String cleanTitle = cleanTabTitle(view.getjTabbedPane().getTitleAt(idx));
		if (markedTabs.add(cleanTitle)) {
			view.getjTabbedPane().setTitleAt(idx, "* " + cleanTitle);
		}
	}

	private void clearSelectedTabMark() {
		int idx = view.getjTabbedPane().getSelectedIndex();
		if (idx < 0) return;
		String title = view.getjTabbedPane().getTitleAt(idx);
		String cleanTitle = cleanTabTitle(title);
		if (!title.equals(cleanTitle)) {
			view.getjTabbedPane().setTitleAt(idx, cleanTitle);
		}
		markedTabs.remove(cleanTitle);
	}

	private void markLogIfInactive() {
		markTabIfInactive("Log");
	}

	private ImageIcon chatIcon() {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.BLACK);
		g.drawRoundRect(1, 2, 13, 9, 4, 4);
		g.drawLine(5, 11, 3, 14);
		g.drawLine(5, 11, 8, 11);
		g.drawLine(4, 6, 12, 6);
		g.drawLine(4, 8, 10, 8);
		g.dispose();
		return new ImageIcon(img);
	}

	private ImageIcon noteIcon() {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.BLACK);
		g.drawRect(3, 1, 10, 14);
		g.drawLine(5, 5, 11, 5);
		g.drawLine(5, 8, 11, 8);
		g.drawLine(5, 11, 9, 11);
		g.dispose();
		return new ImageIcon(img);
	}

	private ImageIcon notesImageIcon() {
		BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawRect(2, 3, 14, 12);
		g.drawLine(3, 14, 7, 9);
		g.drawLine(7, 9, 10, 12);
		g.drawLine(10, 12, 15, 6);
		g.fillOval(5, 5, 3, 3);
		g.dispose();
		return new ImageIcon(img);
	}

	private ImageIcon notesImageSizeIcon(boolean plus) {
		BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawRect(2, 4, 9, 8);
		if (plus) {
			g.drawLine(9, 11, 16, 16);
			g.drawLine(16, 16, 16, 12);
			g.drawLine(16, 16, 12, 16);
		} else {
			g.drawLine(9, 11, 4, 16);
			g.drawLine(4, 16, 4, 12);
			g.drawLine(4, 16, 8, 16);
		}
		g.dispose();
		return new ImageIcon(img);
	}

	private ImageIcon boardIcon() {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.BLACK);
		g.drawRect(1, 2, 14, 10);
		g.drawLine(5, 14, 11, 14);
		g.drawLine(8, 12, 8, 14);
		g.drawLine(4, 9, 7, 6);
		g.drawLine(7, 6, 10, 8);
		g.dispose();
		return new ImageIcon(img);
	}

	private ImageIcon toolIcon(String tool) {
		BufferedImage img = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Color.BLACK);
		if ("Seleccionar".equals(tool)) {
			g.drawPolygon(new int[]{5, 5, 16, 12, 15, 12, 9}, new int[]{3, 18, 11, 10, 17, 18, 12}, 7);
		} else if ("Lápiz".equals(tool)) {
			g.drawLine(5, 17, 16, 6);
			g.drawLine(14, 4, 18, 8);
			g.drawLine(4, 18, 7, 15);
		} else if ("Texto".equals(tool)) {
			g.drawLine(5, 5, 17, 5);
			g.drawLine(11, 5, 11, 17);
			g.drawLine(8, 17, 14, 17);
		} else if ("Imagen".equals(tool)) {
			g.drawRect(4, 5, 14, 12);
			g.drawLine(5, 16, 9, 11);
			g.drawLine(9, 11, 12, 14);
			g.drawLine(12, 14, 17, 8);
			g.fillOval(7, 7, 3, 3);
		} else if ("Flecha".equals(tool)) {
			g.drawLine(4, 16, 17, 5);
			g.drawLine(17, 5, 15, 12);
			g.drawLine(17, 5, 10, 7);
		} else if ("Círculo".equals(tool)) {
			g.drawOval(4, 4, 14, 14);
		} else if ("Cuadrado".equals(tool)) {
			g.drawRect(5, 5, 12, 12);
		} else if ("Rectángulo".equals(tool)) {
			g.drawRect(3, 7, 16, 10);
		} else if ("Triángulo".equals(tool)) {
			g.drawPolygon(new int[]{11, 4, 18}, new int[]{4, 18, 18}, 3);
		}
		g.dispose();
		return new ImageIcon(img);
	}

	private ImageIcon strokeIcon(boolean plus) {
		BufferedImage img = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		if (plus) {
			g.drawRect(5, 5, 8, 8);
			g.drawLine(8, 8, 18, 18);
			g.drawLine(18, 18, 18, 12);
			g.drawLine(18, 18, 12, 18);
		} else {
			g.drawRect(7, 7, 10, 10);
			g.drawLine(4, 4, 13, 13);
			g.drawLine(4, 4, 4, 10);
			g.drawLine(4, 4, 10, 4);
		}
		g.dispose();
		return new ImageIcon(img);
	}

	private ImageIcon colorIcon(Color color) {
		BufferedImage img = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.BLACK);
		g.drawRect(3, 3, 16, 16);
		g.setColor(color);
		g.fillRect(5, 5, 13, 13);
		g.dispose();
		return new ImageIcon(img);
	}

	private ImageIcon clearIcon() {
		BufferedImage img = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Color.BLACK);
		g.drawRect(5, 7, 12, 11);
		g.drawLine(8, 5, 14, 5);
		g.drawLine(7, 6, 15, 6);
		g.drawLine(8, 9, 8, 16);
		g.drawLine(11, 9, 11, 16);
		g.drawLine(14, 9, 14, 16);
		g.dispose();
		return new ImageIcon(img);
	}

	private ImageIcon exportIcon() {
		BufferedImage img = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Color.BLACK);
		g.drawRect(5, 3, 12, 16);
		g.drawLine(8, 8, 14, 8);
		g.drawLine(8, 11, 14, 11);
		g.drawLine(11, 14, 11, 18);
		g.drawLine(8, 15, 11, 18);
		g.drawLine(14, 15, 11, 18);
		g.dispose();
		return new ImageIcon(img);
	}

	private ImageIcon helpIcon() {
		BufferedImage img = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawOval(4, 4, 14, 14);
		g.drawString("?", 9, 16);
		g.dispose();
		return new ImageIcon(img);
	}

	private void broadcastWhiteboard() {
		if (wsClient != null && whiteboardCanvas != null) {
			wsClient.sendEvent(new Event("Pizarra actualizada", user, whiteboardCanvas.serialize()));
		}
	}

	private void sendInitialWorkspaceState(String targetId) {
		if (wsClient == null || targetId == null) return;

		for (String comp : enabledComplementos) {
			wsClient.sendEvent(new Event("__to:" + targetId + ":Complemento habilitado", user, comp));
		}

		if (enabledComplementos.contains("Pizarra") && whiteboardCanvas != null) {
			String state = whiteboardCanvas.serialize();
			wsClient.sendEvent(new Event("__to:" + targetId + ":Pizarra actualizada", user, state));
		}

		if (enabledComplementos.contains("Notas") && notesPane != null) {
			String state = serializeNotesState();
			if (state != null) {
				wsClient.sendEvent(new Event("__to:" + targetId + ":Notas actualizadas", user, state));
			}
		}

		if (enabledComplementos.contains("Chat") && chatArea != null) {
			String chatHistory = chatArea.getText();
			if (chatHistory != null && !chatHistory.isEmpty()) {
				wsClient.sendEvent(new Event("__to:" + targetId + ":Historial de chat", user, chatHistory));
			}
		}
	}

	private void scheduleNotesBroadcast() {
		if (!applyingRemoteNotes && notesSyncTimer != null && System.currentTimeMillis() >= suppressNotesBroadcastUntil) {
			notesSyncTimer.restart();
		}
	}

	private void attachNotesDocumentListener() {
		if (notesPane == null) return;
		if (notesDocumentListener == null) {
			notesDocumentListener = new DocumentListener() {
				public void insertUpdate(DocumentEvent e) { scheduleNotesBroadcast(); }
				public void removeUpdate(DocumentEvent e) { scheduleNotesBroadcast(); }
				public void changedUpdate(DocumentEvent e) { scheduleNotesBroadcast(); }
			};
		}
		notesPane.getDocument().addDocumentListener(notesDocumentListener);
	}

	private void broadcastNotes() {
		if (wsClient == null || notesPane == null || applyingRemoteNotes || System.currentTimeMillis() < suppressNotesBroadcastUntil) {
			return;
		}
		String state = serializeNotesState();
		if (state == null || state.equals(lastSentNotesState)) {
			return;
		}
		lastSentNotesState = state;
		new Thread(() -> wsClient.sendEvent(new Event("Notas actualizadas", user, state)), "notes-sync").start();
	}

	private String serializeNotesState() {
		try {
			StyledDocument doc = notesPane.getStyledDocument();
			StringBuilder state = new StringBuilder("QNOTES2\n");
			StringBuilder text = new StringBuilder();
			AttributeSet currentAttrs = null;
			for (int i = 0; i < doc.getLength(); i++) {
				AttributeSet attrs = doc.getCharacterElement(i).getAttributes();
				IconInfo icon = iconInfo(attrs);
				if (icon != null) {
					appendNotesTextRun(state, text, currentAttrs);
					text.setLength(0);
					currentAttrs = null;
					state.append("I|").append(icon.width).append('|').append(icon.height).append('|').append(icon.base64).append('\n');
					continue;
				}
				if (currentAttrs == null || !sameNoteStyle(currentAttrs, attrs)) {
					appendNotesTextRun(state, text, currentAttrs);
					text.setLength(0);
					currentAttrs = attrs;
				}
				text.append(doc.getText(i, 1));
			}
			appendNotesTextRun(state, text, currentAttrs);
			return state.toString();
		} catch (Exception e) {
			log.err("Error al serializar notas: " + e.getMessage());
			return null;
		}
	}

	private void appendNotesTextRun(StringBuilder state, StringBuilder text, AttributeSet attrs) {
		if (attrs == null || text.length() == 0) return;
		String encoded = Base64.getEncoder().encodeToString(text.toString().getBytes(StandardCharsets.UTF_8));
		state.append("T|")
				.append(StyleConstants.isBold(attrs)).append('|')
				.append(StyleConstants.isItalic(attrs)).append('|')
				.append(StyleConstants.isUnderline(attrs)).append('|')
				.append(Math.max(1, StyleConstants.getFontSize(attrs))).append('|')
				.append(colorToHex(noteForeground(attrs))).append('|')
				.append(encoded).append('\n');
	}

	private boolean sameNoteStyle(AttributeSet a, AttributeSet b) {
		return StyleConstants.isBold(a) == StyleConstants.isBold(b)
				&& StyleConstants.isItalic(a) == StyleConstants.isItalic(b)
				&& StyleConstants.isUnderline(a) == StyleConstants.isUnderline(b)
				&& StyleConstants.getFontSize(a) == StyleConstants.getFontSize(b)
				&& noteForeground(a).equals(noteForeground(b));
	}

	private Color noteForeground(AttributeSet attrs) {
		Color color = StyleConstants.getForeground(attrs);
		return color != null ? color : Color.BLACK;
	}

	private String colorToHex(Color color) {
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	private IconInfo iconInfo(AttributeSet attrs) throws Exception {
		Object iconObj = attrs.getAttribute(StyleConstants.IconAttribute);
		if (!(iconObj instanceof ImageIcon icon)) return null;
		Image image = icon.getImage();
		int width = Math.max(1, icon.getIconWidth());
		int height = Math.max(1, icon.getIconHeight());
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		g.drawImage(image, 0, 0, width, height, null);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(bi, "png", out);
		return new IconInfo(width, height, Base64.getEncoder().encodeToString(out.toByteArray()));
	}

	private void applyRemoteNotes(String text) {
		if (notesPane == null || text == null) {
			return;
		}
		try {
			if (notesSyncTimer != null) {
				notesSyncTimer.stop();
			}
			applyingRemoteNotes = true;
			suppressNotesBroadcastUntil = System.currentTimeMillis() + 1200;
			lastSentNotesState = text;
			int oldCaret = notesPane.getCaretPosition();
			DefaultStyledDocument doc = text.startsWith("QNOTES1\n") || text.startsWith("QNOTES2\n")
					? parseNotesState(text) : parseLegacyRtfNotes(text);
			notesPane.setDocument(doc);
			attachNotesDocumentListener();
			notesPane.setCaretPosition(Math.min(oldCaret, notesPane.getDocument().getLength()));
		} catch (Exception e) {
			log.err("Error al aplicar notas remotas: " + e.getMessage());
		} finally {
			applyingRemoteNotes = false;
		}
	}

	private DefaultStyledDocument parseLegacyRtfNotes(String text) throws Exception {
		byte[] data = Base64.getDecoder().decode(text);
		DefaultStyledDocument doc = new DefaultStyledDocument();
		new RTFEditorKit().read(new ByteArrayInputStream(data), doc, 0);
		return doc;
	}

	private DefaultStyledDocument parseNotesState(String state) throws Exception {
		DefaultStyledDocument doc = new DefaultStyledDocument();
		String[] lines = state.split("\n");
		for (int i = 1; i < lines.length; i++) {
			String line = lines[i];
			if (line.isEmpty()) continue;
			String[] p = line.split("\\|", 7);
			if ("T".equals(p[0]) && (p.length == 6 || p.length == 7)) {
				SimpleAttributeSet attrs = new SimpleAttributeSet();
				StyleConstants.setBold(attrs, Boolean.parseBoolean(p[1]));
				StyleConstants.setItalic(attrs, Boolean.parseBoolean(p[2]));
				StyleConstants.setUnderline(attrs, Boolean.parseBoolean(p[3]));
				StyleConstants.setFontSize(attrs, Integer.parseInt(p[4]));
				String encodedText = p[5];
				if (p.length == 7) {
					StyleConstants.setForeground(attrs, Color.decode(p[5]));
					encodedText = p[6];
				}
				String decoded = new String(Base64.getDecoder().decode(encodedText), StandardCharsets.UTF_8);
				doc.insertString(doc.getLength(), decoded, attrs);
			} else if ("I".equals(p[0]) && p.length == 4) {
				byte[] bytes = Base64.getDecoder().decode(p[3]);
				Image image = ImageIO.read(new ByteArrayInputStream(bytes));
				Image scaled = image.getScaledInstance(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Image.SCALE_SMOOTH);
				SimpleAttributeSet attrs = new SimpleAttributeSet();
				StyleConstants.setIcon(attrs, new ImageIcon(scaled));
				doc.insertString(doc.getLength(), " ", attrs);
			}
		}
		return doc;
	}

	private void loadWhiteboardTab() {
		if (whiteboardCanvas != null) {
			return;
		}
		whiteboardCanvas = new WhiteboardCanvas();
		JPanel toolbar = new JPanel();
		toolbar.setLayout(new javax.swing.BoxLayout(toolbar, javax.swing.BoxLayout.Y_AXIS));
		java.awt.Dimension toolSize = new java.awt.Dimension(34, 32);
		JPanel currentRow = null;
		int buttonsInRow = 0;
		String[] tools = {"Seleccionar", "Lápiz", "Texto", "Imagen", "Flecha", "Círculo", "Cuadrado", "Rectángulo", "Triángulo"};
		for (String tool : tools) {
			JButton b = whiteboardToolButton(toolIcon(tool), tool, toolSize);
			b.addActionListener(e -> {
				if ("Imagen".equals(tool)) whiteboardCanvas.chooseImage(30, 30);
				else whiteboardCanvas.setTool(tool);
			});
			if (currentRow == null || buttonsInRow == 2) {
				currentRow = whiteboardToolRow();
				toolbar.add(currentRow);
				buttonsInRow = 0;
			}
			currentRow.add(b);
			buttonsInRow++;
		}
		JButton smallerText = whiteboardToolButton(strokeIcon(false), "Reducir tamaño/grosor", toolSize);
		smallerText.addActionListener(e -> whiteboardCanvas.changeSelectedSizeOrStroke(-2));
		JButton biggerText = whiteboardToolButton(strokeIcon(true), "Aumentar tamaño/grosor", toolSize);
		biggerText.addActionListener(e -> whiteboardCanvas.changeSelectedSizeOrStroke(2));
		whiteboardColorButton = new JButton();
		whiteboardColorButton.setToolTipText("Color actual");
		whiteboardColorButton.setIcon(colorIcon(Color.BLACK));
		whiteboardColorButton.setContentAreaFilled(false);
		whiteboardColorButton.setOpaque(false);
		whiteboardColorButton.setBorderPainted(true);
		whiteboardColorButton.setPreferredSize(toolSize);
		whiteboardColorButton.setMaximumSize(toolSize);
		whiteboardColorButton.setMinimumSize(toolSize);
		whiteboardColorButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		whiteboardColorButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
		whiteboardColorButton.setFocusable(false);
		whiteboardColorButton.addActionListener(e -> {
			Color selected = JColorChooser.showDialog(view, "Color", whiteboardCanvas.getDrawColor());
			if (selected != null) whiteboardCanvas.setDrawColor(selected);
		});
		JButton clear = whiteboardToolButton(clearIcon(), "Limpiar pizarra", toolSize);
		clear.addActionListener(e -> {
			whiteboardCanvas.clear();
			broadcastWhiteboard();
		});
		JButton export = whiteboardToolButton(exportIcon(), "Guardar pizarra como PNG", toolSize);
		export.addActionListener(e -> exportWhiteboardImage());
		JButton[] extraButtons = {smallerText, biggerText, whiteboardColorButton, clear, export};
		for (JButton button : extraButtons) {
			if (currentRow == null || buttonsInRow == 2) {
				currentRow = whiteboardToolRow();
				toolbar.add(currentRow);
				buttonsInRow = 0;
			}
			currentRow.add(button);
			buttonsInRow++;
		}
		toolbar.add(javax.swing.Box.createVerticalGlue());
		toolbar.setPreferredSize(new java.awt.Dimension(toolSize.width * 2 + 10, toolSize.height * 7));
		JPanel panel = new JPanel(new BorderLayout(4, 4));
		panel.add(toolbar, BorderLayout.WEST);
		panel.add(whiteboardCanvas, BorderLayout.CENTER);
		whiteboardContainerPanel = panel;
		insertSystemTab("Pizarra", boardIcon(), whiteboardContainerPanel, "Pizarra colaborativa simple");
	}

	private JPanel whiteboardToolRow() {
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 2));
		row.setAlignmentX(Component.CENTER_ALIGNMENT);
		row.setMaximumSize(new java.awt.Dimension(78, 36));
		row.setPreferredSize(new java.awt.Dimension(78, 36));
		return row;
	}

	private JButton whiteboardToolButton(ImageIcon icon, String tooltip, java.awt.Dimension size) {
		JButton button = new JButton(icon);
		button.setToolTipText(tooltip);
		button.setAlignmentX(Component.CENTER_ALIGNMENT);
		button.setPreferredSize(size);
		button.setMaximumSize(size);
		button.setMinimumSize(size);
		button.setMargin(new java.awt.Insets(2, 2, 2, 2));
		button.setFocusable(false);
		return button;
	}

	private void updateColorButton(Color color) {
		if (whiteboardColorButton != null) {
			whiteboardColorButton.setIcon(colorIcon(color));
			whiteboardColorButton.repaint();
		}
	}

	private void exportWhiteboardImage() {
		if (whiteboardCanvas == null) return;
		try {
			File dir = new File(view.getjTextField4().getText());
			dir.mkdirs();
			String path = uniqueFilePath(new File(dir, "pizarra.png").getAbsolutePath());
			BufferedImage img = whiteboardCanvas.toImage();
			ImageIO.write(img, "png", new File(path));
			log.info("Pizarra guardada en " + path);
			refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
		} catch (Exception e) {
			log.err("No se pudo guardar la pizarra: " + e.getMessage());
		}
	}

	private void loadNotesTab() {
		if (notesPane != null) {
			return;
		}
		notesPane = new JTextPane();
		notesPane.setDocument(new DefaultStyledDocument());
		notesPane.setFont(notesPane.getFont().deriveFont((float) notesFontSize));
		JToolBar toolbar = new JToolBar();
		toolbar.setFloatable(false);
		JButton bold = toolbarButton("B", new StyledEditorKit.BoldAction());
		JButton italic = toolbarButton("I", new StyledEditorKit.ItalicAction());
		JButton underline = toolbarButton("U", new StyledEditorKit.UnderlineAction());
		JButton image = new JButton(notesImageIcon());
		image.setFocusable(false);
		image.setToolTipText("Insertar imagen en la nota");
		image.addActionListener(e -> chooseNotesImage());
		JButton smallerImage = new JButton(notesImageSizeIcon(false));
		smallerImage.setFocusable(false);
		smallerImage.setToolTipText("Achicar imagen seleccionada en la nota");
		smallerImage.addActionListener(e -> resizeSelectedNotesImage(0.8));
		JButton biggerImage = new JButton(notesImageSizeIcon(true));
		biggerImage.setFocusable(false);
		biggerImage.setToolTipText("Agrandar imagen seleccionada en la nota");
		biggerImage.addActionListener(e -> resizeSelectedNotesImage(1.25));
		toolbar.add(bold);
		toolbar.add(italic);
		toolbar.add(underline);
		JButton smaller = new JButton("A-");
		smaller.setFocusable(false);
		smaller.setToolTipText("Reducir tamaño del texto seleccionado o próximo texto");
		smaller.addActionListener(e -> changeNotesFontSize(-2));
		JButton bigger = new JButton("A+");
		bigger.setFocusable(false);
		bigger.setToolTipText("Aumentar tamaño del texto seleccionado o próximo texto");
		bigger.addActionListener(e -> changeNotesFontSize(2));
		JButton fontColor = new JButton(colorIcon(Color.BLACK));
		fontColor.setFocusable(false);
		fontColor.setToolTipText("Cambiar color de fuente");
		fontColor.addActionListener(e -> chooseNotesFontColor());
		JCheckBox translucent = new JCheckBox("Traslúcida");
		translucent.setToolTipText("Vuelve la ventana semitransparente y superpuesta");
		translucent.addActionListener(e -> setNotesOverlayMode(translucent.isSelected()));
		toolbar.addSeparator();
		toolbar.add(smaller);
		toolbar.add(bigger);
		toolbar.add(fontColor);
		toolbar.addSeparator();
		toolbar.add(image);
		toolbar.add(smallerImage);
		toolbar.add(biggerImage);
		toolbar.addSeparator();
		toolbar.add(translucent);
		notesSyncTimer = new Timer(900, e -> broadcastNotes());
		notesSyncTimer.setRepeats(false);
		notesPane.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("control V"), "notesPaste");
		notesPane.getActionMap().put("notesPaste", new javax.swing.AbstractAction() {
			@Override public void actionPerformed(java.awt.event.ActionEvent e) {
				if (!pasteImageIntoNotes()) {
					new DefaultEditorKit.PasteAction().actionPerformed(e);
				}
			}
		});
		attachNotesDocumentListener();
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(toolbar, BorderLayout.NORTH);
		panel.add(new JScrollPane(notesPane), BorderLayout.CENTER);
		notesContainerPanel = panel;
		insertSystemTab("Notas", noteIcon(), notesContainerPanel, "Notas compartidas con formato básico");
	}

	private JButton toolbarButton(String text, javax.swing.Action action) {
		JButton button = new JButton(text);
		button.setText(text);
		button.setFocusable(false);
		button.addActionListener(e -> {
			int start = notesPane.getSelectionStart();
			int end = notesPane.getSelectionEnd();
			notesPane.requestFocusInWindow();
			notesPane.select(start, end);
			action.actionPerformed(new java.awt.event.ActionEvent(notesPane,
					java.awt.event.ActionEvent.ACTION_PERFORMED, text));
			notesPane.select(start, end);
		});
		return button;
	}

	private void changeNotesFontSize(int delta) {
		notesFontSize = Math.max(8, Math.min(72, notesFontSize + delta));
		int start = notesPane.getSelectionStart();
		int end = notesPane.getSelectionEnd();
		notesPane.requestFocusInWindow();
		notesPane.select(start, end);
		new StyledEditorKit.FontSizeAction("font-size", notesFontSize)
				.actionPerformed(new java.awt.event.ActionEvent(notesPane, java.awt.event.ActionEvent.ACTION_PERFORMED, null));
		notesPane.select(start, end);
		notesPane.requestFocusInWindow();
	}

	private void chooseNotesFontColor() {
		int start = notesPane.getSelectionStart();
		int end = notesPane.getSelectionEnd();
		Color selected = JColorChooser.showDialog(view, "Color de fuente", Color.BLACK);
		if (selected == null) return;
		SimpleAttributeSet attrs = new SimpleAttributeSet();
		StyleConstants.setForeground(attrs, selected);
		if (end > start) {
			notesPane.getStyledDocument().setCharacterAttributes(start, end - start, attrs, false);
			notesPane.select(start, end);
		} else {
			notesPane.setCharacterAttributes(attrs, false);
		}
		notesPane.requestFocusInWindow();
		scheduleNotesBroadcast();
	}

	private void chooseNotesImage() {
		JFileChooser chooser = new JFileChooser();
		if (chooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
			try {
				insertImageIntoNotes(ImageIO.read(chooser.getSelectedFile()));
			} catch (Exception e) {
				log.err("No se pudo insertar imagen en notas: " + e.getMessage());
			}
		}
	}

	private boolean pasteImageIntoNotes() {
		try {
			Transferable t = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
			if (t != null && t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
				insertImageIntoNotes((Image) t.getTransferData(DataFlavor.imageFlavor));
				return true;
			}
		} catch (Exception e) {
			log.err("No se pudo pegar imagen en notas: " + e.getMessage());
		}
		return false;
	}

	private void insertImageIntoNotes(Image image) {
		if (image == null || notesPane == null) return;
		int maxWidth = Math.max(120, notesPane.getWidth() - 40);
		int width = image.getWidth(null);
		int height = image.getHeight(null);
		if (width > maxWidth) {
			height = Math.max(1, (int) Math.round(height * (maxWidth / (double) width)));
			width = maxWidth;
		}
		Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
		SimpleAttributeSet attrs = new SimpleAttributeSet();
		StyleConstants.setIcon(attrs, new ImageIcon(scaled));
		notesPane.setCaretPosition(notesPane.getSelectionStart());
		notesPane.replaceSelection(" ");
		notesPane.getStyledDocument().setCharacterAttributes(notesPane.getCaretPosition() - 1, 1, attrs, false);
		scheduleNotesBroadcast();
	}

	private void resizeSelectedNotesImage(double factor) {
		try {
			int pos = Math.max(0, Math.min(notesPane.getCaretPosition(), notesPane.getDocument().getLength() - 1));
			int iconPos = findNotesIconNear(pos);
			if (iconPos < 0) return;
			AttributeSet attrs = notesPane.getStyledDocument().getCharacterElement(iconPos).getAttributes();
			Object iconObj = attrs.getAttribute(StyleConstants.IconAttribute);
			if (!(iconObj instanceof ImageIcon icon)) return;
			int width = Math.max(24, (int) Math.round(icon.getIconWidth() * factor));
			int height = Math.max(24, (int) Math.round(icon.getIconHeight() * factor));
			Image scaled = icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
			SimpleAttributeSet newAttrs = new SimpleAttributeSet();
			StyleConstants.setIcon(newAttrs, new ImageIcon(scaled));
			notesPane.getStyledDocument().remove(iconPos, 1);
			notesPane.getStyledDocument().insertString(iconPos, " ", newAttrs);
			notesPane.setCaretPosition(iconPos);
			scheduleNotesBroadcast();
		} catch (Exception e) {
			log.err("No se pudo redimensionar imagen de notas: " + e.getMessage());
		}
	}

	private int findNotesIconNear(int pos) {
		StyledDocument doc = notesPane.getStyledDocument();
		for (int i = Math.max(0, pos - 2); i <= Math.min(doc.getLength() - 1, pos + 2); i++) {
			if (doc.getCharacterElement(i).getAttributes().getAttribute(StyleConstants.IconAttribute) instanceof ImageIcon) {
				return i;
			}
		}
		return -1;
	}

	private void setNotesOverlayMode(boolean enabled) {
		try {
			GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
			if (!gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
				log.err("El sistema no soporta transparencia de ventana");
				return;
			}
			view.setAlwaysOnTop(enabled || view.isAlwaysOnTop());
			view.setOpacity(enabled ? 0.45f : 1.0f);
		} catch (Exception ex) {
			log.err("El sistema no soporta transparencia de ventana: " + ex.getMessage());
		}
	}

	private static class IconInfo {
		final int width;
		final int height;
		final String base64;

		IconInfo(int width, int height, String base64) {
			this.width = width;
			this.height = height;
			this.base64 = base64;
		}
	}

	private static class FileTabInfo {
		final String title;
		final String tooltip;
		final Component component;
		final ImageIcon icon;

		FileTabInfo(String title, String tooltip, Component component, ImageIcon icon) {
			this.title = title;
			this.tooltip = tooltip;
			this.component = component;
			this.icon = icon;
		}
	}

	private static class HubCandidate {
		final String userId;
		final String name;
		final String uri;

		HubCandidate(String userId, String name, String uri) {
			this.userId = userId;
			this.name = name != null && !name.isBlank() ? name : userId;
			this.uri = uri;
		}
	}

	private static class ChatMessage {
		String id;
		String senderName;
		String text;
		String replyToName;
		String replyToText;

		String toPayload() {
			return "QCHAT1|" + safe(id) + "|" + enc(replyToName) + "|" + enc(replyToText) + "|" + enc(text);
		}

		String toPinnedPayload() {
			return "QPIN1|" + safe(id) + "|" + enc(senderName) + "|" + enc(text);
		}

		static ChatMessage fromPayload(String payload, User sender) {
			ChatMessage message = new ChatMessage();
			message.senderName = sender != null && sender.getName() != null ? sender.getName() : "Usuario";
			if (payload != null && payload.startsWith("QCHAT1|")) {
				String[] parts = payload.split("\\|", 5);
				if (parts.length == 5) {
					message.id = safe(parts[1]);
					message.replyToName = dec(parts[2]);
					message.replyToText = dec(parts[3]);
					message.text = dec(parts[4]);
					return message;
				}
			}
			message.id = UUIDUtils.generate();
			message.text = payload;
			return message;
		}

		static ChatMessage fromPinnedPayload(String payload, User sender) {
			if (payload == null) return null;
			if (payload.startsWith("QPIN1|")) {
				String[] parts = payload.split("\\|", 4);
				if (parts.length == 4) {
					ChatMessage message = new ChatMessage();
					message.id = safe(parts[1]);
					message.senderName = dec(parts[2]);
					message.text = dec(parts[3]);
					return message;
				}
			}
			return fromPayload(payload, sender);
		}

		private static String enc(String value) {
			if (value == null) return "";
			return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
		}

		private static String dec(String value) {
			if (value == null || value.isEmpty()) return null;
			try {
				return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
			} catch (Exception e) {
				return value;
			}
		}

		private static String safe(String value) {
			return value == null ? "" : value;
		}
	}

	private void loadRemoteUserTab(String username, User user) {
		TabListFile tlf = findTableByUserId(user.getId());
		if (tlf != null) {
			int idx = searchTabById(user.getId());
			ImageIcon icon = new javax.swing.ImageIcon(getClass().getResource("/status-ok.png"));
			view.getjTabbedPane().setIconAt(idx, icon);
			view.getjTabbedPane().setToolTipTextAt(idx,
					"Ultima vez conectado " + new Date() + " (Doble click para cerrar)");
			tlf.enabled();
			int idxUser = remoteUsers.indexOf(user);
			if (idxUser >= 0) {
				remoteUsers.get(idxUser).setOnline(true);
			}
			refreshTables();
		} else {
			loadUserTab(username, user, user.getId(), "/status-ok.png", 2);
		}
	}

	private void loadLocalUserTab(String username, User user) {
		loadUserTab(username, this.user, "Tus archivos locales", "/home.png", 1);
	}

	private void loadUserTab(String username, User tabUser, String tooltip, String iconpath, int iconidx) {
		TabListFile jPanel2 = new TabListFile(wk, tabUser, this, iconidx);

		jPanel2.setName(tabUser.getId());
		ImageIcon icon = new javax.swing.ImageIcon(getClass().getResource(iconpath));
		registerFileTab(username, tooltip, jPanel2, icon);
		if (enabledComplementos.contains("Archivos")) {
			int pos = 0;
			for (FileTabInfo info : fileTabs) {
				if (findTabByTitle(info.title) >= 0) pos++;
			}
			view.getjTabbedPane().insertTab(username, icon, jPanel2, tooltip, pos);
		}
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
			if (idx != -1) {
				view.getjTabbedPane().removeTabAt(idx);
			}
		} while (idx != -1);
		chatArea = null;
		chatInput = null;
		transferPanel = null;
		transferBars.clear();
		transferTargets.clear();
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

	private void sendFileDirect(Event request) {
		sendFileDirect(request, null);
	}

	private void sendFileDirect(Event request, WebSocket directConn) {
		String localFolder = view.getjTextField4().getText();
		new Thread(() -> {
			QFile requestedFile = request.getFile();
			if (requestedFile == null || request.getUser() == null) {
				log.err("Pedido de archivo invalido");
				return;
			}
			String transferId = requestedFile.getTransferId() != null ? requestedFile.getTransferId() : UUIDUtils.generate();
			String targetId = request.getUser().getId();
			String filePath = requestedFile.getRelativePath() != null && !requestedFile.getRelativePath().isEmpty()
					? requestedFile.getRelativePath() : requestedFile.getName();
			String localPath = localFolder + File.separator + filePath;
			File file = new File(localPath);

			if (!file.exists() || !file.isFile()) {
				sendTransferEvent(directConn, targetId, new Event("Error al transferir archivo",
						"No se encontro el archivo local: " + requestedFile.getName()));
				return;
			}

			long totalParts = Math.max(1, (file.length() + FILE_CHUNK_SIZE - 1) / FILE_CHUNK_SIZE);
			log.info("Enviando archivo '" + requestedFile.getName() + "' en " + totalParts + " partes");
			updateTransferProgress(transferId, "Enviando " + requestedFile.getName(), 0, (int) totalParts);

			try (FileInputStream fis = new FileInputStream(file)) {
				byte[] buffer = new byte[FILE_CHUNK_SIZE];
				int read;
				int part = 0;
				while ((read = fis.read(buffer)) != -1) {
					QFile qfile = new QFile();
					qfile.setName(requestedFile.getName());
					qfile.setSize(file.length());
					qfile.setDate(file.lastModified());
					qfile.setOperation(requestedFile.getOperation());
					qfile.setRelativePath(requestedFile.getRelativePath());
					qfile.setTransferId(transferId);
					qfile.setTotalParts((int) totalParts);
					qfile.setCurrentPart(part);
					qfile.setContent(Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, read)));

					sendTransferEvent(directConn, targetId, new Event("Parte de archivo", user, qfile));
					updateTransferProgress(transferId, "Enviando " + requestedFile.getName(), part + 1, (int) totalParts);
					part++;
				}
				log.info("Archivo '" + requestedFile.getName() + "' enviado");
			} catch (Exception ex) {
				log.err("Error al enviar archivo: " + ex.getMessage());
				sendTransferEvent(directConn, targetId, new Event("Error al transferir archivo",
						"Error al enviar archivo: " + ex.getMessage()));
			}
		}, "file-send").start();
	}

	private void sendTransferEvent(WebSocket directConn, String targetId, Event event) {
		try {
			if (directConn != null) {
				directConn.send(EventUtils.toJsonBase64(event));
			} else if (wsClient != null) {
				event.setName("__to:" + targetId + ":" + event.getName());
				wsClient.sendEvent(event);
			}
		} catch (Exception e) {
			log.err("Error al enviar evento de transferencia: " + e.getMessage());
		}
	}

	private void receiveFilePart(QFile qfile) {
		try {
			String transferId = qfile.getTransferId() != null ? qfile.getTransferId() : qfile.getName();
			String localPath = transferTargets.get(transferId);
			if (localPath == null) {
				String filePath = qfile.getRelativePath() != null && !qfile.getRelativePath().isEmpty()
						? qfile.getRelativePath() : qfile.getName();
				localPath = uniqueFilePath(view.getjTextField4().getText() + File.separator + filePath);
				transferTargets.put(transferId, localPath);
			}

			File file = new File(localPath + ".part");
			File parent = file.getParentFile();
			if (parent != null) {
				parent.mkdirs();
			}

			byte[] bytes = Base64.getDecoder().decode(qfile.getContent());
			try (FileOutputStream fos = new FileOutputStream(file, qfile.getCurrentPart() > 0)) {
				fos.write(bytes);
			}

			int completed = qfile.getCurrentPart() + 1;
			updateTransferProgress(transferId, "Descargando " + qfile.getName(), completed, qfile.getTotalParts());
			if (completed >= qfile.getTotalParts()) {
				File finalFile = new File(localPath);
				if (!file.renameTo(finalFile)) {
					throw new Exception("No se pudo finalizar archivo temporal: " + file.getAbsolutePath());
				}
				log.info("Archivo '" + qfile.getName() + "' recibido");
				String chatLinkId = chatTransferLinks.remove(transferId);
				if (chatLinkId != null) {
					chatFileLinks.put(chatLinkId, finalFile.getAbsolutePath());
				}
				if (QFile.OPERATION_OPEN.equals(qfile.getOperation())) {
					exec.open(localPath);
				} else {
					refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
				}
				transferTargets.remove(transferId);
			}
		} catch (Exception ex) {
			log.err("Error al recibir archivo: " + ex.getMessage());
		}
	}

	private void updateTransferProgress(String transferId, String label, int current, int total) {
		if (transferId == null) {
			return;
		}
		javax.swing.SwingUtilities.invokeLater(() -> {
			transferPanel.setVisible(true);
			JProgressBar bar = transferBars.get(transferId);
			if (bar == null) {
				bar = new JProgressBar(0, Math.max(total, 1));
				bar.setStringPainted(true);
				transferPanel.add(bar);
				transferBars.put(transferId, bar);
			} else {
				bar.setMaximum(Math.max(total, 1));
			}
			bar.setValue(Math.min(current, Math.max(total, 1)));
			int percent = total <= 0 ? 0 : (int) ((current * 100.0) / total);
			bar.setString(label + " - " + Math.min(percent, 100) + "%");
			transferPanel.revalidate();
			transferPanel.repaint();
			if (current >= total) {
				final JProgressBar completedBar = bar;
				Timer timer = new Timer(1800, e -> {
					transferBars.remove(transferId);
					transferPanel.remove(completedBar);
					transferPanel.setVisible(transferPanel.getComponentCount() > 0);
					transferPanel.revalidate();
					transferPanel.repaint();
				});
				timer.setRepeats(false);
				timer.start();
			}
		});
	}

	private String uniqueFilePath(String preferredPath) {
		File file = new File(preferredPath);
		if (!file.exists() && !new File(preferredPath + ".part").exists()) {
			return preferredPath;
		}

		String name = file.getName();
		String parent = file.getParent();
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		String ext = dot > 0 ? name.substring(dot) : "";
		for (int i = 1; i < 1000; i++) {
			String candidate = (parent == null ? "" : parent + File.separator) + base + " (" + i + ")" + ext;
			if (!new File(candidate).exists() && !new File(candidate + ".part").exists()) {
				return candidate;
			}
		}
		return preferredPath;
	}

	private void saveSessionHistory(String reason) {
		if (historySaved || wk == null) return;
		historySaved = true;
		try {
			long createdAt = sessionCreatedAt > 0 ? sessionCreatedAt : System.currentTimeMillis();
			Date createdDate = new Date(createdAt);
			String workspaceName = wk.getName() != null ? decodeValue(wk.getName()) : defaultWorkspaceName();
			File sessionDir = buildSessionHistoryDir();
			if (sessionDir == null) return;
			sessionDir.mkdirs();

			if (chatArea != null) {
				Files.writeString(new File(sessionDir, "chat.txt").toPath(), chatArea.getText(), StandardCharsets.UTF_8);
			}
			if (notesPane != null) {
				try (OutputStream out = new FileOutputStream(new File(sessionDir, "notas.rtf"))) {
					new RTFEditorKit().write(out, notesPane.getDocument(), 0, notesPane.getDocument().getLength());
				}
				Files.writeString(new File(sessionDir, "notas.txt").toPath(),
						notesPane.getDocument().getText(0, notesPane.getDocument().getLength()), StandardCharsets.UTF_8);
			}
			if (whiteboardCanvas != null) {
				ImageIO.write(whiteboardCanvas.toImage(), "png", new File(sessionDir, "pizarra.png"));
			}
			saveLogHistory(new File(sessionDir, "logs.log"));
			Files.writeString(new File(sessionDir, "sesion.txt").toPath(),
					"Workspace: " + workspaceName + "\n"
							+ "Creada: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(createdDate) + "\n"
							+ "Guardada: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n"
							+ "Motivo: " + reason + "\n",
					StandardCharsets.UTF_8);
			log.info("Historial de sesion guardado en " + sessionDir.getAbsolutePath());
		} catch (Exception e) {
			log.err("No se pudo guardar el historial de sesion: " + e.getMessage());
		}
	}

	private File getApplicationDirectory() {
		return new File(System.getProperty("user.home", "."), "qfolder").getAbsoluteFile();
	}

	private String safeFileName(String value) {
		String normalized = Normalizer.normalize(value == null ? "sesion" : value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		String safe = normalized.replaceAll("[^a-zA-Z0-9._ -]", "-").trim().replaceAll("\\s+", "-");
		return safe.isEmpty() ? "sesion" : safe;
	}

	private void saveLogHistory(File file) throws Exception {
		StringBuilder text = new StringBuilder();
		javax.swing.ListModel<?> model = view.getjList2().getModel();
		for (int i = 0; i < model.getSize(); i++) {
			text.append(String.valueOf(model.getElementAt(i))).append(System.lineSeparator());
		}
		Files.writeString(file.toPath(), text.toString(), StandardCharsets.UTF_8);
	}

	private class WhiteboardCanvas extends JPanel {
		private final List<String> operations = new ArrayList<>();
		private final List<String> currentStroke = new ArrayList<>();
		private String tool = "Seleccionar";
		private Color drawColor = Color.BLACK;
		private int fontSize = 18;
		private int strokeWidth = 1;
		private int lastX;
		private int lastY;
		private int startX;
		private int startY;
		private int selectedIndex = -1;
		private Point dragOffset;
		private boolean resizing;
		private String previewShape;
		private JTextArea activeTextEditor;

		WhiteboardCanvas() {
			setBackground(Color.WHITE);
			setLayout(null);
			setFocusable(true);
			getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke("DELETE"), "deleteSelected");
			getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke("BACK_SPACE"), "deleteSelected");
			getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke("control V"), "pasteImage");
			getActionMap().put("deleteSelected", new javax.swing.AbstractAction() {
				@Override public void actionPerformed(java.awt.event.ActionEvent e) { deleteSelected(); }
			});
			getActionMap().put("pasteImage", new javax.swing.AbstractAction() {
				@Override public void actionPerformed(java.awt.event.ActionEvent e) { pasteImageFromClipboard(30, 30); }
			});
			setTransferHandler(new TransferHandler() {
				@Override
				public boolean canImport(TransferSupport support) {
					return support.isDataFlavorSupported(DataFlavor.imageFlavor)
							|| support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
				}

				@Override
				@SuppressWarnings("unchecked")
				public boolean importData(TransferSupport support) {
					try {
						Transferable t = support.getTransferable();
						Point p = support.getDropLocation().getDropPoint();
						if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
							addImage((Image) t.getTransferData(DataFlavor.imageFlavor), p.x, p.y);
							return true;
						}
						if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
							List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
							if (!files.isEmpty()) {
								addImage(ImageIO.read(files.get(0)), p.x, p.y);
								return true;
							}
						}
					} catch (Exception e) {
						log.err("No se pudo insertar imagen: " + e.getMessage());
					}
					return false;
				}
			});
			MouseAdapter mouse = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					requestFocusInWindow();
					if (e.isPopupTrigger()) {
						pasteImageFromClipboard(e.getX(), e.getY());
						return;
					}
					if ("Texto".equals(tool)) {
						startTextEditor(e.getX(), e.getY());
						return;
					}
					if ("Imagen".equals(tool)) {
						chooseImage(e.getX(), e.getY());
						return;
					}
					selectedIndex = findElementAt(e.getX(), e.getY());
					if ("Seleccionar".equals(tool) && selectedIndex >= 0) {
						if (e.getClickCount() == 2 && operations.get(selectedIndex).startsWith("T|")) {
							editSelectedText();
							return;
						}
						Rectangle r = boundsOf(operations.get(selectedIndex));
						resizing = r != null && nearResizeCorner(r, e.getX(), e.getY());
						dragOffset = r == null ? null : new Point(e.getX() - r.x, e.getY() - r.y);
						loadSelectedStyle();
						repaint();
					} else {
						selectedIndex = -1;
						repaint();
						currentStroke.clear();
						startX = e.getX();
						startY = e.getY();
					}
					lastX = e.getX();
					lastY = e.getY();
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					if ("Seleccionar".equals(tool) && selectedIndex >= 0) {
						moveOrResizeSelected(e.getX(), e.getY());
						repaint();
						return;
					}
					if ("Lápiz".equals(tool)) {
						currentStroke.add("L|" + lastX + "," + lastY + "," + e.getX() + "," + e.getY() + "|" + colorToHex(drawColor) + "|" + strokeWidth);
					} else if (isShapeTool()) {
						previewShape = shapeOp(tool, startX, startY, e.getX(), e.getY());
					}
					lastX = e.getX();
					lastY = e.getY();
					repaint();
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					if (e.isPopupTrigger()) {
						pasteImageFromClipboard(e.getX(), e.getY());
						return;
					}
					if (selectedIndex >= 0) {
						dragOffset = null;
						resizing = false;
						repaint();
						broadcastWhiteboard();
					} else if ("Lápiz".equals(tool) && !currentStroke.isEmpty()) {
						operations.add(strokeOp(currentStroke));
						selectedIndex = operations.size() - 1;
						tool = "Seleccionar";
						currentStroke.clear();
						repaint();
						broadcastWhiteboard();
					} else if (isShapeTool() && previewShape != null) {
						operations.add(previewShape);
						selectedIndex = operations.size() - 1;
						tool = "Seleccionar";
						previewShape = null;
						repaint();
						broadcastWhiteboard();
					}
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		void setTool(String tool) { this.tool = tool; }
		Color getDrawColor() { return drawColor; }
		void setDrawColor(Color color) {
			this.drawColor = color;
			updateColorButton(color);
			applyColorToSelected(color);
		}
		void setFontSize(int fontSize) { this.fontSize = fontSize; }

		void addText(String text) {
			String encoded = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
			operations.add("T|20,20,160,40|" + fontSize + "|" + colorToHex(drawColor) + "|" + encoded);
			tool = "Seleccionar";
			repaint();
		}

		void addImage(Image image, int x, int y) {
			addImage(image, x, y, false, null);
		}

		void addImage(Image image, int x, int y, boolean showProgressImmediately, String transferId) {
			if (image == null) return;
			String progressId = transferId != null ? transferId : UUIDUtils.generate();
			java.util.concurrent.atomic.AtomicBoolean progressVisible = new java.util.concurrent.atomic.AtomicBoolean(false);
			Timer progressDelay = new Timer(700, e -> {
				progressVisible.set(true);
				updateTransferProgress(progressId, "Preparando imagen para pizarra", 0, 5);
			});
			progressDelay.setRepeats(false);
			if (showProgressImmediately) {
				progressVisible.set(true);
				updateTransferProgress(progressId, "Leyendo imagen del portapapeles", 1, 5);
			} else {
				progressDelay.start();
			}
			new Thread(() -> {
				try {
					if (progressVisible.get()) {
						updateTransferProgress(progressId, "Convirtiendo imagen", 2, 5);
					}
					BufferedImage bi = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
					Graphics2D g = bi.createGraphics();
					g.drawImage(image, 0, 0, null);
					g.dispose();
					if (progressVisible.get()) {
						updateTransferProgress(progressId, "Comprimiendo imagen", 3, 5);
					}
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					ImageIO.write(bi, "png", out);
					String data = Base64.getEncoder().encodeToString(out.toByteArray());
					if (progressVisible.get()) {
						updateTransferProgress(progressId, "Sincronizando imagen de pizarra", 4, 5);
					}
					javax.swing.SwingUtilities.invokeLater(() -> {
						if (!progressVisible.get()) {
							progressDelay.stop();
						}
						operations.add("I|" + x + "," + y + "," + Math.min(220, bi.getWidth()) + "," + Math.min(160, bi.getHeight()) + "|" + data);
						selectedIndex = operations.size() - 1;
						tool = "Seleccionar";
						repaint();
						broadcastWhiteboard();
						if (progressVisible.get()) {
							updateTransferProgress(progressId, "Imagen de pizarra sincronizada", 5, 5);
						}
					});
				} catch (Exception e) {
					progressDelay.stop();
					log.err("No se pudo insertar imagen: " + e.getMessage());
					if (progressVisible.get()) {
						updateTransferProgress(progressId, "Error preparando imagen", 5, 5);
					}
				}
			}, "whiteboard-image-encode").start();
		}

		void chooseImage(int x, int y) {
			JFileChooser chooser = new JFileChooser();
			if (chooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
				try {
					addImage(ImageIO.read(chooser.getSelectedFile()), x, y);
				} catch (Exception e) {
					log.err("No se pudo insertar imagen: " + e.getMessage());
				}
			}
		}

		void startTextEditor(int x, int y) {
			if (activeTextEditor != null) commitTextEditor();
			activeTextEditor = new JTextArea();
			activeTextEditor.setOpaque(false);
			activeTextEditor.setForeground(drawColor);
			activeTextEditor.setFont(activeTextEditor.getFont().deriveFont((float) fontSize));
			activeTextEditor.setBounds(x, y, 180, 50);
			activeTextEditor.setLineWrap(true);
			activeTextEditor.setWrapStyleWord(true);
			activeTextEditor.getDocument().addDocumentListener(new DocumentListener() {
				public void insertUpdate(DocumentEvent e) { resizeActiveTextEditorHeight(); }
				public void removeUpdate(DocumentEvent e) { resizeActiveTextEditorHeight(); }
				public void changedUpdate(DocumentEvent e) { resizeActiveTextEditorHeight(); }
			});
			activeTextEditor.addFocusListener(new java.awt.event.FocusAdapter() {
				@Override public void focusLost(java.awt.event.FocusEvent e) { commitTextEditor(); }
			});
			add(activeTextEditor);
			activeTextEditor.requestFocusInWindow();
			repaint();
		}

		private void resizeActiveTextEditorHeight() {
			if (activeTextEditor == null) return;
			javax.swing.SwingUtilities.invokeLater(() -> {
				if (activeTextEditor == null) return;
				int width = activeTextEditor.getWidth();
				activeTextEditor.setSize(width, Short.MAX_VALUE);
				java.awt.Dimension preferred = activeTextEditor.getPreferredSize();
				activeTextEditor.setSize(width, Math.max(50, preferred.height));
				activeTextEditor.revalidate();
				repaint();
			});
		}

		void commitTextEditor() {
			if (activeTextEditor == null) return;
			String text = activeTextEditor.getText();
			int editorWidth = activeTextEditor.getWidth();
			int editorX = activeTextEditor.getX();
			int editorY = activeTextEditor.getY();
			activeTextEditor.setSize(editorWidth, Short.MAX_VALUE);
			java.awt.Dimension preferred = activeTextEditor.getPreferredSize();
			activeTextEditor.setBounds(editorX, editorY, editorWidth, Math.max(50, preferred.height));
			Rectangle r = activeTextEditor.getBounds();
			remove(activeTextEditor);
			activeTextEditor = null;
			if (text != null && !text.trim().isEmpty()) {
				String encoded = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
				operations.add("T|" + r.x + "," + r.y + "," + r.width + "," + r.height + "|" + fontSize + "|" + colorToHex(drawColor) + "|" + encoded);
				selectedIndex = operations.size() - 1;
				tool = "Seleccionar";
				broadcastWhiteboard();
			}
			revalidate();
			repaint();
		}

		void pasteImageFromClipboard(int x, int y) {
			String transferId = UUIDUtils.generate();
			updateTransferProgress(transferId, "Leyendo portapapeles", 0, 5);
			try {
				Transferable t = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
				if (t != null && t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
					addImage((Image) t.getTransferData(DataFlavor.imageFlavor), x, y, true, transferId);
				} else {
					updateTransferProgress(transferId, "No hay imagen en portapapeles", 5, 5);
				}
			} catch (Exception e) {
				log.err("No se pudo pegar imagen: " + e.getMessage());
				updateTransferProgress(transferId, "Error leyendo portapapeles", 5, 5);
			}
		}

		void clear() {
			operations.clear();
			selectedIndex = -1;
			repaint();
		}

		void deleteSelected() {
			if (selectedIndex >= 0 && selectedIndex < operations.size()) {
				operations.remove(selectedIndex);
				selectedIndex = -1;
				repaint();
				broadcastWhiteboard();
			}
		}

		String serialize() {
			return String.join("\n", operations);
		}

		BufferedImage toImage() {
			if (activeTextEditor != null) commitTextEditor();
			BufferedImage img = new BufferedImage(Math.max(1, getWidth()), Math.max(1, getHeight()), BufferedImage.TYPE_INT_RGB);
			Graphics2D g2 = img.createGraphics();
			g2.setColor(Color.WHITE);
			g2.fillRect(0, 0, img.getWidth(), img.getHeight());
			for (String op : operations) {
				if (!op.startsWith("T|")) drawOperation(g2, op);
			}
			for (String op : operations) {
				if (op.startsWith("T|")) drawOperation(g2, op);
			}
			g2.dispose();
			return img;
		}

		void applyState(String state) {
			operations.clear();
			if (state != null && !state.isEmpty()) {
				operations.addAll(Arrays.asList(state.split("\n")));
			}
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g;
			g2.setColor(Color.BLACK);
			List<String> all = new ArrayList<>(operations);
			all.addAll(currentStroke);
			if (previewShape != null) all.add(previewShape);
			for (int i = 0; i < all.size(); i++) {
				String op = all.get(i);
				if (!op.startsWith("T|")) drawOperation(g2, op);
			}
			for (int i = 0; i < all.size(); i++) {
				String op = all.get(i);
				if (op.startsWith("T|")) drawOperation(g2, op);
			}
			if (selectedIndex >= 0 && selectedIndex < operations.size()) {
				Rectangle r = boundsOf(operations.get(selectedIndex));
				if (r != null) drawHandles(g2, r);
			}
		}

		private void drawOperation(Graphics2D g2, String op) {
				try {
					if (op.startsWith("L|")) {
						String[] parts = op.split("\\|");
						String[] p = parts[1].split(",");
						g2.setColor(parts.length > 2 ? Color.decode(parts[2]) : Color.BLACK);
						g2.setStroke(new BasicStroke(parts.length > 3 ? Integer.parseInt(parts[3]) : 1));
						g2.drawLine(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
								Integer.parseInt(p[2]), Integer.parseInt(p[3]));
						g2.setStroke(new BasicStroke(1));
					} else if (op.startsWith("P|")) {
						String[] parts = op.split("\\|", 5);
						String[] bounds = parts[1].split(",");
						int x = Integer.parseInt(bounds[0]), y = Integer.parseInt(bounds[1]);
						double scaleX = Integer.parseInt(bounds[2]) / 1000.0;
						double scaleY = Integer.parseInt(bounds[3]) / 1000.0;
						g2.setColor(Color.decode(parts[2]));
						g2.setStroke(new BasicStroke(Integer.parseInt(parts[3]), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
						String[] lines = new String(Base64.getDecoder().decode(parts[4]), StandardCharsets.UTF_8).split(";");
						for (String line : lines) {
							if (line.isBlank()) continue;
							String[] p = line.split(",");
							g2.drawLine(x + (int) Math.round(Integer.parseInt(p[0]) * scaleX),
									y + (int) Math.round(Integer.parseInt(p[1]) * scaleY),
									x + (int) Math.round(Integer.parseInt(p[2]) * scaleX),
									y + (int) Math.round(Integer.parseInt(p[3]) * scaleY));
						}
						g2.setStroke(new BasicStroke(1));
					} else if (op.startsWith("T|")) {
						String[] p = op.split("\\|", 5);
						String[] xy = p[1].split(",");
						int x = Integer.parseInt(xy[0]);
						int y = Integer.parseInt(xy[1]);
						int w = Integer.parseInt(xy[2]);
						int h = Integer.parseInt(xy[3]);
						int size = Integer.parseInt(p[2]);
						String text = new String(Base64.getDecoder().decode(p[4]), StandardCharsets.UTF_8);
						JTextArea renderer = new JTextArea(text);
						renderer.setOpaque(false);
						renderer.setForeground(Color.decode(p[3]));
						renderer.setFont(g2.getFont().deriveFont((float) size));
						renderer.setLineWrap(true);
						renderer.setWrapStyleWord(true);
						renderer.setSize(w, Math.max(h, 1));
						Graphics2D copy = (Graphics2D) g2.create(x, y, w, h);
						renderer.paint(copy);
						copy.dispose();
					} else if (op.startsWith("I|")) {
						String[] p = op.split("\\|", 3);
						String[] xy = p[1].split(",");
						BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(p[2])));
						g2.drawImage(img, Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), Integer.parseInt(xy[2]), Integer.parseInt(xy[3]), null);
					} else if (op.startsWith("S|")) {
						String[] p = op.split("\\|");
						String[] xy = p[2].split(",");
						int x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]), w = Integer.parseInt(xy[2]), h = Integer.parseInt(xy[3]);
						g2.setColor(Color.decode(p[3]));
						g2.setStroke(new BasicStroke(p.length > 4 ? Integer.parseInt(p[4]) : 1));
						if ("Círculo".equals(p[1])) g2.drawOval(x, y, w, w);
						else if ("Triángulo".equals(p[1])) g2.drawPolygon(new int[]{x + w / 2, x, x + w}, new int[]{y, y + h, y + h}, 3);
						else if ("Flecha".equals(p[1])) {
							g2.drawLine(x, y + h, x + w, y);
							g2.drawLine(x + w, y, x + w - 12, y + 4);
							g2.drawLine(x + w, y, x + w - 4, y + 12);
						}
						else g2.drawRect(x, y, w, h);
						g2.setStroke(new BasicStroke(1));
					}
				} catch (Exception ignored) {
				}
		}

		private void drawHandles(Graphics2D g2, Rectangle r) {
			g2.setColor(Color.WHITE);
			g2.fillRect(r.x - 2, r.y - 2, r.width + 4, 2);
			g2.fillRect(r.x - 2, r.y + r.height, r.width + 4, 2);
			g2.fillRect(r.x - 2, r.y - 2, 2, r.height + 4);
			g2.fillRect(r.x + r.width, r.y - 2, 2, r.height + 4);
			g2.setColor(Color.BLACK);
			g2.drawRect(r.x, r.y, r.width, r.height);
			g2.setColor(Color.WHITE);
			g2.fillRect(r.x - 5, r.y - 5, 10, 10);
			g2.fillRect(r.x + r.width - 5, r.y - 5, 10, 10);
			g2.fillRect(r.x - 5, r.y + r.height - 5, 10, 10);
			g2.fillRect(r.x + r.width - 5, r.y + r.height - 5, 10, 10);
			g2.setColor(Color.BLACK);
			g2.drawRect(r.x - 5, r.y - 5, 10, 10);
			g2.drawRect(r.x + r.width - 5, r.y - 5, 10, 10);
			g2.drawRect(r.x - 5, r.y + r.height - 5, 10, 10);
			g2.drawRect(r.x + r.width - 5, r.y + r.height - 5, 10, 10);
		}

		private boolean isShapeTool() {
			return "Flecha".equals(tool) || "Círculo".equals(tool) || "Cuadrado".equals(tool) || "Rectángulo".equals(tool) || "Triángulo".equals(tool);
		}

		private String shapeOp(String shape, int x1, int y1, int x2, int y2) {
			int x = Math.min(x1, x2), y = Math.min(y1, y2);
			int w = Math.abs(x2 - x1), h = Math.abs(y2 - y1);
			if ("Cuadrado".equals(shape) || "Círculo".equals(shape)) h = w = Math.max(w, h);
			return "S|" + shape + "|" + x + "," + y + "," + w + "," + h + "|" + colorToHex(drawColor) + "|" + strokeWidth;
		}

		private String strokeOp(List<String> stroke) {
			int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
			List<int[]> lines = new ArrayList<>();
			for (String op : stroke) {
				String[] p = op.split("\\|")[1].split(",");
				int x1 = Integer.parseInt(p[0]), y1 = Integer.parseInt(p[1]);
				int x2 = Integer.parseInt(p[2]), y2 = Integer.parseInt(p[3]);
				lines.add(new int[]{x1, y1, x2, y2});
				minX = Math.min(minX, Math.min(x1, x2));
				minY = Math.min(minY, Math.min(y1, y2));
				maxX = Math.max(maxX, Math.max(x1, x2));
				maxY = Math.max(maxY, Math.max(y1, y2));
			}
			int width = Math.max(1, maxX - minX);
			int height = Math.max(1, maxY - minY);
			StringBuilder data = new StringBuilder();
			for (int[] line : lines) {
				int x1 = (int) Math.round(((line[0] - minX) * 1000.0) / width);
				int y1 = (int) Math.round(((line[1] - minY) * 1000.0) / height);
				int x2 = (int) Math.round(((line[2] - minX) * 1000.0) / width);
				int y2 = (int) Math.round(((line[3] - minY) * 1000.0) / height);
				data.append(x1).append(',').append(y1).append(',').append(x2).append(',').append(y2).append(';');
			}
			String encoded = Base64.getEncoder().encodeToString(data.toString().getBytes(StandardCharsets.UTF_8));
			return "P|" + minX + "," + minY + "," + width + "," + height + "|" + colorToHex(drawColor) + "|" + strokeWidth + "|" + encoded;
		}

		private boolean nearResizeCorner(Rectangle r, int x, int y) {
			int tol = 40;
			return new Rectangle(r.x - tol, r.y - tol, tol * 2, tol * 2).contains(x, y)
					|| new Rectangle(r.x + r.width - tol, r.y - tol, tol * 2, tol * 2).contains(x, y)
					|| new Rectangle(r.x - tol, r.y + r.height - tol, tol * 2, tol * 2).contains(x, y)
					|| new Rectangle(r.x + r.width - tol, r.y + r.height - tol, tol * 2, tol * 2).contains(x, y);
		}

		private String colorToHex(Color color) {
			return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
		}

		private int findElementAt(int x, int y) {
			for (int i = operations.size() - 1; i >= 0; i--) {
				Rectangle r = boundsOf(operations.get(i));
				if (r != null && r.contains(x, y)) return i;
			}
			return -1;
		}

		private Rectangle boundsOf(String op) {
			try {
				if (op.startsWith("T|") || op.startsWith("I|") || op.startsWith("P|")) {
					String[] p = op.split("\\|", 3);
					String[] xy = p[1].split(",");
					return new Rectangle(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), Integer.parseInt(xy[2]), Integer.parseInt(xy[3]));
				} else if (op.startsWith("S|")) {
					String[] p = op.split("\\|", 4);
					String[] xy = p[2].split(",");
					return new Rectangle(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), Integer.parseInt(xy[2]), Integer.parseInt(xy[3]));
				}
			} catch (Exception ignored) {}
			return null;
		}

		private void moveOrResizeSelected(int x, int y) {
			String op = operations.get(selectedIndex);
			String[] parts = op.split("\\|");
			int coordIdx = op.startsWith("S|") ? 2 : 1;
			String[] xy = parts[coordIdx].split(",");
			int ox = Integer.parseInt(xy[0]);
			int oy = Integer.parseInt(xy[1]);
			int w = Integer.parseInt(xy[2]);
			int h = Integer.parseInt(xy[3]);
			if (resizing) {
				w = Math.max(30, x - ox);
				h = Math.max(20, y - oy);
			} else if (dragOffset != null) {
				ox = x - dragOffset.x;
				oy = y - dragOffset.y;
			}
			String coords = ox + "," + oy + "," + w + "," + h;
			if (op.startsWith("S|")) operations.set(selectedIndex, parts[0] + "|" + parts[1] + "|" + coords + "|" + parts[3] + (parts.length > 4 ? "|" + parts[4] : ""));
			else if (op.startsWith("I|")) operations.set(selectedIndex, parts[0] + "|" + coords + "|" + parts[2]);
			else if (op.startsWith("P|")) operations.set(selectedIndex, parts[0] + "|" + coords + "|" + parts[2] + "|" + parts[3] + "|" + parts[4]);
			else if (op.startsWith("T|")) operations.set(selectedIndex, parts[0] + "|" + coords + "|" + parts[2] + "|" + parts[3] + "|" + parts[4]);
		}

		void changeSelectedSizeOrStroke(int delta) {
			if (selectedIndex >= 0 && operations.get(selectedIndex).startsWith("T|")) {
				String[] p = operations.get(selectedIndex).split("\\|", 5);
				int size = Math.max(8, Math.min(72, Integer.parseInt(p[2]) + delta));
				fontSize = size;
				operations.set(selectedIndex, p[0] + "|" + p[1] + "|" + size + "|" + p[3] + "|" + p[4]);
				repaint();
				broadcastWhiteboard();
			} else if (selectedIndex >= 0 && (operations.get(selectedIndex).startsWith("S|") || operations.get(selectedIndex).startsWith("P|"))) {
				String[] p = operations.get(selectedIndex).split("\\|");
				int width = Math.max(1, Math.min(20, (p.length > 4 ? Integer.parseInt(p[4]) : 1) + delta));
				strokeWidth = width;
				if (operations.get(selectedIndex).startsWith("S|")) {
					operations.set(selectedIndex, p[0] + "|" + p[1] + "|" + p[2] + "|" + p[3] + "|" + width);
				} else {
					operations.set(selectedIndex, p[0] + "|" + p[1] + "|" + p[2] + "|" + width + "|" + p[4]);
				}
				repaint();
				broadcastWhiteboard();
			} else {
				strokeWidth = Math.max(1, Math.min(20, strokeWidth + delta));
			}
		}

		private void applyColorToSelected(Color color) {
			if (selectedIndex < 0) return;
			String op = operations.get(selectedIndex);
			String hex = colorToHex(color);
			String[] p = op.split("\\|", 5);
			if (op.startsWith("T|") && p.length == 5) operations.set(selectedIndex, p[0] + "|" + p[1] + "|" + p[2] + "|" + hex + "|" + p[4]);
			else if (op.startsWith("S|") && p.length >= 4) operations.set(selectedIndex, p[0] + "|" + p[1] + "|" + p[2] + "|" + hex + (p.length > 4 ? "|" + p[4] : "|" + strokeWidth));
			else if (op.startsWith("P|") && p.length == 5) operations.set(selectedIndex, p[0] + "|" + p[1] + "|" + hex + "|" + p[3] + "|" + p[4]);
			repaint();
			broadcastWhiteboard();
		}

		private void loadSelectedStyle() {
			if (selectedIndex < 0) return;
			String op = operations.get(selectedIndex);
			try {
				if (op.startsWith("T|")) {
					String[] p = op.split("\\|", 5);
					fontSize = Integer.parseInt(p[2]);
					drawColor = Color.decode(p[3]);
				} else if (op.startsWith("S|")) {
					String[] p = op.split("\\|");
					drawColor = Color.decode(p[3]);
					strokeWidth = p.length > 4 ? Integer.parseInt(p[4]) : 1;
				} else if (op.startsWith("P|")) {
					String[] p = op.split("\\|", 5);
					drawColor = Color.decode(p[2]);
					strokeWidth = Integer.parseInt(p[3]);
				}
				updateColorButton(drawColor);
			} catch (Exception ignored) {}
		}

		private void editSelectedText() {
			String op = operations.get(selectedIndex);
			try {
				String[] p = op.split("\\|", 5);
				String[] xy = p[1].split(",");
				fontSize = Integer.parseInt(p[2]);
				drawColor = Color.decode(p[3]);
				String text = new String(Base64.getDecoder().decode(p[4]), StandardCharsets.UTF_8);
				operations.remove(selectedIndex);
				selectedIndex = -1;
				startTextEditor(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]));
				activeTextEditor.setBounds(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), Integer.parseInt(xy[2]), Integer.parseInt(xy[3]));
				activeTextEditor.setText(text);
			} catch (Exception ignored) {}
		}
	}

	public void downloadFile(User user2, QFile qFile) {
		if (qFile == null || qFile.getOwner() == null) return;
		log.info("Solicitando descargar el archivo '" + qFile.getName() + "'");
		qFile.setOperation(QFile.OPERATION_DOWNLOAD);
		qFile.setTransferId(UUIDUtils.generate());
		updateTransferProgress(qFile.getTransferId(), "Solicitando " + qFile.getName(), 0, 1);
		if (requestFileDirect(qFile)) {
			return;
		}
		String targetId = qFile.getOwner().getId();
		Event event = new Event("__to:" + targetId + ":Quiero descargar el archivo", user, qFile);
		sendEvent(event);
	}

	public void openFile(User user2, QFile qFile) {
		if (qFile == null) return;
		if (qFile.getOwner() != null && qFile.getOwner().equals(this.user)) {
			String baseDir = view.getjTextField4().getText();
			String filePath = qFile.getRelativePath() != null && !qFile.getRelativePath().isEmpty()
					? qFile.getRelativePath() : qFile.getName();
			try {
				exec.open(new File(baseDir, filePath).getAbsolutePath());
			} catch (Exception e) {
				log.err("Error al abrir archivo: " + e.getMessage());
			}
			return;
		}
		log.info("Solicitando abrir el archivo '" + qFile.getName() + "'");
		qFile.setOperation(QFile.OPERATION_OPEN);
		qFile.setTransferId(UUIDUtils.generate());
		updateTransferProgress(qFile.getTransferId(), "Solicitando " + qFile.getName(), 0, 1);
		if (requestFileDirect(qFile)) {
			return;
		}
		String targetId = qFile.getOwner().getId();
		Event event = new Event("__to:" + targetId + ":Quiero descargar el archivo", user, qFile);
		sendEvent(event);
	}

	private boolean requestFileDirect(QFile qFile) {
		User owner = qFile.getOwner();
		if (owner == null || owner.getPeerUrl() == null || owner.getPeerUrl().isBlank()
				|| owner.equals(user) || wk == null) {
			return false;
		}

		new Thread(() -> {
			try {
				String uri = "ws://" + owner.getPeerUrl() + "/ws?wkId="
						+ java.net.URLEncoder.encode(wk.getId(), "UTF-8")
						+ "&userId=" + java.net.URLEncoder.encode(user.getId(), "UTF-8")
						+ "&direct=true";
				WsClient directClient = new WsClient(new URI(uri), log, event -> {
					if ("Parte de archivo".equals(event.getName())) {
						receiveFilePart(event.getFile());
					} else if ("Error al transferir archivo".equals(event.getName())) {
						log.err(event.getResponse());
					}
				}, error -> log.err("Error en conexion directa: " + error), null);

				if (!directClient.connectBlocking()) {
					log.err("No se pudo conectar directo con '" + owner.getName() + "'. Uso el canal del hub.");
					String targetId = owner.getId();
					wsClient.sendEvent(new Event("__to:" + targetId + ":Quiero descargar el archivo", user, qFile));
					return;
				}

				directClient.sendEvent(new Event("Quiero descargar el archivo", user, qFile));
			} catch (Exception e) {
				log.err("No se pudo iniciar descarga directa: " + e.getMessage());
				String targetId = owner.getId();
				if (wsClient != null) {
					wsClient.sendEvent(new Event("__to:" + targetId + ":Quiero descargar el archivo", user, qFile));
				}
			}
		}, "direct-download").start();
		return true;
	}

	public Component getView() {
		return view;
	}

	public void removeFile(User user2, QFile qFile) {
		if (qFile == null) return;
		try {
			String filename = qFile.getRelativePath() != null && !qFile.getRelativePath().isEmpty()
					? qFile.getRelativePath() : qFile.getName();
			String filepath = view.getjTextField4().getText();
			String fullname = filepath + File.separator + filename;
			FileUtils.remove(Paths.get(fullname));
			Event event = new Event("Se borro un archivo");
			event.setUser(user2);
			event.setFile(qFile);
			refreshLocalFilesAndNotify(event);
		} catch (Exception e) {
			log.err("Error al intentar borrar el archivo: " + qFile.getName());
		}
	}

	public void refreshFiles(User user2) {
		refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
	}

	public String getNavigationPath(String userId) {
		return navigationPaths.getOrDefault(userId, "");
	}

	public void navigateTo(String userId, String relativePath) {
		selectedArchivosUserId = userId;
		if (archivosFilterBtn != null) {
			archivosFilterBtn.setText(getArchivosFilterLabel() + " ▼");
		}
		navigationPaths.put(userId, relativePath);
		if (userId.equals(this.user.getId())) {
			refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
		} else if (wsClient != null) {
			wsClient.sendEvent(new Event("__to:" + userId + ":Solicitar archivos de directorio", user, relativePath));
		}
	}

	public void navigateBack(String userId) {
		String current = navigationPaths.getOrDefault(userId, "");
		int sep = current.lastIndexOf('/');
		String newPath = sep >= 0 ? current.substring(0, sep) : "";
		navigationPaths.put(userId, newPath);
		if (userId.equals(this.user.getId())) {
			refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
		} else if (wsClient != null) {
			wsClient.sendEvent(new Event("__to:" + userId + ":Solicitar archivos de directorio", user, newPath));
		}
	}

	private void updateRemoteUserFiles(String userId, List<QFile> files) {
		for (User u : remoteUsers) {
			if (u.getId().equals(userId)) {
				u.setFiles(files);
				return;
			}
		}
	}

	private void refreshFilesForUser(String userId) {
		String navPath = navigationPaths.getOrDefault(userId, "");
		if (userId.equals(this.user.getId())) {
			refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
		} else {
			for (User u : remoteUsers) {
				if (u.getId().equals(userId) && u.isOnline()) {
					loadRemoteUserTab(u.getName(), u);
					refreshTables();
					updateFileTabBackButton(userId, navPath);
					return;
				}
			}
		}
		updateFileTabBackButton(userId, navPath);
	}

	private void updateFileTabBackButton(String userId, String navPath) {
		boolean hasPath = navPath != null && !navPath.isEmpty();
		for (int i = 0; i < view.getjTabbedPane().getTabCount(); i++) {
			Component c = view.getjTabbedPane().getComponentAt(i);
			if (c instanceof TabListFile) {
				TabListFile tlf = (TabListFile) c;
				if (c.getName() != null && c.getName().endsWith(userId)) {
					tlf.updateBackButton(hasPath);
					tlf.updateBreadcrumb(hasPath ? navPath : "");
				} else if ("General".equals(view.getjTabbedPane().getTitleAt(i))) {
					tlf.updateBackButton(hasPath);
					tlf.updateBreadcrumb(hasPath ? navPath : "");
				}
			}
		}
	}

	public Logger getLogger() {
		return log;
	}

	public User getUser() {
		return user;
	}

	public WsClient getWsClient() {
		return wsClient;
	}

	public void shutdown() {
		if (wsServer != null && wk != null && user != null) {
			try {
				wsServer.getHubService().sendToWk(wk.getId(), new Event("Usuario desconectado", user));
			} catch (Exception ignored) {}
		}
		try { Thread.sleep(350); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
		if (cloudflareTunnel != null) {
			cloudflareTunnel.stop();
		}
		if (wsServer != null) {
			wsServer.shutdown();
		}
		if (wsClient != null) {
			try { wsClient.close(); } catch (Exception | NoClassDefFoundError ignored) {}
			wsClient = null;
		}
	}
}
