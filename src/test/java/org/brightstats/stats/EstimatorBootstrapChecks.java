package org.brightstats.stats;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

public final class EstimatorBootstrapChecks {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Estimator bootstrap check "+checks);}
    private static void reject(Runnable work){try{work.run();throw new AssertionError("Invalid bootstrap accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        Gson gson=new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();JsonArray cases=new JsonArray();Random random=new Random(4285);double[][] data=new double[120][3];for(int i=0;i<data.length;i++){double factor=random.nextGaussian()*1.5;for(int j=0;j<3;j++)data[i][j]=j+new double[]{1,.8,1.2}[j]*factor+.6*random.nextGaussian();}
        var prepared=SemModel.reflective(List.of("X1","X2","X3"),data,List.of(new PlsSem.Block("A",new int[]{0,1,2},PlsSem.Mode.A)),List.of(),false,false);var semOptions=new CovarianceSem.Options(1500,1e-10,1e-14,CovarianceSem.Likelihood.NORMAL,false,.95,CovarianceSem.Information.EXPECTED);
        var component=new Gsca.Model(List.of("X1","X2","X3"),List.of("A"),new boolean[][]{{true},{true},{true}},new boolean[][]{{true},{true},{true}},new boolean[][]{{false}},new double[][]{{-1},{-.8},{-1.2}});var gscaOptions=new Gsca.Options(3000,1e-12);
        for(boolean sem:new boolean[]{true,false})for(int variant=0;variant<5;variant++){
            boolean reference=variant>=2;var method=variant==1||variant==4?BootstrapIntervals.Method.BCA:variant==3?BootstrapIntervals.Method.STUDENTIZED:BootstrapIntervals.Method.PERCENTILE;var bootstrap=new Bootstrap.Options(40,reference?295948:42,.95,method,reference?RegressionBootstrap.Convention.REFERENCE_4_1_2_1:RegressionBootstrap.Convention.STANDARD,variant!=3);
            Object result;Bootstrap.Result inference;if(sem){var fitted=SemBootstrap.fit(data,prepared.model(),semOptions,bootstrap,null);result=fitted;inference=fitted.inference();check(fitted.improperReplicates()==0);}else{var fitted=GscaBootstrap.fit(data,component,gscaOptions,bootstrap,null);result=fitted;inference=fitted.inference();}
            check(inference.successful()==40);check(inference.failed()==0);check(!inference.estimates().isEmpty());
            if(!sem)for(var term:inference.estimates())if(term.parameter().family().equals("Fit"))check(Double.isNaN(term.pValue()));
            JsonObject value=new JsonObject();value.addProperty("kind",sem?"SEM":"GSCA");value.add("observations",gson.toJsonTree(data));value.add("model",gson.toJsonTree(sem?prepared.model():component));value.add("options",gson.toJsonTree(sem?semOptions:gscaOptions));value.add("bootstrap",gson.toJsonTree(bootstrap));value.add("result",gson.toJsonTree(result));cases.add(value);
        }
        double[][] simple={{1},{2},{3},{4}};var parameters=List.of(new Bootstrap.Parameter("Mean",-1,-1,-1,"x"));int[] attempts={0},progress={0};var settings=new Bootstrap.Options(40,42,.95);
        var partial=Resampling.run(simple,parameters,new double[]{2.5},settings,(sample,rows)->{attempts[0]++;if(attempts[0]%5==0)throw new IllegalArgumentException("Deliberate rejected fit");double mean=0;for(int i=0;i<sample.length;i++){check(sample[i][0]==simple[rows[i]][0]);mean+=sample[i][0]/sample.length;}sample[0][0]=999;return new double[]{mean};},step->progress[0]=step,"Test mean","Not applicable");check(partial.successful()==32&&partial.failed()==8&&progress[0]==40);check(simple[0][0]==1&&simple[1][0]==2);
        int[] failed={0};reject(()->Resampling.run(simple,parameters,new double[]{2.5},settings,(sample,rows)->{if(++failed[0]%2==0)throw new IllegalArgumentException("Failed");return new double[]{2.5};},null,"Test","None"));
        Thread.currentThread().interrupt();try{GscaBootstrap.fit(data,component,gscaOptions,settings,null);throw new AssertionError("Cancellation ignored");}catch(java.util.concurrent.CancellationException expected){checks++;}finally{Thread.interrupted();}
        Path output=Path.of(args[0]);Files.createDirectories(output);Files.writeString(output.resolve("cases.json"),gson.toJson(cases));System.out.println("{\"estimator_bootstrap_cases\":"+cases.size()+",\"checks\":"+checks+"}");
    }
}
