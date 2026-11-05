package org.brightstats.report;
import com.google.gson.*;
import org.brightstats.stats.*;
/** Missing numeric estimates remain unavailable when an archived comparison is reopened. */
public final class ModelComparisonReports {
    private ModelComparisonReports(){}
    private static void numeric(JsonObject object,Class<?> record){for(var component:record.getRecordComponents())if(component.getType()==double.class&&object.has(component.getName())&&object.get(component.getName()).isJsonNull())object.addProperty(component.getName(),Double.NaN);}
    public static ModelComparison.Result point(JsonObject report){var object=report.getAsJsonObject("results").getAsJsonObject("modelComparison").deepCopy();
        for(String name:new String[]{"first","second"}){var candidate=object.getAsJsonObject(name);NumericArchives.restorePls(candidate.getAsJsonObject("fit"));var prediction=candidate.getAsJsonObject("prediction");for(String metrics:new String[]{"metrics","foldMetrics"})for(var metric:prediction.getAsJsonArray(metrics))numeric(metric.getAsJsonObject(),Prediction.Metric.class);for(var row:prediction.getAsJsonArray("predictions"))numeric(row.getAsJsonObject(),Prediction.Row.class);}
        for(var loss:object.getAsJsonArray("losses")){numeric(loss.getAsJsonObject(),ModelComparison.Loss.class);numeric(loss.getAsJsonObject().getAsJsonObject("test"),PairedLossTest.Result.class);}for(var criterion:object.getAsJsonArray("criteria"))numeric(criterion.getAsJsonObject(),ModelComparison.Criterion.class);
        return NumericArchives.reader().fromJson(object,ModelComparison.Result.class);
    }
}
