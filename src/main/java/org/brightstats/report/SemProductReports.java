package org.brightstats.report;
import com.google.gson.*;
import org.brightstats.stats.*;
public final class SemProductReports {
    private SemProductReports(){}
    public static SemProductIndicators.Fit point(JsonObject report){return NumericArchives.reader().fromJson(NumericArchives.restoreRecord(report.getAsJsonObject("results").get("semProducts"),SemProductIndicators.Fit.class),SemProductIndicators.Fit.class);}
    public static Bootstrap.Result bootstrap(JsonObject report){return report.getAsJsonObject("results").has("bootstrap")?ResamplingReports.read(report.getAsJsonObject("results").get("bootstrap")):null;}
}
