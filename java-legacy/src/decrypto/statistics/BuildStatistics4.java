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

public class BuildStatistics4 {
    static final int SZ = 28;
    static final int SPACE = 26;
    static final int SUM = 27;
    static final int EXPECT = 27;
    double[][][][] counts = new double[28][28][28][28];
    int[] history = new int[4];
    double[][][] counts3 = new double[28][28][28];
    double[][] counts2 = new double[28][28];
    double[] counts1 = new double[28];
    int totalChars;

    public static void main(String[] args) {
        try {
            BuildStatistics4.main_ex(args);
        }
        catch (IOException ex) {
            System.out.println("ex: " + ex);
        }
    }

    public static void main_ex(String[] args) throws IOException {
        BuildStatistics4 bs1 = new BuildStatistics4(args, true, "english4");
        BuildStatistics4 bs2 = new BuildStatistics4(args, false, "english4ns");
    }

    public BuildStatistics4(String[] args, boolean spaces, String classname) throws IOException {
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
        double[] dArray = this.counts[this.history[0]][this.history[1]][this.history[2]];
        int n = this.history[3];
        dArray[n] = dArray[n] + 1.0;
        double[] dArray2 = this.counts[this.history[0]][this.history[1]][this.history[2]];
        dArray2[27] = dArray2[27] + 1.0;
        double[] dArray3 = this.counts3[this.history[1]][this.history[2]];
        int n2 = this.history[3];
        dArray3[n2] = dArray3[n2] + 1.0;
        double[] dArray4 = this.counts3[this.history[1]][this.history[2]];
        dArray4[27] = dArray4[27] + 1.0;
        double[] dArray5 = this.counts2[this.history[2]];
        int n3 = this.history[3];
        dArray5[n3] = dArray5[n3] + 1.0;
        double[] dArray6 = this.counts2[this.history[2]];
        dArray6[27] = dArray6[27] + 1.0;
        int n4 = this.history[3];
        this.counts1[n4] = this.counts1[n4] + 1.0;
        this.counts1[27] = this.counts1[27] + 1.0;
        ++this.totalChars;
    }

    public double expectedInformation(double[] cs) {
        double expectation = 0.0;
        for (int i = 0; i <= 26; ++i) {
            double prob = (cs[i] + 1.0) / (cs[27] + 27.0);
            double information = Math.log(1.0 / prob);
            expectation += prob * information;
        }
        return expectation;
    }

    public void normalize() {
        int i2;
        int i1;
        int i0;
        for (i0 = 0; i0 < this.counts.length; ++i0) {
            for (i1 = 0; i1 < this.counts[i0].length; ++i1) {
                for (i2 = 0; i2 < this.counts[i0][i1].length; ++i2) {
                    double[] v = this.counts[i0][i1][i2];
                    double samples = this.counts[i0][i1][i2][27];
                    double thresh = 5.0;
                    double offset = 0.0;
                    if (samples < thresh) {
                        v = this.counts3[i1][i2];
                        samples = this.counts3[i1][i2][27];
                        offset = 10.0 * this.counts3[i1][i2][27];
                    }
                    if (samples < thresh) {
                        v = this.counts2[i2];
                        samples = this.counts2[i2][27];
                        offset = 100.0 * this.counts2[i2][27];
                    }
                    double sum = 0.0;
                    for (int i = 0; i <= 26; ++i) {
                        this.counts[i0][i1][i2][i] = v[i] + offset;
                        sum += this.counts[i0][i1][i2][i];
                    }
                    this.counts[i0][i1][i2][27] = sum;
                }
            }
        }
        for (i0 = 0; i0 < this.counts.length; ++i0) {
            for (i1 = 0; i1 < this.counts[i0].length; ++i1) {
                for (i2 = 0; i2 < this.counts[i0][i1].length; ++i2) {
                    for (int i3 = 0; i3 < this.counts[i0][i1][i2].length; ++i3) {
                        double logprob;
                        double v = this.counts[i0][i1][i2][i3];
                        double frac = (this.counts[i0][i1][i2][i3] + 1.0) / (this.counts[i0][i1][i2][27] + 28.0);
                        this.counts[i0][i1][i2][i3] = logprob = Math.log(frac);
                    }
                }
            }
        }
        for (i0 = 0; i0 < this.counts.length; ++i0) {
            for (i1 = 0; i1 < this.counts[i0].length; ++i1) {
                for (i2 = 0; i2 < this.counts[i0][i1].length; ++i2) {
                    for (int i3 = 0; i3 < this.counts[i0][i1][i2].length; ++i3) {
                        if (i0 != 27 && i1 != 27 && i2 != 27 && i3 != 27) continue;
                        int j0a = i0;
                        int j0b = i0;
                        if (i0 == 27) {
                            j0a = 0;
                            j0b = 26;
                        }
                        int j1a = i1;
                        int j1b = i1;
                        if (i1 == 27) {
                            j1a = 0;
                            j1b = 26;
                        }
                        int j2a = i2;
                        int j2b = i2;
                        if (i2 == 27) {
                            j2a = 0;
                            j2b = 26;
                        }
                        int j3a = i3;
                        int j3b = i3;
                        if (i3 == 27) {
                            j3a = 0;
                            j3b = 26;
                        }
                        double probsum = 0.0;
                        double expectationsum = 0.0;
                        for (int j0 = j0a; j0 <= j0b; ++j0) {
                            for (int j1 = j1a; j1 <= j1b; ++j1) {
                                for (int j2 = j2a; j2 <= j2b; ++j2) {
                                    for (int j3 = j3a; j3 <= j3b; ++j3) {
                                        double prob = Math.exp(this.counts[j0][j1][j2][j3]);
                                        probsum += prob;
                                        expectationsum += this.counts[j0][j1][j2][j3] * prob;
                                    }
                                }
                            }
                        }
                        this.counts[i0][i1][i2][i3] = expectationsum / probsum;
                    }
                }
            }
        }
    }

