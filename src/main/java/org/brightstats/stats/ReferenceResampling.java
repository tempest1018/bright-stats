package org.brightstats.stats;

import java.util.Random;

/** Reference-compatible two-stage case seeds and bounded-integer/modulus row draws. */
public final class ReferenceResampling {
    private ReferenceResampling() {}
    public static final long DEFAULT_SEED=295948L;
    public static long[] seeds(long baseSeed,int samples){if(samples<1)throw new IllegalArgumentException("Positive sample count required.");Random generator=new Random(baseSeed+samples);long[] result=new long[samples];for(int i=0;i<samples;i++)result[i]=generator.nextLong();return result;}
    public static int[] rows(long sampleSeed,int n){if(n<1)throw new IllegalArgumentException("Positive row count required.");Random generator=new Random(sampleSeed);int[] result=new int[n];for(int i=0;i<n;i++)result[i]=generator.nextInt(Integer.MAX_VALUE)%n;return result;}
    /** Same encounter order as Random.ints(0,n).distinct().limit(n), used by reference permutation. */
    public static int[] permutation(long sampleSeed,int n){if(n<1)throw new IllegalArgumentException("Positive permutation size required.");return new Random(sampleSeed).ints(0,n).peek(value->Matrices.interrupted()).distinct().limit(n).toArray();}
}
