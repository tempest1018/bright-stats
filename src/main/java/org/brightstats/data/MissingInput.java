package org.brightstats.data;
import java.util.*;
import com.google.gson.*;

/** Recover all original numeric source rows, including rows removed by prior preparation. */
public final class MissingInput {
    private MissingInput(){}
    public static double[][] originalAll(JsonObject history,List<String> names,double[][] prepared){if(history==null)return org.brightstats.stats.Matrices.copy(prepared);ImportHistory.verify(history,names,prepared);var source=ImportHistory.read(history);List<Integer> numeric=new ArrayList<>();for(int i=0;i<source.definitions().size();i++)if(source.definitions().get(i).kind()==DataPreparation.Kind.NUMERIC)numeric.add(i);double[][] values=new double[source.raw().rows().size()][numeric.size()];for(int row=0;row<values.length;row++)for(int column=0;column<numeric.size();column++){String cell=source.raw().rows().get(row).get(numeric.get(column));values[row][column]=DataPreparation.missing(cell,source.settings())?Double.NaN:DataPreparation.number(cell,source.settings().decimal());}return values;}
    public static double[][] matrix(JsonElement value){var rows=value.getAsJsonArray();double[][] data=new double[rows.size()][];for(int row=0;row<data.length;row++){var cells=rows.get(row).getAsJsonArray();data[row]=new double[cells.size()];for(int column=0;column<cells.size();column++){var cell=cells.get(column);if(cell.isJsonNull())data[row][column]=Double.NaN;else if(cell.isJsonPrimitive()&&cell.getAsJsonPrimitive().isNumber()&&Double.isFinite(cell.getAsDouble()))data[row][column]=cell.getAsDouble();else throw new IllegalArgumentException("Missing-data input must contain finite JSON numbers or null cells.");}}return data;}
}
