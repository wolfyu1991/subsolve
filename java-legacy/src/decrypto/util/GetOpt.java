/*
 * Decompiled with CFR 0.152.
 */
package decrypto.util;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class GetOpt {
    public int helpMessageWidth = 50;
    HashMap<String, GetOptOpt> optsLong = new HashMap();
    HashMap<String, GetOptOpt> optsShort = new HashMap();
    ArrayList<GetOptOpt> optsHelp = new ArrayList();
    ArrayList<String> extraArgs = new ArrayList();
    String reason = null;

    public String getReason() {
        return this.reason;
    }

    public void addString(char sname, String lname, String defaultValue, String helpMessage) {
        assert (sname != '\u0000' | !lname.equals(""));
        StringOpt opt = new StringOpt(sname, lname, defaultValue, helpMessage);
        if (!lname.equals("")) {
            assert (this.optsLong.get(lname) == null);
            this.optsLong.put(lname, opt);
        }
        if (sname != '\u0000') {
            assert (this.optsShort.get("" + sname) == null);
            this.optsShort.put("" + sname, opt);
        }
        this.optsHelp.add(opt);
    }

    public String getString(String lname) {
        StringOpt opt = (StringOpt)this.optsLong.get(lname);
        return opt.value;
    }

    public void addInt(char sname, String lname, int defaultValue, String helpMessage) {
        assert (sname != '\u0000' | !lname.equals(""));
        IntOpt opt = new IntOpt(sname, lname, defaultValue, helpMessage);
        if (!lname.equals("")) {
            assert (this.optsLong.get(lname) == null);
            this.optsLong.put(lname, opt);
        }
        if (sname != '\u0000') {
            assert (this.optsShort.get("" + sname) == null);
            this.optsShort.put("" + sname, opt);
        }
        this.optsHelp.add(opt);
    }

    public void addDouble(char sname, String lname, double defaultValue, String helpMessage) {
        assert (sname != '\u0000' | !lname.equals(""));
        DoubleOpt opt = new DoubleOpt(sname, lname, defaultValue, helpMessage);
        if (!lname.equals("")) {
            assert (this.optsLong.get(lname) == null);
            this.optsLong.put(lname, opt);
        }
        if (sname != '\u0000') {
            assert (this.optsShort.get("" + sname) == null);
            this.optsShort.put("" + sname, opt);
        }
        this.optsHelp.add(opt);
    }

    public int getInt(String lname) {
        IntOpt opt = (IntOpt)this.optsLong.get(lname);
        return opt.value;
    }

    public double getDouble(String lname) {
        DoubleOpt opt = (DoubleOpt)this.optsLong.get(lname);
        return opt.value;
    }

    public boolean wasSpecified(String lname) {
        GetOptOpt opt = this.optsLong.get(lname);
        return opt.specified;
    }

    public void addBoolean(char sname, String lname, boolean defaultValue, String helpMessage) {
        assert (sname != '\u0000' | !lname.equals(""));
        BooleanOpt opt = new BooleanOpt(sname, lname, defaultValue, helpMessage);
        if (!lname.equals("")) {
            assert (this.optsLong.get(lname) == null);
            this.optsLong.put(lname, opt);
        }
        if (sname != '\u0000') {
            assert (this.optsShort.get("" + sname) == null);
            this.optsShort.put("" + sname, opt);
        }
        this.optsHelp.add(opt);
    }

    public boolean getBoolean(String lname) {
        BooleanOpt opt = (BooleanOpt)this.optsLong.get(lname);
        return opt.value;
    }

    public boolean parse(String[] args) {
        if (args == null) {
            return true;
        }
        ArgFeeder feeder = new ArgFeeder(args);
        while (feeder.hasNext()) {
            String s = feeder.next();
            if (s.startsWith("--")) {
                GetOptOpt goo;
                String optString = s.substring(2);
                String equalString = null;
                int equalIdx = optString.indexOf("=");
                if (equalIdx >= 0) {
                    equalString = optString.substring(equalIdx + 1);
                    optString = optString.substring(0, equalIdx);
                }
                if ((goo = this.optsLong.get(optString)) == null) {
                    this.reason = "Unknown option '" + optString + "'";
                    return false;
                }
                this.reason = goo.eval(equalString, feeder);
                goo.specified = true;
                if (this.reason == null) continue;
                return false;
            }
            if (s.startsWith("-")) {
                for (int j = 1; j < s.length(); ++j) {
                    GetOptOpt goo = this.optsShort.get("" + s.charAt(j));
                    if (goo == null) {
                        this.reason = "Unknown option '" + s.charAt(j) + "'";
                        return false;
                    }
                    this.reason = goo.eval(null, feeder);
                    goo.specified = true;
                    if (this.reason == null) continue;
                    return false;
                }
                continue;
            }
            this.extraArgs.add(s);
        }
        return true;
    }

    public ArrayList<String> getExtraArgs() {
        return this.extraArgs;
    }

    public void dump() {
        try {
            this.dumpInternal(new OutputStreamWriter(System.out));
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    public void dumpInternal(Writer outs) throws IOException {
        int longNameFieldSize = 0;
        int defaultValueFieldSize = 0;
        for (GetOptOpt goo : this.optsHelp) {
            if (goo.lname.length() > longNameFieldSize) {
                longNameFieldSize = goo.lname.length();
            }
            if (goo.stringDefaultValue().length() <= defaultValueFieldSize) continue;
            defaultValueFieldSize = goo.stringDefaultValue().length();
        }
        longNameFieldSize += 2;
        for (GetOptOpt goo : this.optsHelp) {
            outs.write("--" + goo.lname);
            this.fill(outs, longNameFieldSize - goo.lname.length());
            outs.write("= " + goo.stringValue());
            this.fill(outs, defaultValueFieldSize - goo.stringDefaultValue().length());
            outs.write("\n");
        }
        outs.write("\nExtra Arguments:\n");
        for (String s : this.extraArgs) {
            outs.write(s);
            outs.write("  ");
        }
        outs.write("\n");
        outs.flush();
    }

    public void doHelp() {
        try {
            this.doHelp(new OutputStreamWriter(System.out));
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    public void doHelp(Writer outs) throws IOException {
        int longNameFieldSize = 0;
        int defaultValueFieldSize = 0;
        for (GetOptOpt goo : this.optsHelp) {
            if (goo.lname.length() > longNameFieldSize) {
                longNameFieldSize = goo.lname.length();
            }
            if (goo.stringDefaultValue().length() <= defaultValueFieldSize) continue;
            defaultValueFieldSize = goo.stringDefaultValue().length();
        }
        longNameFieldSize += 2;
        longNameFieldSize = Math.max(longNameFieldSize, 8);
        defaultValueFieldSize = Math.max(defaultValueFieldSize, 8);
        outs.write("option flags");
        this.fill(outs, longNameFieldSize - 6);
        outs.write("default");
        this.fill(outs, defaultValueFieldSize - 3);
        outs.write("description\n");
        outs.write("------------");
        this.fill(outs, longNameFieldSize - 6);
        outs.write("-------");
        this.fill(outs, defaultValueFieldSize - 3);
        outs.write("-----------\n");
        for (GetOptOpt goo : this.optsHelp) {
            if (goo.sname != '\u0000') {
                outs.write("-" + goo.sname);
            } else {
                outs.write("  ");
            }
            outs.write("  ");
            outs.write("--" + goo.lname);
            this.fill(outs, longNameFieldSize - goo.lname.length());
            outs.write("[" + goo.stringDefaultValue() + "]");
            this.fill(outs, defaultValueFieldSize - goo.stringDefaultValue().length());
            outs.write("  ");
            ArrayList<String> lines = this.wrapText(goo.getHelpMessage(), this.helpMessageWidth);
            for (int i = 0; i < lines.size(); ++i) {
                if (i == 0) {
                    outs.write(lines.get(i) + "\n");
                    continue;
                }
                this.fill(outs, longNameFieldSize + defaultValueFieldSize + 2 + 2 + 2 + 4 + 2);
                outs.write(lines.get(i) + "\n");
            }
        }
        outs.flush();
    }

    protected ArrayList<String> wrapText(String in, int width) {
        ArrayList<String> outs = new ArrayList<String>();
        String[] ins = in.split(" ");
        String currentLine = "";
        for (String word : ins) {
            if (currentLine.length() + 1 + word.length() < width) {
                if (currentLine.length() > 0) {
                    currentLine = currentLine + " ";
                }
                currentLine = currentLine + word;
                continue;
            }
            outs.add(currentLine);
            currentLine = word;
        }
        if (currentLine.length() > 0) {
            outs.add(currentLine);
        }
        return outs;
    }

    protected void fill(Writer outs, int len) throws IOException {
        for (int i = 0; i < len; ++i) {
            outs.write(" ");
        }
    }

    public static void main(String[] args) {
        GetOpt opts = new GetOpt();
        opts.addString('f', "file", "stdout", "Specify the input file");
        opts.addBoolean('v', "verbose", false, "Enable verbose mode");
        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addInt('p', "port", 25, "Set default port");
        if (!opts.parse(args)) {
            System.out.println("option error: " + opts.getReason());
        }
        if (opts.getBoolean("help")) {
            opts.doHelp();
        }
        opts.dump();
    }

    class BooleanOpt
    extends GetOptOpt {
        boolean value;
        boolean defaultValue;

        BooleanOpt(char sname, String lname, boolean defaultValue, String helpMessage) {
            this.sname = sname;
            this.lname = lname;
            this.value = defaultValue;
            this.defaultValue = defaultValue;
            this.helpMessage = helpMessage;
        }

        String eval(String equalString, ArgFeeder feeder) {
            String s = equalString;
            if (s == null) {
                this.value = true;
                return null;
            }
            this.value = s.equalsIgnoreCase("TRUE");
            if (!this.value && !s.equalsIgnoreCase("FALSE")) {
                return "Invalid boolean format '" + s + "'";
            }
            return null;
        }

        String stringDefaultValue() {
            return "" + this.defaultValue;
        }

        String stringValue() {
            return "" + this.value;
        }

        boolean getValue() {
            return this.value;
        }
    }

    class DoubleOpt
    extends GetOptOpt {
        double value;
        double defaultValue;

        DoubleOpt(char sname, String lname, double defaultValue, String helpMessage) {
            this.sname = sname;
            this.lname = lname;
            this.value = defaultValue;
            this.defaultValue = defaultValue;
            this.helpMessage = helpMessage;
        }

        String eval(String equalString, ArgFeeder feeder) {
            String s = equalString;
            if (s == null) {
                s = feeder.next();
            }
            if (s == null) {
                return "Option " + this.lname + " requires an integer argument.";
            }
            try {
                this.value = Double.parseDouble(s);
            }
            catch (NumberFormatException ex) {
                return "Invalid integer format '" + s + "'";
            }
            return null;
        }

        String stringDefaultValue() {
            return "" + this.defaultValue;
        }

        String stringValue() {
            return "" + this.value;
        }

        double getValue() {
            return this.value;
        }
    }

    class IntOpt
    extends GetOptOpt {
        int value;
        int defaultValue;

        IntOpt(char sname, String lname, int defaultValue, String helpMessage) {
            this.sname = sname;
            this.lname = lname;
            this.value = defaultValue;
            this.defaultValue = defaultValue;
            this.helpMessage = helpMessage;
        }

        String eval(String equalString, ArgFeeder feeder) {
            String s = equalString;
            if (s == null) {
                s = feeder.next();
            }
            if (s == null) {
                return "Option " + this.lname + " requires an integer argument.";
            }
            try {
                this.value = Integer.parseInt(s);
            }
            catch (NumberFormatException ex) {
                return "Invalid integer format '" + s + "'";
            }
            return null;
        }

        String stringDefaultValue() {
            return "" + this.defaultValue;
        }

        String stringValue() {
            return "" + this.value;
        }

        int getValue() {
            return this.value;
        }
    }

    class StringOpt
    extends GetOptOpt {
        String value;
        String defaultValue;

        StringOpt(char sname, String lname, String defaultValue, String helpMessage) {
            this.sname = sname;
            this.lname = lname;
            this.value = defaultValue;
            this.helpMessage = helpMessage;
            this.defaultValue = defaultValue;
        }

        String eval(String equalString, ArgFeeder feeder) {
            String s = equalString;
            if (s == null) {
                s = feeder.next();
            }
            if (s == null) {
                return "Option " + this.lname + " requires a string argument.";
            }
            this.value = s;
            return null;
        }

        String stringDefaultValue() {
            if (this.defaultValue == null) {
                return "";
            }
            return this.defaultValue;
        }

        String stringValue() {
            return this.value;
        }

        String getValue() {
            return this.value;
        }
    }

    abstract class GetOptOpt {
        char sname;
        String lname;
        String helpMessage;
        boolean specified = false;

        GetOptOpt() {
        }

        abstract String eval(String var1, ArgFeeder var2);

        abstract String stringValue();

        abstract String stringDefaultValue();

        String getHelpMessage() {
            return this.helpMessage;
        }
    }

    class ArgFeeder {
        String[] args;
        int pos;

        ArgFeeder(String[] args) {
            this.args = args;
            this.pos = 0;
        }

        String peek() {
            if (this.hasNext()) {
                return this.args[this.pos + 1];
            }
            return null;
        }

        String next() {
            if (this.pos < this.args.length) {
                return this.args[this.pos++];
            }
            return null;
        }

        boolean hasNext() {
            return this.pos < this.args.length;
        }

        int size() {
            return this.args.length;
        }
    }
}

