/*
 * Decompiled with CFR 0.152.
 */
package decrypto.genetic;

import decrypto.Puzzle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

public class GAProblem {
    Puzzle puzzle;
    short[][][] table;
    int nextGenerationId;
    Random rand = new Random();
    double scorethresh = -1.7976931348623157E308;
    float[][][][] tetragrams;
    float[][][][] tetragramsNS;

    public GAProblem(Puzzle puzzle) {
        this.puzzle = puzzle;
        this.table = this.precomputeIndicesTable();
        this.tetragrams = (float[][][][])puzzle.lang.getNgrams(4, true);
        this.tetragramsNS = (float[][][][])puzzle.lang.getNgrams(4, false);
    }

    short[][][] precomputeIndicesTable() {
        HashMap<String, Integer> canonicalMap = new HashMap<String, Integer>();
        int[] canonicalNgrams = new int[this.puzzle.cipherBytes.length];
        for (int i = 0; i < this.puzzle.cipherBytes.length - 3; ++i) {
            String s = "" + ((char)this.puzzle.cipherBytes[i] + 65) + ((char)this.puzzle.cipherBytes[i + 1] + 65) + ((char)this.puzzle.cipherBytes[i + 2] + 65) + ((char)this.puzzle.cipherBytes[i + 3] + 65);
            Integer canonical = (Integer)canonicalMap.get(s);
            if (canonical == null) {
                canonicalMap.put(s, i);
                canonical = i;
            }
            canonicalNgrams[i] = canonical;
        }
        short[][][] twolettertable = new short[26][26][];
        for (byte a = 0; a < 26; ++a) {
            for (byte b = 0; b < 26; ++b) {
                ArrayList<Integer> indices = new ArrayList<Integer>();
                for (int i = 0; i < this.puzzle.cipherBytes.length - 3; ++i) {
                    if (this.puzzle.cipherBytes[i] != a && this.puzzle.cipherBytes[i + 1] != a && this.puzzle.cipherBytes[i + 2] != a && this.puzzle.cipherBytes[i] != b && this.puzzle.cipherBytes[i + 1] != b && this.puzzle.cipherBytes[i + 2] != b) continue;
                    indices.add(canonicalNgrams[i]);
                }
                Collections.sort(indices);
                ArrayList<Integer> compressed = new ArrayList<Integer>();
                int idx = 0;
                while (idx < indices.size()) {
                    short v = (short)((Integer)indices.get(idx)).intValue();
                    int eidx = idx;
                    while (eidx + 1 < indices.size() && (Integer)indices.get(eidx + 1) == v) {
                        ++eidx;
                    }
                    compressed.add(Integer.valueOf(v));
                    compressed.add(eidx - idx + 1);
                    idx = eidx + 1;
                }
                twolettertable[a][b] = new short[compressed.size()];
                for (int i = 0; i < compressed.size(); ++i) {
                    twolettertable[a][b][i] = (short)((Integer)compressed.get(i)).intValue();
                }
            }
        }
        return twolettertable;
    }

    public final double computeScore(int[] map) {
        float[][][][] grams;
        int ca;
        double score = 0.0;
        int cb = ca = this.puzzle.lang.letterCount();
        int cc = ca;
        int len = this.puzzle.cipherBytes.length;
        if (this.puzzle.trustSpaces) {
            if (len > 2) {
                ca = this.puzzle.cipherBytes[len - 2];
            }
            if (len > 1) {
                cb = this.puzzle.cipherBytes[len - 1];
            }
            cc = this.puzzle.lang.letterCount();
            grams = this.tetragrams;
        } else {
            if (len > 3) {
                ca = this.puzzle.cipherBytes[len - 3];
            }
            if (len > 2) {
                cb = this.puzzle.cipherBytes[len - 2];
            }
            if (len > 1) {
                cc = this.puzzle.cipherBytes[len - 1];
            }
            grams = this.tetragramsNS;
        }
        int ma = map[ca];
        int mb = map[cb];
        int mc = map[cc];
        for (int idx = 0; idx < this.puzzle.cipherBytes.length; ++idx) {
            byte cd = this.puzzle.cipherBytes[idx];
            int md = map[cd];
            score += (double)grams[ma][mb][mc][md];
            ma = mb;
            mb = mc;
            mc = md;
        }
        return score;
    }
}

