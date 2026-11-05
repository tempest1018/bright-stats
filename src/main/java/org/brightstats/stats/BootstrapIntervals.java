package org.brightstats.stats;

import java.util.*;
import org.apache.commons.math3.distribution.NormalDistribution;

/** Percentile, bootstrap-t and jackknife-accelerated BCa intervals. */
public final class BootstrapIntervals {
    private BootstrapIntervals() {}
    public enum Method { PERCENTILE, STUDENTIZED, BCA }
    public record Interval(double lower,double upper,double biasCorrection,double acceleration,
                           int usableStudentizedDraws,List<String> warnings) {
        public Interval { warnings=List.copyOf(warnings); }
    }
    public static Interval biasCorrected(double point,double[] draws,double confidence){
        Inference.confidence(confidence);
        if(!Double.isFinite(point)||draws.length<2||!Arrays.stream(draws).allMatch(Double::isFinite))throw new IllegalArgumentException("Finite point and bootstrap draws required.");
        double rank=0;for(double value:draws){if(value<point)rank++;else if(value==point)rank+=.5;}
        double percentile=rank/draws.length;
        if(percentile<=0||percentile>=1)return new Interval(Double.NaN,Double.NaN,Double.NaN,0,0,List.of("Bias-corrected interval unavailable: empirical bias correction is infinite."));
        var normal=new NormalDistribution();double z0=normal.inverseCumulativeProbability(percentile),alpha=(1-confidence)/2;
        double[] sorted=draws.clone();Arrays.sort(sorted);
        return new Interval(Inference.quantile(sorted,normal.cumulativeProbability(2*z0+normal.inverseCumulativeProbability(alpha))),
                Inference.quantile(sorted,normal.cumulativeProbability(2*z0+normal.inverseCumulativeProbability(1-alpha))),z0,0,0,List.of());
    }
    public static Interval calculate(double point,double[] draws,double confidence,Method method,
                                     double pointError,double[] drawErrors,double[] jackknife) {
        Inference.confidence(confidence);Objects.requireNonNull(method);
        if(!Double.isFinite(point)||draws.length<2||!Arrays.stream(draws).allMatch(Double::isFinite))
            throw new IllegalArgumentException("Finite point estimate and bootstrap distribution required.");
        double alpha=(1-confidence)/2;double[] sorted=draws.clone();Arrays.sort(sorted);
        List<String> warnings=new ArrayList<>();double low=alpha,high=1-alpha,z0=Double.NaN,acceleration=Double.NaN;
        int studentized=0;
        if(method==Method.STUDENTIZED) {
            if(drawErrors==null||drawErrors.length!=draws.length)throw new IllegalArgumentException("Each bootstrap-t draw requires its own standard error.");
            double[] pivots=new double[draws.length];
            for(int i=0;i<draws.length;i++)if(Double.isFinite(drawErrors[i])&&drawErrors[i]>0){double value=(draws[i]-point)/drawErrors[i];if(Double.isFinite(value))pivots[studentized++]=value;}
            if(studentized<Math.max(30,(int)Math.ceil(draws.length*.8))||!Double.isFinite(pointError)||pointError<=0) {
                warnings.add("Bootstrap-t interval unavailable: original or replicate standard errors are undefined/zero, or fewer than 80% and 30 usable pivots remain.");
                return new Interval(Double.NaN,Double.NaN,z0,acceleration,studentized,warnings);
            }
            if(studentized<draws.length)warnings.add((draws.length-studentized)+" invalid studentized pivots excluded from the interval only.");
            pivots=Arrays.copyOf(pivots,studentized);Arrays.sort(pivots);
            return new Interval(point-Inference.quantile(pivots,high)*pointError,
                    point-Inference.quantile(pivots,low)*pointError,z0,acceleration,studentized,warnings);
        }
        if(method==Method.BCA) {
            if(jackknife==null||jackknife.length<3||!Arrays.stream(jackknife).allMatch(Double::isFinite)) {
                warnings.add("BCa interval unavailable: a complete finite leave-one-out jackknife is required.");
                return new Interval(Double.NaN,Double.NaN,z0,acceleration,0,warnings);
            }
            double rank=0,mean=0;for(double value:draws){if(value<point)rank++;else if(value==point)rank+=.5;}
            double percentile=rank/draws.length;
            if(percentile<=0||percentile>=1){warnings.add("BCa interval unavailable: bias correction is infinite for this empirical distribution.");return new Interval(Double.NaN,Double.NaN,z0,acceleration,0,warnings);}
            NormalDistribution normal=new NormalDistribution();z0=normal.inverseCumulativeProbability(percentile);
            for(int i=0;i<jackknife.length;i++)mean+=(jackknife[i]-mean)/(i+1);
            double squares=0,cubes=0;for(double value:jackknife){double influence=mean-value;squares+=influence*influence;cubes+=influence*influence*influence;}
            if(!(squares>0)||!Double.isFinite(squares)||!Double.isFinite(cubes)){warnings.add("BCa interval unavailable: jackknife acceleration is undefined.");return new Interval(Double.NaN,Double.NaN,z0,acceleration,0,warnings);}
            acceleration=cubes/(6*Math.pow(squares,1.5));double zLow=z0+normal.inverseCumulativeProbability(alpha),zHigh=z0+normal.inverseCumulativeProbability(1-alpha);
            if(1-acceleration*zLow<=0||1-acceleration*zHigh<=0){warnings.add("BCa interval unavailable: adjusted probability crosses an acceleration singularity.");return new Interval(Double.NaN,Double.NaN,z0,acceleration,0,warnings);}
            low=normal.cumulativeProbability(z0+zLow/(1-acceleration*zLow));high=normal.cumulativeProbability(z0+zHigh/(1-acceleration*zHigh));
            if(!Double.isFinite(low)||!Double.isFinite(high)||low>high){warnings.add("BCa adjusted probabilities are inadmissible.");return new Interval(Double.NaN,Double.NaN,z0,acceleration,0,warnings);}
        }
        return new Interval(Inference.quantile(sorted,low),Inference.quantile(sorted,high),z0,acceleration,studentized,warnings);
    }
}
