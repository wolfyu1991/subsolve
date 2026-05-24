/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.Language;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class Candidate {
    public double score;
    public byte[] cand;

    public Candidate(byte[] cand, double score) {
        this.cand = cand;
        this.score = score;
    }

    public static Candidate[] toCandidates(ArrayList<byte[]> cands, Language lang) {
        Candidate[] c = new Candidate[cands.size()];
        for (int i = 0; i < cands.size(); ++i) {
            c[i] = new Candidate(cands.get(i), lang.computeCandidateScore(cands.get(i)));
        }
        Arrays.sort(c, new CandidateScoreComparator());
        return c;
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    public static class CandidateScoreComparator
    implements Comparator<Candidate> {
        @Override
        public int compare(Candidate a, Candidate b) {
            return -Double.compare(a.score, b.score);
        }
    }
}

