package org.q3s.p2p.client.view.components;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

public class TabelaCellRenderer extends DefaultTableCellRenderer {
	static final Color fundo = Color.white;
	static final Color azul = new Color(225, 235, 255);

	public Component getTableCellRendererComponent(JTable table, java.lang.Object value, boolean isSelected, boolean hasFocus, int row, int column) {

	      Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
	      cell.setBackground(azul);
	      return cell;
	}
}