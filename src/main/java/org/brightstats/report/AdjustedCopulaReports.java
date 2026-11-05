package org.brightstats.report;
import java.util.*;
import com.google.gson.*;
import org.brightstats.stats.*;
public final class AdjustedCopulaReports {
    private AdjustedCopulaReports(){}
    public static AdjustedCopula.Fit observed(JsonObject report){return NumericArchives.reader().fromJson(NumericArchives.restoreRecord(report.getAsJsonObject("results").get("adjustedCopula"),AdjustedCopula.Fit.class),AdjustedCopula.Fit.class);}
    public static ComponentAdjustedCopula.Fit component(JsonObject report){return NumericArchives.reader().fromJson(NumericArchives.restoreRecord(report.getAsJsonObject("results").get("componentAdjustedCopula"),ComponentAdjustedCopula.Fit.class),ComponentAdjustedCopula.Fit.class);}
    public static Bootstrap.Result bootstrap(JsonObject report){return report.getAsJsonObject("results").has("bootstrap")?ResamplingReports.read(report.getAsJsonObject("results").get("bootstrap")):null;}
    public static List<CopulaWald.Result> wald(JsonObject report){List<CopulaWald.Result> out=new ArrayList<>();if(report.getAsJsonObject("results").has("wald"))for(var value:report.getAsJsonObject("results").getAsJsonArray("wald"))out.add(NumericArchives.reader().fromJson(value,CopulaWald.Result.class));return List.copyOf(out);}
}
