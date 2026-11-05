package org.brightstats.desktop;

import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.brightstats.stats.SemSimulation;

final class SimulationPlots {
    private SimulationPlots(){}
    static void add(TabPane tabs,SemSimulation.Result study){
        ComboBox<String> target=new ComboBox<>();study.design().targets().forEach(t->target.getItems().add(t.parameter()));target.setAccessibleText("Power-curve target parameter");NumberAxis x=new NumberAxis(),y=new NumberAxis(0,1,.1);x.setForceZeroInRange(false);x.setLabel("Tested sample size");y.setLabel("Conditional rejection probability");UncertaintyChart chart=new UncertaintyChart(x,y);chart.setAnimated(false);chart.setCreateSymbols(true);chart.setPrefHeight(430);
        Runnable refresh=()->{chart.getData().clear();java.util.List<double[]> envelope=new java.util.ArrayList<>();chart.setTitle("Simulated power: "+target.getValue());var point=new XYChart.Series<Number,Number>();point.setName("Conditional power");var low=new XYChart.Series<Number,Number>();low.setName("Wilson lower 95%");var high=new XYChart.Series<Number,Number>();high.setName("Wilson upper 95%");var desired=new XYChart.Series<Number,Number>();desired.setName("Desired power");for(var row:study.summaries().stream().filter(r->r.parameter().equals(target.getValue())).sorted(java.util.Comparator.comparingInt(SemSimulation.Summary::sampleSize)).toList()){if(Double.isFinite(row.conditionalPower())){envelope.add(new double[]{row.sampleSize(),row.powerLow95(),row.powerHigh95()});point.getData().add(new XYChart.Data<>(row.sampleSize(),row.conditionalPower()));low.getData().add(new XYChart.Data<>(row.sampleSize(),row.powerLow95()));high.getData().add(new XYChart.Data<>(row.sampleSize(),row.powerHigh95()));}desired.getData().add(new XYChart.Data<>(row.sampleSize(),study.design().desiredPower()));}chart.getData().add(point);chart.getData().add(low);chart.getData().add(high);chart.getData().add(desired);chart.envelope(envelope);};target.valueProperty().addListener((o,a,b)->refresh.run());target.getSelectionModel().selectFirst();Label note=new Label("Points describe tested sample sizes only; connecting lines are visual guides. Wilson limits quantify finite-replicate uncertainty. Failed attempts remain in the accounting tables. This is not a guaranteed real-study sample-size requirement.");note.setWrapText(true);note.setMinHeight(Region.USE_PREF_SIZE);note.setMaxWidth(Double.MAX_VALUE);VBox panel=new VBox(10,note,target,chart);VBox.setVgrow(chart,Priority.ALWAYS);panel.setStyle("-fx-padding:12;");Tab tab=new Tab("Power curves",panel);tab.setClosable(false);tabs.getTabs().add(tab);
    }
}
