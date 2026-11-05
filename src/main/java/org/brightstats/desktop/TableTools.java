package org.brightstats.desktop;

import java.util.*;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.application.Platform;
import javafx.geometry.Orientation;

/** Filtering/sorting and complete labels leave canonical report data untouched. */
final class TableTools {
    private TableTools(){}
    static void labels(TableView<String[]> table){
        table.getProperties().put("brightstats.canonicalHeaders",table.getColumns().stream().map(TableColumn::getText).toList());
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);table.setTableMenuButtonVisible(true);
        for(var column:table.getColumns())decorateColumn(table,column);
    }
    private static <T> void decorateColumn(TableView<String[]> table,TableColumn<String[],T> column){
            column.setComparator((a,b)->compareCells(a==null?null:a.toString(),b==null?null:b.toString()));
            column.setResizable(true);column.setCellFactory(ignore->new TableCell<String[],T>(){protected void updateItem(T value,boolean empty){super.updateItem(value,empty);setText(empty||value==null?null:value.toString());setTooltip(empty||value==null?null:new Tooltip(value.toString()));}});
            Label heading=new Label("ⓘ");heading.setTooltip(new Tooltip(column.getText()));column.setGraphic(heading);
            ContextMenu menu=new ContextMenu();MenuItem first=new MenuItem("Move column to the left");first.setOnAction(e->{table.getColumns().remove(column);table.getColumns().addFirst(column);});MenuItem reset=new MenuItem("Fit column to readable width");reset.setOnAction(e->column.setPrefWidth(Math.max(150,Math.min(480,column.getText().length()*8+35))));menu.getItems().addAll(first,reset);column.setContextMenu(menu);
    }
    static BorderPane wrap(TableView<String[]> table){
        if(table.getProperties().containsKey("brightstats.table.wrapper"))return (BorderPane)table.getProperties().get("brightstats.table.wrapper");
        var source=FXCollections.observableArrayList(table.getItems());table.getProperties().put("brightstats.canonicalRows",source);var filtered=new FilteredList<>(source,v->true);var sorted=new SortedList<>(filtered);sorted.comparatorProperty().bind(table.comparatorProperty());table.setItems(sorted);
        TextField search=new TextField();search.setPromptText("Filter rows in this section");search.setAccessibleText("Filter report rows");Label count=new Label(source.size()+" rows");Label identity=new Label("Select a row to keep its identity visible while scrolling.");identity.setWrapText(true);identity.getStyleClass().add("muted");
        search.textProperty().addListener((o,a,b)->{String query=b.strip().toLowerCase(Locale.ROOT);filtered.setPredicate(row->query.isEmpty()||Arrays.stream(row).anyMatch(value->value!=null&&value.toLowerCase(Locale.ROOT).contains(query)));count.setText(filtered.size()+" / "+source.size()+" rows");});
        table.getSelectionModel().selectedItemProperty().addListener((o,a,b)->identity.setText(b==null?"No row selected":Arrays.stream(b).limit(2).map(v->v==null?"":v).reduce((x,y)->x+"  ·  "+y).orElse("")));
        Button clear=new Button("Clear filter");clear.setOnAction(e->search.clear());MenuButton pin=new MenuButton("Pin a column");HBox tools=new HBox(8,search,clear,pin,count);HBox.setHgrow(search,Priority.ALWAYS);VBox top=new VBox(5,tools,identity);top.setStyle("-fx-padding:8;");BorderPane wrapper=new BorderPane(table,top,null,null,null);
        MenuItem unpin=new MenuItem("Unpin");unpin.setOnAction(e->{detach(table);wrapper.setLeft(null);table.getProperties().remove("brightstats.pinnedTable");});pin.getItems().add(unpin);
        for(var original:table.getColumns()){
            MenuItem item=new MenuItem(original.getText());item.setOnAction(event->{
                detach(table);List<Runnable> cleanup=new ArrayList<>();table.getProperties().put("brightstats.detachPinned",(Runnable)()->{for(var action:cleanup)action.run();});TableView<String[]> frozen=new TableView<>();frozen.getProperties().put("brightstats.presentationOnly",true);frozen.setItems(table.getItems());frozen.setPrefWidth(240);frozen.setMinWidth(150);frozen.setMaxWidth(360);TableColumn<String[],String> col=new TableColumn<>(original.getText());col.setCellValueFactory(cell->new javafx.beans.property.ReadOnlyStringWrapper(Objects.toString(original.getCellData(cell.getValue()),"")));col.setPrefWidth(225);col.setSortable(false);frozen.getColumns().add(col);labels(frozen);
                double rowHeight=Math.max(28,UiAppearance.current().fontSize()+14);table.setFixedCellSize(rowHeight);frozen.setFixedCellSize(rowHeight);boolean[] selecting={false};
                frozen.getSelectionModel().selectedIndexProperty().addListener((o,a,b)->{if(!selecting[0]){selecting[0]=true;table.getSelectionModel().select(b.intValue());selecting[0]=false;}});javafx.beans.value.ChangeListener<Number> selectionListener=(o,a,b)->{if(!selecting[0]){selecting[0]=true;frozen.getSelectionModel().select(b.intValue());selecting[0]=false;}};table.getSelectionModel().selectedIndexProperty().addListener(selectionListener);cleanup.add(()->table.getSelectionModel().selectedIndexProperty().removeListener(selectionListener));
                wrapper.setLeft(frozen);table.getProperties().put("brightstats.pinnedTable",frozen);Platform.runLater(()->{if(wrapper.getLeft()!=frozen)return;frozen.applyCss();frozen.layout();table.applyCss();table.layout();ScrollBar a=vertical(table),b=vertical(frozen);if(a!=null&&b!=null){boolean[] changing={false};javafx.beans.value.ChangeListener<Number> toFrozen=(o,old,v)->sync(a,b,changing),toMain=(o,old,v)->sync(b,a,changing);a.valueProperty().addListener(toFrozen);b.valueProperty().addListener(toMain);cleanup.add(()->a.valueProperty().removeListener(toFrozen));cleanup.add(()->b.valueProperty().removeListener(toMain));sync(a,b,changing);}});
            });pin.getItems().add(item);
        }
        table.getProperties().put("brightstats.pinMenu",pin);table.getProperties().put("brightstats.table.wrapper",wrapper);return wrapper;
    }
    private static void detach(TableView<?> table){Object old=table.getProperties().remove("brightstats.detachPinned");if(old instanceof Runnable action)action.run();}
    static int compareCells(String a,String b){if(a==null)return b==null?0:1;if(b==null)return -1;Double x=numeric(a),y=numeric(b);if(x!=null)return y!=null?Double.compare(x,y):-1;if(y!=null)return 1;return a.compareToIgnoreCase(b);}
    private record Parsed(Double value){}
    private static final class ParseCache {String locale="",exponent="E";char decimal='.',minus='-';final LinkedHashMap<String,Parsed> values=new LinkedHashMap<>(1024,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,Parsed> entry){return size()>8192;}};}
    private static final ThreadLocal<ParseCache> CACHE=ThreadLocal.withInitial(ParseCache::new);
    private static Double numeric(String text){var cache=CACHE.get();String locale=UiAppearance.current().numberLocale();if(!locale.equals(cache.locale)){cache.locale=locale;var symbols=java.text.DecimalFormatSymbols.getInstance(Locale.forLanguageTag(locale));cache.decimal=symbols.getDecimalSeparator();cache.exponent=symbols.getExponentSeparator();cache.minus=symbols.getMinusSign();cache.values.clear();}var found=cache.values.get(text);if(found!=null)return found.value();Double number=parseNumeric(text,cache.decimal,cache.exponent,cache.minus);if(text.length()<=128)cache.values.put(text,new Parsed(number));return number;}
    static Double parseNumeric(String text,char decimal){return parseNumeric(text,decimal,"E",'-');}
    private static Double parseNumeric(String text,char decimal,String exponent,char minus){
        String normalized=exponent.equals("E")?text:text.replace(exponent,"E");StringBuilder value=new StringBuilder();
        for(char c:normalized.strip().toCharArray()){if(Character.getType(c)==Character.FORMAT)continue;int digit=Character.digit(c,10);if(digit>=0)value.append((char)('0'+digit));else if(c==decimal)value.append('.');else if(c=='−'||c==minus)value.append('-');else value.append(c);}
        String parsed=value.toString();if(parsed.equals("∞"))return Double.POSITIVE_INFINITY;if(parsed.equals("-∞"))return Double.NEGATIVE_INFINITY;
        try{return Double.valueOf(parsed);}catch(NumberFormatException invalid){return null;}
    }
    private static ScrollBar vertical(TableView<?> table){return table.lookupAll(".scroll-bar").stream().filter(n->n instanceof ScrollBar bar&&bar.getOrientation()==Orientation.VERTICAL).map(n->(ScrollBar)n).findFirst().orElse(null);}
    private static void sync(ScrollBar from,ScrollBar to,boolean[] changing){if(changing[0])return;changing[0]=true;double range=from.getMax()-from.getMin();to.setValue(to.getMin()+(range==0?0:(from.getValue()-from.getMin())/range)*(to.getMax()-to.getMin()));changing[0]=false;}
}
