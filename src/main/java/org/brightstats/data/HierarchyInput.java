package org.brightstats.data;
import java.util.*;import com.google.gson.*;import org.brightstats.stats.*;
public final class HierarchyInput {
 private HierarchyInput(){}
 public static HierarchyPipeline.Model model(JsonElement value){if(value==null||!value.isJsonObject())throw new IllegalArgumentException("Provide the complete hierarchy model.");var object=value.getAsJsonObject();for(String key:object.keySet())if(!Set.of("blocks","edges","options","stages").contains(key))throw new IllegalArgumentException("Unsupported hierarchy model field: "+key);List<HigherOrderPls.Specification> stages=new ArrayList<>();for(var stage:object.getAsJsonArray("stages"))stages.add(HigherOrderInput.specification(stage));return new HierarchyPipeline.Model(PlsModelInput.blocks(object.get("blocks")),PlsModelInput.edges(object.get("edges")),PlsModelInput.options(object.get("options")),stages);}
}
