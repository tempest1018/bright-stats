package org.brightstats.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Initial diagnostics for standardized linear PLS-SEM; no fit indices/resampling. */
public final class ModelDiagnostics {
    private ModelDiagnostics() {}

    public record Reliability(double alpha, double rhoA, double rhoC, double ave) {}
    public record Fit(Reliability[] reliability, double[][] htmt, double[][] fornellLarcker,
                      double[][] crossLoadings, double[][] innerVif, double[][] outerVif,
                      double[][] effectSizes, double[][] totalEffects, double[][] indirectEffects,
                      List<String> warnings) {
        public Fit {
            reliability = reliability.clone(); htmt = copy(htmt); fornellLarcker = copy(fornellLarcker);
            crossLoadings = copy(crossLoadings); innerVif = copy(innerVif); outerVif = copy(outerVif);
            effectSizes = copy(effectSizes); totalEffects = copy(totalEffects); indirectEffects = copy(indirectEffects);
            warnings = List.copyOf(warnings);
        }
        @Override public Reliability[] reliability() { return reliability.clone(); }
        @Override public double[][] htmt() { return copy(htmt); }
        @Override public double[][] fornellLarcker() { return copy(fornellLarcker); }
        @Override public double[][] crossLoadings() { return copy(crossLoadings); }
        @Override public double[][] innerVif() { return copy(innerVif); }
        @Override public double[][] outerVif() { return copy(outerVif); }
        @Override public double[][] effectSizes() { return copy(effectSizes); }
        @Override public double[][] totalEffects() { return copy(totalEffects); }
        @Override public double[][] indirectEffects() { return copy(indirectEffects); }
    }
    private static double[][] copy(double[][] data) { return Arrays.stream(data).map(double[]::clone).toArray(double[][]::new); }
    private static double[][] undefined(int rows, int columns) {
        double[][] result = new double[rows][columns]; for (double[] row : result) Arrays.fill(row, Double.NaN); return result;
    }

