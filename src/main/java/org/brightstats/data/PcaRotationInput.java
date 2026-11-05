package org.brightstats.data;
import com.google.gson.*;
import org.brightstats.stats.PcaRotation;
public final class PcaRotationInput {
    private PcaRotationInput(){}
    public static PcaRotation.Options options(JsonElement value){if(value==null||!value.isJsonObject())throw new IllegalArgumentException("Rotation settings must be an object.");var object=value.getAsJsonObject();var defaults=PcaRotation.Options.defaults();
        for(String field:new String[]{"method","kaiserNormalization","tolerance"})if(object.has(field)){var supplied=object.get(field);if(!supplied.isJsonPrimitive()||field.equals("method")&&!supplied.getAsJsonPrimitive().isString()||field.equals("kaiserNormalization")&&!supplied.getAsJsonPrimitive().isBoolean()||field.equals("tolerance")&&!supplied.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid rotation field type: "+field);}
        return new PcaRotation.Options(object.has("method")?PcaRotation.Method.valueOf(object.get("method").getAsString()):defaults.method(),object.has("kaiserNormalization")?object.get("kaiserNormalization").getAsBoolean():defaults.kaiserNormalization(),object.has("maxIterations")?PlsModelInput.index(object.get("maxIterations")):defaults.maxIterations(),object.has("tolerance")?object.get("tolerance").getAsDouble():defaults.tolerance(),object.has("power")?PlsModelInput.index(object.get("power")):defaults.power());
    }
}
