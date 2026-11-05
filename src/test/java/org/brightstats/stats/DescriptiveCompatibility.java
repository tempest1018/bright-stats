package org.brightstats.stats;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** Compare against statistics produced by fresh native SmartPLS 4.1.2.1 imports. */
public final class DescriptiveCompatibility {
    private static int comparisons;
    private static void near(double actual, double expected) {
        comparisons++;
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > 1e-11 * Math.max(1, Math.abs(expected)))
            throw new AssertionError(actual + " differs from native reference " + expected);
    }
    public static void main(String[] args) throws Exception {
        var fixtures = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        int columns = 0;
        for (var fixture : fixtures.getAsJsonArray("datasets")) {
            var document = fixture.getAsJsonObject();
            var rows = document.getAsJsonArray("observations");
            double[][] data = new double[rows.size()][];
            for (int i = 0; i < data.length; i++) {
                var row = rows.get(i).getAsJsonArray(); data[i] = new double[row.size()];
                for (int j = 0; j < data[i].length; j++) data[i][j] = row.get(j).isJsonNull() ? Double.NaN : row.get(j).getAsDouble();
            }
            var summaries = DescriptiveStatistics.summarize(data);
            var expected = document.getAsJsonArray("expected");
            for (int j = 0; j < summaries.length; j++) {
                var value = summaries[j]; var reference = expected.get(j).getAsJsonObject(); columns++;
                near(value.mean(), reference.get("mean").getAsDouble());
                near(value.upperMedian(), reference.get("median").getAsDouble());
                near(value.populationStandardDeviation(), reference.get("standardDeviation").getAsDouble());
                near(value.minimum(), reference.get("lowestValue").getAsDouble());
                near(value.maximum(), reference.get("highestValue").getAsDouble());
                near(value.missing(), reference.get("numberOfMissingValues").getAsDouble());
                near(value.observed(), rows.size() - reference.get("numberOfMissingValues").getAsInt());
            }
        }
        var missing = DescriptiveStatistics.summarize(new double[][]{{Double.NaN, 7}, {Double.NaN, 7}});
        if (missing[0].observed() != 0 || !Double.isNaN(missing[0].mean()) || missing[1].populationStandardDeviation() != 0)
            throw new AssertionError("All-missing/constant summary is incorrect");
        var medians = DescriptiveStatistics.summarize(new double[][]{{1}, {2}, {3}, {4}})[0];
        near(medians.upperMedian(), 3); near(medians.midpointMedian(), 2.5);
        try { DescriptiveStatistics.summarize(new double[][]{{Double.POSITIVE_INFINITY}}); throw new AssertionError("Infinity accepted"); }
        catch (IllegalArgumentException expected) { }
        try { DescriptiveStatistics.summarize(new double[][]{{1}, {2, 3}}); throw new AssertionError("Ragged data accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("{\"native_version\":\"4.1.2.1\",\"columns\":" + columns + ",\"numeric_comparisons\":" + comparisons + "}");
    }
}
