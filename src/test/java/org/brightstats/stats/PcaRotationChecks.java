package org.brightstats.stats;
import java.util.*;
import java.nio.file.*;
import com.google.gson.*;
public final class PcaRotationChecks {
    static int checks;static void check(boolean condition){checks++;if(!condition)throw new AssertionError("PCA rotation check "+checks);}
    static void reject(Runnable action){try{action.run();throw new AssertionError("Invalid rotation accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        Random random=new Random(783);double[][] data=new double[160][9];for(var row:data){double a=random.nextGaussian(),b=.4*a+random.nextGaussian(),c=.25*a+.3*b+random.nextGaussian();double[] factors={a,b,c};for(int j=0;j<9;j++)row[j]=(1+j*.1)*(factors[j/3]+.12*random.nextGaussian());}
        var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();JsonArray cases=new JsonArray();
        for(boolean correlation:new boolean[]{true,false})for(int retained:new int[]{1,2,3})for(boolean normalize:new boolean[]{true,false}){
            var pca=PrincipalComponents.fit(data,correlation,retained);var item=new JsonObject();item.add("pca",gson.toJsonTree(pca));item.add("data",gson.toJsonTree(data));
            for(var method:PcaRotation.Method.values()){
                var result=PcaRotation.rotate(pca,new PcaRotation.Options(method,normalize,1000,1e-10,4));check(result.converged());double[][] scores=result.scores(),phi=result.correlations(),pattern=result.pattern(),original=pca.loadings();
                for(int j=0;j<retained;j++){check(Math.abs(phi[j][j]-1)<1e-9);double variance=0;for(var row:scores)variance+=row[j]*row[j]/(scores.length-1);check(Math.abs(variance-1)<1e-9);}
                for(int i=0;i<9;i++){double old=0;for(double value:original[i])old+=value*value;check(Math.abs(result.communalities()[i]-old)<1e-8);for(int j=0;j<9;j++){double expected=0,actual=0;for(int a=0;a<retained;a++){expected+=original[i][a]*original[j][a];for(int b=0;b<retained;b++)actual+=pattern[i][a]*phi[a][b]*pattern[j][b];}check(Math.abs(actual-expected)<1e-8);}}
                if(method!=PcaRotation.Method.PROMAX)for(int i=0;i<retained;i++)for(int j=0;j<retained;j++)check(Math.abs(phi[i][j]-(i==j?1:0))<1e-9);
                var numeric=new JsonObject();numeric.add("pcaRotation",gson.toJsonTree(result));var archive=org.brightstats.report.ReportArchive.create("PCA_ROTATION_TEST",new JsonObject(),numeric,new JsonArray(),List.of());var restored=org.brightstats.report.PcaReports.rotation(archive);check(restored.correlations()[0][0]==result.correlations()[0][0]);if(method==PcaRotation.Method.PROMAX)check(Double.isNaN(restored.objective()));
                item.add(method.name(),gson.toJsonTree(result));
            }cases.add(item);
        }
        var pca=PrincipalComponents.fit(data,true,3);reject(()->new PcaRotation.Options(PcaRotation.Method.VARIMAX,true,0,1e-8,4));reject(()->new PcaRotation.Options(PcaRotation.Method.PROMAX,true,1000,1,4));reject(()->new PcaRotation.Options(PcaRotation.Method.PROMAX,true,1000,1e-8,1));double[][] duplicate={{1,1},{2,2},{3,3},{4,4}};reject(()->PcaRotation.rotate(PrincipalComponents.fit(duplicate,true,2),PcaRotation.Options.defaults()));
        var limited=PcaRotation.rotate(pca,new PcaRotation.Options(PcaRotation.Method.VARIMAX,true,1,1e-12,4));check(!limited.converged()&&limited.warnings().stream().anyMatch(w->w.contains("iteration limit")));
        Thread.currentThread().interrupt();try{PcaRotation.rotate(pca,new PcaRotation.Options(PcaRotation.Method.VARIMAX,true,1000,1e-8,4));throw new AssertionError("Cancellation ignored");}catch(java.util.concurrent.CancellationException expected){checks++;}finally{Thread.interrupted();}
        Path output=Path.of(args[0]);Files.createDirectories(output);Files.writeString(output.resolve("cases.json"),gson.toJson(cases));System.out.println("{\"cases\":"+cases.size()+",\"checks\":"+checks+"}");
    }
}
