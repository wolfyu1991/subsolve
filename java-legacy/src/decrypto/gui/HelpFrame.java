/*
 * Decompiled with CFR 0.152.
 */
package decrypto.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;

public class HelpFrame
extends JFrame {
    public static final long serialVersionUID = 1001L;

    public HelpFrame(String title, String jarPath) {
        this(title, jarPath, 640, 400);
    }

    public HelpFrame(String title, String jarPath, int width, int height) {
        super(title);
        JEditorPane jed = new JEditorPane("text/html", "This is a test");
        jed.setEditable(false);
        jed.setText(this.getData(jarPath));
        this.setLayout(new BorderLayout());
        JScrollPane jsp = new JScrollPane(jed);
        this.add((Component)jsp, "Center");
        this.setSize(width, height);
        this.setVisible(true);
        jed.setCaretPosition(0);
    }

    String getData(String jarPath) {
        String cp = System.getProperty("java.class.path");
        String[] items = cp.split(":");
        for (int i = 0; i < items.length; ++i) {
            if (!items[i].endsWith(".jar") || !new File(items[i]).exists()) continue;
            try {
                JarFile jf = new JarFile(items[i]);
                Enumeration<JarEntry> e = jf.entries();
                while (e.hasMoreElements()) {
                    String line;
                    JarEntry je = e.nextElement();
                    if (!je.getName().endsWith(jarPath)) continue;
                    StringBuffer sb = new StringBuffer();
                    BufferedReader ins = new BufferedReader(new InputStreamReader(jf.getInputStream(je)));
                    while ((line = ins.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    return sb.toString();
                }
                continue;
            }
            catch (IOException ioe) {
                return "Error extracting " + items[i];
            }
        }
        return "Help file " + jarPath + " not found";
    }
}

