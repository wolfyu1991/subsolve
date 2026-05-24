/*
 * Decompiled with CFR 0.152.
 */
package decrypto.genetic;

import decrypto.Dict;
import decrypto.MapSet;
import decrypto.Puzzle;
import decrypto.SolverListener;
import decrypto.StopFlag;
import decrypto.genetic.GAProblem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class GeneticSearch {
    GAProblem problem;
    Puzzle puzzle;
    int[] map;
    double score = -1.7976931348623157E308;
    int totaliterations = 0;
    ArrayList<SolverListener> listeners = new ArrayList();
    StopFlag stopFlag;

    public GeneticSearch(GAProblem problem, StopFlag stopFlag) {
        this.problem = problem;
        this.puzzle = problem.puzzle;
        this.stopFlag = stopFlag;
        this.makeInitialMap();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    boolean makeInitialMap() {
        this.map = new int[this.puzzle.lang.letterCount() + 1];
        this.map[this.puzzle.lang.letterCount()] = this.puzzle.lang.letterCount();
        ArrayList<LetterChoice> choices = new ArrayList<LetterChoice>();
        for (int i = 0; i < this.puzzle.lang.letterCount(); ++i) {
            LetterChoice lc = new LetterChoice();
            lc.cipherletter = i;
            lc.nchoices = 0;
            choices.add(lc);
            for (int j = 0; j < this.puzzle.lang.letterCount(); ++j) {
                if (!this.puzzle.initialMap.isMappingOkay((byte)i, (byte)j)) continue;
                ++lc.nchoices;
            }
        }
        Collections.sort(choices);
        boolean badinit = true;
        boolean trials = false;
        block5: while (badinit) {
            StopFlag j = this.stopFlag;
            synchronized (j) {
                if (this.stopFlag.stop) {
                    this.map = null;
                    return false;
                }
            }
            boolean[] mapped = new boolean[this.puzzle.lang.letterCount()];
            badinit = false;
            for (int i = 0; i < this.puzzle.lang.letterCount(); ++i) {
                int pi;
                int ci = ((LetterChoice)choices.get((int)i)).cipherletter;
                int[] possibilities = new int[this.puzzle.lang.letterCount()];
                int npossibilities = 0;
                for (int j2 = 0; j2 < this.puzzle.lang.letterCount(); ++j2) {
                    if (!this.puzzle.initialMap.isMappingOkay((byte)ci, (byte)j2) || mapped[j2]) continue;
                    possibilities[npossibilities++] = j2;
                }
                if (npossibilities == 0) {
                    badinit = true;
                    continue block5;
                }
                this.map[ci] = pi = possibilities[this.problem.rand.nextInt(npossibilities)];
                mapped[pi] = true;
            }
        }
        return true;
    }

    void reportSolution(int[] map, double score) {
        int[] spaces = null;
        MapSet mapset = new MapSet(map);
        if (!this.puzzle.trustSpaces) {
            spaces = GeneticSearch.findSpaces4(this.puzzle, this.puzzle.cipherBytes, map);
        }
        for (SolverListener listener : this.listeners) {
            listener.solverSolution(mapset, spaces, false, score / (double)this.puzzle.numCiphertextLetters);
        }
    }

    void shuffle(int[] v) {
        for (int i = 0; i < v.length; ++i) {
            int mj;
            int j = this.problem.rand.nextInt(v.length - i) + i;
            int mi = v[i];
            v[i] = mj = v[j];
            v[j] = mi;
        }
    }

    public GeneticSearch mutate(int npermutes) {
        int[] pletters;
        int[] cletters;
        GeneticSearch gas;
        block6: {
            gas = new GeneticSearch(this.problem, this.stopFlag);
            if (this.map == null || gas.map == null) {
                return null;
            }
            System.arraycopy(this.map, 0, gas.map, 0, this.map.length);
            cletters = new int[npermutes];
            pletters = new int[npermutes];
            boolean[] clettersPicked = new boolean[this.puzzle.lang.letterCount()];
            for (int i = 0; i < npermutes; ++i) {
                int idx;
                while (clettersPicked[idx = this.problem.rand.nextInt(this.puzzle.lang.letterCount())]) {
                }
                clettersPicked[idx] = true;
                cletters[i] = idx;
                pletters[i] = this.map[idx];
            }
            int trials = 0;
            do {
                this.shuffle(cletters);
                boolean bad = false;
                for (int i = 0; i < cletters.length; ++i) {
                    if (this.puzzle.initialMap.isMappingOkay((byte)cletters[i], (byte)pletters[i])) continue;
                    bad = true;
                    break;
                }
                if (!bad) break block6;
            } while (++trials <= 1000);
            return null;
        }
        for (int i = 0; i < cletters.length; ++i) {
            gas.map[cletters[i]] = pletters[i];
        }
        return gas;
    }

    int[] makeSwaps() {
        int[] swaps = new int[this.puzzle.lang.letterCount() * (this.puzzle.lang.letterCount() - 1)];
        int swappos = 0;
        for (int i = 0; i < this.puzzle.lang.letterCount(); ++i) {
            for (int j = 0; j < this.puzzle.lang.letterCount(); ++j) {
                if (i == j) continue;
                swaps[swappos++] = (i << 16) + j;
            }
        }
        return swaps;
    }

    public void iterate(int maxIterations, boolean[] protect) {
        if (this.map == null) {
            return;
        }
        long starttime = System.nanoTime();
        int iters = 0;
        int[] swaps = this.makeSwaps();
        this.score = this.problem.computeScore(this.map);
        if (this.score > this.problem.scorethresh) {
            this.reportSolution(this.map, this.score);
        }
        iters = 0;
        while (iters < maxIterations) {
            boolean keptSwap = false;
            this.shuffle(swaps);
            for (int swapidx = 0; swapidx < swaps.length; ++swapidx) {
                int mb;
                int a = swaps[swapidx] >> 16;
                int b = swaps[swapidx] & 0xFFFF;
                double newscore = 0.0;
                if (!this.puzzle.initialMap.isMappingOkay((byte)a, (byte)this.map[b]) || !this.puzzle.initialMap.isMappingOkay((byte)b, (byte)this.map[a]) || protect != null && (protect[a] || protect[b])) continue;
                int ma = this.map[a];
                this.map[a] = mb = this.map[b];
                this.map[b] = ma;
                newscore = this.problem.computeScore(this.map);
                if (newscore <= this.score) {
                    this.map[a] = ma;
                    this.map[b] = mb;
                    continue;
                }
                this.score = newscore;
                keptSwap = true;
                if (!(this.score > this.problem.scorethresh)) continue;
                this.reportSolution(this.map, this.score);
            }
            if (!keptSwap) break;
            ++iters;
            ++this.totaliterations;
        }
        long endtime = System.nanoTime();
    }

    static int[] findSpaces4(Puzzle puzzle, byte[] cipherbytes, int[] map) {
        int[][] spaces = new int[8][cipherbytes.length];
        double[] scores = new double[8];
        if (cipherbytes.length < 4) {
            return spaces[0];
        }
        float[][][][] grams = (float[][][][])puzzle.lang.getNgrams(4, true);
        int[] tsv = new int[8];
        for (int j = 0; j < 8; ++j) {
            int tsvlen = 0;
            tsv[tsvlen++] = map[cipherbytes[cipherbytes.length - 1]];
            tsv[tsvlen++] = 26;
            tsv[tsvlen++] = map[cipherbytes[0]];
            if ((j & 4) > 0) {
                tsv[tsvlen++] = 26;
            }
            tsv[tsvlen++] = map[cipherbytes[1]];
            if ((j & 2) > 0) {
                tsv[tsvlen++] = 26;
            }
            tsv[tsvlen++] = map[cipherbytes[2]];
            if ((j & 2) > 0) {
                tsv[tsvlen++] = 26;
            }
            for (int k = 0; k < tsvlen - 3; ++k) {
                int n = j;
                scores[n] = scores[n] + (double)grams[tsv[k]][tsv[k + 1]][tsv[k + 2]][tsv[k + 3]];
            }
        }
        int[][] newspaces = new int[8][cipherbytes.length];
        int[] tsv2 = new int[8];
        double[] expandedscores = new double[16];
        for (int i = 3; i < cipherbytes.length; ++i) {
            int j;
            for (j = 0; j < 8; ++j) {
                int tsvlen = 0;
                tsv2[tsvlen++] = map[cipherbytes[i - 3]];
                if ((j & 4) > 0) {
                    tsv2[tsvlen++] = 26;
                }
                tsv2[tsvlen++] = map[cipherbytes[i - 2]];
                if ((j & 2) > 0) {
                    tsv2[tsvlen++] = 26;
                }
                tsv2[tsvlen++] = map[cipherbytes[i - 1]];
                if ((j & 1) > 0) {
                    tsv2[tsvlen++] = 26;
                }
                tsv2[tsvlen++] = map[cipherbytes[i]];
                int md = tsv2[tsvlen - 1];
                int mc = tsv2[tsvlen - 2];
                int mb = tsv2[tsvlen - 3];
                int ma = tsv2[tsvlen - 4];
                expandedscores[j * 2 + 0] = scores[j] + (double)grams[ma][mb][mc][md];
                if (i == cipherbytes.length - 1) {
                    int n = j * 2 + 0;
                    expandedscores[n] = expandedscores[n] + (double)grams[mb][mc][md][26];
                }
                expandedscores[j * 2 + 1] = scores[j] + (double)grams[ma][mb][mc][md] + (double)grams[mb][mc][md][26];
            }
            for (j = 0; j < 8; ++j) {
                int k;
                if (expandedscores[j] >= expandedscores[j + 8]) {
                    for (k = 0; k < i - 3; ++k) {
                        newspaces[j][k] = spaces[j / 2][k];
                    }
                    newspaces[j][i - 3] = 0;
                    scores[j] = expandedscores[j];
                    continue;
                }
                for (k = 0; k < i - 3; ++k) {
                    newspaces[j][k] = spaces[(j + 8) / 2][k];
                }
                newspaces[j][i - 3] = 1;
                scores[j] = expandedscores[j + 8];
            }
            int[][] tmp = spaces;
            spaces = newspaces;
            newspaces = tmp;
        }
        int bestidx = -1;
        double bestscore = -1.7976931348623157E308;
        for (int j = 0; j < 8; ++j) {
            spaces[j][cipherbytes.length - 3] = (j & 4) > 0 ? 1 : 0;
            spaces[j][cipherbytes.length - 2] = (j & 2) > 0 ? 1 : 0;
            int n = spaces[j][cipherbytes.length - 1] = (j & 1) > 0 ? 1 : 0;
            if (!(scores[j] > bestscore)) continue;
            bestscore = scores[j];
            bestidx = j;
        }
        spaces[bestidx][cipherbytes.length - 1] = 0;
        return spaces[bestidx];
    }

    public static void main(String[] args) {
        try {
            GeneticSearch.mainEx(args);
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    static void mainEx(String[] args) throws IOException {
        Dict dict = new Dict("english-standard.dat");
        Puzzle p = new Puzzle(dict, args[0], args[1], false, true);
        int[] map = new int[p.lang.letterCount() + 1];
        for (int i = 0; i < map.length; ++i) {
            map[i] = i;
        }
        int[] spaces = GeneticSearch.findSpaces4(p, p.cipherBytes, map);
        System.out.println(p.lang.translateString(p.cipherText, new MapSet(map), spaces));
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    class LetterChoice
    implements Comparable<LetterChoice> {
        int cipherletter;
        int nchoices;

        LetterChoice() {
        }

        @Override
        public int compareTo(LetterChoice lc) {
            return this.nchoices - lc.nchoices;
        }
    }
}

