package org.brightstats.stats;

import java.util.*;
import java.util.function.IntConsumer;

/** Case bootstrap for general GSCA masks, with sampled-row component orientation alignment. */
public final class GscaBootstrap {
    private GscaBootstrap(){}
    public record Result(Gsca.Fit point,Bootstrap.Result inference){}
    public static Result fit(double[][] observations,Gsca.Model model,Gsca.Options settings,Bootstrap.Options options,IntConsumer progress){
        var original=Gsca.fit(observations,model,settings);if(!original.converged())throw new IllegalArgumentException("Original GSCA did not converge.");List<Bootstrap.Parameter> parameters=parameters(model);double[][] reference=original.scores();
        var result=Resampling.run(observations,parameters,values(original,parameters,null),options,(sample,rows)->{var fitted=Gsca.fit(sample,model,settings);if(!fitted.converged())throw new IllegalArgumentException("GSCA did not converge");return values(fitted,parameters,Bootstrap.alignment(fitted.scores(),reference,rows));},progress,"GSCA","Components aligned to original scores at sampled row indices");
        result=Resampling.suppressTests(result,v->v.family().equals("Fit")||v.family().equals("R-square"),"Quality-index intervals summarize sampling uncertainty; no ordinary zero-null ratio p-values are reported for FIT/AFIT or R-square.");return new Result(original,result);
    }
    private static List<Bootstrap.Parameter> parameters(Gsca.Model model){
        List<Bootstrap.Parameter> result=new ArrayList<>();int k=model.components().size();boolean[][] weights=model.weights(),loadings=model.loadings(),paths=model.paths();
        for(int j=0;j<k;j++){int weighted=0;for(boolean[] row:weights)if(row[j])weighted++;for(int i=0;i<weights.length;i++){String label=model.components().get(j)+" / "+model.indicators().get(i);if(weights[i][j]&&weighted>1)result.add(new Bootstrap.Parameter("Weight",j,-1,i,label));if(loadings[i][j])result.add(new Bootstrap.Parameter("Loading",j,-1,i,label));}boolean parent=false;for(int i=0;i<k;i++){if(paths[i][j]){parent=true;result.add(new Bootstrap.Parameter("Path",i,j,-1,model.components().get(i)+" → "+model.components().get(j)));}if(i<j)result.add(new Bootstrap.Parameter("Correlation",i,j,-1,model.components().get(i)+" ↔ "+model.components().get(j)));}if(parent)result.add(new Bootstrap.Parameter("R-square",j,-1,-1,model.components().get(j)));}
        for(int i=0;i<4;i++)result.add(new Bootstrap.Parameter("Fit",i,-1,-1,List.of("FIT","AFIT","FIT measurement","FIT structural").get(i)));return result;
    }
    private static double[] values(Gsca.Fit fit,List<Bootstrap.Parameter> parameters,double[] supplied){
        double[] signs=supplied;if(signs==null){signs=new double[fit.model().components().size()];Arrays.fill(signs,1);}double[][] w=fit.weights(),c=fit.loadings(),b=fit.paths(),cor=fit.correlations();double[] r2=fit.rSquared(),quality={fit.fit(),fit.afit(),fit.measurementFit(),fit.structuralFit()},values=new double[parameters.size()];
        for(int i=0;i<values.length;i++){var term=parameters.get(i);int source=term.source(),target=term.target();values[i]=switch(term.family()){case "Weight"->w[term.column()][source]*signs[source];case "Loading"->c[term.column()][source]*signs[source];case "Path"->b[source][target]*signs[source]*signs[target];case "Correlation"->cor[source][target]*signs[source]*signs[target];case "R-square"->r2[source];default->quality[source];};}return values;
    }
}
