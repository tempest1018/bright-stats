package org.brightstats.stats;
import java.util.*;
import java.nio.file.*;
import com.google.gson.*;

public final class PlsScalesChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void close(double a,double b){require(Math.abs(a-b)<1e-7*Math.max(1,Math.abs(b)),"Scale identity differs: "+a+" / "+b);}
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]);Files.createDirectories(out);Random random=new Random(109);double[][] data=new double[96][6];
        for(int i=0;i<data.length;i++){double x=random.nextGaussian(),z=.3*x+random.nextGaussian(),y=.7*x-.4*z+.4*random.nextGaussian();data[i]=new double[]{5+2*x+.3*random.nextGaussian(),-2+3*x+.4*random.nextGaussian(),10+z+.2*random.nextGaussian(),3+4*z+.3*random.nextGaussian(),20+5*y+.2*random.nextGaussian(),-10+2*y+.3*random.nextGaussian()};}
        List<PlsSem.Block> blocks=List.of(new PlsSem.Block("X",new int[]{0,1},PlsSem.Mode.A),new PlsSem.Block("Z",new int[]{2,3},PlsSem.Mode.B),new PlsSem.Block("Y",new int[]{4,5},PlsSem.Mode.A));List<PlsSem.Edge> edges=List.of(new PlsSem.Edge(0,1),new PlsSem.Edge(0,2),new PlsSem.Edge(1,2));var options=PlsSem.Options.defaults();var bootstrap=new Bootstrap.Options(40,552,.95,BootstrapIntervals.Method.PERCENTILE,RegressionBootstrap.Convention.STANDARD,true);
        JsonArray cases=new JsonArray();var json=new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
        for(var metric:PlsScales.Metric.values()){
            var fit=PlsScales.fit(data,blocks,edges,options,metric);require(fit.standardized().converged(),"Core convergence");double[][] scores=fit.scores(),weights=fit.weights();
            for(int j=0;j<blocks.size();j++){double mean=0;for(var row:scores)mean+=row[j]/scores.length;if(metric!=PlsScales.Metric.UNSTANDARDIZED)close(mean,0);if(metric!=PlsScales.Metric.STANDARDIZED)close(Arrays.stream(weights[j]).sum(),1);}
            for(int target=1;target<3;target++){int t=target;var parents=edges.stream().filter(e->e.target()==t).map(PlsSem.Edge::source).toList();double[][] x=new double[data.length][parents.size()];double[] y=new double[data.length];for(int row=0;row<data.length;row++){y[row]=scores[row][target];for(int p=0;p<parents.size();p++)x[row][p]=scores[row][parents.get(p)];}var ols=OlsRegression.fit(x,y);close(fit.intercepts()[target],ols.coefficients()[0]);for(int p=0;p<parents.size();p++)close(fit.paths()[parents.get(p)][target],ols.coefficients()[p+1]);close(ols.rSquared(),fit.standardized().rSquared()[target]);}
            double saved=scores[0][0];scores[0][0]=999;close(fit.scores()[0][0],saved);
            var result=PlsScales.bootstrap(data,blocks,edges,options,metric,bootstrap,null);require(result.inference().successful()==40,"All bootstrap attempts succeed");require(result.inference().estimates().size()==PlsScales.parameters(fit).size(),"Stable inference vector");
            JsonObject entry=new JsonObject();entry.add("observations",json.toJsonTree(data));entry.add("blocks",json.toJsonTree(blocks));entry.add("edges",json.toJsonTree(edges));entry.add("options",json.toJsonTree(options));entry.add("fit",json.toJsonTree(fit));entry.add("bootstrap",json.toJsonTree(result.inference()));cases.add(entry);
        }
        var bca=PlsScales.bootstrap(data,blocks,edges,options,PlsScales.Metric.UNSTANDARDIZED,new Bootstrap.Options(40,777,.90,BootstrapIntervals.Method.BCA,RegressionBootstrap.Convention.STANDARD,true),null);require(bca.inference().successful()==40,"Complete BCa refits");JsonObject bcaCase=new JsonObject();bcaCase.add("observations",json.toJsonTree(data));bcaCase.add("blocks",json.toJsonTree(blocks));bcaCase.add("edges",json.toJsonTree(edges));bcaCase.add("options",json.toJsonTree(options));bcaCase.add("fit",json.toJsonTree(bca.point()));bcaCase.add("bootstrap",json.toJsonTree(bca.inference()));cases.add(bcaCase);
        var raw=PlsScales.fit(data,blocks,edges,options,PlsScales.Metric.UNSTANDARDIZED);double[][] units=Matrices.copy(data);for(var row:units)for(int c=0;c<row.length;c++)row[c]*=1e12;var changed=PlsScales.fit(units,blocks,edges,options,PlsScales.Metric.UNSTANDARDIZED);for(int i=0;i<data.length;i++)for(int j=0;j<blocks.size();j++)close(changed.scores()[i][j]/1e12,raw.scores()[i][j]);
        Thread.currentThread().interrupt();try{PlsScales.fit(data,blocks,edges,options,PlsScales.Metric.STANDARDIZED);throw new AssertionError("Cancellation ignored");}catch(java.util.concurrent.CancellationException expected){checks++;}finally{Thread.interrupted();}
        // A symmetric formative contrast has no raw signed-sum metric.
        double[][] contrast=new double[100][3];for(int i=0;i<100;i++){double x=(i-49.5)/10,y=Math.sin(i);contrast[i]=new double[]{x+y,-x+y,x};}
        try{PlsScales.fit(contrast,List.of(new PlsSem.Block("Contrast",new int[]{0,1},PlsSem.Mode.B),new PlsSem.Block("Outcome",new int[]{2},PlsSem.Mode.A)),List.of(new PlsSem.Edge(0,1)),options,PlsScales.Metric.UNSTANDARDIZED);throw new AssertionError("Undefined raw scale accepted");}catch(IllegalArgumentException expected){checks++;}
        Files.writeString(out.resolve("cases.json"),json.toJson(cases));System.out.println("PLS scale checks: "+checks);
    }
}
