/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.MapSet;

public class Solution {
    public String solution;
    public double score;

    public Solution(String solution, MapSet map, double score) {
        this.solution = solution;
        this.score = score;
    }

    public Solution(String solution, double score) {
        this.solution = solution;
        this.score = score;
    }

    public double getScore() {
        return this.score;
    }

    public int compareTo(Solution s) {
        return -Double.compare(this.score, s.score);
    }

    public String toString() {
        return String.format("%.3f", this.score);
    }
}

