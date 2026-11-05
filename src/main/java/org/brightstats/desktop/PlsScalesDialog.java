package org.brightstats.desktop;
import java.util.*;
import java.nio.file.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.brightstats.stats.PlsScales;

final class PlsScalesDialog {
    private static Dialog<PlsScales.Metric> dialog(Window owner,PlsScales.Metric current){var dialog=new Dialog<PlsScales.Metric>();dialog.initOwner(owner);dialog.setTitle("PLS result metrics");var metric=new ComboBox<PlsScales.Metric>();metric.getItems().setAll(PlsScales.Metric.values());metric.setValue(current);metric.setId("pls-result-metric");var note=UiAppearance.label("Standardized estimation is unchanged. Raw scores use inverse-SD weights normalized to sum to one. Mean-centered results subtract the raw score means. Reliability and fit remain standardized diagnostics. Bootstrap refits both estimation and scaling.");note.setWrapText(true);note.setMaxWidth(500);dialog.getDialogPane().setContent(new VBox(12,UiAppearance.label("Result metric"),metric,note));dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);dialog.setResultConverter(button->button==ButtonType.OK?metric.getValue():null);return dialog;}
    static Optional<PlsScales.Metric> show(Window owner,PlsScales.Metric current){return dialog(owner,current).showAndWait();}
    @SuppressWarnings("unchecked") static PlsScales.Metric smoke(Window owner,Path image)throws Exception{var dialog=dialog(owner,PlsScales.Metric.STANDARDIZED);dialog.show();((ComboBox<PlsScales.Metric>)dialog.getDialogPane().lookup("#pls-result-metric")).setValue(PlsScales.Metric.UNSTANDARDIZED);dialog.getDialogPane().applyCss();dialog.getDialogPane().layout();java.nio.file.Files.deleteIfExists(image);GraphicExport.png(image,dialog.getDialogPane(),1);((Button)dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();var selected=dialog.getResult();if(selected!=PlsScales.Metric.UNSTANDARDIZED)throw new AssertionError("Metric dialog selection changed");return selected;}
}
