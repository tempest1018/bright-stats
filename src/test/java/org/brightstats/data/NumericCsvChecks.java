package org.brightstats.data;

import java.util.List;

public final class NumericCsvChecks {
    private static void reject(String text) {
        boolean rejected = false;
        try { NumericCsv.parse(text); } catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Invalid data accepted");
    }
    public static void main(String[] args) {
        var csv = NumericCsv.parse("\ufeff\"x, one\",\"y\"\r\n1,2\r\n3,4\r\n");
        if (!csv.columns().equals(List.of("x, one", "y")) || csv.values()[1][1] != 4) throw new AssertionError();
        var tabs = NumericCsv.parse("alpha\tbeta\n1\t2\n\n3\t4");
        if (tabs.values().length != 2 || tabs.values()[1][0] != 3) throw new AssertionError();
        var semi = NumericCsv.parse("\"a\"\"b\";c\n1.5;2.5\n");
        if (!semi.columns().getFirst().equals("a\"b") || semi.values()[0][0] != 1.5) throw new AssertionError();
        var multiline = NumericCsv.parse("\"line\nname\",b\n1,2\n");
        if (!multiline.columns().getFirst().contains("\n")) throw new AssertionError();
        reject("x,x\n1,2"); reject("x,y\n1"); reject("x,y\n1,");
        reject("x\nNaN"); reject("x\nInfinity"); reject("\"x\n1");
        reject("\"x\"bad\n1"); reject("x\n=runSomething()");
        double[][] changed = csv.values(); changed[0][0] = 900;
        if (csv.values()[0][0] == 900) throw new AssertionError("Dataset is mutable through its accessor");
        System.out.println("{\"csv_format_cases\":4,\"rejection_cases\":8,\"defensive_copy\":true}");
    }
}
