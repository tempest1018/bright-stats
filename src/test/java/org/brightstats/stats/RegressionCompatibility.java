package org.brightstats.stats;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

/** Independent-library comparisons plus coefficients from saved vendor reports. */
public final class RegressionCompatibility {
    private static double maximumDifference;

    private static void near(double actual, double expected, double tolerance) {
        double difference = Math.abs(actual - expected);
        if (!Double.isFinite(actual) || !Double.isFinite(expected) || difference > tolerance) {
            throw new AssertionError("Numeric mismatch: " + actual + " versus " + expected);
        }
        maximumDifference = Math.max(maximumDifference, difference);
    }

    private static double[] numbers(String line) {
        return Arrays.stream(line.trim().split("\\s+")).mapToDouble(Double::parseDouble).toArray();
    }

    public static void main(String[] args) throws Exception {
        Random random = new Random(1247181L);
        for (int iteration = 0; iteration < 200; iteration++) {
            int n = 30 + random.nextInt(90);
            int p = 1 + random.nextInt(7);
            double[][] x = new double[n][p];
            double[] y = new double[n];
            for (int i = 0; i < n; i++) {
                y[i] = 2 + random.nextGaussian() * .2;
                for (int j = 0; j < p; j++) {
                    x[i][j] = random.nextGaussian();
                    y[i] += (j + 1) * .3 * x[i][j];
                }
            }
            var ours = OlsRegression.fit(x, y);
            var independent = new OLSMultipleLinearRegression();
            independent.newSampleData(y, x);
            double[] expected = independent.estimateRegressionParameters();
            double[] expectedErrors = independent.estimateRegressionParametersStandardErrors();
            for (int j = 0; j < expected.length; j++) {
                near(ours.coefficients()[j], expected[j], 1e-10);
                near(ours.standardErrors()[j], expectedErrors[j], 1e-10);
            }
            near(ours.rSquared(), independent.calculateRSquared(), 1e-10);
            near(ours.adjustedRSquared(), independent.calculateAdjustedRSquared(), 1e-10);
            near(ours.residualSumOfSquares(), independent.calculateResidualSumOfSquares(), 1e-10);
        }
        int vendorCases = 0;
        try (var paths = Files.list(Path.of(args[0]))) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".matrix")).sorted().toList()) {
                var lines = Files.readAllLines(file);
                double[] dimensions = numbers(lines.getFirst());
                int n = (int) dimensions[0];
                int p = (int) dimensions[1];
                double[][] x = new double[n][p];
                double[] y = new double[n];
                for (int i = 0; i < n; i++) {
                    double[] row = numbers(lines.get(i + 1));
                    y[i] = row[0];
                    x[i] = Arrays.copyOfRange(row, 1, row.length);
                }
                double[] expected = numbers(Files.readString(Path.of(file.toString().replace(".matrix", ".expected"))));
                var fit = OlsRegression.fit(x, y);
                for (int j = 0; j < fit.coefficients().length; j++) {
                    if (!Double.isNaN(expected[j])) near(fit.coefficients()[j], expected[j], 1e-9);
                }
                near(fit.rSquared(), expected[expected.length - 1], 1e-9);
                vendorCases++;
            }
        }
        if (vendorCases == 0) throw new AssertionError("No saved vendor report cases checked");
        double[][] duplicate = {{1,1}, {2,2}, {3,3}, {4,4}, {5,5}};
        boolean singularRejected = false;
        try { OlsRegression.fit(duplicate, new double[]{1,2,3,4,5}); }
        catch (IllegalArgumentException expected) { singularRejected = true; }
        if (!singularRejected) throw new AssertionError("Rank deficiency was not rejected");
        System.out.printf(java.util.Locale.ROOT,
            "{\"independent_library_cases\":200,\"saved_vendor_report_cases\":%d,"
            + "\"maximum_absolute_difference\":%.17g,\"rank_deficiency_rejected\":true}%n",
            vendorCases, maximumDifference);
    }
}
