package org.brightstats.desktop;

import java.util.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.brightstats.stats.PlsSem;

/** Guided reflective bindings and directed latent paths, with explicit fixed markers. */
final class ReflectiveModelForm extends VBox {
    private final List<String> source;
    private final Spinner<Integer> count=new Spinner<>(1,5,1);
    private final VBox factors=new VBox(12);
    private final GridPane paths=new GridPane();
    private final List<TextField> names=new ArrayList<>();
    private final List<ListView<String>> selections=new ArrayList<>();
    private final List<ComboBox<String>> markers=new ArrayList<>();
    private final List<int[]> previous=new ArrayList<>();
    private final Map<CheckBox,PlsSem.Edge> edges=new LinkedHashMap<>();
    ReflectiveModelForm(List<String> source,List<PlsSem.Block> initial,List<PlsSem.Edge> connections){this.source=List.copyOf(source);setSpacing(12);getChildren().addAll(new HBox(10,new Label("Reflective factors"),count),new Label("Changing the factor count resets its bindings and paths. Select at least three distinct indicators per factor; choose the fixed unit marker explicitly."),factors,new Label("Directed latent paths (rows → columns)"),paths);int q=Math.max(1,Math.min(5,initial.size()));count.getValueFactory().setValue(q);rebuild(q,initial.size()==q?initial:List.of(),connections);count.valueProperty().addListener((o,a,b)->rebuild(b,List.of(),List.of()));}
    private void rebuild(int q,List<PlsSem.Block> initial,List<PlsSem.Edge> connections){names.clear();selections.clear();markers.clear();previous.clear();edges.clear();factors.getChildren().clear();paths.getChildren().clear();paths.setHgap(15);paths.setVgap(8);
        for(int j=0;j<q;j++){var block=initial.isEmpty()?null:initial.get(j);TextField title=new TextField(block==null?"Factor "+(j+1):block.name());ListView<String> indicators=new ListView<>();indicators.getItems().setAll(source);indicators.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);indicators.setPrefHeight(115);indicators.setAccessibleText("Indicators for factor "+(j+1));ComboBox<String> marker=new ComboBox<>();marker.getItems().setAll(source);marker.setAccessibleText("Fixed unit marker for factor "+(j+1));if(block!=null){for(int col:block.columns())indicators.getSelectionModel().select(col);marker.getSelectionModel().select(block.columns()[0]);}names.add(title);selections.add(indicators);markers.add(marker);previous.add(block==null?new int[0]:block.columns());VBox factor=new VBox(5,new Label("Factor "+(j+1)),title,indicators,new HBox(10,new Label("Fixed marker"),marker));factor.getStyleClass().add("studio-card");factors.getChildren().add(factor);}
        for(int a=0;a<q;a++){paths.add(new Label("Factor "+(a+1)),0,a+1);paths.add(new Label("Factor "+(a+1)),a+1,0);for(int b=0;b<q;b++)if(a!=b){CheckBox box=new CheckBox();var edge=new PlsSem.Edge(a,b);box.setSelected(connections.contains(edge));box.setAccessibleText("Path from factor "+(a+1)+" to factor "+(b+1));edges.put(box,edge);paths.add(box,b+1,a+1);}}
    }
    List<String> selectedColumns(){LinkedHashSet<String> all=new LinkedHashSet<>();for(var choice:selections)all.addAll(choice.getSelectionModel().getSelectedItems());return List.copyOf(all);}
    List<PlsSem.Block> blocks(){List<PlsSem.Block> result=new ArrayList<>();for(int j=0;j<names.size();j++){var selected=selections.get(j).getSelectionModel().getSelectedIndices();int marker=markers.get(j).getSelectionModel().getSelectedIndex();if(marker<0||!selected.contains(marker)||selected.size()<3)throw new IllegalArgumentException("Factor "+(j+1)+" needs at least three indicators and a selected fixed marker.");LinkedHashSet<Integer> order=new LinkedHashSet<>();order.add(marker);for(int col:previous.get(j))if(selected.contains(col))order.add(col);order.addAll(selected);result.add(new PlsSem.Block(names.get(j).getText(),order.stream().mapToInt(Integer::intValue).toArray(),PlsSem.Mode.A));}return List.copyOf(result);}
    List<PlsSem.Edge> edges(){return edges.entrySet().stream().filter(e->e.getKey().isSelected()).map(Map.Entry::getValue).toList();}
}
