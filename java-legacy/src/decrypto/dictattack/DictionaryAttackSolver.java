/*
 * Decompiled with CFR 0.152.
 */
package decrypto.dictattack;

import decrypto.Puzzle;
import decrypto.Solver;
import decrypto.SolverListener;
import decrypto.StopFlag;
import decrypto.Word;
import decrypto.dictattack.DecryptoParameters;
import decrypto.dictattack.LazyPlanner;
import decrypto.dictattack.RandomPlanner;
import decrypto.dictattack.SimpleDictionaryAttack;
import java.util.ArrayList;

public class DictionaryAttackSolver
implements Solver {
    ArrayList<SolverListener> listeners = new ArrayList();
    double startTime = 0.0;
    double endTime = 0.0;
    SolverThread solverThread;
    Puzzle puzzle;
    StopFlag stopFlag;
    public boolean runForever = false;
    Exception runException;

    public DictionaryAttackSolver(Puzzle puzzle) {
        this.puzzle = puzzle;
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
        this.endTime = (double)System.currentTimeMillis() / 1000.0;
    }

    class SolverThread
    extends Thread {
        SolverThread() {
        }

        public void run() {
            for (SolverListener sl : DictionaryAttackSolver.this.listeners) {
                sl.solverMessage("Doing initial search...");
            }
            for (Word w : DictionaryAttackSolver.this.puzzle.words) {
                w.enabled = true;
            }
            SimpleDictionaryAttack sda = new SimpleDictionaryAttack(DictionaryAttackSolver.this.puzzle, new LazyPlanner(DictionaryAttackSolver.this.puzzle), DictionaryAttackSolver.this.listeners);
            sda.solve(DictionaryAttackSolver.this.stopFlag);
            if (!sda.gotFullSolution || DictionaryAttackSolver.this.runForever) {
                int maxDisableWordCount;
                int iters = 1;
                for (maxDisableWordCount = Math.min(DecryptoParameters.maxRandomWordDisable, DictionaryAttackSolver.this.puzzle.words.size() - 1); maxDisableWordCount > 1 && Math.pow(DictionaryAttackSolver.this.puzzle.words.size(), maxDisableWordCount) > (double)DecryptoParameters.maxRandomWordIterations; --maxDisableWordCount) {
                }
                boolean gotSolution = false;
                for (int disableWordCount = 1; disableWordCount <= maxDisableWordCount; ++disableWordCount) {
                    iters *= DictionaryAttackSolver.this.puzzle.words.size();
                    if (DictionaryAttackSolver.this.stopFlag.stop) break;
                    for (int i = 0; i <= iters; ++i) {
                        for (Word w : DictionaryAttackSolver.this.puzzle.words) {
                            w.enabled = true;
                        }
                        String message = "Disabling words: ";
                        int tmp = i;
                        boolean bad = false;
                        for (int j = 0; j < disableWordCount; ++j) {
                            int widx = tmp % DictionaryAttackSolver.this.puzzle.words.size();
                            tmp /= DictionaryAttackSolver.this.puzzle.words.size();
                            if (!DictionaryAttackSolver.this.puzzle.words.get((int)widx).enabled) {
                                bad = true;
                            }
                            DictionaryAttackSolver.this.puzzle.words.get((int)widx).enabled = false;
                            message = message + DictionaryAttackSolver.this.puzzle.words.get((int)widx).text + " ";
                        }
                        if (bad) continue;
                        for (SolverListener sl : DictionaryAttackSolver.this.listeners) {
                            sl.solverMessage(message);
                        }
                        sda = new SimpleDictionaryAttack(DictionaryAttackSolver.this.puzzle, new LazyPlanner(DictionaryAttackSolver.this.puzzle), DictionaryAttackSolver.this.listeners);
                        sda.solve(DictionaryAttackSolver.this.stopFlag);
                        gotSolution |= sda.gotFullSolution;
                    }
                    if (gotSolution && !DictionaryAttackSolver.this.runForever) break;
                }
                for (SolverListener sl : DictionaryAttackSolver.this.listeners) {
                    sl.solverMessage("Switching to randomized search...");
                }
                int maxIters = 5000;
                while (!DictionaryAttackSolver.this.stopFlag.stop) {
                    sda = new SimpleDictionaryAttack(DictionaryAttackSolver.this.puzzle, new RandomPlanner(DictionaryAttackSolver.this.puzzle), DictionaryAttackSolver.this.listeners);
                    sda.maxIters = maxIters;
                    maxIters = (int)((double)maxIters * 1.05);
                    sda.pruneRootNode = false;
                    sda.pruneEachNode = false;
                    sda.scrambleWordOrder = true;
                    sda.solve(DictionaryAttackSolver.this.stopFlag);
                }
            }
            DictionaryAttackSolver.this.endTime = (double)System.currentTimeMillis() / 1000.0;
            double elapsedTime = DictionaryAttackSolver.this.endTime - DictionaryAttackSolver.this.startTime;
            for (SolverListener sl : DictionaryAttackSolver.this.listeners) {
                sl.solverFinished(elapsedTime);
            }
            DictionaryAttackSolver.this.stopFlag.stop = true;
        }
    }
}

