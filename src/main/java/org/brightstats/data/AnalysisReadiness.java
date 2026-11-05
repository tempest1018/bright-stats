package org.brightstats.data;

import java.util.*;
import com.google.gson.JsonObject;
import org.brightstats.stats.*;

/** Descriptive preflight, never a data repair or universal identification certificate. */
public final class AnalysisReadiness {
    private AnalysisReadiness(){}
    public enum Severity { INFO, REVIEW, BLOCKED }
    public record Finding(Severity severity,String area,String detail){}
    public record Review(int rows,int columns,int modelConstructs,int originalMissing,int replacedCells,int removedRows,List<Finding> findings){public Review{findings=List.copyOf(findings);}}
    public static Review inspect(List<String> names,double[][] data,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,JsonObject history){
        List<Finding> findings=new ArrayList<>();int width=names.size(),missing=0,replaced=0,removed=0;
        if(data.length==0||width==0){findings.add(new Finding(Severity.BLOCKED,"Data","Import a dataset before calculating."));return new Review(data.length,width,blocks.size(),0,0,0,findings);}
        if(history!=null){var source=ImportHistory.read(history);replaced=source.replacedCells();removed=source.removedRows();for(var row:source.raw().rows())for(int j=0;j<row.size();j++)if(source.definitions().get(j).kind()==DataPreparation.Kind.NUMERIC&&DataPreparation.missing(row.get(j),source.settings()))missing++;}
        if(missing>0)findings.add(new Finding(Severity.REVIEW,"Missingness",missing+" original numeric missing cells; "+replaced+" replaced cells and "+removed+" removed rows. Choose the method's missing-data assumptions explicitly."));
        boolean finite=true;for(int j=0;j<width;j++){Matrices.interrupted();double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;Set<Double> distinct=new HashSet<>();for(var row:data){if(row.length!=width)throw new IllegalArgumentException("Prepared rows and column names differ.");double value=row[j];if(!Double.isFinite(value)){finite=false;continue;}min=Math.min(min,value);max=Math.max(max,value);if(distinct.size()<12)distinct.add(value);}if(max==min)findings.add(new Finding(Severity.BLOCKED,"Variance",names.get(j)+" is constant among observed values; exclude it from estimators requiring variance."));else if(distinct.size()<=7)findings.add(new Finding(Severity.REVIEW,"Measurement scale",names.get(j)+" has "+distinct.size()+" distinct values. Confirm whether it is ordinal, binary, categorical, or genuinely quantitative; arbitrary codes do not establish metric spacing."));}
        if(!finite)findings.add(new Finding(Severity.REVIEW,"Finite values","Some prepared cells are nonfinite. Use an estimator with explicit missing-data support or revise preparation."));
        Set<Integer> used=new HashSet<>();for(var block:blocks){if(block.columns().length<3&&block.measurementType()==PlsSem.MeasurementType.REFLECTIVE)findings.add(new Finding(Severity.REVIEW,"Measurement",block.name()+" has fewer than three indicators; identification and reliability correction need method-specific review."));for(int col:block.columns()){if(col<0||col>=width)findings.add(new Finding(Severity.BLOCKED,"Bindings",block.name()+" refers to an unavailable column."));else if(!used.add(col))findings.add(new Finding(Severity.REVIEW,"Shared indicators",names.get(col)+" is shared between constructs. Review the higher-order design and discriminant-validity interpretation."));}}
        for(var edge:edges)if(edge.source()<0||edge.target()<0||edge.source()>=blocks.size()||edge.target()>=blocks.size())findings.add(new Finding(Severity.BLOCKED,"Structural graph","A path has an unavailable endpoint."));
        if(data.length<=width)findings.add(new Finding(Severity.REVIEW,"Sample size","The number of observations does not exceed the prepared width. Rank and estimator identification need particular attention."));
        if(finite&&width<=128&&(long)data.length*width<=500000){boolean constants=findings.stream().anyMatch(v->v.area().equals("Variance"));if(!constants){var centered=Matrices.standardize(data,false,false);var covariance=Matrices.covariance(centered.values(),false);double[] sd=new double[width];for(int j=0;j<width;j++)sd[j]=Math.sqrt(covariance[j][j]);for(int i=0;i<width;i++)for(int j=0;j<width;j++)covariance[i][j]/=sd[i]*sd[j];if(Matrices.minEigenvalue(covariance)<1e-9)findings.add(new Finding(Severity.REVIEW,"Rank","The full prepared correlation matrix is singular or nearly singular. Review redundant columns and the variables actually selected by your estimator."));}}
        else findings.add(new Finding(Severity.INFO,"Rank","Full-matrix rank screening was skipped to bound preflight cost. Each estimator still performs its own validity checks."));
        if(blocks.isEmpty())findings.add(new Finding(Severity.INFO,"Model","No composite/factor blocks are defined. Observed-variable workflows can use their own specification."));
        findings.add(new Finding(Severity.INFO,"Interpretation","These descriptive checks neither prove model identification nor establish causal effects. Estimator-specific diagnostics remain authoritative."));
        return new Review(data.length,width,blocks.size(),missing,replaced,removed,findings);
    }
}
