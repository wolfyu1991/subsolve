/*
 * Decompiled with CFR 0.152.
 */
package decrypto.statistics;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class BuildStatistics3 {
    static final int SZ = 28;
    static final int SUM = 27;
    static final int SPACE = 26;
    double[][][] counts = new double[28][28][28];
    int[] history = new int[3];
    int totalChars;

    public static void main(String[] args) {
        try {
            BuildStatistics3.main_ex(args);
        }
        catch (IOException ex) {
            System.out.println("ex: " + ex);
        }
    }

    public static void main_ex(String[] args) throws IOException {
        BuildStatistics3 bs1 = new BuildStatistics3(args, true, "english3");
        BuildStatistics3 bs2 = new BuildStatistics3(args, false, "english3ns");
    }

    public BuildStatistics3(String[] args, boolean spaces, String classname) throws IOException {
        int i;
        for (i = 0; i < this.history.length; ++i) {
            this.history[i] = 26;
        }
        for (i = 0; i < args.length; ++i) {
            int c;
            FileReader fr = new FileReader(args[i]);
            BufferedReader ins = new BufferedReader(fr);
            while ((c = ins.read()) > 0) {
                if (Character.isLetter(c)) {
                    this.handleCharacter(Character.toUpperCase(c) - 65);
                    continue;
                }
                if (!spaces) continue;
                this.handleCharacter(26);
            }
        }
        System.out.println("Total characters: " + this.totalChars);
        this.normalize();
        String outpath = "/tmp/" + classname + ".java";
        System.out.println("Writing to: " + outpath);
        this.emitBinary("/tmp/" + classname + ".sta");
    }

    public void handleCharacter(int v) {
        if (v == 26 && this.history[this.history.length - 1] == 26) {
            return;
        }
        if (v >= 28) {
            return;
        }
        for (int i = 1; i < this.history.length; ++i) {
            this.history[i - 1] = this.history[i];
        }
        this.history[this.history.length - 1] = v;
        double[] dArray = this.counts[this.history[0]][this.history[1]];
        int n = this.history[2];
        dArray[n] = dArray[n] + 1.0;
        double[] dArray2 = this.counts[this.history[0]][this.history[1]];
        dArray2[27] = dArray2[27] + 1.0;
        ++this.totalChars;
        if (this.history[1] == 26) {
            double[] dArray3 = this.counts[this.history[0]][26];
            dArray3[26] = dArray3[26] + 1.0;
            double[] dArray4 = this.counts[this.history[0]][26];
            dArray4[27] = dArray4[27] + 1.0;
            double[] dArray5 = this.counts[26][26];
            int n2 = this.history[2];
            dArray5[n2] = dArray5[n2] + 1.0;
            double[] dArray6 = this.counts[26][26];
            dArray6[27] = dArray6[27] + 1.0;
        }
    }

    public void normalize() {
        for (int i0 = 0; i0 < this.counts.length; ++i0) {
            for (int i1 = 0; i1 < this.counts[i0].length; ++i1) {
                for (int i2 = 0; i2 < this.counts[i0][i1].length; ++i2) {
                    double v = this.counts[i0][i1][i2];
                    double frac = (this.counts[i0][i1][i2] + 1.0) / (this.counts[i0][i1][27] + 28.0);
                    this.counts[i0][i1][i2] = Math.log(frac);
                }
            }
        }
    }

    public static float[][][] readBinary(String path) throws IOException {
        long starttime = System.currentTimeMillis();
        System.out.print("Opening " + path + " ... ");
        System.out.flush();
        DataInputStream ins = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(path)))));
        int dim = ins.readInt();
        int sz = ins.readInt();
        assert (dim == 3);
        float[][][] data = new float[sz][sz][sz];
        for (int i = 0; i < 28; ++i) {
            for (int j = 0; j < 28; ++j) {
                for (int k = 0; k < 28; ++k) {
                    data[i][j][k] = ins.readFloat();
                }
            }
        }
        System.out.printf("done (%.3f s)\n", (double)(System.currentTimeMillis() - starttime) / 1000.0);
        return data;
    }

    public void emitBinary(String path) throws IOException {
        DataOutputStream outs = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(path)));
        outs.writeInt(3);
        outs.writeInt(28);
        for (int i = 0; i < 28; ++i) {
            for (int j = 0; j < 28; ++j) {
                for (int k = 0; k < 28; ++k) {
                    outs.writeFloat((float)this.counts[i][j][k]);
                }
            }
        }
        outs.close();
    }

    public void emit(String classname, String path) throws IOException {
        int i;
        PrintStream outs = new PrintStream(new BufferedOutputStream(new FileOutputStream(path)));
        outs.printf("package decrypto.statistics;\n\n", new Object[0]);
        outs.printf("public class " + classname + "\n{\n", new Object[0]);
        String tab = "\t";
        for (i = 0; i < 28; ++i) {
            outs.printf("static final float[][] trigrams%d() { \n%sreturn new float[][] \n", i, tab);
            outs.printf("%s{\n", tab);
            for (int j = 0; j < 28; ++j) {
                outs.printf("%s%s// %c%c\n", tab, tab, i + 65, j + 65);
                outs.printf("%s%s{  ", tab, tab);
                for (int k = 0; k < 28; ++k) {
                    float v;
                    float lv = v = (float)this.counts[i][j][k];
                    outs.printf("%.4ff", Float.valueOf(lv));
                    if (k < 27) {
                        outs.printf(", ", new Object[0]);
                    } else {
                        outs.printf(" ", new Object[0]);
                    }
                    if ((k & 7) != 7) continue;
                    outs.printf("\n%s%s%s", tab, tab, tab);
                }
                outs.printf("}", new Object[0]);
                if (j < 27) {
                    outs.printf(",\n", new Object[0]);
                    continue;
                }
                outs.printf("\n", new Object[0]);
            }
            outs.printf("%s};\n}\n", tab);
        }
        outs.printf("public static final float trigrams[][][] = new float[][][] {\n", new Object[0]);
        for (i = 0; i < 28; ++i) {
            outs.printf("trigrams%d()", i);
            if (i < 27) {
                outs.printf(", ", new Object[0]);
                continue;
            }
            outs.printf(" ", new Object[0]);
        }
        outs.printf("};\n", new Object[0]);
        outs.printf("}\n", new Object[0]);
        outs.flush();
    }
}

