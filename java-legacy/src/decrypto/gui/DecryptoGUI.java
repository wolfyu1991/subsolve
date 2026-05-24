/*
 * Decompiled with CFR 0.152.
 */
package decrypto.gui;

import decrypto.DecryptoVersion;
import decrypto.Dict;
import decrypto.EnglishLanguage;
import decrypto.Finisher;
import decrypto.Language;
import decrypto.MapSet;
import decrypto.Puzzle;
import decrypto.Solution;
import decrypto.SolutionSet;
import decrypto.Solver;
import decrypto.SolverListener;
import decrypto.StopFlag;
import decrypto.Word;
import decrypto.dictattack.DecryptoParameters;
import decrypto.dictattack.DictionaryAttackSolver;
import decrypto.genetic.GAProblem;
import decrypto.genetic.GeneticSolver;
import decrypto.gui.DecryptoDictGUI;
import decrypto.gui.ExtensionFileFilter;
import decrypto.gui.HelpFrame;
import decrypto.gui.MultiLineCellRenderer;
import decrypto.util.GetOpt;
import decrypto.util.ParameterGUI;
import decrypto.util.ParameterListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.text.DefaultEditorKit;

public class DecryptoGUI
implements SolverListener {
    public String versionString = "8.5";
    SolutionSet set = new SolutionSet(500);
    JFrame frame;
    JTextArea cipherText = new JTextArea();
    JMenu menu;
    JTextField clues = new JTextField();
    MyTableModel tableModel = new MyTableModel();
    JTable solutions = new JTable(this.tableModel);
    JButton solveButton = new JButton("Solve");
    JButton clearButton = new JButton("Clear");
    JButton scrambleButton = new JButton("Encode");
    JProgressBar progressBar = new JProgressBar(0, 1000);
    JLabel statusLabel = new JLabel("Starting up");
    JLabel timerLabel = new JLabel("");
    JComboBox methodBox = new JComboBox<String>(new String[]{"Dictionary attack", "Dictionary attack++", "Genetic, trust spaces", "Genetic, find spaces"});
    JFileChooser fileChooser = new JFileChooser(System.getProperty("user.dir"));
    GetOpt opt = new GetOpt();
    Dict dict;
    Puzzle puzzle;
    static final int MAXPROGRESS = 1000;
    FocusTracker focusTracker = new FocusTracker();
    Language lang = new EnglishLanguage();
    ExtensionFileFilter txtFilter = new ExtensionFileFilter("txt", "Text files");
    ExtensionFileFilter datFilter = new ExtensionFileFilter("dat", "Dictionary files");
    JRadioButtonMenuItem[] setSizeMenuItems;
    JCheckBoxMenuItem allowIdentitySolveItem;
    JCheckBoxMenuItem allowIdentityScrambleItem;
    JCheckBoxMenuItem reduceTildesItem;
    JCheckBoxMenuItem onlyDictionaryWordsItem;
    String plainTextBeforeEncoding;
    String cluesBeforeEncoding;
    Solver solver;
    StopFlag stopFlag;
    boolean tableDirty;
    Mode mode = Mode.IDLE;
    JFrame parameterFrame;
    public boolean gotFullSolution = false;
    static final double partialSolutionPenalty = 0.0;

    public static void main(String[] args) {
        DecryptoGUI d = new DecryptoGUI();
        d.run(args);
    }

    public DecryptoGUI() {
        this.opt.addString('\u0000', "dictionary", "english-standard.dat", "Dictionary to use");
        this.opt.addString('c', "clues", "", "Clues, e.g.: A=C, M=R, JXJ=DAD");
        this.opt.addBoolean('h', "help", false, "Show help");
        this.frame = new JFrame("Decrypto " + this.versionString + " build " + DecryptoVersion.revision + ", eolson@mit.edu");
        int spacing = 5;
        Font fnt = new Font("Monospaced", 0, this.cipherText.getFont().getSize());
        this.clues.setFont(fnt);
        this.solutions.setFont(fnt);
        this.cipherText.setFont(fnt);
        this.cipherText.setLineWrap(true);
        this.cipherText.setWrapStyleWord(true);
        new EditPopupMenu(this.cipherText);
        new EditPopupMenu(this.clues);
        new EditPopupMenu(this.solutions);
        JPanel panel = new JPanel(new BorderLayout(spacing, spacing));
        panel.add((Component)DecryptoGUI.makeLabel("Clues", 'u'), "West");
        panel.add((Component)this.clues, "Center");
        JPanel commandPanel = new JPanel(new GridLayout(2, 1));
        JPanel p = new JPanel(new FlowLayout());
        p.add(this.methodBox);
        p.add(this.solveButton);
        commandPanel.add(p);
        p = new JPanel(new FlowLayout());
        p.add(this.clearButton);
        p.add(this.scrambleButton);
        commandPanel.add(p);
        panel.add((Component)commandPanel, "East");
        JPanel pp = new JPanel(new BorderLayout());
        panel.add((Component)pp, "South");
        JPanel puzzlePanel = new JPanel(new BorderLayout(spacing, spacing));
        puzzlePanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        puzzlePanel.add((Component)DecryptoGUI.makeLabel("Cipher Text", 'T'), "North");
        puzzlePanel.add((Component)new JScrollPane(this.cipherText), "Center");
        puzzlePanel.add((Component)panel, "South");
        JPanel panel2 = new JPanel(new BorderLayout(spacing, spacing));
        panel2.setBorder(new EmptyBorder(5, 5, 5, 5));
        panel2.add((Component)new JLabel("Solutions:"), "West");
        panel2.add((Component)this.progressBar, "Center");
        JPanel bottomPanel = new JPanel(new BorderLayout(spacing, spacing));
        bottomPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        bottomPanel.add((Component)panel2, "North");
        bottomPanel.add((Component)new JScrollPane(this.solutions), "Center");
        JSplitPane jsp = new JSplitPane(0, puzzlePanel, bottomPanel);
        jsp.setResizeWeight(0.3);
        jsp.setDividerLocation(jsp.getResizeWeight());
        jsp.setContinuousLayout(true);
        JMenuBar menuBar = new JMenuBar();
        this.frame.setJMenuBar(menuBar);
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        fileMenu.add(this.makeMenuItem("New Puzzle", 78, KeyStroke.getKeyStroke(78, 2), new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoGUI.this.newPuzzle();
            }
        }));
        fileMenu.add(this.makeMenuItem("Open Puzzle...", 79, KeyStroke.getKeyStroke(79, 2), new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoGUI.this.openPuzzle();
            }
        }));
        fileMenu.add(this.makeMenuItem("Save Puzzle", 83, KeyStroke.getKeyStroke(83, 2), new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoGUI.this.savePuzzle();
            }
        }));
        fileMenu.add(this.makeMenuItem("Quit", 81, KeyStroke.getKeyStroke(81, 2), new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        }));
        JMenu editMenu = new JMenu("Edit");
        editMenu.add(this.makeMenuItem("Cut", 84, KeyStroke.getKeyStroke(88, 2), new DefaultEditorKit.CutAction()));
        editMenu.add(this.makeMenuItem("Copy", 67, KeyStroke.getKeyStroke(67, 2), new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                if (DecryptoGUI.this.focusTracker.lastFocus == DecryptoGUI.this.solutions) {
                    DecryptoGUI.this.solutions.getActionMap().get(TransferHandler.getCopyAction().getValue("Name")).actionPerformed(new ActionEvent(DecryptoGUI.this.solutions, 1001, null));
                } else {
                    new DefaultEditorKit.CopyAction().actionPerformed(e);
                }
            }
        }));
        editMenu.add(this.makeMenuItem("Paste", 80, KeyStroke.getKeyStroke(86, 2), new DefaultEditorKit.PasteAction()));
        menuBar.add(editMenu);
        JMenu dictionaryMenu = new JMenu("Dictionary");
        menuBar.add(dictionaryMenu);
        dictionaryMenu.add(this.makeMenuItem("Choose Dictionary...", 0, null, new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoGUI.this.chooseDictionary();
            }
        }));
        dictionaryMenu.add(this.makeMenuItem("Dictionary Editor...", 0, null, new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoGUI.this.editDictionary();
            }
        }));
        JMenu advancedMenu = new JMenu("Advanced");
        menuBar.add(advancedMenu);
        this.allowIdentitySolveItem = new JCheckBoxMenuItem("Allow identity mappings (R=R) when solving", true);
        advancedMenu.add(this.allowIdentitySolveItem);
        this.allowIdentityScrambleItem = new JCheckBoxMenuItem("Allow identity mappings (R=R) when encoding", false);
        advancedMenu.add(this.allowIdentityScrambleItem);
        this.reduceTildesItem = new JCheckBoxMenuItem("Fewer tildes in solutions", true);
        advancedMenu.add(this.reduceTildesItem);
        this.onlyDictionaryWordsItem = new JCheckBoxMenuItem("Restrict solutions to only dictionary words", false);
        advancedMenu.add(this.onlyDictionaryWordsItem);
        JMenuItem toAllCapsItem = new JMenuItem("Convert puzzle to UPPER CASE");
        toAllCapsItem.addActionListener(new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                String s = DecryptoGUI.this.cipherText.getText().toUpperCase();
                DecryptoGUI.this.cipherText.setText(s);
            }
        });
        advancedMenu.add(toAllCapsItem);
        JMenu setSizeMenu = new JMenu("Maximum solutions");
        advancedMenu.add(setSizeMenu);
        int[] szs = new int[]{100, 500, 1000, 2000, 10000, 100000};
        this.setSizeMenuItems = new JRadioButtonMenuItem[szs.length];
        for (int i = 0; i < szs.length; ++i) {
            this.setSizeMenuItems[i] = new JRadioButtonMenuItem("" + szs[i]);
            this.setSizeMenuItems[i].addActionListener(new SetSizeActionListener(szs[i], this.setSizeMenuItems[i]));
            this.setSizeMenuItems[i].setSelected(i == 1);
            setSizeMenu.add(this.setSizeMenuItems[i]);
        }
        advancedMenu.add(this.makeMenuItem("Testing Options...", 0, null, new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoGUI.this.doTestOptions();
            }
        }));
        JMenu helpMenu = new JMenu("Help");
        menuBar.add(helpMenu);
        helpMenu.add(this.makeMenuItem("Help", 0, KeyStroke.getKeyStroke(112, 0), new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoGUI.this.doHelp();
            }
        }));
        helpMenu.add(this.makeMenuItem("About Decrypto", 0, null, new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoGUI.this.doAbout();
            }
        }));
        fileMenu.setMnemonic(70);
        editMenu.setMnemonic(69);
        dictionaryMenu.setMnemonic(68);
        helpMenu.setMnemonic(72);
        this.scrambleButton.setMnemonic(77);
        this.solveButton.setMnemonic(83);
        this.cipherText.setFocusAccelerator('T');
        this.clues.setFocusAccelerator('u');
        this.solveButton.addActionListener(new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoGUI.this.solve();
            }
        });
        this.scrambleButton.addActionListener(new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                DecryptoGUI.this.scramble();
            }
        });
        this.clearButton.addActionListener(new ActionListener(){

            public void actionPerformed(ActionEvent e) {
                if (DecryptoGUI.this.mode == Mode.SCRAMBLING) {
                    DecryptoGUI.this.cipherText.setText(DecryptoGUI.this.plainTextBeforeEncoding);
                    DecryptoGUI.this.clues.setText(DecryptoGUI.this.cluesBeforeEncoding);
                    DecryptoGUI.this.setMode(Mode.IDLE);
                } else {
                    DecryptoGUI.this.newPuzzle();
                }
            }
        });
        this.solutions.getColumnModel().getColumn(2).setCellRenderer(new MultiLineCellRenderer());
        this.solutions.addComponentListener(new TableComponentListener());
        TableColumnModel tcm = this.solutions.getColumnModel();
        tcm.getColumn(0).setMaxWidth(100);
        tcm.getColumn(1).setMaxWidth(100);
        this.frame.setLayout(new BorderLayout(spacing, spacing));
        this.frame.add((Component)jsp, "Center");
        JPanel labelPanel = new JPanel(new BorderLayout());
        labelPanel.add((Component)this.statusLabel, "West");
        labelPanel.add((Component)this.timerLabel, "East");
        this.frame.add((Component)labelPanel, "South");
        new TimerThread().start();
        this.frame.addWindowListener(new WindowAdapter(){

            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        this.solutions.addFocusListener(this.focusTracker);
        this.cipherText.addFocusListener(this.focusTracker);
        this.clues.addFocusListener(this.focusTracker);
        Image icon = Toolkit.getDefaultToolkit().getImage(this.getClass().getResource("/decrypto.png"));
        this.frame.setIconImage(icon);
        this.fileChooser.setFileFilter(this.txtFilter);
        this.fileChooser.setFileFilter(this.datFilter);
    }

    static JLabel makeLabel(String text, char accel) {
        JLabel jl = new JLabel(text);
        jl.setDisplayedMnemonic(accel);
        return jl;
    }

    JMenuItem makeMenuItem(Action a, String s, int ke, KeyStroke ka) {
        JMenuItem jmi = new JMenuItem(a);
        jmi.setText(s);
        jmi.setMnemonic(ke);
        jmi.setAccelerator(ka);
        return jmi;
    }

    void doTestOptions() {
        if (this.parameterFrame != null) {
            this.parameterFrame.setVisible(true);
            return;
        }
        ParameterGUI pg = new ParameterGUI();
        pg.addCheckBoxes("prunerootnode", "Prune candidates at root node", DecryptoParameters.pruneRootNode, "pruneeachnode", "Prune candidates at each node", DecryptoParameters.pruneEachNode);
        pg.addCheckBoxes("singleletters", "Enable single letters", DecryptoParameters.enableSingleLetters);
        pg.addChoice("verbosity", "Verbosity", new String[]{"Silent", "Verbose", "Verboser"}, DecryptoParameters.verbosity);
        pg.addChoice("planner", "Planner", new String[]{"Static", "Greedy", "Random"}, 1);
        pg.addInt("maxreduceiter", "Maximum reduce iterations", DecryptoParameters.maxReduceIterations);
        pg.addInt("maxworddisable", "Maximum random word disable", DecryptoParameters.maxRandomWordDisable);
        pg.addListener(new MyParameterListener());
        this.parameterFrame = new JFrame("Decrypto Parameters");
        this.parameterFrame.setLayout(new BorderLayout());
        this.parameterFrame.add((Component)pg.getPanel(), "Center");
        this.parameterFrame.pack();
        this.parameterFrame.setVisible(true);
    }

    void doAbout() {
        HelpFrame hf = new HelpFrame("About Decrypto " + this.versionString, "about.html", 300, 250);
    }

    void doHelp() {
        HelpFrame hf = new HelpFrame("Decrypto " + this.versionString + " Help", "helpfile.html", 640, 480);
    }

    void editDictionary() {
        DecryptoDictGUI ddg = new DecryptoDictGUI();
    }

    void newPuzzle() {
        this.setMode(Mode.IDLE);
        this.cipherText.setText("");
        this.clues.setText("");
        this.set.reset();
        this.tableUpdated();
        this.cipherText.requestFocus();
        this.plainTextBeforeEncoding = null;
    }

    void openPuzzle(String path) {
        StringBuffer tx = new StringBuffer();
        StringBuffer cl = new StringBuffer();
        try {
            String line;
            BufferedReader ins = new BufferedReader(new FileReader(path));
            while ((line = ins.readLine()) != null) {
                if (line.contains("=")) {
                    cl.append(line + " ");
                    continue;
                }
                tx.append(line + "\n");
            }
        }
        catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Couldn't load puzzle: " + ex, "Error", 0);
        }
        this.cipherText.setText(tx.toString().trim());
        this.clues.setText(cl.toString().trim());
        this.set.reset();
        this.tableUpdated();
        this.plainTextBeforeEncoding = null;
    }

    void openPuzzle() {
        this.fileChooser.setFileFilter(this.txtFilter);
        int res = this.fileChooser.showOpenDialog(this.frame);
        if (res != 0) {
            return;
        }
        String path = this.fileChooser.getSelectedFile().getAbsolutePath();
        this.openPuzzle(path);
    }

    void savePuzzle() {
        this.fileChooser.setFileFilter(this.txtFilter);
        int res = this.fileChooser.showSaveDialog(this.frame);
        if (res != 0) {
            return;
        }
        String path = this.fileChooser.getSelectedFile().getAbsolutePath();
        try {
            BufferedWriter outs = new BufferedWriter(new FileWriter(path));
            outs.write(this.cipherText.getText() + "\n");
            outs.write(this.clues.getText() + "\n");
            outs.close();
        }
        catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Couldn't save puzzle: " + ex, "Error", 0);
        }
    }

    void chooseDictionary() {
        this.fileChooser.setFileFilter(this.datFilter);
        int res = this.fileChooser.showOpenDialog(this.frame);
        if (res != 0) {
            return;
        }
        String newdictpath = this.fileChooser.getSelectedFile().getAbsolutePath();
        try {
            this.dict = new Dict(newdictpath);
            this.statusLabel.setText("Dictionary \"" + newdictpath + "\" loaded");
        }
        catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Couldn't load dictionary: " + ex, "Error", 0);
            this.statusLabel.setText("Couldn't load dictionary \"" + newdictpath + "\": " + ex);
            return;
        }
    }

    protected JMenuItem makeMenuItem(String name, int mnemonic, KeyStroke ks, ActionListener listener) {
        JMenuItem jmi = new JMenuItem(name, mnemonic);
        jmi.addActionListener(listener);
        jmi.setAccelerator(ks);
        return jmi;
    }

    public void run(String[] args) {
        this.opt.parse(args);
        if (this.opt.getBoolean("help")) {
            this.opt.doHelp();
            return;
        }
        try {
            this.dict = new Dict(this.opt.getString("dictionary"));
            this.statusLabel.setText("Dictionary \"" + this.opt.getString("dictionary") + "\" loaded");
        }
        catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Couldn't open dictionary: " + ex, "Error", 0);
            return;
        }
        ArrayList<String> extraArgs = this.opt.getExtraArgs();
        if (extraArgs.size() == 1 && new File(extraArgs.get(0)).exists()) {
            this.openPuzzle(extraArgs.get(0));
        } else {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < extraArgs.size(); ++i) {
                sb.append(extraArgs.get(i) + " ");
            }
            this.cipherText.setText(sb.toString().trim());
            this.clues.setText(this.opt.getString("clues"));
        }
        this.puzzle = new Puzzle(this.dict, this.cipherText.getText().trim(), this.clues.getText(), true, true);
        this.frame.setSize(600, 400);
        this.frame.setVisible(true);
    }

    void scramble() {
        if (this.mode == Mode.IDLE) {
            this.plainTextBeforeEncoding = this.cipherText.getText();
            this.cluesBeforeEncoding = this.clues.getText();
            this.setMode(Mode.SCRAMBLING);
        }
        this.puzzle = new Puzzle(this.dict, this.plainTextBeforeEncoding, this.cluesBeforeEncoding, true, false);
        this.puzzle.scramble(this.allowIdentityScrambleItem.isSelected());
        this.cipherText.setText(this.puzzle.cipherText);
        this.clues.setText(this.puzzle.clues);
    }

    synchronized void solve() {
        if (this.mode == Mode.SOLVING) {
            this.solver.stop();
            this.setMode(Mode.IDLE);
            return;
        }
        this.setMode(Mode.SOLVING);
        this.set.reset();
        this.tableUpdated();
        switch (this.methodBox.getSelectedIndex()) {
            case 0: {
                this.puzzle = new Puzzle(this.dict, this.cipherText.getText(), this.clues.getText(), true, this.allowIdentitySolveItem.isSelected());
                this.solver = new DictionaryAttackSolver(this.puzzle);
                break;
            }
            case 1: {
                this.puzzle = new Puzzle(this.dict, this.cipherText.getText(), this.clues.getText(), true, this.allowIdentitySolveItem.isSelected());
                this.solver = new DictionaryAttackSolver(this.puzzle);
                ((DictionaryAttackSolver)this.solver).runForever = true;
                break;
            }
            case 2: {
                this.puzzle = new Puzzle(this.dict, this.cipherText.getText(), this.clues.getText(), true, this.allowIdentitySolveItem.isSelected());
                this.solver = new GeneticSolver(new GAProblem(this.puzzle));
                break;
            }
            case 3: {
                this.puzzle = new Puzzle(this.dict, this.cipherText.getText(), this.clues.getText(), false, this.allowIdentitySolveItem.isSelected());
                this.solver = new GeneticSolver(new GAProblem(this.puzzle));
            }
        }
        this.solver.addListener(this);
        this.solver.start(Double.MAX_VALUE);
    }

    void setMode(Mode mode) {
        this.mode = mode;
        switch (mode) {
            case SOLVING: {
                this.solveButton.setText("Stop");
                this.cipherText.setEditable(false);
                this.clues.setEditable(false);
                this.cipherText.setEnabled(false);
                this.scrambleButton.setEnabled(false);
                this.clearButton.setText("Clear");
                this.clearButton.setEnabled(false);
                break;
            }
            case IDLE: {
                this.progressBar.setValue(1000);
                this.solveButton.setText("Solve");
                this.cipherText.setEditable(true);
                this.cipherText.setEnabled(true);
                this.scrambleButton.setEnabled(true);
                this.clues.setEditable(true);
                this.clearButton.setText("Clear");
                this.clearButton.setEnabled(true);
                this.scrambleButton.setText("Encode");
                break;
            }
            case SCRAMBLING: {
                this.cipherText.setEditable(false);
                this.clues.setEditable(false);
                this.scrambleButton.setEnabled(true);
                this.clearButton.setText("Revert");
                this.scrambleButton.setText("Reencode");
                this.clearButton.setEnabled(true);
            }
        }
    }

    public void solverMessage(String s) {
        this.statusLabel.setText(s);
    }

    public void solverFinished(double elapsedTime) {
        this.progressBar.setValue(1000);
        this.setMode(Mode.IDLE);
        String msg = String.format("Finished (%d solutions)", this.set.size());
        this.statusLabel.setText(msg);
        this.tableUpdated();
    }

    public void solverSolution(MapSet mapset, int[] spaces, boolean fullSolution, double score) {
        if (this.onlyDictionaryWordsItem.isSelected()) {
            int[] map = mapset.makeMap();
            for (Word w : this.puzzle.words) {
                String s = this.puzzle.lang.bytesToString(w.word, map);
                if (this.puzzle.dict.isInDictionary(s)) continue;
                return;
            }
        }
        if (fullSolution) {
            this.gotFullSolution = true;
        }
        this.add(mapset, spaces, score + (fullSolution ? 0.0 : 0.0));
    }

    void add(MapSet mapset, int[] spaces, double score) {
        if (this.set.wouldAdd(score)) {
            if (this.methodBox.getSelectedIndex() < 2 && this.reduceTildesItem.isSelected()) {
                Finisher f = new Finisher(this.puzzle, mapset);
                mapset = f.bruteForce();
                score = this.lang.computeScore(this.puzzle.cipherBytes, mapset) / (double)this.puzzle.numCiphertextLetters;
            }
            String s = this.puzzle.lang.translateString(this.puzzle.rawCipherText, mapset, spaces);
            Solution sol = new Solution(s, mapset, score);
            this.set.add(sol);
            this.tableDirty = true;
        }
    }

    public void solverProgress(double progress) {
        this.progressBar.setValue((int)(progress * 1000.0));
    }

    void tableUpdated() {
        int row = this.solutions.getSelectedRow();
        this.tableModel.fireTableDataChanged();
        if (row >= 0 && this.solutions.getRowCount() > 0) {
            this.solutions.setRowSelectionInterval(row, row);
        }
        this.tableDirty = false;
    }

    class FocusTracker
    implements FocusListener {
        Component lastFocus = null;

        FocusTracker() {
        }

        public void focusGained(FocusEvent e) {
            this.lastFocus = e.getComponent();
        }

        public void focusLost(FocusEvent e) {
        }
    }

    public class TableComponentListener
    extends ComponentAdapter {
        public void componentResized(ComponentEvent e) {
            if (DecryptoGUI.this.solutions.getModel().getRowCount() == 0) {
                return;
            }
            TableCellRenderer renderer = DecryptoGUI.this.solutions.getCellRenderer(0, 2);
            DecryptoGUI.this.solutions.setRowHeight(((MultiLineCellRenderer)renderer).getIdealHeight());
        }
    }

    class MyTableModel
    extends AbstractTableModel {
        public static final long serialVersionUID = 1001L;

        MyTableModel() {
        }

        public int getRowCount() {
            return DecryptoGUI.this.set.size();
        }

        public int getColumnCount() {
            return 3;
        }

        public Object getValueAt(int row, int col) {
            Solution s = DecryptoGUI.this.set.getElement(row);
            if (s == null) {
                return "";
            }
            switch (col) {
                case 0: {
                    return "" + (row + 1);
                }
                case 1: {
                    return String.format("%.4f", s.score);
                }
                case 2: {
                    return s.solution;
                }
            }
            return "";
        }

        public String getColumnName(int col) {
            switch (col) {
                case 0: {
                    return "Rank";
                }
                case 1: {
                    return "Score";
                }
                case 2: {
                    return "Solution";
                }
            }
            return "?";
        }
    }

    class TimerThread
    extends Thread {
        TimerThread() {
            this.setDaemon(true);
        }

        public void run() {
            double lastworst = 0.0;
            while (true) {
                double worst;
                Solver tsolver;
                try {
                    Thread.sleep(250L);
                }
                catch (InterruptedException ex) {
                    // empty catch block
                }
                if (DecryptoGUI.this.tableDirty) {
                    DecryptoGUI.this.tableUpdated();
                }
                if ((tsolver = DecryptoGUI.this.solver) != null) {
                    double elapsedTime = tsolver.getElapsedTime();
                    DecryptoGUI.this.timerLabel.setText(String.format("%.3f seconds", elapsedTime));
                }
                if ((worst = DecryptoGUI.this.set.getWorst()) == lastworst) continue;
                lastworst = worst;
                DecryptoGUI.this.tableUpdated();
            }
        }
    }

    class MyParameterListener
    implements ParameterListener {
        MyParameterListener() {
        }

        public void parameterChanged(ParameterGUI pg, String name) {
            DecryptoParameters.pruneRootNode = pg.gb("prunerootnode");
            DecryptoParameters.pruneEachNode = pg.gb("pruneeachnode");
            DecryptoParameters.enableSingleLetters = pg.gb("singleletters");
            DecryptoParameters.verbosity = pg.gi("verbosity");
            DecryptoParameters.maxReduceIterations = pg.gi("maxreduceiter");
            DecryptoParameters.maxRandomWordDisable = pg.gi("maxworddisable");
        }
    }

    class SetSizeActionListener
    implements ActionListener {
        int sz;
        JRadioButtonMenuItem jmi;

        public SetSizeActionListener(int sz, JRadioButtonMenuItem jmi) {
            this.sz = sz;
            this.jmi = jmi;
        }

        public void actionPerformed(ActionEvent e) {
            DecryptoGUI.this.set = new SolutionSet(this.sz);
            for (int i = 0; i < DecryptoGUI.this.setSizeMenuItems.length; ++i) {
                DecryptoGUI.this.setSizeMenuItems[i].setSelected(false);
            }
            this.jmi.setSelected(true);
            DecryptoGUI.this.tableUpdated();
        }
    }

    class EditPopupMenu {
        JPopupMenu popupMenu = new JPopupMenu();
        JComponent parent;

        EditPopupMenu(JComponent parent) {
            this.parent = parent;
            JMenuItem jmi = new JMenuItem("Cut");
            this.popupMenu.add(jmi);
            jmi.addActionListener(new ActionListener(){

                public void actionPerformed(ActionEvent e) {
                    EditPopupMenu.this.parent.getTransferHandler();
                    TransferHandler.getCutAction().actionPerformed(new ActionEvent(EditPopupMenu.this.parent, 1001, null));
                }
            });
            jmi = new JMenuItem("Copy");
            this.popupMenu.add(jmi);
            jmi.addActionListener(new ActionListener(){

                public void actionPerformed(ActionEvent e) {
                    EditPopupMenu.this.parent.getTransferHandler();
                    TransferHandler.getCopyAction().actionPerformed(new ActionEvent(EditPopupMenu.this.parent, 1001, null));
                }
            });
            jmi = new JMenuItem("Paste");
            this.popupMenu.add(jmi);
            jmi.addActionListener(new ActionListener(){

                public void actionPerformed(ActionEvent e) {
                    EditPopupMenu.this.parent.getTransferHandler();
                    TransferHandler.getPasteAction().actionPerformed(new ActionEvent(EditPopupMenu.this.parent, 1001, null));
                }
            });
            parent.addMouseListener(new MouseAdapter(){

                public void mousePressed(MouseEvent e) {
                    if (e.getButton() == 3) {
                        EditPopupMenu.this.invoke(e);
                    }
                }
            });
        }

        void invoke(MouseEvent e) {
            this.popupMenu.show(this.parent, e.getX(), e.getY());
        }
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    static enum Mode {
        IDLE,
        SOLVING,
        SCRAMBLING;

    }
}

