/*
 * Decompiled with CFR 0.152.
 */
package decrypto.util;

import java.util.Arrays;

public class ByteArrayWrapper {
    int hashCode = 0;
    public byte[] array;

    public ByteArrayWrapper(byte[] array) {
        this.array = array;
    }

    public int hashCode() {
        if (this.hashCode != 0) {
            return this.hashCode;
        }
        this.hashCode = Arrays.hashCode(this.array);
        return this.hashCode;
    }

    public boolean equals(Object o) {
        if (o instanceof ByteArrayWrapper) {
            ByteArrayWrapper bw = (ByteArrayWrapper)o;
            return Arrays.equals(bw.array, this.array);
        }
        return false;
    }
}

