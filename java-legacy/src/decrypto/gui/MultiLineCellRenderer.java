/*
 * Decompiled with CFR 0.152.
 */
package decrypto.gui;

import java.awt.Component;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;

public class MultiLineCellRenderer
extends JTextArea
implements TableCellRenderer {
    public static final long serialVersionUID = 1001L;

    public MultiLineCellRenderer() {
        this.setLineWrap(true);
        this.setWrapStyleWord(true);
        this.setOpaque(true);
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (isSelected) {
            this.setForeground(table.getSelectionForeground());
            this.setBackground(table.getSelectionBackground());
        } else {
            this.setForeground(table.getForeground());
            this.setBackground(table.getBackground());
        }
        this.setFont(table.getFont());
        if (hasFocus) {
            this.setBorder(UIManager.getBorder("Table.focusCellHighlightBorder"));
            if (table.isCellEditable(row, column)) {
                this.setForeground(UIManager.getColor("Table.focusCellForeground"));
                this.setBackground(UIManager.getColor("Table.focusCellBackground"));
            }
        } else {
            this.setBorder(new EmptyBorder(1, 2, 1, 2));
        }
        this.setText(value == null ? "" : value.toString());
        return this;
    }

    public int getIdealHeight() {
        return this.getMinimumSize().height;
    }
}

