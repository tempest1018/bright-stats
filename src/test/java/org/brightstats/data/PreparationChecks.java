package org.brightstats.data;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.brightstats.data.DataPreparation.*;
import org.brightstats.report.ReportArchive;
import org.brightstats.stats.OlsRegression;

public final class PreparationChecks {
    private static int checks;
    private static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("Preparation check " + checks); }
    private static void reject(Runnable operation) {
        try { operation.run(); throw new AssertionError("Invalid preparation accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static Prepared prepare(Raw raw, Settings settings) { return DataPreparation.prepare(raw, settings, DataPreparation.infer(raw, settings)); }
    public static void main(String[] args) throws Exception {
        Settings complete = new Settings(',', Decimal.DOT, "", Missing.REQUIRE_COMPLETE);
        Settings mean = new Settings(',', Decimal.DOT, "", Missing.MEAN_REPLACEMENT);
        Settings drop = new Settings(',', Decimal.DOT, "", Missing.REMOVE_ROWS);
        Raw mixed = DataPreparation.parse("x,y,group,unused\n1,3,Group A,7\n,5,Group B,8\n5,,Group A,9\n7,15,Group B,10\n", ',');
        var definitions = new ArrayList<>(DataPreparation.infer(mixed, mean)); definitions.set(3, new Column("unused", Kind.IGNORE));
        Prepared filled = DataPreparation.prepare(mixed, mean, definitions);
        check(filled.columns().equals(List.of("x", "y")));
        // Independent arithmetic: observed x=(1+5+7)/3, observed y=(3+5+15)/3.
        double[][] expectedFilled = {{1,3},{13.0/3,5},{5,23.0/3},{7,15}};
        for (int i = 0; i < 4; i++) for (int j = 0; j < 2; j++) check(Math.abs(filled.values()[i][j] - expectedFilled[i][j]) < 1e-14);
        check(filled.replacedCells() == 2 && filled.removedRows() == 0 && filled.sourceRows().equals(List.of(1,2,3,4)));
        check(filled.retainedRows().get(1).get(2).equals("Group B") && mixed.rows().get(1).getFirst().isEmpty());
        reject(() -> DataPreparation.prepare(mixed, complete, definitions));
        var deleted = DataPreparation.prepare(mixed, drop, definitions);
        check(Arrays.deepEquals(deleted.values(), new double[][]{{1,3},{7,15}}));
        check(deleted.removedRows() == 2 && deleted.sourceRows().equals(List.of(1,4)));
        var blankText = DataPreparation.parse("x,group\n1,\n2,A\n", ',');
        check(prepare(blankText, drop).sourceRows().equals(List.of(2)));
        check(prepare(blankText, mean).values().length == 2);
        var ignored = DataPreparation.parse("x,skip\n1,\n2,3\n", ',');
        check(DataPreparation.prepare(ignored, drop, List.of(new Column("x",Kind.NUMERIC),new Column("skip",Kind.IGNORE))).values().length == 2);
        var allMissing = DataPreparation.parse("x,y\n,1\n,2\n", ',');
        reject(() -> prepare(allMissing, mean)); reject(() -> prepare(allMissing, drop));
        check(DataPreparation.parse("x\n\"\"\n2\n", ',').rows().size() == 2);
        check(prepare(DataPreparation.parse("x\n\"\"\n2\n", ','), mean).values()[0][0] == 2);
        check(DataPreparation.number("1,234.56",Decimal.DOT) == 1234.56);
        check(DataPreparation.number("1.234,56",Decimal.COMMA) == 1234.56);
        check(DataPreparation.number("-,5e+1",Decimal.COMMA) == -5);
        reject(() -> DataPreparation.number("6,3",Decimal.DOT)); reject(() -> DataPreparation.number("6.3",Decimal.COMMA));
        reject(() -> DataPreparation.number("1,23,456.2",Decimal.DOT)); reject(() -> DataPreparation.number("NaN",Decimal.DOT));
        reject(() -> DataPreparation.number("1e9999",Decimal.DOT));
        var marker = new Settings(',',Decimal.DOT,"-99",Missing.MEAN_REPLACEMENT);
        check(DataPreparation.missing("-99.0",marker)); check(!DataPreparation.missing("-999",marker));
        var textMarker = new Settings(',',Decimal.DOT,"NA",Missing.MEAN_REPLACEMENT);
        check(prepare(DataPreparation.parse("x\nNA\n2\n",','), textMarker).values()[0][0] == 2);
        Path probes = Path.of(args[0]); int probeCount = 0;
        for (Path file : Files.list(probes).filter(p -> p.toString().endsWith(".csv")).sorted().toList()) {
            String text = Files.readString(file); char delimiter = DataPreparation.detectDelimiter(text);
            var settings = new Settings(delimiter, file.getFileName().toString().startsWith("08") ? Decimal.COMMA : Decimal.DOT,
                    file.getFileName().toString().startsWith("03") ? "-99" : "", Missing.MEAN_REPLACEMENT);
            Raw raw = DataPreparation.parse(text,delimiter); var columns = new ArrayList<>(DataPreparation.infer(raw, settings));
            if (file.getFileName().toString().startsWith("07")) {
                reject(() -> DataPreparation.prepare(raw, settings, columns)); columns.set(1,new Column("x_renamed",Kind.NUMERIC));
            }
            Prepared prepared = DataPreparation.prepare(raw,settings,columns); probeCount++;
            int expected = file.getFileName().toString().startsWith("09") ? 100 : file.getFileName().toString().startsWith("10") ? 101 : 20;
            check(prepared.values().length == expected);
            if (file.getFileName().toString().startsWith("02") || file.getFileName().toString().startsWith("03")) {
                check(prepared.replacedCells() == 1); check(Math.abs(prepared.values()[3][0] - 206.0/19) < 1e-13);
            }
            if (file.getFileName().toString().startsWith("04")) check(prepared.columns().size() == 3 && columns.getLast().kind() == Kind.TEXT);
            if (file.getFileName().toString().startsWith("05")) check(prepared.warnings().stream().anyMatch(w -> w.contains("Constant numeric column: constant")));
            if (file.getFileName().toString().startsWith("06")) check(prepared.warnings().stream().anyMatch(w -> w.contains("Linearly dependent numeric column: x2")));
            if (file.getFileName().toString().startsWith("08")) {
                check(prepared.values()[0][2] == 6.3 && prepared.columns().size() == 3);
                double[][] matrix = prepared.values();
                for (int i = 0; i < matrix.length; i++) for (int j = 0; j < matrix[i].length; j++)
                    check(matrix[i][j] == Double.parseDouble(raw.rows().get(i).get(j).replace(',','.')));
            }
        }
        // An independent known line remains y=2+3*x after explicit mean replacement.
        var line = prepare(DataPreparation.parse("x,y\n1,5\n2,8\n,11\n4,14\n5,17\n",','), mean);
        double[][] x = Arrays.stream(line.values()).map(row -> new double[]{row[0]}).toArray(double[][]::new);
        double[] y = Arrays.stream(line.values()).mapToDouble(row -> row[1]).toArray();
        var fit = OlsRegression.fit(x,y); check(Math.abs(fit.coefficients()[0]-2)<1e-12 && Math.abs(fit.coefficients()[1]-3)<1e-12);
        var history = ImportHistory.capture("<source&>.csv", mixed, mean, definitions);
        ImportHistory.verify(history, filled.columns(), filled.values());
        JsonObject restoredHistory = JsonParser.parseString(new Gson().toJson(history)).getAsJsonObject();
        ImportHistory.verify(restoredHistory, filled.columns(), filled.values()); check(history.equals(restoredHistory));
        double[][] mutated = filled.values(); mutated[0][0] = 99;
        reject(() -> ImportHistory.verify(history, filled.columns(), mutated)); check(filled.values()[0][0] == 1);
        JsonObject bad = history.deepCopy(); bad.addProperty("replacedCells",99); reject(() -> ImportHistory.read(bad));
        JsonObject project = new JsonObject(); project.add("dataPreparation",history); project.add("observations", new Gson().toJsonTree(filled.values()));
        var report = ReportArchive.create("OLS", project, new JsonObject(),new JsonArray(),filled.warnings());
        Path output = Path.of(args[1]); Files.createDirectories(output);
        ReportArchive.write(output.resolve("prepared.bsreport.json"), report);
        var reopened = ReportArchive.read(output.resolve("prepared.bsreport.json"));
        check(reopened.getAsJsonObject("project").getAsJsonObject("dataPreparation").equals(history));
        String html = ReportArchive.html(reopened); check(html.contains("MEAN_REPLACEMENT") && html.contains("&lt;source&amp;&gt;.csv") && html.contains("Retained source data rows"));
        ReportArchive.writeHtml(output.resolve("prepared.html"), reopened);
        System.out.println("{\"preparation_checks\":"+checks+",\"synthetic_probe_files\":"+probeCount+",\"independent_ols_after_imputation\":true,\"history_and_report_roundtrip\":true}");
    }
}
