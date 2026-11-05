package org.brightstats.stats;

import java.util.*;
import org.apache.commons.math3.linear.*;

/** Validated matrix utilities shared by the statistical engines. */
public final class Matrices {
    private Matrices(){}
    public record Standardized(double[][] values,double[] means,double[] scales) {
        public Standardized { values=copy(values);means=means.clone();scales=scales.clone(); }
        @Override public double[][] values(){return copy(values);}
        @Override public double[] means(){return means.clone();}
        @Override public double[] scales(){return scales.clone();}
    }
    public static double[][] copy(double[][] data){return Arrays.stream(data).map(double[]::clone).toArray(double[][]::new);}
    public static int width(double[][] data,int minimumRows) {
        if(data==null||data.length<minimumRows||data[0]==null||data[0].length==0)throw new IllegalArgumentException("Expected at least "+minimumRows+" nonempty observations.");int p=data[0].length;
        for(double[] row:data){if(row==null||row.length!=p)throw new IllegalArgumentException("Ragged matrix.");for(double v:row)if(!Double.isFinite(v))throw new IllegalArgumentException("Expected finite observations; prepare missing values first.");}return p;
    }
    public static Standardized standardize(double[][] data,boolean scale,boolean sample) {
        int p=width(data,2),n=data.length;double[] means=new double[p],sd=new double[p];double[][] values=copy(data);
        for(int j=0;j<p;j++) {
            for(int i=0;i<n;i++)means[j]+=(data[i][j]-means[j])/(i+1);
            double sum=0;for(int i=0;i<n;i++){values[i][j]-=means[j];sum+=values[i][j]*values[i][j];}
            sd[j]=scale?Math.sqrt(sum/(sample?n-1:n)):1;
            if(!(sd[j]>0)||!Double.isFinite(sd[j])||!Double.isFinite(means[j]))throw new IllegalArgumentException("Constant or unscalable column "+(j+1));
            for(int i=0;i<n;i++){values[i][j]/=sd[j];if(!Double.isFinite(values[i][j]))throw new IllegalArgumentException("Numeric range exceeded during standardization.");}
        }
        return new Standardized(values,means,sd);
    }
    public static double[][] covariance(double[][] centered,boolean sample) {
        int p=width(centered,2),n=centered.length;double[][] result=new double[p][p];
        for(int a=0;a<p;a++)for(int b=0;b<=a;b++){double value=0;for(double[] row:centered)value+=row[a]*row[b]/(sample?n-1:n);if(!Double.isFinite(value))throw new IllegalArgumentException("Covariance exceeded finite range.");result[a][b]=result[b][a]=value;}return result;
    }
    public static double minEigenvalue(double[][] symmetric){return Arrays.stream(new EigenDecomposition(new Array2DRowRealMatrix(symmetric)).getRealEigenvalues()).min().orElseThrow();}
    public static void interrupted(){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Calculation cancelled.");}
}
