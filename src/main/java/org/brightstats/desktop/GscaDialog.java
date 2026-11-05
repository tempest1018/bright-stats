package org.brightstats.desktop;

import java.util.*;
import com.google.gson.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.stage.Window;
import org.brightstats.stats.*;

final class GscaDialog {
    record Settings(Gsca.Options options,Gsca.Model customModel){Settings{Objects.requireNonNull(options);}}
    private final Dialog<Settings> dialog=new Dialog<>();private final TextField iterations=new TextField(),tolerance=new TextField();private final CheckBox custom=new CheckBox("Use an explicit measurement/weight/path specification");private final TextArea model=new TextArea();private final Label error=new Label();private Settings settings;
    private GscaDialog(Window owner,List<String> names,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,Settings previous){
        dialog.initOwner(owner);dialog.setTitle("Generalized structured component analysis");var options=previous==null?Gsca.Options.defaults():previous.options();iterations.setText(Integer.toString(options.maxIterations()));tolerance.setText(Double.toString(options.tolerance()));custom.setSelected(previous!=null&&previous.customModel()!=null);Gson gson=new GsonBuilder().setPrettyPrinting().create();
        try{model.setText(gson.toJson(custom.isSelected()?previous.customModel():Gsca.fromBlocks(names,blocks,edges).model()));}catch(IllegalArgumentException problem){error.setText(problem.getMessage());}
        model.setPrefRowCount(15);model.setEditable(custom.isSelected());custom.selectedProperty().addListener((o,a,b)->model.setEditable(b));
        Label explanation=UiAppearance.label("Joint least squares: indicator measurement and component structural equations. Component scores have unit population variance. Basic mode uses the current diagram; explicit masks allow cross-loadings and overlapping weights. Indicator names identify data columns.");explanation.setWrapText(true);
        VBox basic=new VBox(8,explanation,UiAppearance.label("Maximum iterations"),iterations,UiAppearance.label("Absolute objective improvement tolerance (sum of squared residuals)"),tolerance);basic.setPadding(new Insets(12));VBox advanced=new VBox(8,custom,model);advanced.setPadding(new Insets(12));TabPane tabs=new TabPane(new Tab("Estimation",basic),new Tab("Explicit specification",advanced));for(var tab:tabs.getTabs())tab.setClosable(false);error.setWrapText(true);VBox content=new VBox(8,tabs,error);content.setPrefWidth(700);content.setPadding(new Insets(12));dialog.getDialogPane().setContent(content);dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK,ButtonType.CANCEL);dialog.setResizable(true);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION,event->{settings=null;try{var selected=new Gsca.Options(Integer.parseInt(iterations.getText().trim()),Double.parseDouble(tolerance.getText().trim()));Gsca.Model explicit=custom.isSelected()?gson.fromJson(model.getText(),Gsca.Model.class):null;if(custom.isSelected()&&explicit==null)throw new IllegalArgumentException("Provide a GSCA model.");if(explicit!=null)columns(explicit,names);else Gsca.fromBlocks(names,blocks,edges);settings=new Settings(selected,explicit);}catch(Exception problem){event.consume();error.setText(problem.getMessage());}});dialog.setResultConverter(button->button==ButtonType.OK?settings:null);
    }
    static int[] columns(Gsca.Model model,List<String> names){int[] columns=new int[model.indicators().size()];for(int i=0;i<columns.length;i++){columns[i]=names.indexOf(model.indicators().get(i));if(columns[i]<0)throw new IllegalArgumentException("GSCA indicator is not a data column: "+model.indicators().get(i));}return columns;}
    static Optional<Settings> show(Window owner,List<String> names,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,Settings previous){return new GscaDialog(owner,names,blocks,edges,previous).dialog.showAndWait();}
    static Settings smokeTest(Window owner,List<String> names,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges){var test=new GscaDialog(owner,names,blocks,edges,null);test.dialog.show();test.iterations.setText("0");((Button)test.dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();if(!test.dialog.isShowing()||test.settings!=null)throw new AssertionError("Invalid GSCA iterations accepted");test.iterations.setText("3000");test.custom.setSelected(true);((Button)test.dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();if(test.dialog.getResult()==null||test.dialog.getResult().customModel()==null)throw new AssertionError("GSCA explicit model lost");return test.dialog.getResult();}
}
