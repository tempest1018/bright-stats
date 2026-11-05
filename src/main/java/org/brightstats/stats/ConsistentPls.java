package org.brightstats.stats;

import java.util.*;
import org.apache.commons.math3.linear.*;

/** Dijkstra-Henseler reliability correction for recursive, standardized factor/composite models. */
public final class ConsistentPls {
    private ConsistentPls(){}
    public record Fit(PlsSem.Fit composite,double[] reliabilities,double[][] correlations,double[][] loadings,
                      double[][] paths,double[] rSquared,double minimumEigenvalue,List<String> warnings) {
        public Fit {reliabilities=reliabilities.clone();correlations=Matrices.copy(correlations);loadings=Matrices.copy(loadings);paths=Matrices.copy(paths);rSquared=rSquared.clone();warnings=List.copyOf(warnings);}
        @Override public double[] reliabilities(){return reliabilities.clone();}@Override public double[][] correlations(){return Matrices.copy(correlations);}@Override public double[][] loadings(){return Matrices.copy(loadings);}@Override public double[][] paths(){return Matrices.copy(paths);}@Override public double[] rSquared(){return rSquared.clone();}
        public PlsSem.Fit inferenceFit(){return new PlsSem.Fit(composite.scores(),composite.weights(),loadings,paths,rSquared,composite.iterations(),composite.converged(),composite.finalWeightChange());}
    }
    public static Fit fit(double[][] input,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,PlsSem.Options options) {
        for(var block:blocks)if(block.measurementType()==PlsSem.MeasurementType.REFLECTIVE&&block.mode()!=PlsSem.Mode.A)throw new IllegalArgumentException("PLSc reflective factors require Mode A weights.");
        var composite=PlsSem.fit(input,blocks,edges,options);if(!composite.converged())throw new IllegalArgumentException("Original composite model did not converge.");
        int count=blocks.size(),n=input.length;double[][] scores=composite.scores(),weights=composite.weights(),loadings=composite.loadings(),correlations=new double[count][count];double[] reliability=new double[count];List<String> warnings=new ArrayList<>();
        for(int j=0;j<count;j++) {
            Matrices.interrupted();var block=blocks.get(j);int[] columns=block.columns();reliability[j]=1;
            if(block.measurementType()!=PlsSem.MeasurementType.REFLECTIVE)continue;
            if(columns.length==1){loadings[j][0]=Math.copySign(1,weights[j][0]);warnings.add("Single-indicator factor "+block.name()+" assumes reliability 1; measurement error is not separately identified.");continue;}
            double[][] selected=new double[n][columns.length];for(int i=0;i<n;i++)for(int k=0;k<columns.length;k++)selected[i][k]=input[i][columns[k]];
            double[][] r=Matrices.covariance(Matrices.standardize(selected,true,false).values(),false);double s2=0,s4=0,off=0;
            for(int a=0;a<columns.length;a++){s2+=weights[j][a]*weights[j][a];s4+=Math.pow(weights[j][a],4);for(int b=0;b<columns.length;b++)if(a!=b)off+=weights[j][a]*r[a][b]*weights[j][b];}
            double denominator=s2*s2-s4;double rho=s2*s2*off/denominator;
            if(!Double.isFinite(rho)||rho<=0||rho>1+1e-10)throw new IllegalArgumentException("Invalid PLSc reliability for "+block.name()+": "+rho+"; correction requires 0 < rho_A <= 1.");
            reliability[j]=Math.min(1,rho);for(int k=0;k<columns.length;k++){loadings[j][k]=weights[j][k]*Math.sqrt(reliability[j])/s2;if(Math.abs(loadings[j][k])>1+1e-8)throw new IllegalArgumentException("Improper PLSc loading for "+block.name()+" (absolute loading exceeds 1).");}
        }
        for(int i=0;i<count;i++)for(int j=0;j<=i;j++) {
            double value=0;for(double[] row:scores)value+=row[i]*row[j]/n;value=i==j?1:value/Math.sqrt(reliability[i]*reliability[j]);
            if(!Double.isFinite(value)||Math.abs(value)>1+1e-8)throw new IllegalArgumentException("Disattenuated factor correlation is inadmissible for "+blocks.get(i).name()+" and "+blocks.get(j).name());correlations[i][j]=correlations[j][i]=value;
        }
        double minimum=Matrices.minEigenvalue(correlations);if(!(minimum>1e-10))throw new IllegalArgumentException("Corrected construct correlation matrix is singular or not positive definite (minimum eigenvalue "+minimum+").");
        double[][] paths=new double[count][count];for(double[] row:paths)Arrays.fill(row,Double.NaN);double[] r2=new double[count];Arrays.fill(r2,Double.NaN);
        for(int target=0;target<count;target++) {
            int selectedTarget=target;int[] parents=edges.stream().filter(e->e.target()==selectedTarget).mapToInt(PlsSem.Edge::source).toArray();if(parents.length==0)continue;
            double[][] pp=new double[parents.length][parents.length];double[] py=new double[parents.length];for(int a=0;a<parents.length;a++){py[a]=correlations[parents[a]][target];for(int b=0;b<parents.length;b++)pp[a][b]=correlations[parents[a]][parents[b]];}
            double[] coefficients=new CholeskyDecomposition(new Array2DRowRealMatrix(pp),1e-10,1e-12).getSolver().solve(new ArrayRealVector(py)).toArray();double value=0;for(int a=0;a<parents.length;a++){paths[parents[a]][target]=coefficients[a];value+=py[a]*coefficients[a];}
            if(!Double.isFinite(value)||value< -1e-10||value>1+1e-10)throw new IllegalArgumentException("PLSc produces an inadmissible residual variance for "+blocks.get(target).name());r2[target]=Math.max(0,Math.min(1,value));
        }
        return new Fit(composite,reliability,correlations,loadings,paths,r2,minimum,warnings);
    }
}
