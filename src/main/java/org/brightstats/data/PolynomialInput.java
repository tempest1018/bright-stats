package org.brightstats.data;
import java.util.*;
import com.google.gson.*;
import org.brightstats.stats.*;
public final class PolynomialInput {
    private PolynomialInput(){}
    public static ObservedPolynomial.Model observed(JsonElement value){if(value==null||!value.isJsonObject())throw new IllegalArgumentException("Provide an observed polynomial model.");var object=value.getAsJsonObject();List<String> names=new ArrayList<>();for(var name:object.getAsJsonArray("names")){if(!name.isJsonPrimitive()||!name.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Variable names must be text.");names.add(name.getAsString());}var request=new JsonObject();request.add("extensions",object.get("extensions"));return new ObservedPolynomial.Model(names,PlsModelInput.indices(object.get("columns")),PlsModelInput.edges(object.get("paths")),NonlinearInput.specification(request).extensions(),PlsModelInput.index(object.get("source")),PlsModelInput.index(object.get("target")),object.has("controls")?PlsModelInput.indices(object.get("controls")):new int[0]);}
    public static PathProcess.Options options(JsonElement value){if(value==null||value.isJsonNull())return PathProcess.Options.defaults();var object=value.getAsJsonObject();var defaults=PathProcess.Options.defaults();return new PathProcess.Options(object.has("metric")?PathProcess.Metric.valueOf(object.get("metric").getAsString()):defaults.metric(),object.has("covariance")?RobustRegression.Covariance.valueOf(object.get("covariance").getAsString()):defaults.covariance(),object.has("confidence")?object.get("confidence").getAsDouble():defaults.confidence(),!object.has("considerControls")||object.get("considerControls").getAsBoolean());}
}
