package org.brightstats.report;
import com.google.gson.*;
import org.brightstats.data.*;
import org.brightstats.stats.*;
public final class NonlinearReports {
    private NonlinearReports(){}

    public static NonlinearPls.Fit point(JsonObject report){var fit=report.getAsJsonObject("results").getAsJsonObject("nonlinearPls").deepCopy();fit.get("observations").getAsBigDecimal().intValueExact();fit.add("specification",new Gson().toJsonTree(NonlinearInput.specification(fit.get("specification"))));fit.add("options",new Gson().toJsonTree(PlsModelInput.options(fit.get("options"))));fit.add("mainEffects",new Gson().toJsonTree(PlsModelInput.edges(fit.get("mainEffects"))));for(var entry:fit.getAsJsonArray("equations")){var equation=entry.getAsJsonObject();PlsModelInput.index(equation.get("target"));PlsModelInput.index(equation.get("residualDegreesOfFreedom"));for(var term:equation.getAsJsonArray("regressors"))PlsModelInput.indices(term.getAsJsonObject().get("factors"));}NumericArchives.restorePls(fit.getAsJsonObject("firstStage"));return NumericArchives.reader().fromJson(fit,NonlinearPls.Fit.class);}
    public static Bootstrap.Result bootstrap(JsonObject report){var results=report.getAsJsonObject("results");return results.has("bootstrap")?ResamplingReports.read(results.get("bootstrap")):null;}
}
