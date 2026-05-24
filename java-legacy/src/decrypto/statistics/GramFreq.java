/*
 * Decompiled with CFR 0.152.
 */
package decrypto.statistics;

import decrypto.EnglishLanguage;
import decrypto.genetic.Sampler;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class GramFreq {
    HashMap<String, HashMap<String, Gram>> gramsets = new HashMap();
    HashMap<String, Gram> allgrams = new HashMap();
    int totalCount = 0;
    int order;
    String history = "";
    static final double FRACTOKEEP = 0.6;
    boolean stripspaces = true;
    static EnglishLanguage lang = new EnglishLanguage();

    public static void main(String[] args) {
        try {
            GramFreq.main_ex(args);
        }
        catch (IOException ex) {
            System.out.println("ex: " + ex);
        }
    }

    public static void main_ex(String[] args) throws IOException {
        new GramFreq(5, true, args);
        new GramFreq(5, false, args);
    }

    public GramFreq(int order, boolean stripspaces, String[] args) throws IOException {
        this.order = order;
        this.stripspaces = stripspaces;
        for (int i = 0; i < args.length; ++i) {
            int c;
            FileReader fr = new FileReader(args[i]);
            BufferedReader ins = new BufferedReader(fr);
            while ((c = ins.read()) > 0) {
                if (Character.isLetter(c)) {
                    this.handleCharacter(Character.toUpperCase(c));
                    continue;
                }
                this.handleCharacter(32);
            }
        }
        ArrayList<Gram> allgramslist = new ArrayList<Gram>();
        for (Gram g : this.allgrams.values()) {
            allgramslist.add(g);
        }
        Collections.sort(allgramslist);
        double cumulativeFraction = 0.0;
        int countThreshold = 0;
        for (int i = 0; i < allgramslist.size(); ++i) {
            Gram g = (Gram)allgramslist.get(i);
            if (!((cumulativeFraction += (double)g.count / (double)this.totalCount) > 0.6)) continue;
            countThreshold = g.count;
            break;
        }
        HashMap keepsets = new HashMap();
        for (String pattern : this.gramsets.keySet()) {
            HashMap<String, Gram> grams = this.gramsets.get(pattern);
            ArrayList<Gram> gramlist = new ArrayList<Gram>();
            for (Gram g : grams.values()) {
                gramlist.add(g);
            }
            Collections.sort(gramlist);
            HashMap<String, Gram> keepgrams = new HashMap<String, Gram>();
            for (int i = 0; i < gramlist.size(); ++i) {
                Gram gram = (Gram)gramlist.get(i);
                if (gram.count < countThreshold) continue;
                keepgrams.put(gram.text, gram);
            }
            if (keepgrams.size() <= 0) continue;
            keepsets.put(pattern, keepgrams);
        }
        this.gramsets = keepsets;
        this.emitBinary("/tmp/gramfreq" + order + (stripspaces ? "ns" : "") + ".stb");
    }

    void handleCharacter(int c) {
        Gram gram;
        if (this.stripspaces && c == 32) {
            return;
        }
        if (c == 32 && this.history.length() > 0 && this.history.charAt(this.history.length() - 1) == ' ') {
            return;
        }
        if (this.history.length() == this.order) {
            this.history = this.history.substring(1, this.order);
        }
        this.history = this.history + (char)c;
        if (this.history.length() < this.order) {
            return;
        }
        String pattern = GramFreq.makePattern(this.history);
        HashMap<String, Gram> grams = this.gramsets.get(pattern);
        if (grams == null) {
            grams = new HashMap();
            this.gramsets.put(pattern, grams);
        }
        if ((gram = grams.get(this.history)) == null) {
            gram = new Gram(this.history);
            grams.put(this.history, gram);
            this.allgrams.put(this.history, gram);
        }
        ++gram.count;
        ++this.totalCount;
    }

    public static String makePattern(String s) {
        byte[] map = new byte[256];
        StringBuffer pattern = new StringBuffer();
        int next = 65;
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (c == ' ') {
                pattern.append(' ');
                continue;
            }
            if (map[c] == 0) {
                map[c] = next;
                next = (byte)(next + 1);
            }
            pattern.append((char)map[c]);
        }
        return pattern.toString();
    }

    public void emitBinary(String path) throws IOException {
        DataOutputStream outs = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(path)));
        outs.writeInt(-1438373319);
        outs.writeInt(this.order);
        outs.writeInt(this.gramsets.size());
        for (String pattern : this.gramsets.keySet()) {
            HashMap<String, Gram> grams = this.gramsets.get(pattern);
            outs.writeInt(grams.size());
            assert (grams.size() > 0);
            for (Gram gram : grams.values()) {
                assert (gram.text.length() == this.order);
                for (int i = 0; i < this.order; ++i) {
                    outs.write((byte)gram.text.charAt(i));
                }
                outs.writeDouble(gram.count);
            }
        }
        outs.close();
    }

    public static HashMap<String, Sampler<String>> readBinary(String path) throws IOException {
        DataInputStream ins = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(path)))));
        int magic = ins.readInt();
        assert (magic == -1438373319);
        int order = ins.readInt();
        HashMap<String, Sampler<String>> map = new HashMap<String, Sampler<String>>();
        int npatterns = ins.readInt();
        for (int pattern = 0; pattern < npatterns; ++pattern) {
            int ngrams = ins.readInt();
            double[] proportions = new double[ngrams];
            String[] strings = new String[ngrams];
            for (int gram = 0; gram < ngrams; ++gram) {
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < order; ++i) {
                    sb.append((char)ins.read());
                }
                strings[gram] = sb.toString();
                proportions[gram] = ins.readDouble();
            }
            map.put(GramFreq.makePattern(strings[0]), new Sampler<String>(proportions, strings));
        }
        return map;
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    static final class Gram
    implements Comparable<Gram> {
        String text;
        int count;

        Gram(String s) {
            this.text = s;
        }

        @Override
        public int compareTo(Gram g) {
            return g.count - this.count;
        }
    }
}

