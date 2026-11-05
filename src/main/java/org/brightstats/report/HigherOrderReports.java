package org.brightstats.report;
import com.google.gson.*;
import org.brightstats.data.*;
import org.brightstats.stats.*;
public final class HigherOrderReports {
    private HigherOrderReports(){}
    public static HigherOrderPls.Fit point(JsonObject report){var fit=report.getAsJsonObject("results").getAsJsonObject("higherOrderPls").deepCopy();int width=PlsModelInput.index(fit.get("originalColumns"));PlsModelInput.index(fit.get("observations"));var first=PlsModelInput.blocks(fit.get("firstStageBlocks"));var edges=PlsModelInput.edges(fit.get("firstStageEdges"));var specification=HigherOrderInput.specification(fit.get("specification"));HigherOrderPls.validate(width,first,edges,specification);fit.add("specification",new Gson().toJsonTree(specification));fit.add("firstStageBlocks",new Gson().toJsonTree(first));fit.add("firstStageEdges",new Gson().toJsonTree(edges));fit.add("secondStageBlocks",new Gson().toJsonTree(PlsModelInput.blocks(fit.get("secondStageBlocks"))));fit.add("firstStageOptions",new Gson().toJsonTree(PlsModelInput.options(fit.get("firstStageOptions"))));NumericArchives.restorePls(fit.getAsJsonObject("firstStage"));NumericArchives.restorePls(fit.getAsJsonObject("secondStage"));return NumericArchives.reader().fromJson(fit,HigherOrderPls.Fit.class);}
    public static Bootstrap.Result bootstrap(JsonObject report){var results=report.getAsJsonObject("results");return results.has("bootstrap")?ResamplingReports.read(results.get("bootstrap")):null;}
}
