package org.brightstats.desktop;

import java.util.*;
import java.util.function.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;

/** Presentation-only navigation. World coordinates and model history never change on zoom/pan. */
final class ModelViewport extends BorderPane {
    record Point(double x,double y,String label,boolean selected){}
    record Edge(int source,int target){}
    private final Canvas canvas,mini=new Canvas(220,140);
    private final ScrollPane scroll=new ScrollPane();
    private final Scale transform=new Scale(1,1,0,0);
    private final Slider slider=new Slider(.25,2.5,1);
    private final Label percent=new Label("100%");
    private final CheckBox grid=new CheckBox("Grid"),snap=new CheckBox("Snap");
    private final Rectangle marquee=new Rectangle();
    private final Runnable repaint,arrange;
    private List<Point> points=List.of();private List<Edge> edges=List.of();
    private boolean changing,space,panning,selecting;
    private double anchorX,anchorY,anchorH,anchorV;
    private BiPredicate<Double,Double> hit=(x,y)->false;
    private Consumer<Bounds> selected=b->{};
    private BiConsumer<Boolean,Boolean> displaySettings=(g,s)->{};
    ModelViewport(Canvas canvas,Runnable repaint,Runnable arrange){
        this.canvas=canvas;this.repaint=repaint;this.arrange=arrange;getStyleClass().add("model-viewport");marquee.setVisible(false);marquee.setMouseTransparent(true);marquee.setFill(Color.web("#23acc4",.12));marquee.setStroke(Color.web("#23acc4"));marquee.getStrokeDashArray().setAll(5.0,4.0);Group world=new Group(canvas,marquee);world.getTransforms().add(transform);Group content=new Group(world);scroll.setContent(content);scroll.setPannable(false);scroll.setFitToWidth(false);scroll.setFitToHeight(false);scroll.getStyleClass().add("model-scroller");
        Button minus=new Button("−"),plus=new Button("+"),fit=new Button("Fit model"),reset=new Button("100%"),layout=new Button("Arrange");minus.setAccessibleText("Zoom out");plus.setAccessibleText("Zoom in");slider.setAccessibleText("Model zoom");slider.setPrefWidth(150);minus.setOnAction(e->zoom(transform.getX()/1.2));plus.setOnAction(e->zoom(transform.getX()*1.2));fit.setOnAction(e->fit());reset.setOnAction(e->zoom(1));layout.setOnAction(e->arrange.run());layout.disableProperty().bind(canvas.disabledProperty());slider.valueProperty().addListener((o,a,b)->{if(!changing)zoom(b.doubleValue());});grid.setSelected(true);grid.setOnAction(e->{displaySettings.accept(grid.isSelected(),snap.isSelected());repaint.run();});snap.setOnAction(e->displaySettings.accept(grid.isSelected(),snap.isSelected()));ToolBar toolbar=new ToolBar(minus,slider,plus,percent,new Separator(),fit,reset,layout,new Separator(),grid,snap);toolbar.getStyleClass().add("model-toolbar");setTop(toolbar);
        Label overview=new Label("Overview · click to navigate");overview.setStyle("-fx-font-size:10px;");VBox card=new VBox(4,overview,mini);card.getStyleClass().add("overview-card");card.setPadding(new Insets(8));card.setMaxSize(Region.USE_PREF_SIZE,Region.USE_PREF_SIZE);StackPane center=new StackPane(scroll,card);StackPane.setAlignment(card,Pos.BOTTOM_RIGHT);StackPane.setMargin(card,new Insets(14));setCenter(center);mini.setAccessibleText("Model overview. Click to center the viewport.");mini.setOnMousePressed(e->navigate(e.getX()/mini.getWidth(),e.getY()/mini.getHeight()));scroll.hvalueProperty().addListener((o,a,b)->drawMini());scroll.vvalueProperty().addListener((o,a,b)->drawMini());scroll.viewportBoundsProperty().addListener((o,a,b)->drawMini());
        scroll.addEventFilter(ScrollEvent.SCROLL,e->{if(e.isControlDown()){zoom(transform.getX()*(e.getDeltaY()>0?1.12:1/1.12));e.consume();}});
        canvas.addEventFilter(KeyEvent.KEY_PRESSED,e->{if(e.getCode()==KeyCode.SPACE){space=true;e.consume();}else if(e.isControlDown()&&(e.getCode()==KeyCode.EQUALS||e.getCode()==KeyCode.ADD)){zoom(transform.getX()*1.2);e.consume();}else if(e.isControlDown()&&(e.getCode()==KeyCode.MINUS||e.getCode()==KeyCode.SUBTRACT)){zoom(transform.getX()/1.2);e.consume();}else if(e.isControlDown()&&e.getCode()==KeyCode.DIGIT0){zoom(1);e.consume();}});canvas.addEventFilter(KeyEvent.KEY_RELEASED,e->{if(e.getCode()==KeyCode.SPACE){space=false;e.consume();}});canvas.focusedProperty().addListener((o,a,b)->{if(!b)space=false;});
        canvas.addEventFilter(MouseEvent.MOUSE_PRESSED,e->{panning=e.getButton()==MouseButton.MIDDLE||space&&e.getButton()==MouseButton.PRIMARY;selecting=!panning&&e.getButton()==MouseButton.PRIMARY&&e.isShiftDown()&&!hit.test(world(e).getX(),world(e).getY());if(panning||selecting){canvas.requestFocus();anchorX=panning?e.getSceneX():world(e).getX();anchorY=panning?e.getSceneY():world(e).getY();anchorH=scroll.getHvalue();anchorV=scroll.getVvalue();if(selecting){marquee.setX(anchorX);marquee.setY(anchorY);marquee.setWidth(0);marquee.setHeight(0);marquee.setVisible(true);}e.consume();}});
        canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED,e->{if(panning){double width=Math.max(1,canvas.getWidth()*transform.getX()-scroll.getViewportBounds().getWidth()),height=Math.max(1,canvas.getHeight()*transform.getY()-scroll.getViewportBounds().getHeight());scroll.setHvalue(clamp(anchorH-(e.getSceneX()-anchorX)/width));scroll.setVvalue(clamp(anchorV-(e.getSceneY()-anchorY)/height));e.consume();}else if(selecting){double x=Math.max(0,Math.min(canvas.getWidth(),world(e).getX())),y=Math.max(0,Math.min(canvas.getHeight(),world(e).getY()));marquee.setX(Math.min(anchorX,x));marquee.setY(Math.min(anchorY,y));marquee.setWidth(Math.abs(anchorX-x));marquee.setHeight(Math.abs(anchorY-y));e.consume();}});
        canvas.addEventFilter(MouseEvent.MOUSE_RELEASED,e->{if(selecting){if(marquee.getWidth()>2&&marquee.getHeight()>2)selected.accept(new BoundingBox(marquee.getX(),marquee.getY(),marquee.getWidth(),marquee.getHeight()));marquee.setVisible(false);}if(panning||selecting)e.consume();panning=false;selecting=false;});
        Tooltip.install(canvas,new Tooltip("Ctrl + wheel: zoom · Space + drag or middle drag: pan · Shift + drag on empty space: extend selection"));
    }
    void hooks(BiPredicate<Double,Double> hit,Consumer<Bounds> selected,BiConsumer<Boolean,Boolean> displaySettings){this.hit=hit;this.selected=selected;this.displaySettings=displaySettings;}
    boolean grid(){return grid.isSelected();}boolean snap(){return snap.isSelected();}double zoom(){return transform.getX();}
    void display(boolean grid,boolean snap){this.grid.setSelected(grid);this.snap.setSelected(snap);}
    void geometry(List<Point> points,List<Edge> edges){this.points=List.copyOf(points);this.edges=List.copyOf(edges);drawMini();}
    void zoom(double requested){double value=Math.max(.25,Math.min(2.5,requested)),old=transform.getX(),width=scroll.getViewportBounds().getWidth(),height=scroll.getViewportBounds().getHeight(),centerX=scroll.getHvalue()*Math.max(0,canvas.getWidth()-width/old)+width/(2*old),centerY=scroll.getVvalue()*Math.max(0,canvas.getHeight()-height/old)+height/(2*old);transform.setX(value);transform.setY(value);changing=true;slider.setValue(value);changing=false;percent.setText(Math.round(value*100)+"%");scroll.setHvalue(canvas.getWidth()>width/value?clamp((centerX-width/(2*value))/(canvas.getWidth()-width/value)):0);scroll.setVvalue(canvas.getHeight()>height/value?clamp((centerY-height/(2*value))/(canvas.getHeight()-height/value)):0);drawMini();}
    void fit(){if(points.isEmpty()){zoom(1);return;}double minX=points.stream().mapToDouble(Point::x).min().orElse(0)-100,maxX=points.stream().mapToDouble(Point::x).max().orElse(canvas.getWidth())+100,minY=points.stream().mapToDouble(Point::y).min().orElse(0)-80,maxY=points.stream().mapToDouble(Point::y).max().orElse(canvas.getHeight())+80;zoom(Math.min(scroll.getViewportBounds().getWidth()/Math.max(1,maxX-minX),scroll.getViewportBounds().getHeight()/Math.max(1,maxY-minY)));navigate((minX+maxX)/2/canvas.getWidth(),(minY+maxY)/2/canvas.getHeight());}
    private void navigate(double x,double y){double width=scroll.getViewportBounds().getWidth()/transform.getX()/canvas.getWidth(),height=scroll.getViewportBounds().getHeight()/transform.getY()/canvas.getHeight();scroll.setHvalue(width<1?clamp((x-width/2)/(1-width)):0);scroll.setVvalue(height<1?clamp((y-height/2)/(1-height)):0);drawMini();}
    private void drawMini(){var gc=mini.getGraphicsContext2D();gc.setFill(Color.web("#152b3a"));gc.fillRoundRect(0,0,mini.getWidth(),mini.getHeight(),10,10);double sx=mini.getWidth()/canvas.getWidth(),sy=mini.getHeight()/canvas.getHeight();gc.setStroke(Color.web("#7291a6"));gc.setLineWidth(1);for(var edge:edges)if(edge.source()>=0&&edge.target()>=0&&edge.source()<points.size()&&edge.target()<points.size()){var a=points.get(edge.source());var b=points.get(edge.target());gc.strokeLine(a.x()*sx,a.y()*sy,b.x()*sx,b.y()*sy);}for(var point:points){gc.setFill(Color.web(point.selected()?"#5ce1cc":"#7ab9e1"));gc.fillRoundRect((point.x()-76)*sx,(point.y()-48)*sy,152*sx,96*sy,3,3);}double viewWidth=Math.min(canvas.getWidth(),scroll.getViewportBounds().getWidth()/transform.getX()),viewHeight=Math.min(canvas.getHeight(),scroll.getViewportBounds().getHeight()/transform.getY()),left=scroll.getHvalue()*Math.max(0,canvas.getWidth()-viewWidth),top=scroll.getVvalue()*Math.max(0,canvas.getHeight()-viewHeight);gc.setFill(Color.web("#9bd9ff",.08));gc.fillRect(left*sx,top*sy,viewWidth*sx,viewHeight*sy);gc.setStroke(Color.web("#e3f7ff"));gc.setLineWidth(1.5);gc.strokeRect(left*sx+.75,top*sy+.75,Math.max(0,viewWidth*sx-1.5),Math.max(0,viewHeight*sy-1.5));}
    private Point2D world(MouseEvent event){return canvas.sceneToLocal(event.getSceneX(),event.getSceneY());}
    private static double clamp(double value){return Math.max(0,Math.min(1,value));}
}
