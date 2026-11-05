package org.brightstats.data;
import java.util.*;
import com.google.gson.*;
import org.brightstats.stats.PlsSem;
/** Strict model schema for advanced PLS workflows; indices are zero based. */
public final class PlsModelInput {
    private PlsModelInput(){}
    public static int index(JsonElement value){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Model indices must be JSON integers.");return value.getAsBigDecimal().intValueExact();}
    public static int[] indices(JsonElement value){if(value==null||!value.isJsonArray())throw new IllegalArgumentException("Expected an index array.");int[] result=new int[value.getAsJsonArray().size()];for(int i=0;i<result.length;i++)result[i]=index(value.getAsJsonArray().get(i));return result;}
    public static List<PlsSem.Block> blocks(JsonElement value){if(value==null||!value.isJsonArray())throw new IllegalArgumentException("Provide indicator blocks.");List<PlsSem.Block> blocks=new ArrayList<>();
        for(var entry:value.getAsJsonArray()){var object=entry.getAsJsonObject();var name=object.get("name");if(name==null||!name.isJsonPrimitive()||!name.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Block names must be strings.");
            int[] columns=indices(object.get("columns"));if(Arrays.stream(columns).distinct().count()!=columns.length)throw new IllegalArgumentException("Duplicate indicator in a block.");var mode=object.has("mode")?PlsSem.Mode.valueOf(object.get("mode").getAsString()):PlsSem.Mode.A;double[] initial=null;
            if(object.has("initialWeights")&&!object.get("initialWeights").isJsonNull()){var weights=object.getAsJsonArray("initialWeights");initial=new double[weights.size()];for(int i=0;i<initial.length;i++)initial[i]=weights.get(i).getAsDouble();}
            var type=object.has("measurementType")?PlsSem.MeasurementType.valueOf(object.get("measurementType").getAsString()):mode==PlsSem.Mode.A?PlsSem.MeasurementType.REFLECTIVE:PlsSem.MeasurementType.FORMATIVE;blocks.add(new PlsSem.Block(name.getAsString(),columns,mode,initial,type));}return List.copyOf(blocks);}
    public static List<PlsSem.Edge> edges(JsonElement value){List<PlsSem.Edge> edges=new ArrayList<>();if(value!=null&&!value.isJsonNull())for(var entry:value.getAsJsonArray()){var object=entry.getAsJsonObject();edges.add(new PlsSem.Edge(index(object.get("source")),index(object.get("target"))));}return List.copyOf(edges);}
    public static PlsSem.Options options(JsonElement value){if(value==null||value.isJsonNull())return PlsSem.Options.defaults();var object=value.getAsJsonObject();var defaults=PlsSem.Options.defaults();return new PlsSem.Options(object.has("maxIterations")?index(object.get("maxIterations")):defaults.maxIterations(),object.has("stopCriterion")?object.get("stopCriterion").getAsDouble():defaults.stopCriterion(),object.has("weighting")?PlsSem.Weighting.valueOf(object.get("weighting").getAsString()):defaults.weighting());}
}
