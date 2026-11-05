package org.brightstats.stats;
import org.apache.commons.math3.distribution.TDistribution;
/** Paired case-loss arithmetic. Repeat forecasts must be aggregated before calling this method. */
public final class PairedLossTest {
    private PairedLossTest(){}
    public record Result(int observations,double firstLoss,double secondLoss,double difference,double standardDeviation,
                         double standardError,double statistic,int degreesOfFreedom,double lower,double upper,
                         double upperOneSided,double pFirstBetter,double pTwoSided,String status){}
    public static Result evaluate(double[] first,double[] second,double confidence){
        if(first.length!=second.length||!Double.isFinite(confidence)||confidence<=0||confidence>=1)throw new IllegalArgumentException("Invalid paired-loss comparison.");
        int n=first.length;double a=0,b=0,mean=0,sumSquares=0;
        for(int i=0;i<n;i++){Matrices.interrupted();if(!Double.isFinite(first[i])||!Double.isFinite(second[i])||first[i]<0||second[i]<0)throw new IllegalArgumentException("Loss values must be finite and nonnegative.");double difference=first[i]-second[i];if(!Double.isFinite(difference))throw new IllegalArgumentException("Loss difference exceeded numeric range.");a+=(first[i]-a)/(i+1);b+=(second[i]-b)/(i+1);double delta=difference-mean;mean+=delta/(i+1);sumSquares+=delta*(difference-mean);}
        if(!Double.isFinite(mean)||!Double.isFinite(sumSquares))throw new IllegalArgumentException("Paired loss moments exceeded the finite numeric range.");
        double sd=n>1?Math.sqrt(Math.max(0,sumSquares/(n-1))):Double.NaN,se=n>1?sd/Math.sqrt(n):Double.NaN,t=Double.NaN,low=Double.NaN,high=Double.NaN,one=Double.NaN,p=Double.NaN,two=Double.NaN;
        String status=n<3?"INSUFFICIENT_PAIRED_CASES":!(se>0)?"ZERO_EMPIRICAL_LOSS_VARIANCE":"OK";
        if(status.equals("OK")){if(!Double.isFinite(se))throw new IllegalArgumentException("Loss variance exceeded numeric range.");var distribution=new TDistribution(n-1);t=mean/se;p=distribution.cumulativeProbability(t);two=Math.min(1,2*Math.min(p,distribution.cumulativeProbability(-t)));double critical=distribution.inverseCumulativeProbability((1+confidence)/2);low=mean-critical*se;high=mean+critical*se;one=mean+distribution.inverseCumulativeProbability(confidence)*se;}
        return new Result(n,n==0?Double.NaN:a,n==0?Double.NaN:b,n==0?Double.NaN:mean,sd,se,t,Math.max(0,n-1),low,high,one,p,two,status);
    }
}
