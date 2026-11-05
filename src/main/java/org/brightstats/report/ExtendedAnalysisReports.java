package org.brightstats.report;
import com.google.gson.*;import org.brightstats.stats.*;
public final class ExtendedAnalysisReports {
 private ExtendedAnalysisReports(){}
 private static <T>T read(JsonObject report,String key,Class<T> type){ReportArchive.validate(report);return NumericArchives.reader().fromJson(NumericArchives.restoreRecord(report.getAsJsonObject("results").get(key),type),type);}
 public static SemSensitivity.Influence influence(JsonObject report){return read(report,"semInfluence",SemSensitivity.Influence.class);}
 public static SemSensitivity.Multiverse multiverse(JsonObject report){return read(report,"semMultiverse",SemSensitivity.Multiverse.class);}
 public static BayesianSem.Fit bayesian(JsonObject report){return read(report,"bayesianSem",BayesianSem.Fit.class);}
 public static RegularizedSem.Fit regularized(JsonObject report){return read(report,"regularizedSem",RegularizedSem.Fit.class);}
 public static ClusterSem.Fit cluster(JsonObject report){return read(report,"clusterSem",ClusterSem.Fit.class);}
 public static TwoLevelSem.Fit twoLevel(JsonObject report){return read(report,"twoLevelSem",TwoLevelSem.Fit.class);}
 public static SemSimulation.Result simulation(JsonObject report){return read(report,"simulation",SemSimulation.Result.class);}
 public static OrdinalSem.Fit ordinal(JsonObject report){return read(report,"ordinalSem",OrdinalSem.Fit.class);}
 public static ImputedSem.Fit imputed(JsonObject report){return read(report,"imputedSem",ImputedSem.Fit.class);}
 public static FimlSem.Fit fiml(JsonObject report){return read(report,"fiml",FimlSem.Fit.class);}
 public static LatentInteractionSem.Fit latent(JsonObject report){return read(report,"latentLikelihood",LatentInteractionSem.Fit.class);}
 public static HierarchyPipeline.Fit composite(JsonObject report){return read(report,"hierarchyPipeline",HierarchyPipeline.Fit.class);}
 public static FactorHierarchy.Fit factors(JsonObject report){return read(report,"factorHierarchy",FactorHierarchy.Fit.class);}
 public static Bootstrap.Result bootstrap(JsonObject report){return report.getAsJsonObject("results").has("bootstrap")?ResamplingReports.read(report.getAsJsonObject("results").get("bootstrap")):null;}
}
