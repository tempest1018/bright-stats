package org.brightstats.data;

import com.google.gson.JsonObject;
import java.util.*;

/** Recover pre-imputation numeric cells so cross-validation cannot reuse full-cohort fitted means. */
public final class PredictionInput {
    private PredictionInput() {}
    public static double[][] original(JsonObject history,List<String> names,double[][] prepared) {
        if(history==null)return Arrays.stream(prepared).map(double[]::clone).toArray(double[][]::new);
        ImportHistory.verify(history,names,prepared);var source=ImportHistory.read(history);
        List<Integer> columns=new ArrayList<>();for(int j=0;j<source.definitions().size();j++)if(source.definitions().get(j).kind()==DataPreparation.Kind.NUMERIC)columns.add(j);
        double[][] result=new double[source.sourceRows().size()][columns.size()];
        for(int i=0;i<result.length;i++)for(int k=0;k<columns.size();k++) {
            String value=source.raw().rows().get(source.sourceRows().get(i)-1).get(columns.get(k));
            result[i][k]=DataPreparation.missing(value,source.settings())?Double.NaN:DataPreparation.number(value,source.settings().decimal());
        }
        return result;
    }
}
