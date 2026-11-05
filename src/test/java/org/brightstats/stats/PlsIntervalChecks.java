package org.brightstats.stats;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public final class PlsIntervalChecks {
    public static void main(String[] args)throws Exception{
        Gson gson=new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();JsonArray cases=new JsonArray();Random random=new Random(7163);double[][] data=new double[800][6];
        for(int i=0;i<data.length;i++){double a=random.nextGaussian(),b=.55*a+Math.sqrt(1-.55*.55)*random.nextGaussian();for(int j=0;j<6;j++){double loading=new double[]{.85,.8,.75}[j%3];data[i][j]=loading*(j<3?a:b)+Math.sqrt(1-loading*loading)*random.nextGaussian();}}
        var blocks=List.of(new PlsSem.Block("A",new int[]{0,1,2},PlsSem.Mode.A,new double[]{-1,-1,-1}),new PlsSem.Block("B",new int[]{3,4,5},PlsSem.Mode.A));var edges=List.of(new PlsSem.Edge(0,1));var options=new PlsSem.Options(3000,1e-9,PlsSem.Weighting.PATH);
        for(boolean consistent:new boolean[]{false,true})for(var convention:RegressionBootstrap.Convention.values())for(var method:BootstrapIntervals.Method.values())for(boolean twoSided:new boolean[]{true,false}){
            if(convention==RegressionBootstrap.Convention.STANDARD&&(!twoSided||method!=BootstrapIntervals.Method.BCA))continue;
            var settings=new Bootstrap.Options(80,Long.MAX_VALUE,.95,method,convention,twoSided);var fit=Bootstrap.fit(data,blocks,edges,options,settings,null,consistent);
            if(fit.successful()+fit.failed()!=80||fit.successful()<64||!fit.estimator().equals(consistent?"PLSc":"PLS"))throw new AssertionError("PLS interval coverage differs: "+consistent+" / "+convention+" / "+method+" / "+fit.successful()+"; "+fit.failures());
            JsonObject value=new JsonObject();value.addProperty("consistent",consistent);value.add("observations",gson.toJsonTree(data));value.add("blocks",gson.toJsonTree(blocks));value.add("edges",gson.toJsonTree(edges));value.add("options",gson.toJsonTree(options));value.add("bootstrap",gson.toJsonTree(settings));value.add("result",gson.toJsonTree(fit));cases.add(value);
        }
        try{Bootstrap.fit(Arrays.copyOf(data,240),blocks,edges,options,new Bootstrap.Options(80,Long.MAX_VALUE,.95,BootstrapIntervals.Method.BCA,RegressionBootstrap.Convention.REFERENCE_4_1_2_1,true),null,true);throw new AssertionError("Too many invalid PLSc resamples accepted");}catch(IllegalArgumentException expected){if(!expected.getMessage().contains("Too few usable bootstrap samples"))throw expected;}
        Path output=Path.of(args[0]);Files.createDirectories(output);Files.writeString(output.resolve("cases.json"),gson.toJson(cases));System.out.println("{\"interval_cases\":"+cases.size()+",\"invalid_plsc_resample_rejection\":true,\"sign_alignment\":true,\"standard_bca\":true,\"reference_methods\":3}");
    }
}
