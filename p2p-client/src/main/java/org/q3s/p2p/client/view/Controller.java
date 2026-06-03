package org.q3s.p2p.client.view;

import java.awt.Component;
import java.awt.Container;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
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
import javax.swing.table.DefaultTableModel;
import javax.swing.SwingUtilities;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.java_websocket.WebSocket;
import org.q3s.p2p.adapters.filesystem.QfolderLayout;
import org.q3s.p2p.client.CloudflareInstaller;
import org.q3s.p2p.client.Config;
import org.q3s.p2p.client.UpdateChecker;
import org.q3s.p2p.client.exec.Executor;
import org.q3s.p2p.client.exec.ExecutorFactoryBean;
import org.q3s.p2p.client.hub.CloudflareTunnel;
import org.q3s.p2p.client.hub.EmbeddedWebSocketServer;
import org.q3s.p2p.client.util.FileUtils;
import org.q3s.p2p.client.util.I18n;
import org.q3s.p2p.client.util.LookAndFeelManager;
import org.q3s.p2p.client.util.Logger;
import org.q3s.p2p.client.util.UserPreferences;
import org.q3s.p2p.client.ws.WsClient;
import org.q3s.p2p.client.view.components.FileTableModel;
import org.q3s.p2p.client.view.components.TabListFile;
import org.q3s.p2p.adapters.network.CoreChunkTransferCoordinator;
import org.q3s.p2p.adapters.network.DirectBootstrap;
import org.q3s.p2p.adapters.network.InviteCode;
import org.q3s.p2p.adapters.network.P2PMeshService;
import org.q3s.p2p.adapters.network.P2PNetworkAdapter;
import org.q3s.p2p.core.app.CoreApplicationService;
import org.q3s.p2p.core.codec.CoreEnvelope;
import org.q3s.p2p.core.codec.CoreEnvelopeCodec;
import org.q3s.p2p.core.model.FileMetadata;
import org.q3s.p2p.core.state.WorkspaceState;
import org.q3s.p2p.model.QFile;
import org.q3s.p2p.model.User;
import org.q3s.p2p.model.Workspace;
import org.q3s.p2p.model.util.UUIDUtils;

public class Controller {

	private Workspace wk;
	private User user = User.build(UUIDUtils.generate());
	private String localPublicKey = "";
	private String localPrivateKey = "";

	private List<User> remoteUsers = new ArrayList<User>();

	private WsClient wsClient;
	private EmbeddedWebSocketServer wsServer;
	private CloudflareTunnel cloudflareTunnel;
	private static final int FILE_CHUNK_SIZE = 32 * 1024;
	private String peerTunnelUrl;
	private JTextPane chatArea;
	private JTextField chatInput;
	private JLabel chatReplyLabel;
	private JLabel pinnedChatLabel;
	private String pinnedChatMessageId;
	private JPanel transferPanel;
	private JPanel chatContainerPanel;
	private JPanel helpContainerPanel;
	private javax.swing.JTextPane helpPane;
	private JPanel whiteboardContainerPanel;
	private JPanel notesContainerPanel;
	private JLabel openWorkDirLabel;
	private JPanel membersContainerPanel;
	private JTable membersTable;
	private final Map<String, JProgressBar> transferBars = new LinkedHashMap<>();
	private final Map<String, JPanel> transferRows = new LinkedHashMap<>();
	private final Map<String, String> transferTargets = new LinkedHashMap<>();
	private final Map<String, QFile> activeTransferRequests = new LinkedHashMap<>();
	private final Map<String, String> transferPendingOpenLinks = new LinkedHashMap<>();
	private final Map<String, String> chatFileLinks = new LinkedHashMap<>();
	private final Map<String, String> chatTransferLinks = new LinkedHashMap<>();
	private final Map<String, ChatMessage> chatMessages = new LinkedHashMap<>();
	private final Map<String, int[]> chatMessageRanges = new LinkedHashMap<>();
	private final Map<String, String> indexedCoreFiles = new LinkedHashMap<>();
	private String indexedCoreFilesWorkspaceId;
	private final Map<String, String> pendingMemberPublicKeys = new LinkedHashMap<>();
	private final Map<String, JDialog> approvalDialogs = new LinkedHashMap<>();
	private final Map<String, WebSocket> directPeerConnections = new java.util.concurrent.ConcurrentHashMap<>();
	private final Map<String, User> knownMembers = new LinkedHashMap<>();
	private final Map<String, Long> memberConnectedAt = new LinkedHashMap<>();
	private final Map<String, String> corePeerUrls = new LinkedHashMap<>();
	private final Map<String, Set<String>> corePeerConnections = new LinkedHashMap<>();
	private final Map<String, String> navigationPaths = new LinkedHashMap<>();
	private final Map<String, String> fileNavigationRequestIds = new LinkedHashMap<>();
	private final Map<String, JCheckBox> complementoChecks = new LinkedHashMap<>();
	private final Map<String, Long> complementoSequences = new LinkedHashMap<>();
	private TabListFile archivosTab;
	private JPanel archivosContainerPanel;

