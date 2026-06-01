package org.q3s.p2p.client.view.components;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.table.AbstractTableModel;

import org.q3s.p2p.client.util.I18n;
import org.q3s.p2p.model.QFile;
import org.q3s.p2p.model.User;

public class FileTableModel extends AbstractTableModel {

    private List<FileTableRow> rows = new ArrayList<>();
    private ImageIcon fileIcon = new ImageIcon(getClass().getResource("/files-icon.png"));

    public record FileTableRow(String name, long size, long date, String fileId, String hash, User owner, int peerCount) {}

    protected String[] columnNames = new String[]{
        "", I18n.get("col.file"), I18n.get("col.size"), I18n.get("col.modDate"), I18n.get("col.owner"), I18n.get("col.peers")
    };

    protected Class[] columnClasses = new Class[]{
        ImageIcon.class, String.class, String.class, Date.class, String.class, Integer.class
    };

    public FileTableModel() {
    }

    public FileTableModel(User user) {
    }

    public FileTableModel(List<FileTableRow> rows) {
        this.rows = new ArrayList<>(rows);
    }

    public void setRows(List<FileTableRow> rows) {
        this.rows = new ArrayList<>(rows);
        fireTableDataChanged();
    }

    public void refreshColumnNames() {
        fireTableStructureChanged();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public String getColumnName(int col) {
        String result;
        switch (col) {
            case 1: result = I18n.get("col.file"); break;
            case 2: result = I18n.get("col.size"); break;
            case 3: result = I18n.get("col.modDate"); break;
            case 4: result = I18n.get("col.owner"); break;
            case 5: result = I18n.get("col.peers"); break;
            default: result = "";
        }
        System.out.println("[FileTableModel.getColumnName] col=" + col + " -> \"" + result + "\", I18n locale=" + I18n.currentLocale().getLanguage());
        return result;
    }

    @Override
    public Class getColumnClass(int col) {
        return columnClasses[col];
    }

    @Override
    public Object getValueAt(int row, int col) {
        if (row < 0 || row >= rows.size()) return null;
        FileTableRow r = rows.get(row);
        switch (col) {
            case -1:
                return toQFile(r);
            case 0:
                return fileIcon;
            case 1:
                return r.name();
            case 2:
                return formatSize(r.size());
            case 3:
                return new Date(r.date());
            case 4:
                return r.owner() != null ? r.owner().getName() : "-";
            case 5:
                return r.peerCount();
            default:
                return null;
        }
    }

    private QFile toQFile(FileTableRow r) {
        QFile f = new QFile();
        f.setName(r.name());
        f.setSize(r.size());
        f.setDate(r.date());
        f.setRelativePath(r.name());
        f.setMd5("core:" + r.fileId());
        f.setOperation(QFile.OPERATION_DOWNLOAD);
        f.setOwner(r.owner());
        return f;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.1f GB", mb / 1024.0);
    }

    public List<FileTableRow> getRows() {
        return rows;
    }

}
