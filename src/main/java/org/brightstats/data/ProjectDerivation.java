package org.brightstats.data;
import com.google.gson.*;
public final class ProjectDerivation {
    private ProjectDerivation(){}
    public static final String WARNING="This editable stage-two project treats exported first-stage scores as fixed. Its later analyses omit first-stage estimation uncertainty; use the original higher-order workflow for full two-stage resampling.";
    public static JsonObject validate(JsonElement value,int columns){if(value==null||value.isJsonNull())return null;var object=value.getAsJsonObject();if(!object.get("format").getAsString().equals("bright-stats-derived-data")||PlsModelInput.index(object.get("formatVersion"))!=1||!object.get("kind").getAsString().equals("higher-order-stage-two")||!object.get("sourceSha256").getAsString().matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Unsupported or invalid data-derivation metadata.");int first=PlsModelInput.index(object.get("scoreColumnStart")),count=PlsModelInput.index(object.get("scoreColumns"));if(first<1||count<2||count>64||(long)first+count>columns)throw new IllegalArgumentException("Derived score-column metadata differ from the dataset.");return object.deepCopy();}
}
