package org.q3s.p2p.client.view;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ControllerTabbedPaneVisibilityTest {

	private static final Path CONTROLLER_PATH = Path.of(
			"src/main/java/org/q3s/p2p/client/view/Controller.java");

	private String readSource() throws Exception {
		return Files.readString(CONTROLLER_PATH);
	}

	private String readBodyOf(String source, String methodSignatureFragment, String methodEndMarker) {
		int start = source.indexOf(methodSignatureFragment);
		assertTrue(start >= 0, "No se encontro la firma: " + methodSignatureFragment);
		int end = source.indexOf(methodEndMarker, start);
		assertTrue(end > start, "No se encontro el fin de: " + methodSignatureFragment);
		return source.substring(start, end);
	}

	@Test
	void jButton4ActionPerformedHaceVisibleElTabbedPane() throws Exception {
		String source = readSource();
		String body = readBodyOf(source,
				"private void jButton4ActionPerformed(",
				"private void jButton5ActionPerformed(");
		assertTrue(body.contains("jTabbedPane().setVisible(true)"),
				"jButton4ActionPerformed (Crear workspace) debe hacer jTabbedPane visible tras la creacion");
	}

	@Test
	void activateWorkspaceFromCoreHaceVisibleElTabbedPane() throws Exception {
		String source = readSource();
		String body = readBodyOf(source,
				"private void activateWorkspaceFromCore()",
				"private void publishCoreEvent(");
		assertTrue(body.contains("jTabbedPane().setVisible(true)"),
				"activateWorkspaceFromCore (Union a workspace) debe hacer jTabbedPane visible tras la activacion");
	}

	@Test
	void existeMetodoEnableDefaultComplementos() throws Exception {
		String source = readSource();
		assertTrue(source.contains("private void enableDefaultComplementos()"),
				"Debe existir el metodo enableDefaultComplementos()");
		assertTrue(source.contains("applyComplementoVisibility(name, true)"),
				"enableDefaultComplementos debe invocar applyComplementoVisibility(name, true) para cada complemento");
	}

	@Test
	void jButton4ActionPerformedHabilitaComplementosPorDefecto() throws Exception {
		String source = readSource();
		String body = readBodyOf(source,
				"private void jButton4ActionPerformed(",
				"private void jButton5ActionPerformed(");
		assertTrue(body.contains("enableDefaultComplementos()"),
				"jButton4ActionPerformed (Crear workspace) debe llamar enableDefaultComplementos() para cargar los tabs de Archivos/Chat/Pizarra/Notas/Miembros");
	}

	@Test
	void activateWorkspaceFromCoreHabilitaComplementosPorDefecto() throws Exception {
		String source = readSource();
		String body = readBodyOf(source,
				"private void activateWorkspaceFromCore()",
				"private void publishCoreEvent(");
		assertTrue(body.contains("enableDefaultComplementos()"),
				"activateWorkspaceFromCore (Union a workspace) debe llamar enableDefaultComplementos() para cargar los tabs");
	}

	@Test
	void noSeLlamaNotifyBienvenidoQueDeshaceLaActivacion() throws Exception {
		String source = readSource();
		String createBody = readBodyOf(source,
				"private void jButton4ActionPerformed(",
				"private void jButton5ActionPerformed(");
		String activateBody = readBodyOf(source,
				"private void activateWorkspaceFromCore()",
				"private void publishCoreEvent(");

		assertTrue(!createBody.contains("Bienvenido usuario al grupo!"),
				"jButton4ActionPerformed NO debe disparar notify('Bienvenido usuario al grupo!') porque su handler llama a showJoinAfterWorkspaceLost que deshace la activacion");
		assertTrue(!activateBody.contains("Bienvenido usuario al grupo!"),
				"activateWorkspaceFromCore NO debe disparar notify('Bienvenido usuario al grupo!') por la misma razon");
	}
}
