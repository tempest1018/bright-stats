package org.brightstats.report;

import com.google.gson.*;
import org.brightstats.stats.Nca;

public final class NcaReports {
    private NcaReports(){}
    public static Nca.Fit point(JsonObject report){ReportArchive.validate(report);JsonObject saved=report.getAsJsonObject("results").getAsJsonObject("nca").deepCopy();for(var entry:saved.entrySet())if(!entry.getKey().equals("options"))entry.setValue(undefined(entry.getValue()));return new Gson().fromJson(saved,Nca.Fit.class);}
    private static JsonElement undefined(JsonElement input){if(input.isJsonNull())return new JsonPrimitive(Double.NaN);if(input.isJsonArray()){JsonArray values=new JsonArray();for(var value:input.getAsJsonArray())values.add(undefined(value));return values;}if(input.isJsonObject()){JsonObject values=new JsonObject();for(var entry:input.getAsJsonObject().entrySet())values.add(entry.getKey(),undefined(entry.getValue()));return values;}return input.deepCopy();}
}
