/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.Language;
import decrypto.MapSet;
import decrypto.statistics.BuildStatistics4;
import java.io.IOException;

public class EnglishLanguage
extends Language {
    static float[][][][] _tetragrams;
    static float[][][][] _tetragramsNS;
    static Object syncObject;
    static final double unknownLetterPenalty = -20.0;

    public Object getNgrams(int order, boolean withSpaces) {
        return EnglishLanguage.getNgramsStatic(order, withSpaces);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    static Object getNgramsStatic(int order, boolean withSpaces) {
        Object object = syncObject;
        synchronized (object) {
            try {
                if (order == 3) assert (false);
                if (order == 4) {
                    if (withSpaces) {
                        if (_tetragrams == null) {
                            _tetragrams = BuildStatistics4.readBinary("english4.sta");
                        }
                        return _tetragrams;
                    }
                    if (_tetragramsNS == null) {
                        _tetragramsNS = BuildStatistics4.readBinary("english4ns.sta");
                    }
                    return _tetragramsNS;
                }
            }
            catch (IOException ex) {
                System.out.println("Couldn't open statistics: " + ex);
            }
        }
        return null;
    }

    public char byteToChar(byte v) {
        if (v == -1) {
            return '~';
        }
        if (v == 26) {
            return ' ';
        }
        return (char)(65 + v);
    }

    public final byte charToByte(char c) {
        if (c == ' ') {
            return (byte)this.letterCount();
        }
        if ((c = Character.toUpperCase(c)) < 'A' || c > 'Z') {
            return (byte)this.letterCount();
        }
        return (byte)(c - 65);
    }

    public int letterCount() {
        return 26;
    }

    public String translateString(String ciphertext, MapSet map, int[] spaces) {
        StringBuffer sb = new StringBuffer();
        int spacepos = 0;
        boolean spacepending = false;
        for (int i = 0; i < ciphertext.length(); ++i) {
            char c = ciphertext.charAt(i);
            if (spaces != null && c == ' ') continue;
            byte cb = this.charToByte(c);
            if (cb >= 0 && cb < this.letterCount()) {
                int mc;
                if (spacepending) {
                    sb.append(' ');
                    spacepending = false;
                }
                if ((mc = map.getMapping(this.charToByte(c))) == -1) {
                    sb.append("~");
                } else if (Character.isLowerCase(c)) {
                    sb.append((char)(97 + mc));
                } else {
                    sb.append((char)(65 + mc));
                }
                if (spaces != null && spaces[spacepos] > 0) {
                    spacepending = true;
                }
                ++spacepos;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public double computeCandidateScore(byte[] candidate) {
        int ma = 27;
        int mb = 27;
        int mc = 26;
        double score = 0.0;
        float[][][][] ngrams = (float[][][][])this.getNgrams(4, true);
        for (int i = 0; i < candidate.length; ++i) {
            int md = candidate[i];
            score += (double)ngrams[ma][mb][mc][md];
            ma = mb;
            mb = mc;
            mc = md;
        }
        return score += (double)ngrams[ma][mb][mc][26];
    }

    public double computeScore(byte[] cipherbytes, MapSet mapset) {
        int m2;
        int m1;
        int[] map = new int[28];
        for (int i = 0; i < 26; ++i) {
            map[i] = mapset.getMapping((byte)i);
        }
        map[26] = 26;
        map[27] = 27;
        int len = cipherbytes.length;
        int c0 = len > 2 ? cipherbytes[len - 2] : 27;
        int c1 = len > 1 ? cipherbytes[len - 1] : 27;
        int c2 = 26;
        int m0 = map[c0];
        if (m0 < 0) {
            m0 = 27;
        }
        if ((m1 = map[c1]) < 0) {
            m1 = 27;
        }
        if ((m2 = map[c2]) < 0) {
            m2 = 27;
        }
        float[][][][] grams = (float[][][][])this.getNgrams(4, true);
        double score = 0.0;
        for (int i = 0; i < cipherbytes.length; ++i) {
            byte c3 = cipherbytes[i];
            int m3 = map[c3];
            if (m3 < 0) {
                m3 = 27;
                score += -20.0;
            }
            score += (double)grams[m0][m1][m2][m3];
            m0 = m1;
            m1 = m2;
            m2 = m3;
        }
        return score;
    }

    public double computeScore(byte[] cipherbytes, int[] map) {
        int m2;
        int m1;
        int m0;
        int len = cipherbytes.length;
        int c0 = len > 2 ? cipherbytes[len - 2] : 27;
        int c1 = len > 1 ? cipherbytes[len - 1] : 27;
        int c2 = 26;
        int n = m0 = c0 >= 26 ? c0 : map[c0];
        if (m0 < 0) {
            m0 = 27;
        }
        int n2 = m1 = c1 >= 26 ? c1 : map[c1];
        if (m1 < 0) {
            m1 = 27;
        }
        int n3 = m2 = c2 >= 26 ? c2 : map[c2];
        if (m2 < 0) {
            m2 = 27;
        }
        float[][][][] grams = (float[][][][])this.getNgrams(4, true);
        double score = 0.0;
        for (int i = 0; i < cipherbytes.length; ++i) {
            int m3;
            int c3 = cipherbytes[i];
            int n4 = m3 = c3 == 26 ? c3 : map[c3];
            if (m3 < 0) {
                m3 = 27;
                score += -20.0;
            }
            score += (double)grams[m0][m1][m2][m3];
            m0 = m1;
            m1 = m2;
            m2 = m3;
        }
        return score;
    }

    static {
        syncObject = new Object();
        new PreloadThread().start();
    }

    static class PreloadThread
    extends Thread {
        PreloadThread() {
            this.setPriority(1);
        }

        public void run() {
            EnglishLanguage.getNgramsStatic(4, true);
            EnglishLanguage.getNgramsStatic(4, false);
        }
    }
}

