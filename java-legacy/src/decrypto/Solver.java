/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import decrypto.SolverListener;

public interface Solver {
    public void addListener(SolverListener var1);

    public void start(double var1);

    public void stop();

    public double getElapsedTime();

    public Exception getException();
}

