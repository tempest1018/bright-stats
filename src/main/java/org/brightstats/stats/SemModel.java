package org.brightstats.stats;

import java.util.*;

/** RAM model: v=A v+m+e, Cov(e)=S, with explicit fixed/free/equality-constrained entries. */
public final class SemModel {
    private SemModel() {}
    public enum Kind { DIRECTED, COVARIANCE, INTERCEPT }
    public record Location(Kind kind,int row,int column) {public Location{Objects.requireNonNull(kind);}}
    public record Parameter(String name,List<Location> locations,double start,double scale,Double lower,Double upper) {
        public Parameter {
            if(name==null||name.isBlank()||locations==null||locations.isEmpty()||!Double.isFinite(start)||!Double.isFinite(scale)||scale<=0||lower!=null&&!Double.isFinite(lower)||upper!=null&&!Double.isFinite(upper)||lower!=null&&upper!=null&&lower>=upper||lower!=null&&start<lower||upper!=null&&start>upper)throw new IllegalArgumentException("Invalid SEM parameter or bounds.");
            locations=List.copyOf(locations);
        }
        public Parameter(String name,List<Location> locations,double start,double scale){this(name,locations,start,scale,null,null);}
    }
    public record Model(List<String> variables,int[] observed,double[][] directed,double[][] covariance,double[] intercepts,List<Parameter> parameters) {
        public Model {
            variables=List.copyOf(variables);observed=observed.clone();directed=Matrices.copy(directed);covariance=Matrices.copy(covariance);intercepts=intercepts.clone();parameters=List.copyOf(parameters);
            int size=variables.size();if(size==0||variables.stream().anyMatch(s->s==null||s.isBlank())||new HashSet<>(variables).size()!=size||observed.length==0||Arrays.stream(observed).anyMatch(i->i<0||i>=size)||Arrays.stream(observed).distinct().count()!=observed.length||directed.length!=size||covariance.length!=size||intercepts.length!=size)throw new IllegalArgumentException("Invalid SEM variable/matrix dimensions.");
            for(int i=0;i<size;i++){if(directed[i].length!=size||covariance[i].length!=size||!Double.isFinite(intercepts[i]))throw new IllegalArgumentException("Invalid SEM matrix dimensions or intercept.");for(int j=0;j<size;j++)if(!Double.isFinite(directed[i][j])||!Double.isFinite(covariance[i][j]))throw new IllegalArgumentException("SEM matrices require finite entries.");}
            for(int i=0;i<size;i++){if(directed[i][i]!=0)throw new IllegalArgumentException("SEM self-directed effects are not identified by this parameterization.");for(int j=0;j<i;j++)if(Math.abs(covariance[i][j]-covariance[j][i])>1e-12)throw new IllegalArgumentException("SEM disturbance covariance must be symmetric.");}
            Set<String> names=new HashSet<>(),occupied=new HashSet<>();
            for(var parameter:parameters){if(!names.add(parameter.name()))throw new IllegalArgumentException("SEM parameter names must be unique; use multiple locations for equality constraints.");for(var location:parameter.locations()){
                if(location.row()<0||location.row()>=size||location.kind()!=Kind.INTERCEPT&&(location.column()<0||location.column()>=size)||location.kind()==Kind.DIRECTED&&location.row()==location.column())throw new IllegalArgumentException("Invalid SEM free-parameter location.");
                int row=location.row(),column=location.column();if(location.kind()==Kind.COVARIANCE){row=Math.min(location.row(),location.column());column=Math.max(location.row(),location.column());}if(location.kind()==Kind.INTERCEPT)column=0;
                if(!occupied.add(location.kind()+":"+row+":"+column))throw new IllegalArgumentException("SEM entries cannot belong to multiple free parameters.");
            }}
        }
        @Override public int[] observed(){return observed.clone();}@Override public double[][] directed(){return Matrices.copy(directed);}@Override public double[][] covariance(){return Matrices.copy(covariance);}@Override public double[] intercepts(){return intercepts.clone();}
    }
    public record Prepared(Model model,int[] columns,int[] factors) {
        public Prepared{Objects.requireNonNull(model);columns=columns.clone();factors=factors.clone();if(columns.length!=model.observed().length||Arrays.stream(columns).anyMatch(i->i<0)||Arrays.stream(columns).distinct().count()!=columns.length||Arrays.stream(factors).anyMatch(i->i<0||i>=model.variables().size()))throw new IllegalArgumentException("Invalid prepared SEM column/factor mapping.");}
        @Override public int[] columns(){return columns.clone();}@Override public int[] factors(){return factors.clone();}
        public double[][] select(double[][] data){int width=Matrices.width(data,2);if(Arrays.stream(columns).anyMatch(i->i>=width))throw new IllegalArgumentException("Prepared SEM column is outside the dataset.");double[][] result=new double[data.length][columns.length];for(int i=0;i<data.length;i++)for(int j=0;j<columns.length;j++)result[i][j]=data[i][columns[j]];return result;}
    }
    public static Prepared reflective(List<String> names,double[][] data,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,boolean correlateExogenous,boolean meanStructure){
        int width=Matrices.width(data,3);if(names.size()!=width||blocks.isEmpty())throw new IllegalArgumentException("Provide named data and at least one factor.");
        LinkedHashSet<Integer> selected=new LinkedHashSet<>();Set<String> factorNames=new HashSet<>();for(var block:blocks){if(block.measurementType()!=PlsSem.MeasurementType.REFLECTIVE)throw new IllegalArgumentException("The basic factor builder requires reflective blocks; specify a RAM model for other measurement directions.");if(!factorNames.add(block.name()))throw new IllegalArgumentException("Duplicate factor name.");for(int column:block.columns())if(column<0||column>=width||!selected.add(column))throw new IllegalArgumentException("Factor indicators must be distinct, non-overlapping data columns.");}
        int[] columns=selected.stream().mapToInt(Integer::intValue).toArray();int p=columns.length,size=p+blocks.size();List<String> variables=new ArrayList<>();for(int column:columns)variables.add(names.get(column));for(var block:blocks)variables.add("Factor: "+block.name());
        double[][] selectedData=new double[data.length][p];for(int i=0;i<data.length;i++)for(int j=0;j<p;j++)selectedData[i][j]=data[i][columns[j]];
        var centred=Matrices.standardize(selectedData,false,false);double[][] empirical=Matrices.covariance(centred.values(),false);double[] means=centred.means();
        double[][] a=new double[size][size],s=new double[size][size];double[] m=new double[size];List<Parameter> parameters=new ArrayList<>();int[] factors=new int[blocks.size()];Map<Integer,Integer> position=new HashMap<>();for(int i=0;i<p;i++)position.put(columns[i],i);
        for(int j=0;j<blocks.size();j++){
            factors[j]=p+j;int[] indicators=blocks.get(j).columns();int marker=position.get(indicators[0]);double latentVariance=.7*empirical[marker][marker];a[marker][p+j]=1;
            parameters.add(new Parameter("Variance: "+blocks.get(j).name(),List.of(new Location(Kind.COVARIANCE,p+j,p+j)),latentVariance,latentVariance));
            for(int k=0;k<indicators.length;k++){
                int row=position.get(indicators[k]);double variance=empirical[row][row];if(!(variance>0))throw new IllegalArgumentException("Constant SEM indicator: "+names.get(indicators[k]));
                if(k>0){double loading=empirical[row][marker]/latentVariance;parameters.add(new Parameter("Loading: "+blocks.get(j).name()+" → "+names.get(indicators[k]),List.of(new Location(Kind.DIRECTED,row,p+j)),loading,Math.max(.1,Math.sqrt(variance/latentVariance))));}
                parameters.add(new Parameter("Residual variance: "+names.get(indicators[k]),List.of(new Location(Kind.COVARIANCE,row,row)),.3*variance,variance));
            }
        }
        boolean[] endogenous=new boolean[blocks.size()];Set<PlsSem.Edge> connections=new HashSet<>();for(var edge:edges){if(edge.source()<0||edge.target()<0||edge.source()>=blocks.size()||edge.target()>=blocks.size()||edge.source()==edge.target()||!connections.add(edge))throw new IllegalArgumentException("Invalid SEM structural edge.");endogenous[edge.target()]=true;parameters.add(new Parameter("Path: "+blocks.get(edge.source()).name()+" → "+blocks.get(edge.target()).name(),List.of(new Location(Kind.DIRECTED,p+edge.target(),p+edge.source())),.1,1));}
        if(correlateExogenous)for(int i=0;i<blocks.size();i++)for(int j=0;j<i;j++)if(!endogenous[i]&&!endogenous[j])parameters.add(new Parameter("Covariance: "+blocks.get(j).name()+" ↔ "+blocks.get(i).name(),List.of(new Location(Kind.COVARIANCE,p+i,p+j)),0,Math.sqrt(empirical[position.get(blocks.get(i).columns()[0])][position.get(blocks.get(i).columns()[0])]*empirical[position.get(blocks.get(j).columns()[0])][position.get(blocks.get(j).columns()[0])])));
        if(meanStructure)for(int i=0;i<p;i++)parameters.add(new Parameter("Intercept: "+variables.get(i),List.of(new Location(Kind.INTERCEPT,i,0)),means[i],Math.sqrt(empirical[i][i])));
        return new Prepared(new Model(variables,java.util.stream.IntStream.range(0,p).toArray(),a,s,m,parameters),columns,factors);
    }
}
