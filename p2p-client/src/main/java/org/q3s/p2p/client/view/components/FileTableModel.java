/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.view.components;

import javax.swing.table.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;
import org.q3s.p2p.model.QFile;
import org.q3s.p2p.model.User;

public class FileTableModel extends AbstractTableModel {

    //private List<QFile> files;
    private List<User> users = new ArrayList<>();
    ImageIcon icon = new ImageIcon(getClass().getResource("/files-icon.png"));

    protected String[] columnNames = new String[]{
        "", "Archivo", "Tamaño", "Fecha Modificación", "Propietario"
    };

    protected Class[] columnClasses = new Class[]{
        ImageIcon.class, String.class, Long.class, Date.class, String.class
    };

    public FileTableModel() {
    }

    // This table model works for any one given directory
    public FileTableModel(User user) {
        this.users.add(user);
    }

    public FileTableModel(List<User> users) {
        this.users = users;
    }

    public List<QFile> files() {
        return users.stream().flatMap(a -> a.getFiles().stream()).collect(Collectors.toList());
    }

//    public Object[][] filesToArray(List<User> users) {
//        List<Object[]> r = new ArrayList<Object[]>();
//        for (User user : users) {
//            Object[][] uf = filesToArray(user);
//            r.add(uf);
//        }
//        return r.toArray(new Object[][]{});
//    }    
//
//    private Object[][] filesToArray(User user){
//        List<QFile> f = user.getFiles();
//        Object[][] arr = new Object[f.size()][3];
//        for (int i = 0; i < f.size(); i++) {
//            QFile file = f.get(i);
//            arr[i][0] = "md5XXX";
//            
//            arr[i][1] = file.getName();
//            arr[i][2] = file.getSize();
//            arr[i][3] = file.getDate();
//            
//            arr[i][4] = user.getName();
//        }
//        return arr;
//    }
    // These are easy methods
    public int getColumnCount() {
        return columnNames.length;
    }  // A constant for this model

    public int getRowCount() {
        return files().size();
    }  // # of files in dir

    // Information about each column
    public String getColumnName(int col) {
        return columnNames[col];
    }

    public Class getColumnClass(int col) {
        return columnClasses[col];
    }

    // The method that must actually return the value of each cell
    public Object getValueAt(int row, int col) {
        QFile f = files().get(row);
        switch (col) {
            case 0:
                return icon;
            case 1:
                return f.getName();
            case 2:
                return f.getSize();
            case 3:
                return new Date(f.getDate());
            case 4:
                return f.getOwner().getName();
            default:
                return null;
        }
    }

    public List<User> getUsers() {
        return users;
    }

    
    
}
