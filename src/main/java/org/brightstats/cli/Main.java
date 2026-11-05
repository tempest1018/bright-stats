package org.brightstats.cli;

import com.google.gson.GsonBuilder;
import com.google.gson.Gson;
import java.util.List;
import java.util.Arrays;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.brightstats.interop.LegacyEnvelopeCodec;
import org.brightstats.stats.OlsRegression;
import org.brightstats.stats.PlsSem;
import org.brightstats.stats.ModelDiagnostics;
import org.brightstats.stats.Inference;
import org.brightstats.stats.Bootstrap;
import org.brightstats.stats.Prediction;
import org.brightstats.stats.PrincipalComponents;
import org.brightstats.stats.ConsistentPls;
import org.brightstats.stats.RobustRegression;
import org.brightstats.stats.LogisticRegression;
import org.brightstats.stats.RegressionBootstrap;
import org.brightstats.stats.BootstrapIntervals;
import org.brightstats.stats.CovarianceSem;
import org.brightstats.stats.SemModel;
import org.brightstats.stats.SemComparison;
import org.brightstats.stats.Gsca;
import org.brightstats.stats.GscaBootstrap;
import org.brightstats.stats.SemBootstrap;
import org.brightstats.stats.Nca;
import org.brightstats.stats.NcaPermutation;
import org.brightstats.stats.PlsGroups;
import org.brightstats.stats.PlsPermutation;
import org.brightstats.stats.PlsGroupBootstrap;
import org.brightstats.stats.MultiGroupSem;
import org.brightstats.stats.MultiGroupSemComparison;
import org.brightstats.stats.SemInvariance;
import org.brightstats.stats.ImportancePerformance;
import org.brightstats.stats.TetradAnalysis;
import org.brightstats.stats.PathProcess;
import org.brightstats.stats.FiniteMixture;
import org.brightstats.stats.PredictionSegmentation;
import org.brightstats.stats.PlsMixture;
import org.brightstats.data.ProcessInput;
import org.brightstats.data.CopulaInput;
import org.brightstats.data.PlsModelInput;
import org.brightstats.data.NonlinearInput;
import org.brightstats.data.HigherOrderInput;
import org.brightstats.data.HigherOrderProjects;
import org.brightstats.stats.HigherOrderPls;
import org.brightstats.stats.NonlinearPls;
import org.brightstats.stats.CopulaRegression;

/** Experimental offline calculation interface. Not the finished desktop app. */
public final class Main {
    public static final String VERSION = "0.1.0-development";
    private Main() {}

