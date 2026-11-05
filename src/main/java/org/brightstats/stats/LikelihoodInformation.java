package org.brightstats.stats;

import java.util.*;
import java.util.function.Function;
import org.apache.commons.math3.linear.*;

/** Observed information for an average negative likelihood in scaled coordinates. */
final class LikelihoodInformation {
    record Result(boolean identified,double[][] model,double[][] sandwich){}
    private LikelihoodInformation(){}
    static Result calculate(Function<double[],Bfgs.Evaluation> likelihood,double[] theta,double[] parameterScales,int observations,boolean regular,double[][] independentUnitScores){
        int q=theta.length;double[][] model=new double[q][q],sandwich=new double[q][q];for(var row:model)Arrays.fill(row,Double.NaN);for(var row:sandwich)Arrays.fill(row,Double.NaN);if(q==0)return new Result(true,model,sandwich);double[][] hessian=new double[q][q];
        for(int j=0;j<q;j++){Matrices.interrupted();double step=1e-4*(1+Math.abs(theta[j]));double[] plus=theta.clone(),minus=theta.clone();plus[j]+=step;minus[j]-=step;var a=likelihood.apply(plus);var b=likelihood.apply(minus);if(!Double.isFinite(a.value())||!Double.isFinite(b.value()))return new Result(false,model,sandwich);for(int i=0;i<q;i++)hessian[i][j]=(a.gradient()[i]-b.gradient()[i])/(2*step);}
        for(int i=0;i<q;i++)for(int j=0;j<i;j++)hessian[i][j]=hessian[j][i]=.5*(hessian[i][j]+hessian[j][i]);double[][] correlation=new double[q][q];double[] scale=new double[q];for(int j=0;j<q;j++)scale[j]=Math.sqrt(Math.max(0,hessian[j][j]));for(int i=0;i<q;i++)for(int j=0;j<q;j++)correlation[i][j]=hessian[i][j]/scale[i]/scale[j];if(!Arrays.stream(correlation).flatMapToDouble(Arrays::stream).allMatch(Double::isFinite)||Matrices.minEigenvalue(correlation)<=1e-9)return new Result(false,model,sandwich);if(!regular)return new Result(true,model,sandwich);
        try{RealMatrix bread=new CholeskyDecomposition(new Array2DRowRealMatrix(hessian),1e-8,1e-12).getSolver().getInverse();double[][] meat=new double[q][q];if(independentUnitScores!=null)for(var score:independentUnitScores)for(int i=0;i<q;i++)for(int j=0;j<q;j++)meat[i][j]+=score[i]*score[j];RealMatrix robust=bread.multiply(new Array2DRowRealMatrix(meat)).multiply(bread);for(int i=0;i<q;i++)for(int j=0;j<q;j++){model[i][j]=bread.getEntry(i,j)*parameterScales[i]*parameterScales[j]/observations;if(independentUnitScores!=null)sandwich[i][j]=robust.getEntry(i,j)*parameterScales[i]*parameterScales[j]/observations/observations;}return new Result(true,model,sandwich);}catch(NonPositiveDefiniteMatrixException|NonSymmetricMatrixException|SingularMatrixException invalid){return new Result(false,model,sandwich);}
    }
}
