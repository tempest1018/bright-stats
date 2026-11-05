package org.brightstats.stats;

import java.util.*;
import org.apache.commons.math3.linear.*;

/** Observed-pattern normal likelihood shared by incomplete-data SEM extensions. */
final class SemObservedLikelihood {
    record Pattern(int[] columns,int rows,double[] mean,double[][] covariance){}
    record Probe(double value,double[] gradient){}
    final CovarianceSem.Work ram;
    final double[][] data;
    final int observations,emptyRows,width;
    final List<Pattern> patterns;
    SemObservedLikelihood(double[][] input,SemModel.Model model){
        if(input==null||input.length<3||input.length>20000||model.parameters().size()>300||model.variables().size()>128)throw new IllegalArgumentException("Observed-data SEM supports 3–20,000 rows and at most 300 free parameters.");
        width=model.observed().length;if(width<1||width>64||(long)input.length*width>1000000)throw new IllegalArgumentException("Observed-data SEM supports 1–64 observed variables and at most one million selected cells.");
        double[] mean=new double[width],sum=new double[width];int[] count=new int[width];int empty=0;
        for(var row:input){if(row==null||row.length!=width)throw new IllegalArgumentException("SEM rows must match the observed-variable order.");boolean present=false;for(int j=0;j<width;j++){double value=row[j];if(Double.isInfinite(value))throw new IllegalArgumentException("Infinity is not a missing-value marker.");if(Double.isNaN(value))continue;present=true;count[j]++;double delta=value-mean[j];mean[j]+=delta/count[j];sum[j]+=delta*(value-mean[j]);}if(!present)empty++;}
        emptyRows=empty;observations=input.length-empty;if(observations<3)throw new IllegalArgumentException("At least three rows must contain observations.");double[][] diagonal=new double[width][width];
        for(int j=0;j<width;j++){if(count[j]<3||!(sum[j]>0)||!Double.isFinite(sum[j]))throw new IllegalArgumentException("Each observed variable needs at least three finite values and positive variance (variable "+j+").");diagonal[j][j]=sum[j]/count[j];}
        var options=new CovarianceSem.Options(1000,1e-8,1e-12,CovarianceSem.Likelihood.NORMAL,true,.95,CovarianceSem.Information.OBSERVED);ram=new CovarianceSem.Work(diagonal,mean,model,options);data=Matrices.copy(input);
        LinkedHashMap<String,List<double[]>> groups=new LinkedHashMap<>();Map<String,int[]> indices=new HashMap<>();
        for(var row:data){int[] cols=java.util.stream.IntStream.range(0,width).filter(j->Double.isFinite(row[j])).toArray();if(cols.length==0)continue;String key=Arrays.toString(cols);indices.putIfAbsent(key,cols);groups.computeIfAbsent(key,k->new ArrayList<>()).add(row);}
        if(groups.size()>1024)throw new IllegalArgumentException("At most 1,024 distinct observed missingness patterns are supported.");List<Pattern> built=new ArrayList<>();
        for(var entry:groups.entrySet()){int[] cols=indices.get(entry.getKey());var rows=entry.getValue();double[] m=new double[cols.length];double[][] ss=new double[cols.length][cols.length];int n=0;for(var row:rows){n++;double[] delta=new double[cols.length];for(int j=0;j<cols.length;j++){double value=row[cols[j]]/ram.scales[cols[j]];delta[j]=value-m[j];m[j]+=delta[j]/n;}for(int a=0;a<cols.length;a++)for(int b=0;b<cols.length;b++)ss[a][b]+=delta[a]*(row[cols[b]]/ram.scales[cols[b]]-m[b]);}for(var row:ss)for(int j=0;j<row.length;j++)row[j]/=n;built.add(new Pattern(cols,n,m,ss));}patterns=List.copyOf(built);
    }
    CovarianceSem.State state(double[] theta){var value=ram.state(theta);return value!=null&&CovarianceSem.properDisturbance(value.disturbance().getData(),value.fullCovariance().getData())?value:null;}
    Bfgs.Evaluation evaluate(double[] theta){var s=state(theta);int q=theta.length;if(s==null)return new Bfgs.Evaluation(Double.POSITIVE_INFINITY,new double[q]);double value=0;double[] gradient=new double[q];RealMatrix sigma=ram.normalize(s.covariance());RealVector mu=ram.normalizeMean(s.mean());
        try{for(var pattern:patterns){Matrices.interrupted();int[] c=pattern.columns();RealMatrix v=sigma.getSubMatrix(c,c);var cholesky=new CholeskyDecomposition(v,1e-9,1e-12);RealMatrix inverse=cholesky.getSolver().getInverse();RealVector difference=new ArrayRealVector(pattern.mean()).subtract(select(mu,c));RealMatrix combined=new Array2DRowRealMatrix(pattern.covariance()).add(difference.outerProduct(difference));double jacobian=0;for(int col:c)jacobian+=Math.log(ram.scales[col]);value+=pattern.rows()*(.5*(c.length*Math.log(2*Math.PI)+logDet(cholesky)+inverse.multiply(combined).getTrace())+jacobian);RealMatrix weight=inverse.subtract(inverse.multiply(combined).multiply(inverse));RealVector meanScore=inverse.operate(difference).mapMultiply(-2);for(int j=0;j<q;j++)gradient[j]+=.5*pattern.rows()*(weight.multiply(s.covarianceDerivatives().get(j).getSubMatrix(c,c)).getTrace()+meanScore.dotProduct(select(s.meanDerivatives().get(j),c)));}
        }catch(NonPositiveDefiniteMatrixException|NonSymmetricMatrixException invalid){return new Bfgs.Evaluation(Double.POSITIVE_INFINITY,new double[q]);}
        for(int j=0;j<q;j++)gradient[j]/=observations;return new Bfgs.Evaluation(value/observations,gradient);
    }
    double[][] caseScores(double[] theta){var s=state(theta);if(s==null)throw new IllegalArgumentException("Inadmissible likelihood state.");double[][] scores=new double[data.length][theta.length];RealMatrix sigma=ram.normalize(s.covariance());RealVector mu=ram.normalizeMean(s.mean());Map<String,RealMatrix> inverses=new HashMap<>();
        for(int row=0;row<data.length;row++){Matrices.interrupted();final double[] values=data[row];int[] c=java.util.stream.IntStream.range(0,width).filter(j->Double.isFinite(values[j])).toArray();if(c.length==0)continue;RealMatrix inverse=inverses.computeIfAbsent(Arrays.toString(c),key->new CholeskyDecomposition(sigma.getSubMatrix(c,c),1e-9,1e-12).getSolver().getInverse());double[] d=new double[c.length];for(int j=0;j<c.length;j++)d[j]=values[c[j]]/ram.scales[c[j]]-mu.getEntry(c[j]);RealVector delta=new ArrayRealVector(d),weighted=inverse.operate(delta);RealMatrix weight=inverse.subtract(weighted.outerProduct(weighted));for(int j=0;j<theta.length;j++)scores[row][j]=.5*weight.multiply(s.covarianceDerivatives().get(j).getSubMatrix(c,c)).getTrace()-weighted.dotProduct(select(s.meanDerivatives().get(j),c));}return scores;
    }
    static RealVector select(RealVector vector,int[] columns){double[] out=new double[columns.length];for(int j=0;j<out.length;j++)out[j]=vector.getEntry(columns[j]);return new ArrayRealVector(out,false);}
    static double logDet(CholeskyDecomposition cholesky){RealMatrix l=cholesky.getL();double out=0;for(int j=0;j<l.getRowDimension();j++)out+=2*Math.log(l.getEntry(j,j));return out;}
    double[] initial(){return ram.model.parameters().stream().mapToDouble(p->p.start()/p.scale()).toArray();}
    double[] lower(){return ram.model.parameters().stream().mapToDouble(p->p.lower()==null?Double.NEGATIVE_INFINITY:p.lower()/p.scale()).toArray();}
    double[] upper(){return ram.model.parameters().stream().mapToDouble(p->p.upper()==null?Double.POSITIVE_INFINITY:p.upper()/p.scale()).toArray();}
}
