package org.brightstats.report;
import com.google.gson.*;
import org.brightstats.stats.PredictionSegmentation;
public final class SegmentationReports {
    private SegmentationReports(){}
    public static PredictionSegmentation.Result point(JsonObject report){JsonElement value=report.getAsJsonObject("results").get("pos").deepCopy();restore(value);return new Gson().fromJson(value,PredictionSegmentation.Result.class);}
    private static void restore(JsonElement value){if(value.isJsonArray()){JsonArray array=value.getAsJsonArray();for(int i=0;i<array.size();i++){if(array.get(i).isJsonNull())array.set(i,new JsonPrimitive(Double.NaN));else restore(array.get(i));}}else if(value.isJsonObject())for(var entry:value.getAsJsonObject().entrySet())if(!entry.getValue().isJsonNull())restore(entry.getValue());}
}
