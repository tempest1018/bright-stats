package org.brightstats.stats;

import java.util.*;
import java.util.function.IntConsumer;

/** Explicit post-estimation score metrics; standardized estimation is unchanged. */
public final class PlsScales {
    private PlsScales(){}
    public enum Metric {STANDARDIZED,UNSTANDARDIZED,MEANCENTERED}
    public record Fit(Metric metric,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,PlsSem.Options options,
                      PlsSem.Fit standardized,double[][] scores,double[][] weights,double[][] measurementSlopes,
                      double[][] measurementIntercepts,double[][] paths,double[] intercepts,double[] scoreMeans,
                      double[] scoreScales,List<String> warnings){
        public Fit{Objects.requireNonNull(metric);blocks=List.copyOf(blocks);edges=List.copyOf(edges);warnings=List.copyOf(warnings);scores=Matrices.copy(scores);weights=Matrices.copy(weights);measurementSlopes=Matrices.copy(measurementSlopes);measurementIntercepts=Matrices.copy(measurementIntercepts);paths=Matrices.copy(paths);intercepts=intercepts.clone();scoreMeans=scoreMeans.clone();scoreScales=scoreScales.clone();}
        @Override public double[][] scores(){return Matrices.copy(scores);}
        @Override public double[][] weights(){return Matrices.copy(weights);}
        @Override public double[][] measurementSlopes(){return Matrices.copy(measurementSlopes);}
        @Override public double[][] measurementIntercepts(){return Matrices.copy(measurementIntercepts);}
        @Override public double[][] paths(){return Matrices.copy(paths);}
        @Override public double[] intercepts(){return intercepts.clone();}
        @Override public double[] scoreMeans(){return scoreMeans.clone();}
        @Override public double[] scoreScales(){return scoreScales.clone();}
    }
    public record Resampled(Fit point,Bootstrap.Result inference){}
    public static Fit fit(double[][] data,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,PlsSem.Options options,Metric metric){return convert(data,blocks,edges,options,metric,PlsSem.fit(data,blocks,edges,options));}
    private static Fit convert(double[][] data,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,PlsSem.Options options,Metric metric,PlsSem.Fit core){
        Objects.requireNonNull(metric);int width=Matrices.width(data,3),n=data.length,m=blocks.size();double[] means=new double[width],sd=new double[width];
        for(var row:data)for(int c=0;c<width;c++)means[c]+=row[c]/n;
        for(var row:data)for(int c=0;c<width;c++)sd[c]+=(row[c]-means[c])*(row[c]-means[c])/n;
        for(int c=0;c<width;c++)sd[c]=Math.sqrt(sd[c]);
        double[][] weights=core.weights(),slopes=core.loadings(),measurementIntercepts=new double[m][],scores=core.scores(),paths=core.pathCoefficients();double[] location=new double[m],scale=new double[m],intercepts=new double[m];Arrays.fill(scale,1);Arrays.fill(intercepts,Double.NaN);
        for(int j=0;j<m;j++){
            Matrices.interrupted();int[] columns=blocks.get(j).columns();measurementIntercepts[j]=new double[columns.length];
            if(metric!=Metric.STANDARDIZED){double normalizer=0,absolute=0;for(int k=0;k<columns.length;k++){double value=weights[j][k]/sd[columns[k]];normalizer+=value;absolute+=Math.abs(value);weights[j][k]=value;}
                if(!Double.isFinite(normalizer)||!Double.isFinite(absolute)||!(absolute>0)||Math.abs(normalizer)<=1e-10*absolute)throw new IllegalArgumentException("Raw-scale weights have a vanishing signed sum in block "+blocks.get(j).name()+"; choose standardized results or revise the measurement model.");
                scale[j]=1/normalizer;for(int k=0;k<columns.length;k++){weights[j][k]/=normalizer;location[j]+=weights[j][k]*means[columns[k]];slopes[j][k]=slopes[j][k]*sd[columns[k]]/scale[j];}
                if(metric==Metric.MEANCENTERED)location[j]=0;
                for(int row=0;row<n;row++){double value=0;for(int k=0;k<columns.length;k++)value+=weights[j][k]*(data[row][columns[k]]-(metric==Metric.MEANCENTERED?means[columns[k]]:0));scores[row][j]=value;}
                for(int k=0;k<columns.length;k++)measurementIntercepts[j][k]=(metric==Metric.UNSTANDARDIZED?means[columns[k]]:0)-slopes[j][k]*location[j];
            }
        }
        for(var edge:edges)paths[edge.source()][edge.target()]*=scale[edge.target()]/scale[edge.source()];
        for(int target=0;target<m;target++){final int t=target;if(edges.stream().anyMatch(edge->edge.target()==t)){intercepts[target]=location[target];for(var edge:edges)if(edge.target()==target)intercepts[target]-=paths[edge.source()][target]*location[edge.source()];}}
        for(var row:scores)for(double value:row)if(!Double.isFinite(value))throw new IllegalArgumentException("Score conversion exceeds the finite numerical range.");
        for(var row:slopes)for(double value:row)if(!Double.isFinite(value))throw new IllegalArgumentException("Measurement conversion exceeds the finite numerical range.");
        for(var edge:edges)if(!Double.isFinite(paths[edge.source()][edge.target()])||!Double.isFinite(intercepts[edge.target()]))throw new IllegalArgumentException("Structural conversion exceeds the finite numerical range.");
        List<String>warnings=new ArrayList<>();warnings.add("Metric conversion follows standardized PLS estimation. Reliability, fit, correlations and R-squared remain standardized diagnostics; raw measurement slopes are not correlations.");warnings.add("Raw scores use signed-sum-normalized, inverse-indicator-SD weights. A near-zero signed sum is rejected. Metric/binary/category-specific native conventions are not inferred from this continuous numerical contract.");if(!core.converged())warnings.add("The standardized PLS iteration did not converge; inspect convergence before interpreting results.");
        return new Fit(metric,blocks,edges,options,core,scores,weights,slopes,measurementIntercepts,paths,intercepts,location,scale,warnings);
    }
    public static Resampled bootstrap(double[][] data,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,PlsSem.Options options,Metric metric,Bootstrap.Options bootstrap,IntConsumer progress){
        Fit point=fit(data,blocks,edges,options,metric);if(!point.standardized().converged())throw new IllegalArgumentException("Point PLS fit did not converge; inference is unavailable.");List<Bootstrap.Parameter> parameters=parameters(point);double[] original=values(point);double[][] reference=point.standardized().scores();
        var inference=Resampling.run(data,parameters,original,bootstrap,(sample,rows)->{var core=PlsSem.fit(sample,blocks,edges,options);if(!core.converged())throw new IllegalArgumentException("PLS resample did not converge.");return values(convert(sample,blocks,edges,options,metric,align(core,reference,rows)));},progress,"PLS result metrics: "+metric,"All draws refit PLS and recompute indicator means/SDs, signed-sum normalizers and locations. Standardized signs align using sampled original-row indices; raw signed-sum normalization is sign invariant.");
        if(metric!=Metric.UNSTANDARDIZED)inference=Resampling.suppressTests(inference,p->p.family().equals("Structural intercept")||p.family().equals("Measurement intercept"),"Centered metric intercepts are structural zero identities; tests and p-values are suppressed.");
        return new Resampled(point,inference);
    }
    private static PlsSem.Fit align(PlsSem.Fit fit,double[][] reference,int[] rows){double[][] scores=fit.scores(),weights=fit.weights(),loadings=fit.loadings(),paths=fit.pathCoefficients();int m=weights.length;double[] sign=new double[m];for(int j=0;j<m;j++){double dot=0;for(int i=0;i<rows.length;i++)dot+=scores[i][j]*reference[rows[i]][j];sign[j]=dot<0?-1:1;for(int i=0;i<scores.length;i++)scores[i][j]*=sign[j];for(int k=0;k<weights[j].length;k++){weights[j][k]*=sign[j];loadings[j][k]*=sign[j];}}for(int i=0;i<m;i++)for(int j=0;j<m;j++)paths[i][j]*=sign[i]*sign[j];return new PlsSem.Fit(scores,weights,loadings,paths,fit.rSquared(),fit.iterations(),fit.converged(),fit.finalWeightChange());}
    public static List<Bootstrap.Parameter> parameters(Fit fit){List<Bootstrap.Parameter> out=new ArrayList<>();for(var edge:fit.edges)out.add(new Bootstrap.Parameter("Structural slope",edge.source(),edge.target(),-1,fit.blocks.get(edge.source()).name()+" -> "+fit.blocks.get(edge.target()).name()));for(int j=0;j<fit.blocks.size();j++){final int t=j;if(fit.edges.stream().anyMatch(e->e.target()==t))out.add(new Bootstrap.Parameter("Structural intercept",-1,j,-1,fit.blocks.get(j).name()));for(int k=0;k<fit.weights[j].length;k++){int column=fit.blocks.get(j).columns()[k];for(String family:List.of("Outer weight","Measurement slope","Measurement intercept"))out.add(new Bootstrap.Parameter(family,j,j,column,fit.blocks.get(j).name()+" ["+column+"]"));}}return List.copyOf(out);}
    public static double[] values(Fit fit){List<Double> out=new ArrayList<>();for(var edge:fit.edges)out.add(fit.paths[edge.source()][edge.target()]);for(int j=0;j<fit.blocks.size();j++){if(Double.isFinite(fit.intercepts[j]))out.add(fit.intercepts[j]);for(int k=0;k<fit.weights[j].length;k++){out.add(fit.weights[j][k]);out.add(fit.measurementSlopes[j][k]);out.add(fit.measurementIntercepts[j][k]);}}return out.stream().mapToDouble(Double::doubleValue).toArray();}
}
