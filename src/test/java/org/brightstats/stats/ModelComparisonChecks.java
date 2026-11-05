package org.brightstats.stats;
import java.util.*;
import java.nio.file.*;
import com.google.gson.*;
public final class ModelComparisonChecks {
    static int checks;
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Model comparison check "+checks);}
    static void reject(Runnable action){try{action.run();throw new AssertionError("Invalid comparison accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        Random random=new Random(561);double[][] data=new double[90][6];for(var row:data){double x=random.nextGaussian(),z=random.nextGaussian(),y=.8*x+.6*z+.4*random.nextGaussian();row[0]=x+.15*random.nextGaussian();row[1]=x+.15*random.nextGaussian();row[2]=z+.15*random.nextGaussian();row[3]=z+.15*random.nextGaussian();row[4]=y+.15*random.nextGaussian();row[5]=y+.15*random.nextGaussian();}
        var blocks=List.of(new PlsSem.Block("X",new int[]{0,1},PlsSem.Mode.A),new PlsSem.Block("Z",new int[]{2,3},PlsSem.Mode.A),new PlsSem.Block("Y",new int[]{4,5},PlsSem.Mode.A));
        var full=new ModelComparison.Model("Full",blocks,List.of(new PlsSem.Edge(0,2),new PlsSem.Edge(1,2)),PlsSem.Options.defaults());
        var reduced=new ModelComparison.Model("Reduced",List.of(blocks.get(0),blocks.get(2)),List.of(new PlsSem.Edge(0,1)),PlsSem.Options.defaults());
        var options=new Prediction.Options(Prediction.Scheme.K_FOLD,5,3,.2,812);var fit=ModelComparison.compare(data,full,reduced,options,.95,null);
        check(fit.commonTargets().equals(List.of(4,5)));check(fit.losses().size()==4);check(fit.losses().stream().allMatch(loss->loss.test().observations()==90));check(fit.losses().getFirst().test().difference()<0);check(fit.criteria().size()==1);var criterion=fit.criteria().getFirst();check(criterion.firstParameters()==4&&criterion.secondParameters()==3);check(criterion.firstAkaikeWeight()+criterion.secondAkaikeWeight()==1);check(criterion.firstBicWeight()>criterion.secondBicWeight());
        var identical=ModelComparison.compare(data,full,new ModelComparison.Model("Same",blocks,full.edges(),full.options()),options,.95,null);check(identical.losses().stream().allMatch(loss->loss.test().status().equals("ZERO_EMPIRICAL_LOSS_VARIANCE")));check(identical.criteria().getFirst().firstAkaikeWeight()==.5);
        var holdout=ModelComparison.compare(data,full,reduced,new Prediction.Options(Prediction.Scheme.HOLDOUT,5,3,.2,812),.95,null);check(holdout.losses().stream().allMatch(loss->loss.test().status().equals("HOLDOUT_DESCRIPTIVE")&&Double.isNaN(loss.test().pTwoSided())));
        reject(()->ModelComparison.compare(data,full,full,options,.95,null));reject(()->ModelComparison.compare(data,full,reduced,options,1,null));
        Thread.currentThread().interrupt();try{ModelComparison.compare(data,full,reduced,options,.95,null);throw new AssertionError("Cancellation ignored");}catch(java.util.concurrent.CancellationException expected){checks++;}finally{Thread.interrupted();}
        check(holdout.losses().stream().allMatch(loss->loss.test().observations()<90));
        var noCommon=new ModelComparison.Model("Different outcome",List.of(blocks.get(0),blocks.get(1)),List.of(new PlsSem.Edge(0,1)),PlsSem.Options.defaults());reject(()->ModelComparison.compare(data,full,noCommon,options,.95,null));
        for(var result:List.of(fit,identical,holdout)){var numeric=new JsonObject();numeric.add("modelComparison",new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(result));var archive=org.brightstats.report.ReportArchive.create("COMPARISON_TEST",new JsonObject(),numeric,new JsonArray(),List.of());var restored=org.brightstats.report.ModelComparisonReports.point(archive);check(Double.isNaN(restored.first().fit().rSquared()[0]));check(restored.losses().size()==result.losses().size());check(restored.losses().getFirst().test().difference()==result.losses().getFirst().test().difference());if(result==identical||result==holdout)check(Double.isNaN(restored.losses().getFirst().test().pTwoSided()));}
        Path output=Path.of(args[0]);Files.createDirectories(output);var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();var cases=new JsonObject();cases.add("data",gson.toJsonTree(data));cases.add("kfold",gson.toJsonTree(fit));cases.add("identical",gson.toJsonTree(identical));cases.add("holdout",gson.toJsonTree(holdout));Files.writeString(output.resolve("cases.json"),gson.toJson(cases));System.out.println("{\"checks\":"+checks+"}");
    }
}
