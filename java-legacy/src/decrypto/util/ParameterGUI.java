/*
 * Decompiled with CFR 0.152.
 */
package decrypto.util;

import decrypto.util.ParameterListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class ParameterGUI {
    HashMap<String, PValue> parammap = new HashMap();
    JPanel panel = new JPanel(new GridBagLayout());
    int row = 0;
    GridBagConstraints gA;
    GridBagConstraints gB;
    GridBagConstraints gC;
    GridBagConstraints gD;
    GridBagConstraints gBC;
    GridBagConstraints gCD;
    GridBagConstraints gBCD;
    GridBagConstraints gABCD;
    ArrayList<ParameterListener> listeners = new ArrayList();
    boolean showvars;

    static void setupJTextField(JTextField jtf) {
        JTFListener l = new JTFListener(jtf);
        jtf.addActionListener(l);
        jtf.addCaretListener(l);
        jtf.addKeyListener(l);
    }

    static final int parseInteger(String s, int val) {
        try {
            return Integer.parseInt(s);
        }
        catch (Exception ex) {
            return val;
        }
    }

    static final double parseDouble(String s, double val) {
        try {
            return Double.parseDouble(s);
        }
        catch (Exception ex) {
            return val;
        }
    }

    public ParameterGUI() {
        this(true);
    }

    public ParameterGUI(boolean showvars) {
        this.showvars = showvars;
        this.gA = new GridBagConstraints();
        this.gA.gridx = 0;
        this.gA.weightx = 0.05;
        this.gA.fill = 2;
        this.gB = new GridBagConstraints();
        this.gB.gridx = 1;
        this.gB.weightx = 1.0;
        this.gB.fill = 2;
        this.gC = new GridBagConstraints();
        this.gC.gridx = 2;
        this.gC.weightx = 0.1;
        this.gC.fill = 2;
        this.gD = new GridBagConstraints();
        this.gD.gridx = 3;
        this.gD.weightx = 0.05;
        this.gD.anchor = 10;
        this.gBC = new GridBagConstraints();
        this.gBC.gridx = 1;
        this.gBC.gridwidth = 2;
        this.gBC.weightx = this.gB.weightx + this.gC.weightx;
        this.gBC.fill = 2;
        this.gCD = new GridBagConstraints();
        this.gCD.gridx = 2;
        this.gCD.gridwidth = 2;
        this.gCD.weightx = this.gC.weightx + this.gD.weightx;
        this.gCD.fill = 2;
        this.gCD.anchor = 13;
        this.gBCD = new GridBagConstraints();
        this.gBCD.gridx = 1;
        this.gBCD.gridwidth = 3;
        this.gBCD.weightx = this.gB.weightx + this.gC.weightx + this.gD.weightx;
        this.gBCD.fill = 2;
        this.gBCD.anchor = 13;
        this.gABCD = new GridBagConstraints();
        this.gABCD.gridx = 0;
        this.gABCD.gridwidth = 4;
        this.gABCD.weightx = this.gA.weightx + this.gB.weightx + this.gC.weightx + this.gD.weightx;
        this.gABCD.fill = 2;
    }

    protected void notifyListeners(String name) {
        for (ParameterListener pl : this.listeners) {
            pl.parameterChanged(this, name);
        }
    }

    public void addListener(ParameterListener l) {
        this.listeners.add(l);
    }

    public void addInt(String name, String desc, int value) {
        IntegerValue val = new IntegerValue(name, desc, -2147483647, Integer.MAX_VALUE, value);
        this.parammap.put(name, val);
        this.gA.gridy = this.row;
        this.gBCD.gridy = this.row++;
        this.panel.add((Component)new JLabel(desc), this.gA);
        this.panel.add((Component)val.getTextField(), this.gBCD);
    }

    public void addIntSlider(String name, String desc, int min, int max, int value) {
        IntegerValue val = new IntegerValue(name, desc, min, max, value);
        this.parammap.put(name, val);
        this.gA.gridy = this.row;
        this.gBC.gridy = this.row;
        this.gD.gridy = this.row++;
        this.panel.add((Component)new JLabel(desc), this.gA);
        this.panel.add((Component)val.getSlider(), this.gBC);
        this.panel.add((Component)val.getLabel(), this.gD);
    }

    public void addDouble(String name, String desc, double value) {
        DoubleValue val = new DoubleValue(name, desc, -1.7976931348623157E308, Double.MAX_VALUE, value);
        this.parammap.put(name, val);
        this.gA.gridy = this.row;
        this.gBCD.gridy = this.row++;
        this.panel.add((Component)new JLabel(desc), this.gA);
        this.panel.add((Component)val.getTextField(), this.gBCD);
    }

    public void addDoubleSlider(String name, String desc, double min, double max, double value) {
        DoubleValue val = new DoubleValue(name, desc, min, max, value);
        this.parammap.put(name, val);
        this.gA.gridy = this.row;
        this.gBC.gridy = this.row;
        this.gD.gridy = this.row++;
        this.panel.add((Component)new JLabel(desc), this.gA);
        this.panel.add((Component)val.getSlider(), this.gBC);
        this.panel.add((Component)val.getLabel(), this.gD);
    }

    public void addString(String name, String desc, String value) {
        StringValue val = new StringValue(name, desc, null, value);
        this.parammap.put(name, val);
        this.gA.gridy = this.row;
        this.gBCD.gridy = this.row++;
        this.panel.add((Component)new JLabel(desc), this.gA);
        this.panel.add((Component)val.getTextField(), this.gBCD);
    }

    public void addChoice(String name, String desc, String[] values, int value) {
        StringValue val = new StringValue(name, desc, values, values[value]);
        this.parammap.put(name, val);
        this.gA.gridy = this.row;
        this.gBCD.gridy = this.row++;
        this.panel.add((Component)new JLabel(desc), this.gA);
        this.panel.add((Component)val.getComboBox(), this.gBCD);
    }

    public void addBoolean(String name, String desc, boolean value) {
        BooleanValue val = new BooleanValue(name, desc, value);
        this.parammap.put(name, val);
        this.gA.gridy = this.row;
        this.gBCD.gridy = this.row++;
        this.panel.add((Component)new JLabel(desc), this.gA);
        this.panel.add((Component)val.getCheckBox(), this.gBCD);
    }

    public void addButton(String name, String desc) {
        this.addButtons(name, desc);
    }

    public void addButtons(Object ... args) {
        JPanel p = new JPanel();
        p.setLayout(new GridLayout(1, args.length / 2));
        for (int i = 0; i < args.length / 2; ++i) {
            String name = (String)args[i * 2];
            String desc = (String)args[i * 2 + 1];
            JButton button = new JButton(desc);
            button.addActionListener(new ActionNotifier(name));
            p.add(button);
        }
        this.gABCD.gridy = this.row++;
        this.panel.add((Component)p, this.gABCD);
    }

    public void addCheckBoxes(Object ... args) {
        JPanel p = new JPanel();
        p.setLayout(new GridLayout(1, args.length / 3));
        for (int i = 0; i < args.length / 3; ++i) {
            String name = (String)args[i * 3 + 0];
            String desc = (String)args[i * 3 + 1];
            boolean value = (Boolean)args[i * 3 + 2];
            BooleanValue bv = new BooleanValue(name, desc, value);
            this.parammap.put(name, bv);
            p.add(bv.getCheckBox());
        }
        this.gABCD.gridy = this.row++;
        this.panel.add((Component)p, this.gABCD);
    }

    public double gd(String name) {
        PValue p = this.parammap.get(name);
        assert (p != null);
        assert (p instanceof DoubleValue);
        return ((DoubleValue)p).getDoubleValue();
    }

    public void sd(String name, double v) {
        PValue p = this.parammap.get(name);
        assert (p != null);
        assert (p instanceof DoubleValue);
        ((DoubleValue)p).setDoubleValue(v);
    }

    public int gi(String name) {
        PValue p = this.parammap.get(name);
        assert (p != null);
        if (p instanceof StringValue) {
            return ((StringValue)p).idx;
        }
        assert (p instanceof IntegerValue);
        return ((IntegerValue)p).getIntegerValue();
    }

    public void si(String name, int v) {
        PValue p = this.parammap.get(name);
        assert (p != null);
        assert (p instanceof IntegerValue);
        ((IntegerValue)p).setIntegerValue(v);
    }

    public void setMinMax(String name, int min, int max) {
        PValue p = this.parammap.get(name);
        assert (p != null);
        assert (p instanceof IntegerValue);
        ((IntegerValue)p).setMinMax(min, max);
    }

    public void setMinMax(String name, double min, double max) {
        PValue p = this.parammap.get(name);
        assert (p != null);
        assert (p instanceof DoubleValue);
        ((DoubleValue)p).setMinMax(min, max);
    }

    public String gs(String name) {
        PValue p = this.parammap.get(name);
        assert (p != null);
        assert (p instanceof StringValue);
        return ((StringValue)p).getStringValue();
    }

    public void ss(String name, String v) {
        PValue p = this.parammap.get(name);
        assert (p != null);
        assert (p instanceof StringValue);
        ((StringValue)p).setStringValue(v);
    }

    public boolean gb(String name) {
        PValue p = this.parammap.get(name);
        assert (p != null);
        assert (p instanceof BooleanValue);
        return ((BooleanValue)p).getBooleanValue();
    }

    public void sb(String name, boolean v) {
        PValue p = this.parammap.get(name);
        assert (p != null);
        assert (p instanceof BooleanValue);
        ((BooleanValue)p).setBooleanValue(v);
    }

    public void setEnabled(String name, boolean e) {
        PValue p = this.parammap.get(name);
        assert (p != null);
        p.setEnabled(e);
    }

    public Container getPanel() {
        return this.panel;
    }

    public static void main(String[] args) {
        JFrame f = new JFrame("ParameterGUI Test");
        f.setLayout(new BorderLayout());
        f.setSize(400, 400);
        ParameterGUI pg = new ParameterGUI();
        pg.addDouble("Double Value", "A double value", 3.1415926);
        pg.addInt("IntValue1", "An integer value", 45);
        pg.addIntSlider("IntValue2", "An integer value", 100, 1000, 999);
        pg.addDoubleSlider("DoubleSlider", "A sliding double", -1.0, 1.0, 0.5);
        pg.addDoubleSlider("DoubleSlider2", "Another sliding double", 2500000.0, 3000000.0, 2500000.0);
        pg.addString("StringValue1", "A string value", "Hi");
        pg.addString("StringValue2", "A string value", "world");
        pg.addChoice("Combo", "A combo box", new String[]{"Choice 1", "Choice 2", "Choice 3"}, 1);
        pg.addBoolean("BoolVal1", "A boolean value", true);
        pg.addBoolean("BoolVal2", "A boolean value", false);
        pg.addButton("Button", "button one");
        pg.addButtons("Buttons", "button one", "button2", "button two", "button3", "button three");
        pg.addCheckBoxes("name1", "Checkbox 1", true, "name2", "Checkbox 2", false);
        pg.addListener(new ParameterListener(){

            public void parameterChanged(ParameterGUI pg, String name) {
                System.out.println("Changed " + name);
            }
        });
        f.add((Component)pg.getPanel(), "Center");
        f.setVisible(true);
    }

    class StringValue
    extends PValue {
        JTextField textField;
        JComboBox comboBox;
        String value;
        String[] values;
        int idx;

        public StringValue(String name, String desc, String[] values, String value) {
            super(name, desc);
            this.value = value;
            this.values = values;
            this.updateIndex();
        }

        JTextField getTextField() {
            if (this.textField == null) {
                this.textField = new JTextField(this.value);
                ParameterGUI.setupJTextField(this.textField);
                this.textField.addActionListener(new ActionListener(){

                    public void actionPerformed(ActionEvent e) {
                        String v = StringValue.this.textField.getText();
                        StringValue.this.setStringValue(v);
                    }
                });
            }
            return this.textField;
        }

        JComboBox getComboBox() {
            assert (this.values != null);
            if (this.comboBox == null) {
                this.comboBox = new JComboBox<String>(this.values);
                this.comboBox.addActionListener(new ActionListener(){

                    public void actionPerformed(ActionEvent e) {
                        StringValue.this.setStringValue(StringValue.this.values[StringValue.this.comboBox.getSelectedIndex()]);
                    }
                });
                this.comboBox.setSelectedIndex(this.idx);
            }
            return this.comboBox;
        }

        String getStringValue() {
            return this.value;
        }

        void updateIndex() {
            if (this.values != null) {
                this.idx = -1;
                for (int i = 0; i < this.values.length; ++i) {
                    if (!this.value.equals(this.values[i])) continue;
                    this.idx = i;
                }
                if (this.idx == -1) {
                    System.out.println("Warning: illegal string value specified: " + this.value);
                }
            }
        }

        void setStringValue(String v) {
            if (v.equals(this.value)) {
                return;
            }
            this.value = v;
            this.updateIndex();
            if (this.textField != null) {
                this.textField.setText(v);
            }
            if (this.comboBox != null) {
                this.comboBox.setSelectedIndex(this.idx);
            }
            ParameterGUI.this.notifyListeners(this.name);
        }

        void setEnabled(boolean v) {
            if (this.textField != null) {
                this.textField.setEnabled(v);
            }
        }
    }

    class DoubleValue
    extends PValue {
        JSlider slider;
        JTextField textField;
        JLabel label;
        double min;
        double max;
        double value;
        int ivalue;
        static final int SLIDER_CLICKS = 100000;
        double stepsize;
        String svalue;

        DoubleValue(String name, String desc, double min, double max, double value) {
            super(name, desc);
            this.min = min;
            this.max = max;
            this.value = value;
            this.updateSliderValue();
        }

        void updateSliderValue() {
            this.ivalue = (int)(100000.0 * (this.value - this.min) / (this.max - this.min));
            if (this.slider != null) {
                this.slider.setValue(this.ivalue);
            }
            this.stepsize = (this.max - this.min) / 100000.0;
        }

        JSlider getSlider() {
            if (this.slider == null) {
                this.slider = new JSlider(0, 100000, this.ivalue);
                this.slider.addChangeListener(new ChangeListener(){

                    public void stateChanged(ChangeEvent e) {
                        DoubleValue.this.setDoubleValue((double)DoubleValue.this.slider.getValue() / 100000.0 * (DoubleValue.this.max - DoubleValue.this.min) + DoubleValue.this.min);
                    }
                });
            }
            return this.slider;
        }

        JLabel getLabel() {
            if (this.label == null) {
                this.label = new JLabel("" + this.value);
            }
            return this.label;
        }

        JTextField getTextField() {
            if (this.textField == null) {
                this.textField = new JTextField("" + this.value);
                ParameterGUI.setupJTextField(this.textField);
                this.textField.addActionListener(new ActionListener(){

                    public void actionPerformed(ActionEvent e) {
                        DoubleValue.this.setDoubleValue(ParameterGUI.parseDouble(DoubleValue.this.textField.getText(), DoubleValue.this.value));
                    }
                });
            }
            return this.textField;
        }

        double getDoubleValue() {
            return this.value;
        }

        void setDoubleValue(double v) {
            if (v < this.min) {
                v = this.min;
            }
            if (v > this.max) {
                v = this.max;
            }
            if (v == this.value) {
                return;
            }
            this.value = v;
            this.updateSliderValue();
            int digits = (int)(-Math.log(this.stepsize) / Math.log(10.0)) + 1;
            if (digits < 0) {
                digits = 0;
            }
            this.svalue = String.format("%." + digits + "f", this.value);
            if (this.textField != null) {
                this.textField.setText(this.svalue);
            }
            if (this.label != null) {
                this.label.setText(this.svalue);
            }
            ParameterGUI.this.notifyListeners(this.name);
        }

        void setMinMax(double min, double max) {
            this.min = min;
            this.max = max;
            this.updateSliderValue();
        }

        void setEnabled(boolean v) {
            if (this.slider != null) {
                this.slider.setEnabled(v);
            }
            if (this.textField != null) {
                this.textField.setEnabled(v);
            }
        }
    }

    class IntegerValue
    extends PValue {
        JSlider slider;
        JTextField textField;
        JLabel label;
        int min;
        int max;
        int value;

        IntegerValue(String name, String desc, int min, int max, int value) {
            super(name, desc);
            this.min = min;
            this.max = max;
            this.value = value;
        }

        JSlider getSlider() {
            if (this.slider == null) {
                this.slider = new JSlider(this.min, this.max, this.value);
                this.slider.addChangeListener(new ChangeListener(){

                    public void stateChanged(ChangeEvent e) {
                        IntegerValue.this.setIntegerValue(IntegerValue.this.slider.getValue());
                    }
                });
            }
            return this.slider;
        }

        JLabel getLabel() {
            if (this.label == null) {
                this.label = new JLabel("" + this.value);
            }
            return this.label;
        }

        JTextField getTextField() {
            if (this.textField == null) {
                this.textField = new JTextField("" + this.value);
                ParameterGUI.setupJTextField(this.textField);
                this.textField.addActionListener(new ActionListener(){

                    public void actionPerformed(ActionEvent e) {
                        IntegerValue.this.setIntegerValue(ParameterGUI.parseInteger(IntegerValue.this.textField.getText(), IntegerValue.this.value));
                    }
                });
            }
            return this.textField;
        }

        int getIntegerValue() {
            return this.value;
        }

        void setIntegerValue(int v) {
            if (v < this.min) {
                v = this.min;
            }
            if (v > this.max) {
                v = this.max;
            }
            if (v == this.value) {
                return;
            }
            this.value = v;
            if (this.slider != null) {
                this.slider.setValue(v);
            }
            if (this.textField != null) {
                this.textField.setText("" + v);
            }
            if (this.label != null) {
                this.label.setText("" + v);
            }
            ParameterGUI.this.notifyListeners(this.name);
        }

        void setMinMax(int min, int max) {
            this.min = min;
            this.max = max;
            if (this.slider != null) {
                this.slider.setMinimum(min);
                this.slider.setMaximum(max);
            }
        }

        void setEnabled(boolean v) {
            if (this.slider != null) {
                this.slider.setEnabled(v);
            }
            if (this.textField != null) {
                this.textField.setEnabled(v);
            }
        }
    }

    class BooleanValue
    extends PValue {
        JCheckBox jcb;
        boolean value;

        BooleanValue(String name, String desc, boolean value) {
            super(name, desc);
            this.value = value;
        }

        JCheckBox getCheckBox() {
            if (this.jcb == null) {
                this.jcb = new JCheckBox(this.desc, this.value);
                this.jcb.addActionListener(new ActionListener(){

                    public void actionPerformed(ActionEvent e) {
                        BooleanValue.this.setBooleanValue(BooleanValue.this.jcb.isSelected());
                    }
                });
            }
            return this.jcb;
        }

        boolean getBooleanValue() {
            return this.value;
        }

        void setBooleanValue(boolean v) {
            if (v == this.value) {
                return;
            }
            this.value = v;
            ParameterGUI.this.notifyListeners(this.name);
        }

        void setEnabled(boolean v) {
            this.jcb.setEnabled(v);
        }
    }

    class ActionNotifier
    implements ActionListener {
        String name;

        public ActionNotifier(String name) {
            this.name = name;
        }

        public void actionPerformed(ActionEvent e) {
            ParameterGUI.this.notifyListeners(this.name);
        }
    }

    abstract class PValue {
        String name;
        String desc;

        PValue(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }

        abstract void setEnabled(boolean var1);
    }

    static class JTFListener
    extends KeyAdapter
    implements ActionListener,
    CaretListener {
        JTextField jtf;

        JTFListener(JTextField jtf) {
            this.jtf = jtf;
        }

        public void keyPressed(KeyEvent e) {
            this.jtf.setBackground(Color.yellow);
        }

        public void actionPerformed(ActionEvent e) {
            this.jtf.setBackground(Color.white);
        }

        public void caretUpdate(CaretEvent e) {
        }
    }
}

