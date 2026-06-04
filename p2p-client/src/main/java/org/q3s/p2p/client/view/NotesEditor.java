package org.q3s.p2p.client.view;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

import javax.swing.JTextPane;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;

/**
 * Encapsula el pane de notas compartidas y su logica de sincronizacion con el core.
 * Mantiene el estado de aplicacion remota, debounce de broadcast y conversion de
 * offsets de caracter a indices de linea para el CRDT.
 *
 * Extraido de Controller (PENDIENTE-11) para reducir el tamanio de Controller y
 * permitir testing aislado del editor.
 */
public class NotesEditor {

	@FunctionalInterface
	public interface InsertHandler { void onInsert(int lineIndex, String text); }

	@FunctionalInterface
	public interface DeleteHandler { void onDelete(int lineIndex, int length); }

	@FunctionalInterface
	public interface UpdateHandler { void onUpdate(String state); }

	@FunctionalInterface
	public interface StaleEventChecker extends BiPredicate<String, Long> {}

	@FunctionalInterface
	public interface Debug { void log(String msg); }

	@FunctionalInterface
	public interface Error { void log(String msg); }

	private final JTextPane pane;
	private final int fontSize;
	private final InsertHandler insertHandler;
	private final DeleteHandler deleteHandler;
	private final UpdateHandler updateHandler;
	private final StaleEventChecker staleChecker;
	private final Debug debug;
	private final Error error;
	private DocumentListener documentListener;
	private Timer syncTimer;
	private volatile boolean applyingRemote;
	private volatile long suppressBroadcastUntil;
	private String lastSentState = "";
	private String lastAppliedState = "";

	public NotesEditor(int fontSize, InsertHandler insert, DeleteHandler delete, UpdateHandler update,
			StaleEventChecker stale, Debug debug, Error error) {
		this.fontSize = fontSize;
		this.insertHandler = insert;
		this.deleteHandler = delete;
		this.updateHandler = update;
		this.staleChecker = stale == null ? (n, s) -> false : stale;
		this.debug = debug == null ? m -> {} : debug;
		this.error = error == null ? m -> {} : error;
		this.pane = new JTextPane();
		this.pane.setDocument(new DefaultStyledDocument());
		this.pane.setFont(pane.getFont().deriveFont((float) fontSize));
		this.syncTimer = new Timer(900, e -> broadcast());
		this.syncTimer.setRepeats(false);
	}

	public JTextPane pane() { return pane; }

	public boolean isApplyingRemote() { return applyingRemote; }

	public void setSharedText(String text) {
		if (pane == null) return;
		applyingRemote = true;
		try {
			if (notesDocumentListenerInternal() != null) pane.getDocument().removeDocumentListener(notesDocumentListenerInternal());
			StyledDocument doc = pane.getStyledDocument();
			try { doc.remove(0, doc.getLength()); } catch (Exception ex) { error.log("No se pudo limpiar notas: " + ex.getMessage()); }
			if (text != null && !text.isEmpty()) {
				try { doc.insertString(0, text, null); } catch (Exception ex) { error.log("No se pudo aplicar texto de notas: " + ex.getMessage()); }
			}
			lastAppliedState = text == null ? "" : text;
			if (notesDocumentListenerInternal() != null) pane.getDocument().addDocumentListener(notesDocumentListenerInternal());
		} finally {
			applyingRemote = false;
		}
	}

	public void attach() {
		if (pane == null) return;
		pane.getDocument().addDocumentListener(notesDocumentListenerInternal());
	}

	public void detach() {
		if (pane == null || documentListener == null) return;
		pane.getDocument().removeDocumentListener(documentListener);
	}

	public void dispose() {
		if (syncTimer != null) {
			syncTimer.stop();
			syncTimer = null;
		}
		detach();
	}

	public void setEnabled(boolean enabled) {
		if (pane != null) pane.setEnabled(enabled);
	}

	public String getText() {
		return pane == null ? "" : pane.getText();
	}

	public int charOffsetToLineIndex(int offset) {
		if (pane == null) return 0;
		try {
			return Math.max(0, pane.getStyledDocument().getDefaultRootElement().getElementIndex(offset));
		} catch (Exception e) {
			return 0;
		}
	}

	public boolean isStaleComplementoEvent(String name, long sequence) {
		return staleChecker.test(name, sequence);
	}

	private DocumentListener notesDocumentListenerInternal() {
		if (documentListener == null) {
			documentListener = new DocumentListener() {
				@Override public void insertUpdate(DocumentEvent e) { handleInsert(e); }
				@Override public void removeUpdate(DocumentEvent e) { handleDelete(e); }
				@Override public void changedUpdate(DocumentEvent e) { scheduleBroadcast(); }
			};
		}
		return documentListener;
	}

	private void handleInsert(DocumentEvent e) {
		if (applyingRemote || System.currentTimeMillis() < suppressBroadcastUntil) return;
		try {
			String text = e.getDocument().getText(e.getOffset(), e.getLength());
			if (text == null || text.isEmpty()) return;
			lastSentState = pane.getText();
			int lineIndex = charOffsetToLineIndex(e.getOffset());
			if (insertHandler != null) insertHandler.onInsert(lineIndex, text);
		} catch (Exception ex) {
			debug.log("No se pudo registrar insercion de notas: " + ex.getMessage());
			scheduleBroadcast();
		}
	}

	private void handleDelete(DocumentEvent e) {
		if (applyingRemote || System.currentTimeMillis() < suppressBroadcastUntil) return;
		try {
			lastSentState = pane.getText();
			int lineIndex = charOffsetToLineIndex(e.getOffset());
			if (deleteHandler != null) deleteHandler.onDelete(lineIndex, e.getLength());
		} catch (Exception ex) {
			debug.log("No se pudo registrar borrado de notas: " + ex.getMessage());
			scheduleBroadcast();
		}
	}

	public void scheduleBroadcast() {
		if (!applyingRemote && syncTimer != null && System.currentTimeMillis() >= suppressBroadcastUntil) {
			syncTimer.restart();
		}
	}

	private void broadcast() {
		if (pane == null || applyingRemote || System.currentTimeMillis() < suppressBroadcastUntil) return;
		String state = serializeState();
		if (state != null && !state.equals(lastSentState)) {
			lastSentState = state;
			if (updateHandler != null) updateHandler.onUpdate(state);
		}
	}

	private String serializeState() {
		try {
			if (pane == null) return "";
			StyledDocument doc = pane.getStyledDocument();
			return doc.getText(0, doc.getLength());
		} catch (Exception e) {
			return null;
		}
	}
}
