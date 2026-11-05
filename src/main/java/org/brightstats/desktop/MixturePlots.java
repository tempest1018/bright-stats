package org.brightstats.desktop;

import javafx.geometry.Insets;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.brightstats.stats.PlsMixture;

final class MixturePlots {
    private MixturePlots(){}
    static void add(TabPane tabs,PlsMixture.Result result){var fit=result.mixture();NumberAxis iteration=new NumberAxis(),likelihood=new NumberAxis();iteration.setLabel("Iteration of selected start");likelihood.setLabel("Observed log-likelihood");likelihood.setForceZeroInRange(false);LineChart<Number,Number> trace=new LineChart<>(iteration,likelihood);trace.setAnimated(false);trace.setCreateSymbols(false);trace.setTitle("Selected mixture likelihood development");trace.setMinHeight(400);var series=new XYChart.Series<Number,Number>();series.setName("Observed log-likelihood");for(var step:fit.trace())series.getData().add(new XYChart.Data<>(step.iteration(),step.logLikelihood()));trace.getData().add(series);CategoryAxis classes=new CategoryAxis();NumberAxis proportion=new NumberAxis(0,1,.2);proportion.setLabel("Proportion");BarChart<String,Number> sizes=new BarChart<>(classes,proportion);sizes.setAnimated(false);sizes.setMinHeight(400);sizes.setTitle("Fitted, posterior and hard class proportions");for(int type=0;type<3;type++){var bars=new XYChart.Series<String,Number>();bars.setName(type==0?"Fitted mixture weight":type==1?"Posterior mean":"Hard assignment");for(var segment:fit.segments())bars.getData().add(new XYChart.Data<>("Segment "+(segment.index()+1),type==0?segment.proportion():type==1?segment.posteriorProportion():segment.hardCount()/(double)fit.observations()));sizes.getData().add(bars);}Label note=UiAppearance.label("Posterior probabilities express fitted class uncertainty. Hard labels discard that uncertainty. Multiple starts and variance-floor/weak-class diagnostics must be reviewed before interpreting segments.");note.setWrapText(true);VBox content=new VBox(12,note,trace,sizes);content.setPadding(new Insets(12));ScrollPane scroll=new ScrollPane(content);scroll.setFitToWidth(true);Tab tab=new Tab("Mixture plots",scroll);tab.setClosable(false);tabs.getTabs().add(tab);}
}
