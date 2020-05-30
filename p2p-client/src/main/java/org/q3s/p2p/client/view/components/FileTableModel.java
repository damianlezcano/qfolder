/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client.view.components;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.table.AbstractTableModel;

import org.q3s.p2p.model.QFile;
import org.q3s.p2p.model.User;

public class FileTableModel extends AbstractTableModel {

    private List<User> users = new ArrayList<>();
    private ImageIcon icon = new ImageIcon(getClass().getResource("/files-icon.png"));

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
    	List<QFile> list = new ArrayList<QFile>();
    	if(users != null) {
    		for (User usr : users) {
    			if(usr.isOnline()) {
    				list.addAll(usr.getFiles());    				
    			}else{
    				if(users.size() == 1) {
    					list.addAll(usr.getFiles());
    				}
    			}
    		}    		
    	}
    	return list;
    }

    // These are easy methods
    @Override
    public int getColumnCount() {
        return columnNames.length;
    }  // A constant for this model

    @Override
    public int getRowCount() {
        return files().size();
    }  // # of files in dir

    // Information about each column
    @Override
    public String getColumnName(int col) {
        return columnNames[col];
    }

    @Override
    public Class getColumnClass(int col) {
        return columnClasses[col];
    }

    // The method that must actually return the value of each cell
    @Override
    public Object getValueAt(int row, int col) {
        QFile f = files().get(row);
        switch (col) {
        	case -1:
        		return f;
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