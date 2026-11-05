package org.brightstats.report;
import com.google.gson.*;
import org.brightstats.stats.*;
public final class ComponentCopulaReports {
    private ComponentCopulaReports(){}
    public static ComponentCopula.Fit point(JsonObject report){var object=report.getAsJsonObject("results").getAsJsonObject("componentCopula").deepCopy();var stage=object.getAsJsonObject("firstStage");if(stage.has("pls")&&!stage.get("pls").isJsonNull())NumericArchives.restorePls(stage.getAsJsonObject("pls"));if(stage.has("gsca")&&!stage.get("gsca").isJsonNull()){var fit=stage.getAsJsonObject("gsca");NumericArchives.restoreArray(fit.getAsJsonArray("rSquared"));for(var field:Gsca.Fit.class.getRecordComponents())if(field.getType()==double.class&&fit.has(field.getName())&&fit.get(field.getName()).isJsonNull())fit.addProperty(field.getName(),Double.NaN);}return NumericArchives.reader().fromJson(object,ComponentCopula.Fit.class);}
    public static Bootstrap.Result bootstrap(JsonObject report){return report.getAsJsonObject("results").has("bootstrap")?ResamplingReports.read(report.getAsJsonObject("results").getAsJsonObject("bootstrap")):null;}
}
