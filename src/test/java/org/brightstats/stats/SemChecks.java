package org.brightstats.stats;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

public final class SemChecks {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("SEM check "+checks);}
    private static void reject(Runnable work){try{work.run();throw new AssertionError("Invalid SEM accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        Gson gson=new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();JsonArray cases=new JsonArray();Random random=new Random(92736);
        List<String> names=List.of("a1","a2","a3","b1","b2","b3");var blocks=List.of(new PlsSem.Block("A",new int[]{0,1,2},PlsSem.Mode.A),new PlsSem.Block("B",new int[]{3,4,5},PlsSem.Mode.A));
        for(boolean mean:new boolean[]{false,true})for(var likelihood:CovarianceSem.Likelihood.values())for(int variant=0;variant<3;variant++){
            double[][] data=new double[450][6];for(int i=0;i<data.length;i++){double a=random.nextGaussian(),b=.4*a+Math.sqrt(.84)*random.nextGaussian();for(int j=0;j<6;j++){double scale=variant==2?Math.pow(10,j-3):j+1;data[i][j]=scale*(j*.4+new double[]{1,.8,.7,1,.9,.6}[j]*(j<3?a:b)+.65*random.nextGaussian());}}
            var edges=variant==1?List.of(new PlsSem.Edge(0,1)):List.<PlsSem.Edge>of();var prepared=SemModel.reflective(names,data,blocks,edges,true,mean);
            var options=new CovarianceSem.Options(3000,1e-10,1e-14,likelihood,mean,.95,variant==1?CovarianceSem.Information.OBSERVED:CovarianceSem.Information.EXPECTED);var fit=CovarianceSem.fit(prepared.select(data),prepared.model(),options);
            check(fit.converged());check(fit.identified());check(fit.degreesOfFreedom()==8);check(fit.chiSquare()>=0);check(fit.terms().stream().allMatch(v->v.standardError()>0));
            var value=new JsonObject();value.add("observations",gson.toJsonTree(data));value.add("model",gson.toJsonTree(prepared.model()));value.add("options",gson.toJsonTree(options));value.add("result",gson.toJsonTree(fit));cases.add(value);
            var repeat=CovarianceSem.fitCovariance(fit.sampleCovariance(),fit.sampleMeans(),data.length,prepared.model(),options);check(Math.abs(repeat.discrepancy()-fit.discrepancy())<1e-12);
            double saved=fit.impliedCovariance()[0][0];double[][] altered=fit.impliedCovariance();altered[0][0]=999;check(fit.impliedCovariance()[0][0]==saved);
        }
        var fixed=new SemModel.Model(List.of("x","y"),new int[]{0,1},new double[2][2],new double[][]{{2,.3},{.3,1}},new double[2],List.of());
        var fixedFit=CovarianceSem.fitCovariance(new double[][]{{2,.3},{.3,1}},new double[2],100,fixed,CovarianceSem.Options.defaults());check(fixedFit.converged()&&fixedFit.identified()&&fixedFit.estimates().length==0&&fixedFit.chiSquare()<1e-10);
        reject(()->CovarianceSem.fitCovariance(new double[][]{{1,2},{2,1}},new double[2],100,fixed,CovarianceSem.Options.defaults()));
        reject(()->CovarianceSem.fitCovariance(new double[][]{{1,.2},{.3,1}},new double[2],100,fixed,CovarianceSem.Options.defaults()));
        reject(()->CovarianceSem.fitCovariance(new double[][]{{1,0},{0,0}},new double[2],100,fixed,CovarianceSem.Options.defaults()));
        var duplicateVariance=new SemModel.Model(List.of("x","latent"),new int[]{0},new double[][]{{0,1},{0,0}},new double[2][2],new double[2],List.of(new SemModel.Parameter("error",List.of(new SemModel.Location(SemModel.Kind.COVARIANCE,0,0)),.5,1),new SemModel.Parameter("factor",List.of(new SemModel.Location(SemModel.Kind.COVARIANCE,1,1)),.5,1)));
        reject(()->CovarianceSem.fitCovariance(new double[][]{{1}},new double[1],100,duplicateVariance,CovarianceSem.Options.defaults()));
        // Two individually free factor loadings only identify their product.
        var unidentified=new SemModel.Model(List.of("x","y","f1","f2"),new int[]{0,1},new double[][]{{0,0,1,1},{0,0,1,1},{0,0,0,0},{0,0,0,0}},new double[][]{{1,0,0,0},{0,1,0,0},{0,0,0,0},{0,0,0,0}},new double[4],List.of(new SemModel.Parameter("v1",List.of(new SemModel.Location(SemModel.Kind.COVARIANCE,2,2)),.5,1),new SemModel.Parameter("v2",List.of(new SemModel.Location(SemModel.Kind.COVARIANCE,3,3)),.5,1)));
        var unfit=CovarianceSem.fitCovariance(new double[][]{{2,1},{1,2}},new double[2],200,unidentified,CovarianceSem.Options.defaults());check(!unfit.identified());check(Double.isNaN(unfit.terms().get(0).standardError()));
        List<SemModel.Parameter> residuals=new ArrayList<>();for(int j=0;j<3;j++)residuals.add(new SemModel.Parameter("error"+j,List.of(new SemModel.Location(SemModel.Kind.COVARIANCE,j,j)),.2+j*.3,1));
        var heywood=new SemModel.Model(List.of("x","y","z","f"),new int[]{0,1,2},new double[][]{{0,0,0,1},{0,0,0,.8},{0,0,0,.7},{0,0,0,0}},new double[][]{{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,1}},new double[4],residuals);
        var improper=CovarianceSem.fitCovariance(new double[][]{{.9,.8,.7},{.8,1.24,.56},{.7,.56,1.19}},new double[3],500,heywood,CovarianceSem.Options.defaults());check(improper.converged());check(Math.abs(improper.estimates()[0]+.1)<1e-5);check(improper.warnings().stream().anyMatch(v->v.contains("Heywood")));
        var bounded=new ArrayList<>(residuals);var first=residuals.get(0);bounded.set(0,new SemModel.Parameter(first.name(),first.locations(),first.start(),first.scale(),0.0,null));
        var boundedModel=new SemModel.Model(heywood.variables(),heywood.observed(),heywood.directed(),heywood.covariance(),heywood.intercepts(),bounded);
        var boundedFit=CovarianceSem.fitCovariance(new double[][]{{.9,.8,.7},{.8,1.24,.56},{.7,.56,1.19}},new double[3],500,boundedModel,CovarianceSem.Options.defaults());check(boundedFit.converged());check(Math.abs(boundedFit.estimates()[0])<1e-8);check(Double.isNaN(boundedFit.terms().get(0).standardError()));check(Double.isNaN(boundedFit.chiSquarePValue()));
        // A shared parameter location enforces equality without counting two free parameters.
        var shared=new SemModel.Model(List.of("x","y"),new int[]{0,1},new double[2][2],new double[2][2],new double[2],List.of(new SemModel.Parameter("equal variances",List.of(new SemModel.Location(SemModel.Kind.COVARIANCE,0,0),new SemModel.Location(SemModel.Kind.COVARIANCE,1,1)),1,1)));
        var sharedFit=CovarianceSem.fitCovariance(new double[][]{{2,0},{0,4}},new double[2],100,shared,CovarianceSem.Options.defaults());check(sharedFit.converged());check(Math.abs(sharedFit.estimates()[0]-3)<1e-6);check(sharedFit.degreesOfFreedom()==2);check(Math.abs(sharedFit.terms().get(0).standardError()-.3)<1e-5);
        var independent=new SemModel.Model(List.of("x","y"),new int[]{0,1},new double[2][2],new double[2][2],new double[2],List.of(new SemModel.Parameter("variance x",List.of(new SemModel.Location(SemModel.Kind.COVARIANCE,0,0)),1,1),new SemModel.Parameter("variance y",List.of(new SemModel.Location(SemModel.Kind.COVARIANCE,1,1)),1,1)));
        var independentFit=CovarianceSem.fitCovariance(new double[][]{{2,0},{0,4}},new double[2],100,independent,CovarianceSem.Options.defaults());var comparison=SemComparison.compare(sharedFit,independentFit);check(comparison.differenceDf()==1);check(Math.abs(comparison.difference()-100*Math.log(9.0/8))<1e-8);
        reject(()->SemComparison.compare(independentFit,sharedFit));reject(()->SemComparison.compare(sharedFit,CovarianceSem.fitCovariance(new double[][]{{2,0},{0,5}},new double[2],100,independent,CovarianceSem.Options.defaults())));
        var incompatible=new SemModel.Model(List.of("x","y"),new int[]{0,1},new double[2][2],new double[][]{{1,0},{0,0}},new double[2],List.of(independent.parameters().get(1)));check(!SemComparison.nested(shared,incompatible));
        JsonObject reportResults=new JsonObject();reportResults.add("sem",gson.toJsonTree(boundedFit));var archive=org.brightstats.report.ReportArchive.create("SEM",new JsonObject(),reportResults,new JsonArray(),List.of());var restored=org.brightstats.report.SemReports.fit(archive);check(Double.isNaN(restored.terms().get(0).standardError()));check(restored.model().parameters().get(0).lower()==0);check(restored.model().parameters().get(0).upper()==null);
        Thread.currentThread().interrupt();try{CovarianceSem.fitCovariance(new double[][]{{2,.3},{.3,1}},new double[2],100,fixed,CovarianceSem.Options.defaults());throw new AssertionError("Cancellation ignored");}catch(java.util.concurrent.CancellationException expected){checks++;}finally{Thread.interrupted();}
        Path output=Path.of(args[0]);Files.createDirectories(output);Files.writeString(output.resolve("cases.json"),gson.toJson(cases));System.out.println("{\"sem_cases\":"+cases.size()+",\"checks\":"+checks+"}");
    }
}
