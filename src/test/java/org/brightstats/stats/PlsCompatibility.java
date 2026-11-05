package org.brightstats.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Compares complete score estimation, not just regression on supplied scores. */
public final class PlsCompatibility {
    private static double maximumDifference;
    private static int comparisons;

    private static void near(double actual, double expected) {
        double difference = Math.abs(actual - expected);
        if (!Double.isFinite(actual) || !Double.isFinite(expected) || difference > 1e-9) {
            throw new AssertionError("PLS mismatch: " + actual + " versus " + expected);
        }
        comparisons++;
        maximumDifference = Math.max(maximumDifference, difference);
    }

    public static void main(String[] args) throws Exception {
        int cases = 0;
        int diagnosticComparisons = 0;
        try (var files = Files.list(Path.of(args[0]))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject fixture = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                JsonArray raw = fixture.getAsJsonArray("observations");
                int n = raw.size();
                double[][] data = new double[n][];
                for (int i = 0; i < n; i++) {
                    JsonArray row = raw.get(i).getAsJsonArray();
                    data[i] = new double[row.size()];
                    for (int j = 0; j < row.size(); j++) data[i][j] = row.get(j).getAsDouble();
                }
                JsonArray blockData = fixture.getAsJsonArray("blocks");
                var blocks = new ArrayList<PlsSem.Block>();
                for (var value : blockData) {
                    JsonObject block = value.getAsJsonObject();
                    JsonArray columns = block.getAsJsonArray("columns");
                    int[] indices = new int[columns.size()];
                    for (int i = 0; i < indices.length; i++) indices[i] = columns.get(i).getAsInt();
                    blocks.add(new PlsSem.Block(block.get("name").getAsString(), indices,
                            PlsSem.Mode.valueOf(block.get("mode").getAsString())));
                }
                var edges = new ArrayList<PlsSem.Edge>();
                for (var value : fixture.getAsJsonArray("edges")) {
                    JsonObject edge = value.getAsJsonObject();
                    edges.add(new PlsSem.Edge(edge.get("source").getAsInt(), edge.get("target").getAsInt()));
                }
                var result = PlsSem.fit(data, blocks, edges, PlsSem.Options.defaults());
                if (!result.converged()) throw new AssertionError("Reference model failed to converge");
                double[][] paths = result.pathCoefficients();
                for (var value : fixture.getAsJsonArray("edges")) {
                    JsonObject edge = value.getAsJsonObject();
                    near(paths[edge.get("source").getAsInt()][edge.get("target").getAsInt()], edge.get("expected").getAsDouble());
                }
                double[][] weights = result.weights();
                double[][] loadings = result.loadings();
                for (int j = 0; j < blocks.size(); j++) {
                    JsonObject block = blockData.get(j).getAsJsonObject();
                    for (int k = 0; k < weights[j].length; k++) {
                        near(weights[j][k], block.getAsJsonArray("expected_weights").get(k).getAsDouble());
                        near(loadings[j][k], block.getAsJsonArray("expected_loadings").get(k).getAsDouble());
                    }
                }
                double[][] scores = result.scores();
                JsonArray expectedScores = fixture.getAsJsonArray("expected_scores");
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < blocks.size(); j++) {
                        near(scores[i][j], expectedScores.get(i).getAsJsonArray().get(j).getAsDouble());
                    }
                }
                double[][] augmented = new double[n][];
                for (int i = 0; i < n; i++) {
                    augmented[i] = java.util.Arrays.copyOf(data[i], data[i].length + 1);
                    augmented[i][data[i].length] = 1; // Constant unused columns must not invalidate the model.
                }
                var ignored = PlsSem.fit(augmented, blocks, edges, PlsSem.Options.defaults());
                if (!java.util.Arrays.deepEquals(result.scores(), ignored.scores())) throw new AssertionError("Unused indicator changed the model");
                if (fixture.has("expected_diagnostics")) {
                    var diagnostics = ModelDiagnostics.compute(data, blocks, edges, result);
                    if (!diagnostics.warnings().isEmpty()) throw new AssertionError("Unexpected diagnostic warnings: " + diagnostics.warnings());
                    JsonObject expected = fixture.getAsJsonObject("expected_diagnostics");
                    var reliability = diagnostics.reliability();
                    int before = comparisons;
                    for (var value : expected.getAsJsonArray("reliability")) {
                        JsonArray row = value.getAsJsonArray(); var actual = reliability[row.get(0).getAsInt()];
                        near(actual.alpha(), row.get(1).getAsDouble()); near(actual.rhoA(), row.get(2).getAsDouble());
                        near(actual.rhoC(), row.get(3).getAsDouble()); near(actual.ave(), row.get(4).getAsDouble());
                    }
                    String[] fields = {"htmt", "fornell", "crossLoadings", "innerVif", "outerVif", "effectSizes", "totalEffects", "indirectEffects"};
                    double[][][] matrices = {diagnostics.htmt(), diagnostics.fornellLarcker(), diagnostics.crossLoadings(), diagnostics.innerVif(),
                            diagnostics.outerVif(), diagnostics.effectSizes(), diagnostics.totalEffects(), diagnostics.indirectEffects()};
                    for (int k = 0; k < fields.length; k++) {
                        for (var value : expected.getAsJsonArray(fields[k])) {
                            JsonArray row = value.getAsJsonArray();
                            near(matrices[k][row.get(0).getAsInt()][row.get(1).getAsInt()], row.get(2).getAsDouble());
                        }
                    }
                    diagnosticComparisons += comparisons - before;
                    var changed = new ArrayList<>(blocks);
                    for (int j = 0; j < changed.size(); j++) if (changed.get(j).mode() == PlsSem.Mode.A) {
                        var old = changed.get(j); changed.set(j, new PlsSem.Block(old.name(), old.columns(), old.mode(), old.initialWeights(), PlsSem.MeasurementType.FORMATIVE));
                        if (!Double.isNaN(ModelDiagnostics.compute(data, changed, edges, result).reliability()[j].alpha())) {
                            throw new AssertionError("Weighting mode was confused with conceptual measurement type");
                        }
                        break;
                    }
                }
                var limited = PlsSem.fit(data, blocks, edges, new PlsSem.Options(1, 1e-15, PlsSem.Weighting.PATH));
                if (limited.converged() || limited.iterations() != 1) {
                    throw new AssertionError("Iteration-limit status was lost");
                }
                cases++;
            }
        }
        if (cases == 0) throw new AssertionError("No complete PLS reference cases checked");
        System.out.printf(java.util.Locale.ROOT,
            "{\"complete_pls_cases\":%d,\"numeric_comparisons\":%d,\"maximum_absolute_difference\":%.17g,"
            + "\"diagnostic_comparisons\":%d,\"unused_indicators_ignored\":true,\"iteration_limit_reported\":true,\"reference_version\":\"4.0.9.7\"}%n",
            cases, comparisons, maximumDifference, diagnosticComparisons);
    }
}
