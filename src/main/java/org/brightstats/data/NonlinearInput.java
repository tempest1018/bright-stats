package org.brightstats.data;
import java.util.*;
import com.google.gson.*;
import org.brightstats.stats.NonlinearPls;
public final class NonlinearInput {
    private NonlinearInput(){}
    public static NonlinearPls.Specification specification(JsonElement value){if(value==null||value.isJsonNull())return new NonlinearPls.Specification(List.of(),List.of(),null);var object=value.getAsJsonObject();List<NonlinearPls.Extension> extensions=new ArrayList<>();if(object.has("extensions"))for(var entry:object.getAsJsonArray("extensions")){var term=entry.getAsJsonObject();extensions.add(new NonlinearPls.Extension(PlsModelInput.index(term.get("target")),PlsModelInput.indices(term.get("factors"))));}
        NonlinearPls.Grid grid=null;if(object.has("grid")&&!object.get("grid").isJsonNull()){var values=object.getAsJsonObject("grid");grid=new NonlinearPls.Grid(PlsModelInput.index(values.get("source")),PlsModelInput.index(values.get("target")),values.get("lower").getAsDouble(),values.get("upper").getAsDouble(),values.has("points")?PlsModelInput.index(values.get("points")):41);}
        return new NonlinearPls.Specification(extensions,object.has("probes")?ProcessInput.probes(object.get("probes")):List.of(),grid);}
}
