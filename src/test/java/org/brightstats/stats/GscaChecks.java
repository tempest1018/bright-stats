package org.brightstats.stats;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

public final class GscaChecks {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("GSCA check "+checks);}
    private static void reject(Runnable work){try{work.run();throw new AssertionError("Invalid GSCA accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        Gson gson=new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();JsonArray cases=new JsonArray();Random random=new Random(20261104);
        for(int variant=0;variant<5;variant++)for(int example=0;example<3;example++){
            int n=80+example*90,p=variant==4?3:9,k=3;double[][] data=new double[n][p];
            for(int i=0;i<n;i++){double a=random.nextGaussian(),b=.55*a+.8*random.nextGaussian(),c=.2*a+.5*b+.7*random.nextGaussian();double[] factors={a,b,c};for(int j=0;j<p;j++){double value=variant==4?factors[j]:new double[]{.9,.75,.6}[j%3]*factors[j/3]+.55*random.nextGaussian();if(variant==3&&j==1)value+=.25*b;data[i][j]=3*j+(j+1)*value;}}
            boolean[][] weights=new boolean[p][k],loadings=new boolean[p][k],paths=new boolean[k][k];double[][] initial=new double[p][k];
            for(int j=0;j<p;j++){int component=variant==4?j:j/3;weights[j][component]=true;loadings[j][component]=variant!=2||component!=0;initial[j][component]=variant==2&&component==0?-1:1;}
            if(variant>0){paths[0][1]=paths[0][2]=paths[1][2]=true;}if(variant==3){weights[1][1]=loadings[1][1]=true;initial[1][1]=.3;}
            var model=new Gsca.Model(java.util.stream.IntStream.range(0,p).mapToObj(j->"X"+(j+1)).toList(),List.of("A","B","C"),weights,loadings,paths,initial);var options=new Gsca.Options(5000,1e-9);var fit=Gsca.fit(data,model,options);
            check(fit.converged());check(fit.fit()>=0&&fit.fit()<=1);for(int j=0;j<k;j++)check(Math.abs(fit.correlations()[j][j]-1)<1e-10);
            double[][] scored=fit.transform(data),scores=fit.scores();for(int i=0;i<n;i++)for(int j=0;j<k;j++)check(Math.abs(scored[i][j]-scores[i][j])<1e-12);
            check(Math.abs(fit.fit()-(p*fit.measurementFit()+k*fit.structuralFit())/(p+k))<1e-12);
            if(variant==0){for(int j=0;j<k;j++){double[][] block=new double[n][3];for(int i=0;i<n;i++)for(int l=0;l<3;l++)block[i][l]=data[i][j*3+l];var pca=PrincipalComponents.fit(block,true,1);double[] score=pca.scores()[0];double norm=0,dot=0;for(int l=0;l<3;l++){double w=fit.weights()[j*3+l][j];norm+=w*w;dot+=w*pca.vectors()[l][0];}check(Math.abs(Math.abs(dot)/Math.sqrt(norm)-1)<1e-5);}}
            if(variant==4)for(int j=0;j<k;j++)check(fit.weights()[j][j]==1);
            if(variant==1&&example==0)check(!Gsca.fit(data,model,new Gsca.Options(1,1e-12)).converged());
            double saved=fit.weights()[0][0];double[][] changed=fit.weights();changed[0][0]=999;check(fit.weights()[0][0]==saved);
            JsonObject value=new JsonObject();value.addProperty("variant",variant);value.add("observations",gson.toJsonTree(data));value.add("model",gson.toJsonTree(model));value.add("options",gson.toJsonTree(options));value.add("result",gson.toJsonTree(fit));cases.add(value);
        }
        var one=new Gsca.Model(List.of("x"),List.of("A"),new boolean[][]{{true}},new boolean[][]{{true}},new boolean[][]{{false}},new double[][]{{1}});
        reject(()->Gsca.fit(new double[][]{{1},{1},{1}},one,Gsca.Options.defaults()));
        reject(()->Gsca.fit(new double[][]{{1,2},{2,3},{3,4}},one,Gsca.Options.defaults()));
        reject(()->new Gsca.Model(List.of("x"),List.of("A"),new boolean[][]{{true}},new boolean[][]{{true}},new boolean[][]{{true}},new double[][]{{1}}));
        var unsupported=new Gsca.Model(List.of("x","y"),List.of("A"),new boolean[][]{{true},{true}},new boolean[][]{{false},{false}},new boolean[][]{{false}},new double[][]{{1},{1}});
        reject(()->Gsca.fit(new double[][]{{1,3},{2,1},{3,2},{4,5}},unsupported,Gsca.Options.defaults()));
        Thread.currentThread().interrupt();try{Gsca.fit(new double[][]{{1},{2},{3}},one,Gsca.Options.defaults());throw new AssertionError("Cancellation ignored");}catch(java.util.concurrent.CancellationException expected){checks++;}finally{Thread.interrupted();}
        Path output=Path.of(args[0]);Files.createDirectories(output);Files.writeString(output.resolve("cases.json"),gson.toJson(cases));System.out.println("{\"gsca_cases\":"+cases.size()+",\"checks\":"+checks+"}");
    }
}
