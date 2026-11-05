package org.brightstats.stats;

import java.util.*;
import java.util.function.IntConsumer;

/** Outcome-label permutation: empirical necessity effect against an independence null. */
public final class NcaPermutation {
    private NcaPermutation(){}
    public record Options(int samples,long seed,double significance,RegressionBootstrap.Convention convention,boolean retainDraws){
        public Options{Objects.requireNonNull(convention);if(samples<30||samples>100000||!Double.isFinite(significance)||significance<=0||significance>=.5)throw new IllegalArgumentException("Invalid NCA permutation settings.");}
        public static Options defaults(){return new Options(5000,ReferenceResampling.DEFAULT_SEED,.05,RegressionBootstrap.Convention.REFERENCE_4_1_2_1,false);}
    }
    public record Histogram(double[] breaks,int[] counts){public Histogram{breaks=breaks.clone();counts=counts.clone();}@Override public double[] breaks(){return breaks.clone();}@Override public int[] counts(){return counts.clone();}}
    public record Test(String condition,String ceiling,double original,double criticalEffect,double pValue,
                       double nullMean,double nullStandardDeviation,Histogram histogram,double[] distribution){
        public Test{distribution=distribution==null?null:distribution.clone();}@Override public double[] distribution(){return distribution==null?null:distribution.clone();}
    }
    public record Result(Nca.Fit point,Options options,int successful,int failed,Map<String,Integer> failures,List<Test> tests,List<String> warnings,String method,String randomGenerator){public Result{failures=Collections.unmodifiableMap(new LinkedHashMap<>(failures));tests=List.copyOf(tests);warnings=List.copyOf(warnings);}}
    public static Result fit(double[][] predictors,double[] outcome,List<String> names,String outcomeName,Nca.Options settings,Options options,IntConsumer progress){
        var point=Nca.fit(predictors,outcome,names,outcomeName,settings);int n=outcome.length,p=names.size();if((long)options.samples()*p*2>20000000L||options.retainDraws()&&(long)options.samples()*p*2>1000000L)throw new IllegalArgumentException("NCA permutation storage is too large; reduce the model/sample count or disable retained draws.");
        double[][] columns=new double[p][n];for(int j=0;j<p;j++)for(int i=0;i<n;i++)columns[j][i]=predictors[i][j];boolean reference=options.convention()==RegressionBootstrap.Convention.REFERENCE_4_1_2_1;Random random=new Random(options.seed());long[] seeds=reference?ReferenceResampling.seeds(options.seed(),options.samples()):null;List<double[]> accepted=new ArrayList<>();Map<String,Integer> failures=new LinkedHashMap<>();
        for(int attempt=0;attempt<options.samples();attempt++){
            Matrices.interrupted();int[] rows=reference?ReferenceResampling.permutation(seeds[attempt],n):shuffle(random,n);double[] permuted=new double[n];for(int i=0;i<n;i++)permuted[i]=outcome[rows[i]];
            try{double[] values=new double[2*p];for(int j=0;j<p;j++){Matrices.interrupted();double[] effect=Nca.effects(columns[j],permuted,point.conditions().get(j).scope(),point.outcomeScope());values[j*2]=effect[0];values[j*2+1]=effect[1];}if(!Arrays.stream(values).allMatch(Double::isFinite))throw new IllegalArgumentException("NCA effect is not finite");accepted.add(values);}
            catch(IllegalArgumentException|ArithmeticException problem){failures.merge(problem.getMessage()==null?problem.getClass().getSimpleName():problem.getMessage(),1,Integer::sum);}if(progress!=null)progress.accept(attempt+1);
        }
        int successful=accepted.size(),failed=options.samples()-successful;if(successful<Math.max(30,(int)Math.ceil(options.samples()*.8)))throw new IllegalArgumentException("Too few usable NCA permutations: "+successful+"/"+options.samples()+"; "+failures);
        List<Test> tests=new ArrayList<>();for(int j=0;j<p;j++)for(int ceiling=0;ceiling<2;ceiling++){
            var condition=point.conditions().get(j);double original=ceiling==0?condition.ceFdh().effectSize():condition.crFdh().effectSize();double[] distribution=new double[successful];double mean=0,sum=0;int exceed=0;
            for(int i=0;i<successful;i++){double value=accepted.get(i)[j*2+ceiling],delta=value-mean;mean+=delta/(i+1);sum+=delta*(value-mean);distribution[i]=value;if(value>=(reference?original:original-1e-12))exceed++;}
            double[] sorted=distribution.clone();Arrays.sort(sorted);double critical=reference?sorted[Math.max(0,(int)Math.floor(successful*(1-options.significance()))-1)]:Inference.quantile(sorted,1-options.significance());
            double probability=reference?(Math.abs(original)<=1e-10?Double.NaN:(double)exceed/successful):(exceed+1.0)/(successful+1);
            tests.add(new Test(condition.name(),ceiling==0?"CE-FDH":"CR-FDH",original,critical,probability,mean,Math.sqrt(Math.max(0,sum/(successful-1))),histogram(distribution),options.retainDraws()?distribution:null));
        }
        List<String> warnings=new ArrayList<>();if(failed>0)warnings.add(failed+" permutation fits failed and were excluded without replacement; inspect conditional null coverage.");if(options.samples()<1000)warnings.add("Fewer than 1000 permutations; small Monte Carlo runs give coarse significance estimates.");if(reference)warnings.add("Reference p-values use exceedance/B and can be zero; effects near zero have unavailable reference p-values. Standard mode uses the (exceedance+1)/(B+1) Monte Carlo correction.");
        return new Result(point,options,successful,failed,failures,tests,warnings,reference?"One-tailed necessity permutation; reference exceedance and critical order-statistic conventions":"One-tailed necessity permutation; finite Monte Carlo p-value correction",reference?"Reference two-stage seeds; encounter-order distinct integer permutation":"java.util.Random Fisher-Yates permutation, version 1");
    }
    private static int[] shuffle(Random random,int size){int[] order=java.util.stream.IntStream.range(0,size).toArray();for(int i=size-1;i>0;i--){int selected=random.nextInt(i+1),value=order[i];order[i]=order[selected];order[selected]=value;}return order;}
    private static Histogram histogram(double[] values){double low=Arrays.stream(values).min().orElseThrow(),high=Arrays.stream(values).max().orElseThrow();if(low==high)return new Histogram(new double[]{low,high},new int[]{values.length});double[] breaks=new double[21];int[] counts=new int[20];for(int i=0;i<=20;i++)breaks[i]=low+(high-low)*i/20;for(double value:values)counts[Math.min(19,(int)((value-low)/(high-low)*20))]++;return new Histogram(breaks,counts);}
}
