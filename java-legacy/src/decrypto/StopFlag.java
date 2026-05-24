/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

public class StopFlag {
    public boolean stop;
    public double startTime = (double)System.currentTimeMillis() / 1000.0;
    public double maxTime;

    public StopFlag(double maxTime) {
        this.maxTime = maxTime;
        new WaitThread().start();
    }

    class WaitThread
    extends Thread {
        public WaitThread() {
            this.setDaemon(true);
        }

        public void run() {
            double currentTime;
            double elapsedTime;
            while (!StopFlag.this.stop && !((elapsedTime = (currentTime = (double)System.currentTimeMillis() / 1000.0) - StopFlag.this.startTime) >= StopFlag.this.maxTime)) {
                try {
                    Thread.sleep((int)(1000.0 * Math.min(StopFlag.this.maxTime - elapsedTime, 1.0)));
                }
                catch (InterruptedException ex) {}
            }
            StopFlag.this.stop = true;
        }
    }
}

