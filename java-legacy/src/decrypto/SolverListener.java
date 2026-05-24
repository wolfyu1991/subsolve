/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.MapSet;

public interface SolverListener {
    public void solverMessage(String var1);

    public void solverProgress(double var1);

    public void solverFinished(double var1);

    public void solverSolution(MapSet var1, int[] var2, boolean var3, double var4);
}

