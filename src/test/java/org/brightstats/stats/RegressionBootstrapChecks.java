package org.brightstats.stats;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;

public final class RegressionBootstrapChecks {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Regression bootstrap check "+checks);}
    private static void reject(Runnable work){try{work.run();throw new AssertionError("Invalid bootstrap accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        Gson gson=new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();JsonArray cases=new JsonArray();Random random=new Random(7331);
        for(int example=0;example<3;example++){
            int n=50+example*10;double[][] x=new double[n][2];double[] y=new double[n];for(int i=0;i<n;i++){x[i][0]=random.nextGaussian();x[i][1]=random.nextGaussian()+.2*x[i][0];y[i]=.4+.6*x[i][0]-.3*x[i][1]+random.nextGaussian()*(.4+.3*Math.abs(x[i][0]));}
            String before=gson.toJson(x);long seed=new long[]{42,-777,Long.MAX_VALUE}[example];
            for(var covariance:List.of(RobustRegression.Covariance.CLASSICAL,RobustRegression.Covariance.HC3))for(var method:BootstrapIntervals.Method.values()){
                var options=new RegressionBootstrap.Options(120,seed,.95,method,covariance,true);var fit=RegressionBootstrap.fit(x,y,options,null);check(fit.successful()==120&&fit.failed()==0&&fit.estimates().size()==10);check(fit.estimates().stream().allMatch(e->Arrays.stream(e.histogram().counts()).sum()==120&&e.distribution().length==120));
                var value=new JsonObject();value.add("predictors",gson.toJsonTree(x));value.add("response",gson.toJsonTree(y));value.add("options",gson.toJsonTree(options));value.add("result",gson.toJsonTree(fit));cases.add(value);
                if(example==0&&covariance==RobustRegression.Covariance.CLASSICAL&&method==BootstrapIntervals.Method.PERCENTILE)check(gson.toJson(fit).equals(gson.toJson(RegressionBootstrap.fit(x,y,options,null))));
            }
            check(gson.toJson(x).equals(before));
        }
        double[][] nativeX=new double[20][2];double[] nativeY=new double[20];for(int i=0;i<20;i++){nativeY[i]=i+1;nativeX[i][0]=i%3-1;nativeX[i][1]=2+3*(i+1)-1.5*(i%3-1)+.1*(i%5-2);}
        for(var method:BootstrapIntervals.Method.values())for(boolean twoSided:new boolean[]{true,false}){
            int samples=method==BootstrapIntervals.Method.PERCENTILE&&twoSided?5000:80;
            var options=new RegressionBootstrap.Options(samples,ReferenceResampling.DEFAULT_SEED,.95,method,RobustRegression.Covariance.CLASSICAL,true,RegressionBootstrap.Convention.REFERENCE_4_1_2_1,twoSided);
            var fit=RegressionBootstrap.fit(nativeX,nativeY,options,null);check(fit.successful()==samples);
            if(samples==5000){
                double[][] observed={{-.644,-.646,.023,27.439,-.694,-.602},{.497,.497,.013,37.272,.471,.524},{.333,.333,.001,541.076,.332,.334}};
                for(int j=0;j<3;j++){var estimate=fit.estimates().get(j);double[] actual={estimate.original(),estimate.mean(),estimate.standardError(),estimate.statistic(),estimate.interval().lower(),estimate.interval().upper()};for(int k=0;k<actual.length;k++)check(Math.abs(actual[k]-observed[j][k])<=.0005000001);}
            }
            var value=new JsonObject();value.add("predictors",gson.toJsonTree(nativeX));value.add("response",gson.toJsonTree(nativeY));value.add("options",gson.toJsonTree(options));value.add("result",gson.toJsonTree(fit));cases.add(value);
        }
        var percentile=BootstrapIntervals.calculate(2,new double[]{4,1,3,2},.95,BootstrapIntervals.Method.PERCENTILE,Double.NaN,null,null);check(Math.abs(percentile.lower()-1.075)<1e-12&&Math.abs(percentile.upper()-3.925)<1e-12);
        var degenerate=BootstrapIntervals.calculate(2,new double[]{2,2,2},.95,BootstrapIntervals.Method.BCA,0,null,new double[]{2,2,2});check(Double.isNaN(degenerate.lower())&&!degenerate.warnings().isEmpty());
        var invalidStudent=BootstrapIntervals.calculate(2,new double[40],.95,BootstrapIntervals.Method.STUDENTIZED,0,new double[40],null);check(Double.isNaN(invalidStudent.lower())&&!invalidStudent.warnings().isEmpty());
        reject(()->new RegressionBootstrap.Options(29,42,.95,BootstrapIntervals.Method.PERCENTILE,RobustRegression.Covariance.CLASSICAL));
        Thread.currentThread().interrupt();try{RegressionBootstrap.fit(new double[][]{{1},{2},{3},{4}},new double[]{1,3,2,4},RegressionBootstrap.Options.defaults(),null);throw new AssertionError("Cancellation ignored");}catch(CancellationException expected){checks++;}finally{Thread.interrupted();}
        Path output=Path.of(args[0]);Files.createDirectories(output);Files.writeString(output.resolve("cases.json"),gson.toJson(cases));System.out.println("{\"edge_checks\":"+checks+",\"independent_cases\":"+cases.size()+"}");
    }
}
