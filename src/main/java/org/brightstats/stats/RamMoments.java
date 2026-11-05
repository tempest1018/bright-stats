package org.brightstats.stats;

import java.util.*;
import org.apache.commons.math3.linear.*;

/** Raw-unit RAM moments/derivatives, allowing positive-semidefinite components. */
final class RamMoments {
    record State(RealMatrix covariance,RealVector mean,List<RealMatrix> covarianceDerivatives,List<RealVector> meanDerivatives){}
    private RamMoments(){}
    static State state(SemModel.Model model,double[] theta){
        Matrices.interrupted();int size=model.variables().size(),q=model.parameters().size();if(theta.length!=q)throw new IllegalArgumentException("RAM parameter dimension mismatch.");double[][] a=model.directed(),s=model.covariance();double[] m=model.intercepts();
        for(int j=0;j<q;j++){var parameter=model.parameters().get(j);double value=theta[j]*parameter.scale();if(!Double.isFinite(value)||parameter.lower()!=null&&value<parameter.lower()||parameter.upper()!=null&&value>parameter.upper())return null;for(var l:parameter.locations())switch(l.kind()){case DIRECTED->a[l.row()][l.column()]=value;case COVARIANCE->{s[l.row()][l.column()]=value;s[l.column()][l.row()]=value;}case INTERCEPT->m[l.row()]=value;}}
        try{
            RealMatrix h=new LUDecomposition(MatrixUtils.createRealIdentityMatrix(size).subtract(new Array2DRowRealMatrix(a)),1e-12).getSolver().getInverse(),disturbance=new Array2DRowRealMatrix(s),full=h.multiply(disturbance).multiply(h.transpose());if(!CovarianceSem.properDisturbance(s,full.getData()))return null;RealVector intercept=new ArrayRealVector(m),fullMean=h.operate(intercept);int[] observed=model.observed();List<RealMatrix> dc=new ArrayList<>();List<RealVector> dm=new ArrayList<>();
            for(var parameter:model.parameters()){
                double[][] da=new double[size][size],ds=new double[size][size];double[] di=new double[size];for(var l:parameter.locations())switch(l.kind()){case DIRECTED->da[l.row()][l.column()]+=parameter.scale();case COVARIANCE->{ds[l.row()][l.column()]+=parameter.scale();if(l.row()!=l.column())ds[l.column()][l.row()]+=parameter.scale();}case INTERCEPT->di[l.row()]+=parameter.scale();}
                RealMatrix dh=h.multiply(new Array2DRowRealMatrix(da)).multiply(h);dc.add(dh.multiply(disturbance).multiply(h.transpose()).add(h.multiply(new Array2DRowRealMatrix(ds)).multiply(h.transpose())).add(h.multiply(disturbance).multiply(dh.transpose())).getSubMatrix(observed,observed));dm.add(SemObservedLikelihood.select(dh.operate(intercept).add(h.operate(new ArrayRealVector(di))),observed));
            }
            RealMatrix covariance=full.getSubMatrix(observed,observed);RealVector mean=SemObservedLikelihood.select(fullMean,observed);if(!Arrays.stream(covariance.getData()).flatMapToDouble(Arrays::stream).allMatch(Double::isFinite)||!Arrays.stream(mean.toArray()).allMatch(Double::isFinite))return null;return new State(covariance,mean,List.copyOf(dc),List.copyOf(dm));
        }catch(SingularMatrixException invalid){return null;}
    }
    static double[] initial(SemModel.Model model){return model.parameters().stream().mapToDouble(p->p.start()/p.scale()).toArray();}
    static double[] lower(SemModel.Model model){return model.parameters().stream().mapToDouble(p->p.lower()==null?Double.NEGATIVE_INFINITY:p.lower()/p.scale()).toArray();}
    static double[] upper(SemModel.Model model){return model.parameters().stream().mapToDouble(p->p.upper()==null?Double.POSITIVE_INFINITY:p.upper()/p.scale()).toArray();}
}
