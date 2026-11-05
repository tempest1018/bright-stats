package org.brightstats.data;

import java.util.*;
import com.google.gson.*;
import org.brightstats.stats.*;

public final class ImputationInput {
    private ImputationInput(){}
    private static JsonObject object(JsonElement value,Set<String> allowed){if(value==null||value.isJsonNull())return new JsonObject();if(!value.isJsonObject())throw new IllegalArgumentException("Expected imputation options object.");var object=value.getAsJsonObject();for(String key:object.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unsupported imputation option: "+key);return object;}
    public static GaussianImputation.Options imputation(JsonElement value){var o=object(value,Set.of("imputations","burnIterations","seed"));var d=GaussianImputation.Options.defaults();return new GaussianImputation.Options(o.has("imputations")?PlsModelInput.index(o.get("imputations")):d.imputations(),o.has("burnIterations")?PlsModelInput.index(o.get("burnIterations")):d.burnIterations(),o.has("seed")?o.get("seed").getAsBigDecimal().longValueExact():d.seed());}
    public static ImputedSem.Options options(JsonElement value){var o=object(value,Set.of("imputation","analysis","completeDegreesOfFreedom","sandwichCovariance"));var a=object(o.get("analysis"),Set.of("maxIterations","gradientTolerance","functionTolerance","confidence"));var d=FimlSem.Options.defaults();boolean sandwich=false;if(o.has("sandwichCovariance")){if(!o.get("sandwichCovariance").isJsonPrimitive()||!o.getAsJsonPrimitive("sandwichCovariance").isBoolean())throw new IllegalArgumentException("Declare sandwichCovariance as a JSON boolean.");sandwich=o.get("sandwichCovariance").getAsBoolean();}return new ImputedSem.Options(imputation(o.get("imputation")),new FimlSem.Options(a.has("maxIterations")?PlsModelInput.index(a.get("maxIterations")):d.maxIterations(),a.has("gradientTolerance")?a.get("gradientTolerance").getAsDouble():d.gradientTolerance(),a.has("functionTolerance")?a.get("functionTolerance").getAsDouble():d.functionTolerance(),a.has("confidence")?a.get("confidence").getAsDouble():d.confidence()),o.has("completeDegreesOfFreedom")&&!o.get("completeDegreesOfFreedom").isJsonNull()?o.get("completeDegreesOfFreedom").getAsDouble():null,sandwich);}
}
