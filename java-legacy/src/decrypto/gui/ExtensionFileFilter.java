/*
 * Decompiled with CFR 0.152.
 */
package decrypto.gui;

import java.io.File;
import java.util.HashSet;
import javax.swing.filechooser.FileFilter;

public class ExtensionFileFilter
extends FileFilter {
    public String description = "Acceptable file types";
    HashSet<String> extensions = new HashSet();
    boolean allowDirectories = true;
    boolean gotWildcard = false;

    public ExtensionFileFilter(String suffix, String description) {
        this.addExtension(suffix);
        this.setDescription(description);
    }

    public ExtensionFileFilter(String[] suffixes, String description) {
        this.setDescription(description);
        for (int i = 0; i < suffixes.length; ++i) {
            this.addExtension(suffixes[i]);
        }
    }

    public String getDescription() {
        return this.description;
    }

    public void addExtension(String s) {
        if (s.equals("*")) {
            this.gotWildcard = true;
        }
        this.extensions.add(s.toLowerCase());
    }

    public boolean accept(File f) {
        int period;
        if (this.gotWildcard) {
            return true;
        }
        if (f.isDirectory()) {
            return this.allowDirectories;
        }
        String path = f.getName();
        String type = path.substring((period = path.lastIndexOf(46)) + 1);
        return this.extensions.contains(type.toLowerCase());
    }

    public void setAllowDirectories(boolean b) {
        this.allowDirectories = b;
    }

    public void setDescription(String s) {
        this.description = s;
    }
}

