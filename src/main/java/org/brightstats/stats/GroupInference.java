package org.brightstats.stats;

import java.util.*;
import org.apache.commons.math3.distribution.TDistribution;

/** Parametric/Welch MGA tests with independently selectable reference formulas. */
public final class GroupInference {
    private GroupInference(){}
    public record Test(double difference,double standardError,double statistic,double degreesOfFreedom,double pValue,String method){}
    public record Pair(Test parametric,Test welch){}
    public static Pair evaluate(double a,double b,double seA,double seB,int nA,int nB,boolean reference,boolean twoSided){
        if(!Double.isFinite(a)||!Double.isFinite(b)||!Double.isFinite(seA)||!Double.isFinite(seB)||seA<0||seB<0||nA<3||nB<3)throw new IllegalArgumentException("Invalid group inference estimates, errors or sizes.");double difference=a-b,aa=seA*seA,bb=seB*seB,df=nA+nB-2,pooled=reference?((nA-1.0)*(nA-1)*aa+(nB-1.0)*(nB-1)*bb)/df:((nA-1.0)*nA*aa+(nB-1.0)*nB*bb)/df,sePooled=Math.sqrt(pooled*(1.0/nA+1.0/nB)),va=reference?(nA-1.0)/nA*aa:aa,vb=reference?(nB-1.0)/nB*bb:bb,seWelch=Math.sqrt(va+vb),welchDf;
        if(reference){double denominator=(nA-1.0)/(nA*(double)nA)*aa+(nB-1.0)/(nB*(double)nB)*bb;welchDf=denominator==0?Double.NaN:Math.round((va+vb)/denominator-2);}else{double denominator=aa*aa/(nA-1)+bb*bb/(nB-1);welchDf=denominator==0?Double.NaN:(aa+bb)*(aa+bb)/denominator;}
        if(!Double.isFinite(difference)||!Double.isFinite(sePooled)||!Double.isFinite(seWelch))throw new IllegalArgumentException("Group inference exceeds the finite numeric range.");return new Pair(test(difference,sePooled,df,reference,twoSided,reference?"Reference pooled-MGA formula":"Pooled coefficient-variance approximation"),test(difference,seWelch,welchDf,reference,twoSided,reference?"Reference rounded unequal-variance MGA formula":"Welch-Satterthwaite coefficient-variance approximation"));
    }
    private static Test test(double difference,double se,double df,boolean reference,boolean twoSided,String method){double statistic=reference&&se==0?0:Math.abs(Inference.ratio(difference,se)),p=Double.NaN;if(df>0&&Double.isFinite(df)&&!Double.isNaN(statistic)){p=reference?ReferenceDistributions.tTwoSided(statistic,(int)df)/(twoSided?1:2):(twoSided?2:1)*new TDistribution(df).cumulativeProbability(-statistic);}return new Test(difference,se,statistic,df,p,method);}
    public static double nonparametric(double originalA,double originalB,double[] a,double[] b,boolean reference){
        if(!Double.isFinite(originalA)||!Double.isFinite(originalB)||a.length<1||b.length<1||!Arrays.stream(a).allMatch(Double::isFinite)||!Arrays.stream(b).allMatch(Double::isFinite))throw new IllegalArgumentException("Invalid PLS-MGA distributions.");if(reference&&Math.abs(originalA)<=1e-10&&Math.abs(originalB)<=1e-10)return Double.NaN;double offset=originalA-Arrays.stream(a).average().orElseThrow()-originalB+Arrays.stream(b).average().orElseThrow();double[] sorted=b.clone();Arrays.sort(sorted);long positive=0,tied=0;for(double value:a){Matrices.interrupted();int strict=boundary(sorted,value,offset,false);positive+=strict;if(!reference)tied+=boundary(sorted,value,offset,true)-strict;}double total=(double)a.length*b.length;return Math.max(0,Math.min(1,1-(positive+(reference?0:.5*tied))/total));
    }
    private static int boundary(double[] sorted,double a,double offset,boolean inclusive){int low=0,high=sorted.length;while(low<high){int middle=(low+high)>>>1;double difference=a-sorted[middle]+offset;if(inclusive?difference>=0:difference>0)low=middle+1;else high=middle;}return low;}
}
