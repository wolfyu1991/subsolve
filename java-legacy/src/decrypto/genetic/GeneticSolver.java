/*
 * Decompiled with CFR 0.152.
 */
package decrypto.genetic;

import decrypto.Puzzle;
import decrypto.Solver;
import decrypto.SolverListener;
import decrypto.StopFlag;
import decrypto.genetic.GAProblem;
import decrypto.genetic.GeneticSearch;
import decrypto.genetic.Sampler;
import decrypto.statistics.GramFreq;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class GeneticSolver
implements Solver {
    GAProblem problem;
    Puzzle puzzle;
    ArrayList<SolverListener> listeners = new ArrayList();
    SolverThread solverThread;
    double startTime = 0.0;
    double endTime = 0.0;
    StopFlag stopFlag;
    static HashMap<String, Sampler<String>> gramfreqws;
    static HashMap<String, Sampler<String>> gramfreqns;
    HashMap<String, Sampler<String>> gramfreq;
    Exception runException;

    public GeneticSolver(GAProblem problem) {
        this.problem = problem;
        this.puzzle = problem.puzzle;
        this.gramfreq = this.puzzle.trustSpaces ? gramfreqws : gramfreqns;
    }

    public Exception getException() {
        return this.runException;
    }

    public void addListener(SolverListener listener) {
        this.listeners.add(listener);
    }

    public void start(double maxTime) {
        this.stopFlag = new StopFlag(maxTime);
        assert (this.solverThread == null);
        this.startTime = (double)System.currentTimeMillis() / 1000.0;
        this.solverThread = new SolverThread();
        this.solverThread.start();
    }

    public double getElapsedTime() {
        if (this.endTime != 0.0) {
            return this.endTime - this.startTime;
        }
        if (this.startTime == 0.0) {
            return 0.0;
        }
        return (double)System.currentTimeMillis() / 1000.0 - this.startTime;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void stop() {
        assert (this.solverThread != null);
        if (Thread.currentThread() != this.solverThread) {
            SolverThread solverThread = this.solverThread;
            synchronized (solverThread) {
                this.stopFlag.stop = true;
                try {
                    this.solverThread.join();
                }
                catch (InterruptedException interruptedException) {
                    // empty catch block
                }
            }
        }
        this.stopFlag.stop = true;
        this.endTime = (double)System.currentTimeMillis() / 1000.0;
    }

    static final void shuffle(Random r, byte[] v, int vlen) {
        for (int i = 0; i < vlen; ++i) {
            byte mj;
            int j = r.nextInt(vlen - i) + i;
            byte mi = v[i];
            v[i] = mj = v[j];
            v[j] = mi;
        }
    }

    double similarity(int[] mapa, int[] mapb) {
        int same = 0;
        if (mapa == null || mapb == null) {
            return 0.0;
        }
        for (int i = 0; i < this.puzzle.lang.letterCount(); ++i) {
            if (this.puzzle.histogram[i] <= 0 || mapa[i] != mapb[i]) continue;
            ++same;
        }
        return (double)same / (double)this.puzzle.numUniqueCiphertextLetters;
    }

    static {
        try {
            gramfreqns = GramFreq.readBinary("gramfreq5ns.stb");
            gramfreqws = GramFreq.readBinary("gramfreq5.stb");
        }
        catch (Exception ex) {
            System.out.println("ex: " + ex);
            ex.printStackTrace();
        }
    }

    class SolverThread
    extends Thread {
        SolverThread() {
        }

        GeneticSearch makeMutatedSearch(GeneticSearch bestSearch) {
            int i;
            byte[] plainbyteorphans;
            int ncipherbyteorphans;
            int ncipherbytes;
            byte[] plainbytes;
            GeneticSearch newSearch;
            block16: {
                newSearch = bestSearch.mutate(1 + GeneticSolver.this.problem.rand.nextInt(GeneticSolver.this.puzzle.lang.letterCount()));
                if (newSearch == null) {
                    return new GeneticSearch(GeneticSolver.this.problem, GeneticSolver.this.stopFlag);
                }
                int len = GeneticSolver.this.gramfreq.keySet().iterator().next().length();
                if (GeneticSolver.this.puzzle.cipherBytes.length <= len) {
                    return null;
                }
                int offset = GeneticSolver.this.problem.rand.nextInt(GeneticSolver.this.puzzle.cipherBytes.length - len);
                byte[] ciphersnippet = new byte[len];
                for (int i2 = 0; i2 < len; ++i2) {
                    ciphersnippet[i2] = GeneticSolver.this.puzzle.cipherBytes[offset + i2];
                }
                String pattern = GramFreq.makePattern(GeneticSolver.this.puzzle.lang.bytesToString(ciphersnippet));
                Sampler<String> sampler = GeneticSolver.this.gramfreq.get(pattern);
                if (sampler == null) {
                    return null;
                }
                String plainsnippetstring = sampler.sample(GeneticSolver.this.problem.rand.nextDouble());
                byte[] cipherbytes = new byte[len];
                plainbytes = new byte[len];
                ncipherbytes = 0;
                for (int i3 = 0; i3 < ciphersnippet.length; ++i3) {
                    byte cipherbyte = ciphersnippet[i3];
                    byte plainbyte = GeneticSolver.this.puzzle.lang.charToByte(plainsnippetstring.charAt(i3));
                    if (plainbyte == GeneticSolver.this.puzzle.lang.letterCount()) continue;
                    boolean alreadyhave = false;
                    for (int j = 0; j < i3; ++j) {
                        if (cipherbytes[j] != cipherbyte) continue;
                        alreadyhave = true;
                    }
                    if (alreadyhave) continue;
                    cipherbytes[ncipherbytes] = cipherbyte;
                    plainbytes[ncipherbytes] = plainbyte;
                    ++ncipherbytes;
                    if (GeneticSolver.this.puzzle.initialMap.isMappingOkay(cipherbyte, plainbyte)) continue;
                    return null;
                }
                byte[] cipherbyteorphans = new byte[len];
                ncipherbyteorphans = 0;
                plainbyteorphans = new byte[len];
                int nplainbyteorphans = 0;
                for (int cbidx = 0; cbidx < ncipherbytes; ++cbidx) {
                    byte cipherbyteorphan = -1;
                    for (byte j = 0; j < GeneticSolver.this.puzzle.lang.letterCount(); j = (byte)(j + 1)) {
                        if (newSearch.map[j] != plainbytes[cbidx]) continue;
                        cipherbyteorphan = j;
                        break;
                    }
                    boolean orphan = true;
                    for (int i4 = 0; i4 < ncipherbytes; ++i4) {
                        if (cipherbytes[i4] != cipherbyteorphan) continue;
                        orphan = false;
                    }
                    if (orphan) {
                        cipherbyteorphans[ncipherbyteorphans++] = cipherbyteorphan;
                    }
                    byte plainbyteorphan = (byte)newSearch.map[cipherbytes[cbidx]];
                    boolean orphan2 = true;
                    for (int i5 = 0; i5 < ncipherbytes; ++i5) {
                        if (plainbytes[i5] != plainbyteorphan) continue;
                        orphan2 = false;
                    }
                    if (!orphan2) continue;
                    plainbyteorphans[nplainbyteorphans++] = plainbyteorphan;
                }
                assert (ncipherbyteorphans == nplainbyteorphans);
                int shuffletrials = 0;
                do {
                    GeneticSolver.shuffle(GeneticSolver.this.problem.rand, cipherbyteorphans, ncipherbyteorphans);
                    boolean bad = false;
                    for (int i6 = 0; i6 < ncipherbyteorphans; ++i6) {
                        if (GeneticSolver.this.puzzle.initialMap.isMappingOkay(cipherbyteorphans[i6], plainbyteorphans[i6])) continue;
                        bad = true;
                    }
                    if (!bad) break block16;
                } while (++shuffletrials <= 100);
                return null;
            }
            for (i = 0; i < ncipherbyteorphans; ++i) {
                newSearch.map[cipherbyteorphans[i]] = plainbyteorphans[i];
            }
            for (i = 0; i < ncipherbytes; ++i) {
                newSearch.map[cipherbytes[i]] = plainbytes[i];
            }
            return newSearch;
        }

        public void run() {
            try {
                this.runEx();
            }
            catch (Exception ex) {
                GeneticSolver.this.runException = ex;
                ex.printStackTrace();
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        void runEx() {
            for (SolverListener listener : GeneticSolver.this.listeners) {
                listener.solverMessage("Searching...");
            }
            ArrayList<GeneticSearch> bestSearches = new ArrayList<GeneticSearch>();
            int loops = 0;
            while (true) {
                ++loops;
                SolverThread solverThread = this;
                synchronized (solverThread) {
                    if (GeneticSolver.this.stopFlag.stop) {
                        break;
                    }
                }
                GeneticSearch newSearch = null;
                boolean[] protect = null;
                if (bestSearches.size() == 0) {
                    newSearch = new GeneticSearch(GeneticSolver.this.problem, GeneticSolver.this.stopFlag);
                } else {
                    int r = GeneticSolver.this.problem.rand.nextInt(3);
                    switch (r) {
                        case 0: {
                            GeneticSearch parent = (GeneticSearch)bestSearches.get(GeneticSolver.this.problem.rand.nextInt(bestSearches.size()));
                            newSearch = this.makeMutatedSearch(parent);
                            break;
                        }
                        case 1: {
                            GeneticSearch parent = (GeneticSearch)bestSearches.get(GeneticSolver.this.problem.rand.nextInt(bestSearches.size()));
                            newSearch = parent.mutate(GeneticSolver.this.problem.rand.nextInt(GeneticSolver.this.puzzle.lang.letterCount() / 2));
                            break;
                        }
                        case 2: {
                            newSearch = new GeneticSearch(GeneticSolver.this.problem, GeneticSolver.this.stopFlag);
                        }
                    }
                }
                if (newSearch == null) continue;
                newSearch.listeners = GeneticSolver.this.listeners;
                newSearch.iterate(10000, protect);
                GeneticSolver.this.problem.scorethresh = Math.max(GeneticSolver.this.problem.scorethresh, newSearch.score);
                if (bestSearches.size() == 0) {
                    bestSearches.add(newSearch);
                    continue;
                }
                bestSearches.add(newSearch);
                for (int i = 0; i < bestSearches.size(); ++i) {
                    for (int j = 0; j < i; ++j) {
                        GeneticSearch gsi = (GeneticSearch)bestSearches.get(i);
                        GeneticSearch gsj = (GeneticSearch)bestSearches.get(j);
                        if (gsi == null || gsj == null || !(GeneticSolver.this.similarity(gsi.map, gsj.map) > 0.65)) continue;
                        if (gsi.score > gsj.score) {
                            bestSearches.set(j, null);
                            continue;
                        }
                        bestSearches.set(i, null);
                    }
                }
                ArrayList<GeneticSearch> filteredSearches = new ArrayList<GeneticSearch>();
                for (GeneticSearch gs : bestSearches) {
                    if (gs == null) continue;
                    filteredSearches.add(gs);
                }
                bestSearches = filteredSearches;
                if (bestSearches.size() <= 15) continue;
                GeneticSearch worstSearch = null;
                int worstidx = -1;
                for (int i = 0; i < bestSearches.size(); ++i) {
                    GeneticSearch gs = (GeneticSearch)bestSearches.get(i);
                    if (gs == null || worstSearch != null && !(gs.score < worstSearch.score)) continue;
                    worstidx = i;
                    worstSearch = gs;
                }
                bestSearches.remove(worstidx);
            }
            GeneticSolver.this.endTime = (double)System.currentTimeMillis() / 1000.0;
            double elapsedTime = GeneticSolver.this.endTime - GeneticSolver.this.startTime;
            for (SolverListener sl : GeneticSolver.this.listeners) {
                sl.solverFinished(elapsedTime);
            }
        }
    }
}

