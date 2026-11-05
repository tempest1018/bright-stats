package org.brightstats.stats;

import java.util.*;

/** Explicit Monte Carlo convention, including reference order statistics and zero-difference behavior. */
final class PermutationInference {
    private PermutationInference(){}
    record Summary(double original,double mean,double standardDeviation,double lower,double upper,double pValue,boolean referenceZeroDifference){}
    static Summary evaluate(double original,double[] draws,double alpha,boolean twoSided,boolean lowerTail,boolean reference){
        if(!Double.isFinite(original)||draws.length<1||!Arrays.stream(draws).allMatch(Double::isFinite))throw new IllegalArgumentException("Invalid permutation values.");double mean=0,sum=0;int exceed=0;for(int i=0;i<draws.length;i++){double value=draws[i],delta=value-mean;mean+=delta/(i+1);sum+=delta*(value-mean);if(reference){if(lowerTail?1-original>0&&1-original<=1-value:original>0&&(original<=value||twoSided&&-original>=value)||original<0&&(original>=value||twoSided&&-original<=value))exceed++;}else if(lowerTail?value<=original+1e-12:Math.abs(value)>=Math.abs(original)-1e-12)exceed++;}
        double[] sorted=draws.clone();Arrays.sort(sorted);double tail=lowerTail?alpha:alpha/(twoSided?2:1),low=reference?order(sorted,tail):Inference.quantile(sorted,tail),high=reference?order(sorted,1-tail):Inference.quantile(sorted,1-tail),p=reference?(double)exceed/draws.length:(exceed+1.0)/(draws.length+1);return new Summary(original,mean,Math.sqrt(Math.max(0,sum/(reference?draws.length:Math.max(1,draws.length-1)))),low,high,p,reference&&(lowerTail?original==1:original==0));
    }
    static double order(double[] sorted,double p){int rank=p<.5?(int)Math.ceil(sorted.length*p):p>.5?(int)Math.floor(sorted.length*p):(int)Math.round(sorted.length*p);return sorted[Math.max(0,Math.min(sorted.length-1,rank-1))];}
}
