/*
 * Decompiled with CFR 0.152.
 */
package decrypto.gui;

import decrypto.Dict;
import decrypto.EnglishLanguage;
import decrypto.Language;
import decrypto.Word;
import decrypto.gui.DecryptoGUI;
import decrypto.gui.ExtensionFileFilter;
import decrypto.util.ParameterGUI;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;

public class DecryptoDictGUI {
    public static final long serialVersionUID = 1001L;
    JMenu menu;
    JFrame frame = new JFrame("Decrypto Dictionary Editor");
    ArrayList<Word> words = new ArrayList();
    ArrayList<Word> filteredWords = new ArrayList();
    WordTableModel tableModel = new WordTableModel();
    JTable wordTable = new JTable(this.tableModel);
    JLabel statusLabel = new JLabel("No words loaded");
    JLabel progressLabel = new JLabel("");
    JFileChooser fileChooser = new JFileChooser(System.getProperty("user.dir"));
    ExtensionFileFilter txtFilter = new ExtensionFileFilter("txt", "Text files");
    ExtensionFileFilter datFilter = new ExtensionFileFilter("dat", "Dictionary files");
    ExtensionFileFilter txtDatFilter = new ExtensionFileFilter(new String[]{"txt", "dat"}, "Text files and dictionary files");
    ParameterGUI pg = new ParameterGUI();
    Language lang;
    JTextField filterField = new JTextField(15);

    public static void main(String[] args) {
        DecryptoDictGUI d = new DecryptoDictGUI();
    }

