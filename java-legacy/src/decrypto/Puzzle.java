/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.Candidate;
import decrypto.Dict;
import decrypto.Language;
import decrypto.MapSet;
import decrypto.Word;
import java.util.ArrayList;
import java.util.Collections;

public class Puzzle {
    public String rawCipherText;
    public String cipherText;
    public int numCiphertextLetters;
    public int numUniqueCiphertextLetters;
    public byte[] cipherBytes;
    public boolean trustSpaces;
    public String clues;
    public MapSet initialMap;
    public int[] histogram;
    public byte[] historder;
    public Dict dict;
    public Language lang;
    public ArrayList<Word> words;

    public Puzzle(Dict dict, String _ctext, String _clues, boolean trustSpaces, boolean allowIdentityWhenSolving) {
        int i;
        int i2;
        this.rawCipherText = _ctext;
        this.trustSpaces = trustSpaces;
        this.dict = dict;
        this.lang = dict.getLanguage();
        this.clues = Puzzle.despaceString(_clues.trim());
        this.cipherText = trustSpaces ? Puzzle.onlyAlphaSpaceCharacters(Puzzle.despaceString(_ctext.trim())) : Puzzle.onlyAlphaCharacters(Puzzle.despaceString(_ctext.trim()));
        this.cipherBytes = this.lang.cipherTextToCipherBytes(this.cipherText);
        this.initialMap = new MapSet(this.lang.letterCount());
        this.initialMap.setFullSet();
        if (!allowIdentityWhenSolving) {
            for (i2 = 0; i2 < this.lang.letterCount(); ++i2) {
                this.initialMap.forbidMapping((byte)i2, (byte)i2);
            }
        }
        this.handleClues(this.clues);
        this.numCiphertextLetters = 0;
        for (i2 = 0; i2 < this.cipherBytes.length; ++i2) {
            if (this.cipherBytes[i2] >= this.lang.letterCount()) continue;
            ++this.numCiphertextLetters;
        }
        this.numUniqueCiphertextLetters = 0;
        boolean[] present = new boolean[this.lang.letterCount()];
        for (int i3 = 0; i3 < this.cipherBytes.length; ++i3) {
            if (this.cipherBytes[i3] == this.lang.letterCount()) continue;
            if (!present[this.cipherBytes[i3]]) {
                ++this.numUniqueCiphertextLetters;
            }
            present[this.cipherBytes[i3]] = true;
        }
        this.words = new ArrayList();
        ArrayList<Word> rawwords = this.lang.cipherTextToWords(Puzzle.despaceString(this.rawCipherText));
        Word.WordComparator wcomp = new Word.WordComparator();
        for (Word w : rawwords) {
            boolean existsAlready = false;
            for (Word uw : this.words) {
                if (wcomp.compare(w, uw) != 0) continue;
                existsAlready = true;
                break;
            }
            if (existsAlready) continue;
            this.words.add(w);
        }
        for (Word w : this.words) {
            w.candidates = Candidate.toCandidates(dict.lookup(w.pattern), this.lang);
            if (w.candidates != null && w.candidates.length != 0) continue;
            w.enabled = false;
        }
        ArrayList<Hist> hists = new ArrayList<Hist>();
        for (int i4 = 0; i4 < this.lang.letterCount(); ++i4) {
            hists.add(new Hist(i4));
        }
        for (Word w : this.words) {
            for (int i5 = 0; i5 < w.word.length; ++i5) {
                ++((Hist)hists.get((int)w.word[i5])).count;
            }
        }
        this.histogram = new int[this.lang.letterCount()];
        for (i = 0; i < this.lang.letterCount(); ++i) {
            this.histogram[i] = ((Hist)hists.get((int)i)).count;
        }
        Collections.sort(hists);
        this.historder = new byte[this.lang.letterCount()];
        for (i = 0; i < this.lang.letterCount(); ++i) {
            this.historder[i] = (byte)((Hist)hists.get((int)i)).letter;
        }
    }

    public static String despaceString(String s) {
        int spacecount = 0;
        for (int i = 0; i < s.length(); ++i) {
            if (s.charAt(i) != ' ') continue;
            ++spacecount;
        }
        double frac = (double)spacecount / (double)s.length();
        if (frac < 0.4) {
            return s;
        }
        if (s.length() > 8) {
            spacecount = 0;
            for (int i = 0; i < s.length() - 2; ++i) {
                if (s.charAt(i) != ' ' || s.charAt(i + 2) != ' ') continue;
                ++spacecount;
            }
            frac = (double)spacecount / (double)(s.length() - 2);
            if (frac < 0.4) {
                return s;
            }
        }
        StringBuffer sb = new StringBuffer();
        int spacerun = 0;
        for (int i = 0; i < s.length(); ++i) {
            if (s.charAt(i) == ' ') {
                ++spacerun;
                continue;
            }
            if (spacerun <= 1) {
                sb.append(s.charAt(i));
            } else {
                sb.append(' ');
                sb.append(s.charAt(i));
            }
            spacerun = 0;
        }
        return sb.toString();
    }

