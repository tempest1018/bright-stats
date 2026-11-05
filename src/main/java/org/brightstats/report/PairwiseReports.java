package org.brightstats.report;
import com.google.gson.*;
import org.brightstats.stats.*;
public final class PairwiseReports {
 private PairwiseReports(){}
 public static <T> T read(JsonObject report,String key,Class<T> type){ReportArchive.validate(report);return NumericArchives.reader().fromJson(NumericArchives.restoreRecord(report.getAsJsonObject("results").get(key),type),type);}
 public static Bootstrap.Result bootstrap(JsonObject report){return report.getAsJsonObject("results").has("bootstrap")?ResamplingReports.read(report.getAsJsonObject("results").get("bootstrap")):null;}
}
