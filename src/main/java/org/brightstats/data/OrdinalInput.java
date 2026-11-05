package org.brightstats.data;

import java.util.*;
import com.google.gson.*;
import org.brightstats.stats.OrdinalSem;

public final class OrdinalInput {
    private OrdinalInput(){}
    public static OrdinalSem.Options options(JsonElement value){var d=OrdinalSem.Options.defaults();if(value==null||value.isJsonNull())return d;var o=value.getAsJsonObject();for(String key:o.keySet())if(!Set.of("maxIterations","gradientTolerance","functionTolerance","confidence","minimumCategoryCount").contains(key))throw new IllegalArgumentException("Unsupported ordinal option: "+key);return new OrdinalSem.Options(o.has("maxIterations")?PlsModelInput.index(o.get("maxIterations")):d.maxIterations(),o.has("gradientTolerance")?o.get("gradientTolerance").getAsDouble():d.gradientTolerance(),o.has("functionTolerance")?o.get("functionTolerance").getAsDouble():d.functionTolerance(),o.has("confidence")?o.get("confidence").getAsDouble():d.confidence(),o.has("minimumCategoryCount")?PlsModelInput.index(o.get("minimumCategoryCount")):d.minimumCategoryCount());}
    public static List<String> names(JsonElement value){List<String> values=new ArrayList<>();for(var item:value.getAsJsonArray()){if(!item.isJsonPrimitive()||!item.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Ordinal identities/labels must be JSON strings.");values.add(item.getAsString());}return List.copyOf(values);}
    public static List<List<String>> levels(JsonElement value){return value.getAsJsonArray().asList().stream().map(OrdinalInput::names).toList();}
    public static int[][] codes(JsonElement value){var rows=value.getAsJsonArray();if(rows.size()>10000)throw new IllegalArgumentException("At most 10,000 ordinal rows are supported.");int[][] result=new int[rows.size()][];for(int i=0;i<result.length;i++){var row=rows.get(i).getAsJsonArray();if(row.size()>16)throw new IllegalArgumentException("At most 16 ordinal indicators are supported.");result[i]=new int[row.size()];for(int j=0;j<result[i].length;j++)result[i][j]=PlsModelInput.index(row.get(j));}return result;}
}
