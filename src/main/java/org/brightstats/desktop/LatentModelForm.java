package org.brightstats.desktop;

import java.util.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.brightstats.stats.LatentInteractionSem;

/** Guided measurement/term editing backed by the same immutable likelihood schema as JSON. */
final class LatentModelForm extends VBox {
    private final List<String> names;
    private final Spinner<Integer> count=new Spinner<>(1,4,1);
    private final VBox factors=new VBox(10);
    private final FlowPane products=new FlowPane(12,8);
    private final List<TextField> titles=new ArrayList<>();
    private final List<ListView<String>> indicators=new ArrayList<>();
    private final List<ComboBox<String>> markers=new ArrayList<>();
    private final List<int[]> originalOrder=new ArrayList<>();
    private final Map<CheckBox,LatentInteractionSem.Interaction> terms=new LinkedHashMap<>();
    LatentModelForm(List<String> names,LatentInteractionSem.Model model){this.names=List.copyOf(names);setSpacing(10);getChildren().addAll(new HBox(10,new Label("Number of latent predictors"),count),factors,new Label("Products and squares (optional)"),products);set(model);count.valueProperty().addListener((o,a,b)->rebuild(b,null));}
    private void rebuild(int q,LatentInteractionSem.Model source){
        titles.clear();indicators.clear();markers.clear();originalOrder.clear();terms.clear();factors.getChildren().clear();products.getChildren().clear();
        for(int j=0;j<=q;j++){
            var factor=source==null?null:j<q?source.predictors().get(j):source.outcome();TextField name=new TextField(factor==null?(j==q?"Outcome":"Predictor "+(j+1)):factor.name());name.setAccessibleText(j==q?"Outcome factor name":"Predictor factor name "+(j+1));ListView<String> columns=new ListView<>();columns.getItems().setAll(names);columns.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);columns.setPrefHeight(125);columns.setAccessibleText("Indicators for "+name.getText()+"; first selected source column is the fixed marker");if(factor!=null)for(int col:factor.columns())columns.getSelectionModel().select(col);ComboBox<String> marker=new ComboBox<>();marker.getItems().setAll(names);if(factor!=null)marker.getSelectionModel().select(factor.columns()[0]);marker.setAccessibleText("Fixed unit marker for "+name.getText());markers.add(marker);originalOrder.add(factor==null?new int[0]:factor.columns());titles.add(name);indicators.add(columns);VBox row=new VBox(5,new Label(j==q?"Outcome measurement":"Predictor "+(j+1)+" measurement"),name,columns,new Label("Fixed unit marker indicator"),marker);factors.getChildren().add(row);
        }
        for(int a=0;a<q;a++)for(int b=a;b<q;b++){var term=new LatentInteractionSem.Interaction(a,b);CheckBox box=new CheckBox(a==b?"Predictor "+(a+1)+" squared":"Predictor "+(a+1)+" × "+(b+1));box.setSelected(source!=null&&source.interactions().contains(term));terms.put(box,term);products.getChildren().add(box);}
    }
    void set(LatentInteractionSem.Model source){count.getValueFactory().setValue(source.predictors().size());rebuild(source.predictors().size(),source);}
    LatentInteractionSem.Model read(){List<LatentInteractionSem.Factor> values=new ArrayList<>();for(int j=0;j<titles.size();j++){var selected=indicators.get(j).getSelectionModel().getSelectedIndices();int marker=markers.get(j).getSelectionModel().getSelectedIndex();if(marker<0||!selected.contains(marker))throw new IllegalArgumentException("Choose a fixed marker among the selected indicators for "+titles.get(j).getText());LinkedHashSet<Integer> order=new LinkedHashSet<>();order.add(marker);for(int col:originalOrder.get(j))if(selected.contains(col))order.add(col);order.addAll(selected);values.add(new LatentInteractionSem.Factor(titles.get(j).getText(),order.stream().mapToInt(Integer::intValue).toArray()));}return new LatentInteractionSem.Model(values.subList(0,values.size()-1),values.getLast(),terms.entrySet().stream().filter(e->e.getKey().isSelected()).map(Map.Entry::getValue).toList());}
}
