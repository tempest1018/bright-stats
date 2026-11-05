package org.brightstats.stats;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

public final class AdvancedChecks {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Advanced check "+checks);}
    private static void reject(Runnable work){try{work.run();throw new AssertionError("Invalid analysis accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception {
        var gson=new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();JsonArray cases=new JsonArray();Random random=new Random(20261103);
        for(boolean scaled:new boolean[]{true,false})for(int example=0;example<5;example++) {
            double[][] input=new double[30+example*7][4];for(int i=0;i<input.length;i++){double latent=random.nextGaussian();for(int j=0;j<4;j++)input[i][j]=3*j+(j+1)*(latent+random.nextGaussian()*.6);}
            var fit=PrincipalComponents.fit(input,scaled,3);var value=new JsonObject();value.addProperty("kind","PCA");value.add("observations",gson.toJsonTree(input));value.addProperty("correlation",scaled);value.addProperty("components",3);value.add("result",gson.toJsonTree(fit));cases.add(value);
            check(Arrays.deepEquals(fit.scores(),fit.transform(input)));double eigen=fit.eigenvalues()[0];double[] altered=fit.eigenvalues();altered[0]=999;check(fit.eigenvalues()[0]==eigen);
        }
        reject(()->PrincipalComponents.fit(new double[][]{{1,2},{1,3},{1,4}},true,2));reject(()->PrincipalComponents.fit(new double[][]{{1,2},{1,2}},false,1));
        var rank=PrincipalComponents.fit(new double[][]{{1,1},{2,2},{3,3}},true,2);check(rank.eigenvalues()[1]<1e-12&&!rank.warnings().isEmpty());
        for(int example=0;example<3;example++) {
            double[][] data=new double[1500][9];for(int i=0;i<data.length;i++) {
                double a=random.nextGaussian(),b=.55*a+Math.sqrt(1-.55*.55)*random.nextGaussian(),c=.2*a+.5*b+Math.sqrt(.6)*random.nextGaussian();double[] latent={a,b,c};
                for(int j=0;j<3;j++)for(int k=0;k<3;k++){double loading=new double[]{.9,.8,.7}[k];data[i][j*3+k]=loading*latent[j]+Math.sqrt(1-loading*loading)*random.nextGaussian();}
            }
            var blocks=List.of(new PlsSem.Block("A",new int[]{0,1,2},PlsSem.Mode.A,example==2?new double[]{-1,-1,-1}:null),new PlsSem.Block("B",new int[]{3,4,5},PlsSem.Mode.A),new PlsSem.Block("C",new int[]{6,7,8},PlsSem.Mode.A));var edges=List.of(new PlsSem.Edge(0,1),new PlsSem.Edge(0,2),new PlsSem.Edge(1,2));var options=new PlsSem.Options(3000,1e-9,example==1?PlsSem.Weighting.FACTOR:PlsSem.Weighting.PATH);
            var fit=ConsistentPls.fit(data,blocks,edges,options);check(fit.minimumEigenvalue()>0);check(Math.abs(Math.abs(fit.paths()[0][1])-.55)<.08);
            var value=new JsonObject();value.addProperty("kind","PLSC");value.add("observations",gson.toJsonTree(data));value.add("blocks",gson.toJsonTree(blocks));value.add("edges",gson.toJsonTree(edges));value.add("options",gson.toJsonTree(options));value.add("result",gson.toJsonTree(fit));cases.add(value);
            if(example==0){var bootOptions=new Bootstrap.Options(60,42,.95);var boot=Bootstrap.fit(data,blocks,edges,options,bootOptions,null,true);check(boot.successful()==60&&boot.estimator().equals("PLSc"));var bootstrap=value.deepCopy();bootstrap.addProperty("kind","PLSC_BOOTSTRAP");bootstrap.add("bootstrap",gson.toJsonTree(bootOptions));bootstrap.add("result",gson.toJsonTree(boot));cases.add(bootstrap);}
        }
        for(int example=0;example<10;example++) {
            double[][] x=new double[40+example][3];double[] y=new double[x.length];for(int i=0;i<x.length;i++){for(int j=0;j<3;j++)x[i][j]=random.nextGaussian();y[i]=.5+.7*x[i][0]-.4*x[i][1]+.2*x[i][2]+random.nextGaussian()*(.2+Math.abs(x[i][0]));}
            for(var covariance:RobustRegression.Covariance.values()) {
                var fit=RobustRegression.fit(x,y,covariance,.95);check(fit.terms().size()==4);
                var value=new JsonObject();value.addProperty("kind","ROBUST_OLS");value.add("predictors",gson.toJsonTree(x));value.add("response",gson.toJsonTree(y));value.addProperty("covariance",covariance.name());value.addProperty("confidence",.95);value.add("result",gson.toJsonTree(fit));cases.add(value);
            }
        }
        reject(()->RobustRegression.fit(new double[][]{{0},{0},{0},{1}},new double[]{1,2,3,5},RobustRegression.Covariance.HC3,.95));
        for(int example=0;example<12;example++) {
            int n=140+example*5;double[][] x=new double[n][3];double[] y=new double[n];for(int i=0;i<n;i++){double eta=-.3;for(int j=0;j<3;j++){x[i][j]=random.nextGaussian()+j*.4;eta+=x[i][j]*new double[]{.7,-.5,.3}[j];}y[i]=random.nextDouble()<1/(1+Math.exp(-eta))?2:1;}
            var options=new LogisticRegression.Options(100,1e-9,.95,.5,example%2==0?null:1.0);var fit=LogisticRegression.fit(x,y,options);check(fit.converged());
            var value=new JsonObject();value.addProperty("kind","LOGISTIC");value.add("predictors",gson.toJsonTree(x));value.add("response",gson.toJsonTree(y));value.add("options",gson.toJsonTree(options));value.add("result",gson.toJsonTree(fit));cases.add(value);
        }
        reject(()->LogisticRegression.fit(new double[][]{{-2},{-1},{1},{2}},new double[]{0,0,1,1},LogisticRegression.Options.defaults()));
        reject(()->LogisticRegression.fit(new double[][]{{1},{2},{3}},new double[]{0,0,0},LogisticRegression.Options.defaults()));
        Path output=Path.of(args[0]);Files.createDirectories(output);Files.writeString(output.resolve("cases.json"),gson.toJson(cases));System.out.println("{\"edge_checks\":"+checks+",\"pca_cases\":10,\"plsc_cases\":3,\"plsc_bootstrap_cases\":1,\"regression_covariance_cases\":60,\"logistic_cases\":12}");
    }
}
