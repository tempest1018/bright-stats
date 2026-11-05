package org.brightstats.desktop;

import java.util.*;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.function.Function;
import javafx.util.StringConverter;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import org.brightstats.data.DataPreparation;
import org.brightstats.data.DataPreparation.*;

/** Preview and explicit choices precede any change to the current project. */
final class ImportDialog {
    record Session(Raw raw, Settings settings, List<Column> definitions, Prepared prepared) {
        Session { definitions = List.copyOf(definitions); }
    }
    private final Dialog<Session> dialog = new Dialog<>();
    private final ComboBox<String> delimiter = new ComboBox<>();
    private final ComboBox<Decimal> decimal = new ComboBox<>();
    private final ComboBox<Missing> treatment = new ComboBox<>();
    private final TextField marker = new TextField();
    private final VBox columns = new VBox(6);
    private final TableView<List<String>> sample = new TableView<>();
    private final TableView<double[]> preparedSample = new TableView<>();
    private final Label summary = new Label(), messages = new Label();
    private final Button refresh = UiAppearance.button("Refresh preview / infer types");
    private final List<TextField> names = new ArrayList<>();
    private final List<ComboBox<Kind>> kinds = new ArrayList<>();
    private final String text;
    private final Raw fixedRaw;
    private final List<Kind> sourceKinds;
    private final List<String> sourceWarnings;
    private Raw raw;
    private Settings previewSettings;
    private Session committed;

