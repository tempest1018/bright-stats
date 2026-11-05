package org.brightstats.stats;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.IntConsumer;
import org.apache.commons.math3.linear.*;

/** Training-only preprocessing and root-input prediction; held-out endogenous values are never predictors. */
public final class Prediction {
    private Prediction() {}
    public enum Scheme { K_FOLD, HOLDOUT }
    public record Options(Scheme scheme,int folds,int repeats,double testFraction,long seed) {
        public Options {
            Objects.requireNonNull(scheme);
            if(folds<2 || folds>100 || repeats<1 || repeats>100 || !Double.isFinite(testFraction) || testFraction<.01 || testFraction>.9)
                throw new IllegalArgumentException("Choose 2–100 folds, 1–100 repeats and a 1–90% holdout fraction.");
        }
        public static Options defaults(){return new Options(Scheme.K_FOLD,5,5,.2,42);}
    }
    public record Target(int construct,int column,String label) {}
    public record Row(int repeat,int fold,int row,int target,double actual,double pls,double mean,double linear) {}
    public record Metric(int repeat,int fold,Target target,int observations,double plsRmse,double plsMae,
                         double meanRmse,double meanMae,double linearRmse,double linearMae,double qSquaredPredict) {}
    public record Result(Options options,String method,String preprocessing,String benchmark,int[][] assignments,
                         List<Target> targets,List<Row> predictions,List<Metric> metrics,List<Metric> foldMetrics,List<String> warnings) {
        public Result { assignments=Arrays.stream(assignments).map(int[]::clone).toArray(int[][]::new); targets=List.copyOf(targets);predictions=List.copyOf(predictions);metrics=List.copyOf(metrics);foldMetrics=List.copyOf(foldMetrics);warnings=List.copyOf(warnings); }
        @Override public int[][] assignments(){return Arrays.stream(assignments).map(int[]::clone).toArray(int[][]::new);}
    }
    private record Structure(List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,int[] order,int[] roots,List<Target> targets,boolean[] used) {}
    /** Trained model: prediction only reads root indicators; target cells can be absent (NaN). */
    public static final class Model {
        private final Structure structure;
        private final double[] means,scales,intercepts;
        private final double[][] weights,loadings,paths,linear;
        private final int[] rootColumns;
        private final List<String> warnings;
        private Model(Structure structure,double[] means,double[] scales,double[] intercepts,PlsSem.Fit fit,double[][] linear,int[] roots,List<String> warnings) {
            this.structure=structure;this.means=means.clone();this.scales=scales.clone();this.intercepts=intercepts.clone();weights=fit.weights();loadings=fit.loadings();paths=fit.pathCoefficients();this.linear=Arrays.stream(linear).map(double[]::clone).toArray(double[][]::new);rootColumns=roots.clone();this.warnings=List.copyOf(warnings);
        }
        public List<Target> targets(){return structure.targets();}
        public List<String> warnings(){return warnings;}
        /** Returns [row][target][PLS, training-mean benchmark, linear benchmark] in original indicator units. */
        public double[][][] predict(double[][] observations) {
            double[][][] output=new double[observations.length][structure.targets().size()][3];
            for(int i=0;i<observations.length;i++) {
                interrupted(); double[] row=observations[i]; if(row==null || row.length!=means.length)throw new IllegalArgumentException("Prediction column count differs from training data.");
                double[] scores=new double[structure.blocks().size()];
                for(int construct:structure.order()) {
                    var block=structure.blocks().get(construct);
                    if(Arrays.stream(structure.roots()).anyMatch(root->root==construct)) {
                        int[] columns=block.columns();
                        for(int k=0;k<columns.length;k++)scores[construct]+=standardized(row,columns[k])*weights[construct][k];
                    } else {
                        scores[construct]=intercepts[construct];
                        for(var edge:structure.edges())if(edge.target()==construct)scores[construct]+=paths[edge.source()][construct]*scores[edge.source()];
                    }
                }
                for(int t=0;t<structure.targets().size();t++) {
                    var target=structure.targets().get(t);int[] columns=structure.blocks().get(target.construct()).columns();int index=0;while(columns[index]!=target.column())index++;
                    double forecast=means[target.column()]+scales[target.column()]*loadings[target.construct()][index]*scores[target.construct()];
                    double lm=linear[t][0];for(int k=0;k<rootColumns.length;k++)lm+=linear[t][k+1]*standardized(row,rootColumns[k]);
                    if(!Double.isFinite(forecast)||!Double.isFinite(lm))throw new IllegalArgumentException("Prediction exceeded the finite numeric range.");
                    output[i][t]=new double[]{forecast,means[target.column()],lm};
                }
            }
            return output;
        }
        private double standardized(double[] row,int column) {
            double value=row[column];if(Double.isNaN(value))return 0;
            if(!Double.isFinite(value))throw new IllegalArgumentException("Infinite root indicator in prediction input.");
            return (value-means[column])/scales[column];
        }
    }
    private static void interrupted(){if(Thread.currentThread().isInterrupted())throw new CancellationException("Prediction cancelled.");}
    private static Structure structure(int columns,List<PlsSem.Block> suppliedBlocks,List<PlsSem.Edge> suppliedEdges) {
        var blocks=List.copyOf(suppliedBlocks);var edges=List.copyOf(suppliedEdges);int count=blocks.size();
        if(count==0)throw new IllegalArgumentException("Define a PLS path model first.");
        boolean[] used=new boolean[columns];
        for(var block:blocks)for(int column:block.columns()) {
            if(column<0||column>=columns||used[column])throw new IllegalArgumentException("Prediction requires valid, non-overlapping indicator blocks.");used[column]=true;
        }
        int[] degree=new int[count];Set<PlsSem.Edge> unique=new HashSet<>();
        for(var edge:edges) {
            if(edge.source()<0||edge.target()<0||edge.source()>=count||edge.target()>=count||edge.source()==edge.target()||!unique.add(edge))throw new IllegalArgumentException("Invalid prediction model connection.");degree[edge.target()]++;
        }
        var roots=new ArrayList<Integer>();var order=new ArrayList<Integer>();var targets=new ArrayList<Target>();
        for(int j=0;j<count;j++) {
            if(degree[j]==0){roots.add(j);order.add(j);}
            else if(blocks.get(j).measurementType()==PlsSem.MeasurementType.REFLECTIVE)for(int column:blocks.get(j).columns())targets.add(new Target(j,column,blocks.get(j).name()+" / column "+(column+1)));
        }
        for(int i=0;i<order.size();i++)for(var edge:edges)if(edge.source()==order.get(i)&&--degree[edge.target()]==0)order.add(edge.target());
        if(order.size()!=count)throw new IllegalArgumentException("Prediction requires an acyclic structural model.");
        if(targets.isEmpty())throw new IllegalArgumentException("Prediction requires at least one endogenous reflective indicator block.");
        return new Structure(blocks,edges,order.stream().mapToInt(Integer::intValue).toArray(),roots.stream().mapToInt(Integer::intValue).toArray(),List.copyOf(targets),used);
    }
    public static Model train(double[][] input,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,PlsSem.Options options) {
        if(input==null||input.length<3||input[0]==null||input[0].length==0)throw new IllegalArgumentException("At least three training observations are required.");
        Structure structure=structure(input[0].length,blocks,edges);int n=input.length,p=input[0].length;
        double[][] training=new double[n][p];double[] means=new double[p],scales=new double[p];int[] observed=new int[p];
        for(int i=0;i<n;i++) {
            interrupted();if(input[i]==null||input[i].length!=p)throw new IllegalArgumentException("Ragged prediction training data.");training[i]=input[i].clone();
            for(int j=0;j<p;j++)if(structure.used()[j]) {
                double value=input[i][j];if(Double.isInfinite(value))throw new IllegalArgumentException("Infinite training indicator.");
                if(!Double.isNaN(value)){observed[j]++;means[j]+=(value-means[j])/observed[j];}
            }
        }
        for(int j=0;j<p;j++)if(structure.used()[j]) {
            if(observed[j]==0||!Double.isFinite(means[j]))throw new IllegalArgumentException("Training column "+(j+1)+" has no finite observed mean.");
            double ss=0;for(int i=0;i<n;i++){if(Double.isNaN(training[i][j]))training[i][j]=means[j];double d=training[i][j]-means[j];ss+=d*d;}
            scales[j]=Math.sqrt(ss/n);if(!(scales[j]>0)||!Double.isFinite(scales[j]))throw new IllegalArgumentException("Training column "+(j+1)+" is constant or unscalable.");
        }
        var fit=PlsSem.fit(training,structure.blocks(),structure.edges(),options);
        if(!fit.converged())throw new IllegalArgumentException("Training PLS model did not converge.");
        double[][] scores=fit.scores(),paths=fit.pathCoefficients();double[] scoreMeans=new double[blocks.size()],intercepts=new double[blocks.size()];
        for(int i=0;i<n;i++)for(int j=0;j<scoreMeans.length;j++)scoreMeans[j]+=(scores[i][j]-scoreMeans[j])/(i+1);
        for(int j=0;j<intercepts.length;j++){intercepts[j]=scoreMeans[j];for(var edge:edges)if(edge.target()==j)intercepts[j]-=paths[edge.source()][j]*scoreMeans[edge.source()];}
        int[] roots=Arrays.stream(structure.roots()).flatMap(j->Arrays.stream(blocks.get(j).columns())).distinct().toArray();
        double[][] design=new double[n][roots.length+1];for(int i=0;i<n;i++){design[i][0]=1;for(int k=0;k<roots.length;k++)design[i][k+1]=(training[i][roots[k]]-means[roots[k]])/scales[roots[k]];}
        var svd=new SingularValueDecomposition(new Array2DRowRealMatrix(design,false));
        List<String> warnings=new ArrayList<>();if(svd.getRank()<design[0].length)warnings.add("Linear benchmark uses the SVD minimum-norm solution for a rank-deficient or underdetermined training design.");
        double[][] linear=new double[structure.targets().size()][];
        for(int t=0;t<linear.length;t++){int column=structure.targets().get(t).column();double[] y=new double[n];for(int i=0;i<n;i++)y[i]=training[i][column];linear[t]=svd.getSolver().solve(new ArrayRealVector(y,false)).toArray();}
        return new Model(structure,means,scales,intercepts,fit,linear,roots,warnings);
    }
    public static int[][] assignments(int n,Options options) {
        if(n<4 || (options.scheme()==Scheme.K_FOLD && options.folds()>n))throw new IllegalArgumentException("Too few cases for the requested split.");
        Random random=new Random(options.seed());int[][] assignments=new int[options.repeats()][n];
        for(int repeat=0;repeat<options.repeats();repeat++) {
            int[] shuffled=new int[n];for(int i=0;i<n;i++)shuffled[i]=i;
            for(int i=n-1;i>0;i--){int j=random.nextInt(i+1),value=shuffled[i];shuffled[i]=shuffled[j];shuffled[j]=value;}
            if(options.scheme()==Scheme.K_FOLD)for(int i=0;i<n;i++)assignments[repeat][shuffled[i]]=i%options.folds()+1;
            else {int count=(int)Math.ceil(n*options.testFraction());if(n-count<3)throw new IllegalArgumentException("Holdout leaves fewer than three training cases.");for(int i=0;i<count;i++)assignments[repeat][shuffled[i]]=1;}
        }
        return assignments;
    }
    public static Result evaluate(double[][] data,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,PlsSem.Options modelOptions,Options options,IntConsumer progress) {
        if(data==null||data.length<4||data[0]==null)throw new IllegalArgumentException("Expected at least four observations.");
        Structure structure=structure(data[0].length,blocks,edges);
        for(double[] row:data){if(row==null||row.length!=data[0].length)throw new IllegalArgumentException("Ragged prediction data.");for(int j=0;j<row.length;j++)if(structure.used()[j]&&Double.isInfinite(row[j]))throw new IllegalArgumentException("Infinite indicator in prediction data.");}
        if((long)data.length*options.repeats()*structure.targets().size()>1000000)throw new IllegalArgumentException("Prediction archive would be too large; reduce repeats or the dataset size.");
        int[][] assignments=assignments(data.length,options);List<Row> predictions=new ArrayList<>();List<Metric> foldMetrics=new ArrayList<>();Set<String> warnings=new LinkedHashSet<>();int done=0;
        int folds=options.scheme()==Scheme.K_FOLD?options.folds():1;
        for(int repeat=0;repeat<options.repeats();repeat++)for(int fold=1;fold<=folds;fold++) {
            interrupted();List<double[]> training=new ArrayList<>(),testing=new ArrayList<>();List<Integer> indices=new ArrayList<>();
            for(int i=0;i<data.length;i++)if(assignments[repeat][i]==fold){testing.add(data[i]);indices.add(i);}else training.add(data[i]);
            Model model;
            try {model=train(training.toArray(double[][]::new),blocks,edges,modelOptions);}
            catch(IllegalArgumentException error){throw new IllegalArgumentException("Repeat "+(repeat+1)+", fold "+fold+": "+error.getMessage()+" No partial validation was produced.",error);}
            warnings.addAll(model.warnings());double[][][] forecast=model.predict(testing.toArray(double[][]::new));List<Row> current=new ArrayList<>();
            for(int i=0;i<testing.size();i++)for(int t=0;t<structure.targets().size();t++) {
                var row=new Row(repeat+1,fold,indices.get(i)+1,t,testing.get(i)[structure.targets().get(t).column()],forecast[i][t][0],forecast[i][t][1],forecast[i][t][2]);current.add(row);predictions.add(row);
            }
            foldMetrics.addAll(metrics(current,structure.targets(),repeat+1,fold));if(progress!=null)progress.accept(++done);
        }
        List<Metric> metrics=metrics(predictions,structure.targets(),0,0);
        if(predictions.stream().anyMatch(r->Double.isNaN(r.actual())))warnings.add("Missing held-out target values were not imputed for scoring; predictions are retained and those labels are excluded from error metrics.");
        if(metrics.stream().anyMatch(m->!Double.isFinite(m.qSquaredPredict())))warnings.add("Q²predict is undefined when the mean-benchmark SSE is zero or no observed target labels are available.");
        return new Result(options,"Repeated IID splits; root scores propagated through the trained structural model; reflective indicators reconstructed in original units",
                "Within each training fold: observed means, mean imputation and population scaling; held-out predictors use training means/scales only",
                "Training indicator mean and intercept-inclusive linear regression on all root indicators; SVD minimum-norm for singular designs",
                assignments,structure.targets(),predictions,metrics,foldMetrics,new ArrayList<>(warnings));
    }
    private static List<Metric> metrics(List<Row> rows,List<Target> targets,int repeat,int fold) {
        List<Metric> result=new ArrayList<>();
        for(int t=0;t<targets.size();t++) {
            int count=0;double[] squared=new double[3],absolute=new double[3];
            for(var row:rows)if(row.target()==t&&!Double.isNaN(row.actual())) {
                count++;double[] forecast={row.pls(),row.mean(),row.linear()};for(int k=0;k<3;k++){double error=row.actual()-forecast[k];squared[k]+=error*error;absolute[k]+=Math.abs(error);}
            }
            if(Arrays.stream(squared).anyMatch(x->!Double.isFinite(x))||Arrays.stream(absolute).anyMatch(x->!Double.isFinite(x)))throw new IllegalArgumentException("Prediction errors exceeded the finite numeric range.");
            result.add(new Metric(repeat,fold,targets.get(t),count,Math.sqrt(squared[0]/count),absolute[0]/count,Math.sqrt(squared[1]/count),absolute[1]/count,Math.sqrt(squared[2]/count),absolute[2]/count,count==0||squared[1]==0?Double.NaN:1-squared[0]/squared[1]));
        }
        return result;
    }
}
