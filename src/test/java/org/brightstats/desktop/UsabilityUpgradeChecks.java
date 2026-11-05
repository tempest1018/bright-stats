package org.brightstats.desktop;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import javafx.application.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.brightstats.data.*;
import org.brightstats.stats.*;

public final class UsabilityUpgradeChecks extends Application {
    static int checks;static Throwable failure;
    static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public void start(Stage stage){try{
        Path out=Path.of(getParameters().getRaw().getFirst());Files.createDirectories(out);UiAppearance.install(UserPreferences.defaults());
        double[][] source=new double[60][4];Random random=new Random(5523);for(int i=0;i<source.length;i++){source[i][0]=random.nextGaussian();source[i][1]=1;source[i][2]=i%2;source[i][3]=2*source[i][0];}double[][] before=Matrices.copy(source);
        var readiness=AnalysisReadiness.inspect(List.of("X","Constant","Binary","TwiceX"),source,List.of(new PlsSem.Block("X",new int[]{0,3},PlsSem.Mode.A)),List.of(),null);
        require(readiness.findings().stream().anyMatch(f->f.area().equals("Variance")),"Constant-variable preflight");require(readiness.findings().stream().anyMatch(f->f.area().equals("Measurement scale")),"Measurement-scale preflight");require(Arrays.deepEquals(before,source),"Preflight never modifies observations");
        require(AnalysisReadiness.inspect(List.of(),new double[0][0],List.of(),List.of(),null).findings().getFirst().severity()==AnalysisReadiness.Severity.BLOCKED,"Empty-data preflight");
        TableView<String[]> table=new TableView<>();for(int j=0;j<2;j++){final int column=j;TableColumn<String[],String> c=new TableColumn<>(j==0?"Full parameter identity":"Estimate");c.setCellValueFactory(v->new ReadOnlyStringWrapper(v.getValue()[column]));table.getColumns().add(c);}for(int i=0;i<200;i++)table.getItems().add(new String[]{"Parameter "+i,Integer.toString(i)});require(TableTools.compareCells("2","10")<0&&TableTools.compareCells("1.2e-3","0.1")<0,"Numeric sorting respects magnitude rather than string spelling");TableTools.labels(table);BorderPane wrapped=TableTools.wrap(table);TabPane tabs=new TabPane(new Tab("Coefficients",wrapped),new Tab("Assumptions",new Label("Read the assumptions")));BorderPane report=new BorderPane(tabs,new Label("Synthetic analysis\nComplete assumptions stay accessible."),null,null,null);ReportPresentation.decorate(report);stage.setScene(new Scene(report,1100,680));stage.show();report.applyCss();report.layout();
        VBox explorer=(VBox)report.getLeft();TextField sectionSearch=(TextField)explorer.getChildren().get(1);TreeView<?> tree=(TreeView<?>)explorer.getChildren().get(2);sectionSearch.setText("assump");require(tree.getRoot().getChildren().size()==1,"Report tree filtering");sectionSearch.clear();tree.getSelectionModel().select(1);require(tabs.getSelectionModel().getSelectedIndex()==1,"Report tree selects the underlying section");tabs.getSelectionModel().selectFirst();
        TextField filter=(TextField)((HBox)((VBox)wrapped.getTop()).getChildren().getFirst()).getChildren().getFirst();filter.setText("Parameter 19");require(table.getItems().size()==11,"Result filtering");
        MenuButton pin=(MenuButton)table.getProperties().get("brightstats.pinMenu");pin.getItems().get(1).fire();require(wrapped.getLeft() instanceof TableView<?>,"A real frozen column pane is present");TableView<?> frozen=(TableView<?>)wrapped.getLeft();require(frozen.getItems()==table.getItems(),"Frozen and main panes share the filtered/sorted order");table.getSelectionModel().select(2);require(frozen.getSelectionModel().getSelectedIndex()==2,"Pinned selection stays aligned");
        var collect=Studio.class.getDeclaredMethod("collectReportTables",Node.class,String.class,JsonArray.class);collect.setAccessible(true);JsonArray archived=new JsonArray();collect.invoke(null,report,"Analysis",archived);require(archived.size()==1,"Frozen presentation is not duplicated in reports");require(archived.get(0).getAsJsonObject().getAsJsonArray("rows").size()==200,"A filtered view never truncates canonical report rows");require(archived.get(0).getAsJsonObject().getAsJsonArray("columns").get(0).getAsString().equals("Full parameter identity"),"Complete canonical headers retained");
        filter.clear();pin.getItems().get(0).fire();require(wrapped.getLeft()==null,"Frozen column can be removed");
        var model=new LatentInteractionSem.Model(List.of(new LatentInteractionSem.Factor("Predictor",new int[]{2,0,1})),new LatentInteractionSem.Factor("Outcome",new int[]{5,4,3}),List.of(new LatentInteractionSem.Interaction(0,0)));var form=new LatentModelForm(List.of("X1","X2","X3","Y1","Y2","Y3"),model);Gson gson=new Gson();require(gson.toJsonTree(form.read()).equals(gson.toJsonTree(model)),"Guided model preserves indicator order, fixed markers and product identity");
        stage.getScene().setRoot((Parent)StudioHome.create(()->{},()->{},()->{},()->{},()->{},()->{}));stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();GraphicExport.png(out.resolve("studio-home.png"),stage.getScene().getRoot(),1);stage.getScene().setRoot(report);report.applyCss();report.layout();GraphicExport.png(out.resolve("report-explorer.png"),report,1);
        Files.writeString(out.resolve("receipt.json"),"{\"state\":\"passed\",\"checks\":"+checks+",\"canonical_export_rows\":200}");System.out.println("Usability checks: "+checks);stage.close();Platform.exit();
    }catch(Throwable error){failure=error;error.printStackTrace();Platform.exit();}}
    public static void main(String[] args){launch(args);if(failure!=null)System.exit(1);}
}