    protected Puzzle() {
    }

    public Puzzle copy() {
        Puzzle dp = new Puzzle();
        dp.rawCipherText = this.rawCipherText;
        dp.cipherText = this.cipherText;
        dp.cipherBytes = this.cipherBytes;
        dp.trustSpaces = this.trustSpaces;
        dp.clues = this.clues;
        dp.initialMap = this.initialMap.copy();
        dp.dict = this.dict;
        dp.lang = this.lang;
        dp.historder = this.historder;
        dp.histogram = this.histogram;
        dp.words = new ArrayList();
        for (Word w : this.words) {
            dp.words.add(w.copy());
        }
        return dp;
    }

    void handleClues(String clues) {
        this.clues = clues;
        String[] toks = clues.split("[\\s,]+");
        for (int i = 0; i < toks.length; ++i) {
            byte v;
            byte k;
            int j;
            String[] kv;
            if (toks[i].length() == 0) continue;
            if (toks[i].contains("!=")) {
                kv = toks[i].split("!=");
                if (kv == null || kv.length != 2) {
                    System.out.println("Unable to parse clue: " + toks[i]);
                    continue;
                }
                if (kv[0].length() == 1) {
                    for (j = 0; j < kv[1].length(); ++j) {
                        this.initialMap.forbidMapping(this.lang.charToByte(kv[0].charAt(0)), this.lang.charToByte(kv[1].charAt(j)));
                    }
                    continue;
                }
                if (kv[0].length() != kv[1].length()) {
                    System.out.println("Invalid clue: " + toks[i]);
                    continue;
                }
                for (j = 0; j < kv[0].length(); ++j) {
                    k = this.lang.charToByte(kv[0].charAt(j));
                    v = this.lang.charToByte(kv[1].charAt(j));
                    this.initialMap.forbidMapping(k, v);
                }
                continue;
            }
            kv = toks[i].split("=");
            if (kv == null || kv.length != 2) {
                System.out.println("Unable to parse clue: " + toks[i]);
                continue;
            }
            if (kv[0].length() != kv[1].length()) {
                System.out.println("Must have same number of letters before and after equal sign: " + toks[i]);
                continue;
            }
            if (kv[0].length() < 1) {
                System.out.println("Didn't find a clue in this token: " + toks[i]);
                continue;
            }
            for (j = 0; j < kv[0].length(); ++j) {
                k = this.lang.charToByte(kv[0].charAt(j));
                v = this.lang.charToByte(kv[1].charAt(j));
                this.initialMap.setMapping(k, v);
            }
        }
    }

    public void scramble(boolean allowIdentityMappings) {
        MapSet permutedMap = new MapSet(this.lang.letterCount());
        permutedMap.randomlyPermute(allowIdentityMappings);
        this.cipherText = this.lang.translateString(this.rawCipherText, permutedMap, null);
        String[] toks = this.clues.split("[\\s,]+");
        String newclues = "";
        for (int i = 0; i < toks.length; ++i) {
            String[] kv;
            if (toks[i].length() == 0 || (kv = toks[i].split("=")).length != 2) continue;
            newclues = newclues + this.lang.translateString(kv[0], permutedMap, null) + "=" + kv[1];
            if (i >= toks.length - 1) continue;
            newclues = newclues + " ";
        }
        this.clues = newclues;
        this.initialMap = new MapSet(this.lang.letterCount());
        this.initialMap.setFullSet();
        this.handleClues(newclues);
    }

    static final String onlyAlphaCharacters(String s) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (!Character.isLetter(c)) continue;
            sb.append(c);
        }
        return sb.toString();
    }

    static final String onlyAlphaSpaceCharacters(String s) {
        StringBuffer sb = new StringBuffer();
        boolean havespace = false;
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                if (havespace) {
                    sb.append(' ');
                }
                sb.append(c);
                havespace = false;
                continue;
            }
            if (c != ' ' && c != '.' && c != '-' && c != ':' && c != ';' && c != '?' && c != '!' && c != '(' && c != ')') continue;
            havespace = true;
        }
        return sb.toString();
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    static class Hist
    implements Comparable<Hist> {
        int count;
        int letter;

        public Hist(int letter) {
            this.letter = letter;
        }

        @Override
        public int compareTo(Hist h) {
            return h.count - this.count;
        }
    }
}

