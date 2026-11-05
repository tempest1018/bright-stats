package org.brightstats.stats;
import org.apache.commons.math3.linear.*;
/** Solver-only centered design with a declared correlation; never original cases. */
final class MomentDesign {
 private MomentDesign(){}
 static double[][] correlation(double[][] correlation){int p=Matrices.width(correlation,2);if(p!=correlation.length||p>64||Matrices.minEigenvalue(correlation)<=1e-10)throw new IllegalArgumentException("Moment correlation must be square, positive definite and at most64 variables.");for(int i=0;i<p;i++)if(Math.abs(correlation[i][i]-1)>1e-8)throw new IllegalArgumentException("Moment correlation diagonal must equal one.");double[][] l=new CholeskyDecomposition(new Array2DRowRealMatrix(correlation,false),1e-10,1e-12).getL().getData();int n=p+1;double[][] design=new double[n][p];for(int row=0;row<n;row++)for(int basis=0;basis<p;basis++){double h=row<=basis?1/Math.sqrt((basis+1.0)*(basis+2)):row==basis+1?-(basis+1)/Math.sqrt((basis+1.0)*(basis+2)):0;for(int col=0;col<p;col++)design[row][col]+=Math.sqrt(n)*h*l[col][basis];}return design;}
}
