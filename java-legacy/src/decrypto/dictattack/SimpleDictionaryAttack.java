/*
 * Decompiled with CFR 0.152.
 */
package decrypto.dictattack;

import decrypto.Candidate;
import decrypto.MapSet;
import decrypto.Puzzle;
import decrypto.SolverListener;
import decrypto.StopFlag;
import decrypto.Word;
import decrypto.dictattack.DecryptoParameters;
import decrypto.dictattack.Planner;
import java.util.ArrayList;
import java.util.Random;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class SimpleDictionaryAttack {
    Puzzle puzzle;
    Planner planner;
    boolean[] solvedWords;
    int numSolvedWords;
    int[] firstcand;
    boolean gotFullSolution = false;
    ArrayList<SolverListener> listeners;
    public StopFlag stopFlag = null;
    public boolean pruneRootNode = true;
    public boolean pruneEachNode = true;
    public boolean scrambleWordOrder = false;
    int iters;
    public int maxIters = Integer.MAX_VALUE;
    int reportunits = 0;
    int reportdepth = 2;
    static final int MAXREPORTDEPTH = 10;
    int[] worksize = new int[10];
    int[] workprogress = new int[10];
    double lastprogress = -1.0;

    public SimpleDictionaryAttack(Puzzle puzzle, Planner planner, ArrayList<SolverListener> listeners) {
        this.puzzle = puzzle;
        this.planner = planner;
        this.listeners = listeners;
    }

    static final double roundScore(double in) {
        long T = 262144L;
        long s = (long)(in * (double)T);
        return (double)s / (double)T;
    }

    static final void swap(int[] array, int j, int k) {
        int tmp = array[j];
        array[j] = array[k];
        array[k] = tmp;
    }

    static final int indexOf(int[] array, int v) {
        for (int i = 0; i < array.length; ++i) {
            if (array[i] != v) continue;
            return i;
        }
        return -1;
    }

    public void solve(StopFlag stopFlag) {
        boolean inconsistent;
        this.stopFlag = stopFlag;
        MapSet map = this.puzzle.initialMap.copy();
        this.solvedWords = new boolean[this.puzzle.words.size()];
        for (int i = 0; i < this.puzzle.words.size(); ++i) {
            this.puzzle.words.get((int)i).firstcand = 0;
        }
        this.numSolvedWords = 0;
        if (this.pruneRootNode && (inconsistent = this.reduceCandidates(map))) {
            map = this.puzzle.initialMap.copy();
        }
        this.solve(0, map);
    }

    void reportSolution(MapSet map, boolean fullSolution) {
        if (fullSolution) {
            this.gotFullSolution = true;
        }
        double score = this.puzzle.lang.computeScore(this.puzzle.cipherBytes, map) / (double)this.puzzle.numCiphertextLetters;
        for (SolverListener sl : this.listeners) {
            sl.solverSolution(map, null, fullSolution, score);
        }
    }

    void solve(int recursiondepth, MapSet map) {
        ++this.iters;
        if (this.stopFlag.stop || this.iters > this.maxIters) {
            return;
        }
        if (this.numSolvedWords == this.puzzle.words.size()) {
            this.reportSolution(map, true);
            return;
        }
        int action = this.planner.getAction(this.solvedWords, map);
        if (action == -1) {
            this.reportSolution(map, true);
            return;
        }
        if (action == -2) {
            this.reportSolution(map, false);
            return;
        }
        if ((action & 0x200000) != 0) {
            byte[] a = new byte[1];
            byte[] b = new byte[1];
            a[0] = (byte)(action & 0xFFFF);
            this.reportProgressSize(recursiondepth, this.puzzle.lang.letterCount());
            int[] firstcandSave = new int[this.puzzle.words.size()];
            this.saveFirstcandState(firstcandSave);
            MapSet ourmap = map.copy();
            boolean leaf = true;
            for (int c = 0; c < this.puzzle.lang.letterCount(); ++c) {
                this.reportProgress(recursiondepth, c);
                b[0] = (byte)c;
                if (!ourmap.isMappingOkay(a, b)) continue;
                ourmap.setMappings(a, b);
                boolean inconsistent = this.reduceCandidates(ourmap);
                if (!inconsistent) {
                    this.solve(recursiondepth + 1, ourmap);
                    leaf = false;
                }
                ourmap.setTo(map);
                this.restoreFirstcandState(firstcandSave);
            }
            if (leaf) {
                this.reportSolution(map, false);
            }
            return;
        }
        if ((action & 0x100000) != 0) {
            int wordnum = action & 0xFFFF;
            Word w = this.puzzle.words.get(wordnum);
            this.solvedWords[wordnum] = true;
            ++this.numSolvedWords;
            if (!w.enabled) {
                assert (false);
                this.solve(recursiondepth + 1, map);
            } else {
                int[] firstcandSave = new int[this.puzzle.words.size()];
                this.saveFirstcandState(firstcandSave);
                MapSet ourmap = map.copy();
                this.reportProgressSize(recursiondepth, w.candidates.length - w.firstcand);
                boolean leaf = true;
                if (this.scrambleWordOrder) {
                    Random r = new Random();
                    for (int c = w.firstcand; c < w.candidates.length; ++c) {
                        int d = r.nextInt(w.candidates.length - w.firstcand) + w.firstcand;
                        Candidate cc = w.candidates[c];
                        Candidate cd = w.candidates[d];
                        w.candidates[d] = cc;
                        w.candidates[c] = cd;
                    }
                }
                for (int c = w.firstcand; c < w.candidates.length; ++c) {
                    this.reportProgress(recursiondepth, c - w.firstcand);
                    Candidate candidate = w.candidates[c];
                    if (!ourmap.isMappingOkay(w.word, candidate.cand)) continue;
                    leaf = false;
                    ourmap.setMappings(w.word, candidate.cand);
                    boolean inconsistent = false;
                    if (this.pruneEachNode) {
                        inconsistent = this.reduceCandidates(ourmap);
                    }
                    if (!inconsistent) {
                        this.solve(recursiondepth + 1, ourmap);
                        leaf = false;
                    }
                    ourmap.setTo(map);
                    this.restoreFirstcandState(firstcandSave);
                }
                if (leaf) {
                    this.reportSolution(ourmap, false);
                }
            }
            --this.numSolvedWords;
            this.solvedWords[wordnum] = false;
            return;
        }
        System.out.println("Planner error: No action specified (need WORD or LETTER flag).");
    }

    protected boolean reduceCandidates(MapSet set) {
        MapSet newset = new MapSet(this.puzzle.lang.letterCount());
        boolean dirty = true;
        for (int iters = 0; dirty && !this.stopFlag.stop && iters < DecryptoParameters.maxReduceIterations; ++iters) {
            dirty = false;
            for (int wordnum = 0; wordnum < this.puzzle.words.size(); ++wordnum) {
                if (this.solvedWords[wordnum]) continue;
                Word w = this.puzzle.words.get(wordnum);
                if (!w.enabled) continue;
                newset.setEmptySet();
                for (int c = w.firstcand; c < w.candidates.length; ++c) {
                    Candidate candidate = w.candidates[c];
                    if (set.isMappingOkay(w.word, candidate.cand)) {
                        newset.enableMappings(w.word, candidate.cand);
                        continue;
                    }
                    Candidate tmp = w.candidates[w.firstcand];
                    w.candidates[w.firstcand] = candidate;
                    w.candidates[c] = tmp;
                    ++w.firstcand;
                    dirty = true;
                }
                set.intersectWith(newset);
            }
            if (dirty) {
                set.selfReduce();
            }
            long mappedTo = 0L;
            for (int i = 0; i < this.puzzle.lang.letterCount(); ++i) {
                if (!set.hasMappings((byte)i)) {
                    return true;
                }
                int mi = set.getMapping((byte)i);
                if (mi < 0) continue;
                if ((mappedTo & (long)(1 << mi)) > 0L) {
                    return true;
                }
                mappedTo |= (long)(1 << mi);
            }
        }
        return false;
    }

    void reportProgressSize(int depth, int amount) {
        if (depth <= this.reportdepth) {
            this.worksize[depth] = amount;
        }
    }

    void reportProgress(int depth, int amount) {
        if (depth <= this.reportdepth) {
            this.workprogress[depth] = amount;
            double frac = 1.0;
            double prog = 0.0;
            for (int i = 0; i <= depth; ++i) {
                prog += frac * (double)this.workprogress[i] / (double)this.worksize[i];
                frac /= (double)this.worksize[i];
            }
            if (prog - this.lastprogress < 0.03) {
                return;
            }
            this.lastprogress = prog;
            for (SolverListener listener : this.listeners) {
                listener.solverProgress(prog);
            }
        }
    }

    void saveFirstcandState(int[] v) {
        for (int widx = 0; widx < this.puzzle.words.size(); ++widx) {
            v[widx] = this.puzzle.words.get((int)widx).firstcand;
        }
    }

    void restoreFirstcandState(int[] v) {
        for (int widx = 0; widx < this.puzzle.words.size(); ++widx) {
            this.puzzle.words.get((int)widx).firstcand = v[widx];
        }
    }
}

