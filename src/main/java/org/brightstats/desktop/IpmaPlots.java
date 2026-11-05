package org.brightstats.desktop;

import javafx.geometry.Insets;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.brightstats.stats.ImportancePerformance;

final class IpmaPlots {
    private IpmaPlots(){}
    static void add(TabPane tabs,ImportancePerformance.Result fit){VBox charts=new VBox(14);charts.setPadding(new Insets(12));var constructs=chart("Construct importance and performance",fit.options().units().toString());var indicators=chart("Indicator importance and performance",fit.options().units().toString());for(var point:fit.constructs())add(constructs,point.name(),point.importance(),point.performance());for(var point:fit.indicators())add(indicators,point.name(),point.importance(),point.performance());charts.getChildren().addAll(UiAppearance.label("Hover a point for its name and values. Negative importance requires the opposite directional interpretation."),constructs,indicators);ScrollPane scroll=new ScrollPane(charts);scroll.setFitToWidth(true);Tab tab=new Tab("Importance–performance plots",scroll);tab.setClosable(false);tabs.getTabs().add(tab);}
    private static ScatterChart<Number,Number> chart(String title,String units){NumberAxis x=new NumberAxis(),y=new NumberAxis(0,100,20);x.setLabel("Importance ("+units+")");x.setForceZeroInRange(true);y.setLabel("Performance (0–100)");var chart=new ScatterChart<Number,Number>(x,y);chart.setTitle(title);chart.setAnimated(false);chart.setPrefSize(800,400);chart.setMinHeight(400);return chart;}
    private static void add(ScatterChart<Number,Number> chart,String name,double importance,double performance){var series=new XYChart.Series<Number,Number>();series.setName(name);var point=new XYChart.Data<Number,Number>(importance,performance,name);series.getData().add(point);chart.getData().add(series);point.nodeProperty().addListener((property,old,node)->{if(node!=null)Tooltip.install(node,new Tooltip(name+"\nImportance: "+importance+"\nPerformance: "+performance));});if(point.getNode()!=null)Tooltip.install(point.getNode(),new Tooltip(name+"\nImportance: "+importance+"\nPerformance: "+performance));}
}
