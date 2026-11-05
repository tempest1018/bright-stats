package org.brightstats.data;

import java.util.*;
import com.google.gson.*;
import org.brightstats.stats.PathProcess;

/** Strict index parsing shared by saved projects, the desktop and command interface. */
public final class ProcessInput {
    private ProcessInput(){}
    private static int integer(JsonElement value){return value.getAsBigDecimal().intValueExact();}
    public static PathProcess.Model model(JsonElement supplied){var object=supplied.getAsJsonObject();List<PathProcess.Path> paths=new ArrayList<>();for(var value:object.getAsJsonArray("paths")){var path=value.getAsJsonObject();var modifiers=path.has("moderators")?path.getAsJsonArray("moderators"):new JsonArray();int[] selected=new int[modifiers.size()];for(int j=0;j<selected.length;j++)selected[j]=integer(modifiers.get(j));paths.add(new PathProcess.Path(integer(path.get("source")),integer(path.get("target")),selected));}var controls=object.has("controls")?object.getAsJsonArray("controls"):new JsonArray();int[] selected=new int[controls.size()];for(int j=0;j<selected.length;j++)selected[j]=integer(controls.get(j));return new PathProcess.Model(integer(object.get("source")),integer(object.get("target")),paths,selected);}
    public static List<PathProcess.Probe> probes(JsonElement supplied){if(supplied==null)return List.of();List<PathProcess.Probe> probes=new ArrayList<>();for(var value:supplied.getAsJsonArray()){var object=value.getAsJsonObject();Map<Integer,Double> conditions=new LinkedHashMap<>();for(var entry:object.getAsJsonObject("values").entrySet()){int column=Integer.parseInt(entry.getKey());if(conditions.put(column,entry.getValue().getAsDouble())!=null)throw new IllegalArgumentException("Duplicate moderator probe index.");}probes.add(new PathProcess.Probe(object.get("name").getAsString(),conditions));}return List.copyOf(probes);}
}
