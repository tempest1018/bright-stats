package org.brightstats.desktop;

import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.brightstats.stats.Bootstrap;
import org.brightstats.stats.BootstrapIntervals;
import org.brightstats.stats.RegressionBootstrap;

final class BootstrapDialog {
    private final Dialog<Bootstrap.Options> dialog=new Dialog<>();
    private final TextField samples,seed,confidence;
    private final ComboBox<BootstrapIntervals.Method> interval=new ComboBox<>();
    private final ComboBox<RegressionBootstrap.Convention> convention=new ComboBox<>();
    private final CheckBox oneTailed=new CheckBox("One-tailed (reference convention)");
    private final Label error=new Label();
    private Bootstrap.Options result;
    private BootstrapDialog(Window owner,Bootstrap.Options options) {
        dialog.initOwner(owner); dialog.setTitle("Case bootstrap inference");dialog.setResizable(true);
        samples=new TextField(Integer.toString(options.samples())); seed=new TextField(Long.toString(options.seed())); confidence=new TextField(Double.toString(options.confidence()*100));
        convention.getItems().setAll(RegressionBootstrap.Convention.values());convention.setValue(options.convention());
        interval.getItems().setAll(options.convention()==RegressionBootstrap.Convention.STANDARD?java.util.List.of(BootstrapIntervals.Method.PERCENTILE,BootstrapIntervals.Method.BCA):java.util.List.of(BootstrapIntervals.Method.values()));interval.setValue(options.interval());oneTailed.setSelected(!options.twoSided());oneTailed.setDisable(options.convention()==RegressionBootstrap.Convention.STANDARD);
        convention.valueProperty().addListener((observable,previous,next)->{boolean standard=next==RegressionBootstrap.Convention.STANDARD;oneTailed.setDisable(standard);if(standard)oneTailed.setSelected(false);var chosen=interval.getValue();interval.getItems().setAll(standard?java.util.List.of(BootstrapIntervals.Method.PERCENTILE,BootstrapIntervals.Method.BCA):java.util.List.of(BootstrapIntervals.Method.values()));interval.setValue(chosen==null||standard&&chosen==BootstrapIntervals.Method.STUDENTIZED?BootstrapIntervals.Method.PERCENTILE:chosen);});
        Label explanation=UiAppearance.label("Reference convention reproduces the verified reference interval and test definitions. Standard convention supports percentile and jackknife BCa.\nConstruct signs are aligned to original scores. Failed fits are counted, not replaced. Preparation is fixed before resampling.\nAt least 30 usable fits and 80% of requested attempts must succeed."); explanation.setWrapText(true); error.setWrapText(true);
        VBox fields=new VBox(8,UiAppearance.label("Samples (30–100000)"),samples,UiAppearance.label("Fixed integer seed"),seed,UiAppearance.label("Confidence (%)"),confidence,UiAppearance.label("Inference convention"),convention,UiAppearance.label("Interval method"),interval,oneTailed,explanation,error); fields.setPadding(new Insets(12)); fields.setPrefWidth(650);
        ScrollPane scroll=new ScrollPane(fields);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(570);dialog.getDialogPane().setContent(scroll); dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK,ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION,event -> {
            result=null;
            try { result=new Bootstrap.Options(Integer.parseInt(samples.getText().trim()),Long.parseLong(seed.getText().trim()),Double.parseDouble(confidence.getText().trim())/100,interval.getValue(),convention.getValue(),!oneTailed.isSelected()); }
            catch(Exception problem) { event.consume(); error.setText(problem.getMessage()); }
        });
        dialog.setResultConverter(button -> button==ButtonType.OK?result:null);
    }
    static Optional<Bootstrap.Options> showStandard(Window owner,Bootstrap.Options options){var selected=new Bootstrap.Options(options.samples(),options.seed(),options.confidence(),options.interval()==BootstrapIntervals.Method.BCA?BootstrapIntervals.Method.BCA:BootstrapIntervals.Method.PERCENTILE,RegressionBootstrap.Convention.STANDARD,true);var controls=new BootstrapDialog(owner,selected);controls.convention.setDisable(true);return controls.dialog.showAndWait();}
    static Optional<Bootstrap.Options> show(Window owner,Bootstrap.Options options) { return new BootstrapDialog(owner,options).dialog.showAndWait(); }
    static Bootstrap.Options smokeTest(Window owner) {
        BootstrapDialog test=new BootstrapDialog(owner,Bootstrap.Options.defaults()); test.dialog.show();
        Button commit=(Button)test.dialog.getDialogPane().lookupButton(ButtonType.OK);
        test.samples.setText("0"); commit.fire();
        if(!test.dialog.isShowing() || test.result!=null)throw new AssertionError("Invalid sample count accepted");
        test.samples.setText("80"); test.seed.setText("not an integer"); commit.fire();
        if(!test.dialog.isShowing() || test.result!=null)throw new AssertionError("Invalid seed accepted");
        test.seed.setText(Long.toString(Long.MAX_VALUE)); test.confidence.setText("100"); commit.fire();
        if(!test.dialog.isShowing() || test.result!=null)throw new AssertionError("Invalid confidence accepted");
        test.confidence.setText("95"); commit.fire();
        var result=test.dialog.getResult();
        if(result==null || result.samples()!=80 || result.seed()!=Long.MAX_VALUE || result.confidence()!=.95)throw new AssertionError("Bootstrap controls changed valid settings");
        BootstrapDialog cancelled=new BootstrapDialog(owner,result); cancelled.dialog.show();
        ((Button)cancelled.dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
        if(cancelled.dialog.getResult()!=null)throw new AssertionError("Cancelled settings committed");
        return result;
    }
}
