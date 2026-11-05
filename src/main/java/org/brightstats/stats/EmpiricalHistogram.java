package org.brightstats.stats;

import java.util.Arrays;

public record EmpiricalHistogram(double[] breaks,int[] counts) {
    public EmpiricalHistogram{breaks=breaks.clone();counts=counts.clone();if(breaks.length!=counts.length+1||counts.length<1||!Arrays.stream(breaks).allMatch(Double::isFinite)||Arrays.stream(counts).anyMatch(v->v<0))throw new IllegalArgumentException("Invalid empirical histogram.");}
    @Override public double[] breaks(){return breaks.clone();}@Override public int[] counts(){return counts.clone();}
    public static EmpiricalHistogram of(double[] values){if(values.length==0||!Arrays.stream(values).allMatch(Double::isFinite))throw new IllegalArgumentException("Histogram needs finite observations.");double low=Arrays.stream(values).min().orElseThrow(),high=Arrays.stream(values).max().orElseThrow();if(low==high)return new EmpiricalHistogram(new double[]{low,high},new int[]{values.length});double width=high-low;if(!Double.isFinite(width))throw new IllegalArgumentException("Histogram range exceeds finite precision.");double[] breaks=new double[21];int[] counts=new int[20];for(int i=0;i<=20;i++)breaks[i]=low+width*(i/20.0);for(double value:values)counts[Math.max(0,Math.min(19,(int)((value-low)/width*20)))]++;return new EmpiricalHistogram(breaks,counts);}
}
