/*
 * Decompiled with CFR 0.152.
 */
package decrypto.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UTFDataFormatException;

public final class BufferedRandomAccessFile
extends RandomAccessFile {
    static final int LogBuffSz = 16;
    public static final int BuffSz = 65536;
    static final long BuffMask = -65536L;
    private boolean dirty;
    private boolean closed;
    private long curr;
    private long lo;
    private long hi;
    private byte[] buff;
    private long maxHi;
    private boolean hitEOF;
    private long diskPos;
    private static Object mu = new Object();
    private static byte[][] availBuffs = new byte[100][];
    private static int numAvailBuffs = 0;

    public BufferedRandomAccessFile(File file, String mode) throws IOException {
        super(file, mode);
        this.init();
    }

    private final void Assert(boolean cond) {
        if (!cond) {
            throw new InternalError("Assertion failed in BufferedRandomAccessFile");
        }
    }

    public BufferedRandomAccessFile(String name, String mode) throws IOException {
        super(name, mode);
        this.init();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void init() {
        this.closed = false;
        this.dirty = false;
        this.hi = 0L;
        this.curr = 0L;
        this.lo = 0L;
        Object object = mu;
        synchronized (object) {
            this.buff = numAvailBuffs > 0 ? availBuffs[--numAvailBuffs] : new byte[65536];
        }
        this.maxHi = 65536L;
        this.hitEOF = false;
        this.diskPos = 0L;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void close() throws IOException {
        this.Assert(!this.closed);
        this.flush();
        this.closed = true;
        Object object = mu;
        synchronized (object) {
            if (numAvailBuffs >= availBuffs.length) {
                byte[][] newBuffs = new byte[numAvailBuffs + 10][];
                System.arraycopy(availBuffs, 0, newBuffs, 0, numAvailBuffs);
                availBuffs = newBuffs;
            }
            BufferedRandomAccessFile.availBuffs[BufferedRandomAccessFile.numAvailBuffs++] = this.buff;
        }
        super.close();
    }

    public void flush() throws IOException {
        this.flushBuffer();
    }

    private void flushBuffer() throws IOException {
        if (this.dirty) {
            this.Assert(this.curr > this.lo);
            if (this.diskPos != this.lo) {
                super.seek(this.lo);
            }
            int len = (int)(this.curr - this.lo);
            super.write(this.buff, 0, len);
            this.diskPos = this.curr;
            this.dirty = false;
        }
    }

    private int fillBuffer() throws IOException {
        int n;
        int cnt = 0;
        for (int rem = this.buff.length; rem > 0 && (n = super.read(this.buff, cnt, rem)) >= 0; rem -= n) {
            cnt += n;
        }
        this.hitEOF = cnt < this.buff.length;
        this.diskPos += (long)cnt;
        return cnt;
    }

    public void seek(long pos) throws IOException {
        if (pos >= this.hi || pos < this.lo) {
            this.flushBuffer();
            this.lo = pos & 0xFFFFFFFFFFFF0000L;
            this.maxHi = this.lo + (long)this.buff.length;
            if (this.diskPos != this.lo) {
                super.seek(this.lo);
                this.diskPos = this.lo;
            }
            int n = this.fillBuffer();
            this.hi = this.lo + (long)n;
        } else if (pos < this.curr) {
            this.flushBuffer();
        }
        this.curr = pos;
    }

    public long getFilePointer() {
        return this.curr;
    }

    public long length() throws IOException {
        return Math.max(this.curr, super.length());
    }

    public int read() throws IOException {
        if (this.curr == this.hi) {
            if (this.hitEOF) {
                return -1;
            }
            this.seek(this.curr);
            if (this.curr == this.hi) {
                return -1;
            }
        }
        byte res = this.buff[(int)(this.curr - this.lo)];
        ++this.curr;
        return res & 0xFF;
    }

    public int read(byte[] b) throws IOException {
        return this.read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (this.curr == this.hi) {
            if (this.hitEOF) {
                return -1;
            }
            this.seek(this.curr);
            if (this.curr == this.hi) {
                return -1;
            }
        }
        this.Assert(this.curr < this.hi);
        len = Math.min(len, (int)(this.hi - this.curr));
        int buffOff = (int)(this.curr - this.lo);
        System.arraycopy(this.buff, buffOff, b, off, len);
        this.curr += (long)len;
        return len;
    }

    public void write(int b) throws IOException {
        if (this.curr == this.hi) {
            if (this.hitEOF && this.hi < this.maxHi) {
                ++this.hi;
            } else {
                this.seek(this.curr);
                if (this.curr == this.hi) {
                    this.Assert(this.hitEOF);
                    ++this.hi;
                }
            }
        }
        this.Assert(this.curr < this.hi);
        this.buff[(int)(this.curr - this.lo)] = (byte)b;
        ++this.curr;
        this.dirty = true;
    }

    public void write(byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            int n = this.writeAtMost(b, off, len);
            off += n;
            len -= n;
        }
        this.dirty = true;
    }

    private int writeAtMost(byte[] b, int off, int len) throws IOException {
        if (this.curr == this.hi) {
            if (this.hitEOF && this.hi < this.maxHi) {
                this.hi = this.maxHi;
            } else {
                this.seek(this.curr);
                if (this.curr == this.hi) {
                    this.Assert(this.hitEOF);
                    this.hi = this.maxHi;
                }
            }
        }
        this.Assert(this.curr < this.hi);
        len = Math.min(len, (int)(this.hi - this.curr));
        int buffOff = (int)(this.curr - this.lo);
        System.arraycopy(b, off, this.buff, buffOff, len);
        this.curr += (long)len;
        return len;
    }

    public void writeNum(long x) throws IOException {
        while (x < -64L || x > 63L) {
            this.write((int)(x & 0x7FL | 0x80L));
            x >>= 7;
        }
        this.write((int)(x & 0x7FL));
    }

    public long readNum() throws IOException {
        int s = 0;
        long n = 0L;
        long ch = this.read();
        while (ch >= 128L) {
            n |= (ch & 0x7FL) << s;
            s += 7;
            ch = this.read();
        }
        return n |= ch % 64L - ch / 64L * 64L << s;
    }

    public final void writeUTFx(String str) throws IOException {
        char c;
        int i;
        int strlen = str.length();
        int utflen = 0;
        for (i = 0; i < strlen; ++i) {
            c = str.charAt(i);
            if (c >= '\u0001' && c <= '\u007f') {
                ++utflen;
                continue;
            }
            if (c > '\u07ff') {
                utflen += 3;
                continue;
            }
            utflen += 2;
        }
        this.writeNum(utflen);
        for (i = 0; i < strlen; ++i) {
            c = str.charAt(i);
            if (c >= '\u0001' && c <= '\u007f') {
                this.write(c);
                continue;
            }
            if (c > '\u07ff') {
                this.write(0xE0 | c >> 12 & 0xF);
                this.write(0x80 | c >> 6 & 0x3F);
                this.write(0x80 | c >> 0 & 0x3F);
                continue;
            }
            this.write(0xC0 | c >> 6 & 0x1F);
            this.write(0x80 | c >> 0 & 0x3F);
        }
    }

    public final String readUTFx() throws IOException {
        int utflen = (int)this.readNum();
        char[] str = new char[utflen];
        int count = 0;
        int strlen = 0;
        block5: while (count < utflen) {
            int c = this.read();
            switch (c >> 4) {
                case 0: 
                case 1: 
                case 2: 
                case 3: 
                case 4: 
                case 5: 
                case 6: 
                case 7: {
                    ++count;
                    str[strlen++] = (char)c;
                    continue block5;
                }
                case 12: 
                case 13: {
                    count += 2;
                    int char2 = this.read();
                    str[strlen++] = (char)((c & 0x1F) << 6 | char2 & 0x3F);
                    continue block5;
                }
                case 14: {
                    count += 3;
                    int char2 = this.read();
                    int char3 = this.read();
                    str[strlen++] = (char)((c & 0xF) << 12 | (char2 & 0x3F) << 6 | (char3 & 0x3F) << 0);
                    continue block5;
                }
            }
            throw new UTFDataFormatException();
        }
        return new String(str, 0, strlen);
    }
}

