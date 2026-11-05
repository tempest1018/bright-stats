package org.brightstats.stats;

import java.util.*;

/** SmartPLS 4.1.2.1 interval/summary conventions, separate from standard bootstrap-t and jackknife BCa. */
public final class ReferenceBootstrapInference {
    private ReferenceBootstrapInference() {}
    public record Result(double mean,double standardError,double statistic,double pValue,
                         BootstrapIntervals.Interval interval,BootstrapIntervals.Interval correctedInterval) {}
    public static Result evaluate(double point,double[] draws,double confidence,BootstrapIntervals.Method method,boolean twoSided){
        Inference.confidence(confidence);Objects.requireNonNull(method);
        if(!Double.isFinite(point)||draws.length<2||!Arrays.stream(draws).allMatch(Double::isFinite))throw new IllegalArgumentException("Finite reference point and draws required.");
        int count=draws.length;double sum=0;for(double value:draws)sum+=value;double mean=sum/count,squares=0,cubes=0;
        for(double value:draws){double deviation=mean-value;squares+=Math.pow(deviation,2);cubes+=Math.pow(deviation,3);}
        double se=Math.sqrt(squares/count),sampleSd=Math.sqrt(squares/(count-1)),statistic=Math.abs(point/se);
        double p=ReferenceDistributions.tTwoSided(statistic,count)/(twoSided?1:2);
        double alpha=java.math.BigDecimal.ONE.subtract(java.math.BigDecimal.valueOf(confidence)).doubleValue()/(twoSided?2:1);double[] sorted=draws.clone();Arrays.sort(sorted);
        List<String> notes=new ArrayList<>();notes.add("Reference convention: population bootstrap SD; t reference with bootstrap count; discrete interval order statistics.");
        if(!twoSided)notes.add("Reference one-tailed interval endpoints use alpha and 1-alpha; central interval coverage is "+String.format(java.util.Locale.ROOT,"%.4f%%",100*(1-2*alpha))+".");
        if(se==0)notes.add("Degenerate empirical distribution; undefined test ratios must not be interpreted.");
        double lower,upper,adjustedLower,adjustedUpper,biasCorrection=Double.NaN,acceleration=Double.NaN;
        if(method==BootstrapIntervals.Method.STUDENTIZED){
            double spread=ReferenceDistributions.tCritical(alpha*2,count)*sampleSd;
            lower=point-spread;upper=point+spread;adjustedLower=2*point-mean-spread;adjustedUpper=2*point-mean+spread;
            notes.add("Reference studentized convention uses a t critical value and bootstrap spread; it is not nested bootstrap-t.");
        }else{
            lower=sorted[Math.max(0,(int)Math.ceil(count*alpha)-1)];upper=sorted[Math.max(0,(int)Math.floor(count*(1-alpha))-1)];
            int below=0;for(double value:draws)if(value<point)below++;biasCorrection=ReferenceDistributions.normalQuantile(below/(double)count);
            if(method==BootstrapIntervals.Method.BCA){
                acceleration=cubes/(6*Math.pow(squares,1.5));
                double zLow=ReferenceDistributions.normalQuantile(alpha)-biasCorrection,zHigh=ReferenceDistributions.normalQuantile(1-alpha)-biasCorrection;
                double upperProbability=ReferenceDistributions.normalCdf(biasCorrection-zLow/(1+acceleration*zLow));
                double lowerProbability=ReferenceDistributions.normalCdf(biasCorrection-zHigh/(1+acceleration*zHigh));
                adjustedUpper=sorted[Math.max(1,Math.min(count,(int)Math.floor(count*upperProbability)))-1];
                adjustedLower=sorted[Math.max(0,(int)Math.ceil(count*lowerProbability)-1)];
                notes.add("Reference acceleration uses bootstrap moments rather than leave-one-out jackknife influence.");
            }else{
                double upperProbability=ReferenceDistributions.normalCdf(2*biasCorrection-ReferenceDistributions.normalQuantile(alpha));
                double lowerProbability=ReferenceDistributions.normalCdf(2*biasCorrection-ReferenceDistributions.normalQuantile(1-alpha));
                adjustedUpper=sorted[Math.max(1,Math.min(count,(int)Math.floor(count*upperProbability)))-1];
                adjustedLower=sorted[Math.max(0,(int)Math.ceil(count*lowerProbability)-1)];
            }
        }
        return new Result(mean,se,statistic,p,new BootstrapIntervals.Interval(lower,upper,Double.NaN,Double.NaN,0,notes),
                new BootstrapIntervals.Interval(adjustedLower,adjustedUpper,biasCorrection,acceleration,0,notes));
    }
}
