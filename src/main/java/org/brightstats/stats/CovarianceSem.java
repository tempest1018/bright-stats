package org.brightstats.stats;

import java.util.*;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.special.Gamma;

/** Normal-theory ML estimation of explicit RAM covariance/mean models. */
public final class CovarianceSem {
    private CovarianceSem() {}
    public enum Likelihood { NORMAL, WISHART }
    public enum Information { EXPECTED, OBSERVED }
    public record Options(int maxIterations,double gradientTolerance,double functionTolerance,
                          Likelihood likelihood,boolean meanStructure,double confidence,Information information) {
        public Options {Objects.requireNonNull(likelihood);if(information==null)information=Information.EXPECTED;Inference.confidence(confidence);if(maxIterations<1||maxIterations>100000||!Double.isFinite(gradientTolerance)||gradientTolerance<=0||!Double.isFinite(functionTolerance)||functionTolerance<=0)throw new IllegalArgumentException("Invalid SEM optimization settings.");}
        public Options(int maxIterations,double gradientTolerance,double functionTolerance,Likelihood likelihood,boolean meanStructure,double confidence){this(maxIterations,gradientTolerance,functionTolerance,likelihood,meanStructure,confidence,Information.EXPECTED);}
        public static Options defaults(){return new Options(1000,1e-8,1e-12,Likelihood.NORMAL,false,.95,Information.OBSERVED);}
    }
    public record Diagnostics(double baselineChiSquare,int baselineDegreesOfFreedom,double chiSquareOverDf,
                              double gfi,double agfi,double pgfi,double nfi,double boundedTli,
                              double rmseaLow90,double rmseaHigh90,double discrepancyBic) {}
    public record Term(String parameter,double estimate,double standardError,double z,double pValue,double lower,double upper) {}
    public record Fit(Options options,SemModel.Model model,int observations,int iterations,boolean converged,
                      String convergenceReason,boolean identified,int informationRank,double discrepancy,
                      double chiSquare,int degreesOfFreedom,double chiSquarePValue,double cfi,double tli,
                      double rmsea,double srmr,double logLikelihood,double aic,double bic,double discrepancyAic,Diagnostics diagnostics,
                      double[] estimates,double[][] parameterCovariance,List<Term> terms,double[][] sampleCovariance,
                      double[] sampleMeans,double[][] impliedCovariance,double[] impliedMeans,double[][] directed,
                      double[][] disturbanceCovariance,double[][] fullCovariance,double[] fullMeans,
                      double[][] standardizedDirected,List<String> warnings) {
        public Fit {estimates=estimates.clone();parameterCovariance=Matrices.copy(parameterCovariance);terms=List.copyOf(terms);sampleCovariance=Matrices.copy(sampleCovariance);sampleMeans=sampleMeans.clone();impliedCovariance=Matrices.copy(impliedCovariance);impliedMeans=impliedMeans.clone();directed=Matrices.copy(directed);disturbanceCovariance=Matrices.copy(disturbanceCovariance);fullCovariance=Matrices.copy(fullCovariance);fullMeans=fullMeans.clone();standardizedDirected=Matrices.copy(standardizedDirected);warnings=List.copyOf(warnings);}
        @Override public double[] estimates(){return estimates.clone();}@Override public double[][] parameterCovariance(){return Matrices.copy(parameterCovariance);}@Override public double[][] sampleCovariance(){return Matrices.copy(sampleCovariance);}@Override public double[] sampleMeans(){return sampleMeans.clone();}@Override public double[][] impliedCovariance(){return Matrices.copy(impliedCovariance);}@Override public double[] impliedMeans(){return impliedMeans.clone();}@Override public double[][] directed(){return Matrices.copy(directed);}@Override public double[][] disturbanceCovariance(){return Matrices.copy(disturbanceCovariance);}@Override public double[][] fullCovariance(){return Matrices.copy(fullCovariance);}@Override public double[] fullMeans(){return fullMeans.clone();}@Override public double[][] standardizedDirected(){return Matrices.copy(standardizedDirected);}
    }
    record State(double value,double[] gradient,RealMatrix directed,RealMatrix disturbance,
                         RealMatrix fullCovariance,RealVector fullMean,RealMatrix covariance,RealVector mean,
                         RealMatrix inverse,List<RealMatrix> covarianceDerivatives,List<RealVector> meanDerivatives) {}

