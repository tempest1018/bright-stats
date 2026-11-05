package org.brightstats.data;
import java.util.*;
import com.google.gson.*;
import org.brightstats.stats.CopulaRegression;
/** Strict integer/string schema for independent copula specifications. Indices are zero based. */
public final class CopulaInput {
 private CopulaInput(){}
 public static int index(JsonElement value){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Copula indices must be JSON integers.");return value.getAsBigDecimal().intValueExact();}
 public static int[] indices(JsonElement value){if(value==null||!value.isJsonArray())throw new IllegalArgumentException("Copula indices must be an array.");int[] result=new int[value.getAsJsonArray().size()];for(int i=0;i<result.length;i++)result[i]=index(value.getAsJsonArray().get(i));return result;}
 public static CopulaRegression.Model model(JsonElement value){if(value==null||!value.isJsonObject())throw new IllegalArgumentException("Provide a copula model object.");var object=value.getAsJsonObject();var names=new ArrayList<String>();if(!object.has("names")||!object.get("names").isJsonArray())throw new IllegalArgumentException("Copula model needs predictor names.");for(var name:object.getAsJsonArray("names")){if(!name.isJsonPrimitive()||!name.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Predictor names must be strings.");names.add(name.getAsString());}return new CopulaRegression.Model(names,indices(object.get("endogenous")),object.has("method")?CopulaRegression.Method.valueOf(object.get("method").getAsString()):CopulaRegression.Method.ORIGINAL_GAUSSIAN,object.has("plottingPosition")?CopulaRegression.PlottingPosition.valueOf(object.get("plottingPosition").getAsString()):CopulaRegression.PlottingPosition.WEIBULL);}
}
