package org.brightstats.report;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;

/** Bright-Stats' own offline report format; no dependency on vendor report files. */
public final class ReportArchive {
    private static final Gson JSON = new GsonBuilder().serializeNulls().setPrettyPrinting().setStrictness(Strictness.STRICT).create();
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private ReportArchive() {}

    public static JsonObject create(String estimator, JsonObject project, JsonElement results,
                                    JsonArray tables, List<String> warnings) {
        JsonObject report = new JsonObject();
        report.addProperty("format", "bright-stats-report"); report.addProperty("formatVersion", 1);
        report.addProperty("applicationVersion", org.brightstats.cli.Main.VERSION);
        report.addProperty("createdUtc", Instant.now().toString()); report.addProperty("estimator", estimator);
        report.addProperty("nonfiniteEncoding", "Undefined numeric results are null; infinite results use the strings Infinity and -Infinity.");
        report.add("project", clean(project)); report.add("results", clean(results));
        JsonObject settings = new JsonObject();
        if (estimator.equals("OLS")) {
            settings.addProperty("intercept", true);
            for (String key : List.of("response", "predictors")) {
                if (results.getAsJsonObject().has(key)) settings.add(key, clean(results.getAsJsonObject().get(key)));
            }
            if(results.getAsJsonObject().has("inference")) {
                var inference=clean(results.getAsJsonObject().get("inference")).getAsJsonObject();
                settings.add("confidence",inference.get("confidence")); settings.add("inferenceMethod",inference.get("method"));
            }
        } else if (estimator.equals("PLS-SEM") && project.has("options")) {
            settings = clean(project.get("options")).getAsJsonObject();
        }
        if(results.getAsJsonObject().has("bootstrap"))settings.add("bootstrap",clean(results.getAsJsonObject().getAsJsonObject("bootstrap").get("options")));
        if(results.getAsJsonObject().has("predictiveTests"))settings.add("predictiveInferenceConfidence",clean(results.getAsJsonObject().getAsJsonObject("predictiveTests").get("confidence")));
        if(results.getAsJsonObject().has("pos")){var fitted=results.getAsJsonObject().getAsJsonObject("pos");settings.add("pos",clean(fitted.get("options")));settings.add("model",clean(fitted.get("modelOptions")));}
        if(results.getAsJsonObject().has("fimix")){var fitted=results.getAsJsonObject().getAsJsonObject("fimix");settings.add("fimix",clean(fitted.getAsJsonObject("mixture").get("options")));settings.add("model",clean(fitted.get("modelOptions")));settings.add("originalScoreUnits",clean(fitted.get("originalUnits")));}
        if(results.getAsJsonObject().has("process")){var point=results.getAsJsonObject().getAsJsonObject("process");settings.add("process",clean(point.get("options")));settings.add("processModel",clean(point.get("model")));settings.add("processProbes",clean(point.get("probes")));}
        if(results.getAsJsonObject().has("tetrads"))settings.add("tetrads",clean(results.getAsJsonObject().getAsJsonObject("tetrads").get("options")));
        if(results.getAsJsonObject().has("ipma")){settings.add("ipma",clean(results.getAsJsonObject().getAsJsonObject("ipma").get("options")));settings.add("model",clean(results.getAsJsonObject().getAsJsonObject("ipma").get("modelOptions")));}
        if(results.getAsJsonObject().has("nca"))settings.add("nca",clean(results.getAsJsonObject().getAsJsonObject("nca").get("options")));
        if(results.getAsJsonObject().has("ncaPermutation"))settings.add("ncaPermutation",clean(results.getAsJsonObject().getAsJsonObject("ncaPermutation").get("options")));
        for(String key:List.of("groupPermutation","groupBootstrap"))if(results.getAsJsonObject().has(key)){var calculation=results.getAsJsonObject().getAsJsonObject(key);settings.add(key,clean(calculation.get("options")));settings.add("groups",clean(calculation.getAsJsonObject("point").get("selection")));settings.add("consistent",clean(calculation.getAsJsonObject("point").get("consistent")));}
        if(results.getAsJsonObject().has("multiGroupSem"))settings.add("jointSEM",clean(results.getAsJsonObject().getAsJsonObject("multiGroupSem").get("options")));
        if(results.getAsJsonObject().has("semInvariance")&&project.has("semGroupOptions")){settings.add("invariance",clean(project.get("semGroupOptions")));if(project.has("semOptions"))settings.add("jointSEM",clean(project.get("semOptions")));}
        if(results.getAsJsonObject().has("prediction")) {
            settings.add("prediction",clean(results.getAsJsonObject().getAsJsonObject("prediction").get("options")));
            if(project.has("options"))settings.add("model",clean(project.get("options")));
        }
        if(results.getAsJsonObject().has("copula")){settings.add("copula",clean(results.getAsJsonObject().getAsJsonObject("copula").get("model")));if(project.has("copulaOptions"))settings.add("copulaSelection",clean(project.get("copulaOptions")));}
        if(results.getAsJsonObject().has("nonlinearPls")){var result=results.getAsJsonObject().getAsJsonObject("nonlinearPls");settings.add("nonlinearSpecification",clean(result.get("specification")));settings.add("mainEffects",clean(result.get("mainEffects")));settings.add("firstStageOptions",clean(result.get("options")));if(project.has("nonlinearOptions"))settings.add("nonlinearSelection",clean(project.get("nonlinearOptions")));}
        if(results.getAsJsonObject().has("higherOrderPls")){var result=results.getAsJsonObject().getAsJsonObject("higherOrderPls");settings.add("higherOrderSpecification",clean(result.get("specification")));settings.add("firstStageOptions",clean(result.get("firstStageOptions")));settings.add("firstStageEdges",clean(result.get("firstStageEdges")));if(project.has("higherOrderOptions"))settings.add("higherOrderSelection",clean(project.get("higherOrderOptions")));}
        if(results.getAsJsonObject().has("latentLikelihood")){var fitted=results.getAsJsonObject().getAsJsonObject("latentLikelihood");settings.add("latentLikelihoodModel",clean(fitted.get("model")));settings.add("latentLikelihoodOptions",clean(fitted.get("options")));}
        for(String key:List.of("hierarchyPipeline","factorHierarchy"))if(results.getAsJsonObject().has(key))settings.add(key+"Model",clean(results.getAsJsonObject().getAsJsonObject(key).get("model")));
        if(results.getAsJsonObject().has("fiml")){var fitted=results.getAsJsonObject().getAsJsonObject("fiml");settings.add("fimlOptions",clean(fitted.get("options")));settings.add("fimlModel",clean(fitted.get("model")));}
        if(results.getAsJsonObject().has("imputedSem")){var fitted=results.getAsJsonObject().getAsJsonObject("imputedSem");settings.add("imputationOptions",clean(fitted.get("options")));settings.add("imputationModel",clean(fitted.get("model")));}
        if(results.getAsJsonObject().has("ordinalSem")){var fitted=results.getAsJsonObject().getAsJsonObject("ordinalSem");settings.add("ordinalOptions",clean(fitted.get("options")));settings.add("ordinalModel",clean(fitted.get("model")));if(project.has("ordinalSettings"))settings.add("ordinalSourceSpecification",clean(project.get("ordinalSettings")));}
        if(results.getAsJsonObject().has("simulation"))settings.add("simulationDesign",clean(results.getAsJsonObject().getAsJsonObject("simulation").get("design")));
        for(String key:List.of("clusterSem","twoLevelSem"))if(results.getAsJsonObject().has(key)){var fitted=results.getAsJsonObject().getAsJsonObject(key);settings.add(key+"Options",clean(fitted.get("options")));if(fitted.has("specification"))settings.add("twoLevelSpecification",clean(fitted.get("specification")));}if(project.has("clusterSettings"))settings.add("clusterSourceSpecification",clean(project.get("clusterSettings")));
        if(results.getAsJsonObject().has("regularizedSem")){var fitted=results.getAsJsonObject().getAsJsonObject("regularizedSem");settings.add("regularizationOptions",clean(fitted.get("options")));settings.add("regularizationModel",clean(fitted.get("model")));}
        if(results.getAsJsonObject().has("bayesianSem")){var fitted=results.getAsJsonObject().getAsJsonObject("bayesianSem");settings.add("bayesianOptions",clean(fitted.get("options")));settings.add("bayesianModel",clean(fitted.get("model")));settings.add("bayesianSampler",clean(fitted.get("sampler")));}
        if(results.getAsJsonObject().has("semInfluence")){var fitted=results.getAsJsonObject().getAsJsonObject("semInfluence");settings.add("influenceOptions",clean(fitted.get("options")));settings.add("influenceModel",clean(fitted.get("model")));}if(results.getAsJsonObject().has("semMultiverse")){var fitted=results.getAsJsonObject().getAsJsonObject("semMultiverse");settings.add("sensitivitySpecifications",clean(fitted.get("specifications")));settings.add("sensitivityCompleteCases",clean(fitted.get("completeCases")));}
        report.add("calculationSettings", settings);
        var reportWarnings=new java.util.ArrayList<>(warnings);if(project.has("derivation")){org.brightstats.data.ProjectDerivation.validate(project.get("derivation"),project.getAsJsonArray("observations").get(0).getAsJsonArray().size());if(!reportWarnings.contains(org.brightstats.data.ProjectDerivation.WARNING))reportWarnings.add(org.brightstats.data.ProjectDerivation.WARNING);}
        report.add("tables", clean(tables)); report.add("warnings", JSON.toJsonTree(reportWarnings));
        validate(report); return report;
    }

