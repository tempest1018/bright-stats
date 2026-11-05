package org.brightstats.desktop;
import java.util.*;import javafx.animation.*;import javafx.beans.property.*;import javafx.scene.Node;import javafx.util.Duration;
/** Logical geometry commits immediately; this optional projection animates only presentation. */
final class ModelMotion {
 private final Node owner;private final Runnable repaint;private Map<Integer,double[]> positions=Map.of();
 ModelMotion(Node owner,Runnable repaint){this.owner=owner;this.repaint=repaint;}
 Map<Integer,double[]> positions(){return positions;}
 void finish(){UiMotion.stop(owner);positions=Map.of();}
 void arrange(Map<Integer,double[]> from,Map<Integer,double[]> to){finish();if(UiAppearance.effectiveMotion()!=org.brightstats.data.UserPreferences.Motion.FULL||from.isEmpty()||!from.keySet().equals(to.keySet())){repaint.run();return;}DoubleProperty fraction=new SimpleDoubleProperty(0);fraction.addListener((o,a,b)->{Map<Integer,double[]> frame=new HashMap<>();for(int index:to.keySet()){double[] start=from.get(index),end=to.get(index);frame.put(index,new double[]{start[0]+(end[0]-start[0])*b.doubleValue(),start[1]+(end[1]-start[1])*b.doubleValue()});}positions=Map.copyOf(frame);repaint.run();});Map<Integer,double[]> initial=new HashMap<>();from.forEach((k,v)->initial.put(k,v.clone()));positions=Map.copyOf(initial);Timeline animation=new Timeline(new KeyFrame(Duration.ZERO,new KeyValue(fraction,0)),new KeyFrame(Duration.millis(220),new KeyValue(fraction,1,Interpolator.EASE_BOTH)));UiMotion.play(owner,animation,()->{positions=Map.of();repaint.run();});repaint.run();}
}
