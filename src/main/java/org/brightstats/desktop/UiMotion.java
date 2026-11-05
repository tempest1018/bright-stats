package org.brightstats.desktop;

import java.util.*;
import javafx.animation.*;
import javafx.scene.Node;
import javafx.util.Duration;
import org.brightstats.data.UserPreferences;

/** Short interaction feedback only; never animates numerical values or chart axes. */
final class UiMotion {
    private UiMotion(){}
    private static final Map<Node,Animation> active=new HashMap<>();
    private static final Map<Node,Runnable> finishers=new HashMap<>();
    static void stop(Node node){Animation old=active.remove(node);Runnable finish=finishers.remove(node);if(old!=null)old.stop();if(finish!=null)finish.run();}
    static void stopAll(){for(Node node:new ArrayList<>(active.keySet()))stop(node);}
    static void play(Node node,Animation animation,Runnable finish){stop(node);if(UiAppearance.effectiveMotion()!=UserPreferences.Motion.FULL){finish.run();return;}active.put(node,animation);finishers.put(node,finish);animation.setOnFinished(event->{active.remove(node);finishers.remove(node);finish.run();});animation.play();}
    static void reveal(Node node){
        if(node==null)return;stop(node);
        var mode=UiAppearance.effectiveMotion();
        if(mode==UserPreferences.Motion.OFF){node.setOpacity(1);return;}
        FadeTransition transition=new FadeTransition(Duration.millis(mode==UserPreferences.Motion.FULL?170:60),node);
        transition.setFromValue(.9);transition.setToValue(1);transition.setInterpolator(Interpolator.EASE_OUT);
        active.put(node,transition);finishers.put(node,()->node.setOpacity(1));transition.setOnFinished(event->{active.remove(node);finishers.remove(node);node.setOpacity(1);});transition.play();
    }
}
