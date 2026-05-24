/*
 * Decompiled with CFR 0.152.
 */
package decrypto.dictattack;

import decrypto.MapSet;
import decrypto.Puzzle;
import decrypto.Word;
import decrypto.dictattack.DecryptoParameters;
import decrypto.dictattack.Planner;

public class LazyPlanner
implements Planner {
    Puzzle puzzle;

    public LazyPlanner(Puzzle p) {
        this.puzzle = p;
    }

    public int getAction(boolean[] solvedWords, MapSet map) {
        int i;
        double bestscore = Double.MAX_VALUE;
        int bestword = -1;
        for (i = 0; i < solvedWords.length; ++i) {
            if (solvedWords[i]) continue;
            Word w = this.puzzle.words.get(i);
            if (!w.enabled) continue;
            double thisscore = w.candidates.length - w.firstcand;
            if (thisscore == 0.0) {
                return -2;
            }
            if (!(thisscore < bestscore)) continue;
            bestscore = thisscore;
            bestword = i;
        }
        if (DecryptoParameters.enableSingleLetters && bestscore > (double)this.puzzle.lang.letterCount()) {
            for (i = 0; i < this.puzzle.historder.length; ++i) {
                byte letter;
                if (map.isUniquelyMapped(this.puzzle.historder[i]) || this.puzzle.histogram[letter = this.puzzle.historder[i]] == 0) continue;
                return letter | 0x200000;
            }
        }
        return bestword | 0x100000;
    }
}

