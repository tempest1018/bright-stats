package org.brightstats.report;
import com.google.gson.*;
import org.brightstats.stats.ModelPca;
public final class ModelPcaReports {
    private ModelPcaReports(){}
    public static ModelPca.Fit point(JsonObject report){var object=report.getAsJsonObject("results").getAsJsonObject("modelPca").deepCopy();for(var item:object.getAsJsonArray("groups")){var group=item.getAsJsonObject();if(group.has("rotation")&&!group.get("rotation").isJsonNull()){var rotation=group.getAsJsonObject("rotation");if(rotation.get("objective").isJsonNull())rotation.addProperty("objective",Double.NaN);}}return NumericArchives.reader().fromJson(object,ModelPca.Fit.class);}
}
