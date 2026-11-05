package org.brightstats.desktop;
import java.util.*;import com.google.gson.*;import javafx.event.*;import javafx.scene.Node;import org.brightstats.report.GraphicPoint;import org.brightstats.stats.AnalysisIdentity;
/** Scene-local source selection, isolated from data filtering, estimation and persistence. */
final class VisualSelection {
 private VisualSelection(){}
 static final EventType<Selection> TYPE=new EventType<>(Event.ANY,"BRIGHTSTATS_VISUAL_SELECTION");
 static final class Selection extends Event {
  final String frame,label;final List<Integer> rows;final List<String> columns;
  Selection(String frame,String label,List<Integer> rows,List<String> columns){super(TYPE);this.frame=frame;this.label=label;this.rows=List.copyOf(rows);this.columns=List.copyOf(columns);}
 }
 static String frame(JsonObject project){JsonObject source=new JsonObject();for(String key:List.of("columnNames","observations","dataPreparation","derivation"))if(project.has(key))source.add(key,project.get(key));return AnalysisIdentity.json(source);}
 static void attach(Node owner,JsonObject project){List<Integer> rows=new ArrayList<>();if(project.has("dataPreparation")){var preparation=project.get("dataPreparation");if(!preparation.isJsonObject()||!preparation.getAsJsonObject().has("sourceRows")||!preparation.getAsJsonObject().get("sourceRows").isJsonArray())return;var source=preparation.getAsJsonObject().getAsJsonArray("sourceRows");for(var row:source)rows.add(row.getAsInt());}else if(project.has("observations"))for(int i=1;i<=project.getAsJsonArray("observations").size();i++)rows.add(i);owner.getProperties().put("brightstats.reportSourceFrame",frame(project));owner.getProperties().put("brightstats.reportSourceRows",List.copyOf(rows));}
 @SuppressWarnings("unchecked") static void publish(Node node,GraphicPoint point){for(Node current=node;current!=null;current=current.getParent())if(current.getProperties().get("brightstats.reportSourceFrame") instanceof String frame){List<Integer> rows=point.rows();if(point.rowSpace()==GraphicPoint.RowSpace.ANALYSIS){var mapping=(List<Integer>)current.getProperties().get("brightstats.reportSourceRows");List<Integer> original=new ArrayList<>();for(int row:rows){if(row>mapping.size())return;original.add(mapping.get(row-1));}rows=List.copyOf(original);}node.fireEvent(new Selection(frame,point.label(),rows,point.columns()));return;}}
 static void publish(Node node,String frame,String label,List<Integer> rows,List<String> columns){node.fireEvent(new Selection(frame,label,rows,columns));}
}
