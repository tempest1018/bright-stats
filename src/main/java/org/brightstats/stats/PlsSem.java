package org.brightstats.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Standardized PLS-SEM iteration for finite, preprocessed observations. */
public final class PlsSem {
    private PlsSem() {}
    public enum Mode { A, B }
    public enum MeasurementType { REFLECTIVE, FORMATIVE }
    public enum Weighting { PATH, FACTOR }

    public record Block(String name, int[] columns, Mode mode, double[] initialWeights, MeasurementType measurementType) {
        public Block(String name, int[] columns, Mode mode) { this(name, columns, mode, null); }
        public Block(String name, int[] columns, Mode mode, double[] initialWeights) {
            this(name, columns, mode, initialWeights, mode == Mode.A ? MeasurementType.REFLECTIVE : MeasurementType.FORMATIVE);
        }
        public Block {
            Objects.requireNonNull(name);
            Objects.requireNonNull(columns);
            Objects.requireNonNull(mode);
            Objects.requireNonNull(measurementType);
            columns = columns.clone();
            if (columns.length == 0) throw new IllegalArgumentException("Empty indicator block");
            if (initialWeights == null) {
                initialWeights = new double[columns.length];
                Arrays.fill(initialWeights, 1);
            } else initialWeights = initialWeights.clone();
            if (initialWeights.length != columns.length) throw new IllegalArgumentException("Initial weight count differs");
            for (double value : initialWeights) {
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite initial weight");
            }
        }
        @Override public int[] columns() { return columns.clone(); }
        @Override public double[] initialWeights() { return initialWeights.clone(); }
    }

    public record Edge(int source, int target) {}
    public record Options(int maxIterations, double stopCriterion, Weighting weighting) {
        public Options {
            if (maxIterations < 1 || !Double.isFinite(stopCriterion) || stopCriterion <= 0) {
                throw new IllegalArgumentException("Invalid convergence options");
            }
            Objects.requireNonNull(weighting);
        }
        public static Options defaults() { return new Options(3000, 1e-7, Weighting.PATH); }
    }

    public record Fit(double[][] scores, double[][] weights, double[][] loadings,
                      double[][] pathCoefficients, double[] rSquared,
                      int iterations, boolean converged, double finalWeightChange) {
        public Fit {
            scores = copy(scores); weights = copy(weights); loadings = copy(loadings);
            pathCoefficients = copy(pathCoefficients); rSquared = rSquared.clone();
        }
        @Override public double[][] scores() { return copy(scores); }
        @Override public double[][] weights() { return copy(weights); }
        @Override public double[][] loadings() { return copy(loadings); }
        @Override public double[][] pathCoefficients() { return copy(pathCoefficients); }
        @Override public double[] rSquared() { return rSquared.clone(); }
    }

    private static double[][] copy(double[][] data) {
        return Arrays.stream(data).map(double[]::clone).toArray(double[][]::new);
    }

