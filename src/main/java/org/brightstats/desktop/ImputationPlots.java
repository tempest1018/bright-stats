package org.brightstats.desktop;

import javafx.scene.control.*;
import javafx.scene.chart.*;
import javafx.scene.layout.*;
import org.brightstats.stats.ImputedSem;

final class ImputationPlots {
    private ImputationPlots(){}
    static void add(TabPane tabs,ImputedSem.Fit fit){double[][][] trace=fit.meanTraces();if(trace.length==0||trace[0].length==0)return;ComboBox<String> variable=new ComboBox<>();int[] observed=fit.model().observed();for(int i:observed)variable.getItems().add(fit.model().variables().get(i));variable.setAccessibleText("Imputation trace variable");NumberAxis x=new NumberAxis(),y=new NumberAxis();x.setForceZeroInRange(false);y.setForceZeroInRange(false);x.setLabel("Final burn-in iteration");LineChart<Number,Number> chart=new LineChart<>(x,y);chart.setAnimated(false);chart.setCreateSymbols(false);chart.setPrefHeight(430);Runnable refresh=()->{int j=variable.getSelectionModel().getSelectedIndex();if(j<0)return;chart.getData().clear();chart.setTitle("Independent-chain mean traces: "+variable.getValue());y.setLabel("Normal-model mean in original units");for(int c=0;c<trace.length;c++){var series=new XYChart.Series<Number,Number>();series.setName("Chain "+(c+1));for(int t=0;t<trace[c].length;t++)series.getData().add(new XYChart.Data<>(fit.options().imputation().burnIterations()-trace[c].length+t+1,trace[c][t][j]));chart.getData().add(series);}};variable.valueProperty().addListener((o,a,b)->refresh.run());variable.getSelectionModel().selectFirst();Label note=new Label("Parameter traces show the retained end of each independently seeded burn-in chain. Compare mixing across chains; these are not completed-data observations. The reported mean-trace split R-hat is a limited screen, not a convergence proof.");note.setWrapText(true);note.setMinHeight(Region.USE_PREF_SIZE);note.setMaxWidth(Double.MAX_VALUE);VBox panel=new VBox(10,note,variable,chart);VBox.setVgrow(chart,Priority.ALWAYS);panel.setStyle("-fx-padding:12;");Tab tab=new Tab("Imputation traces",panel);tab.setClosable(false);tabs.getTabs().add(tab);}
}
