/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.Solution;
import decrypto.TreeList;
import java.util.Comparator;

public class SolutionSet {
    TreeList<Solution> set = new TreeList<Solution>(new SolutionComparator());
    int capacity;
    double worst = -1.7976931348623157E308;
    double best = -1.7976931348623157E308;

    public SolutionSet(int capacity) {
        this.capacity = capacity;
    }

    public void reset() {
        this.set.reset();
        this.worst = -1.7976931348623157E308;
        this.best = -1.7976931348623157E308;
    }

    public int size() {
        return this.set.size();
    }

    public double getWorst() {
        return this.worst;
    }

    public double getBest() {
        return this.best;
    }

    public Solution getElement(int idx) {
        return this.set.elementAt(idx);
    }

    public boolean wouldAdd(double score) {
        return !(score < this.worst) || this.set.size() < this.capacity;
    }

    public boolean add(Solution sol) {
        if (sol.score < this.worst && this.set.size() >= this.capacity) {
            return false;
        }
        if (!this.set.add(sol)) {
            return false;
        }
        if (this.set.size() > this.capacity) {
            this.set.remove(this.set.size() - 1);
        }
        this.worst = this.set.elementAt((int)(this.set.size() - 1)).score;
        this.best = Math.max(this.best, sol.score);
        return true;
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    public static class SolutionComparator
    implements Comparator<Solution> {
        @Override
        public int compare(Solution a, Solution b) {
            if (a.score != b.score) {
                return Double.compare(b.score, a.score);
            }
            return b.solution.compareTo(a.solution);
        }
    }
}