	private record FileRegistryEntry(String name, long size, long date, String fileId, String hash, String firstSharedBy) {}
	private final Map<String, FileRegistryEntry> fileRegistry = new LinkedHashMap<>();
	private final Map<String, Set<String>> filePeers = new LinkedHashMap<>();
	private final Map<String, String> pendingChatDownloads = new LinkedHashMap<>();
	private WhiteboardCanvas whiteboardCanvas;
	private JTextField whiteboardTextInput;
	private JButton whiteboardColorButton;
	private JTextPane notesPane;
	private volatile boolean applyingRemoteNotes;
	private javax.swing.Timer joinTimeoutTimer;
	private Timer notesSyncTimer;
	private int notesFontSize = 14;
	private String lastSentNotesState = "";
	private String lastAppliedNotesState = "";
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
	private final ExecutorService outboundEventQueue = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ws-outbound-events");
		t.setDaemon(true);
		return t;
	});
	private volatile long lastLocalWhiteboardChangeAt;

	private View view = new View();

	private Logger log;
	private CoreApplicationService core;
	private CoreChunkTransferCoordinator coreChunkTransfer;
	private P2PNetworkAdapter p2pNetwork;
	private P2PMeshService p2pMesh;
	private DirectBootstrap directBootstrap;
	private File qfolderRootDir;
	private File currentSessionDir;
	private File currentSessionFilesDir;
	private final Set<String> appliedCoreChatIds = new HashSet<>();
	private final Set<String> appliedCoreChatFileIds = new HashSet<>();
	private String lastAppliedCoreWhiteboardState = "";

	private boolean configChange = false;

	private Executor exec = ExecutorFactoryBean.create();

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void start() {

		DefaultListModel model = new DefaultListModel();
		view.getjList2().setModel(model);

		log = new Logger(model);
		qfolderRootDir = new File(Config.SHARED_DIR).getAbsoluteFile();
		qfolderRootDir.mkdirs();
		UserPreferences.init(qfolderRootDir.toPath());
		I18n.setLocale(UserPreferences.getLanguage());
		loadOrCreateLocalIdentity();
		initializeCoreServices(qfolderRootDir.toPath());

		new Thread(() -> removeTemp(), "remove-temp").start();

		log.info("Usuario ID: " + user.getId());

		String hostname = Config.USER_NAME != null ? Config.USER_NAME : getLocalHostName();
		view.getjTextField3().setText(hostname);
		view.getjTextField5().setText(defaultWorkspaceName());
		view.setTitle(hostname);
		user.setName(hostname);
		refreshConfigWorkDirText();
		view.applyI18nTexts();
		refreshTabTitles();

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
				if (p2pMesh != null) p2pMesh.disconnectAll();
			} catch (Exception e) {
				log.debug("No se pudo desconectar malla en shutdown hook: " + e.getMessage());
			}
		}, "shutdown-hook"));
		view.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
		view.addWindowListener(new WindowAdapter() {
			@Override
			public void windowActivated(WindowEvent e) {
				clearSelectedTabMark();
			}

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
		view.getjTextField2().setComponentPopupMenu(createJoinTextPopupMenu());

		new Thread(() -> {
			CloudflareInstaller.ensureInstalled(log);
			log.info("Servicio listo. Puede crear o unirse a un espacio de trabajo.");
		}, "cloudflared-install").start();

		UpdateChecker.checkForUpdates(view, log);
	}

	private void loadOrCreateLocalIdentity() {
		File identityFile = qfolderLayout().identityFile().toFile();
		Properties identity = new Properties();
		try {
			if (identityFile.exists()) {
				try (FileInputStream in = new FileInputStream(identityFile)) {
					identity.load(in);
				}
				String id = identity.getProperty("member.id");
				if (id != null && !id.isBlank()) user.setId(id.trim());
				localPublicKey = identity.getProperty("member.publicKey", "").trim();
				localPrivateKey = identity.getProperty("member.privateKey", "").trim();
				if (!localPublicKey.isBlank() && !localPrivateKey.isBlank()) return;
			}
			ensureLocalKeyPair(identity);
			identity.setProperty("member.id", user.getId());
			identity.setProperty("member.publicKey", localPublicKey);
			identity.setProperty("member.privateKey", localPrivateKey);
			File parent = identityFile.getParentFile();
			if (parent != null) parent.mkdirs();
			try (FileOutputStream out = new FileOutputStream(identityFile)) {
				identity.store(out, "qfolder local identity");
			}
		} catch (Exception e) {
			log.debug("No se pudo cargar identidad local persistente: " + e.getMessage());
		}
	}

	private void ensureLocalKeyPair(Properties identity) throws Exception {
		if (localPublicKey != null && !localPublicKey.isBlank() && localPrivateKey != null && !localPrivateKey.isBlank()) return;
		KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		localPublicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
		localPrivateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
		identity.setProperty("member.publicKey", localPublicKey);
		identity.setProperty("member.privateKey", localPrivateKey);
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
		log.debug("installConfigEnhancements called, configIdx=" + configIdx + ", current I18n locale=" + I18n.currentLocale().getLanguage());
		if (configIdx >= 0) {
			view.getjTabbedPane().setToolTipTextAt(configIdx,
					I18n.get("config") + ": " + I18n.get("config.user") + ", " + I18n.get("config.folder")
					+ ", workspace " + I18n.get("config.alwaysOnTop"));
		}

		JCheckBox alwaysOnTop = new JCheckBox(I18n.get("config.alwaysOnTop"));
		alwaysOnTop.setOpaque(false);
		alwaysOnTop.addActionListener(e -> view.setAlwaysOnTop(alwaysOnTop.isSelected()));

		JButton copyWsId = new JButton(I18n.get("config.copy"));
		copyWsId.setToolTipText(I18n.get("config.copyTooltip"));
		copyWsId.setFocusable(false);
		copyWsId.addActionListener(e -> {
			String id = view.getjTextField6().getText();
			if (id != null && !id.isEmpty()) {
				java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
						.setContents(new java.awt.datatransfer.StringSelection(id), null);
				log.info("Invitación local copiada al portapapeles");
			}
		});

		JLabel openWorkDirLabel = new JLabel(I18n.get("config.openWorkDir"));
		openWorkDirLabel.setForeground(new java.awt.Color(51, 102, 255));
		openWorkDirLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		openWorkDirLabel.setToolTipText(I18n.get("config.openWorkDirTooltip"));
		openWorkDirLabel.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				openWorkingDirectory();
			}
		});

		JLabel languageLabel = new JLabel(I18n.get("config.language"));
		String[] langEntries = { I18n.get("config.languageSpanish"), I18n.get("config.languageEnglish") };
		JComboBox<String> languageCombo = new JComboBox<>(langEntries);
		String currentLang = I18n.currentLocale().getLanguage();
		log.debug("Creating language combo, currentLang=" + currentLang + ", entries=[" + langEntries[0] + ", " + langEntries[1] + "]");
		languageCombo.setSelectedIndex("es".equals(currentLang) ? 0 : 1);
		languageCombo.addActionListener(e -> {
			String code = languageCombo.getSelectedIndex() == 0 ? "es" : "en";
			log.debug("Language combo changed to: " + code + " (index=" + languageCombo.getSelectedIndex() + ")");
			UserPreferences.setLanguage(new Locale(code));
			I18n.setLocale(new Locale(code));
			log.debug("I18n locale after set: " + I18n.currentLocale().getLanguage());
			refreshLanguageTexts();
			log.debug("After refreshLanguageTexts, I18n locale: " + I18n.currentLocale().getLanguage());
			log.info(I18n.get("config.languageApplied"));
		});

		JLabel lafLabel = new JLabel(I18n.get("config.lookAndFeel"));
		lafLabel.setToolTipText(I18n.get("config.lookAndFeelTooltip"));
		Map<String, String> lafOptions = LookAndFeelManager.options();
		String[] lafEntries = lafOptions.values().toArray(new String[0]);
		String[] lafIds = lafOptions.keySet().toArray(new String[0]);
		JComboBox<String> lafCombo = new JComboBox<>(lafEntries);
		String currentLaf = UserPreferences.getLookAndFeel();
		for (int i = 0; i < lafIds.length; i++) {
			if (lafIds[i].equals(currentLaf)) {
				lafCombo.setSelectedIndex(i);
				break;
			}
		}
		lafCombo.addActionListener(e -> {
			int idx = lafCombo.getSelectedIndex();
			if (idx < 0) return;
			String lafId = lafIds[idx];
			UserPreferences.setLookAndFeel(lafId);
			LookAndFeelManager.apply(lafId);
			SwingUtilities.updateComponentTreeUI(view);
			view.pack();
		});

		JLabel complementosLabel = new JLabel(I18n.get("config.complements"));
		complementosLabel.setFont(complementosLabel.getFont().deriveFont(java.awt.Font.BOLD));

		if (enabledComplementos.isEmpty()) {
			enabledComplementos.addAll(java.util.Arrays.asList("Archivos", "Chat", "Pizarra", "Notas", "Miembros"));
		}

		JCheckBox cbChat = new JCheckBox(I18n.get("complement.chat"));
		cbChat.setOpaque(false);
		cbChat.setSelected(enabledComplementos.contains("Chat"));
		cbChat.addActionListener(e -> toggleComplemento("Chat", cbChat.isSelected()));

		JCheckBox cbPizarra = new JCheckBox(I18n.get("complement.whiteboard"));
		cbPizarra.setOpaque(false);
		cbPizarra.setSelected(enabledComplementos.contains("Pizarra"));
		cbPizarra.addActionListener(e -> toggleComplemento("Pizarra", cbPizarra.isSelected()));

		JCheckBox cbNotas = new JCheckBox(I18n.get("complement.notes"));
		cbNotas.setOpaque(false);
		cbNotas.setSelected(enabledComplementos.contains("Notas"));
		cbNotas.addActionListener(e -> toggleComplemento("Notas", cbNotas.isSelected()));

		JCheckBox cbMiembros = new JCheckBox(I18n.get("complement.members"));
		cbMiembros.setOpaque(false);
		cbMiembros.setSelected(enabledComplementos.contains("Miembros"));
		cbMiembros.addActionListener(e -> toggleComplemento("Miembros", cbMiembros.isSelected()));

		JCheckBox cbArchivos = new JCheckBox(I18n.get("complement.files"));
		cbArchivos.setOpaque(false);
		cbArchivos.setSelected(enabledComplementos.contains("Archivos"));
		cbArchivos.addActionListener(e -> toggleComplemento("Archivos", cbArchivos.isSelected()));

		JCheckBox cbLog = new JCheckBox(I18n.get("complement.log"));
		cbLog.setOpaque(false);
		cbLog.setSelected(enabledComplementos.contains("Log"));
		cbLog.addActionListener(e -> toggleComplemento("Log", cbLog.isSelected()));

		JCheckBox cbAyuda = new JCheckBox(I18n.get("complement.help"));
		cbAyuda.setOpaque(false);
		cbAyuda.setSelected(enabledComplementos.contains("Ayuda"));
		cbAyuda.addActionListener(e -> toggleComplemento("Ayuda", cbAyuda.isSelected()));
		complementoChecks.clear();
		complementoChecks.put("Archivos", cbArchivos);
		complementoChecks.put("Chat", cbChat);
		complementoChecks.put("Pizarra", cbPizarra);
		complementoChecks.put("Notas", cbNotas);
		complementoChecks.put("Miembros", cbMiembros);
		complementoChecks.put("Log", cbLog);
		complementoChecks.put("Ayuda", cbAyuda);

		JPanel complementosPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 2));
		complementosPanel.setOpaque(false);
		complementosPanel.add(cbArchivos);
		complementosPanel.add(cbChat);
		complementosPanel.add(cbPizarra);
		complementosPanel.add(cbNotas);
		complementosPanel.add(cbMiembros);
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
				.addGroup(layout.createSequentialGroup()
						.addComponent(view.getjLabel6())
						.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
						.addComponent(openWorkDirLabel))
				.addComponent(alwaysOnTop)
				.addGroup(layout.createSequentialGroup()
						.addComponent(languageLabel)
						.addComponent(languageCombo))
				.addGroup(layout.createSequentialGroup()
						.addComponent(lafLabel)
						.addComponent(lafCombo))
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
				.addGap(8)
				.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
						.addComponent(view.getjLabel6())
						.addComponent(openWorkDirLabel))
				.addGap(18)
				.addComponent(alwaysOnTop)
				.addGap(8)
				.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
						.addComponent(languageLabel)
						.addComponent(languageCombo, javax.swing.GroupLayout.PREFERRED_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
				.addGap(8)
				.addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
						.addComponent(lafLabel)
						.addComponent(lafCombo, javax.swing.GroupLayout.PREFERRED_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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

	private void openWorkingDirectory() {
		try {
			File dir = currentSessionDir != null ? currentSessionDir : getQfolderRootDir();
			dir.mkdirs();
			refreshConfigWorkDirText();
			exec.open(dir.getAbsolutePath());
		} catch (Exception e) {
			log.err(I18n.get("config.openWorkDirError", e.getMessage()));
		}
	}

	private void refreshConfigWorkDirText() {
		if (view == null || view.getjTextField4() == null) return;
		File target = currentSessionDir != null ? currentSessionDir : getQfolderRootDir();
		view.getjTextField4().setText(target.getAbsolutePath());
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
			sendEvent(CoreEnvelope.of(eventName, user.getId(), name));
		}
	}

	private void applyComplementoVisibility(String name, boolean visible) {
		updateComplementoCheck(name, visible);
		if (visible) {
			if ("Chat".equals(name)) { loadChatTab(); ensureChatTabVisible(); }
			if ("Pizarra".equals(name)) { loadWhiteboardTab(); ensureWhiteboardTabVisible(); }
			if ("Notas".equals(name)) { loadNotesTab(); ensureNotesTabVisible(); }
			if ("Miembros".equals(name)) { loadMembersTab(); ensureMembersTabVisible(); }
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
			if ("Miembros".equals(name) && (idx = findTabByTitle("Miembros")) >= 0) {
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
			insertSystemTab("Chat", chatIcon(), chatContainerPanel, I18n.get("tooltip.tab.chat"));
			chatContainerPanel.revalidate();
		}
	}

	private void ensureWhiteboardTabVisible() {
		if (whiteboardContainerPanel != null && findTabByTitle("Pizarra") < 0) {
			insertSystemTab("Pizarra", boardIcon(), whiteboardContainerPanel, I18n.get("tooltip.tab.whiteboard"));
		}
	}

	private void ensureNotesTabVisible() {
		if (notesContainerPanel != null && findTabByTitle("Notas") < 0) {
			insertSystemTab("Notas", noteIcon(), notesContainerPanel, I18n.get("tooltip.tab.notes"));
		}
	}

	private void ensureMembersTabVisible() {
		if (membersContainerPanel != null && findTabByTitle("Miembros") < 0) {
			insertSystemTab("Miembros", groupIcon(), membersContainerPanel, I18n.get("tooltip.tab.members"));
		}
	}

	private void loadHelpTab() {
		if (helpContainerPanel != null) {
			if (findTabByTitle("Ayuda") < 0) {
				insertSystemTab("Ayuda", helpIcon(), helpContainerPanel, I18n.get("help.title"));
			}
			return;
		}
		helpPane = new javax.swing.JTextPane();
		helpPane.setContentType("text/html");
		helpPane.setEditable(false);
		helpPane.setText(buildHelpHtml());
		helpContainerPanel = new JPanel(new BorderLayout(8, 8));
		helpContainerPanel.add(new JScrollPane(helpPane), BorderLayout.CENTER);
		insertSystemTab("Ayuda", helpIcon(), helpContainerPanel, I18n.get("help.title"));
	}

	private void restoreLogTab() {
		ImageIcon icon = new javax.swing.ImageIcon(getClass().getResource("/tab-log.png"));
		int idx = findTabByTitle("Configuración");
		if (idx < 0) {
			view.getjTabbedPane().addTab(I18n.get("tab.log"), icon, view.getjScrollPane2(), I18n.get("log.tabTooltip"));
		} else {
			view.getjTabbedPane().insertTab(I18n.get("tab.log"), icon, view.getjScrollPane2(), I18n.get("log.tabTooltip"), idx);
		}
	}

	private void removeLogTab() {
		int idx = findTabByTitle("Log");
		if (idx >= 0) {
			view.getjTabbedPane().removeTabAt(idx);
		}
	}

	private void hideInitialComplementos() {
		for (String name : new String[]{"Archivos", "Chat", "Pizarra", "Notas", "Miembros", "Ayuda"}) {
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
		qfolderRootDir = new File(Config.SHARED_DIR).getAbsoluteFile();
		currentSessionDir = null;
		currentSessionFilesDir = null;
		prepareWorkspaceSessionDirectories();
		refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
	}

	private void jTextField3FocusLost(java.awt.event.FocusEvent evt) {
		if (configChange) {
			CoreEnvelope e = CoreEnvelope.of("Cambio de nombre", user.getId(), user.getName());
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
			qfolderRootDir = file.getAbsoluteFile();
			currentSessionDir = null;
			currentSessionFilesDir = null;
			prepareWorkspaceSessionDirectories();
			refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
		}
	}

	private void jTextField3KeyReleased(java.awt.event.KeyEvent evt) {
		user.setName(view.getjTextField3().getText());
		trackMember(user, wsClient != null);
		updateWindowTitle();
		configChange = true;
	}

	private void updateWindowTitle() {
		String username = user.getName() != null && !user.getName().trim().isEmpty()
				? user.getName().trim() : getLocalHostName();
		if (wk != null && view.getjTabbedPane().isVisible()) {
			view.setTitle("'" + username + "' conectado al grupo '" + decodeValue(wk.getName()) + "'");
		} else {
			view.setTitle(username + " - qfolder v" + UpdateChecker.getVersion());
		}
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
		view.getjTextField2().setComponentPopupMenu(createJoinTextPopupMenu());
		view.getjButton6().setEnabled(true);

		mostrarErrorEnPantallaLogin(message);

		wsClient = null;
		remoteUsers = new ArrayList<User>();
		if (p2pMesh != null) p2pMesh.disconnectAll();
		removeAllTab();
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
		if (!Config.isTunnelMockEnabled() && !CloudflareInstaller.isInstalled()) {
			javax.swing.SwingUtilities.invokeLater(() ->
				mostrarErrorEnPantallaLogin(I18n.get("tunnel.preparing")));
			new Thread(() -> {
				log.info("Esperando instalación de cloudflared...");
				if (!CloudflareInstaller.awaitInstallation(150)) {
					String error = "Timeout esperando instalación de cloudflared";
					log.err(error);
					javax.swing.SwingUtilities.invokeLater(() -> {
						if (onError != null) {
							onError.accept(error);
						}
					});
					return;
				}
				if (!CloudflareInstaller.isInstalled()) {
					String error = "cloudflared no está instalado";
					log.err(error);
					javax.swing.SwingUtilities.invokeLater(() -> {
						if (onError != null) {
							onError.accept(error);
						}
					});
					return;
				}
				javax.swing.SwingUtilities.invokeLater(() -> {
					mostrarErrorEnPantallaLogin(I18n.get("tunnel.starting"));
					startPeerEndpointServer(onReady, onError);
				});
			}, "wait-cloudflared").start();
			return;
		}
		String tunnelMessage = Config.isTunnelMockEnabled() ? I18n.get("tunnel.starting.mock") : I18n.get("tunnel.starting");
		mostrarErrorEnPantallaLogin(tunnelMessage);
		startPeerEndpointServer(onReady, onError);
	}

	private void startPeerEndpointServer(Runnable onReady, java.util.function.Consumer<String> onError) {
		final int port = Config.WS_SERVER_PORT;
		wsServer = new EmbeddedWebSocketServer(port, () -> {
			log.info("Servidor WebSocket iniciado en puerto " + port);
			cloudflareTunnel = new CloudflareTunnel(log);
			cloudflareTunnel.start(port, tunnelUrl -> {
				peerTunnelUrl = tunnelUrl.replace("https://", "").replace("http://", "").trim();
				user.setPeerUrl(peerTunnelUrl);
				trackMember(user, true);
				log.info("Endpoint del peer listo: " + peerTunnelUrl);
				onReady.run();
			}, error -> {
				log.err("Error al iniciar cloudflared: " + error);
				if (onError != null) {
					onError.accept(error);
				}
			});
		}, this::handleDirectPeerEvent, peerId -> handlePeerDisconnected(peerId));
		wsServer.start();
	}

	private void handleDirectPeerEvent(WebSocket conn, CoreEnvelope envelope) {
		if (envelope == null || envelope.name() == null) return;
		if (envelope != null) {
			String chunkProtocolName = envelope != null && envelope.name() != null && (envelope.name().contains("Core chunk") || envelope.name().contains("core chunk")) ? envelope.name() : null;
			if (chunkProtocolName != null) {
				log.debug("[P2P RECV] tipo=" + chunkProtocolName + " from=" + (envelope.userId() != null ? envelope.userId() : "?") + " conn=" + (conn != null ? "yes" : "no"));
			}
		}
		if (envelope != null && envelope.userId() != null && conn != null) {
			directPeerConnections.put(envelope.userId(), conn);
			log.debug("[P2P RECV] directPeerConnections now has " + directPeerConnections.size() + " entries");
		}
		if (coreChunkTransfer != null && coreChunkTransfer.handle(envelope, conn)) {
			return;
		} else if (CoreEnvelopeCodec.CORE_EVENT_NAME.equals(envelope.name())) {
			log.info("[P2P DIRECT] Core event via direct handler");
			acceptCoreEventOnDirect(envelope);
		} else if (CoreEnvelopeCodec.CORE_SYNC_REQUEST_NAME.equals(envelope.name())) {
			respondCoreSyncOnDirect(envelope, conn);
		} else if (CoreEnvelopeCodec.CORE_SYNC_RESPONSE_NAME.equals(envelope.name())) {
			acceptCoreSyncOnDirect(envelope);
		}
	}

	private void acceptCoreEventOnDirect(CoreEnvelope envelope) {
		try {
			org.q3s.p2p.core.model.Event coreEvent = CoreEnvelopeCodec.decodeCoreEvent(envelope);
			if (coreEvent != null) {
				boolean accepted = core.receiveRemoteEvent(coreEvent);
				log.info("[P2P DIRECT] decoded=true accepted=" + accepted + " type=" + coreEvent.type());
				if (accepted) {
					if (org.q3s.p2p.core.events.EventTypes.MEMBER_JOIN_REQUESTED.equals(coreEvent.type())) {
						User pending = User.build(String.valueOf(coreEvent.payload().get("candidate_member_id")));
						pending.setName(String.valueOf(coreEvent.payload().getOrDefault("candidate_display_name", pending.getId())));
						pendingMemberPublicKeys.put(pending.getId(), String.valueOf(coreEvent.payload().getOrDefault("public_key", "")));
						showApprovalDialog(pending);
					}
					applyCoreStateToVisuals(core.currentState());
				}
			} else {
				log.info("[P2P DIRECT] decode FAILED");
			}
		} catch (Exception e) {
			log.info("[P2P DIRECT] ERROR: " + e.getMessage());
		}
	}

	private void respondCoreSyncOnDirect(CoreEnvelope envelope, WebSocket conn) {
		try {
			if (envelope.userId() == null || envelope.userId().equals(user.getId())) return;
			String wsId = wk != null ? wk.getId() : null;
			if (wsId == null) return;
			java.util.Set<String> knownIds = CoreEnvelopeCodec.decodeKnownEventIds(envelope);
			java.util.List<org.q3s.p2p.core.model.Event> missing = core.missingEvents(knownIds);
			if (!missing.isEmpty() && conn != null) {
				CoreEnvelope response = CoreEnvelope.of(CoreEnvelopeCodec.CORE_SYNC_RESPONSE_NAME, user.getId(),
						CoreEnvelopeCodec.encodeSyncPayload(missing));
				conn.send(response.toJsonBase64());
			}
		} catch (Exception e) {
			log.debug("Sync request directo no procesado: " + e.getMessage());
		}
	}

	private void acceptCoreSyncOnDirect(CoreEnvelope envelope) {
		try {
			java.util.List<org.q3s.p2p.core.model.Event> events = CoreEnvelopeCodec.decodeSyncEvents(envelope);
			int accepted = core.receiveRemoteEvents(events);
			if (accepted > 0) applyCoreStateToVisuals(core.currentState());
		} catch (Exception e) {
			log.debug("Sync response directo no procesado: " + e.getMessage());
		}
	}

	private void handlePeerDisconnected(String peerId) {
		if (peerId == null || peerId.isBlank()) return;
		directPeerConnections.remove(peerId);
		if (p2pMesh != null) p2pMesh.peerDisappeared(peerId);
		javax.swing.SwingUtilities.invokeLater(() -> {
			User stored = knownMembers.get(peerId);
			if (stored != null) stored.setOnline(false);
			refreshMembersTable();
			refreshArchivosTable();
		});
		log.info("Peer desconectado del endpoint local: " + peerId);
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
		setCreateWorkspaceControlsEnabled(false);
		ensurePeerEndpoint(() -> {
			final String cleanUrl = peerTunnelUrl;
			long date = new Date().getTime();
			sessionCreatedAt = date;
			historySaved = false;
			wk = new Workspace(cleanUrl, decodeValue(wsName));
			wk.setDate(date);
			currentSessionDir = null;
			currentSessionFilesDir = null;
			prepareWorkspaceSessionDirectories();
			try {
				core.ensureWorkspaceSession(cleanUrl, decodeValue(wsName), user.getId(), user.getName(), "swing-device", user.getId(),
						localPublicKey, localPrivateKey, 2);
			} catch (Exception e) {
				log.err("No se pudo inicializar core workspace: " + e.getMessage());
			}
			user.setPeerUrl(cleanUrl);

			javax.swing.SwingUtilities.invokeLater(() -> {
				view.getjTextField2().setText(encodeWkId(localInviteCode()));
				view.getjTextField6().setText(encodeWkId(localInviteCode()));
				log.info("Workspace '" + wsName + "' creado. URL: " + cleanUrl);
				mostrarErrorEnPantallaLogin("");
				log.info("Activando espacio de trabajo P2P...");

				view.getjPanelCreateWorkspace().setVisible(false);
				view.getjTabbedPane().setVisible(false);
				view.getjPanelProxy().setVisible(false);
				setCreateWorkspaceControlsEnabled(true);
				view.setTitle(view.getjTextField3().getText());

				notify(CoreEnvelope.of("Bienvenido usuario al grupo!", user.getId(), wk != null ? wk.getId() : ""));
			});
		}, error -> {
			javax.swing.SwingUtilities.invokeLater(() -> {
				setCreateWorkspaceControlsEnabled(true);
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
		view.getjLabel17().setText(message);
	}

	private void setJoinControlsEnabled(boolean enabled) {
		view.getjTextField2().setEnabled(enabled);
		view.getjButton2().setEnabled(enabled);
		view.getjLabel4().setEnabled(enabled);
		view.getjButton6().setEnabled(enabled);
		if (enabled) view.getjTextField2().setFocusable(true);
	}

	private void setCreateWorkspaceControlsEnabled(boolean enabled) {
		view.getjTextField5().setEnabled(enabled);
		view.getjCheckBox1().setEnabled(enabled);
		view.getjPasswordField1().setEnabled(enabled && view.getjCheckBox1().isSelected());
		view.getjPasswordField2().setEnabled(enabled && view.getjCheckBox1().isSelected());
		view.getjButton4().setEnabled(enabled);
		view.getjButton5().setEnabled(enabled);
	}

	private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {
		String invite = decodeWkId(view.getjTextField2().getText());

		if (invite.isEmpty()) {
			mostrarErrorEnPantallaLogin(I18n.get("workspace.emptyId"));
		} else {
			user.setName(view.getjTextField3().getText());
			setJoinControlsEnabled(false);
			mostrarErrorEnPantallaLogin("");
			ensurePeerEndpoint(() -> {
				if (directBootstrap != null && directBootstrap.join(invite, user.getId(), user.getName(), localPublicKey, localPrivateKey)) {
					log.info("Solicitud/conexion P2P enviada. Esperando autorizacion o sync...");
					joinTimeoutTimer = new Timer(30000, e -> {
						if (view.getjPanelJoin().isVisible() && !view.getjButton2().isEnabled()) {
							setJoinControlsEnabled(true);
							mostrarErrorEnPantallaLogin(I18n.get("join.timeout"));
						}
					});
					joinTimeoutTimer.setRepeats(false);
					joinTimeoutTimer.start();
				} else {
					javax.swing.SwingUtilities.invokeLater(() -> {
						setJoinControlsEnabled(true);
						mostrarErrorEnPantallaLogin(I18n.get("workspace.cannotConnect"));
					});
				}
			}, error -> javax.swing.SwingUtilities.invokeLater(() -> {
				setJoinControlsEnabled(true);
				mostrarErrorEnPantallaLogin(I18n.get("endpoint.error") + " " + error);
			}));
		}
	}

	private String webSocketUriForEndpoint(String endpoint) {
		if (endpoint == null) return "";
		String value = endpoint.trim();
		if (value.startsWith("ws://") || value.startsWith("wss://")) return value;
		if (value.startsWith("https://")) return "wss://" + value.substring("https://".length());
		if (value.startsWith("http://")) return "ws://" + value.substring("http://".length());
		if (value.endsWith(".trycloudflare.com")) return "wss://" + value;
		return "ws://" + value;
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
		if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
			javax.swing.SwingUtilities.invokeLater(() -> showApprovalDialog(to));
			return;
		}
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
			try {
				org.q3s.p2p.core.model.Event approval = core.approveJoin(to.getId());
				publishCoreEvent(approval);
				sendDirectCoreEvent(to.getId(), approval);
				org.q3s.p2p.core.model.Event status = p2pMesh != null ? p2pMesh.forcePublishPeerStatus() : null;
				sendDirectCoreEvent(to.getId(), status);
				sendDirectCoreSyncSnapshot(to.getId());
				applyCoreStateToVisuals(core.currentState());
				WorkspaceState state = core.currentState();
				if (state.isAuthorized(to.getId())) {
					log.info("Usuario aprobado e incorporado: " + to.getName());
				} else {
					log.info("Aprobacion registrada para " + to.getName() + ". Esperando mas aprobaciones.");
				}
			} catch (Exception ex) {
				log.debug("No se pudo registrar aprobacion en core: " + ex.getMessage());
			}
			closeApprovalDialog(to.getId());
		});
		refuse.addActionListener(e -> {
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

	private void sendDirectCoreEvent(String peerId, org.q3s.p2p.core.model.Event coreEvent) {
		WebSocket conn = directPeerConnections.get(peerId);
		if (conn == null || conn.isClosed() || coreEvent == null) return;
		try {
			CoreEnvelope envelope = CoreEnvelope.of(CoreEnvelopeCodec.CORE_EVENT_NAME, user.getId(),
					CoreEnvelopeCodec.encodeCoreEvent(coreEvent));
			conn.send(envelope.toJsonBase64());
		} catch (Exception e) {
			log.debug("No se pudo enviar evento core directo a " + peerId + ": " + e.getMessage());
		}
	}

	private void sendDirectCoreSyncSnapshot(String peerId) {
		WebSocket conn = directPeerConnections.get(peerId);
		if (conn == null || conn.isClosed()) return;
		try {
			CoreEnvelope envelope = CoreEnvelope.of(CoreEnvelopeCodec.CORE_SYNC_RESPONSE_NAME, user.getId(),
					CoreEnvelopeCodec.encodeSyncPayload(core.events()));
			conn.send(envelope.toJsonBase64());
		} catch (Exception e) {
			log.debug("No se pudo enviar sync directo a " + peerId + ": " + e.getMessage());
		}
	}

	public void notify(CoreEnvelope envelope) {
		try {
			if (envelope != null && envelope.name() != null) {
				log.debug(envelope.name());
				String name = envelope.name();
				User envelopeUser = userFromEnvelope(envelope);
				if ("Wk no existe, desconectar".equals(name)) {
					if (wsClient != null) wsClient.close();
					view.getjButton2().setEnabled(true);
					view.getjLabel4().setEnabled(true);
					view.getjTextField2().setEnabled(true);
					view.getjTextField2().setFocusable(true);
					view.getjButton6().setEnabled(true);
					mostrarErrorEnPantallaLogin(I18n.get("ws.notExists"));
				} else if ("Wk existente, sin credenciales".equals(name)) {
					log.debug("Evento legacy de hub ignorado: " + name);
				} else if ("Wk existente, con credenciales".equals(name)) {
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
					}

				} else if ("Credenciales incorrectas".equals(name)) {
					log.info("Credenciales de acceso incorrectas");
					mostrarErrorEnPantallaLogin(I18n.get("ws.wrongCredentials"));
				} else if ("Aprobar al usuario".equals(name)) {
					markLogIfInactive();
					log.info("El usuario '" + (envelopeUser != null ? envelopeUser.getName() : "?") + "' solicita ingresar al workspace");
					try {
						String requesterId = envelopeUser != null ? envelopeUser.getId() : envelope.userId();
						String requesterName = envelopeUser != null ? envelopeUser.getName() : requesterId;
					core.recordJoinRequest(requesterId, requesterName, "swing-device", requesterId);
					} catch (Exception e) {
						log.debug("No se pudo registrar solicitud remota core: " + e.getMessage());
					}
					if (envelopeUser != null) showApprovalDialog(envelopeUser);
				} else if ("Usuario rechazado!".equals(name)) {
					log.info("Tu ingreso fue rechazado por los usuarios del workspace");
					if (wsClient != null) wsClient.close();
					mostrarErrorEnPantallaLogin(I18n.get("approve.rejected"));
				} else if ("Bienvenido usuario al grupo!".equals(name)) {
					log.info("Bienvenido al grupo (legacy)");
					showJoinAfterWorkspaceLost(name);
				} else if ("Gracias por la bienvenida, notifico mis archivos".equals(name)
						|| "Estos son mis archivos".equals(name)) {
					log.debug("Evento legacy de archivos ignorado: " + name);
				} else if ("Notifico Cambio en los archivos".equals(name)) {
					if (envelopeUser == null || !envelopeUser.equals(user)) markLogIfInactive();
					notifyChangeFiles(envelope);
				} else if ("Archivo de chat enviado".equals(name)) {
					log.debug("Evento legacy de chat ignorado: " + name);
				} else if (CoreEnvelopeCodec.CORE_EVENT_NAME.equals(name)) {
					acceptCoreEventOnDirect(envelope);
					return;
				} else if ("Se borro un archivo".equals(name)) {
					if (envelopeUser == null || !envelopeUser.equals(user)) markLogIfInactive();
					log.info("Evento legacy de borrado ignorado: " + name);
					notifyChangeFiles(envelope);
				} else if ("Solicitar archivos de directorio".equals(name)
						|| "Respuesta archivos de directorio".equals(name)) {
					log.debug("Evento legacy de navegacion de archivos ignorado: " + name);
				} else if ("Cambio de nombre".equals(name)) {
					if (envelopeUser == null) return;
					int idx = searchTabById(envelopeUser.getId());
					User existing = findKnownUser(envelopeUser);
					if (existing == null) {
						existing = envelopeUser;
						remoteUsers.add(existing);
					}
					String oldUserName = existing.getName();
					existing.setName(envelopeUser.getName());
					trackMember(existing, existing.isOnline());
					refreshTables();
					refreshMembersTable();
					if (!existing.equals(user)) {
						markLogIfInactive();
						log.info("El usuario '" + oldUserName + "' cambio de nombre a '" + existing.getName() + "'");
						if (idx >= 0) view.getjTabbedPane().setTitleAt(idx, existing.getName());
					} else {
						log.info("Cambiaste de nombre a '" + existing.getName() + "'");
					}
				} else if ("Usuario desconectado".equals(name)) {
					markLogIfInactive();
					User disconnectedUser = findKnownUser(envelopeUser);
					String disconnectedName = disconnectedUser != null && disconnectedUser.getName() != null
							? disconnectedUser.getName() : "Usuario";
					String disconnectedId = envelopeUser != null ? envelopeUser.getId() : null;
					log.info("Usuario '" + disconnectedName + "' desconectado");
					if (disconnectedId != null && chatActiveUserIds.contains(disconnectedId)) {
						disconnectedChatUserNames.add(disconnectedName);
						appendChatSystemMessage(I18n.get("chat.userDisconnected", disconnectedName));
						markTabIfInactive("Chat");
					}
					if (disconnectedUser != null) {
						disconnectedUser.setOnline(false);
						trackMember(disconnectedUser, false);
						remoteUsers.remove(disconnectedUser);
					} else if (envelopeUser != null) {
						envelopeUser.setOnline(false);
						trackMember(envelopeUser, false);
						remoteUsers.remove(envelopeUser);
					}
					refreshArchivosTable();
					refreshTables();
					refreshMembersTable();
				} else if ("Quiero descargar el archivo".equals(name)) {
					log.debug("Evento legacy de descarga ignorado: " + name);
				} else if (coreChunkTransfer != null && coreChunkTransfer.handle(envelope, null)) {
					return;

				} else if ("Parte de archivo".equals(name)) {
					log.debug("Evento legacy de transferencia ignorado: " + name);
				} else if ("Error al transferir archivo".equals(name)) {
					markLogIfInactive();
					log.err(envelope.response());

				} else if ("Complemento habilitado".equals(name)) {
					String complementoName = envelope.response();
					if (isStaleComplementoEvent(complementoName, envelope.sequence())) return;
					enabledComplementos.add(complementoName);
					applyComplementoVisibility(complementoName, true);
					log.info("Complemento '" + complementoName + "' habilitado" +
							(envelopeUser != null && !envelopeUser.equals(user) ? " por " + envelopeUser.getName() : ""));

				} else if ("Complemento deshabilitado".equals(name)) {
					String complementoName = envelope.response();
					if (isStaleComplementoEvent(complementoName, envelope.sequence())) return;
					enabledComplementos.remove(complementoName);
					applyComplementoVisibility(complementoName, false);
					log.info("Complemento '" + complementoName + "' deshabilitado" +
							(envelopeUser != null && !envelopeUser.equals(user) ? " por " + envelopeUser.getName() : ""));

				} else if ("Solicitud de ingreso resuelta".equals(name)) {
					closeApprovalDialog(envelopeUser != null ? envelopeUser.getId() : null);

				} else if ("Error al intentar conectarse con el servidor".equals(name)) {
				} else if ("No es posible establecer una conexion".equals(name)) {
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
				} else if ("Se perdio la conexion con el servidor".equals(name)) {
					log.info("Se perdio la conexion con el workspace");
					cacheCurrentSessionForReconnect();
					saveSessionHistory("desconexion");
					showJoinAfterWorkspaceLost(name);
				}
			}
		} catch (Exception e) {
			log.debug("Error notify -> " + e.getMessage());
		}
	}

	private User userFromEnvelope(CoreEnvelope envelope) {
		if (envelope == null || envelope.userId() == null) return null;
		String userId = envelope.userId();
		User known = findKnownUserById(userId);
		if (known != null) return known;
		User built = User.build(userId);
		String display = envelope.userId();
		built.setName(display);
		return built;
	}

	private User findKnownUserById(String userId) {
		if (userId == null) return null;
		for (User u : remoteUsers) {
			if (userId.equals(u.getId())) return u;
		}
		return null;
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

	private String localInviteCode() {
		String wsId = wk != null ? wk.getId() : core.currentWorkspaceId().orElse("");
		String peerUrl = peerTunnelUrl != null && !peerTunnelUrl.isBlank() ? peerTunnelUrl : wsId;
		return InviteCode.encode(peerUrl, wsId);
	}

	private List<org.q3s.p2p.core.model.Event> currentCoreEventsOrEmpty() {
		try {
			return core != null && core.currentWorkspaceId().isPresent() ? new ArrayList<>(core.events()) : List.of();
		} catch (Exception e) {
			return List.of();
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

		// try file-based history from the current session folder
		File sessionDir = buildSessionHistoryDir();
		if (sessionDir == null || !sessionDir.exists()) return;
		File chatFile = new File(new File(sessionDir, "chat"), "chat.txt");
		if (!chatFile.exists()) chatFile = new File(sessionDir, "chat.txt");
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
		return buildSessionDir();
	}

	private File buildSessionDir() {
		if (wk == null) return null;
		long createdAt = sessionCreatedAt > 0 ? sessionCreatedAt : System.currentTimeMillis();
		String workspaceName = wk.getName() != null ? decodeValue(wk.getName()) : defaultWorkspaceName();
		QfolderLayout layout = new QfolderLayout(getQfolderRootDir().toPath());
		return layout.workspaceFolder(wk.getId(), Instant.ofEpochMilli(createdAt), workspaceName).toFile();
	}

	private File getQfolderRootDir() {
		if (qfolderRootDir == null) qfolderRootDir = new File(Config.SHARED_DIR).getAbsoluteFile();
		return qfolderRootDir;
	}

	private QfolderLayout qfolderLayout() {
		return new QfolderLayout(getQfolderRootDir().toPath());
	}

	private File getSessionFilesDir() {
		prepareWorkspaceSessionDirectories();
		return currentSessionFilesDir != null ? currentSessionFilesDir : getQfolderRootDir();
	}

	private void prepareWorkspaceSessionDirectories() {
		if (wk == null) {
			getQfolderRootDir().mkdirs();
			QfolderLayout layout = qfolderLayout();
			layout.userdataRoot().toFile().mkdirs();
			layout.systemdataRoot().toFile().mkdirs();
			refreshConfigWorkDirText();
			return;
		}
		if (currentSessionDir == null) {
			currentSessionDir = buildSessionDir();
			currentSessionFilesDir = new File(currentSessionDir, "files");
			QfolderLayout layout = qfolderLayout();
			layout.createStructure(currentSessionDir.toPath());
			Path systemWorkspaceRoot = layout.systemWorkspaceRoot(wk.getId());
			try {
				Files.createDirectories(systemWorkspaceRoot.resolve("events"));
				Files.createDirectories(systemWorkspaceRoot.resolve("chunks"));
				Files.createDirectories(systemWorkspaceRoot.resolve("snapshots"));
				Files.createDirectories(systemWorkspaceRoot.resolve("state"));
			} catch (Exception e) {
				log.debug("No se pudieron crear directorios systemdata: " + e.getMessage());
			}
			writeWorkspaceJson();
			initializeCoreServices(systemWorkspaceRoot);
		}
		currentSessionFilesDir.mkdirs();
		refreshConfigWorkDirText();
	}

	private void writeWorkspaceJson() {
		if (currentSessionDir == null || wk == null) return;
		try {
			long createdAt = sessionCreatedAt > 0 ? sessionCreatedAt : System.currentTimeMillis();
			String workspaceName = wk.getName() != null ? decodeValue(wk.getName()) : defaultWorkspaceName();
			Map<String, Object> data = QfolderLayout.workspaceJson(wk.getId(), workspaceName,
					Instant.ofEpochMilli(createdAt), Instant.now(), currentSessionDir.getAbsolutePath(), 2);
			Files.writeString(new File(currentSessionDir, "workspace.json").toPath(), jsonObject(data));
		} catch (Exception e) {
			log.debug("No se pudo escribir workspace.json: " + e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private String jsonObject(Map<String, Object> data) {
		JsonObjectBuilder builder = Json.createObjectBuilder();
		for (Map.Entry<String, Object> entry : data.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Number number) builder.add(entry.getKey(), number.longValue());
			else if (value instanceof Boolean bool) builder.add(entry.getKey(), bool);
			else if (value instanceof Map<?, ?> map) builder.add(entry.getKey(), Json.createReader(new java.io.StringReader(jsonObject((Map<String, Object>) map))).readObject());
			else builder.add(entry.getKey(), String.valueOf(value));
		}
		return builder.build().toString();
	}

	private void notifyChangeFiles(CoreEnvelope envelope) {
		User envelopeUser = userFromEnvelope(envelope);
		addUserToRemoteList(envelopeUser);
		refreshArchivosTable();
		refreshTables();
	}

	private void refreshArchivosUserFilter() {
		refreshArchivosTable();
	}

	private void refreshLocalFilesAndNotify(String msg) {
		String baseDir = getSessionFilesDir().getAbsolutePath();
		String navPath = navigationPaths.getOrDefault(user.getId(), "");
		List<QFile> files = FileUtils.files(baseDir, navPath);
		this.user.setFiles(files);
		indexFilesInCore(files, baseDir);
		trackMember(user, true);
		refreshArchivosTable();
		refreshTables();
	}

	private void indexLocalFilesInCore() {
		String baseDir = getSessionFilesDir().getAbsolutePath();
		List<QFile> files = FileUtils.files(baseDir, navigationPaths.getOrDefault(user.getId(), ""));
		this.user.setFiles(files);
		indexFilesInCore(files, baseDir);
	}

	private void indexFilesInCore(List<QFile> files, String baseDir) {
		if (files == null || baseDir == null || baseDir.isBlank()) return;
		loadIndexedCoreFilesCache();
		for (QFile file : files) {
			if (file == null || file.isDirectory()) continue;
			String relativePath = file.getRelativePath() != null && !file.getRelativePath().isBlank()
					? file.getRelativePath() : file.getName();
			String key = relativePath;
			String cachedFingerprint = indexedCoreFiles.get(key);
			String sizeMtime = file.getSize() + ":" + file.getDate();
			if (sizeMtime.equals(cachedFingerprint)) continue;
			File diskFile = new File(baseDir, relativePath);
			String hash = sha256File(diskFile);
			if (hash != null && hash.equals(cachedFingerprint)) {
				indexedCoreFiles.put(key, sizeMtime);
				saveIndexedCoreFilesCache();
				continue;
			}
			try {
				publishCoreEvent(core.shareFile(diskFile.toPath()));
				String storedFingerprint = hash != null ? hash : sizeMtime;
				indexedCoreFiles.put(key, storedFingerprint);
				saveIndexedCoreFilesCache();
			} catch (Exception e) {
				log.debug("No se pudo indexar archivo core '" + relativePath + "': " + e.getMessage());
			}
		}
	}

	private String sha256File(File file) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			try (FileInputStream in = new FileInputStream(file)) {
				byte[] buf = new byte[8192];
				int read;
				while ((read = in.read(buf)) != -1) md.update(buf, 0, read);
			}
			return HexFormat.of().formatHex(md.digest());
		} catch (Exception e) {
			return null;
		}
	}

	private void loadIndexedCoreFilesCache() {
		String workspaceId = wk != null ? wk.getId() : null;
		if (workspaceId == null || workspaceId.isBlank() || workspaceId.equals(indexedCoreFilesWorkspaceId)) return;
		indexedCoreFiles.clear();
		indexedCoreFilesWorkspaceId = workspaceId;
		File file = indexedCoreFilesCacheFile(workspaceId);
		if (!file.isFile()) return;
		try (FileInputStream in = new FileInputStream(file)) {
			Properties props = new Properties();
			props.load(in);
			for (String name : props.stringPropertyNames()) indexedCoreFiles.put(name, props.getProperty(name));
		} catch (Exception e) {
			log.debug("No se pudo leer cache de indexacion core: " + e.getMessage());
		}
	}

	private void saveIndexedCoreFilesCache() {
		String workspaceId = wk != null ? wk.getId() : indexedCoreFilesWorkspaceId;
		if (workspaceId == null || workspaceId.isBlank()) return;
		File file = indexedCoreFilesCacheFile(workspaceId);
		File parent = file.getParentFile();
		if (parent != null) parent.mkdirs();
		try (FileOutputStream out = new FileOutputStream(file)) {
			Properties props = new Properties();
			props.putAll(indexedCoreFiles);
			props.store(out, "qfolder core indexed files cache");
		} catch (Exception e) {
			log.debug("No se pudo guardar cache de indexacion core: " + e.getMessage());
		}
	}

	private File indexedCoreFilesCacheFile(String workspaceId) {
		String safeId = safeFileName(workspaceId == null ? "default" : workspaceId);
		return qfolderLayout().systemWorkspaceRoot(safeId).resolve("index-cache.properties").toFile();
	}

	private void sendEvent(CoreEnvelope e) {
		WsClient client = wsClient;
		if (client == null || e == null) return;
		outboundEventQueue.execute(() -> client.sendEnvelope(e));
	}

	private void initializeCoreServices(Path root) {
		if (p2pMesh != null) {
			try { p2pMesh.disconnectAll(); } catch (Exception ignored) { log.debug("mesh shutdown: " + ignored.getMessage()); }
			p2pMesh = null;
		}
		if (coreChunkTransfer != null) {
			try { coreChunkTransfer.shutdown(); } catch (Exception ignored) { log.debug("shutdown: " + ignored.getMessage()); }
			coreChunkTransfer = null;
		}
		if (p2pNetwork != null) {
			try { p2pNetwork.disconnectAll(); } catch (Exception ignored) { log.debug("disconnect: " + ignored.getMessage()); }
			p2pNetwork = null;
		}
		core = currentSessionDir != null
				? CoreApplicationService.filesystemWorkspace(root)
				: CoreApplicationService.filesystem(root);
		Path snapshotRoot = currentSessionDir != null ? root.resolve("snapshots") : root.resolve("workspaces").resolve("__snapshots");
		try {
			java.nio.file.Files.createDirectories(snapshotRoot);
		} catch (Exception ignored) {
			log.debug("No se pudo crear directorio de snapshots: " + ignored.getMessage());
		}
		core.configureSnapshotPath(snapshotRoot);
		coreChunkTransfer = new CoreChunkTransferCoordinator(core, () -> user, this::sendP2PProtocolEvent, this::updateTransferProgress,
				this::completeCoreChunkDownload, this::fallbackCoreChunkDownload,
				(message, detail) -> log.debug(message + (detail == null || detail.isBlank() ? "" : ": " + detail)));
		core.chunkReplicator().onFileAvailable(this::requestReplicatorDownload);
		core.chunkReplicator().enable();
		p2pNetwork = new P2PNetworkAdapter(user.getId(),
				() -> "ws://localhost:" + Config.WS_SERVER_PORT,
				() -> wk != null ? wk.getId() : null,
				core.eventStore(), this::handleDirectPeerEvent, message -> log.debug(message), true);
		p2pNetwork.onCoreEventStored(e -> {
			applyCoreEventIncremental(e);
			applyCoreStateToVisuals(core.currentState());
		});
		p2pNetwork.onCoreSyncApplied(events -> applyCoreStateToVisuals(core.currentState()));
		p2pMesh = new P2PMeshService(p2pNetwork, core, () -> wk != null ? wk.getId() : null,
				() -> peerTunnelUrl, this::applyCoreStateToVisuals, this::publishCoreEvent, message -> log.debug(message));
		directBootstrap = new DirectBootstrap(core, p2pMesh, this::sendP2PProtocolEvent,
				this::activateWorkspaceFromCore, (peer, wsId) -> showApprovalDialog(peer),
				message -> log.debug(message));
	}

	private void activateWorkspaceFromCore() {
		if (joinTimeoutTimer != null) {
			joinTimeoutTimer.stop();
			joinTimeoutTimer = null;
		}
		javax.swing.SwingUtilities.invokeLater(() -> {
			try {
				WorkspaceState state = core.currentState();
				String wsId = core.currentWorkspaceId().orElse(null);
				if (wsId == null) return;
				String name = state.workspace() != null ? state.workspace().name() : wsId;
				Workspace workspace = new Workspace(wsId, name);
				workspace.setDate(sessionCreatedAt > 0 ? sessionCreatedAt : System.currentTimeMillis());
				notify(CoreEnvelope.of("Bienvenido usuario al grupo!", user.getId(), workspace.getId()));
				try {
					int triggered = core.runStartupCache();
					if (triggered > 0) log.debug("[REPLICATOR] Cache de inicio: " + triggered + " archivos a descargar");
				} catch (Exception e) {
					log.debug("No se pudo ejecutar startup cache: " + e.getMessage());
				}
			} catch (Exception e) {
				log.err("No se pudo activar workspace desde core: " + e.getMessage());
			}
		});
	}

	private void publishCoreEvent(org.q3s.p2p.core.model.Event event) {
		if (event != null) log.debug("[CORE PUBLISH] " + event.type() + " id=" + event.eventId());
		if (p2pMesh != null) p2pMesh.publish(event);
		if (event != null && "file.shared".equals(event.type())) {
			WorkspaceState state = core.currentState();
			if (javax.swing.SwingUtilities.isEventDispatchThread()) {
				applyCoreFilesToVisuals(state);
			} else {
				javax.swing.SwingUtilities.invokeLater(() -> applyCoreFilesToVisuals(state));
			}
		}
	}

	private void sendP2PProtocolEvent(CoreEnvelope envelope) {
		if (envelope == null || envelope.name() == null) return;
		String name = envelope.name();
		if (name.startsWith("__to:")) {
			String rest = name.substring(5);
			int colonIdx = rest.indexOf(':');
			if (colonIdx <= 0) return;
			String targetUserId = rest.substring(0, colonIdx);
			String innerName = rest.substring(colonIdx + 1);
			CoreEnvelope routed = CoreEnvelope.of(innerName, envelope.userId(), envelope.response(), envelope.sequence());
			WebSocket direct = directPeerConnections.get(targetUserId);
			boolean sentDirect = false;
			boolean sentP2P = false;
			try {
				if (direct != null && !direct.isClosed()) {
					log.debug("[P2P SEND] __to:" + targetUserId + " tipo=" + innerName + " via direct (" + directPeerConnections.size() + " direct peers)");
					direct.send(routed.toJsonBase64());
					sentDirect = true;
				}
			} catch (Exception e) {
				log.debug("No se pudo enviar protocolo P2P por direct a " + targetUserId + ": " + e.getMessage());
			}
			try {
				if (p2pNetwork != null) {
					p2pNetwork.sendProtocolEvent(targetUserId, routed);
					sentP2P = true;
				}
			} catch (Exception e) {
				log.debug("No se pudo enviar protocolo P2P por mesh a " + targetUserId + ": " + e.getMessage());
			}
			if (!sentDirect && !sentP2P) {
				log.debug("[P2P SEND] __to:" + targetUserId + " tipo=" + innerName + " NO ROUTE");
			}
			return;
		}
		log.debug("[P2P SEND] broadcast tipo=" + name);
		if (p2pNetwork != null) p2pNetwork.broadcastProtocolEvent(envelope);
		for (WebSocket conn : new ArrayList<>(directPeerConnections.values())) {
			try { if (conn != null && !conn.isClosed()) conn.send(envelope.toJsonBase64()); } catch (Exception ignored) {}
		}
	}

	private void applyCoreStateToVisuals(WorkspaceState state) {
		if (state == null) return;
		if (p2pMesh != null) p2pMesh.applyPeerDiscoveryState(state);
		javax.swing.SwingUtilities.invokeLater(() -> {
			try {
				boolean membershipChanged = applyCoreMembersToVisuals(state);
				boolean chatChanged = false;
			boolean notesChanged = false;
			boolean whiteboardChanged = false;
			boolean membersChanged = applyCorePeerStateToMembers(state) || membershipChanged;
			log.debug("[CORE UI] aplicando estado: chat=" + state.chatMessages().size()
					+ " notas=" + state.notes().size() + " wb=" + state.whiteboardObjects().size()
					+ " archivos=" + state.files().size());
			org.q3s.p2p.core.model.Note sharedNotes = state.notes().get("shared-notes");
			if (sharedNotes != null && notesPane != null) {
				String remoteNotes = sharedNotes.text();
				if (remoteNotes.equals(lastAppliedNotesState)) {
					log.debug("[NOTES] ignorando estado remoto ya aplicado " + notesStateSummary(remoteNotes));
				} else {
					applyRemoteNotes(remoteNotes);
					notesChanged = true;
				}
			}

				if (chatArea != null) {
					int before = appliedCoreChatIds.size();
					for (org.q3s.p2p.core.model.ChatMessage message : state.chatMessages().values()) {
						if (!appliedCoreChatIds.add(message.messageId())) continue;
						ChatMessage visualMessage = new ChatMessage();
						visualMessage.id = "core-" + message.messageId();
						visualMessage.senderName = displayNameForCoreMember(message.authorMemberId());
						visualMessage.text = message.text();
						User sender = User.build(message.authorMemberId());
						sender.setName(visualMessage.senderName);
						appendChatMessage(sender, visualMessage.toPayload());
					}
					int after = appliedCoreChatIds.size();
					if (after > before) {
						chatChanged = true;
						log.info("[CORE UI] chat: " + (after - before) + " nuevos, total=" + after);
					}
				}

				if (whiteboardCanvas != null) {
					String whiteboardState = coreWhiteboardState(state);
					if (!whiteboardState.equals(lastAppliedCoreWhiteboardState)
							&& !whiteboardState.equals(whiteboardCanvas.serialize())) {
						lastAppliedCoreWhiteboardState = whiteboardState;
						log.info("[CORE UI] pizarra: aplicando " + state.whiteboardObjects().size() + " objetos");
						whiteboardCanvas.applyState(whiteboardState);
						whiteboardChanged = true;
					}
				}

				applyCoreFilesToVisuals(state);
				if (chatChanged) markTabIfInactive("Chat");
				if (notesChanged) markTabIfInactive("Notas");
				if (whiteboardChanged) markTabIfInactive("Pizarra");
				if (membersChanged) markTabIfInactive("Miembros");
				logUiSnapshot("applyCoreStateToVisuals");
			} catch (Exception e) {
				log.debug("No se pudo aplicar estado core a UI: " + e.getMessage());
			}
		});
	}

	private boolean applyCoreMembersToVisuals(WorkspaceState state) {
		boolean changed = false;
		for (org.q3s.p2p.core.model.Member coreMember : state.authorizedMembers().values()) {
			User member = coreMember.memberId().equals(user.getId()) ? user : knownMembers.get(coreMember.memberId());
			if (member == null) {
				member = User.build(coreMember.memberId());
				knownMembers.put(member.getId(), member);
				if (!member.equals(user) && !remoteUsers.contains(member)) remoteUsers.add(member);
				changed = true;
			}
			String displayName = coreMember.displayName();
			if (displayName != null && !displayName.isBlank() && !displayName.equals(member.getName())) {
				member.setName(displayName);
				changed = true;
			}
		}
		return changed;
	}

	private boolean applyCorePeerStateToMembers(WorkspaceState state) {
		boolean changed = false;
		if (!corePeerUrls.equals(state.peerUrls())) {
			corePeerUrls.clear();
			corePeerUrls.putAll(state.peerUrls());
			changed = true;
		}
		if (!corePeerConnections.equals(state.peerConnections())) {
			corePeerConnections.clear();
			for (Map.Entry<String, Set<String>> entry : state.peerConnections().entrySet()) {
				corePeerConnections.put(entry.getKey(), new java.util.LinkedHashSet<>(entry.getValue()));
			}
			changed = true;
		}

		for (Map.Entry<String, String> entry : corePeerUrls.entrySet()) {
			User member = entry.getKey().equals(user.getId()) ? user : knownMembers.get(entry.getKey());
			if (member == null) {
				member = User.build(entry.getKey());
				member.setName(displayNameForCoreMember(entry.getKey()));
				knownMembers.put(entry.getKey(), member);
			}
			if (entry.getValue() != null && !entry.getValue().equals(member.getPeerUrl())) {
				member.setPeerUrl(entry.getValue());
				changed = true;
			}
		}
		refreshMembersTable();
		return changed;
	}

	private void applyCoreFilesToVisuals(WorkspaceState state) {
		boolean changed = false;
		for (FileMetadata metadata : state.files().values()) {
			String memberId = metadata.sharedBy();
			if (memberId == null || memberId.isBlank()) continue;
			String hash = metadata.hash();
			if (hash == null || hash.isBlank()) continue;
			fileRegistry.putIfAbsent(hash, new FileRegistryEntry(
					metadata.name(), metadata.size(), System.currentTimeMillis(),
					metadata.fileId(), hash, memberId));
			filePeers.computeIfAbsent(hash, k -> new HashSet<>()).add(memberId);
			changed = true;

			if (metadata.chatAttachment() && chatArea != null && appliedCoreChatFileIds.add(metadata.fileId())) {
				User sender = memberId.equals(user.getId()) ? user : knownMembers.get(memberId);
				if (sender == null) {
					sender = User.build(memberId);
					sender.setName(displayNameForCoreMember(memberId));
				}
				QFile qfile = new QFile();
				qfile.setName(metadata.name());
				qfile.setSize(metadata.size());
				qfile.setDate(System.currentTimeMillis());
				qfile.setRelativePath(metadata.name());
				qfile.setOperation(QFile.OPERATION_DOWNLOAD);
				qfile.setMd5("core:" + metadata.fileId());
				if (!memberId.equals(user.getId())) {
					qfile.setTransferId(UUIDUtils.generate());
				}
				String localPath = memberId.equals(user.getId()) ? new File(getSessionFilesDir(), metadata.name()).getAbsolutePath() : null;
				String linkId = appendChatFileMessage(sender, qfile, localPath);
				if (!memberId.equals(user.getId())) {
					qfile.setOwner(sender);
					pendingChatDownloads.put(linkId, metadata.fileId());
					markTabIfInactive("Chat");
				}
			}
		}
		if (changed) {
			refreshArchivosTable();
			refreshTables();
			markTabIfInactive("Archivos");
			logUiSnapshot("applyCoreFilesToVisuals");
		}
	}

	private boolean isCoreMetadataFile(QFile file) {
		return file != null && file.getMd5() != null && file.getMd5().startsWith("core:");
	}

	private String coreFileId(QFile file) {
		if (!isCoreMetadataFile(file)) return null;
		String id = file.getMd5().substring("core:".length()).trim();
		return id.isEmpty() ? null : id;
	}

	private QFile qFileForCoreFileId(String fileId) {
		if (fileId == null || fileId.isBlank() || core == null) return null;
		for (FileMetadata metadata : core.currentState().files().values()) {
			if (!fileId.equals(metadata.fileId())) continue;
			QFile qfile = new QFile();
			qfile.setName(metadata.name());
			qfile.setSize(metadata.size());
			qfile.setDate(System.currentTimeMillis());
			qfile.setRelativePath(metadata.name());
			qfile.setMd5("core:" + metadata.fileId());
			String ownerId = metadata.sharedBy();
			User owner = ownerId != null && ownerId.equals(user.getId()) ? user : knownMembers.get(ownerId);
			if (owner == null && ownerId != null) {
				owner = User.build(ownerId);
				owner.setName(displayNameForCoreMember(ownerId));
			}
			qfile.setOwner(owner);
			return qfile;
		}
		return null;
	}

	private void applyCoreEventIncremental(org.q3s.p2p.core.model.Event event) {
		if (whiteboardCanvas == null || event == null) return;
		String type = event.type();
		String objectId = String.valueOf(event.payload().getOrDefault("object_id", ""));
		String operation = String.valueOf(event.payload().getOrDefault("operation", ""));
		javax.swing.SwingUtilities.invokeLater(() -> {
			try {
				boolean changed = false;
				switch (type) {
					case "whiteboard.object.added" -> { whiteboardCanvas.applyAction(
							whiteboardCanvas.actionPayload("add", objectId, operation)); changed = true; }
					case "whiteboard.object.moved" -> { whiteboardCanvas.applyAction(
							whiteboardCanvas.actionPayload("update", objectId, operation)); changed = true; }
					case "whiteboard.object.deleted" -> { whiteboardCanvas.applyAction(
							whiteboardCanvas.actionPayload("delete", objectId, null)); changed = true; }
					case "whiteboard.cleared" -> { whiteboardCanvas.applyAction(
							whiteboardCanvas.actionPayload("clear", "", null)); changed = true; }
					case "whiteboard.stroke.added" -> changed = true;
				}
				if (changed) markTabIfInactive("Pizarra");
			} catch (Exception e) {
				log.debug("No se pudo aplicar evento pizarra incremental: " + e.getMessage());
			}
		});
	}

	private String coreWhiteboardState(WorkspaceState state) {
		StringBuilder out = new StringBuilder("QWBSTATE1");
		for (org.q3s.p2p.core.model.WhiteboardStroke stroke : state.strokes().values()) {
			List<int[]> points = stroke.points();
			for (int i = 1; i < points.size(); i++) {
				int[] a = points.get(i - 1);
				int[] b = points.get(i);
				if (a.length < 2 || b.length < 2) continue;
				String color = stroke.color() == null || stroke.color().isBlank() ? "#000000" : stroke.color();
				String op = "L|" + a[0] + "," + a[1] + "," + b[0] + "," + b[1] + "|" + color + "|" + Math.max(1, stroke.width());
				out.append('\n').append(stroke.strokeId()).append('_').append(i).append('|')
						.append(Base64.getEncoder().encodeToString(op.getBytes(StandardCharsets.UTF_8)));
			}
		}
		for (Map.Entry<String, String> entry : state.whiteboardObjects().entrySet()) {
			String op = entry.getValue() == null ? "" : entry.getValue();
			out.append('\n').append(entry.getKey()).append('|')
					.append(Base64.getEncoder().encodeToString(op.getBytes(StandardCharsets.UTF_8)));
		}
		return out.toString();
	}

	private String displayNameForCoreMember(String memberId) {
		User known = knownMembers.get(memberId);
		if (known != null && known.getName() != null && !known.getName().isBlank()) return known.getName();
		return memberId != null ? memberId : "Usuario";
	}

	private void addUserToRemoteList(User user) {
		if (user.equals(this.user)) return;
		boolean newMember = !remoteUsers.contains(user);
		user.setOnline(true);
		trackMember(user, true);
		if (!core.currentState().isAuthorized(user.getId())) {
			try {
				core.recordJoinRequest(user.getId(), user.getName(), "swing-device", user.getId());
			} catch (Exception e) {
				log.debug("No se pudo registrar join request remoto en core: " + e.getMessage());
			}
		}
		int idx = remoteUsers.indexOf(user);
		if (idx != -1) {
			User us = remoteUsers.get(idx);
			List<QFile> coreFiles = new ArrayList<>();
			for (QFile existing : us.getFiles()) {
				if (isCoreMetadataFile(existing)) coreFiles.add(existing);
			}
			us.copy(user);
			us.setOnline(true);
			List<QFile> merged = new ArrayList<>(us.getFiles());
			for (QFile coreFile : coreFiles) {
				boolean alreadyPresent = merged.stream()
						.anyMatch(f -> f.getName().equals(coreFile.getName()) && f.getSize() == coreFile.getSize());
				if (!alreadyPresent) merged.add(coreFile);
			}
			us.setFiles(merged);
		} else {
			remoteUsers.add(user);
		}
		if (disconnectedChatUserNames.remove(user.getName())) {
			String name = user.getName() != null ? user.getName() : "Usuario";
			appendChatSystemMessage(I18n.get("chat.userReconnected", name), new Color(0, 128, 0));
			markTabIfInactive("Chat");
		}
		connectP2PTo(user);
		refreshMembersTable();
		if (newMember) markTabIfInactive("Miembros");
	}

	private void connectP2PTo(User peer) {
		if (p2pMesh != null) {
			p2pMesh.peerAppeared(peer);
			javax.swing.Timer timer = new javax.swing.Timer(1200, e -> {
				refreshMembersTable();
				markTabIfInactive("Miembros");
			});
			timer.setRepeats(false);
			timer.start();
		}
	}

	private void trackMember(User member, boolean online) {
		if (member == null || member.getId() == null) return;
		User stored = knownMembers.get(member.getId());
		boolean changed = stored == null || stored.isOnline() != online
				|| (member.getPeerUrl() != null && !member.getPeerUrl().equals(stored.getPeerUrl()))
				|| (member.getName() != null && !member.getName().equals(stored.getName()));
		if (stored == null) {
			stored = User.build(member.getId());
			knownMembers.put(member.getId(), stored);
		}
		stored.copy(member);
		stored.setOnline(online);
		if (online && !memberConnectedAt.containsKey(member.getId())) {
			memberConnectedAt.put(member.getId(), System.currentTimeMillis());
		}
		refreshMembersTable();
		if (changed && !member.equals(user)) markTabIfInactive("Miembros");
	}

	private User findKnownUser(User candidate) {
		if (candidate == null) return null;
		int idx = remoteUsers.indexOf(candidate);
		if (idx >= 0) return remoteUsers.get(idx);
		return candidate;
	}

	private void loadMembersTab() {
		trackMember(user, true);
		if (membersContainerPanel != null) {
			refreshMembersTable();
			return;
		}

		membersTable = new JTable();
		membersTable.setFillsViewportHeight(true);
		membersTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		membersContainerPanel = new JPanel(new BorderLayout(4, 4));
		membersContainerPanel.add(new JScrollPane(membersTable), BorderLayout.CENTER);
		refreshMembersTable();
		insertSystemTab("Miembros", groupIcon(), membersContainerPanel, I18n.get("tooltip.tab.members"));
	}

	private void refreshMembersTable() {
		if (membersTable == null) return;
		trackMemberWithoutRefresh(user, true);
		String[] columns = {I18n.get("col.members.name"), I18n.get("col.members.status"), I18n.get("col.members.connectedSince"),
				I18n.get("col.members.peers"), I18n.get("col.members.connectedWith"), I18n.get("col.members.publicUrl")};
		DefaultTableModel model = new DefaultTableModel(columns, 0) {
			@Override public boolean isCellEditable(int row, int column) { return false; }
		};
		Set<String> localConnections = p2pMesh != null ? p2pMesh.connectedPeers() : Set.of();
		for (User member : knownMembers.values()) {
			boolean isSelf = member.equals(user);
			boolean connectedToMe = localConnections.contains(member.getId())
					|| isDirectPeerConnected(member.getId());
			boolean online = isSelf || connectedToMe;
			if (member.isOnline() != online) member.setOnline(online);
			if (online && !memberConnectedAt.containsKey(member.getId())) {
				memberConnectedAt.put(member.getId(), System.currentTimeMillis());
			}
			Set<String> distributedConnections = new java.util.LinkedHashSet<>(corePeerConnections.getOrDefault(member.getId(), Set.of()));
			if (isSelf) distributedConnections.addAll(localConnections);
			if (!isSelf && connectedToMe) distributedConnections.add(user.getId());
			if (!online) distributedConnections.clear();
			String connectedWith = connectedPeerNames(distributedConnections);
			String peerCount = String.valueOf(distributedConnections.size());
			model.addRow(new Object[] {
					member.getName() != null ? member.getName() : member.getId(),
					online ? I18n.get("members.connected") : I18n.get("members.disconnected"),
					formatMemberConnectedAt(member.getId()),
					peerCount,
					connectedWith,
					member.getPeerUrl() != null ? member.getPeerUrl() : ""
			});
		}
		membersTable.setModel(model);
		if (membersTable.getColumnModel().getColumnCount() >= 6) {
			membersTable.getColumnModel().getColumn(0).setPreferredWidth(140);
			membersTable.getColumnModel().getColumn(1).setPreferredWidth(100);
			membersTable.getColumnModel().getColumn(2).setPreferredWidth(140);
			membersTable.getColumnModel().getColumn(3).setPreferredWidth(60);
			membersTable.getColumnModel().getColumn(4).setPreferredWidth(180);
			membersTable.getColumnModel().getColumn(5).setPreferredWidth(320);
		}
		logUiSnapshot("refreshMembersTable");
	}

	private String connectedPeerNames(Set<String> peerIds) {
		if (peerIds == null || peerIds.isEmpty()) return "";
		List<String> names = new ArrayList<>();
		for (String peerId : peerIds) names.add(displayNameForCoreMember(peerId));
		return String.join(", ", names);
	}

	private void trackMemberWithoutRefresh(User member, boolean online) {
		if (member == null || member.getId() == null) return;
		User stored = knownMembers.get(member.getId());
		if (stored == null) {
			stored = User.build(member.getId());
			knownMembers.put(member.getId(), stored);
		}
		stored.copy(member);
		stored.setOnline(online);
		if (online && !memberConnectedAt.containsKey(member.getId())) {
			memberConnectedAt.put(member.getId(), System.currentTimeMillis());
		}
	}

	private String formatMemberConnectedAt(String userId) {
		Long connectedAt = memberConnectedAt.get(userId);
		if (connectedAt == null || connectedAt <= 0) return "";
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(connectedAt));
	}

	private void ensureArchivosTab() {
		if (archivosTab != null) return;

		archivosTab = new TabListFile(wk, this.user, this, 0);
		archivosTab.setName("Archivos");

		JPanel panel = new JPanel(new BorderLayout(4, 4));
		panel.add(archivosTab, BorderLayout.CENTER);
		TransferHandler archivosDropHandler = new TransferHandler() {
			@Override public boolean canImport(TransferSupport support) {
				return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
			}

			@Override public boolean importData(TransferSupport support) {
				if (!canImport(support)) return false;
				try {
					@SuppressWarnings("unchecked")
					List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					return importFilesToSession(files, "Archivos");
				} catch (Exception e) {
					log.err("Error al recibir archivo arrastrado a Archivos: " + e.getMessage());
					return false;
				}
			}
		};
		panel.setTransferHandler(archivosDropHandler);
		archivosTab.setTransferHandler(archivosDropHandler);
		archivosTab.getjTable1().setTransferHandler(archivosDropHandler);
		archivosContainerPanel = panel;

		refreshArchivosTable();
	}

	private void showArchivosTab() {
		ensureArchivosTab();
		if (findTabByTitle("Archivos") < 0) {
			insertSystemTab("Archivos", boardIcon(), archivosContainerPanel, I18n.get("tooltip.tab.files"));
		}
	}

	private boolean isDirectPeerConnected(String forPeerId) {
		WebSocket conn = directPeerConnections.get(forPeerId);
		return conn != null && !conn.isClosed();
	}

	private Set<String> activeFilePeers(Set<String> peers) {
		if (peers == null || peers.isEmpty()) return Set.of();
		Set<String> active = new LinkedHashSet<>();
		Set<String> connected = p2pMesh != null ? p2pMesh.connectedPeers() : Set.of();
		for (String peerId : peers) {
			if (peerId == null || peerId.isBlank()) continue;
			if (peerId.equals(user.getId())) { active.add(peerId); continue; }
			if (connected.contains(peerId) || isDirectPeerConnected(peerId)) { active.add(peerId); }
		}
		return active;
	}

	private void refreshArchivosTable() {
		if (archivosTab == null) return;
		List<FileTableModel.FileTableRow> rows = new ArrayList<>();
		for (Map.Entry<String, FileRegistryEntry> entry : fileRegistry.entrySet()) {
			FileRegistryEntry e = entry.getValue();
			Set<String> peers = filePeers.getOrDefault(e.hash(), Set.of());
			Set<String> activePeers = activeFilePeers(peers);
			boolean isLocal = activePeers.contains(user.getId());
			User owner;
			if (isLocal) {
				owner = user;
			} else {
				String firstPeerId = e.firstSharedBy();
				owner = knownMembers.get(firstPeerId);
				if (owner == null) {
					owner = User.build(firstPeerId);
					owner.setName(displayNameForCoreMember(firstPeerId));
				}
			}
			rows.add(new FileTableModel.FileTableRow(e.name(), e.size(), e.date(), e.fileId(), e.hash(), owner, activePeers.size()));
		}
		FileTableModel ftm;
		if (archivosTab.getjTable1().getModel() instanceof FileTableModel existing) {
			existing.setRows(rows);
			ftm = existing;
		} else {
			ftm = new FileTableModel(rows);
			archivosTab.getjTable1().setModel(ftm);
		}
		archivosTab.getjTable1().setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		setFixedColumnWidth(0, 28);
		setFixedColumnWidth(2, 80);
		setFixedColumnWidth(3, 125);
		setFixedColumnWidth(4, 105);
		setFixedColumnWidth(5, 65);
		logUiSnapshot("refreshArchivosTable");
	}

	private void setFixedColumnWidth(int column, int width) {
		try {
			archivosTab.getjTable1().getColumnModel().getColumn(column).setPreferredWidth(width);
			archivosTab.getjTable1().getColumnModel().getColumn(column).setMinWidth(width);
			archivosTab.getjTable1().getColumnModel().getColumn(column).setMaxWidth(width);
		} catch (Exception e) {
		}
	}

	private void logUiSnapshot(String reason) {
		if (!Config.UI_VERBOSE_LOG || log == null) return;
		try {
			StringBuilder out = new StringBuilder("[UI VERBOSE] reason=").append(reason)
					.append(" user=").append(user.getName()).append("(").append(user.getId()).append(")")
					.append(" wk=").append(wk != null ? wk.getId() : "-")
					.append(" selectedTab=").append(currentTabTitle()).append('\n');
			out.append("[UI VERBOSE] members=").append(uiMembersSnapshot()).append('\n');
			out.append("[UI VERBOSE] chat=").append(uiChatSnapshot()).append('\n');
			out.append("[UI VERBOSE] files=").append(uiFilesSnapshot()).append('\n');
			out.append("[UI VERBOSE] notes=").append(uiNotesSnapshot()).append('\n');
			out.append("[UI VERBOSE] whiteboard=").append(uiWhiteboardSnapshot());
			log.debug(out.toString());
		} catch (Exception e) {
			log.debug("[UI VERBOSE] snapshot error: " + e.getMessage());
		}
	}

	private String currentTabTitle() {
		try {
			int idx = view.getjTabbedPane().getSelectedIndex();
			return idx >= 0 ? view.getjTabbedPane().getTitleAt(idx) : "-";
		} catch (Exception e) {
			return "-";
		}
	}

	private String uiMembersSnapshot() {
		if (membersTable == null) return "tab-not-loaded known=" + knownMembers.size();
		StringBuilder out = new StringBuilder("rows=").append(membersTable.getRowCount()).append(" [");
		for (int r = 0; r < membersTable.getRowCount(); r++) {
			if (r > 0) out.append("; ");
			out.append(rowValue(membersTable, r, 0)).append('|')
					.append(rowValue(membersTable, r, 1)).append('|')
					.append("peers=").append(rowValue(membersTable, r, 3)).append('|')
					.append("with=").append(rowValue(membersTable, r, 4)).append('|')
					.append(rowValue(membersTable, r, 5));
		}
		return out.append(']').toString();
	}

	private String uiChatSnapshot() {
		if (chatArea == null) return "tab-not-loaded messages=" + chatMessages.size();
		String text = chatArea.getText();
		return "chars=" + text.length() + " messages=" + chatMessages.size()
				+ " links=" + chatFileLinks.size() + " text='" + compact(text, 500) + "'";
	}

	private String uiFilesSnapshot() {
		if (archivosTab == null) return "tab-not-loaded remoteUsers=" + remoteUsers.size();
		JTable table = archivosTab.getjTable1();
		StringBuilder out = new StringBuilder("rows=").append(table.getRowCount()).append(" registry=")
				.append(fileRegistry.size()).append(" [");
		for (int r = 0; r < table.getRowCount(); r++) {
			if (r > 0) out.append("; ");
			out.append(rowValue(table, r, 1)).append('|')
					.append(rowValue(table, r, 2)).append('|')
					.append(rowValue(table, r, 3)).append('|')
					.append("peers=").append(rowValue(table, r, 5));
		}
		return out.append(']').toString();
	}

	private String uiNotesSnapshot() {
		if (notesPane == null) return "tab-not-loaded";
		return "chars=" + notesPane.getDocument().getLength() + " text='" + compact(notesPane.getText(), 300) + "'";
	}

	private String uiWhiteboardSnapshot() {
		if (whiteboardCanvas == null) return "tab-not-loaded";
		String state = whiteboardCanvas.serialize();
		int operations = state == null || state.isBlank() ? 0 : Math.max(0, state.split("\n").length - 1);
		return "ops=" + operations + " state='" + compact(state, 300) + "'";
	}

	private Object rowValue(JTable table, int row, int col) {
		try {
			return col < table.getColumnCount() ? table.getValueAt(row, col) : "";
		} catch (Exception e) {
			return "?";
		}
	}

	private String compact(String value, int max) {
		if (value == null) return "";
		String compact = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
		return compact.length() > max ? compact.substring(0, Math.max(0, max - 3)) + "..." : compact;
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
		chatReplyLabel = new JLabel(I18n.get("chat.replyPrefix"));
		chatReplyLabel.setForeground(Color.GRAY);
		chatReplyLabel.setVisible(false);
		JButton sendButton = new JButton(I18n.get("chat.send"));
		sendButton.setDefaultCapable(false);
		sendButton.setFocusable(false);
		JButton fileButton = new JButton(attachmentIcon());
		fileButton.setToolTipText(I18n.get("tooltip.attachFile"));
		fileButton.setFocusable(false);
		java.awt.Dimension btnSize = new java.awt.Dimension(chatInput.getPreferredSize().height, chatInput.getPreferredSize().height);
		fileButton.setPreferredSize(btnSize);
		fileButton.setMaximumSize(btnSize);
		fileButton.setMinimumSize(btnSize);
		Runnable send = () -> sendChatMessage();
		chatInput.addActionListener(e -> send.run());
		installChatInputPasteImageBinding(chatInput);
		fileButton.addActionListener(e -> chooseAndSendChatFile());
		sendButton.addActionListener(e -> send.run());

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
		buttonPanel.add(fileButton);
		buttonPanel.add(sendButton);

		JPanel inputPanel = new JPanel(new BorderLayout(4, 2));
		inputPanel.add(chatReplyLabel, BorderLayout.WEST);
		inputPanel.add(chatInput, BorderLayout.CENTER);
		inputPanel.add(buttonPanel, BorderLayout.EAST);

		JScrollPane scroll = new JScrollPane(chatArea);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scroll.setMinimumSize(new java.awt.Dimension(0, 50));

		JPanel panel = new JPanel(new BorderLayout(4, 4));
		panel.setMinimumSize(new java.awt.Dimension(0, 40));
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
		insertSystemTab("Chat", chatIcon(), chatContainerPanel, I18n.get("tooltip.tab.chat"));
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

		try {
			File targetFile = copyFileToSessionFiles(sourceFile);

			QFile qfile = new QFile();
			qfile.setName(targetFile.getName());
			qfile.setSize(targetFile.length());
			qfile.setDate(targetFile.lastModified());
			qfile.setRelativePath(targetFile.getName());
			qfile.setOperation(QFile.OPERATION_DOWNLOAD);

			org.q3s.p2p.core.model.Event event = core.shareChatFile(targetFile.toPath());
			Object fileId = event.payload().get("file_id");
			if (fileId != null) {
				qfile.setMd5("core:" + fileId);
				appliedCoreChatFileIds.add(String.valueOf(fileId));
			}
			publishCoreEvent(event);
			appendChatFileMessage(user, qfile, targetFile.getAbsolutePath());
			refreshArchivosTable();
			refreshTables();
		} catch (Exception e) {
			log.err("Error al enviar archivo por chat: " + e.getMessage());
		}
	}

	private void installChatInputPasteImageBinding(JTextField input) {
		javax.swing.ActionMap actionMap = input.getActionMap();
		javax.swing.InputMap inputMap = input.getInputMap(JComponent.WHEN_FOCUSED);
		String pasteActionKey = "chat-paste-image-or-text";
		inputMap.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, java.awt.event.InputEvent.CTRL_DOWN_MASK), pasteActionKey);
		inputMap.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_INSERT, java.awt.event.InputEvent.SHIFT_DOWN_MASK), pasteActionKey);
		actionMap.put(pasteActionKey, new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				java.awt.datatransfer.Clipboard cb = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
				try {
					java.awt.datatransfer.Transferable trans = cb.getContents(null);
					if (trans != null && trans.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor)) {
						sendChatImageFromClipboard();
						return;
					}
				} catch (Exception ex) {
					log.debug("Clipboard image check failed: " + ex.getMessage());
				}
				input.paste();
			}
		});
	}

	private void sendChatImageFromClipboard() {
		new Thread("chat-image-paste") {
			@Override
			public void run() {
				try {
					java.awt.datatransfer.Clipboard cb = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
					java.awt.datatransfer.Transferable trans = cb.getContents(null);
					if (trans == null || !trans.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor)) return;
					java.awt.Image img = (java.awt.Image) trans.getTransferData(java.awt.datatransfer.DataFlavor.imageFlavor);
					BufferedImage bi = toBufferedImage(img);
					if (bi == null) {
						log.err("No se pudo convertir la imagen del portapapeles");
						return;
					}
					String name = "clip-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date()) + ".png";
					File target = copyImageBytesToSessionFiles(bi, name);
					sendChatFile(target);
				} catch (Exception e) {
					log.err("Error al pegar imagen en chat: " + e.getMessage());
				}
			}
		}.start();
	}

	private static BufferedImage toBufferedImage(java.awt.Image img) {
		if (img == null) return null;
		if (img instanceof BufferedImage) return (BufferedImage) img;
		int w = Math.max(1, img.getWidth(null));
		int h = Math.max(1, img.getHeight(null));
		BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		try {
			g.drawImage(img, 0, 0, null);
		} finally {
			g.dispose();
		}
		return bi;
	}

	private File copyImageBytesToSessionFiles(BufferedImage bi, String desiredName) throws Exception {
		File sessionFilesDir = getSessionFilesDir();
		sessionFilesDir.mkdirs();
		String targetPath = uniqueFilePath(new File(sessionFilesDir, desiredName).getAbsolutePath());
		File targetFile = new File(targetPath);
		try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(targetFile.toPath())) {
			javax.imageio.ImageIO.write(bi, "png", out);
		}
		return targetFile;
	}

	private boolean importFilesToSession(List<File> files, String sourceLabel) {
		if (files == null || files.isEmpty()) return false;
		int copied = 0;
		for (File file : files) {
			if (file == null || !file.isFile()) continue;
			try {
				copyFileToSessionFiles(file);
				copied++;
			} catch (Exception e) {
				log.err("No se pudo importar archivo '" + file.getName() + "': " + e.getMessage());
			}
		}
		if (copied > 0) {
			refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
			log.info(copied + " archivo(s) importado(s) desde " + sourceLabel + " a " + getSessionFilesDir().getAbsolutePath());
			return true;
		}
		return false;
	}

	private File copyFileToSessionFiles(File sourceFile) throws Exception {
		File sessionFilesDir = getSessionFilesDir();
		sessionFilesDir.mkdirs();
		String targetPath = uniqueFilePath(new File(sessionFilesDir, sourceFile.getName()).getAbsolutePath());
		File targetFile = new File(targetPath);
		Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
		return targetFile;
	}

	private void sendChatMessage() {
		if (chatInput == null) {
			return;
		}
		String message = chatInput.getText().trim();
		if (message.isEmpty()) {
			return;
		}
		chatInput.setText("");
		ChatMessage chatMessage = new ChatMessage();
		chatMessage.senderName = user.getName();
		chatMessage.text = message;
		if (replyingToChatMessage != null) {
			chatMessage.replyToName = replyingToChatMessage.senderName;
			chatMessage.replyToText = replyingToChatMessage.text;
			replyingToChatMessage = null;
			chatInput.setToolTipText(null);
			if (chatReplyLabel != null) chatReplyLabel.setVisible(false);
		}
		try {
			org.q3s.p2p.core.model.Event event = core.sendChatMessage(message);
			Object messageId = event.payload().get("message_id");
			if (messageId != null) {
				String coreMessageId = String.valueOf(messageId);
				chatMessage.id = "core-" + coreMessageId;
				appliedCoreChatIds.add(coreMessageId);
			} else {
				chatMessage.id = UUIDUtils.generate();
			}
			appendChatMessage(user, chatMessage.toPayload());
			log.debug("[CHAT] enviando: " + chatMessage.text);
			publishCoreEvent(event);
		} catch (Exception e) {
			log.debug("Chat core no disponible: " + e.getMessage());
			chatMessage.id = UUIDUtils.generate();
			appendChatMessage(user, chatMessage.toPayload());
		}
	}

	private void appendChatMessage(User sender, String message) {
		if (chatArea == null || message == null) {
			return;
		}
		ChatMessage chatMessage = ChatMessage.fromPayload(message, sender);
		if (chatMessage.id != null && !chatMessage.id.isEmpty() && chatMessages.containsKey(chatMessage.id)) {
			log.debug("Mensaje de chat duplicado ignorado: " + chatMessage.id);
			return;
		}
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
		logUiSnapshot("appendChatMessage:" + chatMessage.id);
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
		registerChatFileLink(linkId, qfile, localPath);
		if (qfile.getTransferId() != null) {
			chatTransferLinks.put(qfile.getTransferId(), linkId);
		}
		SimpleAttributeSet link = new SimpleAttributeSet();
		StyleConstants.setForeground(link, new Color(0, 85, 170));
		StyleConstants.setUnderline(link, true);
		link.addAttribute("chatLink", linkId);
		appendChatText(qfile.getName(), link, alignment);
		appendChatText(System.lineSeparator(), normal, alignment);
		logUiSnapshot("appendChatFileMessage:" + qfile.getName());
		return linkId;
	}

	private void registerChatFileLink(String linkId, QFile qfile, String localPath) {
		if (linkId == null || qfile == null) return;
		if (localPath != null) {
			chatFileLinks.put(linkId, localPath);
		} else {
			chatFileLinks.put(linkId, "");
		}
		String fileId = coreFileId(qfile);
		if (fileId != null) {
			pendingChatDownloads.put(linkId, fileId);
		}
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
		JMenuItem reply = new JMenuItem(I18n.get("chat.reply"));
		reply.addActionListener(ev -> {
			replyingToChatMessage = message;
			chatInput.setText("");
			chatInput.setToolTipText(I18n.get("chat.responding") + " " + safeChatPreview(message.text));
			if (chatReplyLabel != null) chatReplyLabel.setVisible(true);
			chatInput.requestFocusInWindow();
		});
		JMenuItem pin = new JMenuItem(I18n.get("chat.pin", "Fijar"));
		pin.addActionListener(ev -> applyPinnedChatMessage(message));
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
		if (path != null && !path.isBlank() && new File(path).isFile()) {
			try {
				exec.open(path);
			} catch (Exception e) {
				log.err("No se pudo abrir el archivo adjunto: " + e.getMessage());
			}
			return;
		}
		String fileId = pendingChatDownloads.get(linkId);
		if (fileId == null || fileId.isBlank()) {
			log.info("El archivo aun no esta disponible para descarga");
			return;
		}
		QFile qfile = qFileForCoreFileId(fileId);
		if (qfile != null) {
			qfile.setOperation(QFile.OPERATION_OPEN);
			qfile.setTransferId(UUIDUtils.generate());
			transferPendingOpenLinks.put(qfile.getTransferId(), linkId);
			registerActiveTransfer(qfile);
			updateTransferProgress(qfile.getTransferId(), I18n.get("transfer.requesting") + " " + qfile.getName(), 0, 1);
			if (!requestCoreChunkDownload(qfile)) {
				log.err("No se pudieron encontrar chunks para " + qfile.getName());
				finishTransferWithError(qfile.getTransferId(), I18n.get("transfer.error") + " " + qfile.getName());
			}
			return;
		}
		log.info("El archivo aun no esta disponible para descarga");
	}

	private void handleChatFileEvent(CoreEnvelope envelope) {
		log.debug("Evento legacy de archivo de chat ignorado (usar core event): " + (envelope != null ? envelope.name() : "null"));
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
		String displayTitle = getTabDisplayTitle(title);
		int idx = findTabByTitle("Log");
		if (idx < 0) {
			idx = findTabByTitle("Configuración");
		}
		if (idx < 0) {
			view.getjTabbedPane().addTab(displayTitle, icon, component, tooltip);
		} else {
			view.getjTabbedPane().insertTab(displayTitle, icon, component, tooltip, idx);
		}
	}

	private static String getTabDisplayTitle(String canonical) {
		switch (canonical) {
			case "Ayuda": return I18n.get("tab.help");
			case "Chat": return I18n.get("tab.chat");
			case "Pizarra": return I18n.get("tab.whiteboard");
			case "Notas": return I18n.get("tab.notes");
			case "Miembros": return I18n.get("tab.members");
			case "Archivos": return I18n.get("tab.files");
			case "Log": return I18n.get("tab.log");
			case "Configuración": return I18n.get("tab.config");
			default: return canonical;
		}
	}

	private int findTabByTitle(String title) {
		String[] alternatives = getTabAlternatives(title);
		for (String alt : alternatives) {
			String cleanAlt = cleanTabTitle(alt);
			for (int i = 0; i < view.getjTabbedPane().getTabCount(); i++) {
				if (cleanAlt.equals(cleanTabTitle(view.getjTabbedPane().getTitleAt(i)))) {
					return i;
				}
			}
		}
		return -1;
	}

	private static String[] getTabAlternatives(String title) {
		switch (title) {
			case "Configuración": case "Configuration":
				return new String[]{"Configuración", "Configuration"};
			case "Ayuda": case "Help":
				return new String[]{"Ayuda", "Help"};
			case "Pizarra": case "Whiteboard":
				return new String[]{"Pizarra", "Whiteboard"};
			case "Notas": case "Notes":
				return new String[]{"Notas", "Notes"};
			case "Miembros": case "Members":
				return new String[]{"Miembros", "Members"};
			case "Archivos": case "Files":
				return new String[]{"Archivos", "Files"};
			default:
				return new String[]{title};
		}
	}

	private String cleanTabTitle(String title) {
		if (title == null) return null;
		if (title.startsWith("* ")) return title.substring(2);
		return title.startsWith("*") ? title.substring(1) : title;
	}

	private void markTabIfInactive(String title) {
		int idx = findTabByTitle(title);
		if (idx < 0) return;
		if (idx == view.getjTabbedPane().getSelectedIndex() && view.isActive()) return;
		String cleanTitle = cleanTabTitle(view.getjTabbedPane().getTitleAt(idx));
		if (markedTabs.add(cleanTitle)) {
			view.getjTabbedPane().setTitleAt(idx, "*" + cleanTitle);
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

	private void refreshTabTitles() {
		renameTabByAnyTitle(I18n.get("tab.files"), "Archivos", "Files");
		renameTabByAnyTitle(I18n.get("tab.chat"), "Chat");
		renameTabByAnyTitle(I18n.get("tab.whiteboard"), "Pizarra", "Whiteboard");
		renameTabByAnyTitle(I18n.get("tab.notes"), "Notas", "Notes");
		renameTabByAnyTitle(I18n.get("tab.members"), "Miembros", "Members");
		renameTabByAnyTitle(I18n.get("tab.help"), "Ayuda", "Help");
		renameTabByAnyTitle(I18n.get("tab.log"), "Log");
		renameTabByAnyTitle(I18n.get("tab.config"), "Configuración", "Configuration");
	}

	private void renameTabByAnyTitle(String newTitle, String... possibleTitles) {
		for (String t : possibleTitles) {
			int idx = findTabByTitle(t);
			if (idx >= 0) {
				view.getjTabbedPane().setTitleAt(idx, newTitle);
				return;
			}
		}
	}

	private void refreshLanguageTexts() {
		view.applyI18nTexts();
		refreshTabTitles();
		refreshHelpTabContent();
		installConfigEnhancements();
		openWorkDirLabel.setText(I18n.get("config.openWorkDir"));
		refreshAllFileTableColumns();
		refreshAllTooltips();
		view.revalidate();
		view.repaint();
	}

	private void refreshAllFileTableColumns() {
		searchAndRefreshFileTables(view.getjTabbedPane());
	}

	private JPopupMenu createJoinTextPopupMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem paste = new JMenuItem(I18n.get("menu.paste"));
		paste.addActionListener(e -> {
			try {
				java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
				if (clipboard.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)) {
					String text = (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
					view.getjTextField2().setText(text);
				}
			} catch (Exception ex) {
				log.err("Error pasting: " + ex.getMessage());
			}
		});
		menu.add(paste);
		return menu;
	}

	private void searchAndRefreshFileTables(Container container) {
		for (Component c : container.getComponents()) {
			if (c instanceof TabListFile) {
				TabListFile tlf = (TabListFile) c;
				if (tlf.getjTable1().getModel() instanceof FileTableModel) {
					((FileTableModel) tlf.getjTable1().getModel()).refreshColumnNames();
				}
				tlf.updateBackButtonText();
				tlf.updateMenuTexts();
			} else if (c instanceof Container) {
				searchAndRefreshFileTables((Container) c);
			}
		}
	}

	private void refreshAllTooltips() {
		if (chatArea != null && chatArea.getParent() != null) {
			Container p = (Container) chatArea.getParent();
			for (Component c : p.getComponents()) {
				if (c instanceof JButton) {
					JButton btn = (JButton) c;
					String tt = btn.getToolTipText();
					if (tt != null && (tt.contains("Adjuntar") || tt.contains("Attach"))) {
						btn.setToolTipText(I18n.get("tooltip.attachFile"));
					}
				}
			}
		}
		if (whiteboardColorButton != null) {
			whiteboardColorButton.setToolTipText(I18n.get("tooltip.currentColor"));
		}
		if (view.getjButton6() != null) {
			view.getjButton6().setToolTipText(I18n.get("login.configureProxy"));
		}
		if (notesContainerPanel != null) {
			updateTooltipsInContainer(notesContainerPanel);
		}
		if (whiteboardContainerPanel != null) {
			updateTooltipsInContainer(whiteboardContainerPanel);
		}
	}

	private void updateTooltipsInContainer(Container container) {
		for (Component c : container.getComponents()) {
			if (c instanceof JButton) {
				JButton btn = (JButton) c;
				String tt = btn.getToolTipText();
				if (tt != null) {
					if (tt.contains("Insertar imagen") || tt.contains("Insert image")) {
						btn.setToolTipText(I18n.get("tooltip.insertImage"));
					} else if (tt.contains("Achicar") || tt.contains("Shrink")) {
						btn.setToolTipText(I18n.get("tooltip.shrinkImage"));
					} else if (tt.contains("Agrandar") || tt.contains("Grow")) {
						btn.setToolTipText(I18n.get("tooltip.growImage"));
					} else if (tt.contains("Reducir") || tt.contains("Reduce")) {
						btn.setToolTipText(I18n.get("tooltip.reduceText"));
					} else if (tt.contains("Aumentar") || tt.contains("Increase")) {
						btn.setToolTipText(I18n.get("tooltip.increaseText"));
					} else if (tt.contains("Cambiar color") || tt.contains("Change font")) {
						btn.setToolTipText(I18n.get("tooltip.fontColor"));
					} else if (tt.contains("Guardar notas") || tt.contains("Save notes")) {
						btn.setToolTipText(I18n.get("tooltip.exportNotes"));
					} else if (tt.contains("Seleccionar") || tt.contains("Select")) {
						btn.setToolTipText(I18n.get("tooltip.whiteboard.select"));
					} else if (tt.contains("Lápiz") || tt.contains("Pencil")) {
						btn.setToolTipText(I18n.get("tooltip.whiteboard.pencil"));
					} else if (tt.contains("Texto") || tt.contains("Text")) {
						btn.setToolTipText(I18n.get("tooltip.whiteboard.text"));
					} else if (tt.contains("Imagen") || tt.contains("Image")) {
						btn.setToolTipText(I18n.get("tooltip.whiteboard.image"));
					} else if (tt.contains("Flecha") || tt.contains("Arrow")) {
						btn.setToolTipText(I18n.get("tooltip.whiteboard.arrow"));
					} else if (tt.contains("Círculo") || tt.contains("Circle")) {
						btn.setToolTipText(I18n.get("tooltip.whiteboard.circle"));
					} else if (tt.contains("Cuadrado") || tt.contains("Square")) {
						btn.setToolTipText(I18n.get("tooltip.whiteboard.square"));
					} else if (tt.contains("Rectángulo") || tt.contains("Rectangle")) {
						btn.setToolTipText(I18n.get("tooltip.whiteboard.rectangle"));
					} else if (tt.contains("Triángulo") || tt.contains("Triangle")) {
						btn.setToolTipText(I18n.get("tooltip.whiteboard.triangle"));
					} else if (tt.contains("Limpiar pizarra") || tt.contains("Clear whiteboard")) {
						btn.setToolTipText(I18n.get("tooltip.whiteboard.clear"));
					} else if (tt.contains("Guardar pizarra") || tt.contains("Save whiteboard")) {
						btn.setToolTipText(I18n.get("tooltip.whiteboard.save"));
					}
				}
			} else if (c instanceof JCheckBox) {
				JCheckBox cb = (JCheckBox) c;
				String text = cb.getText();
				if (text != null && (text.contains("Traslúcida") || text.contains("Translucent"))) {
					cb.setText(I18n.get("notes.translucent"));
					cb.setToolTipText(I18n.get("tooltip.translucent"));
				}
			} else if (c instanceof Container) {
				updateTooltipsInContainer((Container) c);
			}
		}
	}

	private void refreshHelpTabContent() {
		if (helpPane != null) {
			helpPane.setText(buildHelpHtml());
		} else if (helpContainerPanel != null) {
			helpPane = new javax.swing.JTextPane();
			helpPane.setContentType("text/html");
			helpPane.setEditable(false);
			helpPane.setText(buildHelpHtml());
			helpContainerPanel.removeAll();
			helpContainerPanel.add(new JScrollPane(helpPane), BorderLayout.CENTER);
			helpContainerPanel.revalidate();
		}
	}

	private String buildHelpHtml() {
		String qfolderRoot = Config.SHARED_DIR;
		return "<html><body style='padding:8px;font-family:sans-serif'>"
				+ "<h2>qfolder <small>v" + UpdateChecker.getVersion() + "</small></h2>"
				+ "<p><b>" + I18n.get("author") + "</b></p>"
				+ "<p><a href='https://github.com/damianlezcano/qfolder'>github.com/damianlezcano/qfolder</a></p>"
				+ "<p>" + I18n.get("app.description") + "</p>"
				+ "<h3>" + I18n.get("help.highlights") + "</h3>"
				+ "<ul>"
				+ "<li>" + I18n.get("help.bullet.noAccounts") + "</li>"
				+ "<li>" + I18n.get("help.bullet.distributed") + "</li>"
				+ "<li>" + I18n.get("help.bullet.sessions", qfolderRoot + "/sessions") + "</li>"
				+ "<li>" + I18n.get("help.bullet.filesShared") + "</li>"
				+ "<li>" + I18n.get("help.bullet.chatLinks") + "</li>"
				+ "<li>" + I18n.get("help.bullet.replyPin") + "</li>"
				+ "<li>" + I18n.get("help.bullet.whiteboard") + "</li>"
				+ "<li>" + I18n.get("help.bullet.notes") + "</li>"
				+ "<li>" + I18n.get("help.bullet.complements") + "</li>"
				+ "<li>" + I18n.get("help.bullet.autoDetect") + "</li>"
				+ "</ul>"
				+ "<h3>" + I18n.get("help.title") + "</h3>"
				+ "<p>" + I18n.get("app.description") + "</p>"
				+ "<h3>" + I18n.get("help.fileLocations") + "</h3>"
				+ "<table>"
				+ "<tr><td>qfolder root:</td><td><code>" + qfolderRoot + "</code></td></tr>"
				+ "<tr><td>" + I18n.get("help.sessions") + ":</td><td><code>" + qfolderRoot + "/sessions</code></td></tr>"
				+ "</table>"
				+ "<h3>" + I18n.get("help.launchOptions") + "</h3>"
				+ "<table>"
				+ "<tr><td><code>-Dqfolder.user.name=X</code></td><td>" + I18n.get("help.launchOption.userName") + "</td></tr>"
				+ "<tr><td><code>-Dqfolder.shared.dir=X</code></td><td>" + I18n.get("help.launchOption.sharedDir", "~") + "</td></tr>"
				+ "<tr><td><code>-Dqfolder.ws.port=N</code></td><td>" + I18n.get("help.launchOption.wsPort") + "</td></tr>"
				+ "<tr><td><code>-Dqfolder.tunnel.mock=true</code></td><td>" + I18n.get("help.launchOption.tunnelMock") + "</td></tr>"
				+ "</table>"
				+ "<p>qfolder 2020-2026</p>"
				+ "</body></html>";
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

	private ImageIcon groupIcon() {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.BLACK);
		g.drawOval(6, 2, 4, 4);
		g.drawOval(2, 4, 4, 4);
		g.drawOval(10, 4, 4, 4);
		g.drawArc(4, 7, 8, 7, 0, 180);
		g.drawArc(0, 9, 8, 5, 0, 180);
		g.drawArc(8, 9, 8, 5, 0, 180);
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
		if (whiteboardCanvas != null) {
			lastLocalWhiteboardChangeAt = System.currentTimeMillis();
		}
	}

	private void broadcastWhiteboardAction(String action, String elementId, String operation) {
		if (whiteboardCanvas != null) {
			lastLocalWhiteboardChangeAt = System.currentTimeMillis();
		}
		if ("update".equals(action) && operation != null && operation.startsWith("I|")) {
			String[] parts = operation.split("\\|", 3);
			if (parts.length >= 2) {
				operation = parts[0] + "|" + parts[1];
			}
		}
		recordWhiteboardActionInCore(action, elementId, operation);
	}

	private void recordWhiteboardActionInCore(String action, String elementId, String operation) {
		try {
			log.debug("[WB] accion: " + action + " id=" + elementId);
			publishCoreEvent(core.recordWhiteboardObjectAction(action, elementId, operation));
		} catch (Exception e) {
			log.debug("Pizarra core no disponible: " + e.getMessage());
		}
	}

	private void recordWhiteboardStrokeInCore(List<String> stroke) {
		try {
			List<int[]> points = new ArrayList<>();
			String color = "#000000";
			int width = 2;
			for (String line : stroke) {
				String[] parts = line.split("\\|");
				if (parts.length < 2) continue;
				String[] xy = parts[1].split(",");
				if (xy.length < 4) continue;
				if (parts.length > 2 && !parts[2].isBlank()) color = parts[2];
				if (parts.length > 3) width = Math.max(1, Integer.parseInt(parts[3]));
				if (points.isEmpty()) {
					points.add(new int[]{Integer.parseInt(xy[0]), Integer.parseInt(xy[1])});
				}
				points.add(new int[]{Integer.parseInt(xy[2]), Integer.parseInt(xy[3])});
			}
			if (!points.isEmpty()) publishCoreEvent(core.finishWhiteboardStroke(points, color, width));
		} catch (Exception e) {
			log.debug("No se pudo registrar trazo core: " + e.getMessage());
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
				public void insertUpdate(DocumentEvent e) { recordNotesInsert(e); }
				public void removeUpdate(DocumentEvent e) { recordNotesDelete(e); }
				public void changedUpdate(DocumentEvent e) { scheduleNotesBroadcast(); }
			};
		}
		notesPane.getDocument().addDocumentListener(notesDocumentListener);
	}

	private void recordNotesInsert(DocumentEvent e) {
		if (applyingRemoteNotes || System.currentTimeMillis() < suppressNotesBroadcastUntil) return;
		try {
			if (!isPlainTextNotesDocument()) {
				scheduleNotesBroadcast();
				return;
			}
			String text = e.getDocument().getText(e.getOffset(), e.getLength());
			if (text == null || text.isEmpty()) return;
			lastSentNotesState = notesPane.getText();
			int lineIndex = charOffsetToLineIndex(notesPane.getStyledDocument(), e.getOffset());
			publishCoreEvent(core.insertNoteText("shared-notes", lineIndex, text));
		} catch (Exception ex) {
			log.debug("No se pudo registrar insercion de notas: " + ex.getMessage());
			scheduleNotesBroadcast();
		}
	}

	private void recordNotesDelete(DocumentEvent e) {
		if (applyingRemoteNotes || System.currentTimeMillis() < suppressNotesBroadcastUntil) return;
		try {
			if (!isPlainTextNotesDocument()) {
				scheduleNotesBroadcast();
				return;
			}
			lastSentNotesState = notesPane.getText();
			int lineIndex = charOffsetToLineIndex(notesPane.getStyledDocument(), e.getOffset());
			publishCoreEvent(core.deleteNoteText("shared-notes", lineIndex, e.getLength()));
		} catch (Exception ex) {
			log.debug("No se pudo registrar borrado de notas: " + ex.getMessage());
			scheduleNotesBroadcast();
		}
	}

	private int charOffsetToLineIndex(javax.swing.text.StyledDocument doc, int offset) {
		if (doc == null) return 0;
		try {
			return Math.max(0, doc.getDefaultRootElement().getElementIndex(offset));
		} catch (Exception e) {
			return 0;
		}
	}

	private boolean isPlainTextNotesDocument() {
		if (notesPane == null) return true;
		try {
			StyledDocument doc = notesPane.getStyledDocument();
			for (int i = 0; i < doc.getLength(); i++) {
				AttributeSet attrs = doc.getCharacterElement(i).getAttributes();
				if (iconInfo(attrs) != null || StyleConstants.isBold(attrs) || StyleConstants.isItalic(attrs)
						|| StyleConstants.isUnderline(attrs) || !Color.BLACK.equals(noteForeground(attrs))) return false;
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void broadcastNotes() {
		if (notesPane == null || applyingRemoteNotes || System.currentTimeMillis() < suppressNotesBroadcastUntil) {
			return;
		}
		String state = serializeNotesState();
		if (state != null && !state.equals(lastSentNotesState)) {
			lastSentNotesState = state;
			recordNotesUpdateInCore(state);
		}
	}

	private void recordNotesUpdateInCore(String state) {
		try {
			if (core != null && state != null) {
				log.debug("[NOTES] publicando actualizacion");
				publishCoreEvent(core.updateNote("shared-notes", state));
			}
		} catch (Exception e) {
			log.debug("No se pudo registrar notas en core: " + e.getMessage());
		}
	}

	private boolean isStaleComplementoEvent(String name, long sequence) {
		if (name == null || sequence <= 0) return false;
		long latest = complementoSequences.getOrDefault(name, 0L);
		if (sequence <= latest) {
			log.debug("Complemento ignorado por estar desactualizado: " + name + " #" + sequence);
			return true;
		}
		complementoSequences.put(name, sequence);
		return false;
	}

	private String serializeNotesState() {
		try {
			StyledDocument doc = notesPane.getStyledDocument();
			StringBuilder state = new StringBuilder("QNOTES2\n");
			StringBuilder text = new StringBuilder();
			AttributeSet currentAttrs = null;
			int textRuns = 0;
			int images = 0;
			for (int i = 0; i < doc.getLength(); i++) {
				AttributeSet attrs = doc.getCharacterElement(i).getAttributes();
				IconInfo icon = iconInfo(attrs);
				if (icon != null) {
					textRuns += appendNotesTextRun(state, text, currentAttrs);
					text.setLength(0);
					currentAttrs = null;
					state.append("I|").append(icon.width).append('|').append(icon.height).append('|').append(icon.base64).append('\n');
					images++;
					continue;
				}
				if (currentAttrs == null || !sameNoteStyle(currentAttrs, attrs)) {
					textRuns += appendNotesTextRun(state, text, currentAttrs);
					text.setLength(0);
					currentAttrs = attrs;
				}
				text.append(doc.getText(i, 1));
			}
			textRuns += appendNotesTextRun(state, text, currentAttrs);
			log.debug("[NOTES] serializado " + notesStateSummary(state.toString())
					+ " textRuns=" + textRuns + " images=" + images);
			return state.toString();
		} catch (Exception e) {
			log.err("Error al serializar notas: " + e.getMessage());
			return null;
		}
	}

	private int appendNotesTextRun(StringBuilder state, StringBuilder text, AttributeSet attrs) {
		if (attrs == null || text.length() == 0) return 0;
		String encoded = Base64.getEncoder().encodeToString(text.toString().getBytes(StandardCharsets.UTF_8));
		state.append("T|")
				.append(StyleConstants.isBold(attrs)).append('|')
				.append(StyleConstants.isItalic(attrs)).append('|')
				.append(StyleConstants.isUnderline(attrs)).append('|')
				.append(Math.max(1, StyleConstants.getFontSize(attrs))).append('|')
				.append(colorToHex(noteForeground(attrs))).append('|')
				.append(encoded).append('\n');
		return 1;
	}

	private String notesStateSummary(String state) {
		if (state == null) return "state=null";
		if (!state.startsWith("QNOTES2\n")) return "legacy/plain chars=" + state.length();
		int images = 0;
		int textRuns = 0;
		for (String line : state.split("\n")) {
			if (line.startsWith("I|")) images++;
			else if (line.startsWith("T|")) textRuns++;
		}
		return "QNOTES2 chars=" + state.length() + " textRuns=" + textRuns + " images=" + images;
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
			lastAppliedNotesState = text;
			int oldCaret = notesPane.getCaretPosition();
			DefaultStyledDocument doc = text.startsWith("QNOTES1\n") || text.startsWith("QNOTES2\n")
					? parseNotesState(text) : parsePlainOrLegacyNotes(text);
			if (notesDocumentListener != null && notesPane.getDocument() != null) {
				notesPane.getDocument().removeDocumentListener(notesDocumentListener);
			}
			notesPane.setDocument(doc);
			attachNotesDocumentListener();
			notesPane.setCaretPosition(Math.min(oldCaret, notesPane.getDocument().getLength()));
		} catch (Exception e) {
			log.err("Error al aplicar notas remotas: " + e.getMessage());
		} finally {
			applyingRemoteNotes = false;
		}
	}

	private DefaultStyledDocument parsePlainOrLegacyNotes(String text) throws Exception {
		try {
			return parseLegacyRtfNotes(text);
		} catch (Exception ignored) {
			DefaultStyledDocument doc = new DefaultStyledDocument();
			doc.insertString(0, text, null);
			return doc;
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
		int textRuns = 0;
		int images = 0;
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
				textRuns++;
			} else if ("I".equals(p[0]) && p.length == 4) {
				byte[] bytes = Base64.getDecoder().decode(p[3]);
				Image image = ImageIO.read(new ByteArrayInputStream(bytes));
				Image scaled = image.getScaledInstance(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Image.SCALE_SMOOTH);
				SimpleAttributeSet attrs = new SimpleAttributeSet();
				StyleConstants.setIcon(attrs, new ImageIcon(scaled));
				doc.insertString(doc.getLength(), " ", attrs);
				images++;
			}
		}
		log.debug("[NOTES] aplicado remoto " + notesStateSummary(state)
				+ " textRuns=" + textRuns + " images=" + images);
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
		whiteboardColorButton.setToolTipText(I18n.get("tooltip.currentColor"));
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
		clear.addActionListener(e -> whiteboardCanvas.clear());
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
		insertSystemTab("Pizarra", boardIcon(), whiteboardContainerPanel, I18n.get("tooltip.tab.whiteboard"));
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
			File dir = new File(currentSessionDir != null ? currentSessionDir : getQfolderRootDir(), "whiteboard");
			dir.mkdirs();
			String filename = "pizarra-" + timestampForFilename() + ".png";
			File target = new File(dir, filename);
			if (target.exists()) target = new File(uniqueFilePath(target.getAbsolutePath()));
			BufferedImage img = whiteboardCanvas.toImage();
			ImageIO.write(img, "png", target);
			log.info("Pizarra guardada en " + target.getAbsolutePath());
			javax.swing.JOptionPane.showMessageDialog(view,
					I18n.get("whiteboard.saved", target.getAbsolutePath()),
					I18n.get("whiteboard.savedTitle", "Pizarra guardada"),
					javax.swing.JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			log.err("No se pudo guardar la pizarra: " + e.getMessage());
			javax.swing.JOptionPane.showMessageDialog(view,
					I18n.get("whiteboard.saveError", e.getMessage()),
					I18n.get("error.saveTitle", "Error al guardar"),
					javax.swing.JOptionPane.ERROR_MESSAGE);
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
		image.setToolTipText(I18n.get("tooltip.insertImage"));
		image.addActionListener(e -> chooseNotesImage());
		JButton smallerImage = new JButton(notesImageSizeIcon(false));
		smallerImage.setFocusable(false);
		smallerImage.setToolTipText(I18n.get("tooltip.shrinkImage"));
		smallerImage.addActionListener(e -> resizeSelectedNotesImage(0.8));
		JButton biggerImage = new JButton(notesImageSizeIcon(true));
		biggerImage.setFocusable(false);
		biggerImage.setToolTipText(I18n.get("tooltip.growImage"));
		biggerImage.addActionListener(e -> resizeSelectedNotesImage(1.25));
		toolbar.add(bold);
		toolbar.add(italic);
		toolbar.add(underline);
		JButton smaller = new JButton("A-");
		smaller.setFocusable(false);
		smaller.setToolTipText(I18n.get("tooltip.reduceText"));
		smaller.addActionListener(e -> changeNotesFontSize(-2));
		JButton bigger = new JButton("A+");
		bigger.setFocusable(false);
		bigger.setToolTipText(I18n.get("tooltip.increaseText"));
		bigger.addActionListener(e -> changeNotesFontSize(2));
		JButton fontColor = new JButton(colorIcon(Color.BLACK));
		fontColor.setFocusable(false);
		fontColor.setToolTipText(I18n.get("tooltip.fontColor"));
		fontColor.addActionListener(e -> chooseNotesFontColor());
		JCheckBox translucent = new JCheckBox(I18n.get("notes.translucent"));
		translucent.setToolTipText(I18n.get("tooltip.translucent"));
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
		JButton exportNotes = new JButton(exportIcon());
		exportNotes.setFocusable(false);
		exportNotes.setToolTipText(I18n.get("tooltip.exportNotes"));
		exportNotes.addActionListener(e -> exportNotesRtf());
		toolbar.add(exportNotes);
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
		insertSystemTab("Notas", noteIcon(), notesContainerPanel, I18n.get("tooltip.tab.notes"));
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
		long t0 = System.nanoTime();
		log.debug("[PERF][NOTES-BUTTON] INICIO - t0=" + t0);
		JFileChooser chooser = new JFileChooser();
		if (chooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
			try {
				long t1 = System.nanoTime();
				log.debug("[PERF][NOTES-BUTTON] Archivo seleccionado - dt=" + ((t1-t0)/1_000_000) + "ms");
				BufferedImage img = ImageIO.read(chooser.getSelectedFile());
				long t2 = System.nanoTime();
				log.debug("[PERF][NOTES-BUTTON] ImageIO.read - dt=" + ((t2-t1)/1_000_000) + "ms, size=" + (img!=null?img.getWidth()+"x"+img.getHeight():"null"));
				insertImageIntoNotes(img);
				long t3 = System.nanoTime();
				log.debug("[PERF][NOTES-BUTTON] FIN - total=" + ((t3-t0)/1_000_000) + "ms");
			} catch (Exception e) {
				log.err("No se pudo insertar imagen en notas: " + e.getMessage());
			}
		} else {
			log.debug("[PERF][NOTES-BUTTON] CANCELADO por usuario");
		}
	}

	private boolean pasteImageIntoNotes() {
		long t0 = System.nanoTime();
		String transferId = "notes-img-" + System.currentTimeMillis();
		log.debug("[PERF][NOTES-CLIPBOARD] INICIO Ctrl+V - t0=" + t0);
		javax.swing.SwingUtilities.invokeLater(() -> {
			updateTransferProgress(transferId, "Preparando imagen para notas...", 0, 5);
		});
		Thread t = new Thread(new Runnable() {
			private volatile boolean warnedSlow = false;

			@Override
			public void run() {
				long t1 = System.nanoTime();
				log.debug("[PERF][NOTES-CLIPBOARD] Hilo iniciado - dt=" + ((t1-t0)/1_000_000) + "ms");
				try {
					long t1a = System.nanoTime();
					log.debug("[PERF][NOTES-CLIPBOARD] Antes de getSystemClipboard - dt=" + ((t1a-t0)/1_000_000) + "ms");
					java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
					long t1b = System.nanoTime();
					log.debug("[PERF][NOTES-CLIPBOARD] getSystemClipboard - dt=" + ((t1b-t1a)/1_000_000) + "ms");
					long t1c = System.nanoTime();
					log.debug("[PERF][NOTES-CLIPBOARD] Antes de clipboard.getContents - dt=" + ((t1c-t0)/1_000_000) + "ms");

					final long clipboardStartTime = System.currentTimeMillis();
					final Transferable[] transHolder = new Transferable[1];
					final Exception[] holderEx = new Exception[1];
					final boolean[] done = {false};
					final boolean[] warned = {false};

					Thread monitorThread = new Thread(() -> {
						while (!done[0]) {
							long waited = System.currentTimeMillis() - clipboardStartTime;
							if (!warnedSlow && waited > 5000) {
								warnedSlow = true;
								warned[0] = true;
								log.info("[PERF][NOTES-CLIPBOARD] Portapapeles lento, esperando... (ya esperaban " + waited + "ms). Esto puede ocurrir con imagenes grandes en Linux.");
								javax.swing.SwingUtilities.invokeLater(() -> {
									updateTransferProgress(transferId, "Portapapeles lento... (esperando)", 0, 5);
								});
							}
							if (waited > 30000) {
								log.err("[PERF][NOTES-CLIPBOARD] Timeout esperando portapapeles despues de 30s");
								javax.swing.SwingUtilities.invokeLater(() -> {
									updateTransferProgress(transferId, "Timeout de portapapeles", 5, 5);
								});
								done[0] = true;
								return;
							}
							try { Thread.sleep(500); } catch (InterruptedException ie) { break; }
						}
					}, "notes-clipboard-monitor");
					monitorThread.setDaemon(true);
					monitorThread.start();

					Thread workerThread = new Thread(() -> {
						try {
							transHolder[0] = clipboard.getContents(null);
						} catch (Exception e) {
							holderEx[0] = e;
						} finally {
							done[0] = true;
						}
					}, "notes-clipboard-worker");
					workerThread.setDaemon(true);
					workerThread.start();

					while (!done[0]) {
						try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
					}
					if (holderEx[0] != null) {
						log.err("[PERF][NOTES-CLIPBOARD] Error accediendo portapapeles: " + holderEx[0].getMessage());
						javax.swing.SwingUtilities.invokeLater(() -> {
							updateTransferProgress(transferId, "Error de portapapeles", 5, 5);
						});
						return;
					}

					long t2 = System.nanoTime();
					log.debug("[PERF][NOTES-CLIPBOARD] clipboard.getContents - dt=" + ((t2-t1c)/1_000_000) + "ms, total=" + ((t2-t0)/1_000_000) + "ms" + (warned[0] ? " [WARNED]" : ""));
					Transferable trans = transHolder[0];
					boolean hasImage = trans != null && trans.isDataFlavorSupported(DataFlavor.imageFlavor);
					log.debug("[PERF][NOTES-CLIPBOARD] Clipboard check - dt=" + ((t2-t1)/1_000_000) + "ms, hasImage=" + hasImage);
					if (hasImage) {
						long t2a = System.nanoTime();
						log.debug("[PERF][NOTES-CLIPBOARD] Antes de getTransferData - dt=" + ((t2a-t0)/1_000_000) + "ms");
						Image img = (Image) trans.getTransferData(DataFlavor.imageFlavor);
						long t3 = System.nanoTime();
						log.debug("[PERF][NOTES-CLIPBOARD] getTransferData - dt=" + ((t3-t2a)/1_000_000) + "ms, total=" + ((t3-t0)/1_000_000) + "ms, size=" + (img!=null?img.getWidth(null)+"x"+img.getHeight(null):"null"));
						javax.swing.SwingUtilities.invokeLater(() -> {
							updateTransferProgress(transferId, "Insertando imagen en notas...", 2, 5);
						});
						long t4 = System.nanoTime();
						log.debug("[PERF][NOTES-CLIPBOARD] Antes de invokeAndWait - dt=" + ((t4-t0)/1_000_000) + "ms");
						javax.swing.SwingUtilities.invokeAndWait(() -> {
							long t5 = System.nanoTime();
							log.debug("[PERF][NOTES-CLIPBOARD] invokeAndWait inicio (EDT) - dt=" + ((t5-t0)/1_000_000) + "ms");
							insertImageIntoNotes(img);
							long t6 = System.nanoTime();
							log.debug("[PERF][NOTES-CLIPBOARD] insertImageIntoNotes FIN - dt=" + ((t6-t5)/1_000_000) + "ms, total desde t0=" + ((t6-t0)/1_000_000) + "ms");
						});
						long t7 = System.nanoTime();
						log.debug("[PERF][NOTES-CLIPBOARD] Despues de invokeAndWait - dt=" + ((t7-t0)/1_000_000) + "ms");
						javax.swing.SwingUtilities.invokeLater(() -> {
							removeTransferProgress(transferId, transferRows.get(transferId));
							long t8 = System.nanoTime();
							log.debug("[PERF][NOTES-CLIPBOARD] FIN COMPLETO - total=" + ((t8-t0)/1_000_000) + "ms");
						});
					} else {
						javax.swing.SwingUtilities.invokeLater(() -> {
							updateTransferProgress(transferId, "No hay imagen en portapapeles", 5, 5);
						});
					}
				} catch (Exception e) {
					log.err("No se pudo pegar imagen en notas: " + e.getMessage());
					javax.swing.SwingUtilities.invokeLater(() -> {
						updateTransferProgress(transferId, "Error al pegar imagen", 5, 5);
					});
				}
			}
		}, "notes-clipboard-paste");
		t.setDaemon(true);
		t.start();
		return true;
	}

	private void insertImageIntoNotes(Image image) {
		if (image == null || notesPane == null) return;
		long t0 = System.nanoTime();
		int maxWidth = Math.max(120, notesPane.getWidth() - 40);
		int width = image.getWidth(null);
		int height = image.getHeight(null);
		if (width > maxWidth) {
			height = Math.max(1, (int) Math.round(height * (maxWidth / (double) width)));
			width = maxWidth;
		}
		long t1 = System.nanoTime();
		Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
		long t2 = System.nanoTime();
		log.debug("[PERF][NOTES-INSERT] scale=" + width + "x" + height + " - scaleTime=" + ((t2-t1)/1_000_000) + "ms");
		SimpleAttributeSet attrs = new SimpleAttributeSet();
		StyleConstants.setIcon(attrs, new ImageIcon(scaled));
		notesPane.setCaretPosition(notesPane.getSelectionStart());
		notesPane.replaceSelection(" ");
		notesPane.getStyledDocument().setCharacterAttributes(notesPane.getCaretPosition() - 1, 1, attrs, false);
		long t3 = System.nanoTime();
		log.debug("[PERF][NOTES-INSERT] document insert - dt=" + ((t3-t2)/1_000_000) + "ms");
		scheduleNotesBroadcast();
		long t4 = System.nanoTime();
		log.debug("[PERF][NOTES-INSERT] scheduleNotesBroadcast llamada - total insertImageIntoNotes=" + ((t4-t0)/1_000_000) + "ms");
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
				if (id != null && id.equals(cos[i].getName())) {
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
		membersContainerPanel = null;
		membersTable = null;
		transferPanel = null;
		transferBars.clear();
		transferRows.clear();
		transferTargets.clear();
		activeTransferRequests.clear();
		transferPendingOpenLinks.clear();
		appliedCoreChatIds.clear();
		chatMessages.clear();
		chatMessageRanges.clear();
		chatFileLinks.clear();
		chatTransferLinks.clear();
		fileRegistry.clear();
		filePeers.clear();
		knownMembers.clear();
		memberConnectedAt.clear();
		corePeerUrls.clear();
		corePeerConnections.clear();
		pendingMemberPublicKeys.clear();
		indexedCoreFiles.clear();
		indexedCoreFilesWorkspaceId = null;
		if (notesSyncTimer != null) {
			notesSyncTimer.stop();
			notesSyncTimer = null;
		}
		for (JDialog d : approvalDialogs.values()) {
			try { d.dispose(); } catch (Exception ignored) {}
		}
		approvalDialogs.clear();
		lastSentNotesState = null;
		lastAppliedNotesState = null;
		historySaved = false;
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
				if (id != null && id.equals(cos[i].getName())) {
					return (TabListFile) cos[i];
				}
			}
		}
		return null;
	}

	private void sendFileDirect(CoreEnvelope request) {
		sendFileDirect(request, null);
	}

	private void sendFileDirect(CoreEnvelope request, WebSocket directConn) {
		String localFolder = getSessionFilesDir().getAbsolutePath();
		new Thread(() -> {
			if (request == null || request.response() == null || request.userId() == null) {
				log.err("Pedido de archivo invalido (core envelope)");
				return;
			}
			String[] fileInfo = request.response().split("\\|", 2);
			String fileName = fileInfo[0];
			String relativePath = fileInfo.length > 1 ? fileInfo[1] : fileName;
			String transferId = request.sequence() > 0 ? "core_" + request.sequence() : UUIDUtils.generate();
			String targetId = request.userId();
			String filePath = relativePath != null && !relativePath.isEmpty() ? relativePath : fileName;
			String localPath = localFolder + File.separator + filePath;
			File file = new File(localPath);

			if (!file.exists() || !file.isFile()) {
				sendTransferEvent(directConn, targetId, CoreEnvelope.of("Error al transferir archivo", user.getId(),
						"No se encontro el archivo local: " + fileName));
				return;
			}

			long totalParts = Math.max(1, (file.length() + FILE_CHUNK_SIZE - 1) / FILE_CHUNK_SIZE);
			log.info("Enviando archivo '" + fileName + "' en " + totalParts + " partes");
			updateTransferProgress(transferId, I18n.get("transfer.sending") + fileName, 0, (int) totalParts);

			try (FileInputStream fis = new FileInputStream(file)) {
				byte[] buffer = new byte[FILE_CHUNK_SIZE];
				int read;
				int part = 0;
				while ((read = fis.read(buffer)) != -1) {
					String content = Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, read));
					String partPayload = fileName + "|" + relativePath + "|" + part + "|" + totalParts + "|" + content;
					sendTransferEvent(directConn, targetId, CoreEnvelope.of("Parte de archivo", user.getId(), partPayload));
					updateTransferProgress(transferId, I18n.get("transfer.sending") + " " + fileName, part + 1, (int) totalParts);
					part++;
				}
				log.info("Archivo '" + fileName + "' enviado");
			} catch (Exception ex) {
				log.err("Error al enviar archivo: " + ex.getMessage());
				sendTransferEvent(directConn, targetId, CoreEnvelope.of("Error al transferir archivo", user.getId(),
						"Error al enviar archivo: " + ex.getMessage()));
			}
		}, "file-send").start();
	}

	private void sendTransferEvent(WebSocket directConn, String targetId, CoreEnvelope envelope) {
		try {
			if (directConn != null) {
				directConn.send(envelope.toJsonBase64());
			} else if (wsClient != null) {
				sendEvent(CoreEnvelope.of("__to:" + targetId + ":" + envelope.name(), envelope.userId(), envelope.response(), envelope.sequence()));
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
				localPath = uniqueFilePath(getSessionFilesDir().getAbsolutePath() + File.separator + filePath);
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
			updateTransferProgress(transferId, I18n.get("transfer.downloading") + " " + qfile.getName(), completed, qfile.getTotalParts());
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
				if (QFile.OPERATION_OPEN.equals(qfile.getOperation())) exec.open(localPath);
				transferTargets.remove(transferId);
			}
		} catch (Exception ex) {
			log.err("Error al recibir archivo: " + ex.getMessage());
		}
	}

	private void completeCoreChunkDownload(String transferId, FileMetadata metadata, QFile original, byte[] bytes) {
		try {
			String localPath = uniqueFilePath(getSessionFilesDir().getAbsolutePath() + File.separator + metadata.name());
			File target = new File(localPath);
			File parent = target.getParentFile();
			if (parent != null) parent.mkdirs();
			Files.write(target.toPath(), bytes);
			log.info("Archivo '" + metadata.name() + "' descargado desde chunks distribuidos");
			String chatLinkId = chatTransferLinks.remove(transferId);
			if (chatLinkId != null) {
				chatFileLinks.put(chatLinkId, target.getAbsolutePath());
			}
			String openLinkId = transferPendingOpenLinks.remove(transferId);
			if (openLinkId != null) {
				chatFileLinks.put(openLinkId, target.getAbsolutePath());
			}
			if (original != null && QFile.OPERATION_OPEN.equals(original.getOperation())) exec.open(target.getAbsolutePath());
			org.q3s.p2p.core.model.Event reShareEvent = core.shareFile(target.toPath());
			publishCoreEvent(reShareEvent);
			javax.swing.SwingUtilities.invokeLater(this::refreshArchivosTable);
			activeTransferRequests.remove(transferId);
		} catch (Exception e) {
			log.err("No se pudo finalizar descarga distribuida: " + e.getMessage());
			finishTransferWithError(transferId, "Error finalizando descarga");
		}
	}

	private void fallbackCoreChunkDownload(QFile original, String reason) {
		log.err(reason + ". Descarga P2P por chunks no completada.");
		if (original != null) {
			finishTransferWithError(original.getTransferId(), "Error: " + reason);
		}
	}

	private void updateTransferProgress(String transferId, String label, int current, int total) {
		if (transferId == null) {
			return;
		}
		javax.swing.SwingUtilities.invokeLater(() -> {
			transferPanel.setVisible(true);
			JProgressBar bar = transferBars.get(transferId);
			JPanel row = transferRows.get(transferId);
			if (bar == null) {
				bar = new JProgressBar(0, Math.max(total, 1));
				bar.setStringPainted(true);
				row = transferRow(transferId, bar);
				transferPanel.add(row);
				transferBars.put(transferId, bar);
				transferRows.put(transferId, row);
			} else {
				bar.setMaximum(Math.max(total, 1));
			}
			bar.setValue(Math.min(current, Math.max(total, 1)));
			int percent = total <= 0 ? 0 : (int) ((current * 100.0) / total);
			bar.setString(label + " - " + Math.min(percent, 100) + "%");
			transferPanel.revalidate();
			transferPanel.repaint();
			if (current >= total) {
				final JPanel completedRow = row;
				Timer timer = new Timer(1800, e -> {
					removeTransferProgress(transferId, completedRow);
				});
				timer.setRepeats(false);
				timer.start();
			}
		});
	}

	private JPanel transferRow(String transferId, JProgressBar bar) {
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.add(bar, BorderLayout.CENTER);
		JButton retry = new JButton(I18n.get("transfer.retry"));
		retry.setFocusable(false);
		retry.addActionListener(e -> retryTransfer(transferId));
		JButton cancel = new JButton(I18n.get("transfer.cancel"));
		cancel.setFocusable(false);
		cancel.addActionListener(e -> cancelTransfer(transferId));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		buttons.add(retry);
		buttons.add(cancel);
		row.add(buttons, BorderLayout.EAST);
		return row;
	}

	private void removeTransferProgress(String transferId, JPanel row) {
		transferBars.remove(transferId);
		transferRows.remove(transferId);
		activeTransferRequests.remove(transferId);
		transferPendingOpenLinks.remove(transferId);
		chatTransferLinks.remove(transferId);
		if (transferPanel == null) return;
		if (row != null) transferPanel.remove(row);
		transferPanel.setVisible(transferPanel.getComponentCount() > 0);
		transferPanel.revalidate();
		transferPanel.repaint();
	}

	private void finishTransferWithError(String transferId, String message) {
		if (transferId == null) return;
		javax.swing.SwingUtilities.invokeLater(() -> {
			JProgressBar bar = transferBars.get(transferId);
			if (bar != null) {
				bar.setString(message + " - " + I18n.get("transfer.retry") + " / " + I18n.get("transfer.cancel"));
			}
		});
	}

	private void registerActiveTransfer(QFile qFile) {
		if (qFile != null && qFile.getTransferId() != null) {
			activeTransferRequests.put(qFile.getTransferId(), copyTransferRequest(qFile));
		}
	}

	private QFile copyTransferRequest(QFile src) {
		QFile copy = new QFile();
		copy.setName(src.getName());
		copy.setSize(src.getSize());
		copy.setDate(src.getDate());
		copy.setRelativePath(src.getRelativePath());
		copy.setMd5(src.getMd5());
		copy.setOperation(src.getOperation());
		copy.setOwner(src.getOwner());
		copy.setTransferId(src.getTransferId());
		return copy;
	}

	private void retryTransfer(String transferId) {
		QFile original = activeTransferRequests.get(transferId);
		if (original == null) return;
		if (coreChunkTransfer != null) coreChunkTransfer.cancel(transferId);
		String newTransferId = UUIDUtils.generate();
		String linkId = chatTransferLinks.remove(transferId);
		String openLinkId = transferPendingOpenLinks.remove(transferId);
		original.setTransferId(newTransferId);
		if (linkId != null) chatTransferLinks.put(newTransferId, linkId);
		if (openLinkId != null) transferPendingOpenLinks.put(newTransferId, openLinkId);
		removeTransferProgress(transferId, transferRows.get(transferId));
		registerActiveTransfer(original);
		updateTransferProgress(newTransferId, I18n.get("transfer.retrying") + " " + original.getName(), 0, 1);
		if (!requestCoreChunkDownload(original)) {
			finishTransferWithError(newTransferId, I18n.get("transfer.error") + " " + original.getName());
		}
	}

	private void cancelTransfer(String transferId) {
		if (coreChunkTransfer != null) coreChunkTransfer.cancel(transferId);
		removeTransferProgress(transferId, transferRows.get(transferId));
		log.info("Transferencia cancelada");
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
		try {
			long createdAt = sessionCreatedAt > 0 ? sessionCreatedAt : System.currentTimeMillis();
			Date createdDate = new Date(createdAt);
			String workspaceName = wk.getName() != null ? decodeValue(wk.getName()) : defaultWorkspaceName();
			File sessionDir = buildSessionHistoryDir();
			if (sessionDir == null) return;
			sessionDir.mkdirs();

			File chatDir = new File(sessionDir, "chat");
			File notesDir = new File(sessionDir, "notes");
			File whiteboardDir = new File(sessionDir, "whiteboard");
			File logsDir = new File(sessionDir, "logs");
			File membersDir = new File(sessionDir, "members");

			chatDir.mkdirs();
			notesDir.mkdirs();
			whiteboardDir.mkdirs();
			logsDir.mkdirs();
			membersDir.mkdirs();

			if (chatArea != null) {
				Files.writeString(new File(chatDir, "chat.txt").toPath(), chatArea.getText(), StandardCharsets.UTF_8);
			}
			if (notesPane != null) {
				try (OutputStream out = new FileOutputStream(new File(notesDir, "notas.rtf"))) {
					new RTFEditorKit().write(out, notesPane.getDocument(), 0, notesPane.getDocument().getLength());
				}
			}
			if (whiteboardCanvas != null) {
				ImageIO.write(whiteboardCanvas.toImage(), "png", new File(whiteboardDir, "pizarra.png"));
			}
			saveLogHistory(new File(logsDir, "logs.log"));
			Files.writeString(new File(logsDir, "sesion.txt").toPath(),
					"Workspace: " + workspaceName + "\n"
							+ "Creada: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(createdDate) + "\n"
							+ "Guardada: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n"
							+ "Motivo: " + reason + "\n",
					StandardCharsets.UTF_8);
			saveMembersSnapshot(membersDir, workspaceName);
			log.info("Historial de sesion guardado en " + sessionDir.getAbsolutePath());
			historySaved = true;
		} catch (Exception e) {
			log.err("No se pudo guardar el historial de sesion: " + e.getMessage());
		}
	}

	private void saveMembersSnapshot(File membersDir, String workspaceName) {
		try {
			if (user != null) trackMemberWithoutRefresh(user, true);

			Set<String> localConnections = p2pMesh != null ? p2pMesh.connectedPeers() : Set.of();

			JsonArrayBuilder members = Json.createArrayBuilder();

			for (User member : knownMembers.values()) {
				if (member == null || member.getId() == null) continue;

				boolean isSelf = member.equals(user);
				boolean connectedToMe = localConnections.contains(member.getId())
						|| isDirectPeerConnected(member.getId());
				boolean online = isSelf || connectedToMe;

				Set<String> distributedConnections = new java.util.LinkedHashSet<>(corePeerConnections.getOrDefault(member.getId(), Set.of()));

				if (isSelf) distributedConnections.addAll(localConnections);
				if (!isSelf && connectedToMe && user != null) distributedConnections.add(user.getId());
				if (!online) distributedConnections.clear();

				JsonArrayBuilder connectedWith = Json.createArrayBuilder();
				for (String peerId : distributedConnections) connectedWith.add(peerId);

				JsonObjectBuilder item = Json.createObjectBuilder()
					.add("id", member.getId())
					.add("name", member.getName() != null ? member.getName() : "")
					.add("connected", online)
					.add("local", isSelf)
					.add("peer_url", member.getPeerUrl() != null ? member.getPeerUrl() : "")
					.add("peer_count", distributedConnections.size())
					.add("connected_with", connectedWith);

				Long connectedAt = memberConnectedAt.get(member.getId());
				if (connectedAt != null && connectedAt > 0) {
					item.add("connected_at", connectedAt);
				} else {
					item.addNull("connected_at");
				}

				members.add(item);
			}

			JsonObject root = Json.createObjectBuilder()
				.add("workspace_id", wk != null ? wk.getId() : "")
				.add("workspace_name", workspaceName != null ? workspaceName : "")
				.add("saved_at", Instant.now().toString())
				.add("members", members)
				.build();

			Files.writeString(
				new File(membersDir, "members.json").toPath(),
				root.toString(),
				StandardCharsets.UTF_8
			);
		} catch (Exception e) {
			log.debug("No se pudo guardar snapshot de miembros: " + e.getMessage());
		}
	}

	private String timestampForFilename() {
		return new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new java.util.Date());
	}

	private void exportNotesRtf() {
		if (notesPane == null) return;
		try {
			prepareWorkspaceSessionDirectories();
			File dir = new File(currentSessionDir != null ? currentSessionDir : getQfolderRootDir(), "notes");
			dir.mkdirs();
			String filename = "notas-" + timestampForFilename() + ".rtf";
			File target = new File(dir, filename);
			if (target.exists()) target = new File(uniqueFilePath(target.getAbsolutePath()));
			try (java.io.OutputStream out = new java.io.FileOutputStream(target)) {
				new javax.swing.text.rtf.RTFEditorKit().write(out, notesPane.getDocument(), 0, notesPane.getDocument().getLength());
			}
			log.info("Notas guardadas en " + target.getAbsolutePath());
			JOptionPane.showMessageDialog(view,
					I18n.get("notes.saved", target.getAbsolutePath()),
					I18n.get("notes.savedTitle", "Notas guardadas"),
					JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			log.err("No se pudieron guardar las notas: " + e.getMessage());
			JOptionPane.showMessageDialog(view,
					I18n.get("notes.saveError", e.getMessage()),
					I18n.get("error.saveTitle", "Error al guardar"),
					JOptionPane.ERROR_MESSAGE);
		}
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
		private static final String STATE_PREFIX = "QWBSTATE1";
		private static final String ACTION_PREFIX = "QWBA1";
		private final List<String> operations = new ArrayList<>();
		private final List<String> currentStroke = new ArrayList<>();
		private final Map<String, String> cachedImageData = new LinkedHashMap<>();
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
						if (e.getClickCount() == 2 && elementOp(operations.get(selectedIndex)).startsWith("T|")) {
							editSelectedText();
							return;
						}
						Rectangle r = boundsOf(elementOp(operations.get(selectedIndex)));
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
						broadcastWhiteboardAction("update", elementId(operations.get(selectedIndex)), elementOp(operations.get(selectedIndex)));
					} else if ("Lápiz".equals(tool) && !currentStroke.isEmpty()) {
						recordWhiteboardStrokeInCore(new ArrayList<>(currentStroke));
						addElement(strokeOp(currentStroke));
						selectedIndex = operations.size() - 1;
						tool = "Seleccionar";
						currentStroke.clear();
						repaint();
					} else if (isShapeTool() && previewShape != null) {
						addElement(previewShape);
						selectedIndex = operations.size() - 1;
						tool = "Seleccionar";
						previewShape = null;
						repaint();
					}
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		private String elementEntry(String id, String op) {
			return id + "\t" + op;
		}

		private String elementId(String entry) {
			int idx = entry.indexOf('\t');
			return idx > 0 ? entry.substring(0, idx) : UUIDUtils.generate();
		}

		private String elementOp(String entry) {
			int idx = entry.indexOf('\t');
			return idx > 0 ? entry.substring(idx + 1) : entry;
		}

		private String addElement(String op) {
			String id = UUIDUtils.generate();
			operations.add(elementEntry(id, op));
			broadcastWhiteboardAction("add", id, op);
			return id;
		}

		private void updateSelectedElement(String op) {
			if (selectedIndex < 0 || selectedIndex >= operations.size()) return;
			String id = elementId(operations.get(selectedIndex));
			operations.set(selectedIndex, elementEntry(id, op));
			broadcastWhiteboardAction("update", id, op);
		}

		String actionPayload(String action, String elementId, String operation) {
			String encodedOp = operation == null ? "" : Base64.getEncoder().encodeToString(operation.getBytes(StandardCharsets.UTF_8));
			return ACTION_PREFIX + "\n" + action + "\n" + (elementId == null ? "" : elementId) + "\n" + encodedOp;
		}

		void applyAction(String payload) {
			if (payload == null || !payload.startsWith(ACTION_PREFIX + "\n")) return;
			String[] parts = payload.split("\n", 4);
			if (parts.length < 3) return;
			String action = parts[1];
			String id = parts[2];
			String op = "";
			if (parts.length == 4 && !parts[3].isEmpty()) {
				op = new String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8);
			}
			if ("clear".equals(action)) {
				operations.clear();
				cachedImageData.clear();
				selectedIndex = -1;
			} else if ("delete".equals(action)) {
				int idx = findElementIndexById(id);
				if (idx >= 0) operations.remove(idx);
				cachedImageData.remove(id);
				selectedIndex = -1;
			} else if ("add".equals(action)) {
				if (findElementIndexById(id) < 0) {
					if (op.startsWith("I|") && op.indexOf('|', 2) >= 0) {
						String[] opParts = op.split("\\|", 3);
						if (opParts.length >= 3 && !opParts[2].isEmpty()) {
							cachedImageData.put(id, opParts[2]);
						}
					}
					operations.add(elementEntry(id, op));
				}
			} else if ("update".equals(action)) {
				int idx = findElementIndexById(id);
				if (idx >= 0) {
					String existingOp = elementOp(operations.get(idx));
					if (op.startsWith("I|") && op.indexOf('|', 2) < 0) {
						String cached = cachedImageData.get(id);
						if (cached != null) {
							op = op + "|" + cached;
						} else {
							int bar = existingOp.indexOf('|', 2);
							if (bar > 0) {
								cached = existingOp.substring(bar + 1);
								cachedImageData.put(id, cached);
								op = op + "|" + cached;
							}
						}
					}
					operations.set(idx, elementEntry(id, op));
				} else operations.add(elementEntry(id, op));
			}
			repaint();
		}

		private int findElementIndexById(String id) {
			if (id == null || id.isEmpty()) return -1;
			for (int i = 0; i < operations.size(); i++) {
				if (id.equals(elementId(operations.get(i)))) return i;
			}
			return -1;
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
			addElement("T|20,20,160,40|" + fontSize + "|" + colorToHex(drawColor) + "|" + encoded);
			tool = "Seleccionar";
			repaint();
		}

		void addImage(Image image, int x, int y) {
			addImage(image, x, y, false, null);
		}

		void addImage(Image image, int x, int y, boolean showProgressImmediately, String transferId) {
			if (image == null) return;
			long t0 = System.nanoTime();
			String threadName = Thread.currentThread().getName();
			log.debug("[PERF][WB-ADD] INICIO - t0=" + t0 + ", showProgress=" + showProgressImmediately + ", thread=" + threadName);
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
					long t1 = System.nanoTime();
					log.debug("[PERF][WB-ADD] Hilo iniciado - dt=" + ((t1-t0)/1_000_000) + "ms");
					if (progressVisible.get()) {
						updateTransferProgress(progressId, "Convirtiendo imagen", 2, 5);
					}
					long t2 = System.nanoTime();
					log.debug("[PERF][WB-ADD] Antes BufferedImage - dt=" + ((t2-t0)/1_000_000) + "ms");
					BufferedImage bi = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
					long t3 = System.nanoTime();
					log.debug("[PERF][WB-ADD] BufferedImage creado " + bi.getWidth() + "x" + bi.getHeight() + " - dt=" + ((t3-t2)/1_000_000) + "ms");
					Graphics2D g = bi.createGraphics();
					g.drawImage(image, 0, 0, null);
					g.dispose();
					long t4 = System.nanoTime();
					log.debug("[PERF][WB-ADD] Graphics2D drawImage - dt=" + ((t4-t3)/1_000_000) + "ms");
					if (progressVisible.get()) {
						updateTransferProgress(progressId, "Comprimiendo imagen", 3, 5);
					}
					long t5 = System.nanoTime();
					log.debug("[PERF][WB-ADD] Antes PNG write - dt=" + ((t5-t0)/1_000_000) + "ms");
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					ImageIO.write(bi, "png", out);
					byte[] pngBytes = out.toByteArray();
					long t6 = System.nanoTime();
					log.debug("[PERF][WB-ADD] PNG write - size=" + pngBytes.length + " bytes - dt=" + ((t6-t5)/1_000_000) + "ms");
					long t7 = System.nanoTime();
					String data = Base64.getEncoder().encodeToString(pngBytes);
					long t8 = System.nanoTime();
					log.debug("[PERF][WB-ADD] Base64 encode - size=" + data.length() + " chars - dt=" + ((t8-t7)/1_000_000) + "ms");
					if (progressVisible.get()) {
						updateTransferProgress(progressId, "Sincronizando imagen de pizarra", 4, 5);
					}
					javax.swing.SwingUtilities.invokeLater(() -> {
						long t9 = System.nanoTime();
						log.debug("[PERF][WB-ADD] EDT inicio - dt=" + ((t9-t0)/1_000_000) + "ms");
						addElement("I|" + x + "," + y + "," + Math.min(220, bi.getWidth()) + "," + Math.min(160, bi.getHeight()) + "|" + data);
						selectedIndex = operations.size() - 1;
						tool = "Seleccionar";
						repaint();
						long t10 = System.nanoTime();
						log.debug("[PERF][WB-ADD] addElement+repaint FIN - dt=" + ((t10-t9)/1_000_000) + "ms, total=" + ((t10-t0)/1_000_000) + "ms");
						if (progressVisible.get()) {
							updateTransferProgress(progressId, "Imagen de pizarra sincronizada", 5, 5);
							removeTransferProgress(progressId, transferRows.get(progressId));
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
			long t0 = System.nanoTime();
			log.debug("[PERF][WB-BUTTON] INICIO - t0=" + t0);
			JFileChooser chooser = new JFileChooser();
			if (chooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
				try {
					long t1 = System.nanoTime();
					log.debug("[PERF][WB-BUTTON] Archivo seleccionado - dt=" + ((t1-t0)/1_000_000) + "ms");
					BufferedImage img = ImageIO.read(chooser.getSelectedFile());
					long t2 = System.nanoTime();
					log.debug("[PERF][WB-BUTTON] ImageIO.read - dt=" + ((t2-t1)/1_000_000) + "ms, size=" + (img!=null?img.getWidth()+"x"+img.getHeight():"null"));
					addImage(img, x, y);
					long t3 = System.nanoTime();
					log.debug("[PERF][WB-BUTTON] addImage llamado - dt=" + ((t3-t2)/1_000_000) + "ms");
				} catch (Exception e) {
					log.err("No se pudo insertar imagen: " + e.getMessage());
				}
			} else {
				log.debug("[PERF][WB-BUTTON] CANCELADO por usuario");
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
				addElement("T|" + r.x + "," + r.y + "," + r.width + "," + r.height + "|" + fontSize + "|" + colorToHex(drawColor) + "|" + encoded);
				selectedIndex = operations.size() - 1;
				tool = "Seleccionar";
			}
			revalidate();
			repaint();
		}

		void pasteImageFromClipboard(int x, int y) {
			long t0 = System.nanoTime();
			String transferId = UUIDUtils.generate();
			log.debug("[PERF][WB-CLIPBOARD] INICIO Ctrl+V - t0=" + t0 + ", pos=" + x + "," + y);
			javax.swing.SwingUtilities.invokeLater(() -> {
				updateTransferProgress(transferId, "Preparando pegado...", 0, 5);
			});
			Thread t = new Thread(new Runnable() {
				private volatile boolean warnedSlow = false;

				@Override
				public void run() {
					try {
						long t1 = System.nanoTime();
						log.debug("[PERF][WB-CLIPBOARD] Hilo iniciado - dt=" + ((t1-t0)/1_000_000) + "ms");
						long t1a = System.nanoTime();
						log.debug("[PERF][WB-CLIPBOARD] Antes de getSystemClipboard - dt=" + ((t1a-t0)/1_000_000) + "ms");
						java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
						long t1b = System.nanoTime();
						log.debug("[PERF][WB-CLIPBOARD] getSystemClipboard - dt=" + ((t1b-t1a)/1_000_000) + "ms");
						long t1c = System.nanoTime();
						log.debug("[PERF][WB-CLIPBOARD] Antes de clipboard.getContents - dt=" + ((t1c-t0)/1_000_000) + "ms");

						final long clipboardStartTime = System.currentTimeMillis();
						final Transferable[] transHolder = new Transferable[1];
						final Exception[] holderEx = new Exception[1];
						final boolean[] done = {false};
						final boolean[] warned = {false};

						Thread monitorThread = new Thread(() -> {
							while (!done[0]) {
								long waited = System.currentTimeMillis() - clipboardStartTime;
								if (!warnedSlow && waited > 5000) {
									warnedSlow = true;
									warned[0] = true;
									log.info("[PERF][WB-CLIPBOARD] Portapapeles lento, esperando... (ya esperaban " + waited + "ms). Esto puede ocurrir con imagenes grandes en Linux.");
									javax.swing.SwingUtilities.invokeLater(() -> {
										updateTransferProgress(transferId, "Portapapeles lento... (esperando)", 0, 5);
									});
								}
								if (waited > 30000) {
									log.err("[PERF][WB-CLIPBOARD] Timeout esperando portapapeles despues de 30s");
									javax.swing.SwingUtilities.invokeLater(() -> {
										updateTransferProgress(transferId, "Timeout de portapapeles", 5, 5);
									});
									done[0] = true;
									return;
								}
								try { Thread.sleep(500); } catch (InterruptedException ie) { break; }
							}
						}, "wb-clipboard-monitor");
						monitorThread.setDaemon(true);
						monitorThread.start();

						Thread workerThread = new Thread(() -> {
							try {
								transHolder[0] = clipboard.getContents(null);
							} catch (Exception e) {
								holderEx[0] = e;
							} finally {
								done[0] = true;
							}
						}, "wb-clipboard-worker");
						workerThread.setDaemon(true);
						workerThread.start();

						while (!done[0]) {
							try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
						}
						if (holderEx[0] != null) {
							log.err("[PERF][WB-CLIPBOARD] Error accediendo portapapeles: " + holderEx[0].getMessage());
							javax.swing.SwingUtilities.invokeLater(() -> {
								updateTransferProgress(transferId, "Error de portapapeles", 5, 5);
							});
							return;
						}

						long t2 = System.nanoTime();
						log.debug("[PERF][WB-CLIPBOARD] clipboard.getContents - dt=" + ((t2-t1c)/1_000_000) + "ms, total=" + ((t2-t0)/1_000_000) + "ms" + (warned[0] ? " [WARNED]" : ""));
						Transferable trans = transHolder[0];
						boolean hasImage = trans != null && trans.isDataFlavorSupported(DataFlavor.imageFlavor);
						log.debug("[PERF][WB-CLIPBOARD] isDataFlavorSupported check - dt=" + ((t2-t1)/1_000_000) + "ms, hasImage=" + hasImage);
						if (hasImage) {
							long t2a = System.nanoTime();
							log.debug("[PERF][WB-CLIPBOARD] Antes de getTransferData - dt=" + ((t2a-t0)/1_000_000) + "ms");
							Image img = (Image) trans.getTransferData(DataFlavor.imageFlavor);
							long t3 = System.nanoTime();
							log.debug("[PERF][WB-CLIPBOARD] getTransferData - dt=" + ((t3-t2a)/1_000_000) + "ms, total=" + ((t3-t0)/1_000_000) + "ms, size=" + (img!=null?img.getWidth(null)+"x"+img.getHeight(null):"null"));
							javax.swing.SwingUtilities.invokeLater(() -> {
								updateTransferProgress(transferId, "Imagen lista, insertando...", 2, 5);
							});
							addImage(img, x, y, true, transferId);
							long t4 = System.nanoTime();
							log.debug("[PERF][WB-CLIPBOARD] addImage llamado - dt=" + ((t4-t0)/1_000_000) + "ms");
						} else {
							javax.swing.SwingUtilities.invokeLater(() -> {
								updateTransferProgress(transferId, "No hay imagen en portapapeles", 5, 5);
							});
						}
					} catch (Exception e) {
						log.err("No se pudo pegar imagen: " + e.getMessage());
						javax.swing.SwingUtilities.invokeLater(() -> {
							updateTransferProgress(transferId, "Error leyendo portapapeles", 5, 5);
						});
					}
				}
			}, "clipboard-paste");
			t.setDaemon(true);
			t.start();
		}

		void clear() {
			operations.clear();
			selectedIndex = -1;
			repaint();
			broadcastWhiteboardAction("clear", null, null);
		}

		void deleteSelected() {
			if (selectedIndex >= 0 && selectedIndex < operations.size()) {
				String id = elementId(operations.get(selectedIndex));
				operations.remove(selectedIndex);
				selectedIndex = -1;
				repaint();
				broadcastWhiteboardAction("delete", id, null);
			}
		}

		String serialize() {
			StringBuilder state = new StringBuilder(STATE_PREFIX);
			for (String entry : operations) {
				state.append('\n')
						.append(elementId(entry))
						.append('|')
						.append(Base64.getEncoder().encodeToString(elementOp(entry).getBytes(StandardCharsets.UTF_8)));
			}
			return state.toString();
		}

		BufferedImage toImage() {
			if (activeTextEditor != null) commitTextEditor();
			BufferedImage img = new BufferedImage(Math.max(1, getWidth()), Math.max(1, getHeight()), BufferedImage.TYPE_INT_RGB);
			Graphics2D g2 = img.createGraphics();
			g2.setColor(Color.WHITE);
			g2.fillRect(0, 0, img.getWidth(), img.getHeight());
			for (String entry : operations) {
				String op = elementOp(entry);
				if (!op.startsWith("T|")) drawOperation(g2, op);
			}
			for (String entry : operations) {
				String op = elementOp(entry);
				if (op.startsWith("T|")) drawOperation(g2, op);
			}
			g2.dispose();
			return img;
		}

		void applyState(String state) {
			operations.clear();
			if (state != null && !state.isEmpty()) {
				if (state.startsWith(STATE_PREFIX + "\n") || state.equals(STATE_PREFIX)) {
					String[] lines = state.split("\n");
					for (int i = 1; i < lines.length; i++) {
						String[] parts = lines[i].split("\\|", 2);
						if (parts.length == 2) {
							String op = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
							if (op.startsWith("I|") && op.indexOf('|', 2) >= 0) {
								String[] opParts = op.split("\\|", 3);
								if (opParts.length >= 3 && !opParts[2].isEmpty()) {
									cachedImageData.put(parts[0], opParts[2]);
								}
							}
							if (op.startsWith("I|") && op.indexOf('|', 2) < 0) {
								String cached = cachedImageData.get(parts[0]);
								if (cached != null) op = op + "|" + cached;
							}
							operations.add(elementEntry(parts[0], op));
						}
					}
				} else {
					for (String op : Arrays.asList(state.split("\n"))) {
						operations.add(elementEntry(UUIDUtils.generate(), op));
					}
				}
			}
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g;
			g2.setColor(Color.BLACK);
			List<String> all = new ArrayList<>();
			List<String> allIds = new ArrayList<>();
			for (String entry : operations) {
				all.add(elementOp(entry));
				allIds.add(elementId(entry));
			}
			all.addAll(currentStroke);
			allIds.addAll(java.util.Collections.nCopies(currentStroke.size(), (String) null));
			if (previewShape != null) { all.add(previewShape); allIds.add(null); }
			for (int i = 0; i < all.size(); i++) {
				String op = all.get(i);
				if (!op.startsWith("T|")) drawOperation(g2, op, allIds.get(i));
			}
			for (int i = 0; i < all.size(); i++) {
				String op = all.get(i);
				if (op.startsWith("T|")) drawOperation(g2, op, allIds.get(i));
			}
			if (selectedIndex >= 0 && selectedIndex < operations.size()) {
				Rectangle r = boundsOf(elementOp(operations.get(selectedIndex)));
				if (r != null) drawHandles(g2, r);
			}
		}

		private void drawOperation(Graphics2D g2, String op) {
			drawOperation(g2, op, null);
		}

		private void drawOperation(Graphics2D g2, String op, String elementId) {
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
						String[] parts = op.split("\\|", 5);
						int size = Integer.parseInt(parts[2]);
						String[] bounds = parts[1].split(",");
						int x = Integer.parseInt(bounds[0]), y = Integer.parseInt(bounds[1]), w = Integer.parseInt(bounds[2]), h = Integer.parseInt(bounds[3]);
						String text = new String(Base64.getDecoder().decode(parts[4]), StandardCharsets.UTF_8);
						JTextArea renderer = new JTextArea(text);
						renderer.setOpaque(false);
						renderer.setForeground(Color.decode(parts[3]));
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
						String imgData = p.length >= 3 && !p[2].isEmpty() ? p[2] : null;
						if (imgData == null && elementId != null) imgData = cachedImageData.get(elementId);
						if (imgData == null) return;
						BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(imgData)));
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
				} catch (Exception e) {
					log.debug("No se pudo dibujar operacion de pizarra: " + e.getMessage());
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
				Rectangle r = boundsOf(elementOp(operations.get(i)));
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
			} catch (RuntimeException e) {
				log.debug("No se pudo editar texto de pizarra: " + e.getMessage());
			}
			return null;
		}

		private void moveOrResizeSelected(int x, int y) {
			String op = elementOp(operations.get(selectedIndex));
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
			String nextOp = op;
			String id = elementId(operations.get(selectedIndex));
			if (op.startsWith("I|")) {
				String imgData = parts.length >= 3 ? parts[2] : cachedImageData.get(id);
				if (imgData != null) {
					nextOp = parts[0] + "|" + coords + "|" + imgData;
					cachedImageData.put(id, imgData);
				} else {
					nextOp = parts[0] + "|" + coords;
				}
			} else if (op.startsWith("S|")) nextOp = parts[0] + "|" + parts[1] + "|" + coords + "|" + parts[3] + (parts.length > 4 ? "|" + parts[4] : "");
			else if (op.startsWith("P|")) nextOp = parts[0] + "|" + coords + "|" + parts[2] + "|" + parts[3] + "|" + parts[4];
			else if (op.startsWith("T|")) nextOp = parts[0] + "|" + coords + "|" + parts[2] + "|" + parts[3] + "|" + parts[4];
			operations.set(selectedIndex, elementEntry(id, nextOp));
		}

		void changeSelectedSizeOrStroke(int delta) {
			if (selectedIndex >= 0 && elementOp(operations.get(selectedIndex)).startsWith("T|")) {
				String[] p = elementOp(operations.get(selectedIndex)).split("\\|", 5);
				int size = Math.max(8, Math.min(72, Integer.parseInt(p[2]) + delta));
				fontSize = size;
				updateSelectedElement(p[0] + "|" + p[1] + "|" + size + "|" + p[3] + "|" + p[4]);
				repaint();
			} else if (selectedIndex >= 0 && (elementOp(operations.get(selectedIndex)).startsWith("S|") || elementOp(operations.get(selectedIndex)).startsWith("P|"))) {
				String op = elementOp(operations.get(selectedIndex));
				String[] p = op.split("\\|");
				int width = Math.max(1, Math.min(20, (p.length > 4 ? Integer.parseInt(p[4]) : 1) + delta));
				strokeWidth = width;
				if (op.startsWith("S|")) {
					updateSelectedElement(p[0] + "|" + p[1] + "|" + p[2] + "|" + p[3] + "|" + width);
				} else {
					updateSelectedElement(p[0] + "|" + p[1] + "|" + p[2] + "|" + width + "|" + p[4]);
				}
				repaint();
			} else {
				strokeWidth = Math.max(1, Math.min(20, strokeWidth + delta));
			}
		}

		private void applyColorToSelected(Color color) {
			if (selectedIndex < 0) return;
			String op = elementOp(operations.get(selectedIndex));
			String hex = colorToHex(color);
			String[] p = op.split("\\|", 5);
			if (op.startsWith("T|") && p.length == 5) updateSelectedElement(p[0] + "|" + p[1] + "|" + p[2] + "|" + hex + "|" + p[4]);
			else if (op.startsWith("S|") && p.length >= 4) updateSelectedElement(p[0] + "|" + p[1] + "|" + p[2] + "|" + hex + (p.length > 4 ? "|" + p[4] : "|" + strokeWidth));
			else if (op.startsWith("P|") && p.length == 5) updateSelectedElement(p[0] + "|" + p[1] + "|" + hex + "|" + p[3] + "|" + p[4]);
			repaint();
		}

		private void loadSelectedStyle() {
			if (selectedIndex < 0) return;
			String op = elementOp(operations.get(selectedIndex));
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
			String entry = operations.get(selectedIndex);
			String id = elementId(entry);
			String op = elementOp(entry);
			try {
				String[] p = op.split("\\|", 5);
				String[] xy = p[1].split(",");
				fontSize = Integer.parseInt(p[2]);
				drawColor = Color.decode(p[3]);
				String text = new String(Base64.getDecoder().decode(p[4]), StandardCharsets.UTF_8);
				operations.remove(selectedIndex);
				broadcastWhiteboardAction("delete", id, null);
				selectedIndex = -1;
				startTextEditor(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]));
				activeTextEditor.setBounds(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), Integer.parseInt(xy[2]), Integer.parseInt(xy[3]));
				activeTextEditor.setText(text);
			} catch (RuntimeException e) {
				log.debug("No se pudo editar texto de pizarra: " + e.getMessage());
			}
		}
	}

	public void downloadFile(User user2, QFile qFile) {
		if (qFile == null || qFile.getOwner() == null) return;
		log.info("Solicitando descargar el archivo '" + qFile.getName() + "'");
		qFile.setOperation(QFile.OPERATION_DOWNLOAD);
		if (qFile.getTransferId() == null || qFile.getTransferId().isBlank()) {
			qFile.setTransferId(UUIDUtils.generate());
		}
		registerActiveTransfer(qFile);
		updateTransferProgress(qFile.getTransferId(), I18n.get("transfer.requesting") + " " + qFile.getName(), 0, 1);
		if (requestCoreChunkDownload(qFile)) {
			return;
		}
		finishTransferWithError(qFile.getTransferId(), I18n.get("transfer.error") + " " + qFile.getName());
		requestLegacyFileDownload(qFile);
	}

	public void openFile(User user2, QFile qFile) {
		if (qFile == null) return;
		if (qFile.getOwner() != null && qFile.getOwner().equals(this.user)) {
			String baseDir = getSessionFilesDir().getAbsolutePath();
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
		registerActiveTransfer(qFile);
		updateTransferProgress(qFile.getTransferId(), I18n.get("transfer.requesting") + " " + qFile.getName(), 0, 1);
		if (requestCoreChunkDownload(qFile)) {
			return;
		}
		finishTransferWithError(qFile.getTransferId(), I18n.get("transfer.error") + " " + qFile.getName());
		requestLegacyFileDownload(qFile);
	}

	private void requestLegacyFileDownload(QFile qFile) {
		log.err("Archivo no disponible por chunks P2P: " + (qFile != null ? qFile.getName() : ""));
	}

	private boolean requestCoreChunkDownload(QFile qFile) {
		return coreChunkTransfer != null && coreChunkTransfer.request(qFile);
	}

	private void requestReplicatorDownload(org.q3s.p2p.core.model.FileMetadata metadata) {
		if (metadata == null) return;
		try {
			QFile qFile = new QFile();
			qFile.setName(metadata.name());
			qFile.setSize(metadata.size());
			qFile.setDate(System.currentTimeMillis());
			qFile.setMD5("core:" + metadata.fileId());
			qFile.setTotalParts(metadata.chunks() == null ? 0 : metadata.chunks().size());
			qFile.setCurrentPart(0);
			qFile.setOperation(QFile.OPERATION_DOWNLOAD);
			log.debug("[REPLICATOR] Disparando descarga offline de '" + metadata.name() + "' (fileId=" + metadata.fileId() + ")");
			requestCoreChunkDownload(qFile);
		} catch (Exception e) {
			log.debug("No se pudo disparar descarga replicator: " + e.getMessage());
		}
	}

	public Component getView() {
		return view;
	}

	public void removeFile(User user2, QFile qFile) {
		if (qFile == null) return;
		try {
			String filename = qFile.getRelativePath() != null && !qFile.getRelativePath().isEmpty()
					? qFile.getRelativePath() : qFile.getName();
			String filepath = getSessionFilesDir().getAbsolutePath();
			String fullname = filepath + File.separator + filename;
			FileUtils.remove(Paths.get(fullname));
			indexedCoreFiles.remove(filename);

			String hash = qFile.getMd5();
			if (hash != null && !hash.isBlank()) {
				if (hash.startsWith("core:")) {
					String fileId = hash.substring(5);
					fileRegistry.entrySet().removeIf(entry -> fileId.equals(entry.getValue().fileId()));
				} else {
					fileRegistry.remove(hash);
				}
				filePeers.remove(hash);
			}

			saveIndexedCoreFilesCache();
			refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
		} catch (Exception e) {
			log.err("Error al intentar borrar el archivo: " + qFile.getName() + ": " + e.getMessage());
		}
	}

	public void refreshFiles(User user2) {
		if (user2 == null || user2.equals(user)) {
			refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
		} else {
			refreshArchivosTable();
			refreshTables();
		}
	}

	public String getNavigationPath(String userId) {
		return navigationPaths.getOrDefault(userId, "");
	}

	private String fileNavigationPayload(String requestId, String path) {
		String safePath = path == null ? "" : path;
		String encodedPath = Base64.getEncoder().encodeToString(safePath.getBytes(StandardCharsets.UTF_8));
		return "QFILES1\n" + (requestId == null ? "" : requestId) + "\n" + encodedPath;
	}

	private FileNavigationPayload parseFileNavigationPayload(String payload) {
		if (payload != null && payload.startsWith("QFILES1\n")) {
			String[] parts = payload.split("\n", 3);
			if (parts.length == 3) {
				String path = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8);
				return new FileNavigationPayload(parts[1].isEmpty() ? null : parts[1], path);
			}
		}
		return new FileNavigationPayload(null, payload == null ? "" : payload);
	}

	private String nextFileNavigationRequest(String userId) {
		String requestId = UUIDUtils.generate();
		fileNavigationRequestIds.put(userId, requestId);
		return requestId;
	}

	public void navigateTo(String userId, String relativePath) {
		navigationPaths.put(userId, relativePath);
		if (userId.equals(this.user.getId())) {
			refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
		} else {
			log.debug("Navegacion remota legacy deshabilitada; usando metadata core de archivos.");
		}
	}

	public void navigateBack(String userId) {
		String current = navigationPaths.getOrDefault(userId, "");
		int sep = current.lastIndexOf('/');
		String newPath = sep >= 0 ? current.substring(0, sep) : "";
		navigationPaths.put(userId, newPath);
		if (userId.equals(this.user.getId())) {
			refreshLocalFilesAndNotify("Notifico Cambio en los archivos");
		} else {
			log.debug("Navegacion remota legacy deshabilitada; usando metadata core de archivos.");
		}
	}

	private static class FileNavigationPayload {
		final String requestId;
		final String path;

		FileNavigationPayload(String requestId, String path) {
			this.requestId = requestId;
			this.path = path == null ? "" : path;
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
		if (p2pMesh != null) p2pMesh.disconnectAll();
		try { Thread.sleep(350); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
		if (cloudflareTunnel != null) {
			cloudflareTunnel.stop();
		}
		if (wsServer != null) {
			wsServer.shutdown();
		}
		if (wsClient != null) {
			try {
				wsClient.close();
			} catch (Exception | NoClassDefFoundError e) {
				log.debug("No se pudo cerrar wsClient: " + e.getMessage());
			}
			wsClient = null;
		}
		outboundEventQueue.shutdownNow();
	}
}
