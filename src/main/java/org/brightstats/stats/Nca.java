package org.brightstats.stats;

import java.util.*;

/** Positive necessity: CE-FDH and ceiling regression, with explicit empirical/theoretical scopes. */
public final class Nca {
    private Nca(){}
    public record Bounds(double minimum,double maximum){public Bounds{if(!Double.isFinite(minimum)||!Double.isFinite(maximum)||!(maximum>minimum)||!Double.isFinite(maximum-minimum))throw new IllegalArgumentException("NCA scope needs finite, increasing bounds.");}public double range(){return maximum-minimum;}}
    public record Options(int steps,List<Bounds> conditionScopes,Bounds outcomeScope,double exclusionLimit,double exclusionStep,double accuracyTolerance){
        public Options{conditionScopes=conditionScopes==null?List.of():List.copyOf(conditionScopes);if(steps<1||steps>1000||!Double.isFinite(exclusionLimit)||exclusionLimit<0||exclusionLimit>.5||!Double.isFinite(exclusionStep)||exclusionStep<=0||exclusionLimit/exclusionStep>1000||!Double.isFinite(accuracyTolerance)||accuracyTolerance<0||accuracyTolerance>.01)throw new IllegalArgumentException("Invalid NCA step, sensitivity or accuracy settings.");}
        public static Options defaults(){return new Options(10,List.of(),null,.05,.005,1e-12);}
    }
    public enum Requirement {NOT_NECESSARY,REQUIRED,BEYOND_SCOPE}
    public record Point(double x,double y){}
    public record Ceiling(String method,boolean regressionAvailable,double effectSize,double ceilingArea,
                          double slope,double intercept,int observationsAbove,double accuracyPercent,
                          double conditionInefficiencyPercent,double outcomeInefficiencyPercent,
                          double relativeInefficiencyPercent,double absoluteInefficiency){}
    public record Bottleneck(double outcomePercent,double outcome,double outcomePercentile,Requirement requirement,
                             double requiredCondition,double rawRequiredCondition,double conditionPercent,
                             double countBelow,double percentileBelow){}
    public record Sensitivity(double excludedProbability,double empiricalEffectSize,double deltaEmpirical,
                              double theoreticalEffectSize,double deltaTheoretical,double deltaDifference){}
    public record Condition(String name,Bounds scope,double scopeArea,List<Point> ceilingPoints,Ceiling ceFdh,Ceiling crFdh,
                            List<Bottleneck> ceBottlenecks,List<Bottleneck> crBottlenecks,List<Sensitivity> sensitivity,List<String> warnings){
        public Condition{ceilingPoints=List.copyOf(ceilingPoints);ceBottlenecks=List.copyOf(ceBottlenecks);crBottlenecks=List.copyOf(crBottlenecks);sensitivity=List.copyOf(sensitivity);warnings=List.copyOf(warnings);}
    }
    public record Fit(int observations,String outcomeName,Bounds outcomeScope,Options options,List<Condition> conditions,String percentileDefinition){public Fit{conditions=List.copyOf(conditions);}}
    private record Geometry(List<Point> frontier,double area,double slope,double intercept,double regressionArea){}
    public static Fit fit(double[][] predictors,double[] outcome,List<String> names,String outcomeName,Options options){
        int p=Matrices.width(predictors,3);if(outcome.length!=predictors.length||!Arrays.stream(outcome).allMatch(Double::isFinite)||names.size()!=p||new HashSet<>(names).size()!=p||names.stream().anyMatch(v->v==null||v.isBlank())||outcomeName==null||outcomeName.isBlank()||!options.conditionScopes().isEmpty()&&options.conditionScopes().size()!=p)throw new IllegalArgumentException("Invalid NCA data, labels or condition scopes.");
        Bounds yScope=scope(outcome,options.outcomeScope());List<Condition> conditions=new ArrayList<>();
        for(int j=0;j<p;j++){Matrices.interrupted();double[] x=new double[outcome.length];for(int i=0;i<x.length;i++)x[i]=predictors[i][j];Bounds xScope=scope(x,options.conditionScopes().isEmpty()?null:options.conditionScopes().get(j));conditions.add(condition(names.get(j),x,outcome,xScope,yScope,options));}
        return new Fit(outcome.length,outcomeName,yScope,options,conditions,"Percentiles/counts use the number of observations strictly below the threshold; NOT_NECESSARY is distinct from a missing numeric result.");
    }
    private static Condition condition(String name,double[] x,double[] y,Bounds xs,Bounds ys,Options options){
        double scope=xs.range()*ys.range();if(!(scope>0)||!Double.isFinite(scope))throw new IllegalArgumentException("NCA scope area exceeds the supported finite numeric range; rescale the variables.");double[] nx=normalize(x,xs),ny=normalize(y,ys);Geometry geometry=geometry(nx,ny);List<Point> frontier=geometry.frontier();boolean reg=frontier.size()>1;List<String> warnings=new ArrayList<>();
        double ceCondition=frontier.size()>1?100*(1-frontier.get(frontier.size()-1).x()):Double.NaN,ceOutcome=frontier.size()>1?100*frontier.get(0).y():Double.NaN;
        Ceiling ce=ceiling("CE-FDH",false,geometry.area(),scope,Double.NaN,Double.NaN,0,x.length,ceCondition,ceOutcome);
        int above=0;if(reg)for(int i=0;i<x.length;i++)if(ny[i]>geometry.intercept()+geometry.slope()*nx[i]+options.accuracyTolerance())above++;
        double slope=reg?geometry.slope()*ys.range()/xs.range():Double.NaN,intercept=reg?ys.minimum()+geometry.intercept()*ys.range()-slope*xs.minimum():Double.NaN;
        if(reg&&(!Double.isFinite(slope)||!Double.isFinite(intercept)||slope==0&&geometry.slope()>0))warnings.add("Raw regression coefficients exceed reliable numeric range; scope-normalized geometry is retained for effects and bottlenecks.");
        double crCondition=reg?100*(1-clamp((1-geometry.intercept())/geometry.slope())):Double.NaN,crOutcome=reg?100*clamp(geometry.intercept()):Double.NaN;
        Ceiling cr=ceiling("CR-FDH",reg,reg?geometry.regressionArea():0,scope,slope,intercept,above,x.length,crCondition,crOutcome);
        if(!reg)warnings.add("A single ceiling point provides no identified ceiling regression; CR-FDH effect is zero and coefficients are unavailable.");
        // Preserve observed frontier values exactly: round-tripping normalized coordinates
        // can move a boundary by one ULP and incorrectly include a tied observation in a strict CDF.
        Map<Double,Double> rawX=new HashMap<>(),rawY=new HashMap<>();for(int i=0;i<x.length;i++){Double priorX=rawX.put(nx[i],x[i]),priorY=rawY.put(ny[i],y[i]);if(priorX!=null&&priorX!=x[i]||priorY!=null&&priorY!=y[i])throw new IllegalArgumentException("NCA scope normalization cannot distinguish observed values; use a narrower scope or rescale the variables.");}
        List<Point> rawFrontier=frontier.stream().map(v->new Point(rawX.get(v.x()),rawY.get(v.y()))).toList();
        List<Bottleneck> ceRows=new ArrayList<>(),crRows=new ArrayList<>();double[] sortedX=x.clone(),sortedY=y.clone();Arrays.sort(sortedX);Arrays.sort(sortedY);
        for(int step=0;step<=options.steps();step++){
            double level=(double)step/options.steps(),target=ys.minimum()+level*ys.range();Point selected=rawFrontier.get(rawFrontier.size()-1);for(Point point:rawFrontier)if(target<=point.y()){selected=point;break;}
            boolean ceBeyond=target>rawFrontier.get(rawFrontier.size()-1).y()+1e-12*ys.range();ceRows.add(bottleneck(level,target,(selected.x()-xs.minimum())/xs.range(),selected.x(),ceBeyond,xs,sortedX,sortedY));
            double required=reg?(target-intercept)/slope:Double.NaN;
            crRows.add(reg?bottleneck(level,target,(level-geometry.intercept())/geometry.slope(),required,false,xs,sortedX,sortedY):new Bottleneck(level*100,target,percentile(sortedY,target),Requirement.NOT_NECESSARY,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN));
        }
        List<Sensitivity> sensitivity=new ArrayList<>();double prior=0,priorTheory=0;int grid=0;
        for(double threshold=0;threshold<=options.exclusionLimit()+1e-9;threshold+=options.exclusionStep()){
            Matrices.interrupted();if(++grid>1002)throw new IllegalArgumentException("NCA sensitivity grid is too large.");List<Point> curve=threshold==0?frontier:probabilityCeiling(nx,ny,threshold);double empirical=area(curve),theory=threshold==0?0:threshold*(1-Math.log(threshold));double de=threshold==0?Double.NaN:empirical-prior,dt=threshold==0?Double.NaN:theory-priorTheory;sensitivity.add(new Sensitivity(threshold,empirical,de,theory,dt,de-dt));prior=empirical;priorTheory=theory;
        }
        return new Condition(name,xs,scope,rawFrontier,ce,cr,ceRows,crRows,sensitivity,warnings);
    }
    private static Ceiling ceiling(String method,boolean regression,double effect,double scope,double slope,double intercept,int above,int n,double condition,double outcome){
        double inefficiency=condition+outcome-condition*outcome/100;return new Ceiling(method,regression,effect,effect*scope,slope,intercept,above,100.0*(n-above)/n,condition,outcome,inefficiency,inefficiency*scope/100);
    }
    private static Bottleneck bottleneck(double level,double target,double required,double raw,boolean beyond,Bounds xs,double[] sortedX,double[] sortedY){
        if(required<=1e-12)return new Bottleneck(level*100,target,percentile(sortedY,target),Requirement.NOT_NECESSARY,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN);
        if(!Double.isFinite(raw))raw=xs.minimum()+required*xs.range();double value=Math.max(xs.minimum(),Math.min(xs.maximum(),raw));int count=below(sortedX,value);return new Bottleneck(level*100,target,percentile(sortedY,target),beyond||required>1+1e-12?Requirement.BEYOND_SCOPE:Requirement.REQUIRED,value,raw,100*clamp(required),count,100.0*count/sortedX.length);
    }
    static double[] effects(double[] x,double[] y,Bounds xs,Bounds ys){Geometry geometry=geometry(normalize(x,xs),normalize(y,ys));return new double[]{geometry.area(),geometry.frontier().size()>1?geometry.regressionArea():0};}
    private static Geometry geometry(double[] x,double[] y){
        TreeMap<Double,Double> maxima=new TreeMap<>();for(int i=0;i<x.length;i++)maxima.merge(x[i],y[i],Math::max);List<Point> frontier=new ArrayList<>();double highest=Double.NEGATIVE_INFINITY;for(var point:maxima.entrySet())if(point.getValue()>highest){highest=point.getValue();frontier.add(new Point(point.getKey(),highest));}
        double slope=Double.NaN,intercept=Double.NaN,regressionArea=0;if(frontier.size()>1){double mx=0,my=0;for(Point point:frontier){mx+=point.x()/frontier.size();my+=point.y()/frontier.size();}double xx=0,xy=0;for(Point point:frontier){xx+=(point.x()-mx)*(point.x()-mx);xy+=(point.x()-mx)*(point.y()-my);}slope=xy/xx;intercept=my-slope*mx;if(!(slope>0)||!Double.isFinite(slope)||!Double.isFinite(intercept))throw new IllegalArgumentException("NCA ceiling spread is outside the supported numerical range; rescale the variables or scope.");regressionArea=lineArea(slope,intercept);}
        return new Geometry(List.copyOf(frontier),area(frontier),slope,intercept,regressionArea);
    }
    private static double area(List<Point> points){double total=0,lastX=0,lastY=0;for(Point point:points){total+=(point.x()-lastX)*(1-lastY);lastX=point.x();lastY=point.y();}total+=(1-lastX)*(1-lastY);return clamp(total);}
    private static double lineArea(double slope,double intercept){List<Double> points=new ArrayList<>(List.of(0.0,1.0));if(slope!=0)for(double crossing:new double[]{-intercept/slope,(1-intercept)/slope})if(crossing>0&&crossing<1)points.add(crossing);Collections.sort(points);double total=0;for(int i=1;i<points.size();i++){double a=points.get(i-1),b=points.get(i);total+=(b-a)*(2-clamp(slope*a+intercept)-clamp(slope*b+intercept))*.5;}return clamp(total);}
    private static List<Point> probabilityCeiling(double[] x,double[] y,double threshold){
        int required=(int)Math.floor(threshold*x.length)+1;while(required<=x.length&&(double)required/x.length<=threshold)required++;while(required>1&&(double)(required-1)/x.length>threshold)required--;final int capacity=required;
        Integer[] order=new Integer[x.length];for(int i=0;i<order.length;i++)order[i]=i;Arrays.sort(order,Comparator.comparingDouble(i->x[i]));PriorityQueue<Double> top=new PriorityQueue<>();List<Point> points=new ArrayList<>();
        for(int i=0;i<order.length;){Matrices.interrupted();double current=x[order[i]];do{top.add(y[order[i]]);if(top.size()>capacity)top.remove();i++;}while(i<order.length&&x[order[i]]==current);if(top.size()==capacity)points.add(new Point(current,top.element()));}return points;
    }
    private static Bounds scope(double[] values,Bounds requested){double min=Arrays.stream(values).min().orElseThrow(),max=Arrays.stream(values).max().orElseThrow();if(!(max>min))throw new IllegalArgumentException("NCA needs variation in each condition and outcome.");if(requested==null)return new Bounds(min,max);if(min<requested.minimum()||max>requested.maximum())throw new IllegalArgumentException("NCA declared scope excludes observed values.");return requested;}
    private static double[] normalize(double[] values,Bounds scope){double[] normalized=new double[values.length];for(int i=0;i<values.length;i++)normalized[i]=(values[i]-scope.minimum())/scope.range();return normalized;}
    private static int below(double[] sorted,double value){int low=0,high=sorted.length;while(low<high){int middle=(low+high)>>>1;if(sorted[middle]<value)low=middle+1;else high=middle;}return low;}
    private static double percentile(double[] sorted,double value){return 100.0*below(sorted,value)/sorted.length;}
    private static double clamp(double value){return Math.max(0,Math.min(1,value));}
}
