/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.Dict;
import decrypto.MapSet;
import decrypto.Puzzle;
import decrypto.Solver;
import decrypto.SolverListener;
import decrypto.genetic.GAProblem;
import decrypto.genetic.GeneticSolver;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class GABench {
    public static void main(String[] args) {
        try {
            GABench.mainEx(args);
        }
        catch (IOException ex) {
            System.out.println("ex: " + ex);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    static Solution solveWait(Puzzle puzzle, Solver solver, TestCase tc, double timelimit, double scoreThresh) {
        MyDecryptoListener mdl = new MyDecryptoListener();
        mdl.puzzle = puzzle;
        mdl.solver = solver;
        mdl.scorethresh = scoreThresh;
        mdl.tc = tc;
        solver.addListener(mdl);
        solver.start(timelimit);
        MyDecryptoListener myDecryptoListener = mdl;
        synchronized (myDecryptoListener) {
            while (!mdl.finished) {
                try {
                    mdl.wait();
                }
                catch (InterruptedException ex) {}
            }
        }
        if (mdl.bestSolution.score > -1.7976931348623157E308) {
            return mdl.bestSolution;
        }
        return null;
    }

    public static void mainEx(String[] args) throws IOException {
        String line;
        Dict dict;
        try {
            dict = new Dict("english-standard.dat");
        }
        catch (IOException ex) {
            System.out.println("Couldn't open dictionary: " + ex);
            return;
        }
        BufferedWriter outs = new BufferedWriter(new FileWriter("/tmp/gabenchdata.txt"));
        BufferedReader ins = new BufferedReader(new FileReader(new File(args[0])));
        boolean puzzleNumber = false;
        ArrayList<TestCase> testcases = new ArrayList<TestCase>();
        while ((line = ins.readLine()) != null) {
            if (line.startsWith("#") || !line.startsWith("**")) continue;
            TestCase tc = new TestCase();
            tc.ciphertext = ins.readLine();
            tc.plaintext = ins.readLine();
            tc.map = new int[26];
            for (int i = 0; i < Math.min(tc.ciphertext.length(), tc.plaintext.length()); ++i) {
                char cc = tc.ciphertext.charAt(i);
                char pc = tc.plaintext.charAt(i);
                if (!Character.isLetter(cc)) continue;
                cc = Character.toUpperCase(cc);
                pc = Character.toUpperCase(pc);
                tc.map[cc - 65] = pc - 65;
            }
            testcases.add(tc);
        }
        int maxtestcases = Math.min(100, testcases.size());
        for (int trial = 0; trial < 1000; ++trial) {
            for (int tcidx = 0; tcidx < maxtestcases; ++tcidx) {
                TestCase tc = (TestCase)testcases.get(tcidx);
                Puzzle puzzle = new Puzzle(dict, tc.ciphertext, "", false, true);
                GAProblem problem = new GAProblem(puzzle);
                GeneticSolver solver = new GeneticSolver(problem);
                int[] identitymap = new int[dict.getLanguage().letterCount() + 1];
                double scoreThresh = problem.computeScore(tc.map) / (double)puzzle.numCiphertextLetters;
                System.out.println("--------------------------------------");
                System.out.println("puzzle: " + tcidx + "   round: " + trial);
                System.out.println(tc.ciphertext);
                System.out.printf("%10f (%8s  ) %s\n", scoreThresh, "truth", tc.plaintext);
                long startTime = System.currentTimeMillis();
                Solution sol = GABench.solveWait(puzzle, solver, tc, 30.0, scoreThresh);
                long endTime = System.currentTimeMillis();
                double dt = (double)(endTime - startTime) / 1000.0;
                System.out.printf("%10f (%8.3f s) %s\n", sol.score, dt, sol.plaintext);
                ++tc.ntrials;
                if (sol.solved) {
                    ++tc.nsolves;
                }
                tc.time += sol.time;
                tc.time2 += sol.time * sol.time;
                tc.bestscore = Math.max(tc.bestscore, sol.score);
                double avgtime = tc.time / (double)tc.ntrials;
                double stdtime = Math.sqrt(tc.time2 / (double)tc.ntrials - avgtime * avgtime);
                outs.write(String.format("%5d %3d %15f %15f %15f %15f %15f\n", tcidx, sol.solved ? 1 : 0, sol.time, sol.score, avgtime, stdtime, (double)tc.nsolves / (double)tc.ntrials));
                outs.flush();
            }
            int ntrials = 0;
            int nsolves = 0;
            double totalTime = 0.0;
            for (int tcidx = 0; tcidx < maxtestcases; ++tcidx) {
                TestCase tc = (TestCase)testcases.get(tcidx);
                ntrials += tc.ntrials;
                nsolves += tc.nsolves;
                totalTime += tc.time;
            }
            outs.write(String.format("%5d %15f %15f\n", -1, (double)nsolves / (double)ntrials, totalTime / (double)ntrials));
            outs.flush();
        }
    }

    static class TestCase {
        String ciphertext;
        String plaintext;
        int[] map;
        int ntrials;
        int nsolves;
        double time;
        double time2;
        double bestscore = -1.7976931348623157E308;

        TestCase() {
        }
    }

    static class MyDecryptoListener
    implements SolverListener {
        Solution bestSolution = new Solution();
        boolean finished = false;
        double scorethresh;
        Solver solver;
        Puzzle puzzle;
        TestCase tc;

        MyDecryptoListener() {
        }

        public void solverMessage(String s) {
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public void solverFinished(double elapsedTime) {
            MyDecryptoListener myDecryptoListener = this;
            synchronized (myDecryptoListener) {
                this.finished = true;
                this.notifyAll();
            }
        }

        public void solverSolution(MapSet mapset, int[] spaces, boolean fullSolution, double score) {
            int correctMappings = 0;
            for (int i = 0; i < this.tc.map.length; ++i) {
                if (mapset.getMapping((byte)i) != this.tc.map[i]) continue;
                ++correctMappings;
            }
            if (score < this.bestSolution.score) {
                return;
            }
            this.bestSolution.score = score;
            this.bestSolution.plaintext = this.puzzle.lang.translateString(this.puzzle.cipherText, mapset, spaces);
            if (this.bestSolution.score >= this.scorethresh) {
                this.bestSolution.time = this.solver.getElapsedTime();
                this.bestSolution.solved = true;
                this.solver.stop();
            }
        }

        public void solverProgress(double progress) {
        }
    }

    static class Solution {
        double score = -1.7976931348623157E308;
        String plaintext;
        double time;
        boolean solved;

        Solution() {
        }
    }
}

