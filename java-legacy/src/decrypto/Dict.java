/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.Language;
import decrypto.Word;
import decrypto.util.BufferedRandomAccessFile;
import decrypto.util.ByteArrayWrapper;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.WeakHashMap;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class Dict {
    BufferedRandomAccessFile raf;
    long filesize;
    long lowoff;
    public HashMap<String, String> properties = new HashMap();
    WeakHashMap<ByteArrayWrapper, ArrayList<byte[]>> patternCache = new WeakHashMap();
    static final int MAGIC = 233573869;
    Language lang;
    HashSet<String> allWords = null;

    public Dict() {
    }

    public Dict(String path) throws IOException {
        this.raf = new BufferedRandomAccessFile(path, "r");
        this.filesize = this.raf.length();
        int id = this.raf.readInt();
        if (id != 233573869) {
            throw new IOException("Not a dictionary file");
        }
        int nprops = this.raf.readInt();
        for (int i = 0; i < nprops; ++i) {
            String key = this.raf.readUTF();
            String value = this.raf.readUTF();
            this.properties.put(key, value);
        }
        this.lowoff = this.raf.getFilePointer();
    }

    public String getProperty(String key) {
        return this.properties.get(key);
    }

    public ArrayList<byte[]> lookup(byte[] pattern) {
        return this.lookup(pattern, false);
    }

    public ArrayList<byte[]> lookup(byte[] pattern, boolean recycle) {
        try {
            return this.lookup_ex(pattern, recycle);
        }
        catch (IOException ex) {
            System.out.println("ex: " + ex);
            ex.printStackTrace();
            return new ArrayList<byte[]>();
        }
    }

    /*
     * Unable to fully structure code
     */
    public ArrayList<byte[]> readAll() throws IOException {
        pats = new ArrayList<byte[]>();
        this.raf.seek(this.lowoff);
        try {
            block2: while (true) {
                if (this.raf.readByte() != -1 || (wordlength = this.raf.readByte() & 255) == 0) {
                    continue;
                }
                wcount = this.raf.readShort();
                i = 0;
                while (true) {
                    if (i < wcount) ** break;
                    continue block2;
                    thisword = new byte[wordlength];
                    this.raf.readFully(thisword);
                    pats.add(thisword);
                    ++i;
                }
                break;
            }
        }
        catch (IOException ex) {
            return pats;
        }
    }

    public ArrayList<byte[]> lookup_ex(byte[] pattern, boolean recycle) throws IOException {
        byte[] thispattern;
        byte[] thisword;
        int wordlength;
        ByteArrayWrapper pw = new ByteArrayWrapper(pattern);
        ArrayList<Object> matches = this.patternCache.get(pw);
        if (matches != null) {
            if (recycle) {
                return matches;
            }
            return new ArrayList<byte[]>(matches);
        }
        matches = new ArrayList();
        this.patternCache.put(pw, matches);
        long low = this.lowoff;
        long high = this.filesize - 1L;
        long lastpos = -1L;
        short nwords = 0;
        while (true) {
            long pos;
            if ((pos = (low + high) / 2L) == lastpos) {
                return matches;
            }
            lastpos = pos;
            this.raf.seek(pos);
            int cnt = 0;
            try {
                while (this.raf.readByte() != -1) {
                    ++cnt;
                }
            }
            catch (EOFException ex) {
                high = pos;
                continue;
            }
            wordlength = this.raf.readByte() & 0xFF;
            nwords = this.raf.readShort();
            thisword = new byte[wordlength];
            this.raf.readFully(thisword);
            thispattern = Word.makePattern(thisword);
            int cmp = Word.compareArrays(thispattern, pattern);
            if (cmp != 0 && matches.size() > 0) {
                return matches;
            }
            if (cmp < 0) {
                low = pos;
                continue;
            }
            if (cmp <= 0) break;
            high = pos;
        }
        matches.add(thisword);
        for (int i = 0; i < nwords - 1; ++i) {
            thisword = new byte[wordlength];
            try {
                this.raf.readFully(thisword);
            }
            catch (EOFException ex) {
                break;
            }
            thispattern = Word.makePattern(thisword);
            matches.add(thisword);
        }
        return matches;
    }

    public static Language getLanguage(String classname) {
        if (classname == null) {
            classname = "decrypto.EnglishLanguage";
        }
        try {
            Class<?> theClass = ClassLoader.getSystemClassLoader().loadClass(classname);
            Constructor<?> theConstructor = theClass.getConstructor(new Class[0]);
            return (Language)theConstructor.newInstance(new Object[0]);
        }
        catch (Exception ex) {
            System.out.println("Couldn't instantiate language: " + classname);
            return null;
        }
    }

    public Language getLanguage() {
        if (this.lang == null) {
            this.lang = Dict.getLanguage(this.getProperty("langclass"));
        }
        return this.lang;
    }

    public static ArrayList<Word> sortAndPruneWords(ArrayList<Word> words) {
        ArrayList<Word> prunedWords = new ArrayList<Word>();
        Collections.sort(words, new Word.PatternComparator());
        for (int i = 0; i < words.size(); ++i) {
            Word thisWord = words.get(i);
            if (thisWord.text.trim().length() == 0 || thisWord.word.length == 0 || i + 1 < words.size() && Arrays.equals(thisWord.word, words.get((int)(i + 1)).word)) continue;
            prunedWords.add(thisWord);
        }
        return prunedWords;
    }

    public void write(String path, ArrayList<Word> words) throws IOException {
        words = Dict.sortAndPruneWords(words);
        RandomAccessFile outs = new RandomAccessFile(path, "rw");
        outs.writeInt(233573869);
        outs.writeInt(this.properties.size());
        for (String s : this.properties.keySet()) {
            outs.writeUTF(s);
            outs.writeUTF(this.properties.get(s));
        }
        int nwords = words.size();
        int firstword = 0;
        int lastword = 1;
        while (lastword < nwords) {
            if (Arrays.equals(words.get((int)firstword).pattern, words.get((int)lastword).pattern)) {
                ++lastword;
                continue;
            }
            Dict.emit(outs, words, firstword, lastword);
            firstword = lastword;
            lastword = firstword + 1;
        }
        if (firstword < nwords) {
            Dict.emit(outs, words, firstword, lastword);
        }
        outs.close();
    }

    static void emit(RandomAccessFile outs, ArrayList<Word> words, int first, int last) throws IOException {
        boolean verbose = false;
        if (last - first == 0) {
            return;
        }
        outs.writeByte(255);
        outs.write(words.get((int)first).word.length);
        outs.writeShort(last - first);
        if (verbose) {
            System.out.println("pattern ct: " + (last - first));
        }
        for (int i = first; i < last; ++i) {
            Word w = words.get(i);
            outs.write(w.word);
        }
    }

    public boolean isInDictionary(String s) {
        if (this.allWords == null) {
            ArrayList<byte[]> pats;
            this.allWords = new HashSet();
            try {
                pats = this.readAll();
            }
            catch (IOException ex) {
                System.out.println("ex: " + ex);
                return false;
            }
            for (byte[] p : pats) {
                this.allWords.add(this.lang.bytesToString(p));
            }
        }
        return this.allWords.contains(s);
    }
}

