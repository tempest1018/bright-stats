package org.brightstats.stats;

import java.util.Arrays;

/** Column summaries. NaN represents missing data; infinities are rejected. */
public final class DescriptiveStatistics {
    private DescriptiveStatistics() {}
    public record Summary(int observed, int missing, double mean, double upperMedian,
                          double midpointMedian, double populationStandardDeviation,
                          double minimum, double maximum) {}

    public static Summary[] summarize(double[][] data) {
        if (data == null || data.length == 0 || data[0] == null || data[0].length == 0)
            throw new IllegalArgumentException("Expected a nonempty rectangular data matrix");
        int columns = data[0].length;
        for (double[] row : data) {
            if (row == null || row.length != columns) throw new IllegalArgumentException("Data rows have different column counts");
            for (double value : row) if (Double.isInfinite(value)) throw new IllegalArgumentException("Infinite observations are not supported");
        }
        Summary[] result = new Summary[columns];
        for (int column = 0; column < columns; column++) {
            double[] values = new double[data.length];
            int count = 0;
            double mean = 0, sumSquares = 0;
            for (double[] row : data) {
                double value = row[column];
                if (Double.isNaN(value)) continue;
                values[count++] = value;
                double delta = value - mean;
                mean += delta / count;
                sumSquares += delta * (value - mean);
            }
            if (count == 0) {
                result[column] = new Summary(0, data.length, Double.NaN, Double.NaN,
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN);
                continue;
            }
            Arrays.sort(values, 0, count);
            double upper = values[count / 2];
            double midpoint = count % 2 == 1 ? upper : values[count / 2 - 1] / 2 + upper / 2;
            result[column] = new Summary(count, data.length - count, mean, upper, midpoint,
                    Math.sqrt(Math.max(0, sumSquares / count)), values[0], values[count - 1]);
        }
        return result;
    }
}
