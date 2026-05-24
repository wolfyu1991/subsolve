/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.Dict;
import decrypto.MapSet;
import decrypto.Puzzle;
import decrypto.Solution;
import decrypto.SolutionSet;
import decrypto.Solver;
import decrypto.SolverListener;
import decrypto.dictattack.DictionaryAttackSolver;
import decrypto.genetic.GAProblem;
import decrypto.genetic.GeneticSolver;
import decrypto.util.GetOpt;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

public class DecryptoCGI {
    SolutionSet set;
    GetOpt opt = new GetOpt();
    Puzzle puzzle;
    Solver solver;
    Random r = new Random();
    Dict dict;
    double bestscore = -1.7976931348623157E308;

    String escape(String s) {
        s = s.replace("\"", "'");
        s = s.replace("\n", " ");
        s = s.replace("\r", "").trim();
        s = s.replace("  ", " &nbsp;");
        return s;
    }

    public static void main(String[] args) {
        DecryptoCGI app = new DecryptoCGI();
        app.run(args);
    }

    public DecryptoCGI() {
        this.opt.addBoolean('h', "help", false, "Show help");
        this.opt.addString('d', "dictionary", "english-standard.dat", "Dictionary to use");
        this.opt.addString('c', "clues", "", "Clues, e.g.: A=C, M=R, JXJ=DAD");
        this.opt.addDouble('\u0000', "timelimit", 10.0, "Maximum CPU time");
        this.opt.addInt('\u0000', "setsize", 100, "Maximum solutions to store");
        this.opt.addString('\u0000', "puzzlefile", "", "File containing the puzzle");
        this.opt.addBoolean('\u0000', "scramble", false, "Scramble the puzzle and exit");
        this.opt.addBoolean('\u0000', "trustspaces", true, "Trust spaces");
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void run(String[] args) {
        Object sb;
        String clues;
        String ciphertext;
        this.opt.parse(args);
        if (this.opt.getBoolean("help")) {
            this.opt.doHelp();
            return;
        }
        try {
            this.dict = new Dict(this.opt.getString("dictionary"));
        }
        catch (IOException ex) {
            System.out.println("Couldn't open dictionary: " + ex);
            return;
        }
        this.set = new SolutionSet(this.opt.getInt("setsize"));
        if (this.opt.wasSpecified("puzzlefile")) {
            StringBuffer ct = new StringBuffer();
            StringBuffer cs = new StringBuffer();
            try {
                String line;
                BufferedReader ins = new BufferedReader(new FileReader(this.opt.getString("puzzlefile")));
                while ((line = ins.readLine()) != null) {
                    if (line.contains("=")) {
                        cs.append(line + " ");
                        continue;
                    }
                    ct.append(line + " ");
                }
            }
            catch (IOException ex) {
                System.out.println("Couldn't read puzzle file: " + ex);
            }
            ciphertext = ct.toString();
            clues = cs.toString();
        } else {
            ArrayList<String> extraArgs = this.opt.getExtraArgs();
            sb = new StringBuffer();
            for (int i = 0; i < extraArgs.size(); ++i) {
                ((StringBuffer)sb).append(extraArgs.get(i) + " ");
            }
            ciphertext = ((StringBuffer)sb).toString();
            clues = this.opt.getString("clues");
        }
        if (this.opt.getBoolean("scramble")) {
            this.puzzle = new Puzzle(this.dict, ciphertext, clues, true, true);
            this.puzzle.scramble(false);
            System.out.println(this.puzzle.cipherText);
            System.out.println(this.puzzle.clues);
            return;
        }
        new FailsafeThread((int)this.opt.getDouble("timelimit") + 5).start();
        if (this.opt.getBoolean("trustspaces")) {
            this.puzzle = new Puzzle(this.dict, ciphertext, clues, true, true);
            this.solver = new DictionaryAttackSolver(this.puzzle);
        } else {
            this.puzzle = new Puzzle(this.dict, ciphertext, clues, false, true);
            this.solver = new GeneticSolver(new GAProblem(this.puzzle));
        }
        MyDecryptoListener mdl = new MyDecryptoListener();
        this.solver.addListener(mdl);
        this.solver.start(this.opt.getDouble("timelimit"));
        sb = mdl;
        synchronized (sb) {
            while (!mdl.finished) {
                try {
                    mdl.wait();
                }
                catch (InterruptedException ex) {}
            }
        }
        for (int i = 0; i < this.set.size(); ++i) {
            Solution sol = this.set.getElement(i);
            System.out.println(String.format("<script>solsum(%d,%.3f,\"%s\");</script>", i, sol.score, this.escape(sol.solution)));
        }
        double elapsedTime = 0.0;
        System.out.println("<script>elapse(" + elapsedTime + ")</script>");
    }

    public class MyDecryptoListener
    implements SolverListener {
        boolean finished = false;
        String lastprg = "";
        int nprogs = 0;

        void msg(String s) {
            System.out.println("<script>msg(\"" + s + "\");</script>");
        }

        public void solverMessage(String s) {
            this.msg(s);
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public void solverFinished(double elapsedTime) {
            String msg = "";
            msg = String.format("Finished (%.3f seconds)", elapsedTime);
            this.solverMessage(msg);
            MyDecryptoListener myDecryptoListener = this;
            synchronized (myDecryptoListener) {
                this.finished = true;
                this.notifyAll();
            }
        }

        public void solverSolution(MapSet mapset, int[] spaces, boolean fullSolution, double score) {
            if (DecryptoCGI.this.set.wouldAdd(score)) {
                String ss = DecryptoCGI.this.puzzle.lang.translateString(DecryptoCGI.this.puzzle.rawCipherText, mapset, spaces);
                Solution sol = new Solution(ss, mapset, score);
                boolean best = DecryptoCGI.this.set.size() > 0 ? DecryptoCGI.this.set.getElement((int)0).score < score : false;
                boolean added = DecryptoCGI.this.set.add(sol);
                if (added && score > DecryptoCGI.this.bestscore) {
                    DecryptoCGI.this.bestscore = score;
                    System.out.println(String.format("<script>sol(%.4f,\"%s\");</script>", sol.score, DecryptoCGI.this.escape(sol.solution)));
                }
            }
        }

        public void solverProgress(double progress) {
            String newprg = String.format("<script>prg(%.2f);</script>", progress);
            if (!newprg.equals(this.lastprg)) {
                ++this.nprogs;
                if (this.nprogs < 100 || (this.nprogs & this.nprogs - 1) == 0) {
                    System.out.println(newprg);
                }
                this.lastprg = newprg;
            }
        }
    }

    class FailsafeThread
    extends Thread {
        int seconds;

        public FailsafeThread(int seconds) {
            this.seconds = seconds;
            this.setDaemon(true);
        }

        public void run() {
            try {
                Thread.sleep(this.seconds * 1000);
                System.out.println("An error has occured. Logging it!");
                BufferedWriter outs = new BufferedWriter(new FileWriter("/var/tmp/decrypto_failsafe.log", true));
                outs.write("***********************\n");
                outs.write("DATE:         " + new Date() + "\n");
                outs.write("DICTIONARY:   " + DecryptoCGI.this.opt.getString("dictionary") + "\n");
                outs.write("TRUST SPACES: " + DecryptoCGI.this.opt.getBoolean("trustspaces") + "\n");
                outs.write("PUZZLE:       " + DecryptoCGI.this.puzzle.cipherText + "\n");
                outs.write("CLUES:        " + DecryptoCGI.this.puzzle.clues + "\n\n");
                if (DecryptoCGI.this.solver != null && DecryptoCGI.this.solver.getException() != null) {
                    Exception e = DecryptoCGI.this.solver.getException();
                    e.printStackTrace(new PrintWriter(outs));
                }
                outs.close();
                System.exit(-1);
            }
            catch (InterruptedException ex) {
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }
    }
}

