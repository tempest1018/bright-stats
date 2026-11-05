package org.brightstats.stats;
import java.util.*;
import org.apache.commons.math3.linear.*;

/** Orthogonal varimax and oblique promax with explicit unit-variance rotated coordinates. */
public final class PcaRotation {
    private PcaRotation(){}
    public enum Method {NONE,VARIMAX,PROMAX}
    public record Options(Method method,boolean kaiserNormalization,int maxIterations,double tolerance,int power){
        public Options{Objects.requireNonNull(method);if(maxIterations<1||maxIterations>5000||!Double.isFinite(tolerance)||tolerance<=0||tolerance>=1||power<2||power>6)throw new IllegalArgumentException("Rotation requires 1–5000 iterations, tolerance between zero and one and promax power 2–6.");}
        public static Options defaults(){return new Options(Method.NONE,true,1000,1e-8,4);}
    }
    public record Fit(Options options,int iterations,boolean converged,double objective,double[][] transformation,double[][] pattern,
                      double[][] structure,double[][] correlations,double[][] scores,double[] communalities,List<String> warnings){
        public Fit{transformation=Matrices.copy(transformation);pattern=Matrices.copy(pattern);structure=Matrices.copy(structure);correlations=Matrices.copy(correlations);scores=Matrices.copy(scores);communalities=communalities.clone();warnings=List.copyOf(warnings);}
        @Override public double[][] transformation(){return Matrices.copy(transformation);}@Override public double[][] pattern(){return Matrices.copy(pattern);}@Override public double[][] structure(){return Matrices.copy(structure);}@Override public double[][] correlations(){return Matrices.copy(correlations);}@Override public double[][] scores(){return Matrices.copy(scores);}@Override public double[] communalities(){return communalities.clone();}
    }
    public static Fit rotate(PrincipalComponents.Fit pca,Options options){
        Matrices.interrupted();int k=pca.retained(),p=pca.loadings().length;if(k>64||(long)p*k>500_000)throw new IllegalArgumentException("Rotation exceeds the 64-component/500000-loading working limit.");
        double[] eigenvalues=pca.eigenvalues();double largest=eigenvalues[0];for(int j=0;j<k;j++)if(!(eigenvalues[j]>largest*1e-12))throw new IllegalArgumentException("Retained near-zero eigenvalues cannot define stable unit-variance rotated coordinates; retain fewer components.");
        RealMatrix original=new Array2DRowRealMatrix(pca.loadings()),normalized=original.copy(),rotation=MatrixUtils.createRealIdentityMatrix(k);double[] rowScale=new double[p];
        for(int i=0;i<p;i++){double sum=0;for(int j=0;j<k;j++)sum+=original.getEntry(i,j)*original.getEntry(i,j);rowScale[i]=options.kaiserNormalization()&&sum>0?Math.sqrt(sum):1;for(int j=0;j<k;j++)normalized.setEntry(i,j,original.getEntry(i,j)/rowScale[i]);}
        int iterations=0;boolean converged=options.method()==Method.NONE||k==1;double previous=Double.NaN;
        // For two axes the criterion is a sinusoid in four times the angle. Solve it directly;
        // iterative SVD can converge very slowly when one retained direction has little variance.
        if(!converged&&k==2){double[] a=new double[p],b=new double[p];double meanA=0,meanB=0;for(int i=0;i<p;i++){double x=normalized.getEntry(i,0),y=normalized.getEntry(i,1);a[i]=x*x-y*y;b[i]=2*x*y;meanA+=(a[i]-meanA)/(i+1);meanB+=(b[i]-meanB)/(i+1);}double aa=0,bb=0,ab=0;for(int i=0;i<p;i++){double x=a[i]-meanA,y=b[i]-meanB;aa+=x*x;bb+=y*y;ab+=x*y;}if(!Double.isFinite(aa)||!Double.isFinite(bb)||!Double.isFinite(ab))throw new IllegalArgumentException("Varimax angle exceeded numeric range; rescale covariance-PCA variables.");double angle=.25*Math.atan2(2*ab,aa-bb),c=Math.cos(angle),s=Math.sin(angle);rotation=new Array2DRowRealMatrix(new double[][]{{c,-s},{s,c}});iterations=1;converged=true;}
        if(!converged)for(int iteration=1;iteration<=options.maxIterations();iteration++){
            Matrices.interrupted();RealMatrix loadings=normalized.multiply(rotation),target=loadings.copy();double[] norm=new double[k];for(int j=0;j<k;j++)for(int i=0;i<p;i++)norm[j]+=loadings.getEntry(i,j)*loadings.getEntry(i,j);
            for(int i=0;i<p;i++)for(int j=0;j<k;j++){double value=loadings.getEntry(i,j);target.setEntry(i,j,value*value*value-value*norm[j]/p);}
            var svd=new SingularValueDecomposition(normalized.transpose().multiply(target));rotation=svd.getU().multiply(svd.getVT());double objective=Arrays.stream(svd.getSingularValues()).sum();iterations=iteration;
            if(Double.isFinite(previous)&&Math.abs(objective-previous)<=options.tolerance()*Math.max(1e-15,Math.abs(previous))){converged=true;break;}previous=objective;
        }
        List<String>warnings=new ArrayList<>(pca.warnings());if(!converged)warnings.add("Varimax iteration limit reached. Rotation is provisional; increase the limit or inspect sensitivity to retained dimensions.");
        if(options.method()==Method.PROMAX&&k>1){
            RealMatrix varimax=original.multiply(rotation),target=varimax.copy();for(int i=0;i<p;i++)for(int j=0;j<k;j++){double value=varimax.getEntry(i,j);target.setEntry(i,j,Math.copySign(Math.pow(Math.abs(value),options.power()),value));}
            var solver=new SingularValueDecomposition(varimax);if(solver.getRank()!=k)throw new IllegalArgumentException("Promax loading system is rank deficient.");RealMatrix oblique=solver.getSolver().solve(target);var condition=new SingularValueDecomposition(oblique);if(condition.getConditionNumber()>1e10||condition.getRank()!=k)throw new IllegalArgumentException("Promax target yields a numerically singular transformation.");
            RealMatrix inverse=condition.getSolver().getInverse(),phi=inverse.multiply(inverse.transpose());for(int j=0;j<k;j++){double scale=Math.sqrt(phi.getEntry(j,j));for(int i=0;i<k;i++)oblique.multiplyEntry(i,j,scale);}rotation=rotation.multiply(oblique);
            warnings.add("Promax is oblique: pattern and structure coefficients differ, and correlated components do not have additive explained-variance percentages. Power defines the signed target after varimax.");
        }
        RealMatrix pattern=original.multiply(rotation);for(int j=0;j<k;j++){int anchor=0;for(int i=1;i<p;i++)if(Math.abs(pattern.getEntry(i,j))>Math.abs(pattern.getEntry(anchor,j)))anchor=i;if(pattern.getEntry(anchor,j)<0){for(int i=0;i<k;i++)rotation.multiplyEntry(i,j,-1);for(int i=0;i<p;i++)pattern.multiplyEntry(i,j,-1);}}
        RealMatrix inverse=new SingularValueDecomposition(rotation).getSolver().getInverse(),phi=inverse.multiply(inverse.transpose()),structure=pattern.multiply(phi);double[][] scores=pca.scores();for(var row:scores)for(int j=0;j<k;j++)row[j]/=Math.sqrt(eigenvalues[j]);RealMatrix rotatedScores=new Array2DRowRealMatrix(scores).multiply(inverse.transpose());
        RealMatrix reconstructed=pattern.multiply(phi).multiply(pattern.transpose());double[] communalities=new double[p];for(int i=0;i<p;i++)communalities[i]=reconstructed.getEntry(i,i);
        double objective=0;RealMatrix finalOrthogonal=normalized.multiply(options.method()==Method.PROMAX?MatrixUtils.createRealIdentityMatrix(k):rotation);for(int j=0;j<k;j++){double second=0,fourth=0;for(int i=0;i<p;i++){double value=finalOrthogonal.getEntry(i,j),square=value*value;second+=square;fourth+=square*square;}objective+=fourth-second*second/p;}
        if(options.method()==Method.PROMAX)objective=Double.NaN;
        warnings.add("Rotated scores have unit sample variances, including covariance PCA. Structure coefficients are variable-score covariances; they are correlations only for correlation PCA. They are transformed PCA coordinates, not common-factor posterior scores. Original eigenvalues/variance ratios describe the unrotated PCA only. Column signs use the largest absolute pattern coefficient; local rotation solutions and repeated-eigenvalue indeterminacy remain possible.");
        return new Fit(options,iterations,converged,objective,rotation.getData(),pattern.getData(),structure.getData(),phi.getData(),rotatedScores.getData(),communalities,warnings);
    }
}
