package org.brightstats.desktop;

import java.util.Locale;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.brightstats.stats.Nca;

final class NcaPlots {
    private NcaPlots(){}
    static void add(TabPane tabs,Nca.Fit fit,double[][] predictors,double[] outcome){
        if(predictors.length!=fit.observations()||outcome.length!=fit.observations())throw new IllegalArgumentException("NCA plot observations differ from the fitted report.");VBox charts=new VBox(16);charts.setPadding(new Insets(12));
        for(int j=0;j<fit.conditions().size();j++){
            var condition=fit.conditions().get(j);var xs=condition.scope();var ys=fit.outcomeScope();Canvas canvas=new Canvas(700,430);var g=canvas.getGraphicsContext2D();g.setFill(Color.WHITE);g.fillRect(0,0,700,430);double left=70,top=30,width=590,height=335;java.util.function.DoubleUnaryOperator px=x->left+width*(x-xs.minimum())/xs.range(),py=y->top+height-height*(y-ys.minimum())/ys.range();
            g.setStroke(Color.LIGHTGRAY);g.setFill(Color.DIMGRAY);for(int tick=0;tick<=5;tick++){double x=xs.minimum()+xs.range()*tick/5,y=ys.minimum()+ys.range()*tick/5;g.strokeLine(px.applyAsDouble(x),top,px.applyAsDouble(x),top+height);g.strokeLine(left,py.applyAsDouble(y),left+width,py.applyAsDouble(y));g.fillText(label(x),px.applyAsDouble(x)-15,top+height+20);g.fillText(label(y),4,py.applyAsDouble(y)+4);}
            g.setFill(Color.web("#2563ab",.6));for(int i=0;i<outcome.length;i++){if(predictors[i].length!=fit.conditions().size())throw new IllegalArgumentException("Invalid NCA plot width.");g.fillOval(px.applyAsDouble(predictors[i][j])-2,py.applyAsDouble(outcome[i])-2,4,4);}
            g.setStroke(Color.web("#087f75"));g.setLineWidth(2);double lastX=xs.minimum(),lastY=ys.minimum();for(var point:condition.ceilingPoints()){g.strokeLine(px.applyAsDouble(lastX),py.applyAsDouble(lastY),px.applyAsDouble(point.x()),py.applyAsDouble(lastY));g.strokeLine(px.applyAsDouble(point.x()),py.applyAsDouble(lastY),px.applyAsDouble(point.x()),py.applyAsDouble(point.y()));lastX=point.x();lastY=point.y();}g.strokeLine(px.applyAsDouble(lastX),py.applyAsDouble(lastY),left+width,py.applyAsDouble(lastY));
            var line=condition.crFdh();if(line.regressionAvailable()&&Double.isFinite(line.slope())&&Double.isFinite(line.intercept())){double start=Math.max(xs.minimum(),(ys.minimum()-line.intercept())/line.slope()),end=Math.min(xs.maximum(),(ys.maximum()-line.intercept())/line.slope());if(end>=start){g.setStroke(Color.web("#cb6b0a"));g.setLineDashes(7,4);g.strokeLine(px.applyAsDouble(start),py.applyAsDouble(line.slope()*start+line.intercept()),px.applyAsDouble(end),py.applyAsDouble(line.slope()*end+line.intercept()));g.setLineDashes();}}
            g.setFill(Color.BLACK);g.fillText(condition.name()+" (condition)",left+width*.35,405);g.fillText(fit.outcomeName()+" (outcome)",left,16);Label title=new Label(condition.name()+" → "+fit.outcomeName()+" · CE-FDH (teal steps), CR-FDH (orange dashed), observations (blue)");title.setWrapText(true);charts.getChildren().addAll(title,canvas);
        }
        charts.getProperties().put("brightstats.svg",(java.util.function.Supplier<String>)()->org.brightstats.report.SvgGraphics.nca(fit,predictors,outcome));
        ScrollPane scroll=new ScrollPane(charts);scroll.setFitToWidth(true);Tab tab=new Tab("Ceiling plots",scroll);tab.setClosable(false);tabs.getTabs().add(tab);
    }
    private static String label(double value){return String.format(Locale.ROOT,"%.4g",value);}
}
