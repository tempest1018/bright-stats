package org.brightstats.stats;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;

public final class InferenceChecks {
    private static int checks;
    private static void check(boolean condition) { checks++; if(!condition)throw new AssertionError("Inference check "+checks); }
    private static void reject(Runnable work) { try { work.run(); throw new AssertionError("Invalid inference accepted"); } catch(IllegalArgumentException expected){ checks++; } }
    public static void main(String[] args) throws Exception {
        Gson gson=new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create(); JsonArray cases=new JsonArray();
        Random random=new Random(7711);
        for(int example=0;example<30;example++) {
            int n=12+example, p=1+example%4; double[][] x=new double[n][p]; double[] y=new double[n];
            for(int i=0;i<n;i++) { y[i]=-.7+random.nextGaussian()*.9; for(int j=0;j<p;j++) { x[i][j]=random.nextGaussian(); y[i]+=x[i][j]*(j%2==0?.6:-.35); } }
            for(double confidence:new double[]{.9,.95,.99}) {
                JsonObject value=new JsonObject(); value.addProperty("kind","OLS"); value.add("predictors",gson.toJsonTree(x)); value.add("response",gson.toJsonTree(y)); value.addProperty("confidence",confidence);
                value.add("result",gson.toJsonTree(Inference.regression(OlsRegression.fit(x,y),confidence))); cases.add(value);
            }
        }
        double[][] data=new double[90][6];
        for(int i=0;i<data.length;i++) {
            double a=random.nextGaussian(),b=.6*a+random.nextGaussian()*.8,c=-.3*a+.7*b+random.nextGaussian()*.7;
            data[i]=new double[]{a+random.nextGaussian()*.3,.8*a+random.nextGaussian()*.4,b+random.nextGaussian()*.25,.9*b+random.nextGaussian()*.3,c+random.nextGaussian()*.3,.85*c+random.nextGaussian()*.3};
        }
        List<PlsSem.Edge> edges=List.of(new PlsSem.Edge(0,1),new PlsSem.Edge(1,2),new PlsSem.Edge(0,2));
        Bootstrap.Result first=null; List<PlsSem.Block> standard=null;
        for(int example=0;example<3;example++) {
            List<PlsSem.Block> blocks=List.of(new PlsSem.Block("A",new int[]{0,1},example==1?PlsSem.Mode.B:PlsSem.Mode.A,example==2?new double[]{-1,-1}:null),new PlsSem.Block("B",new int[]{2,3},PlsSem.Mode.A),new PlsSem.Block("C",new int[]{4,5},PlsSem.Mode.A));
            var settings=new Bootstrap.Options(100,example==2?Long.MAX_VALUE:42,.95);
            var modelOptions=new PlsSem.Options(3000,1e-9,example==2?PlsSem.Weighting.FACTOR:PlsSem.Weighting.PATH);
            var result=Bootstrap.fit(data,blocks,edges,modelOptions,settings,null);
            if(example==0){first=result; standard=blocks;}
            check(result.successful()==100 && result.failed()==0);
            JsonObject value=new JsonObject(); value.addProperty("kind","PLS_BOOTSTRAP"); value.add("observations",gson.toJsonTree(data));value.add("blocks",gson.toJsonTree(blocks));value.add("edges",gson.toJsonTree(edges));value.add("options",gson.toJsonTree(modelOptions));value.add("bootstrap",gson.toJsonTree(settings));value.add("result",gson.toJsonTree(result));cases.add(value);
            check(value.getAsJsonObject("bootstrap").get("seed").getAsLong()==settings.seed());
        }
        var standardBlocks=standard; var settings=new Bootstrap.Options(100,42,.95); var modelOptions=new PlsSem.Options(3000,1e-9,PlsSem.Weighting.PATH);
        check(first.equals(Bootstrap.fit(data,standardBlocks,edges,modelOptions,settings,null)));
        check(!first.equals(Bootstrap.fit(data,standardBlocks,edges,modelOptions,new Bootstrap.Options(100,43,.95),null)));
        check(Inference.quantile(new double[]{0,2,4,10},.25)==1.5);
        check(Double.isNaN(Inference.ratio(0,0)) && Double.isInfinite(Inference.ratio(1,0)));
        check(Double.isNaN(Inference.normalTwoSided(Double.NaN)) && Inference.normalTwoSided(Double.POSITIVE_INFINITY)==0);
        var zero=Inference.regression(OlsRegression.fit(new double[][]{{1},{2},{3},{4}},new double[4]),.95);
        check(Double.isNaN(zero.terms().get(0).pValue()) && zero.terms().get(0).lower()==0);
        check(Bootstrap.alignment(new double[][]{{-1},{1}},new double[][]{{1},{-1}},new int[]{0,1})[0]==-1);
        reject(()->new Bootstrap.Options(29,1,.95)); reject(()->new Bootstrap.Options(100001,1,.95)); reject(()->Inference.confidence(Double.NaN));reject(()->Inference.confidence(1));
        reject(()->Bootstrap.fit(data,standardBlocks,edges,new PlsSem.Options(1,1e-30,PlsSem.Weighting.PATH),settings,null));
        try { Bootstrap.fit(data,standardBlocks,edges,modelOptions,settings,done->{if(done==5)Thread.currentThread().interrupt();}); throw new AssertionError("Cancelled run returned inference"); }
        catch(CancellationException expected){checks++;} finally{Thread.interrupted();}
        var single=List.of(new PlsSem.Block("X",new int[]{0},PlsSem.Mode.A),new PlsSem.Block("Y",new int[]{1},PlsSem.Mode.A));
        var simpleEdges=List.of(new PlsSem.Edge(0,1));
        var imperfect=Bootstrap.fit(new double[][]{{-1,1},{0,0},{1,-1}},single,simpleEdges,PlsSem.Options.defaults(),new Bootstrap.Options(100,42,.95),null);
        check(imperfect.failed()>0 && imperfect.successful()+imperfect.failed()==100 && !imperfect.failures().isEmpty());
        reject(()->Bootstrap.fit(new double[][]{{0,0},{0,0},{0,0},{1,1}},single,simpleEdges,PlsSem.Options.defaults(),new Bootstrap.Options(100,42,.95),null));
        Path output=Path.of(args[0]); Files.createDirectories(output); Files.writeString(output.resolve("independent-cases.json"),gson.toJson(cases));
        System.out.println("{\"edge_and_reproducibility_checks\":"+checks+",\"independent_ols_cases\":90,\"independent_bootstrap_models\":3,\"bootstrap_attempts_per_model\":100,\"failed_sample_reporting\":true,\"cancellation\":true}");
    }
}