    public static Fit fit(double[][] data,SemModel.Model model,Options options){
        int width=Matrices.width(data,3);if(width!=model.observed().length)throw new IllegalArgumentException("SEM data columns must follow the model's observed-variable order.");
        var centred=Matrices.standardize(data,false,options.likelihood()==Likelihood.WISHART);
        return fitCovariance(Matrices.covariance(centred.values(),options.likelihood()==Likelihood.WISHART),centred.means(),data.length,model,options);
    }
    /** Covariance must already use the divisor selected by likelihood: N for NORMAL, N−1 for WISHART. */
    static boolean properDisturbance(double[][] disturbance,double[][] fullCovariance){int n=disturbance.length;double[] scales=new double[n];for(int i=0;i<n;i++){if(disturbance[i][i]<0)return false;scales[i]=Math.sqrt(Math.max(Math.abs(disturbance[i][i]),Math.abs(fullCovariance[i][i])));}double[][] normalized=new double[n][n];for(int i=0;i<n;i++)for(int j=0;j<n;j++){if(scales[i]==0||scales[j]==0){if(disturbance[i][j]!=0)return false;}else{normalized[i][j]=disturbance[i][j]/scales[i]/scales[j];if(!Double.isFinite(normalized[i][j]))return false;}}return Matrices.minEigenvalue(normalized)>=-1e-8;}
    public static Fit fitCovariance(double[][] covariance,double[] means,int observations,SemModel.Model model,Options options){
        Objects.requireNonNull(options);Objects.requireNonNull(model);int p=Matrices.width(covariance,1),q=model.parameters().size();
        if(covariance.length!=p||p!=model.observed().length||means.length!=p||observations<3||!Arrays.stream(means).allMatch(Double::isFinite))throw new IllegalArgumentException("Invalid SEM covariance/mean dimensions or sample count.");
        if(!options.meanStructure()&&(Arrays.stream(model.intercepts()).anyMatch(v->v!=0)||model.parameters().stream().flatMap(v->v.locations().stream()).anyMatch(v->v.kind()==SemModel.Kind.INTERCEPT)))throw new IllegalArgumentException("Enable the mean structure before specifying SEM intercepts.");
        for(int i=0;i<p;i++)for(int j=0;j<i;j++)if(Math.abs(covariance[i][j]-covariance[j][i])>1e-10*Math.max(1,Math.abs(covariance[i][j])))throw new IllegalArgumentException("Sample covariance must be symmetric.");
        int moments=p*(p+1)/2+(options.meanStructure()?p:0),df=moments-q;if(df<0)throw new IllegalArgumentException("SEM is underidentified: more free parameters than observed moments.");
        var work=new Work(covariance,means,model,options);double[] initial=new double[q],lower=new double[q],upper=new double[q];
        for(int j=0;j<q;j++){var parameter=model.parameters().get(j);initial[j]=parameter.start()/parameter.scale();lower[j]=parameter.lower()==null?Double.NEGATIVE_INFINITY:parameter.lower()/parameter.scale();upper[j]=parameter.upper()==null?Double.POSITIVE_INFINITY:parameter.upper()/parameter.scale();}
        var optimum=Bfgs.minimize(work::evaluate,initial,lower,upper,options.maxIterations(),options.gradientTolerance(),options.functionTolerance());
        var state=work.state(optimum.point());if(state==null)throw new IllegalArgumentException("SEM final state is numerically inadmissible.");
        double effectiveN=options.likelihood()==Likelihood.NORMAL?observations:observations-1;double[][] information=new double[q][q];
        List<RealMatrix> weighted=new ArrayList<>();for(var derivative:state.covarianceDerivatives())weighted.add(state.inverse().multiply(derivative));
        for(int i=0;i<q;i++)for(int j=0;j<=i;j++){
            double value=effectiveN*.5*weighted.get(i).multiply(weighted.get(j)).getTrace();
            if(options.meanStructure())value+=effectiveN*state.meanDerivatives().get(i).dotProduct(state.inverse().operate(state.meanDerivatives().get(j)));
            information[i][j]=information[j][i]=value;
        }
        if(options.information()==Information.OBSERVED){
            double[] optimumPoint=optimum.point();double[][] derivative=new double[q][q];
            for(int j=0;j<q;j++){double step=1e-4*Math.max(1,Math.abs(optimumPoint[j]));double[] high=optimumPoint.clone(),low=optimumPoint.clone();high[j]+=step;low[j]-=step;State plus=work.state(high),minus=work.state(low);
                for(int i=0;i<q;i++)derivative[i][j]=plus==null||minus==null?Double.NaN:effectiveN*.5*(plus.gradient()[i]-minus.gradient()[i])/(2*step);
            }
            for(int i=0;i<q;i++)for(int j=0;j<q;j++)information[i][j]=(derivative[i][j]+derivative[j][i])*.5;
        }
        boolean boundary=false;double[] finalPoint=optimum.point();for(int j=0;j<q;j++)if(Math.abs(finalPoint[j]-lower[j])<1e-8||Math.abs(finalPoint[j]-upper[j])<1e-8)boundary=true;
        int rank=0;double[][] parameterCovariance=new double[q][q];for(double[] row:parameterCovariance)Arrays.fill(row,Double.NaN);List<String> warnings=new ArrayList<>();
        if(q>0&&Arrays.stream(information).flatMapToDouble(Arrays::stream).allMatch(Double::isFinite)){var decomposition=new SingularValueDecomposition(new Array2DRowRealMatrix(information));double[] singular=decomposition.getSingularValues();double threshold=singular[0]*1e-10;for(double value:singular)if(value>threshold)rank++;
            if(Matrices.minEigenvalue(information)<=threshold)rank=Math.min(rank,q-1);
            if(rank==q&&optimum.converged()&&!boundary){
                RealMatrix inverse=decomposition.getSolver().getInverse();for(int i=0;i<q;i++)for(int j=0;j<q;j++)parameterCovariance[i][j]=inverse.getEntry(i,j)*model.parameters().get(i).scale()*model.parameters().get(j).scale();
            }
        }
        boolean identified=rank==q;if(!identified)warnings.add("Final "+options.information()+" information is singular, indefinite, numerically unavailable or weakly identified; standard errors are unavailable.");
        if(!optimum.converged())warnings.add("Convergence was not established; estimates and model-fit summaries are provisional; Wald inference is unavailable.");
        if(boundary)warnings.add("A parameter is on a configured bound. Ordinary Wald and chi-square reference distributions need not apply; inferential p-values are unavailable.");
        double[] estimates=new double[q];List<Term> terms=new ArrayList<>();double critical=new NormalDistribution().inverseCumulativeProbability((1+options.confidence())/2);
        for(int j=0;j<q;j++){estimates[j]=optimum.point()[j]*model.parameters().get(j).scale();double se=Math.sqrt(parameterCovariance[j][j]),z=Inference.ratio(estimates[j],se);terms.add(new Term(model.parameters().get(j).name(),estimates[j],se,z,Inference.normalTwoSided(z),estimates[j]-critical*se,estimates[j]+critical*se));}
        RealMatrix implied=state.covariance();double discrepancy=Math.max(0,state.value()),chi=effectiveN*discrepancy;
        double baseline=0;for(int i=0;i<p;i++)baseline+=Math.log(work.empirical.getEntry(i,i));baseline-=work.logDetEmpirical;
        double baselineChi=effectiveN*Math.max(0,baseline);int baselineDf=p*(p-1)/2;
        double excess=Math.max(0,chi-df),denominator=Math.max(excess,baselineChi-baselineDf),cfi=denominator>0?1-excess/denominator:excess==0?1:Double.NaN;
        double tli=df>0&&baselineDf>0&&baselineChi/baselineDf!=1?(baselineChi/baselineDf-chi/df)/(baselineChi/baselineDf-1):Double.NaN;
        double rmsea=df>0?Math.sqrt(Math.max(0,(chi-df)/(effectiveN*df))):Double.NaN;
        RealMatrix product=state.inverse().multiply(work.empirical),deviation=product.subtract(MatrixUtils.createRealIdentityMatrix(p));double gfi=1-deviation.multiply(deviation).getTrace()/product.multiply(product).getTrace();
        double low90=Double.NaN,high90=Double.NaN;if(df>0){try{low90=Math.sqrt(NoncentralChiSquare.noncentrality(chi,df,.95)/(effectiveN*df));high90=Math.sqrt(NoncentralChiSquare.noncentrality(chi,df,.05)/(effectiveN*df));}catch(IllegalArgumentException numeric){warnings.add(numeric.getMessage());}}
        var diagnostics=new Diagnostics(baselineChi,baselineDf,df>0?chi/df:Double.NaN,gfi,df>0?1-p*(p+1)/(2.0*df)*(1-gfi):Double.NaN,df/(p*(p+1)/2.0)*gfi,baselineChi>0?1-chi/baselineChi:Double.NaN,Double.isFinite(tli)?Math.max(0,Math.min(1,tli)):Double.NaN,low90,high90,chi+Math.log(observations)*q);
        double residualSum=0;for(int i=0;i<p;i++)for(int j=0;j<=i;j++){double residual=(covariance[i][j]-implied.getEntry(i,j))/Math.sqrt(covariance[i][i]*covariance[j][j]);residualSum+=residual*residual;}double srmr=Math.sqrt(residualSum/(p*(p+1)/2.0));
        // This Gaussian log likelihood is reported separately from discrepancy-based AIC.
        double gaussianScale=options.likelihood()==Likelihood.NORMAL?1:(observations-1)/(double)observations;
        double trace=work.empirical.scalarMultiply(gaussianScale).multiply(state.inverse()).getTrace();
        if(options.meanStructure()){RealVector difference=work.normalizedMeans.subtract(work.normalizeMean(state.mean()));trace+=difference.dotProduct(state.inverse().operate(difference));}
        double logDet=work.logDet(work.normalize(implied));for(double scale:work.scales)logDet+=2*Math.log(scale);double logLikelihood=-observations*.5*(p*Math.log(2*Math.PI)+logDet+trace);
        int size=model.variables().size();double[][] standardized=new double[size][size];for(int i=0;i<size;i++)for(int j=0;j<size;j++){double vi=state.fullCovariance().getEntry(i,i),vj=state.fullCovariance().getEntry(j,j);standardized[i][j]=vi>0&&vj>0?state.directed().getEntry(i,j)*Math.sqrt(vj/vi):Double.NaN;}
        boolean improper=false;for(int i=0;i<size;i++)if(state.disturbance().getEntry(i,i)<0)improper=true;
        if(improper)warnings.add("Improper solution: at least one disturbance/error variance is negative (Heywood case). Inspect the model before interpretation.");
        if(!properDisturbance(state.disturbance().getData(),state.fullCovariance().getData()))warnings.add("Disturbance covariance is not positive semidefinite; a finite observed likelihood alone does not establish an admissible SEM.");
        if(!options.meanStructure())warnings.add("Covariance-only estimation centres the data; observed means are not tested as model moments.");
        return new Fit(options,model,observations,optimum.iterations(),optimum.converged(),optimum.reason(),identified,rank,discrepancy,chi,df,df>0&&!boundary&&optimum.converged()?Gamma.regularizedGammaQ(df*.5,chi*.5):Double.NaN,cfi,tli,rmsea,srmr,logLikelihood,-2*logLikelihood+2*q,-2*logLikelihood+Math.log(observations)*q,chi+2*q,diagnostics,estimates,parameterCovariance,terms,covariance,means,implied.getData(),state.mean().toArray(),state.directed().getData(),state.disturbance().getData(),state.fullCovariance().getData(),state.fullMean().toArray(),standardized,warnings);
    }

