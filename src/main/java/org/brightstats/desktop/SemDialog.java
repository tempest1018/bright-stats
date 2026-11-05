package org.brightstats.desktop;

import java.util.*;
import com.google.gson.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.stage.Window;
import org.brightstats.stats.*;

final class SemDialog {
    record Settings(CovarianceSem.Options options,boolean correlateExogenous,SemModel.Model customModel){Settings{Objects.requireNonNull(options);}}
    private final Dialog<Settings> dialog=new Dialog<>();
    private final TextField iterations=new TextField(),gradient=new TextField(),function=new TextField(),confidence=new TextField();
    private final ComboBox<CovarianceSem.Likelihood> likelihood=new ComboBox<>();
    private final ComboBox<CovarianceSem.Information> information=new ComboBox<>();
    private final CheckBox mean=new CheckBox("Estimate observed intercepts; fix latent means at zero"),correlate=new CheckBox("Correlate exogenous factors"),custom=new CheckBox("Use the explicit RAM model below");
    private final TextArea ram=new TextArea();private final Label error=new Label();private Settings settings;
    private final List<String> names;private final double[][] data;private final List<PlsSem.Block> blocks;private final List<PlsSem.Edge> edges;
    private final Gson gson=new GsonBuilder().setPrettyPrinting().create();
    private SemDialog(Window owner,List<String> names,double[][] data,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,Settings previous){
        this.names=names;this.data=data;this.blocks=blocks;this.edges=edges;dialog.initOwner(owner);dialog.setTitle("CB-SEM / CFA — maximum likelihood");
        var options=previous==null?CovarianceSem.Options.defaults():previous.options();iterations.setText(Integer.toString(options.maxIterations()));gradient.setText(Double.toString(options.gradientTolerance()));function.setText(Double.toString(options.functionTolerance()));confidence.setText(Double.toString(options.confidence()));
        likelihood.getItems().setAll(CovarianceSem.Likelihood.values());likelihood.setValue(options.likelihood());information.getItems().setAll(CovarianceSem.Information.values());information.setValue(options.information());mean.setSelected(options.meanStructure());correlate.setSelected(previous!=null&&previous.correlateExogenous());custom.setSelected(previous!=null&&previous.customModel()!=null);
        ram.setPrefRowCount(15);ram.setWrapText(false);ram.setEditable(custom.isSelected());if(custom.isSelected())ram.setText(gson.toJson(previous.customModel()));else regenerate();custom.selectedProperty().addListener((o,a,b)->ram.setEditable(b));
        Button regenerate=UiAppearance.button("Generate RAM from current reflective diagram");regenerate.setOnAction(event->{try{regenerate();error.setText("");}catch(Exception problem){error.setText(problem.getMessage());}});
        Label explanation=UiAppearance.label("Basic mode fixes each factor's first loading to 1 and estimates its variance. Explicit RAM supports fixed values, shared parameter locations (equality constraints), covariances, intercepts and optional bounds. Observed variable names must match data columns.");explanation.setWrapText(true);
        VBox basic=new VBox(8,UiAppearance.label("Likelihood (NORMAL: covariance / N; WISHART: / (N−1))"),likelihood,UiAppearance.label("Information matrix for standard errors"),information,mean,correlate,UiAppearance.label("Maximum iterations"),iterations,UiAppearance.label("Gradient tolerance"),gradient,UiAppearance.label("Function tolerance"),function,UiAppearance.label("Confidence level, e.g. 0.95"),confidence);basic.setPadding(new Insets(12));
        VBox advanced=new VBox(8,explanation,custom,regenerate,ram);advanced.setPadding(new Insets(12));TabPane tabs=new TabPane(new Tab("Estimation",basic),new Tab("Explicit RAM model",advanced));for(Tab tab:tabs.getTabs())tab.setClosable(false);
        error.setWrapText(true);VBox content=new VBox(8,tabs,error);content.setPadding(new Insets(12));content.setPrefWidth(720);dialog.getDialogPane().setContent(content);dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK,ButtonType.CANCEL);dialog.setResizable(true);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION,event->{settings=null;try{
            var selected=new CovarianceSem.Options(Integer.parseInt(iterations.getText().trim()),Double.parseDouble(gradient.getText().trim()),Double.parseDouble(function.getText().trim()),likelihood.getValue(),mean.isSelected(),Double.parseDouble(confidence.getText().trim()),information.getValue());
            SemModel.Model model=custom.isSelected()?gson.fromJson(ram.getText(),SemModel.Model.class):null;if(custom.isSelected()&&model==null)throw new IllegalArgumentException("Provide a RAM model.");
            if(model!=null)columns(model,names);else SemModel.reflective(names,data,blocks,edges,correlate.isSelected(),mean.isSelected());settings=new Settings(selected,correlate.isSelected(),model);
        }catch(Exception problem){event.consume();error.setText(problem.getMessage());}});dialog.setResultConverter(button->button==ButtonType.OK?settings:null);
    }
    private void regenerate(){try{ram.setText(gson.toJson(SemModel.reflective(names,data,blocks,edges,correlate.isSelected(),mean.isSelected()).model()));}catch(IllegalArgumentException problem){ram.setText("");error.setText(problem.getMessage()+" An explicit RAM model can be supplied instead.");}}
    static int[] columns(SemModel.Model model,List<String> names){int[] observed=model.observed(),columns=new int[observed.length];for(int i=0;i<observed.length;i++){columns[i]=names.indexOf(model.variables().get(observed[i]));if(columns[i]<0)throw new IllegalArgumentException("RAM observed variable is not a data column: "+model.variables().get(observed[i]));}return columns;}
    static Optional<Settings> show(Window owner,List<String> names,double[][] data,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges,Settings previous){return new SemDialog(owner,names,data,blocks,edges,previous).dialog.showAndWait();}
    static Settings smokeTest(Window owner,List<String> names,double[][] data,List<PlsSem.Block> blocks,List<PlsSem.Edge> edges){var test=new SemDialog(owner,names,data,blocks,edges,null);test.dialog.show();test.iterations.setText("0");((Button)test.dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();if(!test.dialog.isShowing()||test.settings!=null)throw new AssertionError("Invalid SEM iteration count accepted");test.iterations.setText("1000");test.mean.setSelected(true);test.custom.setSelected(true);test.regenerate();((Button)test.dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();var result=test.dialog.getResult();if(result==null||result.customModel()==null||!result.options().meanStructure())throw new AssertionError("SEM dialog dropped explicit model or mean settings");return result;}
}
