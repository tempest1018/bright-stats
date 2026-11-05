package org.brightstats.desktop;

import java.util.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.stage.Window;
import org.brightstats.stats.*;

final class RegressionDialog {
    record Selection(int response,int[] predictors,RobustRegression.Covariance covariance,
                     double confidence,double threshold,Double eventClass,RegressionBootstrap.Options bootstrap) {
        Selection { predictors=predictors.clone(); }
        @Override public int[] predictors(){return predictors.clone();}
    }
    private final Dialog<Selection> dialog=new Dialog<>();
    private final ComboBox<String> response=new ComboBox<>();
    private final ListView<String> predictors=new ListView<>();
    private final ComboBox<RobustRegression.Covariance> covariance=new ComboBox<>();
    private final ComboBox<BootstrapIntervals.Method> interval=new ComboBox<>();
    private final ComboBox<RegressionBootstrap.Convention> convention=new ComboBox<>();
    private final CheckBox oneTailed=new CheckBox("One-tailed (reference convention)");
    private final TextField confidence,threshold=new TextField(),event=new TextField();
    private final TextField samples=new TextField(),seed=new TextField();
    private final CheckBox retain=new CheckBox("Retain individual draws (larger reports)");
    private final Label error=new Label();
    private final boolean logistic,bootstrapping;
    private Selection selection;

