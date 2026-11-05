package org.brightstats.report;

import com.google.gson.*;
import org.brightstats.stats.MultiGroupSem;

/** Restore numeric NA values while retaining nullable input specifications and parameter bounds. */
public final class MultiGroupSemReports {
    private MultiGroupSemReports(){}
    public static MultiGroupSem.Fit fit(JsonObject archive){ReportArchive.validate(archive);var results=archive.getAsJsonObject("results");if(!results.has("multiGroupSem")||!results.get("multiGroupSem").isJsonObject())throw new IllegalArgumentException("Choose a saved joint CB-SEM estimation report, not a sequence or another estimator.");return new Gson().fromJson(undefined(results.get("multiGroupSem")),MultiGroupSem.Fit.class);}
    private static JsonElement undefined(JsonElement input){if(input.isJsonNull())return new JsonPrimitive(Double.NaN);if(input.isJsonArray()){JsonArray values=new JsonArray();for(var value:input.getAsJsonArray())values.add(undefined(value));return values;}if(input.isJsonObject()){JsonObject values=new JsonObject();for(var entry:input.getAsJsonObject().entrySet())values.add(entry.getKey(),entry.getKey().equals("options")||entry.getKey().equals("parameters")||entry.getKey().equals("input")?entry.getValue().deepCopy():undefined(entry.getValue()));return values;}return input.deepCopy();}
}