    private static JsonElement clean(JsonElement value) {
        if (value == null || value.isJsonNull()) return JsonNull.INSTANCE;
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().entrySet().forEach(entry -> result.add(entry.getKey(), clean(entry.getValue())));
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray(); value.getAsJsonArray().forEach(item -> result.add(clean(item))); return result;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            double number = primitive.getAsDouble();
            if (Double.isNaN(number)) return JsonNull.INSTANCE;
            if (Double.isInfinite(number)) return new JsonPrimitive(number > 0 ? "Infinity" : "-Infinity");
        }
        return primitive.deepCopy();
    }

    public static void validate(JsonObject report) {
        try {
            requireString(report.get("format")); requireString(report.get("applicationVersion"));
            requireString(report.get("createdUtc")); requireString(report.get("estimator"));
            if (!"bright-stats-report".equals(report.get("format").getAsString())
                    || !report.get("formatVersion").isJsonPrimitive()
                    || !report.get("formatVersion").getAsJsonPrimitive().isNumber()
                    || report.get("formatVersion").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) != 0)
                throw new IllegalArgumentException("Unsupported report format");
            Instant.parse(report.get("createdUtc").getAsString());
            if (report.get("estimator").getAsString().isBlank() || !report.get("project").isJsonObject()
                    || !report.get("results").isJsonObject()) throw new IllegalArgumentException("Incomplete report");
            for (JsonElement warning : report.getAsJsonArray("warnings")) requireString(warning);
            for (JsonElement value : report.getAsJsonArray("tables")) {
                JsonObject table = value.getAsJsonObject(); requireString(table.get("title"));
                JsonArray columns = table.getAsJsonArray("columns");
                if (columns.isEmpty()) throw new IllegalArgumentException("Report table has no columns");
                columns.forEach(ReportArchive::requireString);
                for (JsonElement row : table.getAsJsonArray("rows")) {
                    if (row.getAsJsonArray().size() != columns.size()) throw new IllegalArgumentException("Report table dimensions differ");
                    row.getAsJsonArray().forEach(ReportArchive::requireString);
                }
            }
        } catch (NullPointerException | ClassCastException | IllegalStateException error) {
            throw new IllegalArgumentException("Invalid Bright-Stats report", error);
        }
    }
    private static void requireString(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected report text");
    }

    public static void write(Path path, JsonObject report) throws IOException {
        validate(report); byte[] bytes = JSON.toJson(report).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Report exceeds the 64 MiB archive limit");
        writeBytes(path, bytes);
    }
    public static JsonObject read(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) throw new IllegalArgumentException("Report exceeds the 64 MiB archive limit");
        JsonObject report = org.brightstats.data.BoundedJson.read(path).getAsJsonObject();
        if (report == null) throw new IllegalArgumentException("Empty report");
        validate(report); return report;
    }
    private static void writeBytes(Path path, byte[] bytes) throws IOException {
        Path target = path.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".bright-stats-report-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    public static String html(JsonObject report) {
        validate(report);
        StringBuilder output = new StringBuilder("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'\">"
                + "<title>Bright-Stats report</title><style>body{font:15px system-ui,sans-serif;max-width:1200px;margin:40px auto;padding:0 24px;color:#163247}"
                + "table{border-collapse:collapse;margin:16px 0 32px}td,th{border:1px solid #ccd8df;padding:8px;text-align:left}th{background:#eaf3f5}"
                + "section{overflow:auto}.warning{color:#793b00}code{white-space:pre-wrap}@media print{body{margin:0}tr{break-inside:avoid}}</style></head><body>"
                + "<h1>Bright-Stats report</h1><p>");
        output.append(escape(report.get("estimator").getAsString())).append(" · ")
                .append(escape(report.get("createdUtc").getAsString())).append("</p>");
        JsonObject project = report.getAsJsonObject("project");
        if (project.has("title")) output.append("<p>Project: ").append(escape(project.get("title").getAsString())).append(".</p>");
        if (project.has("observations")) output.append("<p>Input observations: ").append(project.getAsJsonArray("observations").size()).append(".</p>");
        output.append("<p>Archive numeric results retain full precision. Tables below use the displayed precision.</p>");
        if (project.has("dataPreparation")) {
            JsonObject preparation = project.getAsJsonObject("dataPreparation");
            output.append("<h2>Data preparation</h2><p>Source: ").append(escape(preparation.get("sourceName").getAsString()))
                    .append(". Replaced cells: ").append(preparation.get("replacedCells").getAsInt())
                    .append(". Removed rows: ").append(preparation.get("removedRows").getAsInt()).append(".</p><code>")
                    .append(escape(JSON.toJson(preparation.get("settings")))).append("</code><p>Column definitions: ")
                    .append(escape(JSON.toJson(preparation.get("definitions")))).append("</p><p>Retained source data rows: ")
                    .append(escape(JSON.toJson(preparation.get("sourceRows")))).append(".</p>");
        }
        JsonObject results = report.getAsJsonObject("results");
        if(results.has("prediction")) {
            var prediction=results.getAsJsonObject("prediction");output.append("<h2>Predictive validation</h2><p>").append(escape(prediction.get("method").getAsString())).append("</p><p>")
                    .append(escape(prediction.get("preprocessing").getAsString())).append("</p><p>").append(escape(prediction.get("benchmark").getAsString())).append("</p><code>")
                    .append(escape(JSON.toJson(prediction.get("options")))).append("</code>");
            if(results.has("inputSource"))output.append("<p>Input source: ").append(escape(results.get("inputSource").getAsString())).append("</p>");
        }
        if(results.has("bootstrap")) {
            JsonObject bootstrap=results.getAsJsonObject("bootstrap");
            output.append("<h2>Bootstrap inference</h2><p>").append(escape(bootstrap.get("method").getAsString()))
                    .append("</p><p>Successful samples: ").append(bootstrap.get("successful").getAsInt()).append("; failed: ")
                    .append(bootstrap.get("failed").getAsInt()).append(".</p><p>").append(escape(bootstrap.get("signHandling").getAsString()))
                    .append("</p><code>").append(escape(JSON.toJson(bootstrap.get("options")))).append("</code>");
            if(bootstrap.get("failed").getAsInt()>0)output.append("<p>Failure reasons: ").append(escape(JSON.toJson(bootstrap.get("failures")))).append("</p>");
        }
        if(results.has("inference"))output.append("<h2>Regression inference</h2><code>").append(escape(JSON.toJson(results.getAsJsonObject("inference").get("confidence"))))
                .append("</code><p>").append(escape(results.getAsJsonObject("inference").get("method").getAsString())).append("</p>");
        if (results.has("response")) output.append("<p>Response: ").append(escape(results.get("response").getAsString())).append(".</p>");
        if (results.has("fit") && results.get("fit").isJsonObject()) {
            output.append("<h2>Calculation summary</h2><table>");
            for (var entry : results.getAsJsonObject("fit").entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) continue;
                output.append("<tr><th>").append(escape(entry.getKey().replaceAll("([a-z])([A-Z])", "$1 $2")))
                        .append("</th><td>").append(escape(entry.getValue().getAsString())).append("</td></tr>");
            }
            output.append("</table>");
        }
        for (JsonElement warning : report.getAsJsonArray("warnings")) output.append("<p class=\"warning\">").append(escape(warning.getAsString())).append("</p>");
        for (JsonElement value : report.getAsJsonArray("tables")) {
            JsonObject table = value.getAsJsonObject(); output.append("<section><h2>").append(escape(table.get("title").getAsString())).append("</h2><table><thead><tr>");
            for (JsonElement column : table.getAsJsonArray("columns")) output.append("<th>").append(escape(column.getAsString())).append("</th>");
            output.append("</tr></thead><tbody>");
            for (JsonElement row : table.getAsJsonArray("rows")) {
                output.append("<tr>"); for (JsonElement cell : row.getAsJsonArray()) output.append("<td>").append(escape(cell.getAsString())).append("</td>"); output.append("</tr>");
            }
            output.append("</tbody></table></section>");
        }
        output.append("<h2>Calculation settings</h2><code>").append(escape(report.has("calculationSettings") ? JSON.toJson(report.get("calculationSettings")) : "See full-precision JSON archive."))
                .append("</code><p>Generated locally by Bright-Stats ").append(escape(report.get("applicationVersion").getAsString())).append(".</p></body></html>");
        return output.toString();
    }
    public static void writeHtml(Path path, JsonObject report) throws IOException {
        writeBytes(path, html(report).getBytes(StandardCharsets.UTF_8));
    }
    private static String escape(String text) {
        return XmlText.escape(text);
    }
}
