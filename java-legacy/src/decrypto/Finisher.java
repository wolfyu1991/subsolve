/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.Language;
import decrypto.MapSet;
import decrypto.Puzzle;
import java.util.Random;

public class Finisher {
    Puzzle puzzle;
    int[] startmap;
    int[] ourmap;
    Language lang;
    byte[] cletters;
    byte[] pletters;
    Random rand = new Random();

    public Finisher(Puzzle puzzle, MapSet _map) {
        int i;
        this.puzzle = puzzle;
        this.lang = puzzle.lang;
        byte[] _cletters = new byte[this.lang.letterCount()];
        int ncletters = 0;
        byte[] _pletters = new byte[this.lang.letterCount()];
        int npletters = 0;
        boolean[] mapped = new boolean[this.lang.letterCount()];
        for (i = 0; i < this.lang.letterCount(); ++i) {
            int mi = _map.getMapping((byte)i);
            if (mi >= 0) {
                mapped[_map.getMapping((byte)((byte)i))] = true;
                continue;
            }
            if (puzzle.histogram[i] <= 0) continue;
            _cletters[ncletters++] = (byte)i;
        }
        for (i = 0; i < this.lang.letterCount(); ++i) {
            if (mapped[i]) continue;
            _pletters[npletters++] = (byte)i;
        }
        this.cletters = new byte[ncletters];
        for (i = 0; i < ncletters; ++i) {
            this.cletters[i] = _cletters[i];
        }
        this.pletters = new byte[npletters];
        for (i = 0; i < npletters; ++i) {
            this.pletters[i] = _pletters[i];
        }
        this.startmap = _map.makeMap();
    }

    public MapSet bruteForce() {
        boolean verbose = false;
        if (this.cletters.length == 0) {
            return new MapSet(this.startmap);
        }
        this.ourmap = new int[this.startmap.length];
        for (int i = 0; i < this.startmap.length; ++i) {
            this.ourmap[i] = this.startmap[i];
        }
        int iters = (int)Math.pow(this.pletters.length, this.cletters.length);
        long starttime = System.currentTimeMillis();
        if (verbose) {
            System.out.printf("===Greedy Finisher: cipherletters: %d plainletters: %d (%d iters)\n", this.cletters.length, this.pletters.length, iters);
            System.out.printf("%15.5f : %s\n", this.lang.computeScore(this.puzzle.cipherBytes, this.ourmap) / (double)this.puzzle.numCiphertextLetters, this.lang.translateString(this.puzzle.cipherText, new MapSet(this.ourmap), null));
        }
        if (iters > 30000) {
            return new MapSet(this.ourmap);
        }
        int[] newmap = new int[this.cletters.length];
        int[] bestmap = new int[this.ourmap.length];
        double bestscore = -1.7976931348623157E308;
        for (int iter = 0; iter < iters; ++iter) {
            double score;
            int tmp = iter;
            boolean good = true;
            for (int i = 0; i < this.cletters.length; ++i) {
                newmap[i] = tmp % this.pletters.length;
                tmp /= this.pletters.length;
                for (int j = 0; j < i; ++j) {
                    if (newmap[j] != newmap[i]) continue;
                    good = false;
                }
                if (!this.puzzle.initialMap.isMappingOkay(this.cletters[i], this.pletters[newmap[i]])) {
                    good = false;
                    break;
                }
                this.ourmap[this.cletters[i]] = this.pletters[newmap[i]];
            }
            if (!good || !((score = this.lang.computeScore(this.puzzle.cipherBytes, this.ourmap)) > bestscore)) continue;
            bestscore = score;
            for (int i = 0; i < this.ourmap.length; ++i) {
                bestmap[i] = this.ourmap[i];
            }
        }
        if (bestmap == null) {
            return new MapSet(this.startmap);
        }
        if (verbose) {
            System.out.printf("%15.5f : %s\n", this.lang.computeScore(this.puzzle.cipherBytes, bestmap) / (double)this.puzzle.numCiphertextLetters, this.lang.translateString(this.puzzle.cipherText, new MapSet(bestmap), null));
            long endtime = System.currentTimeMillis();
            System.out.printf("time: %15.5f\n", (double)(endtime - starttime) / 1000.0);
        }
        return new MapSet(bestmap);
    }

    public void randomize() {
        for (int i = 0; i < this.cletters.length; ++i) {
            int j;
            boolean okay;
            do {
                j = this.rand.nextInt(this.pletters.length);
                okay = true;
                for (int k = 0; k < i; ++k) {
                    if (this.ourmap[this.cletters[k]] != this.pletters[j]) continue;
                    okay = false;
                }
            } while (!okay);
            this.ourmap[this.cletters[i]] = this.pletters[j];
        }
    }

    public void iterate() {
    }
}

