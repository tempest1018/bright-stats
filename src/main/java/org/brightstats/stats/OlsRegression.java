package org.brightstats.stats;

/** Ordinary least squares with an intercept, using Householder QR. */
public final class OlsRegression {
    private OlsRegression() {}

    public record Fit(double[] coefficients, double[] standardErrors, int observations,
                      int residualDegreesOfFreedom, double residualSumOfSquares,
                      double rSquared, double adjustedRSquared) {
        public Fit {
            coefficients = coefficients.clone();
            standardErrors = standardErrors.clone();
        }
        @Override public double[] coefficients() { return coefficients.clone(); }
        @Override public double[] standardErrors() { return standardErrors.clone(); }
        public double predict(double[] predictors) {
            if (predictors.length + 1 != coefficients.length) {
                throw new IllegalArgumentException("Predictor count differs from fitted model");
            }
            double value = coefficients[0];
            for (int i = 0; i < predictors.length; i++) {
                if (!Double.isFinite(predictors[i])) throw new IllegalArgumentException("Non-finite predictor");
                value = Math.fma(coefficients[i + 1], predictors[i], value);
            }
            return value;
        }
    }

    public static Fit fit(double[][] predictors, double[] response) {
        if (predictors == null || response == null || predictors.length != response.length
                || response.length == 0 || predictors[0] == null) {
            throw new IllegalArgumentException("Expected matching nonempty predictor and response arrays");
        }
        int n = response.length;
        int p = predictors[0].length + 1;
        if (n <= p) throw new IllegalArgumentException("Residual degrees of freedom must be positive");
        double[][] qr = new double[n][p];
        double scale = 0;
        for (int i = 0; i < n; i++) {
            if (predictors[i] == null || predictors[i].length != p - 1 || !Double.isFinite(response[i])) {
                throw new IllegalArgumentException("Ragged data or non-finite response");
            }
            qr[i][0] = 1;
            for (int j = 1; j < p; j++) {
                if (!Double.isFinite(predictors[i][j - 1])) throw new IllegalArgumentException("Non-finite predictor");
                qr[i][j] = predictors[i][j - 1];
            }
        }
        for (int j = 0; j < p; j++) {
            double norm = 0;
            for (int i = 0; i < n; i++) norm = Math.hypot(norm, qr[i][j]);
            scale = Math.max(scale, norm);
        }
        double tolerance = scale * 1e-12;
        double[] diagonal = new double[p];
        for (int k = 0; k < p; k++) {
            double norm = 0;
            for (int i = k; i < n; i++) norm = Math.hypot(norm, qr[i][k]);
            if (norm <= tolerance) throw new IllegalArgumentException("Rank-deficient or poorly scaled design");
            if (qr[k][k] < 0) norm = -norm;
            for (int i = k; i < n; i++) qr[i][k] /= norm;
            qr[k][k] += 1;
            for (int j = k + 1; j < p; j++) {
                double projection = 0;
                for (int i = k; i < n; i++) projection += qr[i][k] * qr[i][j];
                projection = -projection / qr[k][k];
                for (int i = k; i < n; i++) qr[i][j] += projection * qr[i][k];
            }
            diagonal[k] = -norm;
        }
        double[] transformed = response.clone();
        for (int k = 0; k < p; k++) {
            double projection = 0;
            for (int i = k; i < n; i++) projection += qr[i][k] * transformed[i];
            projection = -projection / qr[k][k];
            for (int i = k; i < n; i++) transformed[i] += projection * qr[i][k];
        }
        double[] coefficients = java.util.Arrays.copyOf(transformed, p);
        for (int k = p - 1; k >= 0; k--) {
            coefficients[k] /= diagonal[k];
            for (int j = 0; j < k; j++) coefficients[j] -= coefficients[k] * qr[j][k];
        }
        double mean = 0;
        for (int i = 0; i < n; i++) mean += (response[i] - mean) / (i + 1);
        double sse = 0;
        double sst = 0;
        for (int i = 0; i < n; i++) {
            double prediction = coefficients[0];
            for (int j = 1; j < p; j++) prediction = Math.fma(coefficients[j], predictors[i][j - 1], prediction);
            double error = response[i] - prediction;
            sse = Math.fma(error, error, sse);
            double centered = response[i] - mean;
            sst = Math.fma(centered, centered, sst);
        }
        int df = n - p;
        double r2 = sst == 0 ? Double.NaN : 1 - sse / sst;
        double adjusted = 1 - (1 - r2) * (n - 1) / df;
        double[][] inverseR = new double[p][p];
        for (int column = 0; column < p; column++) {
            for (int row = p - 1; row >= 0; row--) {
                double value = row == column ? 1 : 0;
                for (int j = row + 1; j < p; j++) value -= qr[row][j] * inverseR[j][column];
                inverseR[row][column] = value / diagonal[row];
            }
        }
        double[] standardErrors = new double[p];
        for (int i = 0; i < p; i++) {
            double diagonalVariance = 0;
            for (int j = 0; j < p; j++) diagonalVariance += inverseR[i][j] * inverseR[i][j];
            standardErrors[i] = Math.sqrt((sse / df) * diagonalVariance);
        }
        return new Fit(coefficients, standardErrors, n, df, sse, r2, adjusted);
    }
}