    public static float[][][][] readBinary(String path) throws IOException {
        long starttime = System.currentTimeMillis();
        System.out.flush();
        DataInputStream ins = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(path)))));
        int dim = ins.readInt();
        int sz = ins.readInt();
        assert (dim == 4);
        float[][][][] data = new float[sz][sz][sz][sz];
        for (int i = 0; i < 28; ++i) {
            for (int j = 0; j < 28; ++j) {
                for (int k = 0; k < 28; ++k) {
                    for (int w = 0; w < 28; ++w) {
                        data[i][j][k][w] = ins.readFloat();
                    }
                }
            }
        }
        return data;
    }

    public void emitBinary(String path) throws IOException {
        DataOutputStream outs = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(path)));
        outs.writeInt(4);
        outs.writeInt(28);
        for (int i = 0; i < 28; ++i) {
            for (int j = 0; j < 28; ++j) {
                for (int k = 0; k < 28; ++k) {
                    for (int w = 0; w < 28; ++w) {
                        outs.writeFloat((float)this.counts[i][j][k][w]);
                    }
                }
            }
        }
        outs.close();
    }

    public void emit(String classname, String path) throws IOException {
        int i1;
        int i0;
        PrintStream outs = new PrintStream(new BufferedOutputStream(new FileOutputStream(path)));
        outs.printf("package decrypto.statistics;\n\n", new Object[0]);
        outs.printf("public class " + classname + "\n{\n", new Object[0]);
        String tab = "\t";
        for (i0 = 0; i0 < 28; ++i0) {
            for (i1 = 0; i1 < 28; ++i1) {
                outs.printf("static final float[][] tetragrams%d_%d() { \nreturn new float[][] \n", i0, i1);
                outs.printf("%s{\n", tab);
                for (int j = 0; j < 28; ++j) {
                    outs.printf("%s%s// %c%c%c\n", tab, tab, i0 + 65, i1 + 65, j + 65);
                    outs.printf("%s%s{  ", tab, tab);
                    for (int k = 0; k < 28; ++k) {
                        float v;
                        float lv = v = (float)this.counts[i0][i1][j][k];
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
        }
        outs.printf("public static final float tetragrams[][][][] = new float[][][][] {\n", new Object[0]);
        for (i0 = 0; i0 < 28; ++i0) {
            outs.printf("{", new Object[0]);
            for (i1 = 0; i1 < 28; ++i1) {
                outs.printf("tetragrams%d_%d()", i0, i1);
                if (i1 < 27) {
                    outs.printf(", ", new Object[0]);
                    continue;
                }
                outs.printf(" ", new Object[0]);
            }
            outs.printf("}", new Object[0]);
            if (i0 < 27) {
                outs.printf(", \n", new Object[0]);
                continue;
            }
            outs.printf(" \n", new Object[0]);
        }
        outs.printf("};\n", new Object[0]);
        outs.printf("}\n", new Object[0]);
        outs.flush();
    }
}

