package org.brightstats.report;

import com.google.gson.*;
import org.brightstats.stats.ImportancePerformance;

public final class IpmaReports {
    private IpmaReports(){}
    public static ImportancePerformance.Result point(JsonObject archive){JsonElement value=archive.getAsJsonObject("results").get("ipma").deepCopy();restore(value);return new GsonBuilder().serializeSpecialFloatingPointValues().create().fromJson(value,ImportancePerformance.Result.class);}
    private static void restore(JsonElement value){if(value.isJsonObject()){var object=value.getAsJsonObject();for(var entry:object.entrySet())if(entry.getValue().isJsonNull())entry.setValue(new JsonPrimitive(Double.NaN));else restore(entry.getValue());}else if(value.isJsonArray()){var array=value.getAsJsonArray();for(int j=0;j<array.size();j++)if(array.get(j).isJsonNull())array.set(j,new JsonPrimitive(Double.NaN));else restore(array.get(j));}}
}
