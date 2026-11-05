package org.brightstats.stats;

import java.util.*;
import java.util.function.IntConsumer;
import org.apache.commons.math3.linear.*;

/** Two-stage composite-score polynomial paths. Products are never restandardized. */
public final class NonlinearPls {
    private NonlinearPls() {}
    public record Extension(int target, int[] factors) {
        public Extension {
            factors=factors.clone(); Arrays.sort(factors);
            if(target<0||factors.length<2||factors.length>3)
                throw new IllegalArgumentException("Polynomial extensions need two or three factors.");
            for(int factor:factors)if(factor<0||factor==target)
                throw new IllegalArgumentException("A polynomial factor cannot be its own outcome.");
        }
        @Override public int[] factors(){return factors.clone();}
    }
    public record Grid(int source,int target,double lower,double upper,int points) {
        public Grid {
            if(source<0||target<0||source==target||!Double.isFinite(lower)||!Double.isFinite(upper)
                    ||!Double.isFinite(upper-lower)||!(upper>lower)||points<2||points>201)
                throw new IllegalArgumentException("Choose distinct curve variables, finite ordered bounds and 2–201 points.");
        }
    }
    public record Specification(List<Extension> extensions,List<PathProcess.Probe> probes,Grid grid) {
        public Specification {
            extensions=List.copyOf(extensions);probes=List.copyOf(probes);
            if(extensions.size()>200||probes.size()>20)
                throw new IllegalArgumentException("Use at most 200 polynomial requests and 20 probe conditions.");
        }
    }
    public record Regressor(int[] factors) {
        public Regressor {factors=factors.clone();}
        @Override public int[] factors(){return factors.clone();}
    }
    public record Contribution(int column,double reducedRSquared,Double fSquared) {}
    public record Equation(int target,List<Regressor> regressors,double[] coefficients,int residualDegreesOfFreedom,
                           double residualSumOfSquares,double rSquared,double adjustedRSquared,
                           double standardizedConditionNumber,List<Contribution> contributions) {
        public Equation {regressors=List.copyOf(regressors);coefficients=coefficients.clone();contributions=List.copyOf(contributions);}
        @Override public double[] coefficients(){return coefficients.clone();}
    }
    public record Effect(String probe,String family,int source,int target,List<Integer> route,double value) {
        public Effect {route=List.copyOf(route);}
    }
    public record CurvePoint(String probe,int source,int target,double x,double value) {}
    public record Fit(Specification specification,PlsSem.Options options,int observations,List<String> names,
                      List<PlsSem.Edge> mainEffects,PlsSem.Fit firstStage,double[][] scores,
                      List<Equation> equations,List<Effect> effects,List<CurvePoint> curves,List<String> warnings) {
        public Fit {if(names.size()<2||observations<3||scores.length!=observations||Matrices.width(scores,3)!=names.size()||new HashSet<>(names).size()!=names.size())throw new IllegalArgumentException("Invalid nonlinear result dimensions.");for(var equation:equations){if(equation.target()<0||equation.target()>=names.size()||equation.coefficients.length!=equation.regressors.size()+1||equation.residualDegreesOfFreedom!=observations-equation.coefficients.length||equation.residualDegreesOfFreedom<=0||!Double.isFinite(equation.residualSumOfSquares)||equation.residualSumOfSquares<0||!Double.isFinite(equation.rSquared)||!Double.isFinite(equation.adjustedRSquared)||!Double.isFinite(equation.standardizedConditionNumber))throw new IllegalArgumentException("Invalid nonlinear equation result.");for(double value:equation.coefficients)if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite polynomial coefficient.");for(var term:equation.regressors)for(int factor:term.factors)if(factor<0||factor>=names.size()||factor==equation.target)throw new IllegalArgumentException("Invalid archived polynomial factor.");}names=List.copyOf(names);mainEffects=List.copyOf(mainEffects);scores=Matrices.copy(scores);
            equations=List.copyOf(equations);effects=List.copyOf(effects);curves=List.copyOf(curves);warnings=List.copyOf(warnings);}
        @Override public double[][] scores(){return Matrices.copy(scores);}
    }
    public record ScoreFit(Specification specification,int observations,List<String> names,List<PlsSem.Edge> mainEffects,double[][] scores,List<Equation> equations,List<Effect> effects,List<CurvePoint> curves,List<String> warnings){public ScoreFit{names=List.copyOf(names);mainEffects=List.copyOf(mainEffects);scores=Matrices.copy(scores);equations=List.copyOf(equations);effects=List.copyOf(effects);curves=List.copyOf(curves);warnings=List.copyOf(warnings);}@Override public double[][] scores(){return Matrices.copy(scores);}}
    public static List<PlsSem.Edge> normalizedPaths(List<String> names,List<PlsSem.Edge> paths,List<Extension> extensions){List<PlsSem.Block> blocks=new ArrayList<>();for(int i=0;i<names.size();i++)blocks.add(new PlsSem.Block(names.get(i),new int[]{i},PlsSem.Mode.A));return new Work(blocks,paths,PlsSem.Options.defaults(),new Specification(extensions,List.of(),null)).edges;}
    public static ScoreFit fitScores(double[][] supplied,List<String> names,List<PlsSem.Edge> paths,Specification specification,boolean zeroLinearIntercept,boolean details){if(Matrices.width(supplied,3)!=names.size()||(long)supplied.length*names.size()>5000000L)throw new IllegalArgumentException("Score matrix dimensions differ or exceed five million cells.");List<PlsSem.Block> blocks=new ArrayList<>();for(int i=0;i<names.size();i++)blocks.add(new PlsSem.Block(names.get(i),new int[]{i},PlsSem.Mode.A));var fitted=regressScores(Matrices.copy(supplied),new Work(blocks,paths,PlsSem.Options.defaults(),specification),null,details,zeroLinearIntercept);return new ScoreFit(fitted.specification(),fitted.observations(),fitted.names(),fitted.mainEffects(),fitted.scores(),fitted.equations(),fitted.effects(),fitted.curves(),fitted.warnings());}
    private static Fit scoreCarrier(ScoreFit fit){return new Fit(fit.specification(),PlsSem.Options.defaults(),fit.observations(),fit.names(),fit.mainEffects(),null,fit.scores(),fit.equations(),fit.effects(),fit.curves(),fit.warnings());}
    static List<Bootstrap.Parameter> scoreParameters(ScoreFit fit,boolean includeLinearIntercept){return parameters(scoreCarrier(fit),includeLinearIntercept);}
    static double[] scoreValues(ScoreFit fit,boolean includeLinearIntercept){return values(scoreCarrier(fit),includeLinearIntercept);}
    public record Resampled(Fit point,Bootstrap.Result inference) {}
    private record Route(int source,int target,List<Integer> nodes) {}
    private static final class Work {
        final List<PlsSem.Block> blocks;final PlsSem.Options options;final Specification specification;
        final List<String> names;final List<PlsSem.Edge> edges;final List<List<Regressor>> definitions;
        final List<PathProcess.Probe> probes;final List<Route> routes=new ArrayList<>();final boolean[][] graph;
        Work(List<PlsSem.Block> supplied,List<PlsSem.Edge> base,PlsSem.Options options,Specification specification) {
            blocks=List.copyOf(supplied);this.options=Objects.requireNonNull(options);
            this.specification=Objects.requireNonNull(specification);int count=blocks.size();
            if(count<2||count>64)throw new IllegalArgumentException("Two-stage polynomial PLS supports 2–64 constructs.");
            names=blocks.stream().map(PlsSem.Block::name).toList();
            if(new HashSet<>(names).size()!=count||names.stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException("Name constructs uniquely.");
            graph=new boolean[count][count];
            for(var edge:base){checkEdge(edge.source(),edge.target(),count);
                if(graph[edge.source()][edge.target()])throw new IllegalArgumentException("Duplicate main-effect path.");
                graph[edge.source()][edge.target()]=true;}
            List<Map<String,Regressor>> nonlinear=new ArrayList<>();for(int i=0;i<count;i++)nonlinear.add(new TreeMap<>());
            for(var extension:specification.extensions){if(extension.target>=count)throw new IllegalArgumentException("Polynomial target out of range.");
                for(int factor:extension.factors){checkEdge(factor,extension.target,count);graph[factor][extension.target]=true;}
                // All lower-order multisets are retained, including x^2 in x^2*w.
                for(int mask=1;mask<(1<<extension.factors.length);mask++){int size=Integer.bitCount(mask);if(size<2)continue;
                    int[] factors=new int[size];for(int i=0,j=0;i<extension.factors.length;i++)if((mask&(1<<i))!=0)factors[j++]=extension.factors[i];
                    nonlinear.get(extension.target).put(Arrays.toString(factors),new Regressor(factors));}}
            int[] indegree=new int[count];for(int i=0;i<count;i++)for(int j=0;j<count;j++)if(graph[i][j])indegree[j]++;
            ArrayDeque<Integer> queue=new ArrayDeque<>();for(int i=0;i<count;i++)if(indegree[i]==0)queue.add(i);int visited=0;
            while(!queue.isEmpty()){int source=queue.remove();visited++;for(int target=0;target<count;target++)if(graph[source][target]&&--indegree[target]==0)queue.add(target);}
            if(visited!=count)throw new IllegalArgumentException("Main effects and hierarchy must form an acyclic graph.");
            List<PlsSem.Edge> normalized=new ArrayList<>();for(int i=0;i<count;i++)for(int j=0;j<count;j++)if(graph[i][j])normalized.add(new PlsSem.Edge(i,j));edges=List.copyOf(normalized);if(edges.isEmpty())throw new IllegalArgumentException("Specify at least one main or polynomial relationship.");
            definitions=new ArrayList<>();for(int target=0;target<count;target++){List<Regressor> terms=new ArrayList<>();
                for(int source=0;source<count;source++)if(graph[source][target])terms.add(new Regressor(new int[]{source}));
                List<Regressor> extra=new ArrayList<>(nonlinear.get(target).values());extra.sort(Comparator.comparingInt(value->value.factors.length));terms.addAll(extra);definitions.add(List.copyOf(terms));}
            probes=specification.probes.isEmpty()?List.of(new PathProcess.Probe("Mean composite scores",Map.of())):specification.probes;
            Set<String> probeNames=new HashSet<>();for(var probe:probes){if(!probeNames.add(probe.name()))throw new IllegalArgumentException("Name probes uniquely.");
                for(int column:probe.values().keySet())if(column>=count)throw new IllegalArgumentException("Probe construct out of range.");}
            if(specification.grid!=null){var grid=specification.grid;if(grid.source>=count||grid.target>=count||definitions.get(grid.target).stream().noneMatch(term->Arrays.stream(term.factors).anyMatch(column->column==grid.source)))throw new IllegalArgumentException("Curve variables or response equation are unavailable.");}
            for(int source=0;source<count;source++)walk(source,source,new ArrayList<>(List.of(source)));
        }
        private void walk(int source,int current,List<Integer> path){for(int next=0;next<graph.length;next++)if(graph[current][next]){
            List<Integer> route=new ArrayList<>(path);route.add(next);routes.add(new Route(source,next,List.copyOf(route)));
            if(routes.size()>10000)throw new IllegalArgumentException("More than 10,000 structural routes; simplify the model before enumerating specific effects.");
            walk(source,next,route);}}
    }
    private static void checkEdge(int source,int target,int count){if(source<0||target<0||source>=count||target>=count||source==target)throw new IllegalArgumentException("Invalid polynomial/main-effect connection.");}
    public static void validate(List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,PlsSem.Options options,Specification specification){new Work(blocks,edges,options,specification);}
    private static double monomial(Regressor term,double[] values){double product=1;for(int factor:term.factors)product*=values[factor];return product;}
    private static double derivative(Regressor term,int source,double[] values){double result=0;for(int i=0;i<term.factors.length;i++)if(term.factors[i]==source){double product=1;for(int j=0;j<term.factors.length;j++)if(i!=j)product*=values[term.factors[j]];result+=product;}return result;}
    private static double predict(Equation equation,double[] values){double result=equation.coefficients[0];for(int j=0;j<equation.regressors.size();j++)result=Math.fma(equation.coefficients[j+1],monomial(equation.regressors.get(j),values),result);return result;}
    private static double condition(double[][] design){double[][] matrix=new double[design.length][design[0].length+1];for(var row:matrix)row[0]=1;
        for(int j=0;j<design[0].length;j++){double mean=0,variance=0;for(var row:design)mean+=row[j]/design.length;for(var row:design)variance+=(row[j]-mean)*(row[j]-mean)/design.length;
            if(!(variance>0)||!Double.isFinite(variance))throw new IllegalArgumentException("Constant or nonfinite polynomial design column.");
            double scale=Math.sqrt(variance);for(int i=0;i<design.length;i++)matrix[i][j+1]=(design[i][j]-mean)/scale;}
        var svd=new SingularValueDecomposition(new Array2DRowRealMatrix(matrix,false));double condition=svd.getConditionNumber();
        if(svd.getRank()!=matrix[0].length||!Double.isFinite(condition)||condition>1e10)throw new IllegalArgumentException("Rank-deficient or numerically unidentified polynomial equation.");return condition;}
    private static Fit compute(double[][] data,Work work,double[][] reference,int[] rows,boolean details){
        Matrices.width(data,3);Matrices.interrupted();var stage=PlsSem.fit(data,work.blocks,work.edges,work.options);
        if(!stage.converged())throw new IllegalArgumentException("The main-effects PLS model did not converge.");
        double[][] scores=stage.scores();if(reference!=null){double[] signs=Bootstrap.alignment(scores,reference,rows);for(var row:scores)for(int j=0;j<signs.length;j++)row[j]*=signs[j];}
        return regressScores(scores,work,stage,details,true);
    }
    private static Fit regressScores(double[][] scores,Work work,PlsSem.Fit stage,boolean details,boolean zeroLinearIntercept){
        List<String> warnings=new ArrayList<>(List.of("Two-stage unit-variance PLS composite scores; not PLSc or nonlinear latent maximum likelihood.",
                "Lower-order main effects/products are included automatically. Product columns are left unscaled in the structural regressions.",
                "Ordinary generated-score standard errors are omitted. Full-procedure IID bootstrap refits and aligns the first-stage scores in every resample.",
                "Effects are local derivatives at the supplied composite-score probes. Curves hold other parent scores fixed; neither proves causality or a population-average treatment effect.",
                "Bootstrap ratio probabilities are unadjusted across parameters/probes; curve intervals, if requested, are pointwise rather than simultaneous."));
        if(stage==null){warnings.set(0,"Polynomial regressions of explicitly supplied variables or component scores; first-stage estimation is described by the enclosing analysis.");warnings.set(2,"Point equation fits alone do not account for generated-score uncertainty. The enclosing analysis states its refitting and inference procedure.");}
        List<Equation> equations=new ArrayList<>();Map<Integer,Equation> byTarget=new HashMap<>();
        for(int target=0;target<work.names.size();target++){Matrices.interrupted();var terms=work.definitions.get(target);if(terms.isEmpty())continue;
            if(scores.length<=terms.size()+1||(long)scores.length*terms.size()>5000000L)throw new IllegalArgumentException("Polynomial equation lacks residual degrees of freedom or exceeds five million design cells.");
            double[][] x=new double[scores.length][terms.size()];double[] y=new double[scores.length];for(int i=0;i<scores.length;i++){y[i]=scores[i][target];for(int j=0;j<terms.size();j++)x[i][j]=monomial(terms.get(j),scores[i]);}
            double condition=condition(x);var fit=OlsRegression.fit(x,y);List<Contribution> contributions=new ArrayList<>();
            if(details){if(condition>30)warnings.add(work.names.get(target)+": standardized polynomial condition exceeds 30; inspect collinearity.");
                double meanY=0,tss=0;for(double value:y)meanY+=value/y.length;for(double value:y)tss+=(value-meanY)*(value-meanY);double sigma=Math.sqrt(fit.residualSumOfSquares()/fit.residualDegreesOfFreedom());double[] errors=fit.standardErrors(),beta=fit.coefficients();
                for(int column=0;column<terms.size();column++)if(terms.get(column).factors.length>1){Matrices.interrupted();double restricted,f2;
                    // Exact nested-OLS identity, using the covariance diagonal only as algebra, not generated-score inference.
                    if(sigma>0&&errors[column+1]>0){double incremental=Math.pow(beta[column+1]*(sigma/errors[column+1]),2);restricted=1-(fit.residualSumOfSquares()+incremental)/tss;f2=incremental/fit.residualSumOfSquares();}
                    else{double[][] reduced=new double[x.length][terms.size()-1];for(int i=0;i<x.length;i++)for(int j=0,k=0;j<terms.size();j++)if(j!=column)reduced[i][k++]=x[i][j];restricted=OlsRegression.fit(reduced,y).rSquared();f2=fit.rSquared()<1?(fit.rSquared()-restricted)/(1-fit.rSquared()) : fit.rSquared()>restricted?Double.POSITIVE_INFINITY:Double.NaN;}
                    contributions.add(new Contribution(column,restricted,Double.isNaN(f2)?null:f2));}}
            double[] reportedCoefficients=fit.coefficients();if(zeroLinearIntercept&&terms.stream().allMatch(term->term.factors.length==1))reportedCoefficients[0]=0;
            Equation equation=new Equation(target,terms,reportedCoefficients,fit.residualDegreesOfFreedom(),fit.residualSumOfSquares(),fit.rSquared(),fit.adjustedRSquared(),condition,contributions);equations.add(equation);byTarget.put(target,equation);
        }
        List<Effect> effects=new ArrayList<>();List<CurvePoint> curves=new ArrayList<>();int count=work.names.size();
        for(var probe:work.probes){Matrices.interrupted();double[] values=new double[count];probe.values().forEach((column,value)->values[column]=value);double[][] jacobian=new double[count][count];
            for(var equation:equations)for(int source=0;source<count;source++)for(int j=0;j<equation.regressors.size();j++)jacobian[source][equation.target]+=equation.coefficients[j+1]*derivative(equation.regressors.get(j),source,values);
            double[][] indirect=new double[count][count];boolean[][] reaches=new boolean[count][count],hasIndirect=new boolean[count][count];
            for(var route:work.routes){double product=1;for(int j=1;j<route.nodes.size();j++)product*=jacobian[route.nodes.get(j-1)][route.nodes.get(j)];reaches[route.source][route.target]=true;
                if(route.nodes.size()>2){hasIndirect[route.source][route.target]=true;indirect[route.source][route.target]+=product;effects.add(new Effect(probe.name(),"Specific indirect derivative",route.source,route.target,route.nodes,product));}}
            for(int source=0;source<count;source++)for(int target=0;target<count;target++)if(reaches[source][target]){
                if(work.graph[source][target])effects.add(new Effect(probe.name(),"Direct derivative",source,target,List.of(source,target),jacobian[source][target]));
                if(hasIndirect[source][target])effects.add(new Effect(probe.name(),"Total indirect derivative",source,target,List.of(),indirect[source][target]));
                effects.add(new Effect(probe.name(),"Total derivative",source,target,List.of(),jacobian[source][target]+indirect[source][target]));}
            if(work.specification.grid!=null){var grid=work.specification.grid;double[] at=values.clone();for(int step=0;step<grid.points;step++){double x=grid.lower+(grid.upper-grid.lower)*step/(grid.points-1.0);at[grid.source]=x;curves.add(new CurvePoint(probe.name(),grid.source,grid.target,x,predict(byTarget.get(grid.target),at)));}}
        }
        if(work.specification.grid!=null){var grid=work.specification.grid;double minimum=Double.POSITIVE_INFINITY,maximum=Double.NEGATIVE_INFINITY;for(var row:scores){minimum=Math.min(minimum,row[grid.source]);maximum=Math.max(maximum,row[grid.source]);}if(grid.lower<minimum||grid.upper>maximum)warnings.add("The requested curve grid extends outside the observed first-stage score range; those points extrapolate.");}
        if(effects.stream().anyMatch(value->!Double.isFinite(value.value))||curves.stream().anyMatch(value->!Double.isFinite(value.value)))throw new IllegalArgumentException("Polynomial effect/curve exceeds the finite numerical range.");
        return new Fit(work.specification,work.options,scores.length,work.names,work.edges,stage,scores,equations,effects,curves,warnings);
    }
    public static Fit fit(double[][] data,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,PlsSem.Options options,Specification specification){return compute(data,new Work(blocks,edges,options,specification),null,null,true);}
    public static String label(Regressor term,List<String> names){return String.join(" × ",Arrays.stream(term.factors).mapToObj(names::get).toList());}
    private static List<Bootstrap.Parameter> parameters(Fit fit){return parameters(fit,false);}
    private static List<Bootstrap.Parameter> parameters(Fit fit,boolean includeLinearIntercept){List<Bootstrap.Parameter> result=new ArrayList<>();for(var equation:fit.equations){boolean polynomial=equation.regressors.stream().anyMatch(term->term.factors.length>1);
        for(int j=0;j<equation.coefficients.length;j++){if(j==0&&!polynomial&&!includeLinearIntercept)continue;Regressor term=j==0?null:equation.regressors.get(j-1);result.add(new Bootstrap.Parameter(j==0?"Intercept":term.factors.length==1?"Linear coefficient":"Polynomial coefficient",term!=null&&term.factors.length==1?term.factors[0]:-1,equation.target,j,j==0?"Intercept":label(term,fit.names)));}}
        for(int i=0;i<fit.effects.size();i++){var effect=fit.effects.get(i);result.add(new Bootstrap.Parameter(effect.family,effect.source,effect.target,i,effect.probe+" "+effect.route));}
        for(int i=0;i<fit.curves.size();i++){var point=fit.curves.get(i);result.add(new Bootstrap.Parameter("Conditional fitted outcome",point.source,point.target,i,point.probe+" x="+point.x));}return result;}
    private static double[] values(Fit fit){return values(fit,false);}
    private static double[] values(Fit fit,boolean includeLinearIntercept){List<Double> result=new ArrayList<>();for(var equation:fit.equations){boolean polynomial=equation.regressors.stream().anyMatch(term->term.factors.length>1);for(int j=0;j<equation.coefficients.length;j++)if(j>0||polynomial||includeLinearIntercept)result.add(equation.coefficients[j]);}for(var effect:fit.effects)result.add(effect.value);for(var point:fit.curves)result.add(point.value);return result.stream().mapToDouble(Double::doubleValue).toArray();}
    public static Resampled bootstrap(double[][] data,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,PlsSem.Options options,Specification specification,Bootstrap.Options resampling,IntConsumer progress){
        Work work=new Work(blocks,edges,options,specification);Fit point=compute(data,work,null,null,true);double[][] reference=point.scores();var parameters=parameters(point);if(parameters.size()>50000)throw new IllegalArgumentException("More than 50,000 polynomial inference targets; reduce routes/probes/grid points.");
        var inference=Resampling.run(data,parameters,values(point),resampling,(sample,rows)->values(compute(sample,work,reference,rows,false)),progress,"Two-stage nonlinear PLS","Every bootstrap/jackknife refits main-effects PLS, aligns score signs using original sampled row indices, rebuilds hierarchical raw products and refits structural equations. Probe/grid coordinates remain fixed.");
        List<String> warnings=new ArrayList<>(inference.warnings());warnings.addAll(point.warnings);inference=new Bootstrap.Result(inference.options(),inference.method(),inference.randomGenerator(),inference.signHandling(),inference.successful(),inference.failed(),inference.failures(),inference.estimates(),warnings,inference.estimator());return new Resampled(point,inference);
    }
}
