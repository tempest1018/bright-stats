package org.brightstats.stats;

import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/** Shared fixed-attempt case-resampling inference for independently fitted estimators. */
final class Resampling {
    private Resampling(){}
    @FunctionalInterface interface Fitter {double[] fit(double[][] sample,int[] originalRows);}
    static Bootstrap.Result run(double[][] data,List<Bootstrap.Parameter> parameters,double[] point,
                                Bootstrap.Options options,Fitter fitter,IntConsumer progress,String estimator,String signHandling){
        Matrices.width(data,3);int count=parameters.size();if(count==0||point.length!=count||!Arrays.stream(point).allMatch(Double::isFinite))throw new IllegalArgumentException("No finite bootstrap target vector is available.");
        if((long)count*options.samples()>20000000L)throw new IllegalArgumentException("Too many bootstrap estimates; reduce sample count or model size.");
        boolean reference=options.convention()==RegressionBootstrap.Convention.REFERENCE_4_1_2_1;Random random=new Random(options.seed());long[] seeds=reference?ReferenceResampling.seeds(options.seed(),options.samples()):null;
        List<double[]> accepted=new ArrayList<>();Map<String,Integer> failures=new LinkedHashMap<>();
        for(int attempt=0;attempt<options.samples();attempt++){
            Matrices.interrupted();int[] rows=reference?ReferenceResampling.rows(seeds[attempt],data.length):Bootstrap.draw(random,data.length);
            try{double[] values=fitter.fit(select(data,rows),rows);if(values.length!=count||!Arrays.stream(values).allMatch(Double::isFinite))throw new IllegalArgumentException("Non-finite or inconsistent bootstrap estimates");accepted.add(values.clone());}
            catch(IllegalArgumentException|ArithmeticException problem){failures.merge(problem.getMessage()==null?problem.getClass().getSimpleName():problem.getMessage(),1,Integer::sum);}
            if(progress!=null)progress.accept(attempt+1);
        }
        int successful=accepted.size(),failed=options.samples()-successful;if(successful<Math.max(30,(int)Math.ceil(options.samples()*.8)))throw new IllegalArgumentException("Too few usable bootstrap samples: "+successful+"/"+options.samples()+". Failures: "+failures+". No inference was produced.");
        List<String> warnings=new ArrayList<>();double[][] jackknife=null;
        if(!reference&&options.interval()==BootstrapIntervals.Method.BCA){
            if((long)data.length*count>20000000L)throw new IllegalArgumentException("Too many jackknife estimates.");jackknife=new double[data.length][count];int bad=0;
            for(int omit=0;omit<data.length;omit++){Matrices.interrupted();Arrays.fill(jackknife[omit],Double.NaN);int[] rows=new int[data.length-1];for(int i=0,target=0;i<data.length;i++)if(i!=omit)rows[target++]=i;
                try{double[] values=fitter.fit(select(data,rows),rows);if(values.length!=count||!Arrays.stream(values).allMatch(Double::isFinite))throw new IllegalArgumentException("Invalid jackknife estimates");jackknife[omit]=values;}
                catch(IllegalArgumentException|ArithmeticException problem){bad++;}
            }
            if(bad>0)warnings.add(bad+" jackknife fits failed; affected BCa intervals are unavailable, not silently replaced.");
        }
        List<Bootstrap.Estimate> estimates=new ArrayList<>();
        for(int j=0;j<count;j++){
            Matrices.interrupted();double[] distribution=new double[successful];double mean=0,sum=0;for(int i=0;i<successful;i++){double value=accepted.get(i)[j],delta=value-mean;mean+=delta/(i+1);sum+=delta*(value-mean);distribution[i]=value;}
            double se=Math.sqrt(Math.max(0,sum/(successful-1))),statistic=Math.abs(Inference.ratio(point[j],se)),pValue;BootstrapIntervals.Interval interval,corrected;
            if(!Double.isFinite(mean)||!Double.isFinite(se))throw new IllegalArgumentException("Bootstrap moments exceed the finite numerical range.");
            if(reference){var summary=ReferenceBootstrapInference.evaluate(point[j],distribution,options.confidence(),options.interval(),options.twoSided());mean=summary.mean();se=summary.standardError();statistic=summary.statistic();pValue=summary.pValue();interval=summary.interval();corrected=summary.correctedInterval();}
            else{double[] omitted=null;if(jackknife!=null){omitted=new double[data.length];for(int i=0;i<data.length;i++)omitted[i]=jackknife[i][j];}interval=BootstrapIntervals.calculate(point[j],distribution,options.confidence(),options.interval(),Double.NaN,null,omitted);corrected=BootstrapIntervals.biasCorrected(point[j],distribution,options.confidence());pValue=Inference.normalTwoSided(statistic);}
            estimates.add(new Bootstrap.Estimate(parameters.get(j),point[j],mean,se,statistic,pValue,interval.lower(),interval.upper(),interval,corrected));
        }
        if(failed>0)warnings.add(failed+" bootstrap fits failed and were excluded without replacement; inspect failure reasons and conditional coverage.");
        if(options.samples()<1000)warnings.add("Fewer than 1000 bootstrap attempts; use a larger run for substantive inference.");
        if(estimates.stream().anyMatch(v->v.standardError()==0))warnings.add("Some bootstrap distributions have zero variance; undefined ratios are retained as unavailable.");
        warnings.add(reference?"Reference inference convention matches separately probed interval/summary routines; it does not establish full native estimator parity.":"Standard inference uses sample SD, normal-reference ratios and type-7 percentile or leave-one-out jackknife BCa intervals.");
        return new Bootstrap.Result(options,estimator+" IID case resampling; "+options.convention()+"; "+options.interval(),reference?"Reference two-stage Java seeds and bounded-integer/modulus rows":"java.util.Random nextInt(n), fixed attempt order",signHandling,successful,failed,failures,estimates,warnings,estimator);
    }
    static Bootstrap.Result suppressTests(Bootstrap.Result result,Predicate<Bootstrap.Parameter> predicate,String explanation){
        List<Bootstrap.Estimate> estimates=result.estimates().stream().map(v->predicate.test(v.parameter())?new Bootstrap.Estimate(v.parameter(),v.original(),v.mean(),v.standardError(),Double.NaN,Double.NaN,v.lower(),v.upper(),v.interval(),v.correctedInterval()):v).toList();List<String> warnings=new ArrayList<>(result.warnings());warnings.add(explanation);
        return new Bootstrap.Result(result.options(),result.method(),result.randomGenerator(),result.signHandling(),result.successful(),result.failed(),result.failures(),estimates,warnings,result.estimator());
    }
    private static double[][] select(double[][] data,int[] rows){double[][] selected=new double[rows.length][];for(int i=0;i<rows.length;i++)selected[i]=data[rows[i]].clone();return selected;}
}
