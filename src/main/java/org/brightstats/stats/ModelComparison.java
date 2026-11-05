package org.brightstats.stats;

import java.util.*;
import java.util.function.IntConsumer;

/** Two candidate PLS models on one prepared dataset and identical held-out assignments. */
public final class ModelComparison {
    private ModelComparison(){}
    public record Model(String name,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,PlsSem.Options options){
        public Model{if(name==null||name.isBlank())throw new IllegalArgumentException("Name both candidate models.");blocks=List.copyOf(blocks);edges=List.copyOf(edges);Objects.requireNonNull(options);}
    }
    public record Candidate(Model model,PlsSem.Fit fit,Prediction.Result prediction){}
    public record Loss(String scope,List<Integer> columns,PairedLossTest.Result test,double pHolmTwoSided){public Loss{columns=List.copyOf(columns);}}
    public record Criterion(String construct,int firstConstruct,int secondConstruct,int observations,int firstParameters,int secondParameters,
                            double firstResidualVariance,double secondResidualVariance,double firstAic,double secondAic,double firstBic,double secondBic,
                            double firstAicc,double secondAicc,double firstAkaikeWeight,double secondAkaikeWeight,double firstBicWeight,double secondBicWeight,String status){}
    public record Result(Prediction.Options validation,double confidence,Candidate first,Candidate second,List<Integer> commonTargets,
                         List<Loss> losses,List<Criterion> criteria,List<String> warnings){public Result{commonTargets=List.copyOf(commonTargets);losses=List.copyOf(losses);criteria=List.copyOf(criteria);warnings=List.copyOf(warnings);}}
    private record Key(int repeat,int row,int column){}
    public static Result compare(double[][] data,Model first,Model second,Prediction.Options validation,double confidence,IntConsumer progress){
        if(first.name().equals(second.name()))throw new IllegalArgumentException("Candidate model names must differ.");
        if(!Double.isFinite(confidence)||confidence<=0||confidence>=1)throw new IllegalArgumentException("Choose confidence between zero and one.");
        Matrices.width(data,6);long forecasts=(long)data.length*validation.repeats()*(first.blocks().stream().mapToInt(b->b.columns().length).sum()+second.blocks().stream().mapToInt(b->b.columns().length).sum());
        if(forecasts>1_000_000)throw new IllegalArgumentException("Comparison exceeds the one-million-forecast working limit; reduce repeats or dataset size.");
        int steps=(validation.scheme()==Prediction.Scheme.K_FOLD?validation.folds():1)*validation.repeats();
        Candidate a=run(data,first,validation,progress,0),b=run(data,second,validation,progress,steps);
        if(!Arrays.deepEquals(a.prediction().assignments(),b.prediction().assignments()))throw new IllegalArgumentException("Held-out assignments differ.");
        Map<Key,Prediction.Row> left=index(a.prediction()),right=index(b.prediction());
        TreeSet<Integer> common=new TreeSet<>();for(var t:a.prediction().targets())common.add(t.column());Set<Integer> other=new HashSet<>();for(var t:b.prediction().targets())other.add(t.column());common.retainAll(other);
        if(common.isEmpty())throw new IllegalArgumentException("Models have no common held-out target indicators.");
        List<String>warnings=new ArrayList<>();warnings.addAll(a.prediction().warnings());warnings.addAll(b.prediction().warnings());
        if(common.size()!=a.prediction().targets().size()||common.size()!=b.prediction().targets().size())warnings.add("Prediction comparison uses only common target columns. Targets unique to either candidate are excluded explicitly from the common-target list.");
        LinkedHashMap<String,List<Integer>> scopes=new LinkedHashMap<>();scopes.put("OVERALL",List.copyOf(common));
        for(int i=0;i<first.blocks().size();i++){List<Integer> columns=columns(first.blocks().get(i));if(!common.containsAll(columns))continue;for(int j=0;j<second.blocks().size();j++)if(columns.equals(columns(second.blocks().get(j)))){scopes.put("CONSTRUCT "+i+" / "+j+": "+first.blocks().get(i).name()+" / "+second.blocks().get(j).name(),columns);break;}}
        for(int column:common)scopes.put("INDICATOR "+column,List.of(column));List<Loss> losses=new ArrayList<>();
        for(var scope:scopes.entrySet()){
            List<Double> x=new ArrayList<>(),y=new ArrayList<>();for(int row=1;row<=data.length;row++){
                Matrices.interrupted();double meanA=0,meanB=0;int repeats=0;
                for(int repeat=1;repeat<=validation.repeats();repeat++){double la=0,lb=0;int count=0;boolean complete=true;
                    for(int column:scope.getValue()){var pa=left.get(new Key(repeat,row,column));var pb=right.get(new Key(repeat,row,column));if(pa==null||pb==null||!Double.isFinite(pa.actual())||!Double.isFinite(pb.actual())){complete=false;break;}if(pa.fold()!=pb.fold()||pa.actual()!=pb.actual())throw new IllegalArgumentException("Paired forecasts disagree on fold or actual outcome.");double ea=pa.actual()-pa.pls(),eb=pb.actual()-pb.pls();double sa=ea*ea,sb=eb*eb;if(!Double.isFinite(sa)||!Double.isFinite(sb))throw new IllegalArgumentException("Squared prediction error exceeded numeric range.");count++;la+=(sa-la)/count;lb+=(sb-lb)/count;}
                    if(complete){repeats++;meanA+=(la-meanA)/repeats;meanB+=(lb-meanB)/repeats;}}
                if(repeats>0&&(validation.scheme()==Prediction.Scheme.HOLDOUT||repeats==validation.repeats())){x.add(meanA);y.add(meanB);}}
            var test=PairedLossTest.evaluate(x.stream().mapToDouble(Double::doubleValue).toArray(),y.stream().mapToDouble(Double::doubleValue).toArray(),confidence);
            if(validation.scheme()==Prediction.Scheme.HOLDOUT)test=new PairedLossTest.Result(test.observations(),test.firstLoss(),test.secondLoss(),test.difference(),test.standardDeviation(),test.standardError(),Double.NaN,test.degreesOfFreedom(),Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,"HOLDOUT_DESCRIPTIVE");
            losses.add(new Loss(scope.getKey(),scope.getValue(),test,Double.NaN));}
        // Equivalent indicator sets form one hypothesis, even when displayed under several scope labels.
        Map<List<Integer>,List<Integer>> equivalent=new LinkedHashMap<>();for(int i=0;i<losses.size();i++)equivalent.computeIfAbsent(losses.get(i).columns(),unused->new ArrayList<>()).add(i);
        List<Integer> order=equivalent.values().stream().map(List::getFirst).filter(i->Double.isFinite(losses.get(i).test().pTwoSided())).sorted(Comparator.comparingDouble(i->losses.get(i).test().pTwoSided())).toList();double previous=0;
        for(int rank=0;rank<order.size();rank++){var old=losses.get(order.get(rank));double adjusted=Math.min(1,Math.max(previous,(order.size()-rank)*old.test().pTwoSided()));previous=adjusted;for(int i:equivalent.get(old.columns()))losses.set(i,new Loss(losses.get(i).scope(),old.columns(),old.test(),adjusted));}
        List<Criterion> criteria=new ArrayList<>();double[][] scoreA=a.fit().scores(),scoreB=b.fit().scores();double[][] pathA=a.fit().pathCoefficients(),pathB=b.fit().pathCoefficients();
        for(int i=0;i<first.blocks().size();i++){var block=first.blocks().get(i);List<Integer> cols=columns(block);if(first.blocks().stream().filter(candidate->columns(candidate).equals(cols)).count()!=1)continue;final int targetA=i;if(first.edges().stream().noneMatch(e->e.target()==targetA))continue;List<Integer> matches=new ArrayList<>();for(int j=0;j<second.blocks().size();j++)if(cols.equals(columns(second.blocks().get(j))))matches.add(j);if(matches.size()!=1)continue;int j=matches.getFirst();final int targetB=j;var peer=second.blocks().get(j);if(second.edges().stream().noneMatch(e->e.target()==targetB)||block.mode()!=peer.mode()||block.measurementType()!=peer.measurementType())continue;
            double[] ca=criterion(scoreA,pathA,first.edges(),i),cb=criterion(scoreB,pathB,second.edges(),j);boolean valid=Double.isFinite(ca[2])&&Double.isFinite(cb[2]);double wa=valid?weight(ca[2],cb[2]):Double.NaN,wb=valid?weight(ca[3],cb[3]):Double.NaN;
            criteria.add(new Criterion(block.name()+" / "+peer.name(),i,j,data.length,(int)ca[0],(int)cb[0],ca[1],cb[1],ca[2],cb[2],ca[3],cb[3],ca[4],cb[4],wa,valid?1-wa:Double.NaN,wb,valid?1-wb:Double.NaN,valid?"OK":"NUMERICALLY_ZERO_RESIDUAL_VARIANCE"));}
        if(criteria.isEmpty())warnings.add("No unambiguous endogenous construct with identical indicator set, measurement type and mode is available for score-based information criteria.");
        warnings.add("Negative paired loss difference favors the first model. Repeated squared errors are averaged within observation; original-unit indicator losses are equally weighted. Two-sided Holm p-values adjust distinct indicator-set hypotheses. Confidence intervals are pointwise; folds share training observations and IID cross-sectional inference is approximate.");
        warnings.add("Information criteria are Gaussian residual-score heuristics, not a joint latent-variable likelihood or likelihood-ratio test. Estimated construct scores may differ between candidates. k counts structural parents plus an intercept and residual variance; residual variance <= 1e-24 in standardized-score units suppresses criteria. Akaike/BIC weights describe only these two candidates, not probability of truth.");
        warnings.add("Repeated holdout comparisons are descriptive only. These results do not establish causal validity, universal superiority, or native numerical equivalence.");
        return new Result(validation,confidence,a,b,List.copyOf(common),losses,criteria,warnings);
    }
    private static Candidate run(double[][] data,Model model,Prediction.Options options,IntConsumer progress,int offset){var fit=PlsSem.fit(data,model.blocks(),model.edges(),model.options());if(!fit.converged())throw new IllegalArgumentException("Candidate "+model.name()+" did not converge.");return new Candidate(model,fit,Prediction.evaluate(data,model.blocks(),model.edges(),model.options(),options,progress==null?null:n->progress.accept(offset+n)));}
    private static Map<Key,Prediction.Row> index(Prediction.Result result){Set<Integer> columns=new HashSet<>();for(var target:result.targets())if(!columns.add(target.column()))throw new IllegalArgumentException("Candidate repeats an observed target column; prediction pairing would be ambiguous.");Map<Key,Prediction.Row> index=new HashMap<>();for(var row:result.predictions()){int column=result.targets().get(row.target()).column();if(index.put(new Key(row.repeat(),row.row(),column),row)!=null)throw new IllegalArgumentException("Duplicate forecast identity.");}return index;}
    private static List<Integer> columns(PlsSem.Block block){return Arrays.stream(block.columns()).sorted().boxed().toList();}
    private static double[] criterion(double[][] scores,double[][] paths,List<PlsSem.Edge> edges,int target){double rss=0;int parents=0;for(var edge:edges)if(edge.target()==target)parents++;for(var row:scores){double residual=row[target];for(var edge:edges)if(edge.target()==target)residual-=paths[edge.source()][target]*row[edge.source()];rss+=residual*residual;}int n=scores.length,k=parents+2;double variance=rss/n,aic=Double.NaN,bic=Double.NaN,aicc=Double.NaN;if(Double.isFinite(variance)&&variance>1e-24){double likelihood=n*Math.log(variance);aic=likelihood+2*k;bic=likelihood+k*Math.log(n);if(n>k+1)aicc=aic+2.0*k*(k+1)/(n-k-1);}return new double[]{k,variance,aic,bic,aicc};}
    private static double weight(double first,double second){double delta=(first-second)/2;if(delta>=0){double e=Math.exp(-delta);return e/(1+e);}return 1/(1+Math.exp(delta));}
}