    public static Fit compute(double[][] observations, List<PlsSem.Block> blocks,
                              List<PlsSem.Edge> edges, PlsSem.Fit model) {
        int n = observations.length, variables = observations[0].length, count = blocks.size();
        if (!model.converged()) throw new IllegalArgumentException("Diagnostics require a converged model");
        double[][] scores = model.scores(), weights = model.weights(), loadings = model.loadings();
        if (scores.length != n || scores[0].length != count) throw new IllegalArgumentException("Model/data dimensions differ");
        boolean[] used = new boolean[variables];
        for (var block : blocks) for (int column : block.columns()) used[column] = true;
        double[][] standardized = new double[n][variables];
        for (int j = 0; j < variables; j++) {
            if (!used[j]) continue;
            double mean = 0;
            for (int i = 0; i < n; i++) mean += (observations[i][j] - mean) / (i + 1);
            double variance = 0;
            for (int i = 0; i < n; i++) { standardized[i][j] = observations[i][j] - mean; variance += standardized[i][j] * standardized[i][j] / n; }
            double sd = Math.sqrt(variance);
            if (!(sd > 0)) throw new IllegalArgumentException("Constant indicator in diagnostic data");
            for (int i = 0; i < n; i++) standardized[i][j] /= sd;
        }
        double[][] correlations = undefined(variables, variables);
        for (int i = 0; i < variables; i++) {
            if (!used[i]) continue;
            for (int j = 0; j <= i; j++) if (used[j]) {
                double value = 0; for (double[] row : standardized) value += row[i] * row[j] / n;
                correlations[i][j] = correlations[j][i] = Math.max(-1, Math.min(1, value));
            }
        }
        Reliability[] reliability = new Reliability[count];
        double[] monotrait = new double[count];
        Arrays.fill(monotrait, Double.NaN);
        List<String> warnings = new ArrayList<>();
        for (int j = 0; j < count; j++) {
            int[] columns = blocks.get(j).columns();
            if (blocks.get(j).measurementType() != PlsSem.MeasurementType.REFLECTIVE) {
                reliability[j] = new Reliability(Double.NaN, Double.NaN, Double.NaN, Double.NaN); continue;
            }
            double squares = 0, sum = 0;
            for (double value : loadings[j]) { sum += value; squares += value * value; }
            double ave = squares / columns.length;
            if (columns.length == 1) {
                reliability[j] = new Reliability(Double.NaN, Double.NaN, Double.NaN, ave); monotrait[j] = 1; continue;
            }
            double total = 0, within = 0;
            for (int a : columns) for (int b : columns) {
                total += correlations[a][b]; if (a != b) within += Math.abs(correlations[a][b]);
            }
            monotrait[j] = within / (columns.length * (columns.length - 1.0));
            double alpha = total == 0 ? Double.NaN : columns.length / (columns.length - 1.0) * (1 - columns.length / total);
            double s2 = 0, s4 = 0;
            for (double value : weights[j]) { s2 += value * value; s4 += value * value * value * value; }
            double denominator = s2 * s2 - s4;
            double rhoA = denominator == 0 ? Double.NaN : s2 * s2 * (1 - s2) / denominator;
            double rhoC = sum * sum / (sum * sum + columns.length - squares);
            reliability[j] = new Reliability(alpha, rhoA, rhoC, ave);
        }
        double[][] htmt = undefined(count, count), fornell = undefined(count, count);
        for (int i = 0; i < count; i++) {
            for (int j = 0; j <= i; j++) {
                double correlation = 0; for (double[] row : scores) correlation += row[i] * row[j] / n;
                fornell[i][j] = fornell[j][i] = i == j ? Math.sqrt(reliability[i].ave()) : correlation;
                if (i == j || blocks.get(i).measurementType() != PlsSem.MeasurementType.REFLECTIVE
                        || blocks.get(j).measurementType() != PlsSem.MeasurementType.REFLECTIVE) continue;
                double between = 0; int[] a = blocks.get(i).columns(), b = blocks.get(j).columns();
                for (int first : a) for (int second : b) between += Math.abs(correlations[first][second]);
                double denominator = Math.sqrt(monotrait[i] * monotrait[j]);
                htmt[i][j] = htmt[j][i] = denominator == 0 ? Double.NaN : between / (a.length * b.length) / denominator;
            }
        }
        double[][] crossLoadings = undefined(variables, count);
        for (int j = 0; j < variables; j++) if (used[j]) {
            for (int k = 0; k < count; k++) {
                double value = 0; for (int i = 0; i < n; i++) value += standardized[i][j] * scores[i][k] / n;
                crossLoadings[j][k] = value;
            }
        }
        double[][] outerVif = new double[count][];
        for (int j = 0; j < count; j++) {
            int[] columns = blocks.get(j).columns(); outerVif[j] = new double[columns.length];
            for (int k = 0; k < columns.length; k++) {
                int excluded = columns[k]; List<Integer> others = Arrays.stream(columns).filter(c -> c != excluded).boxed().toList();
                outerVif[j][k] = vif(standardized, excluded, others, warnings);
            }
        }
        List<List<Integer>> parents = new ArrayList<>(), children = new ArrayList<>();
        for (int i = 0; i < count; i++) { parents.add(new ArrayList<>()); children.add(new ArrayList<>()); }
        for (var edge : edges) { parents.get(edge.target()).add(edge.source()); children.get(edge.source()).add(edge.target()); }
        double[][] innerVif = undefined(count, count), f2 = undefined(count, count);
        double[] r2 = model.rSquared();
        for (var edge : edges) {
            List<Integer> remaining = parents.get(edge.target()).stream().filter(p -> p != edge.source()).toList();
            double reduced = remaining.isEmpty() ? 0 : OlsRegression.fit(select(scores, remaining), column(scores, edge.target())).rSquared();
            f2[edge.source()][edge.target()] = (r2[edge.target()] - reduced) / (1 - r2[edge.target()]);
            innerVif[edge.source()][edge.target()] = vif(scores, edge.source(), remaining, warnings);
        }
        List<Integer> ordered = new ArrayList<>(); int[] indegree = new int[count];
        for (int i = 0; i < count; i++) { indegree[i] = parents.get(i).size(); if (indegree[i] == 0) ordered.add(i); }
        for (int i = 0; i < ordered.size(); i++) for (int child : children.get(ordered.get(i))) if (--indegree[child] == 0) ordered.add(child);
        if (ordered.size() != count) throw new IllegalArgumentException("Structural model contains a cycle");
        double[][] total = new double[count][count], indirect = new double[count][count], paths = model.pathCoefficients();
        for (int source = 0; source < count; source++) {
            double[] accumulated = new double[count]; accumulated[source] = 1;
            for (int node : ordered) for (int child : children.get(node)) accumulated[child] += accumulated[node] * paths[node][child];
            for (int target = 0; target < count; target++) {
                total[source][target] = source == target ? 0 : accumulated[target];
                indirect[source][target] = total[source][target] - (Double.isFinite(paths[source][target]) ? paths[source][target] : 0);
            }
        }
        return new Fit(reliability, htmt, fornell, crossLoadings, innerVif, outerVif, f2, total, indirect, warnings);
    }

    private static double vif(double[][] data, int target, List<Integer> predictors, List<String> warnings) {
        if (predictors.isEmpty()) return 1;
        try {
            double residualFraction = 1 - OlsRegression.fit(select(data, predictors), column(data, target)).rSquared();
            if (residualFraction <= 0) { warnings.add("Perfect collinearity in auxiliary regression for column " + target); return Double.POSITIVE_INFINITY; }
            return 1 / residualFraction;
        } catch (IllegalArgumentException error) {
            warnings.add("VIF unavailable for column " + target + ": " + error.getMessage()); return Double.NaN;
        }
    }
    private static double[] column(double[][] data, int index) {
        double[] output = new double[data.length]; for (int i = 0; i < output.length; i++) output[i] = data[i][index]; return output;
    }
    private static double[][] select(double[][] data, List<Integer> columns) {
        double[][] output = new double[data.length][columns.size()];
        for (int i = 0; i < output.length; i++) for (int j = 0; j < columns.size(); j++) output[i][j] = data[i][columns.get(j)]; return output;
    }
}
