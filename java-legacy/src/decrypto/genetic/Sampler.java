/*
 * Decompiled with CFR 0.152.
 */
package decrypto.genetic;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class Sampler<T> {
    double[] proportions;
    T[] objs;
    double totalProportion;

    public Sampler(double[] proportions, T[] objs) {
        assert (proportions.length == objs.length);
        this.proportions = proportions;
        this.objs = objs;
        for (int i = 0; i < proportions.length; ++i) {
            this.totalProportion += proportions[i];
        }
    }

    public T sample(double v) {
        v *= this.totalProportion;
        for (int i = 0; i < this.proportions.length; ++i) {
            if (!((v -= this.proportions[i]) < 0.0)) continue;
            return this.objs[i];
        }
        assert (false);
        return this.objs[0];
    }
}