    public static Fit fit(double[][] observations, List<Block> suppliedBlocks,
                          List<Edge> suppliedEdges, Options options) {
        Objects.requireNonNull(options);
        List<Block> blocks = List.copyOf(suppliedBlocks);
        List<Edge> edges = List.copyOf(suppliedEdges);
        if (observations == null || observations.length < 3 || observations[0] == null
                || observations[0].length == 0 || blocks.isEmpty()) {
            throw new IllegalArgumentException("Expected nonempty observations and indicator blocks");
        }
        int n = observations.length;
        int variables = observations[0].length;
        int count = blocks.size();
        boolean[] used = new boolean[variables];
        for (Block block : blocks) {
            HashSet<Integer> included = new HashSet<>();
            for (int column : block.columns) {
                if (column < 0 || column >= variables || !included.add(column)) {
                    throw new IllegalArgumentException("Invalid or duplicate indicator column");
                }
                used[column] = true;
            }
        }
        double[][] standardized = new double[n][variables];
        double[] means = new double[variables];
        for (int i = 0; i < n; i++) {
            if (observations[i] == null || observations[i].length != variables) {
                throw new IllegalArgumentException("Ragged observations");
            }
            for (int j = 0; j < variables; j++) {
                if (!used[j]) continue;
                double value = observations[i][j];
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Preprocess missing/non-finite observations first");
                means[j] += (value - means[j]) / (i + 1);
            }
        }
        for (int j = 0; j < variables; j++) {
            if (!used[j]) continue;
            double sum = 0;
            for (int i = 0; i < n; i++) {
                standardized[i][j] = observations[i][j] - means[j];
                sum += standardized[i][j] * standardized[i][j];
            }
            double sd = Math.sqrt(sum / n);
            if (sd == 0 || !Double.isFinite(sd)) throw new IllegalArgumentException("Constant or unscalable indicator");
            for (int i = 0; i < n; i++) standardized[i][j] /= sd;
        }
        List<List<Integer>> parents = new ArrayList<>(), children = new ArrayList<>();
        for (int j = 0; j < count; j++) { parents.add(new ArrayList<>()); children.add(new ArrayList<>()); }
        HashSet<Edge> unique = new HashSet<>();
        for (Edge edge : edges) {
            if (edge.source < 0 || edge.source >= count || edge.target < 0 || edge.target >= count
                    || edge.source == edge.target || !unique.add(edge)) {
                throw new IllegalArgumentException("Invalid or duplicate structural edge");
            }
            parents.get(edge.target).add(edge.source);
            children.get(edge.source).add(edge.target);
        }
        // Standard PLS path models are recursive; do not silently accept feedback cycles.
        int[] indegree = new int[count];
        ArrayList<Integer> ready = new ArrayList<>();
        for (int j = 0; j < count; j++) {
            indegree[j] = parents.get(j).size();
            if (indegree[j] == 0) ready.add(j);
        }
        for (int i = 0; i < ready.size(); i++) {
            for (int child : children.get(ready.get(i))) if (--indegree[child] == 0) ready.add(child);
        }
        if (ready.size() != count) throw new IllegalArgumentException("Structural model contains a directed cycle");

        double[][][] blockData = new double[count][][];
        double[][] weights = new double[count][];
        double[][] scores = new double[n][count];
        for (int j = 0; j < count; j++) {
            Block block = blocks.get(j);
            HashSet<Integer> included = new HashSet<>();
            blockData[j] = new double[n][block.columns.length];
            for (int k = 0; k < block.columns.length; k++) {
                int column = block.columns[k];
                if (column < 0 || column >= variables || !included.add(column)) {
                    throw new IllegalArgumentException("Invalid or duplicate indicator column");
                }
                for (int i = 0; i < n; i++) blockData[j][i][k] = standardized[i][column];
            }
            weights[j] = block.initialWeights.clone();
            normalize(blockData[j], weights[j], scores, j);
        }
        boolean converged = false;
        int iteration = 0;
        double change = Double.POSITIVE_INFINITY;
        for (; iteration < options.maxIterations; iteration++) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("PLS calculation cancelled.");
            double[][] nextScores = new double[n][count];
            double[][] nextWeights = new double[count][];
            change = 0;
            for (int j = 0; j < count; j++) {
                double[] inner = new double[n];
                List<Integer> predecessors = parents.get(j);
                if (options.weighting == Weighting.PATH && !predecessors.isEmpty()) {
                    double[] coefficients = OlsRegression.fit(select(scores, predecessors), column(scores, j)).coefficients();
                    for (int k = 0; k < predecessors.size(); k++) {
                        for (int i = 0; i < n; i++) inner[i] += coefficients[k + 1] * scores[i][predecessors.get(k)];
                    }
                } else {
                    for (int parent : predecessors) addCorrelatedScore(inner, scores, parent, j);
                }
                for (int child : children.get(j)) addCorrelatedScore(inner, scores, child, j);
                if (predecessors.isEmpty() && children.get(j).isEmpty()) inner = column(scores, j);
                int indicators = weights[j].length;
                double[] updated;
                if (blocks.get(j).mode == Mode.B) {
                    updated = Arrays.copyOfRange(OlsRegression.fit(blockData[j], inner).coefficients(), 1, indicators + 1);
                } else {
                    updated = new double[indicators];
                    for (int k = 0; k < indicators; k++) {
                        for (int i = 0; i < n; i++) updated[k] += blockData[j][i][k] * inner[i] / n;
                    }
                }
                normalize(blockData[j], updated, nextScores, j);
                double orientation = 0;
                for (int i = 0; i < n; i++) orientation += nextScores[i][j] * scores[i][j];
                if (orientation < 0) {
                    for (int k = 0; k < indicators; k++) updated[k] = -updated[k];
                    for (int i = 0; i < n; i++) nextScores[i][j] = -nextScores[i][j];
                }
                for (int k = 0; k < indicators; k++) change = Math.max(change, Math.abs(updated[k] - weights[j][k]));
                nextWeights[j] = updated;
            }
            scores = nextScores;
            weights = nextWeights;
            if (change < options.stopCriterion) { converged = true; iteration++; break; }
        }
        double[][] paths = new double[count][count];
        for (double[] row : paths) Arrays.fill(row, Double.NaN);
        double[] r2 = new double[count];
        Arrays.fill(r2, Double.NaN);
        double[][] loadings = new double[count][];
        for (int j = 0; j < count; j++) {
            loadings[j] = new double[weights[j].length];
            for (int k = 0; k < loadings[j].length; k++) {
                for (int i = 0; i < n; i++) loadings[j][k] += blockData[j][i][k] * scores[i][j] / n;
            }
            if (!parents.get(j).isEmpty()) {
                var regression = OlsRegression.fit(select(scores, parents.get(j)), column(scores, j));
                for (int k = 0; k < parents.get(j).size(); k++) paths[parents.get(j).get(k)][j] = regression.coefficients()[k + 1];
                r2[j] = regression.rSquared();
            }
        }
        return new Fit(scores, weights, loadings, paths, r2, iteration, converged, change);
    }

    private static void normalize(double[][] data, double[] weights, double[][] destination, int column) {
        double variance = 0;
        for (int i = 0; i < data.length; i++) {
            double score = 0;
            for (int k = 0; k < weights.length; k++) score += data[i][k] * weights[k];
            destination[i][column] = score;
            variance += score * score / data.length;
        }
        double norm = Math.sqrt(variance);
        if (!(norm > 0) || !Double.isFinite(norm)) throw new IllegalArgumentException("Degenerate construct score");
        for (int i = 0; i < data.length; i++) destination[i][column] /= norm;
        for (int k = 0; k < weights.length; k++) weights[k] /= norm;
    }

    private static void addCorrelatedScore(double[] destination, double[][] scores, int neighbor, int target) {
        double correlation = 0;
        for (double[] row : scores) correlation += row[neighbor] * row[target] / scores.length;
        for (int i = 0; i < scores.length; i++) destination[i] += correlation * scores[i][neighbor];
    }

    private static double[] column(double[][] matrix, int column) {
        double[] result = new double[matrix.length];
        for (int i = 0; i < result.length; i++) result[i] = matrix[i][column];
        return result;
    }

    private static double[][] select(double[][] matrix, List<Integer> columns) {
        double[][] result = new double[matrix.length][columns.size()];
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < columns.size(); j++) result[i][j] = matrix[i][columns.get(j)];
        }
        return result;
    }
}