    static final class Work {
        final SemModel.Model model;final Options options;final int[] observed;final int size,p;final double[] scales;
        final RealMatrix empirical;final RealVector normalizedMeans;final double logDetEmpirical;
        Work(double[][] covariance,double[] means,SemModel.Model model,Options options){
            this.model=model;this.options=options;observed=model.observed();size=model.variables().size();p=observed.length;scales=new double[p];for(int i=0;i<p;i++){scales[i]=Math.sqrt(covariance[i][i]);if(!(scales[i]>0)||!Double.isFinite(scales[i]))throw new IllegalArgumentException("SEM requires positive finite observed variances.");}
            empirical=normalize(new Array2DRowRealMatrix(covariance));normalizedMeans=new ArrayRealVector(means);for(int i=0;i<p;i++)normalizedMeans.setEntry(i,means[i]/scales[i]);logDetEmpirical=logDet(empirical);
        }
        Bfgs.Evaluation evaluate(double[] point){State state=state(point);return state==null?new Bfgs.Evaluation(Double.POSITIVE_INFINITY,new double[point.length]):new Bfgs.Evaluation(state.value(),state.gradient());}
        State state(double[] point){
            Matrices.interrupted();int q=model.parameters().size();double[][] a=model.directed(),s=model.covariance();double[] m=model.intercepts();
            for(int j=0;j<q;j++){double value=point[j]*model.parameters().get(j).scale();if(!Double.isFinite(value))return null;for(var location:model.parameters().get(j).locations())switch(location.kind()){case DIRECTED->a[location.row()][location.column()]=value;case COVARIANCE->{s[location.row()][location.column()]=value;s[location.column()][location.row()]=value;}case INTERCEPT->m[location.row()]=value;}}
            RealMatrix directed=new Array2DRowRealMatrix(a),disturbance=new Array2DRowRealMatrix(s),h;
            try{h=new LUDecomposition(MatrixUtils.createRealIdentityMatrix(size).subtract(directed),1e-12).getSolver().getInverse();}catch(SingularMatrixException invalid){return null;}
            RealMatrix full=h.multiply(disturbance).multiply(h.transpose()),covariance=full.getSubMatrix(observed,observed),normalized=normalize(covariance);RealVector fullMean=h.operate(new ArrayRealVector(m)),mean=select(fullMean);
            CholeskyDecomposition decomposition;try{decomposition=new CholeskyDecomposition(normalized,1e-9,1e-12);}catch(NonPositiveDefiniteMatrixException|NonSymmetricMatrixException invalid){return null;}
            RealMatrix inverse=decomposition.getSolver().getInverse();RealVector difference=options.meanStructure()?normalizedMeans.subtract(normalizeMean(mean)):new ArrayRealVector(p);
            RealMatrix combined=empirical.add(difference.outerProduct(difference));double value=logDet(decomposition)+combined.multiply(inverse).getTrace()-logDetEmpirical-p;
            RealMatrix weight=inverse.subtract(inverse.multiply(combined).multiply(inverse));RealVector meanGradient=inverse.operate(difference).mapMultiply(-2);
            List<RealMatrix> derivatives=new ArrayList<>();List<RealVector> meanDerivatives=new ArrayList<>();double[] gradient=new double[q];
            for(int j=0;j<q;j++){
                Matrices.interrupted();double[][] da=new double[size][size],ds=new double[size][size];double[] dm=new double[size];double scale=model.parameters().get(j).scale();
                for(var location:model.parameters().get(j).locations())switch(location.kind()){case DIRECTED->da[location.row()][location.column()]+=scale;case COVARIANCE->{ds[location.row()][location.column()]+=scale;if(location.row()!=location.column())ds[location.column()][location.row()]+=scale;}case INTERCEPT->dm[location.row()]+=scale;}
                RealMatrix dh=h.multiply(new Array2DRowRealMatrix(da)).multiply(h);
                RealMatrix derivative=dh.multiply(disturbance).multiply(h.transpose()).add(h.multiply(new Array2DRowRealMatrix(ds)).multiply(h.transpose())).add(h.multiply(disturbance).multiply(dh.transpose()));
                RealMatrix dc=normalize(derivative.getSubMatrix(observed,observed));RealVector dmu=normalizeMean(select(dh.operate(new ArrayRealVector(m)).add(h.operate(new ArrayRealVector(dm)))));
                derivatives.add(dc);meanDerivatives.add(dmu);gradient[j]=weight.multiply(dc).getTrace()+(options.meanStructure()?meanGradient.dotProduct(dmu):0);
            }
            if(!Double.isFinite(value)||!Arrays.stream(gradient).allMatch(Double::isFinite))return null;
            return new State(value,gradient,directed,disturbance,full,fullMean,covariance,mean,inverse,derivatives,meanDerivatives);
        }
        RealMatrix normalize(RealMatrix matrix){double[][] result=matrix.getData();for(int i=0;i<p;i++)for(int j=0;j<p;j++)result[i][j]/=scales[i]*scales[j];return new Array2DRowRealMatrix(result,false);}
        RealVector normalizeMean(RealVector vector){double[] result=vector.toArray();for(int i=0;i<p;i++)result[i]/=scales[i];return new ArrayRealVector(result,false);}
        RealVector select(RealVector vector){double[] result=new double[p];for(int i=0;i<p;i++)result[i]=vector.getEntry(observed[i]);return new ArrayRealVector(result,false);}
        double logDet(RealMatrix matrix){try{return logDet(new CholeskyDecomposition(matrix,1e-9,1e-12));}catch(NonPositiveDefiniteMatrixException|NonSymmetricMatrixException invalid){throw new IllegalArgumentException("SEM empirical covariance must be positive definite.");}}
        double logDet(CholeskyDecomposition decomposition){RealMatrix lower=decomposition.getL();double result=0;for(int i=0;i<p;i++)result+=2*Math.log(lower.getEntry(i,i));return result;}
    }
}
