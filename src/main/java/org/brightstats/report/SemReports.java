package org.brightstats.report;

import com.google.gson.*;
import org.brightstats.stats.CovarianceSem;

/** Rehydrate own SEM archives while preserving undefined doubles and nullable parameter bounds. */
public final class SemReports {
    private SemReports(){}
    public static CovarianceSem.Fit fit(JsonObject archive){
        ReportArchive.validate(archive);JsonObject results=archive.getAsJsonObject("results");if(!results.has("sem")||!results.get("sem").isJsonObject())throw new IllegalArgumentException("Choose a Bright-Stats SEM estimation report.");
        JsonObject serialized=results.getAsJsonObject("sem").deepCopy();if(!serialized.has("diagnostics")||!serialized.has("model")||!serialized.has("options"))throw new IllegalArgumentException("The SEM report lacks the required model/diagnostic specification.");
        for(var entry:serialized.entrySet())if(!entry.getKey().equals("model")&&!entry.getKey().equals("options"))entry.setValue(undefined(entry.getValue()));
        return new Gson().fromJson(serialized,CovarianceSem.Fit.class);
    }
    private static JsonElement undefined(JsonElement input){
        if(input.isJsonNull())return new JsonPrimitive(Double.NaN);
        if(input.isJsonArray()){JsonArray array=new JsonArray();for(var value:input.getAsJsonArray())array.add(undefined(value));return array;}
        if(input.isJsonObject()){JsonObject object=new JsonObject();for(var entry:input.getAsJsonObject().entrySet())object.add(entry.getKey(),undefined(entry.getValue()));return object;}
        return input.deepCopy();
    }
}
