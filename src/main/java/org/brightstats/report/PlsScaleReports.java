package org.brightstats.report;
import com.google.gson.*;
import org.brightstats.stats.*;
public final class PlsScaleReports {
    private PlsScaleReports(){}
    public static PlsScales.Fit point(JsonObject report){return NumericArchives.reader().fromJson(NumericArchives.restoreRecord(report.getAsJsonObject("results").get("plsScales"),PlsScales.Fit.class),PlsScales.Fit.class);}
    public static Bootstrap.Result bootstrap(JsonObject report){return report.getAsJsonObject("results").has("bootstrap")?ResamplingReports.read(report.getAsJsonObject("results").get("bootstrap")):null;}
}
