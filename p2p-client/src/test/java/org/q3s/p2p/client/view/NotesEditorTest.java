package org.q3s.p2p.client.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;

import org.junit.jupiter.api.Test;

class NotesEditorTest {

	@Test void notasPaneSeCreaConFuenteIndicada() {
		NotesEditor editor = new NotesEditor(18, null, null, null, null, null, null);
		JTextPane pane = editor.pane();
		assertNotNull(pane);
		assertEquals(18f, pane.getFont().getSize(), 0.01f);
		editor.dispose();
	}

	@Test void insercionEnPaneDisparaInsertHandlerConLineIndex() throws BadLocationException, InterruptedException {
		AtomicReference<int[]> captured = new AtomicReference<>();
		NotesEditor editor = new NotesEditor(14,
				(lineIndex, text) -> captured.set(new int[] { lineIndex, text.length() }),
				null, null, null, null, null);
		editor.attach();
		editor.pane().setText("");
		editor.pane().getStyledDocument().insertString(0, "primera\n", null);
		Thread.sleep(50);
		assertNotNull(captured.get(), "InsertHandler debe invocarse al insertar texto en el pane");
		assertEquals(8, captured.get()[1], "El handler recibe el largo del texto insertado (primera + newline = 8)");
		assertTrue(captured.get()[0] >= 0 && captured.get()[0] < 3, "El lineIndex debe estar en el rango valido del documento");
		editor.dispose();
	}

	@Test void setSharedTextNoDisparaHandlersPorqueApplyingRemoteEstaActivo() throws BadLocationException, InterruptedException {
		List<String> events = new ArrayList<>();
		NotesEditor editor = new NotesEditor(14,
				(lineIndex, text) -> events.add("insert:" + text),
				(lineIndex, length) -> events.add("delete:" + length),
				state -> events.add("update:" + state),
				null, null, null);
		editor.attach();
		editor.setSharedText("hello\nworld");
		Thread.sleep(50);
		assertTrue(events.isEmpty(),
				"setSharedText debe ser un no-op para los handlers porque applyingRemote=true");
		assertEquals("hello\nworld", editor.getText());
		editor.dispose();
	}

	@Test void disposeNoLanzaExcepcionesTrasActividad() throws BadLocationException {
		NotesEditor editor = new NotesEditor(14, null, null, null, null, null, null);
		editor.attach();
		editor.pane().getStyledDocument().insertString(0, "x", null);
		editor.dispose();
		assertNotNull(editor.pane(), "dispose() mantiene el pane (la referencia sigue accesible para el caller)");
	}

	@Test void charOffsetToLineIndexMapeaCorrectamente() {
		NotesEditor editor = new NotesEditor(14, null, null, null, null, null, null);
		try {
			editor.pane().getStyledDocument().insertString(0, "linea1\nlinea2\nlinea3", null);
		} catch (BadLocationException e) { fail(e); }
		assertEquals(0, editor.charOffsetToLineIndex(0));
		assertEquals(0, editor.charOffsetToLineIndex(5));
		assertEquals(1, editor.charOffsetToLineIndex(7));
		assertEquals(2, editor.charOffsetToLineIndex(14));
		editor.dispose();
	}
}
