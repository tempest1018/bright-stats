package org.brightstats.stats;

import java.util.*;
import java.util.function.IntConsumer;

/** Fixed-attempt paired-row regression bootstrap; no replacement of failed fits. */
public final class RegressionBootstrap {
    private RegressionBootstrap() {}
    public enum Convention { STANDARD, REFERENCE_4_1_2_1;
        @Override public String toString(){return this==STANDARD?"Standard inference":"SmartPLS 4.1.2.1 reference";}
    }
    public record Options(int samples,long seed,double confidence,BootstrapIntervals.Method interval,
                          RobustRegression.Covariance covariance,boolean retainDraws,Convention convention,boolean twoSided) {
        public Options {new Bootstrap.Options(samples,seed,confidence);Objects.requireNonNull(interval);Objects.requireNonNull(covariance);Objects.requireNonNull(convention);if(convention==Convention.STANDARD&&!twoSided)throw new IllegalArgumentException("The standard convention currently supports two-sided tests; one-tailed reference behavior requires reference convention.");}
        public Options(int samples,long seed,double confidence,BootstrapIntervals.Method interval,RobustRegression.Covariance covariance){this(samples,seed,confidence,interval,covariance,false);}
        public Options(int samples,long seed,double confidence,BootstrapIntervals.Method interval,RobustRegression.Covariance covariance,boolean retainDraws){this(samples,seed,confidence,interval,covariance,retainDraws,Convention.STANDARD,true);}
        public static Options defaults(){return new Options(5000,ReferenceResampling.DEFAULT_SEED,.95,BootstrapIntervals.Method.PERCENTILE,RobustRegression.Covariance.CLASSICAL,false,Convention.REFERENCE_4_1_2_1,true);}
    }
    public record Estimate(String family,int coefficient,double original,double mean,double standardError,
                           double statistic,double pValue,BootstrapIntervals.Interval interval,BootstrapIntervals.Interval biasCorrectedInterval,Histogram histogram,double[] distribution) {
        public Estimate { distribution=distribution.clone(); }
        @Override public double[] distribution(){return distribution.clone();}
    }
    public record Histogram(double[] breaks,int[] counts){public Histogram{breaks=breaks.clone();counts=counts.clone();}@Override public double[] breaks(){return breaks.clone();}@Override public int[] counts(){return counts.clone();}}
    public record Result(Options options,String method,int successful,int failed,Map<String,Integer> failures,
                         List<Estimate> estimates,List<String> warnings) {
        public Result {failures=Collections.unmodifiableMap(new LinkedHashMap<>(failures));estimates=List.copyOf(estimates);warnings=List.copyOf(warnings);}
    }
    private record Values(double[] values,double[] errors) {}
    public static Result fit(double[][] x,double[] y,Options options,IntConsumer progress) {
        Objects.requireNonNull(options);var original=values(x,y,options.covariance());
        if(!Arrays.stream(original.values()).allMatch(Double::isFinite))throw new IllegalArgumentException("Original regression estimates exceed the finite numeric range.");
        int count=original.values().length,n=y.length;
        if((long)options.samples()*count>20000000L)throw new IllegalArgumentException("Too many stored bootstrap results; reduce samples or predictors.");
        if(options.retainDraws()&&(long)options.samples()*count>1000000L)throw new IllegalArgumentException("Individual draw retention is limited to one million values; reduce samples/predictors or disable draw retention.");
        Random random=new Random(options.seed());List<Values> accepted=new ArrayList<>();Map<String,Integer> failures=new LinkedHashMap<>();
        boolean reference=options.convention()==Convention.REFERENCE_4_1_2_1;
        long[] seeds=reference?ReferenceResampling.seeds(options.seed(),options.samples()):null;
        for(int attempt=0;attempt<options.samples();attempt++) {
            Matrices.interrupted();int[] rows=reference?ReferenceResampling.rows(seeds[attempt],n):Bootstrap.draw(random,n);double[][] sampleX=new double[n][];double[] sampleY=new double[n];
            for(int i=0;i<n;i++){sampleX[i]=x[rows[i]].clone();sampleY[i]=y[rows[i]];}
            try{var sample=values(sampleX,sampleY,options.covariance());if(!Arrays.stream(sample.values()).allMatch(Double::isFinite))throw new IllegalArgumentException("Non-finite bootstrap estimates");accepted.add(sample);}
            catch(IllegalArgumentException|ArithmeticException error){failures.merge(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage(),1,Integer::sum);}
            if(progress!=null)progress.accept(attempt+1);
        }
        int successful=accepted.size(),failed=options.samples()-successful;
        if(successful<Math.max(30,(int)Math.ceil(options.samples()*.8)))throw new IllegalArgumentException("Too few usable regression bootstrap samples: "+successful+"/"+options.samples()+". Failures: "+failures);
        double[][] jackknife=null;List<String> warnings=new ArrayList<>();
        if(!reference&&options.interval()==BootstrapIntervals.Method.BCA){jackknife=new double[n][count];for(double[] row:jackknife)Arrays.fill(row,Double.NaN);
            for(int omit=0;omit<n;omit++){Matrices.interrupted();double[][] leaveX=new double[n-1][];double[] leaveY=new double[n-1];for(int i=0,target=0;i<n;i++)if(i!=omit){leaveX[target]=x[i].clone();leaveY[target++]=y[i];}try{jackknife[omit]=values(leaveX,leaveY,options.covariance()).values();}catch(IllegalArgumentException|ArithmeticException error){warnings.add("Jackknife row "+(omit+1)+" failed: "+error.getMessage());}}
        }
        List<Estimate> estimates=new ArrayList<>();int k=x[0].length+1;
        for(int j=0;j<count;j++) {
            double mean=0,sum=0;double[] draws=new double[successful],errors=new double[successful];for(int b=0;b<successful;b++){draws[b]=accepted.get(b).values()[j];errors[b]=accepted.get(b).errors()[j];double delta=draws[b]-mean;mean+=delta/(b+1);sum+=delta*(draws[b]-mean);}
            double se=Math.sqrt(Math.max(0,sum/(successful-1))),statistic=Math.abs(Inference.ratio(original.values()[j],se));
            if(!Double.isFinite(mean)||!Double.isFinite(se))throw new IllegalArgumentException("Regression bootstrap moments exceed the finite numeric range.");
            double[] omitted=null;if(jackknife!=null){omitted=new double[n];for(int i=0;i<n;i++)omitted[i]=jackknife[i][j];}
            // Standard bootstrap-t requires analytic errors; reference intervals follow a separately verified convention.
            BootstrapIntervals.Interval interval,corrected;double pValue;
            if(reference){var inference=ReferenceBootstrapInference.evaluate(original.values()[j],draws,options.confidence(),options.interval(),options.twoSided());mean=inference.mean();se=inference.standardError();statistic=inference.statistic();pValue=inference.pValue();interval=inference.interval();corrected=inference.correctedInterval();}
            else{interval=BootstrapIntervals.calculate(original.values()[j],draws,options.confidence(),options.interval(),original.errors()[j],errors,omitted);corrected=BootstrapIntervals.biasCorrected(original.values()[j],draws,options.confidence());pValue=Inference.normalTwoSided(statistic);}
            String family=j<k?"Coefficient":j<2*k?"Standardized coefficient":j==2*k?"R-square":j==2*k+1?"Adjusted R-square":"VIF";
            int coefficient=j<k?j:j<2*k?j-k:j>=2*k+2?j-(2*k+2)+1:-1;estimates.add(new Estimate(family,coefficient,original.values()[j],mean,se,statistic,pValue,interval,corrected,histogram(draws),options.retainDraws()?draws:new double[0]));
        }
        if(failed>0)warnings.add(failed+" failed fits were excluded without replacement; inspect failure reasons.");
        if(options.samples()<1000)warnings.add("Fewer than 1000 attempts; use a larger run for substantive inference.");
        warnings.add(reference?"Reference convention: t-reference p-values with bootstrap count; population bootstrap SD. Selected interval behavior follows the verified 4.1.2.1 routines.":"Standard convention: two-sided normal-reference p-values; sample bootstrap SD; bootstrap-t / jackknife BCa where selected.");
        warnings.add("Nonzero R-square and VIF ratios are not omnibus or collinearity significance tests.");
        if(estimates.stream().anyMatch(e->!e.interval().warnings().isEmpty()))warnings.add("Some interval rows have warnings; inspect each numerical interval record.");
        return new Result(options,(reference?"Reference two-stage case seeds/modulus draws; population SD; t-reference; discrete intervals; ":"IID paired rows; Java LCG; sample SD (B−1); type-7 quantiles; ")+options.interval(),successful,failed,failures,estimates,warnings);
    }
    private static Histogram histogram(double[] draws){double min=Arrays.stream(draws).min().orElseThrow(),max=Arrays.stream(draws).max().orElseThrow();if(min==max)return new Histogram(new double[]{min,max},new int[]{draws.length});double[] breaks=new double[21];int[] counts=new int[20];for(int i=0;i<=20;i++)breaks[i]=min+(max-min)*i/20;for(double value:draws){int index=Math.max(0,Math.min(19,(int)((value-min)/(max-min)*20)));counts[index]++;}return new Histogram(breaks,counts);}
    private static Values values(double[][] x,double[] y,RobustRegression.Covariance covariance) {
        var fit=RobustRegression.fit(x,y,covariance,.95);double[] beta=fit.ordinary().coefficients();int k=beta.length,n=y.length;double[] values=new double[3*k+1],errors=new double[values.length];Arrays.fill(errors,Double.NaN);
        double meanY=0,sumY=0;for(int i=0;i<n;i++){double delta=y[i]-meanY;meanY+=delta/(i+1);sumY+=delta*(y[i]-meanY);}
        if(!(sumY>0))throw new IllegalArgumentException("Standardized regression requires nonconstant response.");
        for(int j=0;j<k;j++){values[j]=beta[j];errors[j]=fit.terms().get(j).standardError();double factor=0;if(j>0){double mean=0,sum=0;for(int i=0;i<n;i++){double delta=x[i][j-1]-mean;mean+=delta/(i+1);sum+=delta*(x[i][j-1]-mean);}factor=Math.sqrt(sum/sumY);}values[k+j]=j==0?0:beta[j]*factor;errors[k+j]=j==0?0:errors[j]*factor;}
        values[2*k]=fit.ordinary().rSquared();values[2*k+1]=fit.ordinary().adjustedRSquared();
        for(int j=0;j<k-1;j++){double[][] auxiliary=new double[n][k-2];double[] target=new double[n];for(int i=0;i<n;i++){target[i]=x[i][j];for(int column=0,destination=0;column<k-1;column++)if(column!=j)auxiliary[i][destination++]=x[i][column];}double fraction=1-OlsRegression.fit(auxiliary,target).rSquared();values[2*k+2+j]=fraction>0?1/fraction:Double.POSITIVE_INFINITY;}
        return new Values(values,errors);
    }
}
