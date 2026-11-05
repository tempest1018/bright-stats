package org.brightstats.stats;

import java.util.*;
import org.apache.commons.math3.linear.*;

/** Joint least-squares component estimation by alternating coefficient and weight updates. */
public final class Gsca {
    private Gsca(){}
    public record Options(int maxIterations,double tolerance){public Options{if(maxIterations<1||maxIterations>100000||!Double.isFinite(tolerance)||tolerance<=0)throw new IllegalArgumentException("Invalid GSCA iteration settings.");}public static Options defaults(){return new Options(3000,1e-7);}}
    public record Model(List<String> indicators,List<String> components,boolean[][] weights,boolean[][] loadings,boolean[][] paths,double[][] initialWeights){
        public Model{
            indicators=List.copyOf(indicators);components=List.copyOf(components);weights=copy(weights);loadings=copy(loadings);paths=copy(paths);initialWeights=Matrices.copy(initialWeights);
            int p=indicators.size(),k=components.size();if(p==0||k==0||new HashSet<>(indicators).size()!=p||new HashSet<>(components).size()!=k||indicators.stream().anyMatch(String::isBlank)||components.stream().anyMatch(String::isBlank)||weights.length!=p||loadings.length!=p||paths.length!=k||initialWeights.length!=p)throw new IllegalArgumentException("Invalid GSCA names or matrix dimensions.");
            for(int i=0;i<p;i++){if(weights[i].length!=k||loadings[i].length!=k||initialWeights[i].length!=k)throw new IllegalArgumentException("GSCA measurement matrices must be indicators × components.");boolean used=false;for(int j=0;j<k;j++){if(!Double.isFinite(initialWeights[i][j])||!weights[i][j]&&initialWeights[i][j]!=0)throw new IllegalArgumentException("Initial GSCA weights must be finite and respect their mask.");used|=weights[i][j]||loadings[i][j];}if(!used)throw new IllegalArgumentException("Exclude unused indicators from the GSCA analysis matrix.");}
            for(int j=0;j<k;j++){if(paths[j].length!=k||paths[j][j])throw new IllegalArgumentException("GSCA paths must be square without self effects.");boolean weighted=false;for(int i=0;i<p;i++)weighted|=weights[i][j];if(!weighted)throw new IllegalArgumentException("Each component needs at least one weighted indicator.");}
        }
        @Override public boolean[][] weights(){return copy(weights);}@Override public boolean[][] loadings(){return copy(loadings);}@Override public boolean[][] paths(){return copy(paths);}@Override public double[][] initialWeights(){return Matrices.copy(initialWeights);}
    }
    public record Fit(Model model,Options options,int observations,int iterations,boolean converged,double objective,
                      double fit,double afit,double afitNominal,double measurementFit,double structuralFit,
                      int nominalParameters,int nonzeroParameters,double[] means,double[] scales,
                      double[][] weights,double[][] loadings,double[][] paths,double[][] scores,double[][] correlations,
                      double[] rSquared,List<String> warnings){
        public Fit{means=means.clone();scales=scales.clone();weights=Matrices.copy(weights);loadings=Matrices.copy(loadings);paths=Matrices.copy(paths);scores=Matrices.copy(scores);correlations=Matrices.copy(correlations);rSquared=rSquared.clone();warnings=List.copyOf(warnings);}
        @Override public double[] means(){return means.clone();}@Override public double[] scales(){return scales.clone();}@Override public double[][] weights(){return Matrices.copy(weights);}@Override public double[][] loadings(){return Matrices.copy(loadings);}@Override public double[][] paths(){return Matrices.copy(paths);}@Override public double[][] scores(){return Matrices.copy(scores);}@Override public double[][] correlations(){return Matrices.copy(correlations);}@Override public double[] rSquared(){return rSquared.clone();}
        public double[][] transform(double[][] observations){int p=Matrices.width(observations,1);if(p!=means.length)throw new IllegalArgumentException("GSCA scoring column count differs.");double[][] result=new double[observations.length][model.components().size()];for(int i=0;i<observations.length;i++)for(int a=0;a<p;a++)for(int j=0;j<result[i].length;j++)result[i][j]+=(observations[i][a]-means[a])/scales[a]*weights[a][j];return result;}
    }
    private record State(RealMatrix correlation,RealMatrix loadings,RealMatrix paths,RealMatrix a,RealMatrix residual,double objective){}
    public record Prepared(Model model,int[] columns){public Prepared{Objects.requireNonNull(model);columns=columns.clone();if(columns.length!=model.indicators().size()||Arrays.stream(columns).anyMatch(i->i<0)||Arrays.stream(columns).distinct().count()!=columns.length)throw new IllegalArgumentException("Invalid GSCA indicator mapping.");}@Override public int[] columns(){return columns.clone();}}
    public static Prepared fromBlocks(List<String> names,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges){
        if(blocks.isEmpty())throw new IllegalArgumentException("Define at least one component.");LinkedHashSet<Integer> selected=new LinkedHashSet<>();for(var block:blocks){Set<Integer> local=new HashSet<>();for(int column:block.columns()){if(column<0||column>=names.size()||!local.add(column))throw new IllegalArgumentException("Invalid or repeated GSCA block indicator.");selected.add(column);}}
        int[] columns=selected.stream().mapToInt(Integer::intValue).toArray();Map<Integer,Integer> position=new HashMap<>();for(int i=0;i<columns.length;i++)position.put(columns[i],i);int p=columns.length,k=blocks.size();boolean[][] weights=new boolean[p][k],loadings=new boolean[p][k],paths=new boolean[k][k];double[][] initial=new double[p][k];
        for(int j=0;j<k;j++){int[] indicators=blocks.get(j).columns();double[] starting=blocks.get(j).initialWeights();for(int i=0;i<indicators.length;i++){int row=position.get(indicators[i]);weights[row][j]=true;loadings[row][j]=blocks.get(j).measurementType()==PlsSem.MeasurementType.REFLECTIVE;initial[row][j]=starting[i];}}
        Set<PlsSem.Edge> unique=new HashSet<>();for(var edge:edges){if(edge.source()<0||edge.target()<0||edge.source()>=k||edge.target()>=k||edge.source()==edge.target()||!unique.add(edge))throw new IllegalArgumentException("Invalid or repeated GSCA path.");paths[edge.source()][edge.target()]=true;}
        return new Prepared(new Model(Arrays.stream(columns).mapToObj(names::get).toList(),blocks.stream().map(PlsSem.Block::name).toList(),weights,loadings,paths,initial),columns);
    }
    public static Fit fit(double[][] observations,Model model,Options options){
        int p=Matrices.width(observations,3),n=observations.length,k=model.components().size();if(p!=model.indicators().size())throw new IllegalArgumentException("GSCA observations must follow the indicator order.");
        var standardized=Matrices.standardize(observations,true,false);RealMatrix x=new Array2DRowRealMatrix(standardized.values()),r=new Array2DRowRealMatrix(Matrices.covariance(standardized.values(),false)),w=new Array2DRowRealMatrix(model.initialWeights());
        boolean[][] weightMask=model.weights(),loadingMask=model.loadings(),pathMask=model.paths();int[][] supports=new int[k][];RealMatrix[] inverses=new RealMatrix[k];
        for(int j=0;j<k;j++){final int column=j;supports[j]=java.util.stream.IntStream.range(0,p).filter(i->weightMask[i][column]).toArray();RealMatrix block=r.getSubMatrix(supports[j],supports[j]);try{inverses[j]=new CholeskyDecomposition(block,1e-9,1e-12).getSolver().getInverse();}catch(NonPositiveDefiniteMatrixException invalid){throw new IllegalArgumentException("Dependent GSCA weight indicators for "+model.components().get(j));}normalize(w,r,j,supports[j]);}
        State state=state(r,w,loadingMask,pathMask);double previous=state.objective()*n;boolean converged=false;int iterations=0;List<String> warnings=new ArrayList<>();
        for(int iteration=0;iteration<options.maxIterations();iteration++){
            Matrices.interrupted();RealMatrix a=state.a();
            for(int j=0;j<k;j++){
                Matrices.interrupted();if(supports[j].length==1){w.setEntry(supports[j][0],j,1);continue;}
                RealVector h=a.getRowVector(j).mapMultiply(-1);h.addToEntry(p+j,1);RealMatrix residual=combined(w,p,k).subtract(w.multiply(a));
                RealMatrix without=residual.subtract(w.getColumnVector(j).outerProduct(h));RealVector target=r.multiply(without).operate(h).mapMultiply(-1);double[] selected=new double[supports[j].length];for(int i=0;i<selected.length;i++)selected[i]=target.getEntry(supports[j][i]);RealVector update=inverses[j].operate(new ArrayRealVector(selected));
                for(int i=0;i<selected.length;i++)w.setEntry(supports[j][i],j,update.getEntry(i));normalize(w,r,j,supports[j]);
            }
            state=state(r,w,loadingMask,pathMask);iterations=iteration+1;double current=n*state.objective(),improvement=previous-current;
            if(improvement< -1e-8*Math.max(1,Math.abs(previous))){warnings.add("GSCA objective increased beyond rounding tolerance; convergence was not established.");break;}
            if(Math.abs(improvement)<=options.tolerance()){converged=true;break;}previous=current;
        }
        if(!converged)warnings.add("GSCA did not establish convergence within the requested limit; results are provisional.");
        double measurement=0,structural=0;RealMatrix error=state.residual(),weighted=r.multiply(error);for(int j=0;j<p+k;j++){double value=error.getColumnVector(j).dotProduct(weighted.getColumnVector(j));if(j<p)measurement+=value;else structural+=value;}
        double fit=1-state.objective()/(p+k),fitM=1-measurement/p,fitS=count(pathMask)==0?0:1-structural/k;int nominal=count(weightMask)+count(loadingMask)+count(pathMask),nonzero=0;for(double[] row:w.getData())for(double value:row)if(Math.abs(value)>1e-10)nonzero++;for(double[] row:state.a().getData())for(double value:row)if(Math.abs(value)>1e-10)nonzero++;
        double size=(double)n*p,afit=size>nonzero?1-(1-fit)*size/(size-nonzero):Double.NaN,afitNominal=size>nominal?1-(1-fit)*size/(size-nominal):Double.NaN;if(size<=nominal)warnings.add("Too few indicator observations for the nominal AFIT parameter adjustment.");
        double[] rSquared=new double[k];for(int j=0;j<k;j++){boolean parent=false;for(int i=0;i<k;i++)parent|=pathMask[i][j];double value=error.getColumnVector(p+j).dotProduct(weighted.getColumnVector(p+j));rSquared[j]=parent?1-value:Double.NaN;}
        return new Fit(model,options,n,iterations,converged,n*state.objective(),fit,afit,afitNominal,fitM,fitS,nominal,nonzero,standardized.means(),standardized.scales(),w.getData(),state.loadings().getData(),state.paths().getData(),x.multiply(w).getData(),state.correlation().getData(),rSquared,warnings);
    }
    private static State state(RealMatrix r,RealMatrix w,boolean[][] measurement,boolean[][] paths){
        int p=w.getRowDimension(),k=w.getColumnDimension();RealMatrix correlation=w.transpose().multiply(r).multiply(w),cross=r.multiply(w),c=new Array2DRowRealMatrix(p,k),b=new Array2DRowRealMatrix(k,k);
        for(int i=0;i<p;i++){final int row=i;int[] factors=java.util.stream.IntStream.range(0,k).filter(j->measurement[row][j]).toArray();if(factors.length>0){double[] target=new double[factors.length];for(int j=0;j<factors.length;j++)target[j]=cross.getEntry(i,factors[j]);RealVector coefficient=solve(correlation.getSubMatrix(factors,factors),target);for(int j=0;j<factors.length;j++)c.setEntry(i,factors[j],coefficient.getEntry(j));}}
        for(int j=0;j<k;j++){final int column=j;int[] parents=java.util.stream.IntStream.range(0,k).filter(i->paths[i][column]).toArray();if(parents.length>0){double[] target=new double[parents.length];for(int i=0;i<parents.length;i++)target[i]=correlation.getEntry(parents[i],j);RealVector coefficient=solve(correlation.getSubMatrix(parents,parents),target);for(int i=0;i<parents.length;i++)b.setEntry(parents[i],j,coefficient.getEntry(i));}}
        RealMatrix a=new Array2DRowRealMatrix(k,p+k);a.setSubMatrix(c.transpose().getData(),0,0);a.setSubMatrix(b.getData(),0,p);RealMatrix residual=combined(w,p,k).subtract(w.multiply(a));double objective=residual.transpose().multiply(r).multiply(residual).getTrace();if(!Double.isFinite(objective))throw new IllegalArgumentException("GSCA objective exceeded the finite numerical range.");return new State(correlation,c,b,a,residual,Math.max(0,objective));
    }
    private static RealVector solve(RealMatrix matrix,double[] target){try{return new CholeskyDecomposition(matrix,1e-9,1e-12).getSolver().solve(new ArrayRealVector(target));}catch(NonPositiveDefiniteMatrixException invalid){throw new IllegalArgumentException("GSCA equation has dependent component predictors.");}}
    private static RealMatrix combined(RealMatrix weights,int p,int k){RealMatrix result=new Array2DRowRealMatrix(p,p+k);result.setSubMatrix(MatrixUtils.createRealIdentityMatrix(p).getData(),0,0);result.setSubMatrix(weights.getData(),0,p);return result;}
    private static void normalize(RealMatrix w,RealMatrix r,int column,int[] support){RealVector values=w.getColumnVector(column);double variance=values.dotProduct(r.operate(values));if(!(variance>1e-24)||!Double.isFinite(variance))throw new IllegalArgumentException("GSCA weight update has no identified, nonconstant direction.");for(int i:support)w.setEntry(i,column,w.getEntry(i,column)/Math.sqrt(variance));}
    private static boolean[][] copy(boolean[][] matrix){return Arrays.stream(matrix).map(boolean[]::clone).toArray(boolean[][]::new);}
    private static int count(boolean[][] matrix){int result=0;for(boolean[] row:matrix)for(boolean value:row)if(value)result++;return result;}
}
