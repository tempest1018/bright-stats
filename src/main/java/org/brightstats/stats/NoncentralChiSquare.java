package org.brightstats.stats;

import org.apache.commons.math3.special.Gamma;

/** Poisson-mixture noncentral chi-square CDF and inversion for RMSEA intervals. */
final class NoncentralChiSquare {
    private NoncentralChiSquare(){}
    static double cdf(double value,int df,double noncentrality){
        if(df<1||value<0||noncentrality<0||!Double.isFinite(value)||!Double.isFinite(noncentrality))throw new IllegalArgumentException("Invalid noncentral chi-square arguments.");
        if(noncentrality>1e7)throw new IllegalArgumentException("Noncentral chi-square exceeds the supported numerical range.");
        if(value==0)return 0;if(noncentrality==0)return Gamma.regularizedGammaP(df*.5,value*.5);
        double mean=noncentrality*.5;int centre=(int)Math.floor(mean),radius=(int)Math.ceil(Math.max(40,12*Math.sqrt(mean)));
        double centralWeight=Math.exp(-mean+centre*Math.log(mean)-Gamma.logGamma(centre+1.0));double result=centralWeight*Gamma.regularizedGammaP(df*.5+centre,value*.5),weight=centralWeight;
        for(int k=centre-1;k>=Math.max(0,centre-radius);k--){Matrices.interrupted();weight*= (k+1)/mean;result+=weight*Gamma.regularizedGammaP(df*.5+k,value*.5);}
        weight=centralWeight;for(int k=centre+1;k<=centre+radius;k++){Matrices.interrupted();weight*=mean/k;result+=weight*Gamma.regularizedGammaP(df*.5+k,value*.5);}
        return Math.max(0,Math.min(1,result));
    }
    static double noncentrality(double value,int df,double probability){
        if(probability<=0||probability>=1)throw new IllegalArgumentException("Invalid noncentral chi-square target probability.");
        if(cdf(value,df,0)<=probability)return 0;double low=0,high=Math.max(1,value+df);
        while(cdf(value,df,high)>probability){high*=2;if(high>1e7)throw new IllegalArgumentException("RMSEA interval exceeds the supported numerical range.");}
        for(int iteration=0;iteration<70;iteration++){double middle=(low+high)*.5;if(cdf(value,df,middle)>probability)low=middle;else high=middle;}
        return (low+high)*.5;
    }
}
