package org.brightstats.report;

import com.google.gson.*;
import org.brightstats.stats.PathProcess;

public final class ProcessReports {
    private ProcessReports(){}
    public static PathProcess.Fit point(JsonObject archive){var numeric=archive.getAsJsonObject("results");JsonElement value=numeric.get("process").deepCopy();restore(value);return new GsonBuilder().serializeSpecialFloatingPointValues().create().fromJson(value,PathProcess.Fit.class);}
    private static void restore(JsonElement value){if(value.isJsonObject()){for(var entry:value.getAsJsonObject().entrySet())if(entry.getValue().isJsonNull())entry.setValue(new JsonPrimitive(Double.NaN));else restore(entry.getValue());}else if(value.isJsonArray()){var array=value.getAsJsonArray();for(int index=0;index<array.size();index++)if(array.get(index).isJsonNull())array.set(index,new JsonPrimitive(Double.NaN));else restore(array.get(index));}}
}
