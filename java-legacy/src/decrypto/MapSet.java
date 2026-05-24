/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import java.util.Random;

public class MapSet {
    long[] set;
    int size;
    static final int[] onehotlog2 = new int[]{0, 0, 1, 0, 2, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    static final int[] LogTable256 = new int[]{0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7};

    public MapSet(int size) {
        this.size = size;
        this.set = new long[size];
    }

    public MapSet(int[] map) {
        this.size = map.length;
        this.set = new long[this.size];
        for (int i = 0; i < map.length; ++i) {
            this.set[i] = 1 << map[i];
        }
    }

    public int[] makeMap() {
        int[] m = new int[this.set.length];
        for (int i = 0; i < this.set.length; ++i) {
            m[i] = this.getMapping((byte)i);
        }
        return m;
    }

    public MapSet copy() {
        MapSet ms = new MapSet(this.size);
        ms.setTo(this);
        return ms;
    }

    public boolean hasMappings(int c) {
        return this.set[c] != 0L;
    }

    public void setTo(MapSet ms) {
        this.size = ms.size;
        System.arraycopy(ms.set, 0, this.set, 0, this.size);
    }

    public void setEmptySet() {
        for (int i = 0; i < this.size; ++i) {
            this.set[i] = 0L;
        }
    }

    public void setFullSet() {
        for (int i = 0; i < this.size; ++i) {
            this.set[i] = (1 << this.size + 1) - 1;
        }
    }

    public void forbidMapping(byte a, byte b) {
        int ai = a & 0xFF;
        int bi = b & 0xFF;
        int n = ai;
        this.set[n] = this.set[n] & (long)(~(1 << bi));
    }

    public void setMapping(byte a, byte b) {
        int ai = a & 0xFF;
        int bi = b & 0xFF;
        int i = 0;
        while (i < this.size) {
            int n = i++;
            this.set[n] = this.set[n] & (long)(~(1 << bi));
        }
        this.set[ai] = 1 << bi;
    }

    public boolean isUniquelyMapped(byte a) {
        int ai = a & 0xFF;
        long v = this.set[ai];
        return v != 0L && (v & v - 1L) == 0L;
    }

    public boolean isMappingOkay(byte a, byte b) {
        int ai = a & 0xFF;
        int bi = b & 0xFF;
        return (this.set[ai] & (long)(1 << bi)) != 0L;
    }

    public boolean isMappingOkay(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; ++i) {
            int ai = a[i] & 0xFF;
            int bi = b[i] & 0xFF;
            if ((this.set[ai] & (long)(1 << bi)) != 0L) continue;
            return false;
        }
        return true;
    }

    public void setMappings(byte[] a, byte[] b) {
        int i;
        long mask = 0L;
        for (i = 0; i < b.length; ++i) {
            int bi = b[i] & 0xFF;
            mask |= (long)(1 << bi);
        }
        mask ^= 0xFFFFFFFFFFFFFFFFL;
        i = 0;
        while (i < this.size) {
            int n = i++;
            this.set[n] = this.set[n] & mask;
        }
        for (i = 0; i < b.length; ++i) {
            int ai = a[i] & 0xFF;
            int bi = b[i] & 0xFF;
            this.set[ai] = 1 << bi;
        }
    }

