/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.Dict;
import decrypto.Language;
import decrypto.Word;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class MakeDict {
    public static void main(String[] args) {
        try {
            MakeDict.main_ex(args);
        }
        catch (Exception ex) {
            System.out.println("ex: " + ex);
            ex.printStackTrace();
        }
    }

    public static void main_ex(String[] args) throws Exception {
        String line;
        HashMap<String, String> properties = new HashMap<String, String>();
        properties.put("version", "1.0");
        properties.put("language", "decrypto.EnglishLanguage");
        if (args.length != 2) {
            System.out.println("Usage: wordlist dictfile");
            return;
        }
        Language lang = Dict.getLanguage((String)properties.get("language"));
        FileReader fr = new FileReader(args[0]);
        BufferedReader ins = new BufferedReader(fr);
        ArrayList<Word> words = new ArrayList<Word>();
        while ((line = ins.readLine()) != null) {
            byte[] bs = lang.stringToBytes(line);
            if (bs == null) continue;
            words.add(new Word(line, 0, bs));
        }
        System.out.println("Sorting");
        Collections.sort(words, new Word.PatternComparator());
        System.out.println("Removing duplicates");
        for (int i = 0; i < words.size() - 1; ++i) {
            if (!Arrays.equals(((Word)words.get((int)i)).word, ((Word)words.get((int)(i + 1))).word)) continue;
            words.remove(i);
        }
        RandomAccessFile outs = new RandomAccessFile(args[1], "rw");
        outs.writeInt(233573869);
        outs.writeInt(properties.size());
        for (String s : properties.keySet()) {
            outs.writeUTF(s);
            outs.writeUTF((String)properties.get(s));
        }
        int nwords = words.size();
        int firstword = 0;
        int lastword = 1;
        while (lastword < nwords) {
            if (Arrays.equals(((Word)words.get((int)firstword)).pattern, ((Word)words.get((int)lastword)).pattern)) {
                ++lastword;
                continue;
            }
            MakeDict.emit(outs, words, firstword, lastword);
            firstword = lastword;
            lastword = firstword + 1;
        }
        if (firstword < nwords) {
            MakeDict.emit(outs, words, firstword, lastword);
        }
        System.out.println("done");
    }

    static void emit(RandomAccessFile outs, ArrayList<Word> words, int first, int last) throws IOException {
        boolean verbose = false;
        if (last - first == 0) {
            return;
        }
        outs.writeByte(255);
        outs.write(words.get((int)first).word.length);
        if (verbose) {
            System.out.println("pattern ct: " + (last - first));
        }
        for (int i = first; i < last; ++i) {
            Word w = words.get(i);
            outs.write(w.word);
        }
    }
}

