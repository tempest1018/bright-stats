package org.brightstats.data;
import java.util.*;import com.google.gson.*;import org.brightstats.stats.*;
public final class ClusterInput {
 private ClusterInput(){}
 public static List<String> ids(JsonElement value){if(value==null||!value.isJsonArray())throw new IllegalArgumentException("Provide original-row cluster IDs as a JSON array of strings.");List<String> labels=new ArrayList<>();for(var cell:value.getAsJsonArray())labels.add(StatisticalInput.text(cell));return List.copyOf(labels);}
 public static FimlSem.Options estimator(JsonElement value){var o=StatisticalInput.object(value,Set.of("maxIterations","gradientTolerance","functionTolerance","confidence"),"cluster estimator",true);var d=FimlSem.Options.defaults();return new FimlSem.Options(StatisticalInput.integer(o,"maxIterations",d.maxIterations()),StatisticalInput.number(o,"gradientTolerance",d.gradientTolerance()),StatisticalInput.number(o,"functionTolerance",d.functionTolerance()),StatisticalInput.number(o,"confidence",d.confidence()));}
 public static ClusterSem.Options options(JsonElement value){var o=StatisticalInput.object(value,Set.of("estimator","smallSampleCorrection","bootstrapReplicates","seed"),"cluster options",true);return new ClusterSem.Options(estimator(o.get("estimator")),StatisticalInput.bool(o,"smallSampleCorrection",true),StatisticalInput.integer(o,"bootstrapReplicates",0),StatisticalInput.integer64(o,"seed",42));}
 public static TwoLevelSem.Specification specification(JsonElement value){var o=StatisticalInput.object(value,Set.of("within","between"),"two-level specification",false);return new TwoLevelSem.Specification(SemModelInput.model(o.get("within")),SemModelInput.model(o.get("between")));}
}
