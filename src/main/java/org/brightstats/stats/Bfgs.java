package org.brightstats.stats;

import java.util.*;
import java.util.function.Function;

/** Scaled-coordinate BFGS with a safeguarded Armijo search and optional box bounds. */
final class Bfgs {
    private Bfgs() {}
    record Evaluation(double value,double[] gradient){Evaluation{gradient=gradient.clone();}@Override public double[] gradient(){return gradient.clone();}}
    record Result(double[] point,Evaluation evaluation,int iterations,boolean converged,String reason){Result{point=point.clone();}@Override public double[] point(){return point.clone();}}
    static Result minimize(Function<double[],Evaluation> function,double[] initial,double[] lower,double[] upper,
                           int maximum,double gradientTolerance,double functionTolerance){
        int q=initial.length;double[] point=initial.clone();double[][] inverse=identity(q);Evaluation current=function.apply(point);
        if(!valid(current,q))throw new IllegalArgumentException("SEM starting values do not produce a finite, admissible likelihood.");
        for(int iteration=0;iteration<maximum;iteration++){
            Matrices.interrupted();double[] gradient=current.gradient(),projected=gradient.clone();
            for(int i=0;i<q;i++)if(point[i]<=lower[i]&&gradient[i]>0||point[i]>=upper[i]&&gradient[i]<0){projected[i]=0;for(int j=0;j<q;j++)inverse[i][j]=inverse[j][i]=0;inverse[i][i]=1;}
            if(maximum(projected)<=gradientTolerance)return new Result(point,current,iteration,true,"Projected gradient criterion");
            double[] direction=new double[q];for(int i=0;i<q;i++)for(int j=0;j<q;j++)direction[i]-=inverse[i][j]*projected[j];
            for(int i=0;i<q;i++)if(point[i]<=lower[i]&&direction[i]<0||point[i]>=upper[i]&&direction[i]>0)direction[i]=0;
            if(!Double.isFinite(dot(direction,gradient))||dot(direction,gradient)>=0){inverse=identity(q);for(int i=0;i<q;i++)direction[i]=-projected[i];}
            double step=1;double[] candidate=new double[q];Evaluation next=null;boolean accepted=false;
            while(step>=1e-14){
                Matrices.interrupted();double[] displacement=new double[q];for(int i=0;i<q;i++){candidate[i]=Math.max(lower[i],Math.min(upper[i],point[i]+step*direction[i]));displacement[i]=candidate[i]-point[i];}
                next=function.apply(candidate);
                if(valid(next,q)&&next.value()<=current.value()+1e-4*dot(gradient,displacement)+1e-14){accepted=true;break;}step*=.5;
            }
            if(!accepted)return new Result(point,current,iteration,false,"Line search could not improve an admissible likelihood");
            double[] displacement=new double[q],change=new double[q],hy=new double[q];double[] nextGradient=next.gradient();
            for(int i=0;i<q;i++){displacement[i]=candidate[i]-point[i];change[i]=nextGradient[i]-gradient[i];if(displacement[i]==0&&(candidate[i]<=lower[i]&&nextGradient[i]>0||candidate[i]>=upper[i]&&nextGradient[i]<0))change[i]=0;}
            double curvature=dot(displacement,change);
            if(curvature>1e-12*Math.sqrt(dot(displacement,displacement)*dot(change,change))&&Double.isFinite(curvature)){
                for(int i=0;i<q;i++)for(int j=0;j<q;j++)hy[i]+=inverse[i][j]*change[j];
                double multiplier=(curvature+dot(change,hy))/(curvature*curvature);
                for(int i=0;i<q;i++)for(int j=0;j<q;j++)inverse[i][j]+=multiplier*displacement[i]*displacement[j]-(hy[i]*displacement[j]+displacement[i]*hy[j])/curvature;
            }else inverse=identity(q);
            double improvement=Math.abs(current.value()-next.value());point=candidate.clone();current=next;
            double[] nextProjected=nextGradient.clone();for(int i=0;i<q;i++)if(point[i]<=lower[i]&&nextGradient[i]>0||point[i]>=upper[i]&&nextGradient[i]<0)nextProjected[i]=0;
            if(improvement<=functionTolerance*Math.max(1,Math.abs(current.value()))&&maximum(nextProjected)<=Math.max(1e-10,10*gradientTolerance))
                return new Result(point,current,iteration+1,true,"Function criterion with small gradient");
        }
        return new Result(point,current,maximum,false,"Iteration limit reached");
    }
    private static boolean valid(Evaluation value,int size){return value!=null&&Double.isFinite(value.value())&&value.gradient().length==size&&Arrays.stream(value.gradient()).allMatch(Double::isFinite);}
    private static double[][] identity(int q){double[][] matrix=new double[q][q];for(int i=0;i<q;i++)matrix[i][i]=1;return matrix;}
    private static double dot(double[] a,double[] b){double result=0;for(int i=0;i<a.length;i++)result+=a[i]*b[i];return result;}
    private static double maximum(double[] values){double result=0;for(double value:values)result=Math.max(result,Math.abs(value));return result;}
}
