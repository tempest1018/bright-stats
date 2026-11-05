package org.brightstats.stats;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.IntConsumer;

/** Fixed-attempt, iid case bootstrap for supported linear PLS-SEM models. */
public final class Bootstrap {
    private Bootstrap() {}
    public record Options(int samples,long seed,double confidence,BootstrapIntervals.Method interval,
                          RegressionBootstrap.Convention convention,boolean twoSided) {
        public Options {
            if (samples < 30 || samples > 100000) throw new IllegalArgumentException("Bootstrap samples must be between 30 and 100000.");
            Inference.confidence(confidence);
            Objects.requireNonNull(interval);Objects.requireNonNull(convention);
            if(convention==RegressionBootstrap.Convention.STANDARD&&(!twoSided||interval==BootstrapIntervals.Method.STUDENTIZED))throw new IllegalArgumentException("Standard case-bootstrap intervals support two-sided percentile or jackknife BCa; reference convention supplies the reference studentized/one-tailed methods.");
        }
        public Options(int samples,long seed,double confidence){this(samples,seed,confidence,BootstrapIntervals.Method.PERCENTILE,RegressionBootstrap.Convention.STANDARD,true);}
        public static Options defaults() { return new Options(5000,ReferenceResampling.DEFAULT_SEED,.95,BootstrapIntervals.Method.PERCENTILE,RegressionBootstrap.Convention.REFERENCE_4_1_2_1,true); }
    }
    public record Parameter(String family, int source, int target, int column, String label) {}
    public record Estimate(Parameter parameter, double original, double mean, double standardError,
                           double statistic,double pValue,double lower,double upper,
                           BootstrapIntervals.Interval interval,BootstrapIntervals.Interval correctedInterval) {}
    public record Result(Options options, String method, String randomGenerator, String signHandling,
                         int successful, int failed, Map<String,Integer> failures,
                         List<Estimate> estimates, List<String> warnings,String estimator) {
        public Result { failures = Collections.unmodifiableMap(new LinkedHashMap<>(failures)); estimates = List.copyOf(estimates); warnings = List.copyOf(warnings); }
    }
    public static int[] draw(Random random, int n) {
        int[] rows = new int[n]; for (int i=0;i<n;i++) rows[i]=random.nextInt(n); return rows;
    }
    public static Result fit(double[][] observations, List<PlsSem.Block> blocks, List<PlsSem.Edge> edges,
                             PlsSem.Options modelOptions, Options options, IntConsumer progress) {
        return fit(observations,blocks,edges,modelOptions,options,progress,false);
    }
    public static Result fit(double[][] observations,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,
                             PlsSem.Options modelOptions,Options options,IntConsumer progress,boolean consistent) {
        var original = consistent?ConsistentPls.fit(observations,blocks,edges,modelOptions).inferenceFit():PlsSem.fit(observations,blocks,edges,modelOptions);
        if (!original.converged()) throw new IllegalArgumentException("Original PLS model did not converge; bootstrap inference is unavailable.");
        List<Parameter> parameters = parameters(blocks,edges);
        if ((long)parameters.size()*options.samples()>20000000L) throw new IllegalArgumentException("Too many bootstrap estimates; reduce the sample count or model size.");
        double[] point = values(original,blocks,parameters,null);
        if(!Arrays.stream(point).allMatch(Double::isFinite))throw new IllegalArgumentException("Original PLS estimates exceed the finite numeric range.");
        List<double[]> accepted = new ArrayList<>(); Map<String,Integer> failures = new LinkedHashMap<>();
        Random random = new Random(options.seed()); double[][] referenceScores = original.scores();
        boolean reference=options.convention()==RegressionBootstrap.Convention.REFERENCE_4_1_2_1;
        long[] seeds=reference?ReferenceResampling.seeds(options.seed(),options.samples()):null;
        for (int attempt=0;attempt<options.samples();attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("Bootstrap cancelled.");
            int[] rows = reference?ReferenceResampling.rows(seeds[attempt],observations.length):draw(random,observations.length); double[][] sample = new double[rows.length][];
            for (int i=0;i<rows.length;i++) sample[i]=observations[rows[i]].clone();
            try {
                var model = consistent?ConsistentPls.fit(sample,blocks,edges,modelOptions).inferenceFit():PlsSem.fit(sample,blocks,edges,modelOptions);
                if (!model.converged()) failures.merge("PLS did not converge",1,Integer::sum);
                else {
                    double[] signs = alignment(model.scores(),referenceScores,rows);
                    double[] vector = values(model,blocks,parameters,signs);
                    boolean finite = Arrays.stream(vector).allMatch(Double::isFinite);
                    if (!finite) failures.merge("Non-finite estimate",1,Integer::sum); else accepted.add(vector);
                }
            } catch (IllegalArgumentException | ArithmeticException error) {
                failures.merge(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage(),1,Integer::sum);
            }
            if (progress != null) progress.accept(attempt+1);
        }
        int successful = accepted.size(), failed = options.samples()-successful;
        if (successful < Math.max(30,(int)Math.ceil(options.samples()*.8)))
            throw new IllegalArgumentException("Too few usable bootstrap samples: " + successful + "/" + options.samples() + ". Failures: " + failures + ". No inference was produced.");
        List<String> warnings = new ArrayList<>();double[][] jackknife=null;
        if(!reference&&options.interval()==BootstrapIntervals.Method.BCA){
            if((long)observations.length*parameters.size()>20000000L)throw new IllegalArgumentException("Too many jackknife results; reduce data/model size or choose reference intervals.");
            jackknife=new double[observations.length][parameters.size()];int failedJackknifes=0;
            for(int omit=0;omit<observations.length;omit++){
                Matrices.interrupted();Arrays.fill(jackknife[omit],Double.NaN);int[] rows=new int[observations.length-1];double[][] selected=new double[rows.length][];
                for(int i=0,target=0;i<observations.length;i++)if(i!=omit){rows[target]=i;selected[target++]=observations[i].clone();}
                try{var model=consistent?ConsistentPls.fit(selected,blocks,edges,modelOptions).inferenceFit():PlsSem.fit(selected,blocks,edges,modelOptions);if(!model.converged())throw new IllegalArgumentException("Jackknife did not converge");jackknife[omit]=values(model,blocks,parameters,alignment(model.scores(),referenceScores,rows));}
                catch(IllegalArgumentException|ArithmeticException error){failedJackknifes++;}
            }
            if(failedJackknifes>0)warnings.add(failedJackknifes+" jackknife fits failed; affected BCa intervals are unavailable, not silently replaced.");
        }
        List<Estimate> estimates = new ArrayList<>();
        for (int k=0;k<parameters.size();k++) {
            double mean=0,sum=0; double[] distribution = new double[successful];
            for(int i=0;i<successful;i++) { double value=accepted.get(i)[k], delta=value-mean; mean+=delta/(i+1); sum+=delta*(value-mean); distribution[i]=value; }
            double se = Math.sqrt(Math.max(0,sum/(successful-1))), statistic=Math.abs(Inference.ratio(point[k],se));
            if(!Double.isFinite(mean) || !Double.isFinite(se))throw new IllegalArgumentException("Bootstrap moments exceed the finite numeric range; no inference was produced.");
            BootstrapIntervals.Interval interval,corrected;double pValue;
            if(reference){var inference=ReferenceBootstrapInference.evaluate(point[k],distribution,options.confidence(),options.interval(),options.twoSided());mean=inference.mean();se=inference.standardError();statistic=inference.statistic();pValue=inference.pValue();interval=inference.interval();corrected=inference.correctedInterval();}
            else{double[] omitted=null;if(jackknife!=null){omitted=new double[jackknife.length];for(int i=0;i<omitted.length;i++)omitted[i]=jackknife[i][k];}interval=BootstrapIntervals.calculate(point[k],distribution,options.confidence(),options.interval(),Double.NaN,null,omitted);corrected=BootstrapIntervals.biasCorrected(point[k],distribution,options.confidence());pValue=Inference.normalTwoSided(statistic);}
            estimates.add(new Estimate(parameters.get(k),point[k],mean,se,statistic,pValue,interval.lower(),interval.upper(),interval,corrected));
        }
        if (failed>0) warnings.add(failed+" bootstrap samples failed and were excluded without replacement; inference is conditional on the "+successful+" successful fits. Inspect failure reasons.");
        if (options.samples()<1000) warnings.add("Fewer than 1000 bootstrap attempts: use a larger run for substantive inference.");
        if (estimates.stream().anyMatch(e -> e.standardError()==0)) warnings.add("Some bootstrap distributions have zero variance; zero/zero statistics are undefined and nonzero/zero ratios are infinite.");
        warnings.add(reference?"Reference inference: population bootstrap SD; t-reference p-values with bootstrap count; selected corrected bounds are reported separately.":"Standard inference: sample bootstrap SD; normal-reference tests; type-7 percentile or jackknife BCa bounds.");
        return new Result(options,reference?"Reference 4.1.2.1 inference; "+options.interval():options.interval()==BootstrapIntervals.Method.PERCENTILE?"IID case resampling; sample SD (B-1); percentile CI (linear/type-7); two-sided normal-reference p-values":"IID case resampling; sample SD (B-1); jackknife BCa; two-sided normal-reference p-values",
                reference?"Reference two-stage Java seeds and bounded-integer/modulus rows":"java.util.Random nextInt(n), version 1; fixed attempt order", "Construct scores aligned to original scores at the sampled row indices",
                successful,failed,failures,estimates,warnings,consistent?"PLSc":"PLS");
    }
    static double[] alignment(double[][] scores,double[][] reference,int[] rows) {
        double[] signs=new double[scores[0].length]; Arrays.fill(signs,1);
        for(int j=0;j<signs.length;j++) {
            double mean=0; for(int i=0;i<rows.length;i++) mean+=(reference[rows[i]][j]-mean)/(i+1);
            double dot=0; for(int i=0;i<rows.length;i++) dot+=scores[i][j]*(reference[rows[i]][j]-mean);
            if (!Double.isFinite(dot) || Math.abs(dot)<1e-12) throw new IllegalArgumentException("Ambiguous construct sign alignment");
            if(dot<0)signs[j]=-1;
        }
        return signs;
    }
    static List<Parameter> parameters(List<PlsSem.Block> blocks,List<PlsSem.Edge> edges) {
        List<Parameter> result=new ArrayList<>(); int count=blocks.size(); boolean[][] reach=new boolean[count][count], indirect=new boolean[count][count];
        for(var edge:edges) { reach[edge.source()][edge.target()]=true; result.add(new Parameter("Path",edge.source(),edge.target(),-1,blocks.get(edge.source()).name()+" → "+blocks.get(edge.target()).name())); }
        for(int j=0;j<count;j++) if(blocks.get(j).columns().length>1) for(int column:blocks.get(j).columns()) {
            result.add(new Parameter("Weight",j,-1,column,blocks.get(j).name()+" / column "+(column+1)));
            result.add(new Parameter("Loading",j,-1,column,blocks.get(j).name()+" / column "+(column+1)));
        }
        boolean[][] power=new boolean[count][count]; for(int i=0;i<count;i++)power[i]=reach[i].clone();
        for(int length=2;length<count;length++) {
            boolean[][] next=new boolean[count][count];
            for(int i=0;i<count;i++)for(int j=0;j<count;j++)for(var edge:edges) if(edge.target()==j && power[i][edge.source()])next[i][j]=true;
            for(int i=0;i<count;i++)for(int j=0;j<count;j++) { indirect[i][j]|=next[i][j]; reach[i][j]|=next[i][j]; } power=next;
        }
        for(int i=0;i<count;i++)for(int j=0;j<count;j++) {
            String label=blocks.get(i).name()+" → "+blocks.get(j).name();
            if(reach[i][j])result.add(new Parameter("Total effect",i,j,-1,label));
            if(indirect[i][j])result.add(new Parameter("Total indirect effect",i,j,-1,label));
        }
        return result;
    }
    static double[] values(PlsSem.Fit fit,List<PlsSem.Block> blocks,List<Parameter> parameters,double[] suppliedSigns) {
        int count=blocks.size(); double[] signs=suppliedSigns==null?new double[count]:suppliedSigns;
        if(suppliedSigns==null)Arrays.fill(signs,1);
        double[][] paths=fit.pathCoefficients(),weights=fit.weights(),loadings=fit.loadings();
        boolean[][] structural=new boolean[count][count];
        for(var parameter:parameters)if(parameter.family().equals("Path"))structural[parameter.source()][parameter.target()]=true;
        double[][] total=new double[count][count],power=new double[count][count];
        for(int i=0;i<count;i++)for(int j=0;j<count;j++) { double value=structural[i][j]?paths[i][j]:0; if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite structural coefficient"); paths[i][j]=value*signs[i]*signs[j]; total[i][j]=paths[i][j]; power[i][j]=paths[i][j]; }
        for(int length=2;length<count;length++) {
            double[][] next=new double[count][count];
            for(int i=0;i<count;i++)for(int j=0;j<count;j++)for(int k=0;k<count;k++)next[i][j]+=power[i][k]*paths[k][j];
            for(int i=0;i<count;i++)for(int j=0;j<count;j++)total[i][j]+=next[i][j]; power=next;
        }
        double[] result=new double[parameters.size()];
        for(int k=0;k<result.length;k++) {
            var term=parameters.get(k); int source=term.source(),target=term.target();
            if(term.column()>=0) {
                int index=0; int[] columns=blocks.get(source).columns(); while(columns[index]!=term.column())index++;
                result[k]=(term.family().equals("Weight")?weights[source][index]:loadings[source][index])*signs[source];
            } else result[k]=switch(term.family()) { case "Path" -> paths[source][target]; case "Total effect" -> total[source][target]; default -> total[source][target]-paths[source][target]; };
        }
        return result;
    }
}
