package org.brightstats.data;
import com.google.gson.*;
import org.brightstats.stats.*;
public final class ModelComparisonInput {
    private ModelComparisonInput(){}
    public static ModelComparison.Model model(JsonElement value){if(value==null||!value.isJsonObject())throw new IllegalArgumentException("Provide first and second model objects.");var object=value.getAsJsonObject();var name=object.get("name");if(name==null||!name.isJsonPrimitive()||!name.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Candidate names must be strings.");return new ModelComparison.Model(name.getAsString(),PlsModelInput.blocks(object.get("blocks")),PlsModelInput.edges(object.get("edges")),PlsModelInput.options(object.get("options")));}
    public static Prediction.Options validation(JsonElement value){if(value==null||value.isJsonNull())return Prediction.Options.defaults();var object=value.getAsJsonObject();var d=Prediction.Options.defaults();return new Prediction.Options(object.has("scheme")?Prediction.Scheme.valueOf(object.get("scheme").getAsString()):d.scheme(),object.has("folds")?PlsModelInput.index(object.get("folds")):d.folds(),object.has("repeats")?PlsModelInput.index(object.get("repeats")):d.repeats(),object.has("testFraction")?object.get("testFraction").getAsDouble():d.testFraction(),object.has("seed")?object.get("seed").getAsBigDecimal().longValueExact():d.seed());}
}
