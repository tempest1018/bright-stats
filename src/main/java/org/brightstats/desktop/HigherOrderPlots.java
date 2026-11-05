package org.brightstats.desktop;
import java.util.*;
import javafx.geometry.Insets;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.brightstats.stats.*;
final class HigherOrderPlots {
    private HigherOrderPlots(){}
    static void add(TabPane tabs,HigherOrderPls.Fit fit){VBox plots=new VBox(12);plots.setPadding(new Insets(12));Label note=UiAppearance.label("Higher-order dimensions are measurement relationships: interpret loadings for reflective measurement and weights for formative measurement. These point plots do not show bootstrap uncertainty; inspect the resampling tables. Original lower-order constructs are absent as separate stage-two nodes.");note.setWrapText(true);plots.getChildren().add(note);for(int index=0;index<fit.specification().higherOrder().size();index++){var definition=fit.specification().higherOrder().get(index);int block=fit.specification().retained().length+index;boolean reflective=definition.measurementType()==PlsSem.MeasurementType.REFLECTIVE;CategoryAxis x=new CategoryAxis();NumberAxis y=new NumberAxis();x.setLabel("Lower-order dimension");y.setLabel(reflective?"Dimension loading":"Dimension weight");BarChart<String,Number> chart=new BarChart<>(x,y);chart.setTitle(definition.name()+" · "+definition.measurementType()+" · Mode "+definition.mode());chart.setAnimated(false);chart.setMinHeight(430);var series=new XYChart.Series<String,Number>();series.setName(reflective?"Reflective loading":"Formative weight");for(var dimension:fit.dimensions())if(dimension.higherOrder()==block)series.getData().add(new XYChart.Data<>(dimension.dimension(),reflective?dimension.loading():dimension.weight()));chart.getData().add(series);plots.getChildren().add(chart);}ScrollPane scroll=new ScrollPane(plots);scroll.setFitToWidth(true);Tab tab=new Tab("Higher-order measurement plots",scroll);tab.setClosable(false);tabs.getTabs().add(tab);}
}
