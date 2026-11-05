package org.brightstats.report;

import com.google.gson.*;
import org.brightstats.stats.PlsMixture;

public final class MixtureReports {
    private MixtureReports(){}
    public static PlsMixture.Result point(JsonObject archive){var value=archive.getAsJsonObject("results").get("fimix").deepCopy();restore(value);return new GsonBuilder().serializeSpecialFloatingPointValues().create().fromJson(value,PlsMixture.Result.class);}
    private static void restore(JsonElement value){if(value.isJsonObject()){for(var entry:value.getAsJsonObject().entrySet())if(entry.getValue().isJsonNull())entry.setValue(new JsonPrimitive(Double.NaN));else restore(entry.getValue());}else if(value.isJsonArray()){var array=value.getAsJsonArray();for(int j=0;j<array.size();j++)if(array.get(j).isJsonNull())array.set(j,new JsonPrimitive(Double.NaN));else restore(array.get(j));}}
}
