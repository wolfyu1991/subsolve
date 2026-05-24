/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.Candidate;
import java.util.ArrayList;
import java.util.Comparator;

public class Word {
    public String text;
    public int wordnum;
    public byte[] uniqueletters;
    public byte[] word;
    public byte[] pattern;
    public int numUniqueLetters;
    public Candidate[] candidates;
    public int firstcand = 0;
    public boolean enabled = true;

    public Word(String text, int wordnum, byte[] word) {
        int i;
        this.text = text;
        this.wordnum = wordnum;
        this.word = word;
        this.pattern = Word.makePattern(word);
        this.numUniqueLetters = 0;
        for (int i2 = 0; i2 < this.pattern.length; ++i2) {
            if (this.pattern[i2] + 1 <= this.numUniqueLetters) continue;
            this.numUniqueLetters = this.pattern[i2] + 1;
        }
        ArrayList<Byte> f = new ArrayList<Byte>();
        for (i = 0; i < word.length; ++i) {
            if (f.contains(word[i])) continue;
            f.add(word[i]);
        }
        this.uniqueletters = new byte[f.size()];
        for (i = 0; i < f.size(); ++i) {
            this.uniqueletters[i] = (Byte)f.get(i);
        }
    }

    protected Word() {
    }

    public Word copy() {
        Word w = new Word();
        w.text = this.text;
        w.wordnum = this.wordnum;
        w.word = this.word;
        w.pattern = this.pattern;
        w.enabled = this.enabled;
        w.candidates = new Candidate[this.candidates.length];
        w.numUniqueLetters = this.numUniqueLetters;
        w.uniqueletters = this.uniqueletters;
        for (int i = 0; i < this.candidates.length; ++i) {
            w.candidates[i] = this.candidates[i];
        }
        return w;
    }

    public static byte[] makePattern(byte[] word) {
        byte[] pattern = new byte[word.length];
        byte next = 1;
        byte[] map = new byte[256];
        for (int i = 0; i < word.length; ++i) {
            int c = word[i] & 0xFF;
            if (map[c] == 0) {
                map[c] = next;
                next = (byte)(next + 1);
            }
            pattern[i] = (byte)(map[c] - 1);
        }
        return pattern;
    }

    public static int compareArrays(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return a.length - b.length;
        }
        for (int i = 0; i < a.length; ++i) {
            if (a[i] == b[i]) continue;
            return a[i] - b[i];
        }
        return 0;
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    public static class TextComparator
    implements Comparator<Word> {
        @Override
        public int compare(Word a, Word b) {
            return a.text.compareTo(b.text);
        }
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    public static class PatternComparator
    implements Comparator<Word> {
        @Override
        public int compare(Word a, Word b) {
            int i = Word.compareArrays(a.pattern, b.pattern);
            if (i != 0) {
                return i;
            }
            return a.text.compareTo(b.text);
        }
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    public static class WordComparator
    implements Comparator<Word> {
        @Override
        public int compare(Word a, Word b) {
            if (b == null) {
                return -1;
            }
            return Word.compareArrays(a.word, b.word);
        }
    }
}

