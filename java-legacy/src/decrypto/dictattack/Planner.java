/*
 * Decompiled with CFR 0.152.
 */
package decrypto.dictattack;

import decrypto.MapSet;

public interface Planner {
    public static final int WORD_FLAG = 0x100000;
    public static final int LETTER_FLAG = 0x200000;
    public static final int MASK = 65535;

    public int getAction(boolean[] var1, MapSet var2);
}

