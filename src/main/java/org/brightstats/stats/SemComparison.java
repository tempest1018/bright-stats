package org.brightstats.stats;

import java.util.*;
import org.apache.commons.math3.special.Gamma;

/** Regular likelihood-ratio comparison of demonstrably nested linear RAM specifications. */
public final class SemComparison {
    private SemComparison(){}
    public record Result(double restrictedChiSquare,double unrestrictedChiSquare,int restrictedDf,int unrestrictedDf,
                         double difference,int differenceDf,double pValue,double restrictedAic,double unrestrictedAic,
                         double restrictedBic,double unrestrictedBic,String method,List<String> warnings){public Result{warnings=List.copyOf(warnings);}}
    private record Cell(SemModel.Kind kind,int row,int column){}
    private record Expression(Integer parameter,double constant){}
    public static Result compare(CovarianceSem.Fit restricted,CovarianceSem.Fit unrestricted){
        if(!restricted.converged()||!unrestricted.converged()||!restricted.identified()||!unrestricted.identified())throw new IllegalArgumentException("Model comparison requires converged, identified fits.");
        if(restricted.observations()!=unrestricted.observations()||restricted.options().likelihood()!=unrestricted.options().likelihood()||restricted.options().meanStructure()!=unrestricted.options().meanStructure()||!restricted.model().variables().equals(unrestricted.model().variables())||!Arrays.equals(restricted.model().observed(),unrestricted.model().observed()))throw new IllegalArgumentException("Compare the same variables, observation count, mean structure and likelihood convention.");
        if(!same(restricted.sampleCovariance(),unrestricted.sampleCovariance())||restricted.options().meanStructure()&&!same(new double[][]{restricted.sampleMeans()},new double[][]{unrestricted.sampleMeans()}))throw new IllegalArgumentException("Model comparison requires identical observed sample moments.");
        for(var fit:List.of(restricted,unrestricted)){
            if(fit.model().parameters().stream().anyMatch(v->v.lower()!=null||v.upper()!=null))throw new IllegalArgumentException("Ordinary likelihood-ratio tests do not cover configured parameter bounds.");
            if(fit.warnings().stream().anyMatch(v->v.contains("Heywood")||v.contains("not positive semidefinite")))throw new IllegalArgumentException("Resolve improper SEM solutions before ordinary likelihood-ratio comparison.");
        }
        int differenceDf=restricted.degreesOfFreedom()-unrestricted.degreesOfFreedom();if(differenceDf<=0)throw new IllegalArgumentException("The restricted model must have fewer free parameters.");
        if(!nested(restricted.model(),unrestricted.model()))throw new IllegalArgumentException("The specified RAM constraints are not nested; an ordinary chi-square difference test is invalid.");
        double difference=restricted.chiSquare()-unrestricted.chiSquare();if(difference< -1e-6*Math.max(1,restricted.chiSquare()))throw new IllegalArgumentException("The unrestricted fit is worse; check optimization before comparison.");difference=Math.max(0,difference);
        return new Result(restricted.chiSquare(),unrestricted.chiSquare(),restricted.degreesOfFreedom(),unrestricted.degreesOfFreedom(),difference,differenceDf,Gamma.regularizedGammaQ(differenceDf*.5,difference*.5),restricted.discrepancyAic(),unrestricted.discrepancyAic(),restricted.diagnostics().discrepancyBic(),unrestricted.diagnostics().discrepancyBic(),"Normal-theory likelihood-ratio test; nested linear RAM constraints; AIC/BIC use chi-square discrepancy",List.of());
    }
    static boolean nested(SemModel.Model restricted,SemModel.Model unrestricted){
        Map<Cell,Expression> first=expressions(restricted),second=expressions(unrestricted);Map<Integer,Expression> groups=new HashMap<>();
        for(var entry:second.entrySet()){
            Expression alternative=entry.getValue(),nullValue=first.get(entry.getKey());if(nullValue==null)return false;
            if(alternative.parameter()==null){if(nullValue.parameter()!=null||!close(nullValue.constant(),alternative.constant()))return false;}
            else{
                Expression previous=groups.putIfAbsent(alternative.parameter(),nullValue);
                if(previous!=null&&(previous.parameter()!=null||nullValue.parameter()!=null?!Objects.equals(previous.parameter(),nullValue.parameter()):!close(previous.constant(),nullValue.constant())))return false;
            }
        }
        return true;
    }
    private static Map<Cell,Expression> expressions(SemModel.Model model){
        Map<Cell,Expression> result=new LinkedHashMap<>();int size=model.variables().size();double[][] a=model.directed(),s=model.covariance();double[] m=model.intercepts();
        for(int i=0;i<size;i++){for(int j=0;j<size;j++)result.put(new Cell(SemModel.Kind.DIRECTED,i,j),new Expression(null,a[i][j]));for(int j=0;j<=i;j++)result.put(new Cell(SemModel.Kind.COVARIANCE,i,j),new Expression(null,s[i][j]));result.put(new Cell(SemModel.Kind.INTERCEPT,i,0),new Expression(null,m[i]));}
        for(int j=0;j<model.parameters().size();j++)for(var location:model.parameters().get(j).locations()){
            int row=location.row(),column=location.column();if(location.kind()==SemModel.Kind.COVARIANCE){row=Math.max(location.row(),location.column());column=Math.min(location.row(),location.column());}if(location.kind()==SemModel.Kind.INTERCEPT)column=0;
            result.put(new Cell(location.kind(),row,column),new Expression(j,0));
        }
        return result;
    }
    private static boolean same(double[][] first,double[][] second){if(first.length!=second.length)return false;for(int i=0;i<first.length;i++){if(first[i].length!=second[i].length)return false;for(int j=0;j<first[i].length;j++)if(!close(first[i][j],second[i][j]))return false;}return true;}
    private static boolean close(double a,double b){return Math.abs(a-b)<=1e-10*Math.max(1,Math.max(Math.abs(a),Math.abs(b)));}
}
