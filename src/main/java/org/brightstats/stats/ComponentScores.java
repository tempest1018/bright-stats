package org.brightstats.stats;
import java.util.*;

/** Explicit PLS/GSCA score-estimation stage for full-pipeline downstream inference. */
public final class ComponentScores {
    private ComponentScores(){}
    public enum Estimator {PLS,GSCA}
    public record Model(Estimator estimator,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,PlsSem.Options plsOptions,
                        Gsca.Model gscaModel,Gsca.Options gscaOptions,int[] gscaColumns){
        public Model{Objects.requireNonNull(estimator);blocks=List.copyOf(blocks);edges=List.copyOf(edges);gscaColumns=gscaColumns.clone();
            if(estimator==Estimator.PLS){Objects.requireNonNull(plsOptions);if(blocks.isEmpty()||blocks.size()>64||gscaModel!=null||gscaOptions!=null||gscaColumns.length!=0)throw new IllegalArgumentException("Provide one PLS measurement/structural definition.");}
            else{Objects.requireNonNull(gscaModel);Objects.requireNonNull(gscaOptions);if(!blocks.isEmpty()||!edges.isEmpty()||plsOptions!=null||gscaModel.components().size()>64||gscaColumns.length!=gscaModel.indicators().size()||Arrays.stream(gscaColumns).anyMatch(i->i<0)||Arrays.stream(gscaColumns).distinct().count()!=gscaColumns.length)throw new IllegalArgumentException("Provide one GSCA definition and distinct dataset column bindings.");}}
        @Override public int[] gscaColumns(){return gscaColumns.clone();}
        public static Model pls(List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,PlsSem.Options options){return new Model(Estimator.PLS,blocks,edges,options,null,null,new int[0]);}
        public static Model gsca(Gsca.Model model,Gsca.Options options,int[] columns){return new Model(Estimator.GSCA,List.of(),List.of(),null,model,options,columns);}
        public List<String> names(){return estimator==Estimator.PLS?blocks.stream().map(PlsSem.Block::name).toList():gscaModel.components();}
        public List<PlsSem.Edge> paths(){if(estimator==Estimator.PLS)return edges;boolean[][] paths=gscaModel.paths();List<PlsSem.Edge> result=new ArrayList<>();for(int source=0;source<paths.length;source++)for(int target=0;target<paths.length;target++)if(paths[source][target])result.add(new PlsSem.Edge(source,target));return List.copyOf(result);}
    }
    public record Fit(Model model,PlsSem.Fit pls,Gsca.Fit gsca,double[][] scores,List<String> warnings){public Fit{scores=Matrices.copy(scores);warnings=List.copyOf(warnings);}@Override public double[][] scores(){return Matrices.copy(scores);}}
    public static Fit fit(double[][] data,Model model){Matrices.interrupted();int width=Matrices.width(data,3);if((long)data.length*width>5_000_000)throw new IllegalArgumentException("Component stage exceeds five million data cells.");PlsSem.Fit pls=null;Gsca.Fit gsca=null;double[][] scores;List<String>warnings=new ArrayList<>();
        if(model.estimator()==Estimator.PLS){pls=PlsSem.fit(data,model.blocks(),model.edges(),model.plsOptions());if(!pls.converged())throw new IllegalArgumentException("First-stage PLS did not converge.");scores=pls.scores();}
        else{int[] columns=model.gscaColumns();if(Arrays.stream(columns).anyMatch(column->column>=width))throw new IllegalArgumentException("GSCA score binding lies outside the dataset.");double[][] selected=new double[data.length][columns.length];for(int row=0;row<data.length;row++)for(int col=0;col<columns.length;col++)selected[row][col]=data[row][columns[col]];gsca=Gsca.fit(selected,model.gscaModel(),model.gscaOptions());if(!gsca.converged())throw new IllegalArgumentException("First-stage GSCA did not converge.");scores=gsca.scores();warnings.addAll(gsca.warnings());}
        scores=Matrices.standardize(scores,true,true).values();warnings.add("Component scores are sample-centered and scaled to unit sample variance before downstream estimation. They are estimated proxies, not directly observed latent variables.");return new Fit(model,pls,gsca,scores,warnings);
    }
    public static double[][] align(double[][] sample,double[][] original,int[] originalRows){Matrices.interrupted();int columns=Matrices.width(sample,2);if(sample.length!=originalRows.length||Matrices.width(original,2)!=columns)throw new IllegalArgumentException("Score alignment dimensions differ.");double[][] result=Matrices.copy(sample);for(int col=0;col<columns;col++){double cross=0,a=0,b=0;for(int row=0;row<result.length;row++){int source=originalRows[row];if(source<0||source>=original.length)throw new IllegalArgumentException("Score alignment row is outside the original data.");double x=result[row][col],y=original[source][col];cross+=x*y;a+=x*x;b+=y*y;}if(!Double.isFinite(cross)||Math.abs(cross)<=1e-8*Math.sqrt(a*b))throw new IllegalArgumentException("A resampled component cannot be oriented reliably to its original sampled rows.");if(cross<0)for(var row:result)row[col]=-row[col];}return result;}
}
