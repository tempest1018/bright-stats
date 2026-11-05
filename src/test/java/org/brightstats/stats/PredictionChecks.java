package org.brightstats.stats;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.brightstats.data.*;

public final class PredictionChecks {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Prediction check "+checks);}
    private static void reject(Runnable work){try{work.run();throw new AssertionError("Invalid prediction accepted");}catch(IllegalArgumentException expected){checks++;}}
    private static double[][] copy(double[][] data){return Arrays.stream(data).map(double[]::clone).toArray(double[][]::new);}
    public static void main(String[] args)throws Exception {
        var gson=new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();JsonArray cases=new JsonArray();Random random=new Random(8611);
        for(int example=0;example<5;example++) {
            double[][] data=new double[60][6];for(int i=0;i<data.length;i++) {
                double a=random.nextGaussian(),b=.7*a+random.nextGaussian()*.6,c=-.2*a+.8*b+random.nextGaussian()*.7;
                data[i]=new double[]{a+random.nextGaussian()*.2,example==4?0:.8*a+random.nextGaussian()*.3,b+random.nextGaussian()*.25,.9*b+random.nextGaussian()*.2,c+random.nextGaussian()*.2,.8*c+random.nextGaussian()*.2};if(example==4)data[i][1]=data[i][0];
                if(example==1){if(i%11==0)data[i][0]=Double.NaN;if(i%13==0)data[i][4]=Double.NaN;}
            }
            var blocks=List.of(new PlsSem.Block("A",new int[]{0,1},example==1?PlsSem.Mode.B:PlsSem.Mode.A,example==2?new double[]{-1,-1}:null),new PlsSem.Block("B",new int[]{2,3},PlsSem.Mode.A),new PlsSem.Block("C",new int[]{4,5},PlsSem.Mode.A));
            var edges=example==2?List.of(new PlsSem.Edge(0,1),new PlsSem.Edge(1,2)):List.of(new PlsSem.Edge(0,1),new PlsSem.Edge(0,2),new PlsSem.Edge(1,2));
            var modelOptions=new PlsSem.Options(3000,1e-9,example==2?PlsSem.Weighting.FACTOR:PlsSem.Weighting.PATH);
            var options=new Prediction.Options(example==1?Prediction.Scheme.HOLDOUT:Prediction.Scheme.K_FOLD,example==3?5:3,example==3?3:2,.25,example==1?Long.MAX_VALUE:42);
            double[][] before=copy(data);var result=Prediction.evaluate(data,blocks,edges,modelOptions,options,null);check(Arrays.deepEquals(before,data));
            check(result.metrics().size()==4);if(example==1)check(result.warnings().stream().anyMatch(w->w.contains("Missing held-out")));if(example==4)check(result.warnings().stream().anyMatch(w->w.contains("minimum-norm")));
            JsonObject value=new JsonObject();value.add("observations",gson.toJsonTree(data));value.add("blocks",gson.toJsonTree(blocks));value.add("edges",gson.toJsonTree(edges));value.add("options",gson.toJsonTree(modelOptions));value.add("prediction",gson.toJsonTree(options));value.add("result",gson.toJsonTree(result));cases.add(value);
        }
        var single=List.of(new PlsSem.Block("X",new int[]{0},PlsSem.Mode.A),new PlsSem.Block("Y",new int[]{1},PlsSem.Mode.A));var edge=List.of(new PlsSem.Edge(0,1));var model=PlsSem.Options.defaults();
        double[][] line=new double[24][2];for(int i=0;i<line.length;i++)line[i]=new double[]{i,2+3*i};
        var settings=new Prediction.Options(Prediction.Scheme.K_FOLD,4,2,.25,42);var result=Prediction.evaluate(line,single,edge,model,settings,null);
        check(result.metrics().getFirst().plsRmse()<1e-12&&result.metrics().getFirst().linearRmse()<1e-12&&result.metrics().getFirst().qSquaredPredict()>.999999);
        check(result.predictions().size()==48);int[][] assignments=result.assignments();for(int[] repeat:assignments)for(int value:repeat)check(value>=1&&value<=4);
        check(Arrays.deepEquals(assignments,Prediction.assignments(24,settings)));check(!Arrays.deepEquals(assignments,Prediction.assignments(24,new Prediction.Options(Prediction.Scheme.K_FOLD,4,2,.25,43))));
        int old=assignments[0][0];assignments[0][0]=99;check(result.assignments()[0][0]==old);
        var trained=Prediction.train(line,single,edge,model);double[][] inputs={{30,Double.NaN},{31,1000000}};var forecasts=trained.predict(inputs);
        check(Math.abs(forecasts[0][0][0]-92)<1e-11&&Math.abs(forecasts[1][0][0]-95)<1e-11);
        check(trained.predict(new double[][]{{30,Double.POSITIVE_INFINITY}})[0][0][0]==forecasts[0][0][0]);
        var holdout=new Prediction.Options(Prediction.Scheme.HOLDOUT,5,1,.25,42);var baseline=Prediction.evaluate(line,single,edge,model,holdout,null);double[][] altered=copy(line);int[] split=baseline.assignments()[0];
        for(int i=0;i<altered.length;i++)if(split[i]==1)altered[i][1]+=1000000;
        var blinded=Prediction.evaluate(altered,single,edge,model,holdout,null);
        for(int i=0;i<baseline.predictions().size();i++){var a=baseline.predictions().get(i);var b=blinded.predictions().get(i);check(a.pls()==b.pls()&&a.mean()==b.mean()&&a.linear()==b.linear());}
        // Sparse labels remain missing in evaluation, rather than being imputed as synthetic truth.
        for(int i=0;i<altered.length;i++)if(split[i]==1)altered[i][1]=Double.NaN;
        var noLabels=Prediction.evaluate(altered,single,edge,model,holdout,null);check(noLabels.metrics().getFirst().observations()==0&&Double.isNaN(noLabels.metrics().getFirst().plsRmse()));
        var raw=DataPreparation.parse("x,y\n1,5\n,8\n3,11\n4,\n5,17\n6,20\n",',');var config=new DataPreparation.Settings(',',DataPreparation.Decimal.DOT,"",DataPreparation.Missing.MEAN_REPLACEMENT);var definitions=DataPreparation.infer(raw,config);var prepared=DataPreparation.prepare(raw,config,definitions);
        var history=ImportHistory.capture("original.csv",raw,config,definitions);var original=PredictionInput.original(history,prepared.columns(),prepared.values());check(Double.isNaN(original[1][0])&&Double.isNaN(original[3][1])&&Double.isFinite(prepared.values()[1][0]));
        reject(()->new Prediction.Options(Prediction.Scheme.K_FOLD,1,1,.2,1));reject(()->new Prediction.Options(Prediction.Scheme.K_FOLD,5,0,.2,1));reject(()->new Prediction.Options(Prediction.Scheme.HOLDOUT,5,1,1,1));
        reject(()->Prediction.evaluate(line,single,edge,model,new Prediction.Options(Prediction.Scheme.K_FOLD,25,1,.2,1),null));
        reject(()->Prediction.train(line,List.of(new PlsSem.Block("A",new int[]{0},PlsSem.Mode.A),new PlsSem.Block("B",new int[]{0},PlsSem.Mode.A)),edge,model));
        reject(()->Prediction.train(line,single,List.of(new PlsSem.Edge(0,1),new PlsSem.Edge(1,0)),model));
        reject(()->Prediction.train(new double[][]{{1,2},{1,3},{1,4}},single,edge,model));reject(()->Prediction.train(new double[][]{{1,Double.NaN},{2,Double.NaN},{3,Double.NaN}},single,edge,model));
        try{Prediction.evaluate(line,single,edge,model,settings,done->{Thread.currentThread().interrupt();});throw new AssertionError("Cancelled validation returned results");}catch(CancellationException expected){checks++;}finally{Thread.interrupted();}
        Path output=Path.of(args[0]);Files.createDirectories(output);Files.writeString(output.resolve("independent-cases.json"),gson.toJson(cases));
        System.out.println("{\"edge_checks\":"+checks+",\"independent_models\":5,\"heldout_outcome_blinding\":true,\"source_missingness_restored\":true,\"cancellation\":true}");
    }
}
