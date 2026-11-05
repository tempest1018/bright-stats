package org.brightstats.stats;

import java.util.*;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.linear.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;

/** Unpenalized binary maximum-likelihood logistic regression with safeguarded Newton steps. */
public final class LogisticRegression {
    private LogisticRegression(){}
    public record Options(int maxIterations,double tolerance,double confidence,double threshold,Double eventClass) {
        public Options {Inference.confidence(confidence);if(maxIterations<1||maxIterations>10000||!Double.isFinite(tolerance)||tolerance<=0||!Double.isFinite(threshold)||threshold<=0||threshold>=1||eventClass!=null&&!Double.isFinite(eventClass))throw new IllegalArgumentException("Invalid logistic estimation options.");}
        public static Options defaults(){return new Options(100,1e-8,.95,.5,null);}
    }
    public record Term(double coefficient,double standardError,double z,double pValue,double lower,double upper,double oddsRatio,double oddsLower,double oddsUpper) {}
    public record Fit(Options options,double eventClass,double referenceClass,int iterations,boolean converged,
                      double logLikelihood,double nullLogLikelihood,double deviance,double aic,double bic,double mcfaddenRSquared,
                      double[] coefficients,double[][] covariance,List<Term> terms,double[] probabilities,double[] residuals,int[][] confusion,List<String> warnings) {
        public Fit {coefficients=coefficients.clone();covariance=Matrices.copy(covariance);terms=List.copyOf(terms);probabilities=probabilities.clone();residuals=residuals.clone();confusion=Arrays.stream(confusion).map(int[]::clone).toArray(int[][]::new);warnings=List.copyOf(warnings);}
        @Override public double[] coefficients(){return coefficients.clone();}@Override public double[][] covariance(){return Matrices.copy(covariance);}@Override public double[] probabilities(){return probabilities.clone();}@Override public double[] residuals(){return residuals.clone();}@Override public int[][] confusion(){return Arrays.stream(confusion).map(int[]::clone).toArray(int[][]::new);}
        public double probability(double[] predictors){if(predictors.length+1!=coefficients.length)throw new IllegalArgumentException("Predictor count differs.");double eta=coefficients[0];for(int j=0;j<predictors.length;j++){if(!Double.isFinite(predictors[j]))throw new IllegalArgumentException("Non-finite predictor.");eta+=coefficients[j+1]*predictors[j];}return sigmoid(eta);}
    }
    private static double sigmoid(double value){if(value>=0)return 1/(1+Math.exp(-value));double exp=Math.exp(value);return exp/(1+exp);}
    private static double likelihood(double[][] x,double[] y,double[] beta) {double result=0;for(int i=0;i<x.length;i++){double eta=0;for(int j=0;j<beta.length;j++)eta+=x[i][j]*beta[j];result+=y[i]*eta-(eta>0?eta+Math.log1p(Math.exp(-eta)):Math.log1p(Math.exp(eta)));}return result;}
    public static Fit fit(double[][] predictors,double[] response,Options options) {
        Objects.requireNonNull(options);if(predictors==null||response==null||response.length<3||predictors.length!=response.length||predictors[0]==null)throw new IllegalArgumentException("Expected matching binary response and predictor data.");
        int n=response.length,p=predictors[0].length,k=p+1;if(n<=k)throw new IllegalArgumentException("More cases than logistic coefficients are required.");TreeSet<Double> classes=new TreeSet<>();
        for(int i=0;i<n;i++){if(predictors[i]==null||predictors[i].length!=p||!Double.isFinite(response[i]))throw new IllegalArgumentException("Ragged data or non-finite response.");classes.add(response[i]);for(double value:predictors[i])if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite predictor.");}
        if(classes.size()!=2)throw new IllegalArgumentException("Logistic regression requires exactly two observed response classes.");double event=options.eventClass()==null?classes.last():options.eventClass();if(!classes.contains(event))throw new IllegalArgumentException("Selected event class is absent from the response.");double reference=classes.first()==event?classes.last():classes.first();
        double[] mean=new double[p],scale=new double[p],y=new double[n];double eventRate=0;for(int i=0;i<n;i++){y[i]=response[i]==event?1:0;eventRate+=y[i]/n;for(int j=0;j<p;j++)mean[j]+=(predictors[i][j]-mean[j])/(i+1);}
        for(int j=0;j<p;j++){double sum=0;for(int i=0;i<n;i++)sum+=Math.pow(predictors[i][j]-mean[j],2);scale[j]=Math.sqrt(sum/n);if(!(scale[j]>0)||!Double.isFinite(scale[j]))throw new IllegalArgumentException("Constant or unscalable logistic predictor.");}
        double[][] x=new double[n][k];for(int i=0;i<n;i++){x[i][0]=1;for(int j=0;j<p;j++)x[i][j+1]=(predictors[i][j]-mean[j])/scale[j];}
        if(new SingularValueDecomposition(new Array2DRowRealMatrix(x)).getRank()<k)throw new IllegalArgumentException("Rank-deficient logistic design.");List<String> warnings=new ArrayList<>();
        if(n<=2000&&k<=40)separation(x,y);else warnings.add("Large design: explicit linear-program separation check omitted; numerical convergence/conditioning checks still apply.");
        double[] beta=new double[k];beta[0]=Math.log(eventRate/(1-eventRate));double ll=likelihood(x,y,beta);boolean converged=false;int iteration=0;
        for(;iteration<options.maxIterations();iteration++) {
            Matrices.interrupted();double[][] information=new double[k][k];double[] gradient=new double[k];
            for(int i=0;i<n;i++){double eta=0;for(int j=0;j<k;j++)eta+=x[i][j]*beta[j];double probability=sigmoid(eta),weight=probability*(1-probability);for(int a=0;a<k;a++){gradient[a]+=x[i][a]*(y[i]-probability);for(int b=0;b<k;b++)information[a][b]+=weight*x[i][a]*x[i][b];}}
            double[] step;try{step=new CholeskyDecomposition(new Array2DRowRealMatrix(information),1e-10,1e-12).getSolver().solve(new ArrayRealVector(gradient)).toArray();}catch(RuntimeException invalid){warnings.add("Ill-conditioned likelihood; separation or weak identification is possible.");break;}
            double fraction=1;double[] candidate=new double[k];double next=Double.NEGATIVE_INFINITY;
            while(fraction>=1e-10){for(int j=0;j<k;j++)candidate[j]=beta[j]+fraction*step[j];next=likelihood(x,y,candidate);if(Double.isFinite(next)&&next>=ll-1e-12)break;fraction*=.5;}
            if(fraction<1e-10){warnings.add("Logistic line search failed.");break;}double delta=0;for(int j=0;j<k;j++)delta=Math.max(delta,Math.abs(candidate[j]-beta[j]));beta=candidate;ll=next;
            if(delta<options.tolerance()){converged=true;iteration++;break;}
        }
        double[][] information=new double[k][k];double[] probability=new double[n],residuals=new double[n];int[][] confusion=new int[2][2];
        for(int i=0;i<n;i++){double eta=0;for(int j=0;j<k;j++)eta+=x[i][j]*beta[j];probability[i]=sigmoid(eta);residuals[i]=y[i]-probability[i];confusion[(int)y[i]][probability[i]>=options.threshold()?1:0]++;double w=probability[i]*(1-probability[i]);for(int a=0;a<k;a++)for(int b=0;b<k;b++)information[a][b]+=w*x[i][a]*x[i][b];}
        double[][] transform=new double[k][k];transform[0][0]=1;for(int j=1;j<k;j++){transform[0][j]=-mean[j-1]/scale[j-1];transform[j][j]=1/scale[j-1];}RealMatrix conversion=new Array2DRowRealMatrix(transform);double[] original=conversion.operate(beta);double[][] covariance=new double[k][k];for(double[] row:covariance)Arrays.fill(row,Double.NaN);
        if(converged)try{covariance=conversion.multiply(new CholeskyDecomposition(new Array2DRowRealMatrix(information),1e-10,1e-12).getSolver().getInverse()).multiply(conversion.transpose()).getData();}catch(RuntimeException error){converged=false;warnings.add("Final Fisher information is singular; inference is unavailable.");}
        if(!converged)warnings.add("Maximum-likelihood convergence was not established; coefficients/probabilities are provisional and Wald inference is unavailable.");
        double critical=new NormalDistribution().inverseCumulativeProbability((1+options.confidence())/2);List<Term> terms=new ArrayList<>();for(int j=0;j<k;j++){double se=converged?Math.sqrt(Math.max(0,covariance[j][j])):Double.NaN,z=Inference.ratio(original[j],se),low=original[j]-critical*se,high=original[j]+critical*se;terms.add(new Term(original[j],se,z,Inference.normalTwoSided(z),low,high,Math.exp(original[j]),Math.exp(low),Math.exp(high)));}
        double nullLL=0;for(double value:y)nullLL+=value*Math.log(eventRate)+(1-value)*Math.log1p(-eventRate);
        return new Fit(options,event,reference,iteration,converged,ll,nullLL,-2*ll,2*k-2*ll,Math.log(n)*k-2*ll,1-ll/nullLL,original,covariance,terms,probability,residuals,confusion,warnings);
    }
    private static void separation(double[][] x,double[] y) {
        int k=x[0].length;double[] objective=new double[k*2];List<LinearConstraint> constraints=new ArrayList<>();
        for(int i=0;i<x.length;i++){double[] coefficients=new double[k*2];double sign=y[i]==1?1:-1;for(int j=0;j<k;j++){coefficients[j]=sign*x[i][j];coefficients[j+k]=-coefficients[j];objective[j]+=coefficients[j];objective[j+k]+=coefficients[j+k];}constraints.add(new LinearConstraint(coefficients,Relationship.GEQ,0));}
        double[] bound=new double[k*2];Arrays.fill(bound,1);constraints.add(new LinearConstraint(bound,Relationship.LEQ,1));
        PointValuePair optimum=new SimplexSolver(1e-9).optimize(new MaxIter(10000),new LinearObjectiveFunction(objective,0),new LinearConstraintSet(constraints),GoalType.MAXIMIZE,new NonNegativeConstraint(true));
        if(optimum.getValue()>1e-7)throw new IllegalArgumentException("Complete or quasi-complete separation: a finite unpenalized logistic maximum-likelihood estimate does not exist.");
    }
}
