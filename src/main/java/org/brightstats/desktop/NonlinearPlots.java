package org.brightstats.desktop;
import java.util.*;
import javafx.geometry.Insets;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.brightstats.stats.*;
final class NonlinearPlots {
    private NonlinearPlots(){}
    static void add(TabPane tabs,NonlinearPls.Fit fit,Bootstrap.Result inference){
        VBox plots=new VBox(12);plots.setPadding(new Insets(12));Label note=UiAppearance.label("Composite-score units. Curves vary the focal score while holding other equation parents at their listed probe values; they are not a propagated network response. Bounds are selected pointwise bootstrap intervals, not a simultaneous confidence band. Coincident non-focal probe conditions are plotted once.");note.setWrapText(true);plots.getChildren().add(note);
        if(!fit.curves().isEmpty()){
            var grid=fit.specification().grid();NumberAxis x=new NumberAxis(),y=new NumberAxis();x.setLabel(fit.names().get(grid.source())+" (unit-variance composite score)");y.setLabel(fit.names().get(grid.target())+" (fitted composite score)");x.setForceZeroInRange(false);y.setForceZeroInRange(false);LineChart<Number,Number> curves=new LineChart<>(x,y);curves.setTitle("Conditional structural-equation mean");curves.setAnimated(false);curves.setCreateSymbols(false);curves.setMinHeight(460);
            Map<Integer,Bootstrap.Estimate> intervals=new HashMap<>();if(inference!=null)for(var estimate:inference.estimates())if(estimate.parameter().family().equals("Conditional fitted outcome"))intervals.put(estimate.parameter().column(),estimate);
            Map<String,Map<Integer,Double>> conditions=new LinkedHashMap<>();for(var probe:fit.specification().probes())conditions.put(probe.name(),probe.values());TreeSet<Integer> otherParents=new TreeSet<>();for(var equation:fit.equations())if(equation.target()==grid.target())for(var term:equation.regressors())for(int factor:term.factors())if(factor!=grid.source())otherParents.add(factor);
            Set<String> seen=new HashSet<>();Map<String,List<Integer>> grouped=new LinkedHashMap<>();for(int i=0;i<fit.curves().size();i++)grouped.computeIfAbsent(fit.curves().get(i).probe(),key->new ArrayList<>()).add(i);
            boolean unavailable=false;
            for(var entry:grouped.entrySet()){var values=conditions.getOrDefault(entry.getKey(),Map.of());List<String> labels=new ArrayList<>();for(int parent:otherParents)labels.add(fit.names().get(parent)+"="+values.getOrDefault(parent,0.0));String label=labels.isEmpty()?"No other equation parents":String.join("; ",labels);if(!seen.add(label))continue;
                var mean=new XYChart.Series<Number,Number>();mean.setName(label);var low=new XYChart.Series<Number,Number>();low.setName(label+" · pointwise lower");var high=new XYChart.Series<Number,Number>();high.setName(label+" · pointwise upper");boolean complete=inference!=null;
                for(int index:entry.getValue()){var point=fit.curves().get(index);mean.getData().add(new XYChart.Data<>(point.x(),point.value()));var interval=intervals.get(index);if(interval==null||!Double.isFinite(interval.lower())||!Double.isFinite(interval.upper()))complete=false;else{low.getData().add(new XYChart.Data<>(point.x(),interval.lower()));high.getData().add(new XYChart.Data<>(point.x(),interval.upper()));}}
                curves.getData().add(mean);if(complete){curves.getData().add(low);curves.getData().add(high);}else if(inference!=null)unavailable=true;
            }
            plots.getChildren().add(curves);if(unavailable){Label warning=UiAppearance.label("At least one curve interval is unavailable; its entire bound series is omitted rather than connecting across missing intervals.");warning.setWrapText(true);plots.getChildren().add(warning);}
        }
        if(!fit.effects().isEmpty()){
            int source=fit.specification().grid()==null?fit.effects().getFirst().source():fit.specification().grid().source(),target=fit.specification().grid()==null?fit.effects().getFirst().target():fit.specification().grid().target();CategoryAxis x=new CategoryAxis();NumberAxis y=new NumberAxis();x.setLabel("Composite-score probe");y.setLabel("Local response derivative");BarChart<String,Number> effects=new BarChart<>(x,y);effects.setTitle(fit.names().get(source)+" → "+fit.names().get(target)+": local derivatives");effects.setAnimated(false);effects.setMinHeight(440);
            for(String family:List.of("Direct derivative","Total indirect derivative","Total derivative")){var series=new XYChart.Series<String,Number>();series.setName(family);for(var effect:fit.effects())if(effect.source()==source&&effect.target()==target&&effect.family().equals(family))series.getData().add(new XYChart.Data<>(effect.probe(),effect.value()));if(!series.getData().isEmpty())effects.getData().add(series);}plots.getChildren().add(effects);
        }
        ScrollPane scroll=new ScrollPane(plots);scroll.setFitToWidth(true);Tab tab=new Tab("Nonlinear PLS plots",scroll);tab.setClosable(false);tabs.getTabs().add(tab);
    }
}