    private ImportDialog(Window owner,String text){this(owner,text,null,null,List.of());}
    private ImportDialog(Window owner,String text,Raw source,List<Kind> types,List<String> warnings) {
        this.text = text;this.fixedRaw=source;this.sourceKinds=types==null?null:List.copyOf(types);this.sourceWarnings=List.copyOf(warnings); dialog.initOwner(owner); dialog.setTitle("Import data — preview and preparation");
        delimiter.getItems().addAll("Comma", "Semicolon", "Tab");
        delimiter.getSelectionModel().select(switch (DataPreparation.detectDelimiter(text)) { case ';' -> 1; case '\t' -> 2; default -> 0; });
        decimal.getItems().setAll(Decimal.values()); decimal.setValue(Decimal.DOT);
        decimal.setConverter(labels(value -> value == Decimal.DOT ? "Dot (1,000.23)" : "Comma (1.000,23)"));
        treatment.getItems().setAll(Missing.values()); treatment.setValue(Missing.REQUIRE_COMPLETE);
        treatment.setConverter(labels(value -> switch (value) {
            case REQUIRE_COMPLETE -> "Require complete numeric columns";
            case MEAN_REPLACEMENT -> "Mean replacement (numeric cells)";
            case REMOVE_ROWS -> "Remove incomplete retained rows";
        }));
        marker.setPromptText("Optional, e.g. -99 or NA");
        GridPane choices = new GridPane(); choices.setHgap(12); choices.setVgap(8);
        choices.addRow(0, UiAppearance.label("Delimiter"), delimiter, UiAppearance.label("Decimal format"), decimal);
        choices.addRow(1, UiAppearance.label("Missing marker"), marker, UiAppearance.label("Missing-data treatment"), treatment);
        Label help = new Label("Choose the decimal format explicitly. Thousands groups must contain three digits.\n"
                + "Mean replacement fills missing numeric cells using observed column means. Row removal excludes rows missing any retained column, including text.\n"
                + "Text / group columns are preserved without numeric recoding. Ignored columns do not affect preparation; original source values remain in the project.");
        help.setWrapText(true); help.setMinHeight(Region.USE_PREF_SIZE);
        refresh.setOnAction(event -> preview());
        delimiter.valueProperty().addListener((o, a, b) -> settingsChanged());
        decimal.valueProperty().addListener((o, a, b) -> settingsChanged());
        marker.textProperty().addListener((o, a, b) -> settingsChanged());
        treatment.valueProperty().addListener((o, a, b) -> settingsChanged());
        summary.setWrapText(true); messages.setWrapText(true); messages.setMinHeight(Region.USE_PREF_SIZE);
        sample.setPlaceholder(UiAppearance.label("Refresh the preview to inspect source values."));
        preparedSample.setPlaceholder(UiAppearance.label("Resolve the preparation errors to preview numeric analysis values."));
        Tab preparedTab = new Tab("Prepared numeric values (dot decimals)",preparedSample), sourceTab = new Tab("Original source values",sample);
        preparedTab.setClosable(false); sourceTab.setClosable(false); TabPane previews = new TabPane(preparedTab,sourceTab); previews.setPrefHeight(210);
        previews.setMinHeight(170);
        ScrollPane columnScroll = new ScrollPane(columns); columnScroll.setFitToWidth(true); columnScroll.setPrefHeight(170);
        VBox content = new VBox(10, choices, help, refresh, summary, UiAppearance.label("Column names and types (editable)"), columnScroll,
                UiAppearance.label("Data preview — first 12 rows"), previews, messages);
        content.setPadding(new Insets(12)); content.setPrefWidth(930);
        ScrollPane form = new ScrollPane(content); form.setFitToWidth(true); form.setPrefViewportWidth(950); form.setPrefViewportHeight(640);
        dialog.getDialogPane().setContent(form); dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Import prepared data");
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, event -> {
            committed = null;
            try {
                Settings settings = settings();
                if (raw == null || !settings.equals(previewSettings)) throw new IllegalArgumentException("Refresh the preview after changing import settings.");
                List<Column> definitions = definitions();
                var prepared = DataPreparation.prepare(raw, settings, definitions);
                committed = new Session(raw, settings, definitions, prepared);
            } catch (Exception error) {
                event.consume(); messages.setText(error.getMessage());
            }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? committed : null);
        if(fixedRaw!=null){delimiter.setDisable(true);decimal.setDisable(true);}
        preview();
    }
    private Settings settings() {
        return new Settings(new char[]{',', ';', '\t'}[delimiter.getSelectionModel().getSelectedIndex()], decimal.getValue(), marker.getText(), treatment.getValue());
    }
    private List<Column> definitions() {
        List<Column> result = new ArrayList<>();
        for (int j = 0; j < names.size(); j++) result.add(new Column(names.get(j).getText(), kinds.get(j).getValue()));
        return result;
    }
    private void preview() {
        raw = null; names.clear(); kinds.clear(); columns.getChildren().clear(); sample.getColumns().clear(); sample.getItems().clear();
        try {
            Settings settings = settings(); Raw incoming = fixedRaw==null?DataPreparation.parse(text, settings.delimiter()):fixedRaw;
            var inferred = DataPreparation.infer(incoming, settings);
            int missing = 0;
            for (var row : incoming.rows()) for (String value : row) if (DataPreparation.missing(value, settings)) missing++;
            summary.setText(incoming.rows().size() + " source rows · " + inferred.size() + " columns · " + missing + " missing cells · UTF-8");
            for (int j = 0; j < inferred.size(); j++) {
                var column = inferred.get(j); TextField name = new TextField(column.name()); name.setPrefWidth(260);
                ComboBox<Kind> kind = new ComboBox<>(); kind.getItems().setAll(Kind.values()); kind.setValue(sourceKinds==null?column.kind():sourceKinds.get(j));
                kind.setConverter(labels(value -> switch(value) { case NUMERIC -> "Numeric"; case TEXT -> "Text / group"; case IGNORE -> "Ignore"; }));
                names.add(name); kinds.add(kind); columns.getChildren().add(new HBox(10, new Label("Column " + (j + 1)), name, kind));
                name.textProperty().addListener((o, a, b) -> updatePreparation());
                kind.valueProperty().addListener((o, a, b) -> updatePreparation());
                int index = j; TableColumn<List<String>, String> field = new TableColumn<>(column.name()); field.setPrefWidth(140);
                field.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().get(index))); sample.getColumns().add(field);
            }
            sample.getItems().addAll(incoming.rows().subList(0, Math.min(12, incoming.rows().size())));
            raw = incoming; previewSettings = settings;
            updatePreparation();
        } catch (Exception error) { messages.setText(error.getMessage()); summary.setText("Preview unavailable"); }
    }
    private void settingsChanged() { if (raw != null) messages.setText("Import settings changed. Refresh the preview before importing."); }
    private void updatePreparation() {
        if (raw == null) return;
        preparedSample.getColumns().clear(); preparedSample.getItems().clear();
        if (!settings().equals(previewSettings)) { settingsChanged(); return; }
        try {
            var prepared = DataPreparation.prepare(raw, settings(), definitions());
            for (int j = 0; j < prepared.columns().size(); j++) {
                int index = j; TableColumn<double[],String> column = new TableColumn<>(prepared.columns().get(j)); column.setPrefWidth(145);
                column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Double.toString(cell.getValue()[index]))); preparedSample.getColumns().add(column);
            }
            double[][] matrix = prepared.values(); preparedSample.getItems().addAll(Arrays.asList(matrix).subList(0,Math.min(12,matrix.length)));
            messages.setText(prepared.sourceRows().size() + " analysis rows; " + prepared.replacedCells() + " cells replaced; " + prepared.removedRows() + " rows removed.\n"
                    + String.join("\n", sourceWarnings)+"\n"+String.join("\n", prepared.warnings()) + "\nReview numeric-looking text columns against the selected decimal format.");
        } catch (IllegalArgumentException error) { messages.setText(error.getMessage() + " Edit columns or settings before importing."); }
    }
    private static <T> StringConverter<T> labels(Function<T,String> display) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return value == null ? "" : display.apply(value); }
            @Override public T fromString(String text) { throw new UnsupportedOperationException("Choose an available option."); }
        };
    }
    static Optional<Session> show(Window owner, String text) { return new ImportDialog(owner, text).dialog.showAndWait(); }

    static Optional<Session> show(Window owner,Raw source,List<Kind> types,List<String> warnings){return new ImportDialog(owner,org.brightstats.data.WorkbookData.delimited(source),source,types,warnings).dialog.showAndWait();}
    static Map<String,Session> interchangeSmoke(Window owner,org.brightstats.data.WorkbookData.Imported workbook,org.brightstats.data.SpssData.Imported spss){Map<String,Session> result=new LinkedHashMap<>();for(boolean excel:new boolean[]{true,false}){Raw source=excel?workbook.raw():spss.raw();List<Kind> types=excel?List.of(Kind.NUMERIC,Kind.NUMERIC,Kind.TEXT,Kind.TEXT,Kind.TEXT,Kind.TEXT,Kind.NUMERIC):spss.variables().stream().map(variable->variable.width()==0?Kind.NUMERIC:Kind.TEXT).toList();var dialog=new ImportDialog(owner,org.brightstats.data.WorkbookData.delimited(source),source,types,excel?workbook.warnings():spss.warnings());dialog.dialog.show();dialog.treatment.setValue(Missing.MEAN_REPLACEMENT);((Button)dialog.dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();if(!dialog.dialog.isShowing()||dialog.committed!=null)throw new AssertionError("Stale typed import accepted");dialog.refresh.fire();((Button)dialog.dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();Session session=dialog.dialog.getResult();if(session==null||session.prepared().replacedCells()!=(excel?1:2)||session.definitions().getLast().kind()!=(excel?Kind.NUMERIC:Kind.TEXT)||!dialog.delimiter.isDisabled()||!dialog.decimal.isDisabled())throw new AssertionError("Typed import lost storage/missing policy");if(excel&&!session.raw().rows().getFirst().get(3).equals("0012"))throw new AssertionError("Workbook lost literal text");result.put(excel?"excel":"spss",session);}return result;}
    /** Exercises real JavaFX controls during the bounded desktop smoke test. */
    static Session smokeJourney(Window owner,String csv,Path image)throws Exception{var controls=new ImportDialog(owner,csv);controls.dialog.show();controls.treatment.setValue(Missing.MEAN_REPLACEMENT);controls.refresh.fire();if(controls.kinds.size()!=11||controls.kinds.get(9).getValue()!=Kind.TEXT||controls.kinds.get(10).getValue()!=Kind.NUMERIC)throw new AssertionError("Journey import roles differ");controls.dialog.getDialogPane().applyCss();controls.dialog.getDialogPane().layout();java.nio.file.Files.deleteIfExists(image);GraphicExport.png(image,controls.dialog.getDialogPane(),1);((Button)controls.dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();var result=controls.dialog.getResult();if(result==null||result.prepared().values().length!=240||result.prepared().replacedCells()!=1)throw new AssertionError("Journey import/preparation failed");return result;}
    static Map<String,Session> smokeTest(Window owner, Path screenshot) throws Exception {
        Map<String,Session> sessions = new LinkedHashMap<>();
        ImportDialog comma = new ImportDialog(owner,"x;x;group\n1,5;-99;Group A\n2,5;4,5;Group B\n3,5;6,5;Group A\n4,5;8,5;Group B\n");
        comma.dialog.show(); comma.decimal.setValue(Decimal.COMMA); comma.marker.setText("-99"); comma.treatment.setValue(Missing.MEAN_REPLACEMENT);
        Button commit = (Button) comma.dialog.getDialogPane().lookupButton(ButtonType.OK); commit.fire();
        if (!comma.dialog.isShowing() || comma.committed != null || !comma.messages.getText().contains("Refresh")) throw new AssertionError("Stale preview accepted");
        comma.refresh.fire(); commit.fire();
        if (!comma.dialog.isShowing() || comma.committed != null || !comma.messages.getText().contains("unique")) throw new AssertionError("Duplicate headers accepted");
        comma.names.get(1).setText("outcome");
        comma.dialog.getDialogPane().applyCss(); comma.dialog.getDialogPane().layout();
        var image = comma.dialog.getDialogPane().snapshot(null,null);
        BufferedImage bitmap = new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<bitmap.getHeight();y++) for(int x=0;x<bitmap.getWidth();x++) bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        ImageIO.write(bitmap,"png",screenshot.toFile());
        commit.fire(); Session filled = comma.dialog.getResult();
        if (filled == null || filled.prepared().values()[0][0] != 1.5 || filled.prepared().values()[0][1] != 6.5
                || filled.prepared().replacedCells() != 1 || filled.definitions().getLast().kind() != Kind.TEXT) throw new AssertionError("Decimal/marker/text import failed");
        sessions.put("mean",filled);
        ImportDialog deletion = new ImportDialog(owner,"x,y,group,ignored\n1,3,A,unused\n2,,B,unused\n3,7,C,\n4,10,D,unused\n5,11,E,unused\n");
        deletion.dialog.show(); deletion.treatment.setValue(Missing.REMOVE_ROWS); deletion.refresh.fire(); deletion.kinds.get(3).setValue(Kind.IGNORE);
        ((Button)deletion.dialog.getDialogPane().lookupButton(ButtonType.OK)).fire(); Session dropped = deletion.dialog.getResult();
        if (dropped == null || dropped.prepared().removedRows() != 1 || !dropped.prepared().sourceRows().equals(List.of(1,3,4,5))) throw new AssertionError("Row removal/type override failed");
        sessions.put("drop",dropped);
        ImportDialog cancelled = new ImportDialog(owner,"x\n1\n2\n"); cancelled.dialog.show();
        ((Button)cancelled.dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
        if (cancelled.dialog.getResult() != null || cancelled.committed != null) throw new AssertionError("Cancelled import committed");
        return sessions;
    }
}
