package org.brightstats.stats;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
public final class NonlinearChecks {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("Nonlinear check "+checks);checks++;}
    private static void reject(Runnable action){try{action.run();throw new AssertionError("Invalid nonlinear model accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args[0]);Files.createDirectories(output);var gson=new GsonBuilder().serializeSpecialFloatingPointValues().create();JsonArray cases=new JsonArray();
        for(int scenario=0;scenario<8;scenario++) {
            int n=120,count=scenario==2||scenario==4||scenario==6?4:3;boolean multi=scenario==6;
            double[][] data=new double[n][multi?12:count];Random random=new Random(73643+scenario);
            for(double[] row:data){double x=random.nextGaussian(),w=random.nextGaussian(),z=random.nextGaussian(),noise=random.nextGaussian(),m=.6*x+.2*w+.6*random.nextGaussian();
                double y=.5*x+.2*w+.45*x*w+.4*noise;
                if(scenario==1)y=.5*x+.2*w-.45*x*x+.4*noise;
                if(scenario==2)y=.4*x+.2*w+.1*z+.3*x*w+.35*x*w*z+.4*noise;
                if(scenario==3)y=.4*x+.2*w+.2*x*x+.1*x*w+.35*x*x*w+.4*noise;
                if(scenario==4||scenario==6)y=.2*x+.6*m+.4*m*w+.4*noise;
                if(scenario==5){w=m;y=.3*x+.7*w+.4*noise;}
                double[] latent=count==4?(scenario==2?new double[]{x,w,z,y}:new double[]{x,m,w,y}):new double[]{x,w,y};
                if(multi)for(int j=0;j<4;j++)for(int indicator=0;indicator<3;indicator++)row[3*j+indicator]=(.8+.1*indicator)*latent[j]+.25*random.nextGaussian();
                else System.arraycopy(latent,0,row,0,count);
            }
            List<PlsSem.Block> blocks=new ArrayList<>();for(int j=0;j<count;j++)blocks.add(new PlsSem.Block("C"+j,multi?new int[]{3*j,3*j+1,3*j+2}:new int[]{j},multi&&j==1?PlsSem.Mode.B:PlsSem.Mode.A,scenario==7&&j==0?new double[]{-1}:null));
            int target=count-1,moderator=scenario==4||scenario==6?2:1;List<PlsSem.Edge> edges=new ArrayList<>();
            if(scenario==4||scenario==6){edges.add(new PlsSem.Edge(0,1));edges.add(new PlsSem.Edge(1,target));edges.add(new PlsSem.Edge(0,target));}
            else if(scenario==5){edges.add(new PlsSem.Edge(0,1));edges.add(new PlsSem.Edge(1,2));edges.add(new PlsSem.Edge(0,2));}
            else edges.add(new PlsSem.Edge(0,target));
            List<NonlinearPls.Extension> extensions=switch(scenario){case 1->List.of(new NonlinearPls.Extension(target,new int[]{0,0}));case 2->List.of(new NonlinearPls.Extension(target,new int[]{0,1,2}));case 3->List.of(new NonlinearPls.Extension(target,new int[]{0,0,1}));case 5->List.of();case 4,6->List.of(new NonlinearPls.Extension(target,new int[]{1,2}));default->List.of(new NonlinearPls.Extension(target,new int[]{0,1}));};
            var probes=List.of(new PathProcess.Probe("Low",Map.of(moderator,-1.0,0,-.5)),new PathProcess.Probe("Mean",Map.of()),new PathProcess.Probe("High",Map.of(moderator,1.0,0,.5)));
            var specification=new NonlinearPls.Specification(extensions,probes,new NonlinearPls.Grid(0,target,-2,2,7));var options=PlsSem.Options.defaults();var fit=NonlinearPls.fit(data,blocks,edges,options,specification);
            check(fit.firstStage().converged());check(fit.curves().size()==21);check(fit.observations()==n);check(fit.effects().stream().allMatch(effect->Double.isFinite(effect.value())));check(fit.mainEffects().stream().anyMatch(edge->edge.source()==moderator&&edge.target()==target)||scenario==1);check(fit.warnings().size()>=5);
            double original=fit.scores()[0][0];double[][] changed=fit.scores();changed[0][0]=999;check(fit.scores()[0][0]==original);
            if(scenario==2)check(fit.equations().getLast().regressors().size()==7);
            if(scenario==3)check(fit.equations().getLast().regressors().size()==5);
            if(scenario==4||scenario==6)check(fit.effects().stream().anyMatch(effect->effect.family().equals("Specific indirect derivative")));
            if(scenario==5)for(var equation:fit.equations())for(int j=0;j<equation.regressors().size();j++)check(Math.abs(equation.coefficients()[j+1]-fit.firstStage().pathCoefficients()[equation.regressors().get(j).factors()[0]][equation.target()])<1e-12);
            JsonObject item=new JsonObject();item.addProperty("id","nonlinear-"+scenario);item.add("observations",gson.toJsonTree(data));item.add("blocks",gson.toJsonTree(blocks));item.add("edges",gson.toJsonTree(edges));item.add("options",gson.toJsonTree(options));item.add("specification",gson.toJsonTree(specification));item.add("fit",gson.toJsonTree(fit));
            if(scenario==0||scenario==1||scenario==4||scenario==5||scenario==6){var settings=new Bootstrap.Options(40,183,.95,scenario==1?BootstrapIntervals.Method.BCA:BootstrapIntervals.Method.PERCENTILE,RegressionBootstrap.Convention.STANDARD,true);var result=NonlinearPls.bootstrap(data,blocks,edges,options,specification,settings,null);check(result.inference().successful()==40&&result.inference().failed()==0);check(result.inference().signHandling().contains("refits main-effects PLS"));item.add("bootstrapOptions",gson.toJsonTree(settings));item.add("bootstrap",gson.toJsonTree(result.inference()));if(scenario==0){JsonObject project=new JsonObject();project.add("observations",gson.toJsonTree(data));project.add("blocks",gson.toJsonTree(blocks));project.add("edges",gson.toJsonTree(edges));JsonObject numeric=new JsonObject();numeric.add("bootstrap",gson.toJsonTree(result.inference()));numeric.add("nonlinearPls",gson.toJsonTree(fit));var archive=org.brightstats.report.ReportArchive.create("NONLINEAR_TEST",project,numeric,new JsonArray(),List.of());check(Double.isNaN(org.brightstats.report.NonlinearReports.point(archive).firstStage().rSquared()[0]));var encoded=archive.getAsJsonObject("results").getAsJsonObject("bootstrap");var decoded=org.brightstats.report.ResamplingReports.read(encoded);check(decoded.estimates().getFirst().lower()==result.inference().estimates().getFirst().lower());check(Double.isNaN(decoded.estimates().getFirst().interval().acceleration()));var estimate=encoded.getAsJsonArray("estimates").get(0).getAsJsonObject();estimate.add("lower",JsonNull.INSTANCE);estimate.getAsJsonObject("interval").add("lower",JsonNull.INSTANCE);check(Double.isNaN(org.brightstats.report.ResamplingReports.read(encoded).estimates().getFirst().lower()));var damaged=encoded.deepCopy();damaged.addProperty("successful",0);reject(()->org.brightstats.report.ResamplingReports.read(damaged));}}
            cases.add(item);
        }
        var blocks=List.of(new PlsSem.Block("X",new int[]{0},PlsSem.Mode.A),new PlsSem.Block("Y",new int[]{1},PlsSem.Mode.A));var edges=List.of(new PlsSem.Edge(0,1));var options=PlsSem.Options.defaults();var empty=new NonlinearPls.Specification(List.of(),List.of(),null);
        reject(()->new NonlinearPls.Extension(1,new int[]{1,1}));reject(()->new NonlinearPls.Extension(1,new int[]{0}));reject(()->new NonlinearPls.Grid(0,1,-Double.MAX_VALUE,Double.MAX_VALUE,7));
        reject(()->NonlinearPls.validate(blocks,List.of(new PlsSem.Edge(0,1),new PlsSem.Edge(1,0)),options,empty));reject(()->NonlinearPls.validate(blocks,List.of(new PlsSem.Edge(0,1),new PlsSem.Edge(0,1)),options,empty));reject(()->NonlinearPls.validate(blocks,edges,options,new NonlinearPls.Specification(List.of(),List.of(new PathProcess.Probe("Bad",Map.of(2,0.0))),null)));
        reject(()->NonlinearPls.fit(new double[][]{{0,1},{1,2},{0,3},{1,4},{0,5},{1,6}},blocks,edges,options,new NonlinearPls.Specification(List.of(new NonlinearPls.Extension(1,new int[]{0,0})),List.of(),null)));
        Thread.currentThread().interrupt();try{NonlinearPls.fit(new double[][]{{1,2},{2,3},{3,5},{4,4}},blocks,edges,options,empty);throw new AssertionError("Nonlinear fit ignored cancellation");}catch(java.util.concurrent.CancellationException expected){checks++;}finally{Thread.interrupted();}
        Files.writeString(output.resolve("cases.json"),gson.toJson(cases));Files.writeString(output.resolve("checks.json"),"{\"cases\":"+cases.size()+",\"checks\":"+checks+"}");System.out.println("{\"cases\":"+cases.size()+",\"checks\":"+checks+"}");
    }
}
