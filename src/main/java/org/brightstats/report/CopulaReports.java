package org.brightstats.report;
import com.google.gson.*;
import org.brightstats.data.CopulaInput;
import org.brightstats.stats.CopulaRegression;
public final class CopulaReports {
 private CopulaReports(){}
 public static CopulaRegression.Fit point(JsonObject report){var results=report.getAsJsonObject("results");if(results==null||!results.has("copula"))throw new IllegalArgumentException("No archived copula result.");var fit=results.getAsJsonObject("copula").deepCopy();fit.add("model",new Gson().toJsonTree(CopulaInput.model(fit.get("model"))));return new Gson().fromJson(fit,CopulaRegression.Fit.class);}
}
