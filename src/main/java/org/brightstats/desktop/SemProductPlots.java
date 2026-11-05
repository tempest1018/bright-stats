package org.brightstats.desktop;
import java.util.*;
import javafx.geometry.Insets;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.brightstats.stats.*;
final class SemProductPlots {
    private SemProductPlots(){}
    static void add(TabPane tabs,SemProductIndicators.Fit fit,Bootstrap.Result bootstrap){VBox panel=new VBox(12);panel.setPadding(new Insets(12));Label note=UiAppearance.label("Conditional direct slopes in centered, fixed-marker factor units. Unspecified moderator values are zero. Product-indicator SEM is an independent method; ordinary Gaussian parameter tests are not robust for these nonnormal products. Selected bootstrap bounds are pointwise.");note.setWrapText(true);panel.getChildren().add(note);CategoryAxis x=new CategoryAxis();NumberAxis y=new NumberAxis();x.setLabel("Centered-factor probe");y.setLabel("Conditional direct derivative");BarChart<String,Number> chart=new BarChart<>(x,y);chart.setTitle("Covariance-SEM moderation");chart.setAnimated(false);chart.setMinHeight(440);Map<String,XYChart.Series<String,Number>> groups=new LinkedHashMap<>();List<String> names=fit.specification().base().variables();for(var effect:fit.conditional()){String label=names.get(effect.source())+" to "+names.get(effect.target());var series=groups.computeIfAbsent(label,key->{var result=new XYChart.Series<String,Number>();result.setName(key);return result;});series.getData().add(new XYChart.Data<>(effect.probe(),effect.derivative()));}chart.getData().addAll(groups.values());panel.getChildren().add(chart);if(bootstrap!=null){List<String> unavailable=new ArrayList<>();for(var estimate:bootstrap.estimates())if(estimate.parameter().family().equals("Conditional centered-factor derivative")&&(!Double.isFinite(estimate.lower())||!Double.isFinite(estimate.upper())))unavailable.add(estimate.parameter().label());if(!unavailable.isEmpty())panel.getChildren().add(UiAppearance.label("Some conditional intervals are unavailable. Consult resampling accounting; missing values are not zero."));}Tab tab=new Tab("SEM moderation plots",new ScrollPane(panel));tab.setClosable(false);tabs.getTabs().add(tab);}
}
