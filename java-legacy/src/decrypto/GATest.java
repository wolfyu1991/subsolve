/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.Dict;
import decrypto.MapSet;
import decrypto.Puzzle;
import decrypto.Solver;
import decrypto.SolverListener;
import decrypto.dictattack.DictionaryAttackSolver;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class GATest {
    public static void main(String[] args) {
        try {
            GATest.mainEx(args);
        }
        catch (IOException ex) {
            System.out.println("ex: " + ex);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    static Solution solveWait(Puzzle puzzle, Solver solver, double timelimit) {
        MyDecryptoListener mdl = new MyDecryptoListener();
        mdl.puzzle = puzzle;
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
        BufferedReader ins = new BufferedReader(new FileReader(new File(args[0])));
        int puzzleNumber = 0;
        while ((line = ins.readLine()) != null) {
            char c;
            int offset;
            if (line.startsWith("#") || line.length() < 10) continue;
            for (offset = 0; offset < line.length() && !Character.isLetter(c = line.charAt(offset)) && c != '\"'; ++offset) {
            }
            line = line.substring(offset);
            System.out.printf("**%d**\n%s\n", puzzleNumber, line);
            Puzzle puzzle = new Puzzle(dict, line, "", true, true);
            DictionaryAttackSolver solver = new DictionaryAttackSolver(puzzle);
            Solution sol = GATest.solveWait(puzzle, solver, 15.0);
            System.out.printf("%s\n\n", sol.plaintext);
            ++puzzleNumber;
        }
    }

    static class MyDecryptoListener
    implements SolverListener {
        Solution bestSolution = new Solution();
        boolean finished = false;
        Puzzle puzzle;

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
            if (score < this.bestSolution.score) {
                return;
            }
            this.bestSolution.score = score;
            this.bestSolution.plaintext = this.puzzle.lang.translateString(this.puzzle.cipherText, mapset, spaces);
        }

        public void solverProgress(double progress) {
        }
    }

    static class Solution {
        double score = -1.7976931348623157E308;
        String plaintext;

        Solution() {
        }
    }
}

