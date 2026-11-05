package org.brightstats.desktop;
import java.util.*;import javafx.scene.chart.*;import javafx.scene.shape.Polygon;import javafx.scene.paint.Color;
/** Wilson envelopes use only existing simulation summaries. Connecting edges are guides. */
final class UncertaintyChart extends LineChart<Number,Number>{
 final Polygon ribbon=new Polygon();List<double[]> rows=List.of();
 UncertaintyChart(NumberAxis x,NumberAxis y){super(x,y);ribbon.setFill(Color.web("#228bb8",.19));ribbon.setMouseTransparent(true);ribbon.setManaged(false);}
 void envelope(List<double[]> values){rows=values.stream().map(double[]::clone).toList();getProperties().put("brightstats.ribbon",rows);requestChartLayout();}
 void visibilityChanged(){ribbon.setVisible(!ChartTools.hidden(this).contains(1)&&!ChartTools.hidden(this).contains(2));requestChartLayout();}
 @Override protected void layoutPlotChildren(){super.layoutPlotChildren();getPlotChildren().remove(ribbon);ribbon.getPoints().clear();if(ChartTools.hidden(this).contains(1)||ChartTools.hidden(this).contains(2))return;for(double[] row:rows)ribbon.getPoints().addAll(getXAxis().getDisplayPosition(row[0]),getYAxis().getDisplayPosition(row[1]));for(int i=rows.size()-1;i>=0;i--){var row=rows.get(i);ribbon.getPoints().addAll(getXAxis().getDisplayPosition(row[0]),getYAxis().getDisplayPosition(row[2]));}if(rows.size()>=2)getPlotChildren().addFirst(ribbon);}
}