    public void enableMappings(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; ++i) {
            int ai = a[i] & 0xFF;
            int bi = b[i] & 0xFF;
            int n = ai;
            this.set[n] = this.set[n] | (long)(1 << bi);
        }
    }

    public void intersectWith(MapSet m) {
        for (int i = 0; i < this.size; ++i) {
            if (m.set[i] == 0L) continue;
            long v = this.set[i];
            int n = i;
            this.set[n] = this.set[n] & m.set[i];
            if (this.set[i] == v || (this.set[i] & this.set[i] - 1L) != 0L) continue;
            v = this.set[i];
            int j = 0;
            while (j < this.size) {
                int n2 = j++;
                this.set[n2] = this.set[n2] & (v ^ 0xFFFFFFFFFFFFFFFFL);
            }
            this.set[i] = v;
        }
    }

    public void selfReduce() {
        long[] lls = new long[this.size];
        for (int i = 0; i < this.size; ++i) {
            long m;
            int bidx1;
            long b0 = this.set[i];
            long b1 = b0 & b0 - 1L;
            long b2 = b1 & b1 - 1L;
            if (b2 != 0L) continue;
            int bidx0 = MapSet.oneHotLog2(b1);
            if (bidx0 > (bidx1 = MapSet.oneHotLog2(b0 ^ b1))) {
                int tmp = bidx0;
                bidx0 = bidx1;
                bidx1 = tmp;
            }
            if ((lls[bidx0] & (m = (long)(1 << bidx1))) != 0L) {
                int j = i + 1;
                while (j < this.size) {
                    int n = j++;
                    this.set[n] = this.set[n] & (b0 ^ 0xFFFFFFFFFFFFFFFFL);
                }
            }
            int n = bidx0;
            lls[n] = lls[n] | m;
        }
    }

    public int getMapping(byte a) {
        if (a < 0) {
            return -1;
        }
        long v = this.set[a & 0xFF];
        if (v == 0L || (v & v - 1L) != 0L) {
            return -1;
        }
        return MapSet.oneHotLog2(v);
    }

    public static final int oneHotLog2(long _v) {
        int v = (int)_v;
        int tt = v >> 16;
        if (tt != 0) {
            int t = v >> 24;
            if (t != 0) {
                return 24 + LogTable256[t];
            }
            return 16 + LogTable256[tt & 0xFF];
        }
        int t = v >> 8;
        if (t != 0) {
            return 8 + LogTable256[t];
        }
        return LogTable256[v];
    }

    public void print() {
        for (int i = 0; i < this.size; ++i) {
            System.out.printf("%c: ", i + 65);
            for (int j = 0; j < this.size; ++j) {
                if ((this.set[i] & (long)(1 << j)) != 0L) {
                    System.out.printf("%c", j + 65);
                    continue;
                }
                System.out.printf(" ", new Object[0]);
            }
            System.out.printf("\n", new Object[0]);
        }
    }

    public void randomlyPermute(boolean allowIdentityMappings) {
        int i;
        boolean hasIdentityMapping;
        byte[] tmp = new byte[this.size];
        Random r = new Random();
        block0: do {
            int i2;
            for (i2 = 0; i2 < tmp.length; ++i2) {
                tmp[i2] = (byte)i2;
            }
            i2 = 0;
            while (i2 < tmp.length) {
                byte mk;
                int j = r.nextInt(tmp.length - i2) + i2;
                int k = i2++;
                byte mj = tmp[j];
                tmp[j] = mk = tmp[k];
                tmp[k] = mj;
            }
            hasIdentityMapping = false;
            for (i = 0; i < tmp.length; ++i) {
                if (tmp[i] != (byte)i) continue;
                hasIdentityMapping = true;
                continue block0;
            }
        } while (hasIdentityMapping && !allowIdentityMappings);
        for (i = 0; i < tmp.length; ++i) {
            this.set[i] = 1 << tmp[i];
        }
    }

    public static void main(String[] args) {
        int trials = 500000;
        int[] hits = new int[26];
        for (int i = 0; i < trials; ++i) {
            MapSet mapset = new MapSet(26);
            mapset.randomlyPermute(true);
            for (int j = 0; j < 26; ++j) {
                if (mapset.getMapping((byte)j) != j) continue;
                int n = j;
                hits[n] = hits[n] + 1;
            }
        }
        for (int j = 0; j < 26; ++j) {
            System.out.printf("%.3f  ", (double)hits[j] / (double)trials);
        }
        System.out.printf("\n", new Object[0]);
    }
}

