package org.brightstats.stats;

import java.util.*;
import org.apache.commons.math3.linear.*;

/** Centred PCA on sample covariance or sample correlation matrices. */
public final class PrincipalComponents {
    private PrincipalComponents(){}
    public record Fit(boolean correlation,int retained,double[] means,double[] scales,double[] eigenvalues,double[] explainedRatio,
                      double[][] vectors,double[][] loadings,double[][] scores,List<String> warnings) {
        public Fit {means=means.clone();scales=scales.clone();eigenvalues=eigenvalues.clone();explainedRatio=explainedRatio.clone();vectors=Matrices.copy(vectors);loadings=Matrices.copy(loadings);scores=Matrices.copy(scores);warnings=List.copyOf(warnings);}
        @Override public double[] means(){return means.clone();}@Override public double[] scales(){return scales.clone();}@Override public double[] eigenvalues(){return eigenvalues.clone();}@Override public double[] explainedRatio(){return explainedRatio.clone();}
        @Override public double[][] vectors(){return Matrices.copy(vectors);}@Override public double[][] loadings(){return Matrices.copy(loadings);}@Override public double[][] scores(){return Matrices.copy(scores);}
        public double[][] transform(double[][] input){int p=Matrices.width(input,1);if(p!=means.length)throw new IllegalArgumentException("PCA input columns differ from training.");double[][] result=new double[input.length][retained];for(int i=0;i<input.length;i++)for(int k=0;k<retained;k++)for(int j=0;j<p;j++)result[i][k]+=(input[i][j]-means[j])/scales[j]*vectors[j][k];return result;}
    }
    public static Fit fit(double[][] input,boolean correlation,int retained) {
        int p=Matrices.width(input,2);if(retained<1||retained>p)throw new IllegalArgumentException("Retain between 1 and "+p+" components.");
        var standardized=Matrices.standardize(input,correlation,true);double[][] centered=standardized.values();var decomposition=new EigenDecomposition(new Array2DRowRealMatrix(Matrices.covariance(centered,true)));
        Integer[] order=new Integer[p];for(int j=0;j<p;j++)order[j]=j;Arrays.sort(order,Comparator.comparingDouble((Integer j)->decomposition.getRealEigenvalue(j)).reversed());
        double[] eigenvalues=new double[p],ratios=new double[p];double[][] vectors=new double[p][retained],loadings=new double[p][retained],scores=new double[input.length][retained];double total=0;
        for(int k=0;k<p;k++){Matrices.interrupted();double eigen=decomposition.getRealEigenvalue(order[k]);if(eigen< -1e-9)throw new IllegalArgumentException("PCA covariance is not positive semidefinite.");eigenvalues[k]=Math.max(0,eigen);total+=eigenvalues[k];
            if(k<retained){double[] vector=decomposition.getEigenvector(order[k]).toArray();int anchor=0;for(int j=1;j<p;j++)if(Math.abs(vector[j])>Math.abs(vector[anchor]))anchor=j;double sign=vector[anchor]<0?-1:1;for(int j=0;j<p;j++){vectors[j][k]=vector[j]*sign;loadings[j][k]=vectors[j][k]*Math.sqrt(eigenvalues[k]);}}}
        if(!(total>0)||!Double.isFinite(total))throw new IllegalArgumentException("No finite variance is available for PCA.");for(int k=0;k<p;k++)ratios[k]=eigenvalues[k]/total;
        for(int i=0;i<input.length;i++)for(int k=0;k<retained;k++)for(int j=0;j<p;j++)scores[i][k]+=centered[i][j]*vectors[j][k];
        List<String> warnings=new ArrayList<>();if(eigenvalues[p-1]<=total*1e-12)warnings.add("Rank-deficient covariance: zero-variance components do not supply independent information.");
        for(int k=1;k<p;k++)if(Math.abs(eigenvalues[k]-eigenvalues[k-1])<=total*1e-12){warnings.add("Repeated eigenvalues: individual component directions are not uniquely identified; compare the eigenspace.");break;}
        return new Fit(correlation,retained,standardized.means(),standardized.scales(),eigenvalues,ratios,vectors,loadings,scores,warnings);
    }
}