    private RegressionDialog(Window owner,List<String> names,boolean logistic,
                             RobustRegression.Covariance current,double level,
                             LogisticRegression.Options logisticSettings,RegressionBootstrap.Options bootstrapSettings) {
        this.logistic=logistic;bootstrapping=bootstrapSettings!=null;
        dialog.initOwner(owner);dialog.setResizable(true);
        dialog.setTitle(logistic?"Binary logistic regression":bootstrapping?"Regression bootstrapping":"Linear regression");
        response.getItems().setAll(names);response.getSelectionModel().selectFirst();
        predictors.getItems().setAll(names);predictors.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        predictors.setPrefHeight(160);confidence=new TextField(Double.toString(level*100));
        covariance.getItems().setAll(RobustRegression.Covariance.values());covariance.setValue(current);
        var saved=logisticSettings==null?LogisticRegression.Options.defaults():logisticSettings;
        threshold.setText(Double.toString(saved.threshold()));event.setText(saved.eventClass()==null?"":saved.eventClass().toString());
        event.setPromptText("Blank: higher observed class is the event");error.setWrapText(true);
        VBox content=new VBox(8,UiAppearance.label("Response"),response,UiAppearance.label("Predictors (exclude response)"),predictors,
                              UiAppearance.label("Confidence (%)"),confidence);
        content.setPadding(new Insets(12));content.setPrefWidth(580);
        if(logistic)content.getChildren().addAll(UiAppearance.label("Decision threshold (probability)"),threshold,
                UiAppearance.label("Event class (optional numeric value)"),event,
                UiAppearance.label("Two observed classes required; unpenalized ML with normal Wald inference."));
        else content.getChildren().addAll(UiAppearance.label("Analytic covariance / standard errors"),covariance,
                new Label(bootstrapping?"Analytic errors studentize coefficient draws; reported SE is bootstrap SD."
                        :"CLASSICAL: Student-t; HC0–HC4: asymptotic normal Wald inference."));
        if(bootstrapping){
            samples.setText(Integer.toString(bootstrapSettings.samples()));seed.setText(Long.toString(bootstrapSettings.seed()));
            interval.getItems().setAll(BootstrapIntervals.Method.values());interval.setValue(bootstrapSettings.interval());
            retain.setSelected(bootstrapSettings.retainDraws());
            convention.getItems().setAll(RegressionBootstrap.Convention.values());convention.setValue(bootstrapSettings.convention());
            oneTailed.setSelected(!bootstrapSettings.twoSided());oneTailed.setDisable(bootstrapSettings.convention()==RegressionBootstrap.Convention.STANDARD);
            convention.valueProperty().addListener((observable,previous,next)->{boolean standard=next==RegressionBootstrap.Convention.STANDARD;oneTailed.setDisable(standard);if(standard)oneTailed.setSelected(false);});
            content.getChildren().addAll(UiAppearance.label("Subsamples (fixed attempts)"),samples,UiAppearance.label("Fixed seed"),seed,
                    UiAppearance.label("Inference convention"),convention,UiAppearance.label("Confidence interval"),interval,oneTailed,retain);
        }
        content.getChildren().add(error);
        ScrollPane scroll=new ScrollPane(content);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(bootstrapping?600:500);
        dialog.getDialogPane().setContent(scroll);dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK,ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION,action->{
            selection=null;try{selection=selection();}catch(Exception problem){action.consume();error.setText(problem.getMessage());}
        });
        dialog.setResultConverter(button->button==ButtonType.OK?selection:null);
    }
    private Selection selection(){
        double level=Double.parseDouble(confidence.getText().trim())/100;Inference.confidence(level);
        int dependent=response.getSelectionModel().getSelectedIndex();
        int[] chosen=predictors.getSelectionModel().getSelectedIndices().stream().mapToInt(Integer::intValue).toArray();
        if(dependent<0||Arrays.stream(chosen).anyMatch(c->c==dependent))throw new IllegalArgumentException("Select a response distinct from every predictor.");
        double cut=logistic?Double.parseDouble(threshold.getText().trim()):.5;
        Double positive=logistic&&!event.getText().isBlank()?Double.valueOf(event.getText().trim()):null;
        if(logistic)new LogisticRegression.Options(100,1e-8,level,cut,positive);
        RegressionBootstrap.Options bootstrap=bootstrapping?new RegressionBootstrap.Options(Integer.parseInt(samples.getText().trim()),
                Long.parseLong(seed.getText().trim()),level,interval.getValue(),covariance.getValue(),retain.isSelected(),convention.getValue(),!oneTailed.isSelected()):null;
        return new Selection(dependent,chosen,covariance.getValue(),level,cut,positive,bootstrap);
    }
    static Optional<Selection> show(Window owner,List<String> names,boolean logistic,RobustRegression.Covariance covariance,double confidence){
        return new RegressionDialog(owner,names,logistic,covariance,confidence,null,null).dialog.showAndWait();
    }
    static Optional<Selection> showLogistic(Window owner,List<String> names,LogisticRegression.Options settings){
        return new RegressionDialog(owner,names,true,RobustRegression.Covariance.CLASSICAL,settings.confidence(),settings,null).dialog.showAndWait();
    }
    static Optional<Selection> showBootstrap(Window owner,List<String> names,RegressionBootstrap.Options settings){
        return new RegressionDialog(owner,names,false,settings.covariance(),settings.confidence(),null,settings).dialog.showAndWait();
    }
    static Selection smokeTest(Window owner,boolean logistic){
        var test=new RegressionDialog(owner,List.of("X1","X2","Y"),logistic,RobustRegression.Covariance.CLASSICAL,.95,null,null);
        test.dialog.show();test.response.getSelectionModel().select(2);test.predictors.getSelectionModel().selectIndices(0,1);
        test.confidence.setText("100");test.accept();
        if(!test.dialog.isShowing()||test.selection!=null)throw new AssertionError("Invalid regression confidence accepted");
        test.confidence.setText("95");test.covariance.setValue(RobustRegression.Covariance.HC4);test.accept();
        if(test.dialog.getResult()==null||test.dialog.getResult().response()!=2)throw new AssertionError("Regression controls changed selection");
        return test.dialog.getResult();
    }
    static Selection smokeBootstrap(Window owner){
        var test=new RegressionDialog(owner,List.of("X1","X2","Y"),false,RobustRegression.Covariance.CLASSICAL,.95,null,RegressionBootstrap.Options.defaults());
        test.dialog.show();test.response.getSelectionModel().select(2);test.predictors.getSelectionModel().selectIndices(0,1);
        test.samples.setText("29");test.accept();
        if(!test.dialog.isShowing()||test.selection!=null)throw new AssertionError("Invalid bootstrap count accepted");
        test.samples.setText("80");test.seed.setText(Long.toString(Long.MAX_VALUE));test.interval.setValue(BootstrapIntervals.Method.BCA);
        test.retain.setSelected(true);test.accept();var selection=test.dialog.getResult();
        if(selection==null||selection.bootstrap().seed()!=Long.MAX_VALUE||selection.bootstrap().interval()!=BootstrapIntervals.Method.BCA)
            throw new AssertionError("Bootstrap controls changed settings");
        return selection;
    }
    private void accept(){((Button)dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();}
}
