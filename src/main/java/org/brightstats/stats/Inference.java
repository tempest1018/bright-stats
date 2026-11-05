package org.brightstats.stats;

import java.util.*;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.special.Erf;

/** Two-sided inference: classical Student-t for OLS; normal-reference bootstrap ratios. */
public final class Inference {
    private Inference() {}
    public record Term(double estimate, double standardError, double statistic, double pValue,
                       double lower, double upper) {}
    public record Regression(double confidence, int degreesOfFreedom, String method, List<Term> terms) {
        public Regression { terms = List.copyOf(terms); }
    }
    public static void confidence(double confidence) {
        if (!Double.isFinite(confidence) || confidence < .5 || confidence > .9999)
            throw new IllegalArgumentException("Confidence must be between 50% and 99.99%.");
    }
    public static Regression regression(OlsRegression.Fit fit, double confidence) {
        confidence(confidence);
        TDistribution distribution = new TDistribution(fit.residualDegreesOfFreedom());
        double critical = distribution.inverseCumulativeProbability((1 + confidence) / 2);
        List<Term> terms = new ArrayList<>(); double[] coefficients = fit.coefficients(), errors = fit.standardErrors();
        for (int i = 0; i < coefficients.length; i++) {
            double statistic = ratio(coefficients[i],errors[i]);
            double p = Double.isNaN(statistic) ? Double.NaN : 2 * distribution.cumulativeProbability(-Math.abs(statistic));
            terms.add(new Term(coefficients[i], errors[i], statistic, p,
                    coefficients[i] - critical * errors[i], coefficients[i] + critical * errors[i]));
        }
        return new Regression(confidence,fit.residualDegreesOfFreedom(),"Two-sided classical Student-t; homoskedastic OLS standard errors",terms);
    }
    public static double ratio(double estimate, double standardError) {
        if (standardError == 0) return estimate == 0 ? Double.NaN : Math.copySign(Double.POSITIVE_INFINITY,estimate);
        return estimate / standardError;
    }
    public static double normalTwoSided(double statistic) {
        return Double.isNaN(statistic) ? Double.NaN : Erf.erfc(Math.abs(statistic) / Math.sqrt(2));
    }
    /** Linear interpolation of order statistics, h=(B-1)*p (R type 7). */
    public static double quantile(double[] sorted, double probability) {
        if (sorted.length == 0 || probability < 0 || probability > 1) throw new IllegalArgumentException("Invalid quantile");
        double h = (sorted.length - 1) * probability; int lower = (int)Math.floor(h), upper = (int)Math.ceil(h);
        return sorted[lower] + (h - lower) * (sorted[upper] - sorted[lower]);
    }
}
