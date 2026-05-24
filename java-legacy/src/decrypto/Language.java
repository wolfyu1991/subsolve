/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.MapSet;
import decrypto.Word;
import java.util.ArrayList;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public abstract class Language {
    public abstract Object getNgrams(int var1, boolean var2);

    public abstract byte charToByte(char var1);

    public abstract char byteToChar(byte var1);

    public abstract int letterCount();

    public byte[] stringToBytes(String s) {
        ArrayList<Byte> bytes = new ArrayList<Byte>();
        for (int i = 0; i < s.length(); ++i) {
            byte b;
            char c = s.charAt(i);
            if (!Character.isLetter(c) || (b = this.charToByte(c)) < 0) continue;
            bytes.add(b);
        }
        byte[] bs = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); ++i) {
            bs[i] = (Byte)bytes.get(i);
        }
        return bs;
    }

    public String bytesToString(byte[] candidate) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < candidate.length; ++i) {
            sb.append(this.byteToChar(candidate[i]));
        }
        return sb.toString();
    }

    public String bytesToString(byte[] candidate, int[] map) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < candidate.length; ++i) {
            sb.append(this.byteToChar((byte)map[candidate[i]]));
        }
        return sb.toString();
    }

    public ArrayList<Word> cipherTextToWords(String ciphertext) {
        ArrayList<Word> words = new ArrayList<Word>();
        String[] toks = ciphertext.split("\\s+|-|\\.|,|;|/|\\+|\\(|\\)|\\\\|\\?");
        int wordnum = 0;
        for (int i = 0; i < toks.length; ++i) {
            StringBuffer toksb = new StringBuffer();
            for (int j = 0; j < toks[i].length(); ++j) {
                char c = toks[i].charAt(j);
                if (this.charToByte(c) < 0 || this.charToByte(c) >= this.letterCount()) continue;
                toksb.append(c);
            }
            String token = toksb.toString();
            byte[] bs = this.stringToBytes(token);
            if (bs.length <= 0) continue;
            Word word = new Word(token, wordnum++, bs);
            if (toks[i].charAt(0) == '^') continue;
            words.add(word);
        }
        return words;
    }

    public byte[] cipherTextToCipherBytes(String cipherText) {
        ArrayList<Byte> bytes = new ArrayList<Byte>();
        boolean lastWasSpace = false;
        for (int i = 0; i < cipherText.length(); ++i) {
            char c = cipherText.charAt(i);
            byte b = this.charToByte(c);
            if (b == this.letterCount() && lastWasSpace) continue;
            lastWasSpace = b == this.letterCount();
            bytes.add(b);
        }
        byte[] bs = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); ++i) {
            bs[i] = (Byte)bytes.get(i);
        }
        return bs;
    }

    public abstract String translateString(String var1, MapSet var2, int[] var3);

    public abstract double computeScore(byte[] var1, MapSet var2);

    public abstract double computeCandidateScore(byte[] var1);

    public abstract double computeScore(byte[] var1, int[] var2);
}

