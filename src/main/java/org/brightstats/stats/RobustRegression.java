package org.brightstats.stats;

import java.util.*;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.distribution.NormalDistribution;

/** Intercept-inclusive OLS with classical or HC0–HC4 sandwich covariance. */
public final class RobustRegression {
    private RobustRegression(){}
    public enum Covariance { CLASSICAL, HC0, HC1, HC2, HC3, HC4 }
    public record Fit(OlsRegression.Fit ordinary,Covariance covariance,double confidence,String inferenceMethod,
                      double[][] coefficientCovariance,List<Inference.Term> terms,double[] fitted,double[] residuals,
                      double[] leverage,double[] studentizedResiduals,double[] cooksDistance,List<String> warnings) {
        public Fit {coefficientCovariance=Matrices.copy(coefficientCovariance);terms=List.copyOf(terms);fitted=fitted.clone();residuals=residuals.clone();leverage=leverage.clone();studentizedResiduals=studentizedResiduals.clone();cooksDistance=cooksDistance.clone();warnings=List.copyOf(warnings);}
        @Override public double[][] coefficientCovariance(){return Matrices.copy(coefficientCovariance);}@Override public double[] fitted(){return fitted.clone();}@Override public double[] residuals(){return residuals.clone();}@Override public double[] leverage(){return leverage.clone();}@Override public double[] studentizedResiduals(){return studentizedResiduals.clone();}@Override public double[] cooksDistance(){return cooksDistance.clone();}
    }
    public static Fit fit(double[][] predictors,double[] response,Covariance covariance,double confidence) {
        Objects.requireNonNull(covariance);Inference.confidence(confidence);var fit=OlsRegression.fit(predictors,response);int n=response.length,p=predictors[0].length+1;
        double[][] design=new double[n][p];for(int i=0;i<n;i++){design[i][0]=1;for(int j=1;j<p;j++)design[i][j]=predictors[i][j-1];}
        RealMatrix x=new Array2DRowRealMatrix(design,false),inverse=new QRDecomposition(x,1e-12).getSolver().getInverse(),bread=inverse.multiply(inverse.transpose());
        double[] fitted=new double[n],residuals=new double[n],leverage=new double[n],studentized=new double[n],cooks=new double[n];double mse=fit.residualSumOfSquares()/fit.residualDegreesOfFreedom();List<String> warnings=new ArrayList<>();
        for(int i=0;i<n;i++) {
            Matrices.interrupted();fitted[i]=fit.predict(predictors[i]);residuals[i]=response[i]-fitted[i];double h=0;for(int a=0;a<p;a++)for(int b=0;b<p;b++)h+=design[i][a]*bread.getEntry(a,b)*design[i][b];
            leverage[i]=Math.max(0,Math.min(1,h));double remaining=1-leverage[i];
            studentized[i]=mse>0&&remaining>1e-12?residuals[i]/Math.sqrt(mse*remaining):Double.NaN;
            cooks[i]=mse>0&&remaining>1e-12?residuals[i]*residuals[i]*leverage[i]/(p*mse*remaining*remaining):Double.NaN;
            if(remaining<=1e-12&&(covariance==Covariance.HC2||covariance==Covariance.HC3||covariance==Covariance.HC4))throw new IllegalArgumentException(covariance+" covariance is undefined for a unit-leverage observation (row "+(i+1)+").");
        }
        RealMatrix estimate;
        if(covariance==Covariance.CLASSICAL)estimate=bread.scalarMultiply(mse);
        else {
            double[][] meat=new double[p][p];for(int i=0;i<n;i++){double value=residuals[i]*residuals[i];if(covariance==Covariance.HC1)value*=n/(double)(n-p);else if(covariance==Covariance.HC2)value/=1-leverage[i];else if(covariance==Covariance.HC3)value/=Math.pow(1-leverage[i],2);else if(covariance==Covariance.HC4)value/=Math.pow(1-leverage[i],Math.min(4,n*leverage[i]/p));for(int a=0;a<p;a++)for(int b=0;b<p;b++)meat[a][b]+=design[i][a]*design[i][b]*value;}
            estimate=bread.multiply(new Array2DRowRealMatrix(meat,false)).multiply(bread);
        }
        double[] coefficients=fit.coefficients();List<Inference.Term> terms=new ArrayList<>();String method;
        if(covariance==Covariance.CLASSICAL){terms.addAll(Inference.regression(fit,confidence).terms());method="Classical homoskedastic covariance; two-sided Student-t reference";}
        else {
            method=covariance+" heteroskedasticity-consistent sandwich covariance; two-sided asymptotic normal Wald reference";double critical=new NormalDistribution().inverseCumulativeProbability((1+confidence)/2);
            for(int j=0;j<p;j++){double variance=estimate.getEntry(j,j);if(!Double.isFinite(variance)||variance< -1e-10)throw new IllegalArgumentException("Invalid sandwich covariance.");double se=Math.sqrt(Math.max(0,variance)),z=Inference.ratio(coefficients[j],se);terms.add(new Inference.Term(coefficients[j],se,z,Inference.normalTwoSided(z),coefficients[j]-critical*se,coefficients[j]+critical*se));}
            warnings.add("Robust covariance uses asymptotic normal inference; it is not a small-sample correction for clustering or serial dependence.");
        }
        if(Arrays.stream(leverage).anyMatch(h->h>=1-1e-12))warnings.add("Some observations have unit leverage; corresponding residual influence diagnostics are undefined.");
        return new Fit(fit,covariance,confidence,method,estimate.getData(),terms,fitted,residuals,leverage,studentized,cooks,warnings);
    }
}