    public DecryptoDictGUI() {
        this.pg.addString("langclass", "Language Class", "decrypto.EnglishLanguage");
        this.lang = Dict.getLanguage(this.pg.gs("langclass"));
        Font fnt = new Font("Monospaced", 0, this.statusLabel.getFont().getSize());
        this.wordTable.setFont(fnt);
        this.wordTable.addKeyListener(new MyTableKeyListener());
        JMenuBar menuBar = new JMenuBar();
        this.frame.setJMenuBar(menuBar);
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        fileMenu.add(this.makeMenuItem("New", 78, null, new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoDictGUI.this.clear();
            }
        }));
        fileMenu.add(this.makeMenuItem("Import words...", 73, null, new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoDictGUI.this.importWords();
            }
        }));
        fileMenu.add(this.makeMenuItem("Save dictionary...", 83, null, new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoDictGUI.this.saveDictionary();
            }
        }));
        fileMenu.add(this.makeMenuItem("Save word list...", 0, null, new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoDictGUI.this.saveWordList();
            }
        }));
        fileMenu.addSeparator();
        fileMenu.add(this.makeMenuItem("Quit", 81, KeyStroke.getKeyStroke(81, 2), new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoDictGUI.this.frame.dispose();
            }
        }));
        JMenu sortMenu = new JMenu("Sort");
        menuBar.add(sortMenu);
        sortMenu.add(this.makeMenuItem("Alphabetical Order", 65, null, new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                Collections.sort(DecryptoDictGUI.this.words, new Word.TextComparator());
                DecryptoDictGUI.this.update();
            }
        }));
        sortMenu.add(this.makeMenuItem("Pattern Order", 68, null, new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                Collections.sort(DecryptoDictGUI.this.words, new Word.PatternComparator());
                DecryptoDictGUI.this.update();
            }
        }));
        JMenu toolsMenu = new JMenu("Tools");
        menuBar.add(toolsMenu);
        toolsMenu.add(this.makeMenuItem("Add words manually...", 65, null, new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoDictGUI.this.addWordsManually();
            }
        }));
        this.frame.addWindowListener(new WindowAdapter(){

            public void windowClosing(WindowEvent e) {
                DecryptoDictGUI.this.frame.dispose();
            }
        });
        this.frame.setLayout(new BorderLayout());
        this.frame.add((Component)new JScrollPane(this.wordTable), "Center");
        JPanel panel = new JPanel(new BorderLayout());
        panel.add((Component)this.statusLabel, "West");
        JPanel panel2 = new JPanel(new BorderLayout());
        panel2.add((Component)DecryptoGUI.makeLabel("   Filter: ", 'f'), "West");
        panel2.add((Component)this.filterField, "Center");
        JButton clearFilterButton = new JButton("Clear");
        clearFilterButton.addActionListener(new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoDictGUI.this.filterField.setText("");
                DecryptoDictGUI.this.update();
            }
        });
        panel2.add((Component)clearFilterButton, "East");
        this.filterField.setFocusAccelerator('f');
        this.filterField.getDocument().addDocumentListener(new DocumentListener(){

            public void changedUpdate(DocumentEvent e) {
                DecryptoDictGUI.this.update();
            }

            public void insertUpdate(DocumentEvent e) {
                DecryptoDictGUI.this.update();
            }

            public void removeUpdate(DocumentEvent e) {
                DecryptoDictGUI.this.update();
            }
        });
        panel.add((Component)panel2, "Center");
        panel.add((Component)this.progressLabel, "South");
        this.frame.add((Component)panel, "South");
        this.frame.add((Component)this.pg.getPanel(), "North");
        TableColumnModel tcm = this.wordTable.getColumnModel();
        tcm.getColumn(0).setMaxWidth(100);
        Image icon = Toolkit.getDefaultToolkit().getImage(this.getClass().getResource("/decrypto.png"));
        this.frame.setIconImage(icon);
        this.fileChooser.setFileFilter(this.txtFilter);
        this.fileChooser.setFileFilter(this.datFilter);
        this.fileChooser.setFileFilter(this.txtDatFilter);
        this.frame.setSize(400, 600);
        this.frame.setVisible(true);
        this.update();
    }

    void addWordsManually() {
        new WordEditor();
    }

    void update() {
        String s = this.filterField.getText().toUpperCase();
        s = this.lang.bytesToString(this.lang.stringToBytes(s));
        int currentPosition = this.wordTable.getSelectedRow();
        if (currentPosition < 0 || currentPosition >= this.words.size()) {
            currentPosition = 0;
        }
        this.filteredWords.clear();
        for (int i = 0; i < this.words.size(); ++i) {
            Word w = this.words.get(i);
            if (!w.text.startsWith(s)) continue;
            this.filteredWords.add(w);
        }
        this.statusLabel.setText("Size: " + this.words.size() + " words");
        if (s.length() == 0) {
            this.progressLabel.setText("");
        } else {
            this.progressLabel.setText(String.format("Filter: %d of %d words begin with %s", this.filteredWords.size(), this.words.size(), s));
        }
        this.tableModel.fireTableDataChanged();
    }

    void clear() {
        this.words = new ArrayList();
        this.update();
    }

    void importWords() {
        this.fileChooser.setFileFilter(this.txtDatFilter);
        int res = this.fileChooser.showOpenDialog(this.frame);
        if (res != 0) {
            return;
        }
        String path = this.fileChooser.getSelectedFile().getAbsolutePath();
        if (path.toLowerCase().endsWith(".dat")) {
            this.loadFromDictionary(path);
        } else {
            this.loadFromWordList(path);
        }
        this.progressLabel.setText("Words imported.");
    }

    void loadFromDictionary() {
        this.fileChooser.setFileFilter(this.datFilter);
        int res = this.fileChooser.showOpenDialog(this.frame);
        if (res != 0) {
            return;
        }
        String path = this.fileChooser.getSelectedFile().getAbsolutePath();
        this.loadFromDictionary(path);
    }

    void loadFromDictionary(String path) {
        try {
            Dict d = new Dict(path);
            this.lang = d.getLanguage();
            ArrayList<byte[]> pats = d.readAll();
            for (int i = 0; i < pats.size(); ++i) {
                this.words.add(new Word(this.lang.bytesToString(pats.get(i)), 0, pats.get(i)));
            }
        }
        catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Couldn't read dictionary: " + ex, "Error", 0);
        }
        this.words = Dict.sortAndPruneWords(this.words);
        this.update();
    }

    void loadFromWordList() {
        this.fileChooser.setFileFilter(this.txtFilter);
        int res = this.fileChooser.showOpenDialog(this.frame);
        if (res != 0) {
            return;
        }
        String path = this.fileChooser.getSelectedFile().getAbsolutePath();
        this.loadFromWordList(path);
    }

    void loadFromWordList(String path) {
        try {
            String line;
            FileReader fr = new FileReader(path);
            BufferedReader ins = new BufferedReader(fr);
            this.lang = Dict.getLanguage(this.pg.gs("langclass"));
            while ((line = ins.readLine()) != null) {
                byte[] bs;
                if ((line = line.trim()).length() == 0 || line.charAt(0) == '#' || (bs = this.lang.stringToBytes(line)) == null) continue;
                this.words.add(new Word(this.lang.bytesToString(bs), 0, bs));
            }
        }
        catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Couldn't read word list: " + ex, "Error", 0);
        }
        this.words = Dict.sortAndPruneWords(this.words);
        this.update();
    }

    void saveWordList() {
        this.fileChooser.setFileFilter(this.txtFilter);
        int res = this.fileChooser.showSaveDialog(this.frame);
        if (res != 0) {
            return;
        }
        String path = this.fileChooser.getSelectedFile().getAbsolutePath();
        if (path.endsWith(".dat")) {
            path = path.substring(0, path.length() - 4) + ".txt";
            System.out.println(path);
            this.fileChooser.setSelectedFile(new File(path));
        }
        try {
            BufferedWriter outs = new BufferedWriter(new FileWriter(path));
            for (int i = 0; i < this.words.size(); ++i) {
                outs.write(this.words.get((int)i).text);
                outs.newLine();
            }
            outs.close();
        }
        catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Couldn't write word list: " + ex, "Error", 0);
        }
    }

    void saveDictionary() {
        this.fileChooser.setFileFilter(this.datFilter);
        int res = this.fileChooser.showSaveDialog(this.frame);
        if (res != 0) {
            return;
        }
        String path = this.fileChooser.getSelectedFile().getAbsolutePath();
        Dict dict = new Dict();
        dict.properties.put("langclass", this.pg.gs("langclass"));
        dict.properties.put("version", "2");
        try {
            dict.write(path, this.words);
        }
        catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Couldn't write dictionary: " + ex, "Error", 0);
        }
    }

    protected JMenuItem makeMenuItem(String name, int mnemonic, KeyStroke ks, ActionListener listener) {
        JMenuItem jmi = new JMenuItem(name, mnemonic);
        jmi.addActionListener(listener);
        jmi.setAccelerator(ks);
        return jmi;
    }

    class MyTableKeyListener
    extends KeyAdapter {
        MyTableKeyListener() {
        }

        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == 127 || e.getKeyCode() == 8) {
                int[] idxs = DecryptoDictGUI.this.wordTable.getSelectedRows();
                HashSet<Word> deletewords = new HashSet<Word>();
                for (int j = 0; j < idxs.length; ++j) {
                    deletewords.add(DecryptoDictGUI.this.filteredWords.get(idxs[j]));
                }
                ArrayList<Word> newwords = new ArrayList<Word>();
                for (int i = 0; i < DecryptoDictGUI.this.words.size(); ++i) {
                    Word w = DecryptoDictGUI.this.words.get(i);
                    if (deletewords.contains(w)) continue;
                    newwords.add(w);
                }
                DecryptoDictGUI.this.words = newwords;
                DecryptoDictGUI.this.update();
            }
        }
    }

    class WordTableModel
    extends AbstractTableModel {
        public static final long serialVersionUID = 1001L;

        WordTableModel() {
        }

        public int getRowCount() {
            return DecryptoDictGUI.this.filteredWords.size();
        }

        public int getColumnCount() {
            return 2;
        }

        public Object getValueAt(int row, int col) {
            Word w = DecryptoDictGUI.this.filteredWords.get(row);
            switch (col) {
                case 0: {
                    return "" + (row + 1);
                }
                case 1: {
                    return w.text;
                }
            }
            return "";
        }

        public String getColumnName(int col) {
            switch (col) {
                case 0: {
                    return "Row";
                }
                case 1: {
                    return "Word";
                }
            }
            return "?";
        }
    }

    class WordEditor {
        JFrame wframe;
        JTextArea textArea = new JTextArea();
        JButton addButton = new JButton("Add");
        JButton cancelButton = new JButton("Cancel");

        WordEditor() {
            this.wframe = new JFrame("Add words");
            this.wframe.setLayout(new BorderLayout());
            this.wframe.add((Component)new JLabel("Type in new words, one per line."), "North");
            JPanel p = new JPanel(new GridLayout(1, 2));
            p.add(this.cancelButton);
            p.add(this.addButton);
            this.wframe.add((Component)this.textArea, "Center");
            this.wframe.add((Component)p, "South");
            this.wframe.setSize(400, 400);
            this.wframe.setVisible(true);
            this.wframe.addWindowListener(new WindowAdapter(){

                public void windowClosing(WindowEvent e) {
                    WordEditor.this.wframe.dispose();
                }
            });
            this.cancelButton.addActionListener(new ActionListener(){

                public void actionPerformed(ActionEvent e) {
                    WordEditor.this.wframe.dispose();
                }
            });
            this.addButton.addActionListener(new ActionListener(){

                public void actionPerformed(ActionEvent e) {
                    if (DecryptoDictGUI.this.lang == null) {
                        DecryptoDictGUI.this.lang = new EnglishLanguage();
                    }
                    String[] t = WordEditor.this.textArea.getText().split("\\s+");
                    for (int i = 0; i < t.length; ++i) {
                        byte[] bs;
                        if (t[i].charAt(0) == '#' || (bs = DecryptoDictGUI.this.lang.stringToBytes(t[i])) == null) continue;
                        DecryptoDictGUI.this.words.add(new Word(DecryptoDictGUI.this.lang.bytesToString(bs), 0, bs));
                    }
                    DecryptoDictGUI.this.words = Dict.sortAndPruneWords(DecryptoDictGUI.this.words);
                    DecryptoDictGUI.this.update();
                    WordEditor.this.wframe.dispose();
                }
            });
        }
    }
}

