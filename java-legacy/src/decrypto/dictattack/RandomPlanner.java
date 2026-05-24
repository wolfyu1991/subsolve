/*
 * Decompiled with CFR 0.152.
 */
package decrypto.dictattack;

import decrypto.MapSet;
import decrypto.Puzzle;
import decrypto.dictattack.Planner;
import java.util.Random;

public class RandomPlanner
implements Planner {
    Random r = new Random();
    Puzzle puzzle;

    public RandomPlanner(Puzzle puzzle) {
        this.puzzle = puzzle;
    }

    public int getAction(boolean[] solvedWords, MapSet map) {
        int widx;
        int trials = 0;
        do {
            widx = this.r.nextInt(solvedWords.length);
            if (++trials <= solvedWords.length * 10) continue;
            return -1;
        } while (solvedWords[widx] || !this.puzzle.words.get((int)widx).enabled);
        return 0x100000 | widx;
    }
}