    public static void main(String[] args) {
        // JSON/text pipes must preserve model names on Windows as well as Linux.
        System.setOut(new java.io.PrintStream(System.out,true,java.nio.charset.StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(System.err,true,java.nio.charset.StandardCharsets.UTF_8));
        try {
            if (args.length == 0 || args[0].equals("--help")) {
                System.out.println("Bright-Stats " + VERSION + " - development calculation interface\n"
                    + "data-read INPUT.json   Read XLS/XLSX or SAV/ZSAV values and metadata\n"
                    + "data-sheets INPUT.json List workbook worksheets\n"
                    + "data-write-workbook INPUT.json Export literal workbook data\n"
                    + "regress INPUT.json     Fit ordinary least squares with an intercept\n"
                    + "regress-bootstrap INPUT.json  Paired-row OLS bootstrap with percentile, bootstrap-t or BCa CI\n"
                    + "pls INPUT.json         Fit standardized linear PLS-SEM\n"
                    + "predict INPUT.json     Repeated holdout/k-fold PLS predictive validation\n"
                    + "plsc INPUT.json        Consistent PLS factor/composite correction\n"
                    + "component-copula INPUT.json PLS/GSCA score control functions\n"
                    + "component-copula-bootstrap INPUT.json Full-pipeline inference\n"
                    + "transform-data INPUT.json      Append fixed row features and recodes\n"
                    + "pls-scales INPUT.json          Standardized/raw/centered PLS metrics\n"
                    + "pls-scales-bootstrap INPUT.json Full metric refit inference\n"
                    + "sem-influence INPUT.json  Exact case/cluster deletion sensitivity\n"
                    + "sem-multiverse INPUT.json  Common-sample specification alternatives\n"
                    + "sem-bayesian INPUT.json  Proper-prior Bayesian SEM and MCMC diagnostics\n"
                    + "sem-regularized INPUT.json  Elastic-net SEM and nested held-out validation\n"
                    + "sem-cluster INPUT.json  Marginal SEM with cluster inference and resampling\n"
                    + "sem-two-level INPUT.json  Separate within/between continuous SEM\n"
                    + "sem-simulate INPUT.json  Seeded SEM power and precision study\n"
                    + "sem-fiml INPUT.json  Continuous observed-data Gaussian SEM\n"
                    + "impute-data / sem-mi INPUT.json  Proper continuous normal imputation and Rubin pooling\n"
                    + "ordinal-sem INPUT.json  Explicit-order polychoric/DWLS robust SEM\n"
                    + "latent-interaction / latent-interaction-bootstrap INPUT.json  Marginal Normal/Student-t latent ML\n"
                    + "hierarchy-pls / hierarchy-plsc INPUT.json  Multi-level hierarchy (also -bootstrap)\n"
                    + "pairwise-moments / pairwise-components / pairwise-sem INPUT.json (component/SEM: -bootstrap)\n"
                    + "adjusted-copula INPUT.json     Adjusted-CDF polynomial equation system\n"
                    + "adjusted-copula-bootstrap INPUT.json Shared system inference and joint tests\n"
                    + "component-adjusted-copula INPUT.json PLS/GSCA adjusted systems\n"
                    + "component-adjusted-copula-bootstrap INPUT.json Full component/system inference\n"
                    + "pca-model INPUT.json   Global/per-construct PLS, CB-SEM or GSCA PCA\n"
                    + "pca INPUT.json         Covariance/correlation principal components\n"
                    + "logistic INPUT.json    Binary maximum-likelihood regression\n"
                    + "sem INPUT.json         Covariance/mean RAM maximum-likelihood SEM/CFA\n"
                    + "sem-compare INPUT.json Nested RAM likelihood-ratio comparison\n"
                    + "gsca INPUT.json        Generalized structured component analysis\n"
                    + "sem-bootstrap INPUT.json  Paired-row covariance SEM uncertainty\n"
                    + "gsca-bootstrap INPUT.json Paired-row GSCA uncertainty\n"
                    + "pos INPUT.json         Prediction-oriented hard PLS segmentation\n"
                    + "mixture INPUT.json     Gaussian structural mixtures on fixed scores\n"
                    + "fimix INPUT.json       PLS score extraction and structural segmentation\n"
                    + "process INPUT.json     Linear mediation / conditional path models\n"
                    + "model-compare INPUT.json    Paired held-out comparison of two PLS models\n"
                    + "higher-order-pls INPUT.json Disjoint two-stage hierarchical components\n"
                    + "higher-order-pls-bootstrap INPUT.json Both-stage inference\n"
                    + "higher-order-prepare INPUT.json Editable fixed-score stage-two project\n"
                    + "nonlinear-pls INPUT.json   Two-stage latent interactions/polynomials\n"
                    + "nonlinear-pls-bootstrap INPUT.json Full first-stage-refit inference\n"
                    + "copula INPUT.json          Continuous Gaussian-copula / two-stage correction\n"
                    + "copula-bootstrap INPUT.json Full-procedure generated-regressor bootstrap\n"
                    + "process-bootstrap INPUT.json Paired conditional-effect uncertainty\n"
                    + "tetrads INPUT.json     Bootstrap vanishing-tetrad measurement assessment\n"
                    + "ipma INPUT.json        Construct/indicator importance and performance\n"
                    + "nca INPUT.json         CE-FDH / CR-FDH necessary-condition analysis\n"
                    + "nca-permutation INPUT.json Outcome-label necessity significance tests\n"
                    + "pls-groups INPUT.json  Named, disjoint PLS/PLSc group estimates\n"
                    + "pls-group-permutation INPUT.json Group contrasts and composite MICOM\n"
                    + "pls-group-bootstrap INPUT.json Group intervals and parametric/Welch/PLS-MGA\n"
                    + "sem-groups INPUT.json  Joint grouped RAM likelihood and cross-group contrasts\n"
                    + "sem-groups-compare INPUT.json Nested joint-group likelihood-ratio comparison\n"
                    + "sem-invariance-stage INPUT.json Fit a reflective invariance constraint level\n"
                    + "sem-invariance INPUT.json Basic/strict reflective measurement invariance\n"
                    + "                      Optional bootstrap: {samples,seed,confidence}; OLS accepts confidence\n"
                    + "decode INPUT KEYFILE   Decode an authorized legacy model/report envelope\n"
                    + "--version             Show development version\n"
                    + "This development build does not provide the complete SmartPLS feature set.\n"
                    + "The desktop preview has a separate graphical launcher.");
                return;
            }
            if (args[0].equals("--version") && args.length == 1) {
                System.out.println("Bright-Stats " + VERSION);
                return;
            }
            if (args[0].equals("decode") && args.length == 3) {
                String plaintext = LegacyEnvelopeCodec.decode(Files.readString(Path.of(args[1])),
                        Files.readAllBytes(Path.of(args[2])), false);
                System.out.println(plaintext);
                return;
            }
            if (args.length != 2) throw new IllegalArgumentException("Use --help for supported commands");
            JsonObject input = org.brightstats.data.BoundedJson.read(Path.of(args[1])).getAsJsonObject();
            JsonObject output = switch (args[0]) {
                case "data-read" -> dataRead(input);
                case "data-sheets" -> dataSheets(input);
                case "data-write-workbook" -> dataWrite(input);
                case "regress" -> regress(input);
                case "pls" -> pls(input,false,false);
                case "plsc" -> pls(input,false,true);
                case "predict" -> pls(input,true,false);
                case "pca" -> pca(input);
                case "sem-products" -> semProducts(input,false,false);
                case "sem-products-bootstrap" -> semProducts(input,true,false);
                case "sem-products-prepare" -> semProducts(input,false,true);
                case "component-polynomial" -> componentPolynomial(input,false);
                case "component-polynomial-bootstrap" -> componentPolynomial(input,true);
                case "observed-polynomial" -> observedPolynomial(input,false);
                case "observed-polynomial-bootstrap" -> observedPolynomial(input,true);
                case "regress-origin" -> originRegression(input,false);
                case "regress-origin-bootstrap" -> originRegression(input,true);
                case "transform-data" -> transformData(input);
                case "sem-influence" -> semInfluence(input);
                case "sem-multiverse" -> semMultiverse(input);
                case "sem-bayesian" -> bayesianSem(input);
                case "sem-regularized" -> regularizedSem(input);
                case "sem-cluster" -> clusterSem(input);
                case "sem-two-level" -> twoLevelSem(input);
                case "sem-simulate" -> simulate(input);
                case "sem-fiml" -> fiml(input);
                case "sem-mi" -> imputedSem(input);
                case "ordinal-sem" -> ordinalSem(input);
                case "impute-data" -> imputeData(input);
                case "latent-interaction" -> latentInteraction(input,false);
                case "latent-interaction-bootstrap" -> latentInteraction(input,true);
                case "hierarchy-pls" -> hierarchy(input,false,false);
                case "hierarchy-pls-bootstrap" -> hierarchy(input,true,false);
                case "hierarchy-plsc" -> hierarchy(input,false,true);
                case "hierarchy-plsc-bootstrap" -> hierarchy(input,true,true);
                case "pairwise-moments" -> pairwise(input,"MATRICES",false);
                case "pairwise-components" -> pairwise(input,"COMPONENT",false);
                case "pairwise-components-bootstrap" -> pairwise(input,"COMPONENT",true);
                case "pairwise-sem" -> pairwise(input,"SEM",false);
                case "pairwise-sem-bootstrap" -> pairwise(input,"SEM",true);
                case "adjusted-copula" -> adjustedCopula(input,false,false);
                case "adjusted-copula-bootstrap" -> adjustedCopula(input,true,false);
                case "component-adjusted-copula" -> adjustedCopula(input,false,true);
                case "component-adjusted-copula-bootstrap" -> adjustedCopula(input,true,true);
                case "pls-scales" -> plsScales(input,false);
                case "pls-scales-bootstrap" -> plsScales(input,true);
                case "component-copula" -> componentCopula(input,false);
                case "component-copula-bootstrap" -> componentCopula(input,true);
                case "pca-model" -> modelPca(input);
                case "logistic" -> logistic(input);
                case "regress-bootstrap" -> regressionBootstrap(input);
                case "sem" -> sem(input);
                case "sem-compare" -> semCompare(input);
                case "gsca" -> gsca(input);
                case "sem-bootstrap" -> semBootstrap(input);
                case "gsca-bootstrap" -> gscaBootstrap(input);
                case "pos" -> pos(input);
                case "mixture" -> mixture(input,false);
                case "fimix" -> mixture(input,true);
                case "model-compare" -> modelCompare(input);
                case "higher-order-pls" -> higherOrder(input,false,false);
                case "higher-order-pls-bootstrap" -> higherOrder(input,true,false);
                case "higher-order-prepare" -> higherOrder(input,false,true);
                case "nonlinear-pls" -> nonlinear(input,false);
                case "nonlinear-pls-bootstrap" -> nonlinear(input,true);
                case "copula" -> copula(input,false);
                case "copula-bootstrap" -> copula(input,true);
                case "process" -> process(input,false);
                case "process-bootstrap" -> process(input,true);
                case "tetrads" -> tetrads(input);
                case "ipma" -> ipma(input);
                case "nca" -> nca(input,false);
                case "nca-permutation" -> nca(input,true);
                case "pls-groups" -> groups(input,0);
                case "pls-group-permutation" -> groups(input,1);
                case "pls-group-bootstrap" -> groups(input,2);
                case "sem-groups" -> semGroups(input);
                case "sem-groups-compare" -> semGroupsCompare(input);
                case "sem-invariance-stage" -> semInvariance(input,false);
                case "sem-invariance" -> semInvariance(input,true);
                default -> throw new IllegalArgumentException("Unknown command; use --help");
            };
            output.addProperty("application", "Bright-Stats");
            output.addProperty("version", VERSION);
            System.out.println(new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(output));
        } catch (Exception error) {
            System.err.println("Bright-Stats: " + error.getMessage());
            System.exit(2);
        }
    }

    private static JsonObject dataSheets(JsonObject input)throws Exception{JsonObject result=new JsonObject();result.add("sheets",new Gson().toJsonTree(org.brightstats.data.WorkbookData.sheets(Path.of(input.get("path").getAsString()))));return result;}
    private static JsonObject dataRead(JsonObject input)throws Exception{Path path=Path.of(input.get("path").getAsString());String name=path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);JsonObject result=new JsonObject();if(name.endsWith(".sav")||name.endsWith(".zsav")){result.addProperty("sourceType","SPSS");result.add("data",new Gson().toJsonTree(org.brightstats.data.SpssData.read(path,!input.has("applyUserMissing")||input.get("applyUserMissing").getAsBoolean())));}else{result.addProperty("sourceType","EXCEL");result.add("data",new Gson().toJsonTree(org.brightstats.data.WorkbookData.read(path,input.has("sheet")?input.get("sheet").getAsString():null,input.has("headerRow")?input.get("headerRow").getAsBigDecimal().intValueExact():0)));}return result;}
    private static JsonObject dataWrite(JsonObject input)throws Exception{Path path=Path.of(input.get("path").getAsString());var raw=new Gson().fromJson(input.get("raw"),org.brightstats.data.DataPreparation.Raw.class);boolean xlsx=path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx");if(!xlsx&&!path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".xls"))throw new IllegalArgumentException("Choose an .xls or .xlsx output.");org.brightstats.data.WorkbookData.write(path,input.has("sheet")?input.get("sheet").getAsString():"Data",raw,xlsx);JsonObject result=new JsonObject();result.addProperty("written",true);result.addProperty("rows",raw.rows().size());result.addProperty("columns",raw.headers().size());return result;}
    private record PlsInput(double[][] data,java.util.List<PlsSem.Block> blocks,java.util.List<PlsSem.Edge> edges,PlsSem.Options options){}
    private static int[] indices(JsonArray values){int[] result=new int[values.size()];for(int i=0;i<result.length;i++)result[i]=values.get(i).getAsBigDecimal().intValueExact();return result;}
    private static PlsInput plsInput(JsonObject input){
        double[][] data=matrix(input.getAsJsonArray("observations"));var blocks=new ArrayList<PlsSem.Block>();for(var element:input.getAsJsonArray("blocks")){var block=element.getAsJsonObject();var mode=PlsSem.Mode.valueOf(block.get("mode").getAsString());blocks.add(new PlsSem.Block(block.get("name").getAsString(),indices(block.getAsJsonArray("columns")),mode,block.has("initialWeights")?vector(block.getAsJsonArray("initialWeights")):null,block.has("measurementType")?PlsSem.MeasurementType.valueOf(block.get("measurementType").getAsString()):mode==PlsSem.Mode.A?PlsSem.MeasurementType.REFLECTIVE:PlsSem.MeasurementType.FORMATIVE));}var edges=new ArrayList<PlsSem.Edge>();for(var element:input.getAsJsonArray("edges")){var edge=element.getAsJsonObject();edges.add(new PlsSem.Edge(edge.get("source").getAsBigDecimal().intValueExact(),edge.get("target").getAsBigDecimal().intValueExact()));}var supplied=input.has("options")?input.getAsJsonObject("options"):new JsonObject();var options=new PlsSem.Options(supplied.has("maxIterations")?supplied.get("maxIterations").getAsBigDecimal().intValueExact():3000,supplied.has("stopCriterion")?supplied.get("stopCriterion").getAsDouble():1e-7,supplied.has("weighting")?PlsSem.Weighting.valueOf(supplied.get("weighting").getAsString()):PlsSem.Weighting.PATH);return new PlsInput(data,blocks,edges,options);
    }
    private static JsonObject groups(JsonObject input,int kind){
        var model=plsInput(input);var selected=input.getAsJsonObject("selection");var selection=new PlsGroups.Selection(selected.has("nameA")?selected.get("nameA").getAsString():"Group A",selected.has("nameB")?selected.get("nameB").getAsString():"Group B",indices(selected.getAsJsonArray("rowsA")),indices(selected.getAsJsonArray("rowsB")));boolean consistent=input.has("consistent")&&input.get("consistent").getAsBoolean();var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();JsonObject output=new JsonObject();output.addProperty("estimator",(consistent?"PLSC":"PLS")+(kind==0?"_GROUPS":kind==1?"_GROUP_PERMUTATION":"_BOOTSTRAP_MGA"));
        if(kind==0){output.add("groups",clean(gson.toJsonTree(PlsGroups.fit(model.data(),model.blocks(),model.edges(),model.options(),selection,consistent))));return output;}
        if(kind==1){var supplied=input.has("permutation")?input.getAsJsonObject("permutation"):new JsonObject();var defaults=PlsPermutation.Options.defaults();var options=new PlsPermutation.Options(supplied.has("samples")?supplied.get("samples").getAsBigDecimal().intValueExact():defaults.samples(),supplied.has("seed")?supplied.get("seed").getAsBigDecimal().longValueExact():defaults.seed(),supplied.has("significance")?supplied.get("significance").getAsDouble():defaults.significance(),supplied.has("convention")?RegressionBootstrap.Convention.valueOf(supplied.get("convention").getAsString()):defaults.convention(),!supplied.has("twoSided")||supplied.get("twoSided").getAsBoolean(),supplied.has("retainDraws")&&supplied.get("retainDraws").getAsBoolean(),supplied.has("configuralConfirmed")&&supplied.get("configuralConfirmed").getAsBoolean());output.add("groups",clean(gson.toJsonTree(PlsPermutation.fit(model.data(),model.blocks(),model.edges(),model.options(),selection,consistent,options,null))));return output;}
        var supplied=input.deepCopy();var settings=input.has("bootstrap")?input.getAsJsonObject("bootstrap").deepCopy():new JsonObject();if(!settings.has("convention"))settings.addProperty("convention","STANDARD");supplied.add("bootstrap",settings);output.add("groups",clean(gson.toJsonTree(PlsGroupBootstrap.fit(model.data(),model.blocks(),model.edges(),model.options(),selection,consistent,componentBootstrapOptions(supplied),input.has("retainDraws")&&input.get("retainDraws").getAsBoolean(),null))));return output;
    }
    private static CovarianceSem.Options semOptions(JsonObject input,boolean defaultMean){var supplied=input.has("options")?input.getAsJsonObject("options"):new JsonObject();var defaults=CovarianceSem.Options.defaults();return new CovarianceSem.Options(supplied.has("maxIterations")?supplied.get("maxIterations").getAsBigDecimal().intValueExact():defaults.maxIterations(),supplied.has("gradientTolerance")?supplied.get("gradientTolerance").getAsDouble():defaults.gradientTolerance(),supplied.has("functionTolerance")?supplied.get("functionTolerance").getAsDouble():defaults.functionTolerance(),supplied.has("likelihood")?CovarianceSem.Likelihood.valueOf(supplied.get("likelihood").getAsString()):defaults.likelihood(),supplied.has("meanStructure")?supplied.get("meanStructure").getAsBoolean():defaultMean,supplied.has("confidence")?supplied.get("confidence").getAsDouble():defaults.confidence(),supplied.has("information")?CovarianceSem.Information.valueOf(supplied.get("information").getAsString()):defaults.information());}
    private static SemModel.Model semModel(JsonElement input){var model=input.getAsJsonObject();indices(model.getAsJsonArray("observed"));for(var value:model.getAsJsonArray("parameters"))for(var location:value.getAsJsonObject().getAsJsonArray("locations")){location.getAsJsonObject().get("row").getAsBigDecimal().intValueExact();location.getAsJsonObject().get("column").getAsBigDecimal().intValueExact();}return new com.google.gson.Gson().fromJson(model,SemModel.Model.class);}
    private static MultiGroupSem.Group semGroup(JsonObject input,SemModel.Model template,CovarianceSem.Options options){String name=input.get("name").getAsString();var model=template==null?semModel(input.get("model")):template;java.util.Map<String,String> bindings=new java.util.LinkedHashMap<>();if(input.has("bindings"))for(var entry:input.getAsJsonObject("bindings").entrySet()){if(!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isString())throw new IllegalArgumentException("SEM binding tags must be strings.");bindings.put(entry.getKey(),entry.getValue().getAsString());}if(input.has("observations"))return MultiGroupSem.Group.fromData(name,matrix(input.getAsJsonArray("observations")),model,options,bindings);double[][] covariance=matrix(input.getAsJsonArray("covariance"));if(options.meanStructure()&&!input.has("means"))throw new IllegalArgumentException("SEM group covariance input requires means when mean structure is enabled.");return new MultiGroupSem.Group(name,input.get("sampleSize").getAsBigDecimal().intValueExact(),covariance,input.has("means")?vector(input.getAsJsonArray("means")):new double[covariance.length],model,bindings);}
    private static MultiGroupSem.Fit semGroupsFit(JsonObject input){var options=semOptions(input,false);var groups=new ArrayList<MultiGroupSem.Group>();for(var value:input.getAsJsonArray("groups"))groups.add(semGroup(value.getAsJsonObject(),null,options));return MultiGroupSem.fit(groups,options);}
    private static JsonObject semGroups(JsonObject input){var fit=semGroupsFit(input);return semGroupOutput(fit,input.has("referenceGroup")?input.get("referenceGroup").getAsString():fit.groups().get(0).input().name());}
    private static JsonObject semGroupOutput(MultiGroupSem.Fit fit,String reference){var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();JsonObject output=new JsonObject();output.addProperty("estimator","CBSEM_JOINT_MULTIGROUP_ML");output.add("fit",clean(gson.toJsonTree(fit)));output.add("contrasts",clean(gson.toJsonTree(MultiGroupSem.contrasts(fit,reference))));output.add("meanContrasts",clean(gson.toJsonTree(MultiGroupSem.meanContrasts(fit,reference))));return output;}
    private static JsonObject semGroupsCompare(JsonObject input){var restricted=semGroupsFit(input.getAsJsonObject("restricted"));var unrestricted=semGroupsFit(input.getAsJsonObject("unrestricted"));var output=new JsonObject();output.addProperty("estimator","NESTED_JOINT_CBSEM_COMPARISON");output.add("comparison",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(MultiGroupSemComparison.compare(restricted,unrestricted))));return output;}
    private static JsonObject semInvariance(JsonObject input,boolean sequence){var options=semOptions(input,true);var model=semModel(input.get("model"));var prepared=new SemModel.Prepared(model,java.util.stream.IntStream.range(0,model.observed().length).toArray(),indices(input.getAsJsonArray("factors")));var samples=new ArrayList<SemInvariance.Sample>();for(var value:input.getAsJsonArray("groups")){var group=value.getAsJsonObject();if(group.has("model")||group.has("bindings"))throw new IllegalArgumentException("Automatic invariance uses the root template; omit per-group models/bindings or use sem-groups.");var source=semGroup(group,model,options);samples.add(new SemInvariance.Sample(source.name(),source.observations(),source.covariance(),source.means()));}if(!sequence){var stage=SemInvariance.Stage.valueOf(input.get("stage").getAsString());var fit=SemInvariance.fitStage(prepared,samples,stage,options);return semGroupOutput(fit,input.has("referenceGroup")?input.get("referenceGroup").getAsString():fit.groups().get(0).input().name());}var result=SemInvariance.sequence(prepared,samples,options,input.has("strict")&&input.get("strict").getAsBoolean(),null);JsonObject output=new JsonObject();output.addProperty("estimator","CBSEM_MEASUREMENT_INVARIANCE");output.add("invariance",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(result)));return output;}
    private static JsonObject pos(JsonObject input){var source=plsInput(input);var supplied=input.has("segmentation")?input.getAsJsonObject("segmentation"):new JsonObject();for(String key:List.of("segments","repetitions","maxIterations","searchDepth","minimumSize","target","maximumGroupFits"))if(supplied.has(key))supplied.get(key).getAsBigDecimal().intValueExact();if(supplied.has("seed"))supplied.get("seed").getAsBigDecimal().longValueExact();var defaults=new Gson().toJsonTree(PredictionSegmentation.Options.defaults(source.data().length)).getAsJsonObject();for(var entry:supplied.entrySet()){if(!defaults.has(entry.getKey()))throw new IllegalArgumentException("Unknown segmentation setting: "+entry.getKey());defaults.add(entry.getKey(),entry.getValue());}var options=new Gson().fromJson(defaults,PredictionSegmentation.Options.class);int[] initial=null;if(input.has("initialAssignments")){var values=input.getAsJsonArray("initialAssignments");initial=new int[values.size()];for(int i=0;i<initial.length;i++)initial[i]=values.get(i).getAsBigDecimal().intValueExact();}var result=PredictionSegmentation.fit(source.data(),source.blocks(),source.edges(),source.options(),options,initial,input.has("mixture")?mixtureOptions(input):null,null);JsonObject output=new JsonObject();output.addProperty("estimator","PLS_POS");output.add("pos",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(result)));return output;}
    private static FiniteMixture.Options mixtureOptions(JsonObject input){var supplied=input.has("mixture")?input.getAsJsonObject("mixture"):new JsonObject();for(String key:List.of("segments","repetitions","maxIterations"))if(supplied.has(key))supplied.get(key).getAsBigDecimal().intValueExact();if(supplied.has("seed"))supplied.get("seed").getAsBigDecimal().longValueExact();var defaults=new Gson().toJsonTree(FiniteMixture.Options.defaults()).getAsJsonObject();for(var entry:supplied.entrySet())defaults.add(entry.getKey(),entry.getValue());return new Gson().fromJson(defaults,FiniteMixture.Options.class);}
    private static JsonObject mixture(JsonObject input,boolean pls){var options=mixtureOptions(input);var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();JsonObject output=new JsonObject();if(pls){var source=plsInput(input);var result=PlsMixture.fit(source.data(),source.blocks(),source.edges(),source.options(),options,input.has("originalUnits")&&input.get("originalUnits").getAsBoolean(),null);output.addProperty("estimator","FIMIX_PLS");output.add("fimix",clean(gson.toJsonTree(result)));}else{double[][] data=matrix(input.getAsJsonArray("observations"));List<String> names=input.has("names")?Arrays.asList(new Gson().fromJson(input.get("names"),String[].class)):java.util.stream.IntStream.range(0,data[0].length).mapToObj(j->"Score"+(j+1)).toList();List<PlsSem.Edge> edges=new ArrayList<>();for(var value:input.getAsJsonArray("edges")){var edge=value.getAsJsonObject();edges.add(new PlsSem.Edge(edge.get("source").getAsBigDecimal().intValueExact(),edge.get("target").getAsBigDecimal().intValueExact()));}output.addProperty("estimator","GAUSSIAN_STRUCTURAL_MIXTURE");output.add("mixture",clean(gson.toJsonTree(FiniteMixture.fit(data,names,edges,options,null))));}return output;}
    private static JsonObject modelCompare(JsonObject input){var result=org.brightstats.stats.ModelComparison.compare(matrix(input.getAsJsonArray("observations")),org.brightstats.data.ModelComparisonInput.model(input.get("first")),org.brightstats.data.ModelComparisonInput.model(input.get("second")),org.brightstats.data.ModelComparisonInput.validation(input.get("validation")),input.has("confidence")?input.get("confidence").getAsDouble():.95,null);return clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(result)).getAsJsonObject();}
    private static JsonObject higherOrder(JsonObject input,boolean bootstrap,boolean prepare){double[][] data=matrix(input.getAsJsonArray("observations"));var blocks=PlsModelInput.blocks(input.get("blocks"));var edges=PlsModelInput.edges(input.get("edges"));var options=PlsModelInput.options(input.get("options"));var specification=HigherOrderInput.specification(input.get("specification"));var output=new JsonObject();var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();output.addProperty("estimator","DISJOINT_TWO_STAGE_HIGHER_ORDER_PLS");if(bootstrap){var supplied=input.has("bootstrap")?input.getAsJsonObject("bootstrap").deepCopy():new JsonObject();if(!supplied.has("convention"))supplied.addProperty("convention","STANDARD");var result=HigherOrderPls.bootstrap(data,blocks,edges,options,specification,bootstrapOptions(supplied),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else{var fit=HigherOrderPls.fit(data,blocks,edges,options,specification);if(prepare)return HigherOrderProjects.create(input,fit);output.add("fit",clean(gson.toJsonTree(fit)));}return output;}
    private static JsonObject nonlinear(JsonObject input,boolean bootstrap){double[][] data=matrix(input.getAsJsonArray("observations"));var blocks=PlsModelInput.blocks(input.get("blocks"));var edges=PlsModelInput.edges(input.get("edges"));var options=PlsModelInput.options(input.get("options"));var specification=NonlinearInput.specification(input.get("specification"));var output=new JsonObject();var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();output.addProperty("estimator","TWO_STAGE_NONLINEAR_PLS");if(bootstrap){var supplied=input.has("bootstrap")?input.getAsJsonObject("bootstrap").deepCopy():new JsonObject();if(!supplied.has("convention"))supplied.addProperty("convention","STANDARD");var result=NonlinearPls.bootstrap(data,blocks,edges,options,specification,bootstrapOptions(supplied),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else output.add("fit",clean(gson.toJsonTree(NonlinearPls.fit(data,blocks,edges,options,specification))));return output;}
    private static JsonObject copula(JsonObject input,boolean bootstrap){double[][] predictors=matrix(input.getAsJsonArray("predictors"));double[] response=vector(input.getAsJsonArray("response"));var model=CopulaInput.model(input.get("model"));JsonObject output=new JsonObject();var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();output.addProperty("estimator","CONTINUOUS_GAUSSIAN_COPULA_OLS");if(bootstrap){var supplied=input.has("bootstrap")?input.getAsJsonObject("bootstrap").deepCopy():new JsonObject();if(!supplied.has("convention"))supplied.addProperty("convention","STANDARD");var result=CopulaRegression.bootstrap(predictors,response,model,bootstrapOptions(supplied),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else output.add("fit",clean(gson.toJsonTree(CopulaRegression.fit(predictors,response,model))));return output;}
    private static JsonObject process(JsonObject input,boolean bootstrap){double[][] data=matrix(input.getAsJsonArray("observations"));List<String> names=input.has("names")?Arrays.asList(new Gson().fromJson(input.get("names"),String[].class)):java.util.stream.IntStream.range(0,data[0].length).mapToObj(j->"X"+(j+1)).toList();var model=ProcessInput.model(input.get("model"));var probes=ProcessInput.probes(input.get("probes"));var supplied=input.has("options")?input.getAsJsonObject("options"):new JsonObject();var defaults=PathProcess.Options.defaults();var options=new PathProcess.Options(supplied.has("metric")?PathProcess.Metric.valueOf(supplied.get("metric").getAsString()):defaults.metric(),supplied.has("covariance")?RobustRegression.Covariance.valueOf(supplied.get("covariance").getAsString()):defaults.covariance(),supplied.has("confidence")?supplied.get("confidence").getAsDouble():defaults.confidence(),!supplied.has("considerControls")||supplied.get("considerControls").getAsBoolean());var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();JsonObject output=new JsonObject();output.addProperty("estimator","OBSERVED_CONDITIONAL_PROCESS");if(bootstrap){var result=PathProcess.bootstrap(data,names,model,options,probes,componentBootstrapOptions(input),null);output.add("process",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else output.add("process",clean(gson.toJsonTree(PathProcess.fit(data,names,model,options,probes))));return output;}
    private static JsonObject tetrads(JsonObject input){var prepared=input.deepCopy();if(!prepared.has("edges"))prepared.add("edges",new JsonArray());for(var value:prepared.getAsJsonArray("blocks"))if(!value.getAsJsonObject().has("mode"))value.getAsJsonObject().addProperty("mode","A");var source=plsInput(prepared);var supplied=input.has("tetrads")?input.getAsJsonObject("tetrads"):new JsonObject();var defaults=TetradAnalysis.Options.defaults();var options=new TetradAnalysis.Options(supplied.has("samples")?supplied.get("samples").getAsBigDecimal().intValueExact():defaults.samples(),supplied.has("seed")?supplied.get("seed").getAsBigDecimal().longValueExact():defaults.seed(),supplied.has("significance")?supplied.get("significance").getAsDouble():defaults.significance(),supplied.has("convention")?RegressionBootstrap.Convention.valueOf(supplied.get("convention").getAsString()):defaults.convention(),!supplied.has("twoSided")||supplied.get("twoSided").getAsBoolean(),supplied.has("selection")?TetradAnalysis.Selection.valueOf(supplied.get("selection").getAsString()):defaults.selection(),supplied.has("retainDraws")&&supplied.get("retainDraws").getAsBoolean());List<String> names=input.has("names")?Arrays.asList(new Gson().fromJson(input.get("names"),String[].class)):java.util.stream.IntStream.range(0,source.data()[0].length).mapToObj(j->"X"+(j+1)).toList();var fit=TetradAnalysis.fit(source.data(),names,source.blocks(),options,null);JsonObject output=new JsonObject();output.addProperty("estimator","CONFIRMATORY_TETRADS");output.add("tetrads",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(fit)));return output;}
    private static JsonObject ipma(JsonObject input){var source=plsInput(input);var supplied=input.getAsJsonObject("ipma");int target=supplied.get("target").getAsBigDecimal().intValueExact();var defaults=ImportancePerformance.Options.defaults(target);List<ImportancePerformance.Range> ranges=new ArrayList<>();if(supplied.has("ranges"))for(var entry:supplied.getAsJsonArray("ranges")){var range=entry.getAsJsonObject();ranges.add(new ImportancePerformance.Range(range.get("column").getAsBigDecimal().intValueExact(),range.get("minimum").getAsDouble(),range.get("maximum").getAsDouble(),range.has("reverse")&&range.get("reverse").getAsBoolean()));}var options=new ImportancePerformance.Options(target,supplied.has("depth")?ImportancePerformance.Depth.valueOf(supplied.get("depth").getAsString()):defaults.depth(),supplied.has("units")?ImportancePerformance.Units.valueOf(supplied.get("units").getAsString()):defaults.units(),ranges);List<String> names=input.has("names")?Arrays.asList(new Gson().fromJson(input.get("names"),String[].class)):java.util.stream.IntStream.range(0,source.data()[0].length).mapToObj(j->"X"+(j+1)).toList();var result=ImportancePerformance.fit(source.data(),names,source.blocks(),source.edges(),source.options(),options);JsonObject output=new JsonObject();output.addProperty("estimator","IMPORTANCE_PERFORMANCE");output.add("ipma",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(result)));return output;}
    private static JsonObject nca(JsonObject input,boolean permutation){
        double[][] x=matrix(input.getAsJsonArray("predictors"));double[] y=vector(input.getAsJsonArray("response"));int p=org.brightstats.stats.Matrices.width(x,3);var names=input.has("conditionNames")?java.util.Arrays.asList(new com.google.gson.Gson().fromJson(input.get("conditionNames"),String[].class)):java.util.stream.IntStream.range(0,p).mapToObj(j->"X"+(j+1)).toList();String outcome=input.has("outcomeName")?input.get("outcomeName").getAsString():"Y";
        var supplied=input.has("options")?input.getAsJsonObject("options"):new JsonObject();var defaults=Nca.Options.defaults();var scopes=supplied.has("conditionScopes")?java.util.Arrays.asList(new com.google.gson.Gson().fromJson(supplied.get("conditionScopes"),Nca.Bounds[].class)):java.util.List.<Nca.Bounds>of();var settings=new Nca.Options(supplied.has("steps")?supplied.get("steps").getAsBigDecimal().intValueExact():defaults.steps(),scopes,supplied.has("outcomeScope")&&!supplied.get("outcomeScope").isJsonNull()?new com.google.gson.Gson().fromJson(supplied.get("outcomeScope"),Nca.Bounds.class):null,supplied.has("exclusionLimit")?supplied.get("exclusionLimit").getAsDouble():defaults.exclusionLimit(),supplied.has("exclusionStep")?supplied.get("exclusionStep").getAsDouble():defaults.exclusionStep(),supplied.has("accuracyTolerance")?supplied.get("accuracyTolerance").getAsDouble():defaults.accuracyTolerance());
        var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();JsonObject output=new JsonObject();output.addProperty("estimator",permutation?"NCA_PERMUTATION":"NCA_CE_CR_FDH");if(!permutation){output.add("fit",clean(gson.toJsonTree(Nca.fit(x,y,names,outcome,settings))));return output;}
        var options=input.has("permutation")?input.getAsJsonObject("permutation"):new JsonObject();var fallback=NcaPermutation.Options.defaults();var selected=new NcaPermutation.Options(options.has("samples")?options.get("samples").getAsBigDecimal().intValueExact():fallback.samples(),options.has("seed")?options.get("seed").getAsBigDecimal().longValueExact():fallback.seed(),options.has("significance")?options.get("significance").getAsDouble():fallback.significance(),options.has("convention")?RegressionBootstrap.Convention.valueOf(options.get("convention").getAsString()):fallback.convention(),options.has("retainDraws")&&options.get("retainDraws").getAsBoolean());var result=NcaPermutation.fit(x,y,names,outcome,settings,selected,null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("permutation",clean(gson.toJsonTree(result)));return output;
    }
    private static JsonObject regress(JsonObject input) {
        double[][] predictors = matrix(input.getAsJsonArray("predictors"));
        double[] response = vector(input.getAsJsonArray("response"));
        var fit = OlsRegression.fit(predictors, response);
        JsonObject output = new JsonObject();
        output.addProperty("estimator", "OLS_WITH_INTERCEPT");
        output.addProperty("observations", fit.observations());
        output.addProperty("residualDegreesOfFreedom", fit.residualDegreesOfFreedom());
        output.add("coefficients", array(fit.coefficients()));
        output.addProperty("coefficientOrder", "intercept followed by input predictor order");
        output.add("standardErrors", array(fit.standardErrors()));
        output.add("residualSumOfSquares", number(fit.residualSumOfSquares()));
        output.add("rSquared", number(fit.rSquared()));
        output.add("adjustedRSquared", number(fit.adjustedRSquared()));
        output.add("inference", clean(new GsonBuilder().serializeSpecialFloatingPointValues().create()
                .toJsonTree(Inference.regression(fit,input.has("confidence")?input.get("confidence").getAsDouble():.95))));
        if(input.has("covariance")) {
            var robust=RobustRegression.fit(predictors,response,RobustRegression.Covariance.valueOf(input.get("covariance").getAsString()),input.has("confidence")?input.get("confidence").getAsDouble():.95);
            var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();output.add("robust",clean(gson.toJsonTree(robust)));
            output.add("inference",clean(gson.toJsonTree(new Inference.Regression(robust.confidence(),fit.residualDegreesOfFreedom(),robust.inferenceMethod(),robust.terms()))));
            JsonArray errors=new JsonArray();for(var term:robust.terms())errors.add(number(term.standardError()));output.add("standardErrors",errors);output.addProperty("estimator","OLS_"+robust.covariance());
        }
        return output;
    }
    private static Bootstrap.Options independentBootstrap(JsonObject input){var supplied=input.has("bootstrap")?input.getAsJsonObject("bootstrap").deepCopy():new JsonObject();if(!supplied.has("convention"))supplied.addProperty("convention","STANDARD");return bootstrapOptions(supplied);}
    private static JsonObject originRegression(JsonObject input,boolean bootstrap){double[][] x=matrix(input.getAsJsonArray("predictors"));double[] y=vector(input.getAsJsonArray("response"));var covariance=input.has("covariance")?RobustRegression.Covariance.valueOf(input.get("covariance").getAsString()):RobustRegression.Covariance.CLASSICAL;double confidence=input.has("confidence")?input.get("confidence").getAsDouble():.95;var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();JsonObject output=new JsonObject();output.addProperty("estimator","ORIGIN_OLS");if(bootstrap){var result=org.brightstats.stats.OriginRegression.bootstrap(x,y,covariance,confidence,independentBootstrap(input),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else output.add("fit",clean(gson.toJsonTree(org.brightstats.stats.OriginRegression.fit(x,y,covariance,confidence))));return output;}
    private static JsonObject semProducts(JsonObject input,boolean bootstrap,boolean prepare){double[][] data=matrix(input.getAsJsonArray("observations"));var specification=org.brightstats.data.SemProductInput.specification(input.get("specification"));var options=semOptions(input,false);var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();JsonObject output=new JsonObject();output.addProperty("estimator","CBSEM_MATCHED_PRODUCT_INDICATORS_DMC");if(prepare){output.add("prepared",clean(gson.toJsonTree(org.brightstats.stats.SemProductIndicators.prepare(data,specification))));return output;}if(bootstrap){var result=org.brightstats.stats.SemProductIndicators.bootstrap(data,specification,options,independentBootstrap(input),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else output.add("fit",clean(gson.toJsonTree(org.brightstats.stats.SemProductIndicators.fit(data,specification,options))));return output;}
    private static JsonObject componentPolynomial(JsonObject input,boolean bootstrap){double[][] data=matrix(input.getAsJsonArray("observations"));var model=org.brightstats.data.ComponentCopulaInput.model(input.get("model"));var specification=NonlinearInput.specification(input.get("specification"));var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();JsonObject output=new JsonObject();output.addProperty("estimator",model.estimator()+"_COMPONENT_POLYNOMIAL");if(bootstrap){var result=org.brightstats.stats.ComponentPolynomial.bootstrap(data,model,specification,independentBootstrap(input),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else output.add("fit",clean(gson.toJsonTree(org.brightstats.stats.ComponentPolynomial.fit(data,model,specification))));return output;}
    private static JsonObject observedPolynomial(JsonObject input,boolean bootstrap){double[][] data=matrix(input.getAsJsonArray("observations"));var model=org.brightstats.data.PolynomialInput.observed(input.get("model"));var options=org.brightstats.data.PolynomialInput.options(input.get("options"));var specification=NonlinearInput.specification(input.get("specification"));var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();JsonObject output=new JsonObject();output.addProperty("estimator","OBSERVED_POLYNOMIAL");if(bootstrap){var result=org.brightstats.stats.ObservedPolynomial.bootstrap(data,model,options,specification,independentBootstrap(input),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else output.add("fit",clean(gson.toJsonTree(org.brightstats.stats.ObservedPolynomial.fit(data,model,options,specification))));return output;}
    private static JsonObject transformData(JsonObject input){var project=input.has("project")?input.getAsJsonObject("project").deepCopy():input.deepCopy();List<String> names=new ArrayList<>();for(var value:project.getAsJsonArray("columnNames"))names.add(value.getAsString());double[][] data=matrix(project.getAsJsonArray("observations"));var specification=new Gson().fromJson(input.get("specification"),org.brightstats.data.DataTransforms.Specification.class);var result=org.brightstats.data.DataTransforms.append(project.has("dataPreparation")?project.getAsJsonObject("dataPreparation"):null,names,data,specification);var gson=new Gson();project.addProperty("format","bright-stats-project");project.addProperty("formatVersion",1);project.remove("specification");project.add("columnNames",gson.toJsonTree(result.prepared().columns()));project.add("observations",gson.toJsonTree(result.prepared().values()));project.add("dataPreparation",result.history());if(!project.has("blocks"))project.add("blocks",new JsonArray());if(!project.has("edges"))project.add("edges",new JsonArray());var output=new JsonObject();output.addProperty("estimator","FIXED_ROW_TRANSFORMATION");output.add("project",project);output.add("warnings",gson.toJsonTree(result.warnings()));return output;}
    private static JsonObject latentInteraction(JsonObject input,boolean bootstrap){var data=matrix(input.getAsJsonArray("observations"));var model=org.brightstats.data.LatentInteractionInput.model(input.get("model"));var options=org.brightstats.data.LatentInteractionInput.options(input.get("options"));var gson=new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create();var output=new JsonObject();output.addProperty("estimator",options.predictorDistribution()==org.brightstats.stats.LatentInteractionSem.PredictorDistribution.NORMAL?"GAUSSIAN_LATENT_INTERACTION_ML":"STUDENT_PREDICTOR_LATENT_INTERACTION_ML");org.brightstats.stats.LatentInteractionSem.Fit point;if(bootstrap){var b=input.has("bootstrap")?input.getAsJsonObject("bootstrap").deepCopy():new JsonObject();if(!b.has("convention"))b.addProperty("convention","STANDARD");var result=org.brightstats.stats.LatentInteractionSem.bootstrap(data,model,options,bootstrapOptions(b),null);point=result.point();output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else point=org.brightstats.stats.LatentInteractionSem.fit(data,model,options);output.add("fit",clean(gson.toJsonTree(point)));if(input.has("compareLinear")&&input.get("compareLinear").getAsBoolean()){var baseline=org.brightstats.stats.LatentInteractionSem.fit(data,new org.brightstats.stats.LatentInteractionSem.Model(model.predictors(),model.outcome(),List.of()),options);output.add("linearBaseline",clean(gson.toJsonTree(baseline)));output.add("comparison",clean(gson.toJsonTree(org.brightstats.stats.LatentInteractionSem.compare(baseline,point))));}return output;}
    private static JsonObject hierarchy(JsonObject input,boolean bootstrap,boolean factors){var data=matrix(input.getAsJsonArray("observations"));var model=org.brightstats.data.HierarchyInput.model(input.get("model"));org.brightstats.stats.HierarchyPipeline.validate(data[0].length,model);var gson=new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create();var output=new JsonObject();output.addProperty("estimator",factors?"HIERARCHICAL_PLSC_MOMENTS":"MULTILEVEL_COMPOSITE_HIERARCHY");if(bootstrap){var b=input.has("bootstrap")?input.getAsJsonObject("bootstrap").deepCopy():new JsonObject();if(!b.has("convention"))b.addProperty("convention","STANDARD");if(factors){var result=org.brightstats.stats.FactorHierarchy.bootstrap(data,model,bootstrapOptions(b),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else{var result=org.brightstats.stats.HierarchyPipeline.bootstrap(data,model,bootstrapOptions(b),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}}else output.add("fit",clean(gson.toJsonTree(factors?org.brightstats.stats.FactorHierarchy.fit(data,model):org.brightstats.stats.HierarchyPipeline.fit(data,model))));return output;}
    private static JsonObject pairwise(JsonObject input,String kind,boolean bootstrap){var raw=org.brightstats.data.MissingInput.matrix(input.get("observations"));List<String> names=new ArrayList<>();for(var name:input.getAsJsonArray("columns"))names.add(name.getAsString());var gson=new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create();JsonObject output=new JsonObject();output.addProperty("estimator","PAIRWISE_"+kind);var supplied=input.has("bootstrap")?input.getAsJsonObject("bootstrap").deepCopy():new JsonObject();if(!supplied.has("convention"))supplied.addProperty("convention","STANDARD");if(kind.equals("MATRICES"))output.add("fit",clean(gson.toJsonTree(org.brightstats.stats.PairwiseMoments.fit(raw,names))));else if(kind.equals("COMPONENT")){var model=org.brightstats.data.ComponentCopulaInput.model(input.get("model"));boolean consistent=input.has("consistent")&&input.get("consistent").getAsBoolean();if(bootstrap){var result=org.brightstats.stats.PairwiseComponents.bootstrap(raw,names,model,consistent,bootstrapOptions(supplied),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else output.add("fit",clean(gson.toJsonTree(org.brightstats.stats.PairwiseComponents.fit(raw,names,model,consistent))));}else{var model=semModel(input.get("model"));var options=semOptions(input,false);if(bootstrap){var result=org.brightstats.stats.PairwiseSem.bootstrap(raw,names,model,options,bootstrapOptions(supplied),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else output.add("fit",clean(gson.toJsonTree(org.brightstats.stats.PairwiseSem.fit(raw,names,model,options))));}return output;}
    private static JsonObject adjustedCopula(JsonObject input,boolean bootstrap,boolean component){double[][] data=matrix(input.getAsJsonArray("observations"));var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();var output=new JsonObject();var supplied=input.has("bootstrap")?input.getAsJsonObject("bootstrap").deepCopy():new JsonObject();if(!supplied.has("convention"))supplied.addProperty("convention","STANDARD");if(component){var model=org.brightstats.data.ComponentCopulaInput.model(input.get("model"));var specification=org.brightstats.data.AdjustedCopulaInput.component(input.get("specification"));output.addProperty("estimator",model.estimator()+"_ADJUSTED_COPULA");if(bootstrap){var result=org.brightstats.stats.ComponentAdjustedCopula.bootstrap(data,model,specification,bootstrapOptions(supplied),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));output.add("wald",clean(gson.toJsonTree(result.wald())));}else output.add("fit",clean(gson.toJsonTree(org.brightstats.stats.ComponentAdjustedCopula.fit(data,model,specification))));}else{var model=org.brightstats.data.AdjustedCopulaInput.model(input.get("system"));output.addProperty("estimator","OBSERVED_ADJUSTED_COPULA");if(bootstrap){var result=org.brightstats.stats.AdjustedCopula.bootstrap(data,model,bootstrapOptions(supplied),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));output.add("wald",clean(gson.toJsonTree(result.wald())));}else output.add("fit",clean(gson.toJsonTree(org.brightstats.stats.AdjustedCopula.fit(data,model))));}return output;}
    private static JsonObject plsScales(JsonObject input,boolean bootstrap){double[][] data=matrix(input.getAsJsonArray("observations"));var blocks=org.brightstats.data.PlsModelInput.blocks(input.get("blocks"));var edges=org.brightstats.data.PlsModelInput.edges(input.get("edges"));var options=org.brightstats.data.PlsModelInput.options(input.get("options"));var metric=input.has("metric")?org.brightstats.stats.PlsScales.Metric.valueOf(input.get("metric").getAsString()):org.brightstats.stats.PlsScales.Metric.STANDARDIZED;var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();var output=new JsonObject();output.addProperty("estimator","PLS_SCALES");if(bootstrap){var supplied=input.has("bootstrap")?input.getAsJsonObject("bootstrap").deepCopy():new JsonObject();if(!supplied.has("convention"))supplied.addProperty("convention","STANDARD");var result=org.brightstats.stats.PlsScales.bootstrap(data,blocks,edges,options,metric,bootstrapOptions(supplied),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else output.add("fit",clean(gson.toJsonTree(org.brightstats.stats.PlsScales.fit(data,blocks,edges,options,metric))));return output;}
    private static JsonObject componentCopula(JsonObject input,boolean bootstrap){double[][] data=matrix(input.getAsJsonArray("observations"));var model=org.brightstats.data.ComponentCopulaInput.model(input.get("model"));var specification=org.brightstats.data.ComponentCopulaInput.specification(input.get("specification"));var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();var output=new JsonObject();output.addProperty("estimator",model.estimator()+"_COMPONENT_COPULA");if(bootstrap){var supplied=input.has("bootstrap")?input.getAsJsonObject("bootstrap").deepCopy():new JsonObject();if(!supplied.has("convention"))supplied.addProperty("convention","STANDARD");var result=org.brightstats.stats.ComponentCopula.bootstrap(data,model,specification,bootstrapOptions(supplied),null);output.add("fit",clean(gson.toJsonTree(result.point())));output.add("bootstrap",clean(gson.toJsonTree(result.inference())));}else output.add("fit",clean(gson.toJsonTree(org.brightstats.stats.ComponentCopula.fit(data,model,specification))));return output;}
    private static JsonObject modelPca(JsonObject input){double[][] data=matrix(input.getAsJsonArray("observations"));List<String> names=new ArrayList<>();for(var name:input.getAsJsonArray("columnNames")){if(!name.isJsonPrimitive()||!name.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Model PCA column names must be strings.");names.add(name.getAsString());}var source=org.brightstats.data.ModelPcaInput.source(input,names);var options=org.brightstats.data.ModelPcaInput.options(input.get("options"));var output=new JsonObject();output.addProperty("estimator",source.context()+"_MODEL_PCA");output.add("fit",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(org.brightstats.stats.ModelPca.fit(data,names,source,options,null))));return output;}
    private static JsonObject pca(JsonObject input) {
        double[][] data=matrix(input.getAsJsonArray("observations"));int width=org.brightstats.stats.Matrices.width(data,2);int components=input.has("components")?input.get("components").getAsBigDecimal().intValueExact():Math.min(3,width);
        var fit=PrincipalComponents.fit(data,!input.has("correlation")||input.get("correlation").getAsBoolean(),components);JsonObject output=new JsonObject();output.addProperty("estimator","PCA");output.add("fit",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(fit)));if(input.has("rotation")){var options=org.brightstats.data.PcaRotationInput.options(input.get("rotation"));output.add("rotation",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(org.brightstats.stats.PcaRotation.rotate(fit,options))));}return output;
    }
    private static JsonObject semInfluence(JsonObject input){var options=org.brightstats.data.SensitivityInput.influence(input.get("options"));var fit=org.brightstats.stats.SemSensitivity.influence(org.brightstats.data.MissingInput.matrix(input.get("observations")),options.unit()==org.brightstats.stats.SemSensitivity.Unit.CLUSTER?org.brightstats.data.ClusterInput.ids(input.get("clusterIds")):null,org.brightstats.data.SemModelInput.model(input.get("model")),options,null);JsonObject out=new JsonObject();out.addProperty("estimator","SEM_EXACT_DELETION_INFLUENCE");out.add("fit",clean(new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create().toJsonTree(fit)));return out;}
    private static JsonObject semMultiverse(JsonObject input){var fit=org.brightstats.stats.SemSensitivity.multiverse(org.brightstats.data.MissingInput.matrix(input.get("observations")),org.brightstats.data.SensitivityInput.candidates(input.get("candidates")),org.brightstats.data.StatisticalInput.bool(input,"completeCases",false),null);JsonObject out=new JsonObject();out.addProperty("estimator","SEM_COMMON_SAMPLE_SPECIFICATIONS");out.add("fit",clean(new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create().toJsonTree(fit)));return out;}
    private static JsonObject bayesianSem(JsonObject input){var fit=org.brightstats.stats.BayesianSem.fit(org.brightstats.data.MissingInput.matrix(input.get("observations")),org.brightstats.data.SemModelInput.model(input.get("model")),org.brightstats.data.BayesianInput.options(input.get("options")),null);JsonObject out=new JsonObject();out.addProperty("estimator","BAYESIAN_GAUSSIAN_SEM_METROPOLIS");out.add("fit",clean(new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create().toJsonTree(fit)));return out;}
    private static JsonObject regularizedSem(JsonObject input){var fit=org.brightstats.stats.RegularizedSem.fit(org.brightstats.data.MissingInput.matrix(input.get("observations")),org.brightstats.data.SemModelInput.model(input.get("model")),org.brightstats.data.RegularizedInput.options(input.get("options")),null);JsonObject out=new JsonObject();out.addProperty("estimator","ELASTIC_NET_SEM_NESTED_CV");out.add("fit",clean(new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create().toJsonTree(fit)));return out;}
    private static JsonObject clusterSem(JsonObject input){var fit=org.brightstats.stats.ClusterSem.fit(org.brightstats.data.MissingInput.matrix(input.get("observations")),org.brightstats.data.ClusterInput.ids(input.get("clusterIds")),org.brightstats.data.SemModelInput.model(input.get("model")),org.brightstats.data.ClusterInput.options(input.get("options")),null);JsonObject out=new JsonObject();out.addProperty("estimator","CLUSTER_ROBUST_MARGINAL_SEM");out.add("fit",clean(new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create().toJsonTree(fit)));return out;}
    private static JsonObject twoLevelSem(JsonObject input){var fit=org.brightstats.stats.TwoLevelSem.fit(org.brightstats.data.MissingInput.matrix(input.get("observations")),org.brightstats.data.ClusterInput.ids(input.get("clusterIds")),org.brightstats.data.ClusterInput.specification(input.get("specification")),org.brightstats.data.ClusterInput.estimator(input.get("options")));JsonObject out=new JsonObject();out.addProperty("estimator","TWO_LEVEL_CONTINUOUS_GAUSSIAN_SEM");out.add("fit",clean(new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create().toJsonTree(fit)));return out;}
    private static JsonObject simulate(JsonObject input){var result=org.brightstats.stats.SemSimulation.run(org.brightstats.data.SimulationInput.design(input.get("design")),null);JsonObject out=new JsonObject();out.addProperty("estimator","SEM_MONTE_CARLO_POWER_PRECISION");out.add("study",clean(new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create().toJsonTree(result)));return out;}

    private static JsonObject ordinalSem(JsonObject input){var names=org.brightstats.data.OrdinalInput.names(input.get("names"));var levels=org.brightstats.data.OrdinalInput.levels(input.get("levels"));var codes=org.brightstats.data.OrdinalInput.codes(input.get("codes"));var model=org.brightstats.data.SemModelInput.model(input.get("model"));var result=org.brightstats.stats.OrdinalSem.fit(codes,names,levels,model,org.brightstats.data.OrdinalInput.options(input.get("options")));JsonObject out=new JsonObject();out.addProperty("estimator","ORDINAL_POLYCHORIC_DWLS_MV");out.add("fit",clean(new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create().toJsonTree(result)));return out;}
    private static JsonObject imputeData(JsonObject input){var result=org.brightstats.stats.GaussianImputation.impute(org.brightstats.data.MissingInput.matrix(input.get("observations")),org.brightstats.data.ImputationInput.imputation(input.get("options")),null);JsonObject out=new JsonObject();out.addProperty("estimator","CONTINUOUS_JOINT_NORMAL_IMPUTATION");out.add("imputation",clean(new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create().toJsonTree(result)));return out;}
    private static JsonObject imputedSem(JsonObject input){var result=org.brightstats.stats.ImputedSem.fit(org.brightstats.data.MissingInput.matrix(input.get("observations")),org.brightstats.data.SemModelInput.model(input.get("model")),org.brightstats.data.ImputationInput.options(input.get("options")),null);JsonObject out=new JsonObject();out.addProperty("estimator","MULTIPLE_IMPUTATION_SEM_RUBIN");out.add("fit",clean(new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create().toJsonTree(result)));return out;}
    private static JsonObject fiml(JsonObject input){var supplied=input.has("options")?input.getAsJsonObject("options"):new JsonObject();for(String key:supplied.keySet())if(!java.util.Set.of("maxIterations","gradientTolerance","functionTolerance","confidence").contains(key))throw new IllegalArgumentException("Unsupported FIML option: "+key);var defaults=org.brightstats.stats.FimlSem.Options.defaults();var options=new org.brightstats.stats.FimlSem.Options(supplied.has("maxIterations")?supplied.get("maxIterations").getAsBigDecimal().intValueExact():defaults.maxIterations(),supplied.has("gradientTolerance")?supplied.get("gradientTolerance").getAsDouble():defaults.gradientTolerance(),supplied.has("functionTolerance")?supplied.get("functionTolerance").getAsDouble():defaults.functionTolerance(),supplied.has("confidence")?supplied.get("confidence").getAsDouble():defaults.confidence());var result=org.brightstats.stats.FimlSem.fit(org.brightstats.data.MissingInput.matrix(input.get("observations")),org.brightstats.data.SemModelInput.model(input.get("model")),options);JsonObject out=new JsonObject();out.addProperty("estimator","GAUSSIAN_OBSERVED_DATA_FIML");out.add("fit",clean(new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create().toJsonTree(result)));return out;}
    private static JsonObject sem(JsonObject input){
        JsonObject output=new JsonObject();output.addProperty("estimator","CBSEM_ML_RAM");output.add("fit",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(semFit(input))));return output;
    }
    private static JsonObject gsca(JsonObject input){
        JsonObject output=new JsonObject();output.addProperty("estimator","GSCA_ALTERNATING_LEAST_SQUARES");output.add("fit",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(gscaFit(input))));return output;
    }
    private static Gsca.Fit gscaFit(JsonObject input){
        var supplied=input.has("options")?input.getAsJsonObject("options"):new JsonObject();var defaults=Gsca.Options.defaults();var options=new Gsca.Options(supplied.has("maxIterations")?supplied.get("maxIterations").getAsBigDecimal().intValueExact():defaults.maxIterations(),supplied.has("tolerance")?supplied.get("tolerance").getAsDouble():defaults.tolerance());
        var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();var model=gson.fromJson(input.get("model"),Gsca.Model.class);return Gsca.fit(matrix(input.getAsJsonArray("observations")),model,options);
    }
    private static Bootstrap.Options componentBootstrapOptions(JsonObject input){
        var supplied=input.has("bootstrap")?input.getAsJsonObject("bootstrap"):new JsonObject();var convention=supplied.has("convention")?RegressionBootstrap.Convention.valueOf(supplied.get("convention").getAsString()):RegressionBootstrap.Convention.REFERENCE_4_1_2_1;
        return new Bootstrap.Options(supplied.has("samples")?supplied.get("samples").getAsBigDecimal().intValueExact():5000,supplied.has("seed")?supplied.get("seed").getAsBigDecimal().longValueExact():convention==RegressionBootstrap.Convention.STANDARD?42:org.brightstats.stats.ReferenceResampling.DEFAULT_SEED,supplied.has("confidence")?supplied.get("confidence").getAsDouble():.95,supplied.has("interval")?BootstrapIntervals.Method.valueOf(supplied.get("interval").getAsString()):BootstrapIntervals.Method.PERCENTILE,convention,!supplied.has("twoSided")||supplied.get("twoSided").getAsBoolean());
    }
    private static JsonObject semBootstrap(JsonObject input){
        if(!input.has("observations"))throw new IllegalArgumentException("SEM case bootstrapping requires raw observations, not only covariance input.");var point=semFit(input);var fitted=SemBootstrap.fit(matrix(input.getAsJsonArray("observations")),point.model(),point.options(),componentBootstrapOptions(input),null);var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();JsonObject output=new JsonObject();output.addProperty("estimator","CBSEM_CASE_BOOTSTRAP");output.add("fit",clean(gson.toJsonTree(fitted.point())));output.add("bootstrap",clean(gson.toJsonTree(fitted.inference())));output.addProperty("improperFits",fitted.improperReplicates());return output;
    }
    private static JsonObject gscaBootstrap(JsonObject input){
        var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();var point=gscaFit(input);var fitted=GscaBootstrap.fit(matrix(input.getAsJsonArray("observations")),point.model(),point.options(),componentBootstrapOptions(input),null);JsonObject output=new JsonObject();output.addProperty("estimator","GSCA_CASE_BOOTSTRAP");output.add("fit",clean(gson.toJsonTree(fitted.point())));output.add("bootstrap",clean(gson.toJsonTree(fitted.inference())));return output;
    }
    private static JsonObject semCompare(JsonObject input){
        var restricted=semFit(input.getAsJsonObject("restricted"));var unrestricted=semFit(input.getAsJsonObject("unrestricted"));var comparison=SemComparison.compare(restricted,unrestricted);
        JsonObject output=new JsonObject();output.addProperty("estimator","NESTED_CBSEM_ML_COMPARISON");output.add("fit",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(comparison)));return output;
    }
    private static CovarianceSem.Fit semFit(JsonObject input){
        var options=semOptions(input,false);var model=semModel(input.get("model"));CovarianceSem.Fit fit;
        if(input.has("observations"))fit=CovarianceSem.fit(matrix(input.getAsJsonArray("observations")),model,options);
        else{
            double[][] covariance=matrix(input.getAsJsonArray("covariance"));int n=input.get("sampleSize").getAsBigDecimal().intValueExact();
            if(options.meanStructure()&&!input.has("means"))throw new IllegalArgumentException("Covariance input needs means when meanStructure is enabled.");
            fit=CovarianceSem.fitCovariance(covariance,input.has("means")?vector(input.getAsJsonArray("means")):new double[covariance.length],n,model,options);
        }
        return fit;
    }
    private static JsonObject logistic(JsonObject input) {
        var options=input.has("options")?input.getAsJsonObject("options"):new JsonObject();var settings=new LogisticRegression.Options(options.has("maxIterations")?options.get("maxIterations").getAsBigDecimal().intValueExact():100,
                options.has("tolerance")?options.get("tolerance").getAsDouble():1e-8,options.has("confidence")?options.get("confidence").getAsDouble():.95,options.has("threshold")?options.get("threshold").getAsDouble():.5,
                options.has("eventClass")&&!options.get("eventClass").isJsonNull()?options.get("eventClass").getAsDouble():null);
        var fit=LogisticRegression.fit(matrix(input.getAsJsonArray("predictors")),vector(input.getAsJsonArray("response")),settings);JsonObject output=new JsonObject();output.addProperty("estimator","BINARY_LOGISTIC_ML");output.add("fit",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(fit)));return output;
    }
    private static JsonObject regressionBootstrap(JsonObject input) {
        var supplied=input.has("options")?input.getAsJsonObject("options"):new JsonObject();
        var convention=supplied.has("convention")?RegressionBootstrap.Convention.valueOf(supplied.get("convention").getAsString()):RegressionBootstrap.Convention.REFERENCE_4_1_2_1;
        var options=new RegressionBootstrap.Options(supplied.has("samples")?supplied.get("samples").getAsBigDecimal().intValueExact():5000,
                supplied.has("seed")?supplied.get("seed").getAsBigDecimal().longValueExact():convention==RegressionBootstrap.Convention.STANDARD?42:org.brightstats.stats.ReferenceResampling.DEFAULT_SEED,
                supplied.has("confidence")?supplied.get("confidence").getAsDouble():.95,
                supplied.has("interval")?BootstrapIntervals.Method.valueOf(supplied.get("interval").getAsString()):BootstrapIntervals.Method.PERCENTILE,
                supplied.has("covariance")?RobustRegression.Covariance.valueOf(supplied.get("covariance").getAsString()):RobustRegression.Covariance.CLASSICAL,
                supplied.has("retainDraws")&&supplied.get("retainDraws").getAsBoolean(),
                convention,
                !supplied.has("twoSided")||supplied.get("twoSided").getAsBoolean());
        var fit=RegressionBootstrap.fit(matrix(input.getAsJsonArray("predictors")),vector(input.getAsJsonArray("response")),options,null);
        JsonObject output=new JsonObject();output.addProperty("estimator","REGRESSION_BOOTSTRAP");output.add("fit",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(fit)));return output;
    }

    private static JsonObject pls(JsonObject input,boolean predictive,boolean consistent) {
        var model=plsInput(input);double[][] data=model.data();var blocks=model.blocks();var edges=model.edges();var options=model.options();
        if(predictive) {
            JsonObject supplied=input.has("prediction")?input.getAsJsonObject("prediction"):new JsonObject();
            var settings=new Prediction.Options(supplied.has("scheme")?Prediction.Scheme.valueOf(supplied.get("scheme").getAsString()):Prediction.Scheme.K_FOLD,
                    supplied.has("folds")?supplied.get("folds").getAsBigDecimal().intValueExact():5,
                    supplied.has("repeats")?supplied.get("repeats").getAsBigDecimal().intValueExact():5,
                    supplied.has("testFraction")?supplied.get("testFraction").getAsDouble():.2,
                    supplied.has("seed")?supplied.get("seed").getAsBigDecimal().longValueExact():42);
            JsonObject output=new JsonObject();output.addProperty("estimator","PLS_PREDICTIVE_VALIDATION");
            var point=Prediction.evaluate(data,blocks,edges,options,settings,null);var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();output.add("prediction",clean(gson.toJsonTree(point)));output.add("predictiveTests",clean(gson.toJsonTree(org.brightstats.stats.PredictionInference.evaluate(point,input.has("predictionInferenceConfidence")?input.get("predictionInferenceConfidence").getAsDouble():.95))));return output;
        }
        if(consistent) {
            var fitted=ConsistentPls.fit(data,blocks,edges,options);JsonObject output=new JsonObject();output.addProperty("estimator","CONSISTENT_PLS");output.add("fit",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(fitted)));
            if(input.has("bootstrap")){var supplied=input.getAsJsonObject("bootstrap");var settings=bootstrapOptions(supplied);output.add("bootstrap",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(Bootstrap.fit(data,blocks,edges,options,settings,null,true))));}return output;
        }
        var fit = PlsSem.fit(data, blocks, edges, options);
        JsonObject output = new JsonObject();
        output.addProperty("estimator", "STANDARDIZED_LINEAR_PLS_SEM");
        output.addProperty("observations", data.length);
        output.addProperty("converged", fit.converged());
        output.addProperty("iterations", fit.iterations());
        output.add("finalWeightChange", number(fit.finalWeightChange()));
        JsonArray names = new JsonArray();
        for (var block : blocks) names.add(block.name());
        output.add("constructs", names);
        output.add("scores", array(fit.scores()));
        output.add("weights", array(fit.weights()));
        output.add("loadings", array(fit.loadings()));
        output.add("pathCoefficients", array(fit.pathCoefficients()));
        output.add("rSquared", array(fit.rSquared()));
        output.add("diagnostics", fit.converged() ? diagnostics(ModelDiagnostics.compute(data, blocks, edges, fit)) : JsonNull.INSTANCE);
        if(input.has("bootstrap")) {
            var values=input.getAsJsonObject("bootstrap");
            var settings=bootstrapOptions(values);
            output.add("bootstrap",clean(new GsonBuilder().serializeSpecialFloatingPointValues().create()
                    .toJsonTree(Bootstrap.fit(data,blocks,edges,options,settings,null))));
        }
        return output;
    }
    private static Bootstrap.Options bootstrapOptions(JsonObject values){
        var convention=values.has("convention")?RegressionBootstrap.Convention.valueOf(values.get("convention").getAsString()):RegressionBootstrap.Convention.REFERENCE_4_1_2_1;
        return new Bootstrap.Options(values.has("samples")?values.get("samples").getAsBigDecimal().intValueExact():5000,
                values.has("seed")?values.get("seed").getAsBigDecimal().longValueExact():convention==RegressionBootstrap.Convention.STANDARD?42:org.brightstats.stats.ReferenceResampling.DEFAULT_SEED,
                values.has("confidence")?values.get("confidence").getAsDouble():.95,
                values.has("interval")?BootstrapIntervals.Method.valueOf(values.get("interval").getAsString()):BootstrapIntervals.Method.PERCENTILE,
                convention,!values.has("twoSided")||values.get("twoSided").getAsBoolean());
    }
    private static JsonElement clean(JsonElement value) {
        if(value.isJsonObject()) { JsonObject result=new JsonObject(); value.getAsJsonObject().entrySet().forEach(e->result.add(e.getKey(),clean(e.getValue()))); return result; }
        if(value.isJsonArray()) { JsonArray result=new JsonArray(); value.getAsJsonArray().forEach(e->result.add(clean(e))); return result; }
        if(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && !Double.isFinite(value.getAsDouble()))return JsonNull.INSTANCE;
        return value.deepCopy();
    }

    private static JsonObject diagnostics(ModelDiagnostics.Fit fit) {
        JsonObject output = new JsonObject(); JsonArray reliability = new JsonArray();
        for (var row : fit.reliability()) {
            JsonObject item = new JsonObject(); item.add("alpha", number(row.alpha())); item.add("rhoA", number(row.rhoA()));
            item.add("rhoC", number(row.rhoC())); item.add("ave", number(row.ave())); reliability.add(item);
        }
        output.add("reliability", reliability); output.add("htmt", array(fit.htmt()));
        output.add("fornellLarcker", array(fit.fornellLarcker())); output.add("crossLoadings", array(fit.crossLoadings()));
        output.add("innerVif", array(fit.innerVif())); output.add("outerVif", array(fit.outerVif()));
        output.add("effectSizes", array(fit.effectSizes())); output.add("totalEffects", array(fit.totalEffects()));
        output.add("indirectEffects", array(fit.indirectEffects())); JsonArray warnings = new JsonArray();
        for (String warning : fit.warnings()) warnings.add(warning);
        output.add("warnings", warnings); return output;
    }

    private static JsonElement number(double value) {
        return Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    }
    private static JsonArray array(double[] data) {
        JsonArray output = new JsonArray();
        for (double value : data) output.add(number(value));
        return output;
    }
    private static JsonArray array(double[][] data) {
        JsonArray output = new JsonArray();
        for (double[] row : data) output.add(array(row));
        return output;
    }
    private static double[] vector(JsonArray input) {
        double[] output = new double[input.size()];
        for (int i = 0; i < output.length; i++) output[i] = input.get(i).isJsonNull()?Double.NaN:input.get(i).getAsDouble();
        return output;
    }
    private static double[][] matrix(JsonArray input) {
        double[][] output = new double[input.size()][];
        for (int i = 0; i < output.length; i++) output[i] = vector(input.get(i).getAsJsonArray());
        return output;
    }
}
