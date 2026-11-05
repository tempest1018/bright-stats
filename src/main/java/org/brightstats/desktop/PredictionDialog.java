package org.brightstats.desktop;

import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.brightstats.stats.Prediction;

final class PredictionDialog {
    record Settings(Prediction.Options prediction,double confidence){}
    private final TextField confidence=new TextField();
    private double validatedConfidence;
    private final Dialog<Prediction.Options> dialog=new Dialog<>();
    private final ComboBox<Prediction.Scheme> scheme=new ComboBox<>();
    private final TextField folds,repeats,fraction,seed;
    private final Label error=new Label();
    private Prediction.Options result;
    private PredictionDialog(Window owner,Prediction.Options settings){this(owner,settings,.95);}
    private PredictionDialog(Window owner,Prediction.Options settings,double level) {
        confidence.setText(Double.toString(level));
        dialog.initOwner(owner);dialog.setTitle("Predictive validation");scheme.getItems().setAll(Prediction.Scheme.values());scheme.setValue(settings.scheme());
        folds=new TextField(Integer.toString(settings.folds()));repeats=new TextField(Integer.toString(settings.repeats()));fraction=new TextField(Double.toString(settings.testFraction()*100));seed=new TextField(Long.toString(settings.seed()));
        scheme.valueProperty().addListener((o,a,b)->enabled());enabled();
        Label help=UiAppearance.label("K_FOLD: every case is held out once per repeat. HOLDOUT: random training/test split per repeat.\nEach split refits the model and preprocessing using training data only.\nPredictions use root indicators and trained paths, never held-out endogenous indicators.\nOnly endogenous reflective indicators are scored; indicator blocks must not overlap.\nBenchmarks use training means and linear regression on root indicators.");help.setWrapText(true);error.setWrapText(true);
        VBox content=new VBox(7,UiAppearance.label("Split method"),scheme,UiAppearance.label("Folds (k-fold only)"),folds,UiAppearance.label("Repeats"),repeats,UiAppearance.label("Test percentage (holdout only)"),fraction,UiAppearance.label("Fixed integer seed"),seed,UiAppearance.label("CVPAT confidence level (0–1; k-fold comparisons)"),confidence,help,error);content.setPadding(new Insets(12));content.setPrefWidth(680);
        dialog.getDialogPane().setContent(content);dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK,ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION,event->{result=null;try{result=read();}catch(Exception problem){event.consume();error.setText(problem.getMessage());}});
        dialog.setResultConverter(button->button==ButtonType.OK?result:null);
    }
    private void enabled(){if(folds!=null)folds.setDisable(scheme.getValue()!=Prediction.Scheme.K_FOLD);if(fraction!=null)fraction.setDisable(scheme.getValue()!=Prediction.Scheme.HOLDOUT);}
    private Prediction.Options read(){validatedConfidence=Double.parseDouble(confidence.getText().trim());if(!Double.isFinite(validatedConfidence)||validatedConfidence<=0||validatedConfidence>=1)throw new IllegalArgumentException("Choose confidence between zero and one.");return new Prediction.Options(scheme.getValue(),scheme.getValue()==Prediction.Scheme.K_FOLD?Integer.parseInt(folds.getText().trim()):5,Integer.parseInt(repeats.getText().trim()),scheme.getValue()==Prediction.Scheme.HOLDOUT?Double.parseDouble(fraction.getText().trim())/100:.2,Long.parseLong(seed.getText().trim()));}
    static Optional<Settings> showWithInference(Window owner,Prediction.Options settings,double confidence){var test=new PredictionDialog(owner,settings,confidence);return test.dialog.showAndWait().map(options->new Settings(options,test.validatedConfidence));}
    static Optional<Prediction.Options> show(Window owner,Prediction.Options settings){return new PredictionDialog(owner,settings).dialog.showAndWait();}
    static Prediction.Options smokeTest(Window owner) {
        var test=new PredictionDialog(owner,Prediction.Options.defaults());test.dialog.show();Button commit=(Button)test.dialog.getDialogPane().lookupButton(ButtonType.OK);
        test.folds.setText("1");commit.fire();if(!test.dialog.isShowing()||test.result!=null)throw new AssertionError("Invalid folds accepted");
        test.folds.setText("3");test.repeats.setText("0");commit.fire();if(!test.dialog.isShowing()||test.result!=null)throw new AssertionError("Invalid repeats accepted");
        test.repeats.setText("2");test.seed.setText("1.5");commit.fire();if(!test.dialog.isShowing()||test.result!=null)throw new AssertionError("Fractional seed accepted");
        test.seed.setText(Long.toString(Long.MAX_VALUE));test.confidence.setText("1");commit.fire();if(!test.dialog.isShowing()||test.result!=null)throw new AssertionError("Invalid prediction confidence accepted");test.confidence.setText(".95");commit.fire();var result=test.dialog.getResult();if(result==null||result.folds()!=3||result.repeats()!=2||result.seed()!=Long.MAX_VALUE)throw new AssertionError("Prediction settings changed");
        var holdout=new PredictionDialog(owner,result);holdout.dialog.show();holdout.scheme.setValue(Prediction.Scheme.HOLDOUT);holdout.fraction.setText("100");((Button)holdout.dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
        if(!holdout.dialog.isShowing()||holdout.result!=null)throw new AssertionError("Invalid holdout accepted");
        holdout.fraction.setText("20");((Button)holdout.dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
        if(holdout.dialog.getResult()==null||holdout.dialog.getResult().scheme()!=Prediction.Scheme.HOLDOUT||holdout.dialog.getResult().testFraction()!=.2)throw new AssertionError("Valid holdout settings changed");
        var cancelled=new PredictionDialog(owner,result);cancelled.dialog.show();((Button)cancelled.dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();if(cancelled.dialog.getResult()!=null)throw new AssertionError("Cancelled prediction committed");return result;
    }
}
