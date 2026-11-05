package org.brightstats.report;

import com.google.gson.*;
import java.nio.file.*;
import java.util.List;
import org.brightstats.stats.OlsRegression;

public final class ReportArchiveChecks {
    private static void reject(JsonObject report) {
        try { ReportArchive.validate(report); throw new AssertionError("Invalid archive accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]); Files.createDirectories(output);
        JsonObject project = JsonParser.parseString("{\"observations\":[[1],[2],[3],[4]],\"options\":{\"maxIterations\":3000}}").getAsJsonObject();
        JsonObject numeric = new JsonObject();
        numeric.addProperty("response", "outcome");
        var fit = OlsRegression.fit(new double[][]{{1}, {2}, {3}, {4}}, new double[]{3, 5, 7, 9});
        numeric.add("fit", new Gson().toJsonTree(fit)); numeric.addProperty("undefined", Double.NaN);
        numeric.addProperty("infinite", Double.POSITIVE_INFINITY); numeric.addProperty("precise", 0.1234567890123456);
        numeric.addProperty("negativeInfinite", Double.NEGATIVE_INFINITY);
        JsonArray tables = JsonParser.parseString("[{\"title\":\"<script>alert(1)</script>\",\"columns\":[\"Term\",\"Coefficient\"],\"rows\":[[\"A&B\",\"2.000000\"]]}]").getAsJsonArray();
        var report = ReportArchive.create("OLS", project, numeric, tables, List.of("Example warning <b>"));
        var activeSettings = report.getAsJsonObject("calculationSettings");
        if (!activeSettings.get("intercept").getAsBoolean() || activeSettings.has("maxIterations")
                || !activeSettings.get("response").getAsString().equals("outcome"))
            throw new AssertionError("OLS report contains incorrect calculation settings");
        project.addProperty("changed", true); tables.get(0).getAsJsonObject().addProperty("title", "changed");
        if (report.getAsJsonObject("project").has("changed")) throw new AssertionError("Input snapshot aliases current project");
        if (!report.getAsJsonObject("results").get("undefined").isJsonNull()
                || !report.getAsJsonObject("results").get("infinite").getAsString().equals("Infinity")
                || !report.getAsJsonObject("results").get("negativeInfinite").getAsString().equals("-Infinity"))
            throw new AssertionError("Non-finite values were not normalized");
        Path archive = output.resolve("roundtrip.bsreport.json"); ReportArchive.write(archive, report);
        var restored = ReportArchive.read(archive);
        if (!restored.equals(report)) throw new AssertionError("Archive roundtrip changed its results or metadata");
        if (Double.doubleToLongBits(restored.getAsJsonObject("results").get("precise").getAsDouble())
                != Double.doubleToLongBits(0.1234567890123456)) throw new AssertionError("Numeric precision lost");
        String html = ReportArchive.html(restored);
        if (html.contains("<script>") || html.contains("<b>") || !html.contains("&lt;script&gt;") || !html.contains("A&amp;B")
                || !html.contains("Response: outcome") || !html.contains("Content-Security-Policy") || html.contains("http://") || html.contains("https://"))
            throw new AssertionError("HTML is not escaped and self-contained");
        ReportArchive.writeHtml(output.resolve("roundtrip.html"), restored);
        var bad = report.deepCopy(); bad.addProperty("formatVersion", 1.5); reject(bad);
        bad = report.deepCopy(); bad.remove("results"); reject(bad);
        bad = report.deepCopy(); bad.remove("applicationVersion"); reject(bad);
        bad = report.deepCopy(); bad.getAsJsonArray("tables").get(0).getAsJsonObject().getAsJsonArray("rows").get(0).getAsJsonArray().add("extra"); reject(bad);
        Path invalid = output.resolve("invalid.json"); Files.writeString(invalid, new Gson().toJson(report) + " trailing");
        try { ReportArchive.read(invalid); throw new AssertionError("Trailing data accepted"); }
        catch (JsonParseException expected) { }
        System.out.println("{\"report_roundtrip\":true,\"full_precision\":true,\"input_snapshot_isolated\":true,\"html_escaped_offline\":true,\"rejection_cases\":5}");
    }
}
