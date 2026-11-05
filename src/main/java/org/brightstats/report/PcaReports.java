package org.brightstats.report;
import com.google.gson.*;
import org.brightstats.stats.PcaRotation;
public final class PcaReports {
    private PcaReports(){}
    public static PcaRotation.Fit rotation(JsonObject report){var object=report.getAsJsonObject("results").getAsJsonObject("pcaRotation").deepCopy();if(object.get("objective").isJsonNull())object.addProperty("objective",Double.NaN);return NumericArchives.reader().fromJson(object,PcaRotation.Fit.class);}
}
