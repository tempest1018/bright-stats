package org.brightstats.report;
import com.google.gson.*;
import org.brightstats.stats.*;
public final class PolynomialReports {
    private PolynomialReports(){}
    public static ComponentPolynomial.Fit component(JsonObject report){return NumericArchives.reader().fromJson(NumericArchives.restoreRecord(report.getAsJsonObject("results").get("componentPolynomial"),ComponentPolynomial.Fit.class),ComponentPolynomial.Fit.class);}
    public static ObservedPolynomial.Fit observed(JsonObject report){return NumericArchives.reader().fromJson(NumericArchives.restoreRecord(report.getAsJsonObject("results").get("observedPolynomial"),ObservedPolynomial.Fit.class),ObservedPolynomial.Fit.class);}
    public static Bootstrap.Result bootstrap(JsonObject report){return report.getAsJsonObject("results").has("bootstrap")?ResamplingReports.read(report.getAsJsonObject("results").get("bootstrap")):null;}
}
