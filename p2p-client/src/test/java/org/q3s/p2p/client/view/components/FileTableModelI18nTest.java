package org.q3s.p2p.client.view.components;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.q3s.p2p.client.util.I18n;

class FileTableModelI18nTest {

    @BeforeEach
    void setUp() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        I18n.setLocale(Locale.getDefault());
    }

    @Test
    void getColumnName_usesI18n_whenEnglish() {
        I18n.setLocale(Locale.ENGLISH);
        FileTableModel model = new FileTableModel();
        assertEquals("File", model.getColumnName(1), "col.file should be English: File");
        assertEquals("Size", model.getColumnName(2), "col.size should be English: Size");
        assertEquals("Modified", model.getColumnName(3), "col.modDate should be English: Modified");
        assertEquals("Owner", model.getColumnName(4), "col.owner should be English: Owner");
        assertEquals("Peers", model.getColumnName(5), "col.peers should be English: Peers");
    }

    @Test
    void getColumnName_usesI18n_whenSpanish() {
        I18n.setLocale(new Locale("es"));
        FileTableModel model = new FileTableModel();
        assertEquals("Archivo", model.getColumnName(1), "col.file should be Spanish: Archivo");
        assertEquals("Tamaño", model.getColumnName(2), "col.size should be Spanish: Tamaño");
        assertEquals("Fecha Modificación", model.getColumnName(3), "col.modDate should be Spanish: Fecha Modificación");
        assertEquals("Propietario", model.getColumnName(4), "col.owner should be Spanish: Propietario");
        assertEquals("Peers", model.getColumnName(5), "col.peers should be Spanish: Peers");
    }

    @Test
    void getColumnName_returnsEmpty_forIconColumn() {
        FileTableModel model = new FileTableModel();
        assertEquals("", model.getColumnName(0), "Column 0 should be empty (icon)");
    }

    @Test
    void refreshColumnNames_doesNotThrow() {
        FileTableModel model = new FileTableModel();
        assertDoesNotThrow(() -> model.refreshColumnNames(), "refreshColumnNames should not throw");
    }
}