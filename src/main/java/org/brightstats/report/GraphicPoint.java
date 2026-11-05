package org.brightstats.report;
import java.util.*;
/** Explicit source binding for a plotted mark; inference never guesses case IDs from axis values. */
public record GraphicPoint(String label,List<Integer> rows,List<String> columns,RowSpace rowSpace){
 public enum RowSpace { ORIGINAL, ANALYSIS }
 public GraphicPoint{label=Objects.requireNonNullElse(label,"");rows=List.copyOf(rows);columns=List.copyOf(columns);Objects.requireNonNull(rowSpace);if(rows.stream().anyMatch(r->r==null||r<1)||new HashSet<>(rows).size()!=rows.size()||columns.stream().anyMatch(s->s==null||s.isBlank()))throw new IllegalArgumentException("Graphic source bindings require positive one-based rows and named columns.");}
 public static GraphicPoint original(String label,List<Integer> rows,List<String> columns){return new GraphicPoint(label,rows,columns,RowSpace.ORIGINAL);}
 public static GraphicPoint analysis(String label,int row,List<String> columns){return new GraphicPoint(label,List.of(row),columns,RowSpace.ANALYSIS);}
 @Override public String toString(){return label;}
}
