package org.brightstats.stats;

import com.google.gson.*;
import java.nio.file.*;

public final class ReferenceInferenceChecks {
    private static int fields;private static double maximum;
    private static void close(double actual,double expected,String name){fields++;if(Double.isNaN(actual)&&Double.isNaN(expected))return;if(!Double.isFinite(actual)||!Double.isFinite(expected)){if(actual!=expected)throw new AssertionError(name+": "+actual+" != "+expected);return;}double difference=Math.abs(actual-expected);maximum=Math.max(maximum,difference);if(difference>1e-11*Math.max(1,Math.abs(expected)))throw new AssertionError(name+": "+actual+" != "+expected);}
    public static void main(String[] args)throws Exception{
        var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();int index=0;
        for(var element:input.getAsJsonArray("cases")){
            var value=element.getAsJsonObject();var rows=value.getAsJsonArray("draws");double[] draws=new double[rows.size()];for(int i=0;i<draws.length;i++)draws[i]=rows.get(i).getAsDouble();
            String originalMethod=value.get("method").getAsString();var method=switch(originalMethod){case "PERCENTILE_BC"->BootstrapIntervals.Method.PERCENTILE;case "PERCENTILE_BCA"->BootstrapIntervals.Method.BCA;default->BootstrapIntervals.Method.STUDENTIZED;};
            var actual=ReferenceBootstrapInference.evaluate(value.get("point").getAsDouble(),draws,1-value.get("alpha").getAsDouble(),method,value.get("tail").getAsString().equals("TWO_TAILED"));
            var interval=value.getAsJsonArray("interval").get(0).getAsJsonArray();var corrected=value.getAsJsonArray("correctedInterval").get(0).getAsJsonArray();var summary=value.getAsJsonArray("summary").get(0).getAsJsonArray();
            close(actual.interval().lower(),interval.get(2).getAsDouble(),index+" ordinary lower");close(actual.interval().upper(),interval.get(3).getAsDouble(),index+" ordinary upper");close(actual.correctedInterval().lower(),corrected.get(3).getAsDouble(),index+" corrected lower");close(actual.correctedInterval().upper(),corrected.get(4).getAsDouble(),index+" corrected upper");
            close(actual.mean(),summary.get(1).getAsDouble(),index+" mean");close(actual.standardError(),summary.get(2).getAsDouble(),index+" SD");close(actual.statistic(),summary.get(3).getAsDouble(),index+" statistic");close(actual.pValue(),summary.get(4).getAsDouble(),index+" p");index++;
        }
        for(var element:input.getAsJsonArray("tCritical")){var value=element.getAsJsonObject();close(ReferenceDistributions.tCritical(.05,value.get("df").getAsDouble()),value.get("value").getAsDouble(),"t critical");}
        int samplingCases=0;
        for(var element:input.getAsJsonArray("samplingCases")){var value=element.getAsJsonObject();int count=value.get("samples").getAsInt(),n=value.get("rows").getAsInt();long[] seeds=ReferenceResampling.seeds(ReferenceResampling.DEFAULT_SEED,count);var expected=value.getAsJsonArray("seeds");for(int i=0;i<count;i++){fields++;if(seeds[i]!=expected.get(i).getAsLong())throw new AssertionError("Reference resample seed changed");}int selected=0;for(int position:new int[]{0,1,count-1}){int[] rows=ReferenceResampling.rows(seeds[position],n);var expectedRows=value.getAsJsonArray("selectedRows").get(selected++).getAsJsonArray();for(int i=0;i<n;i++)close(rows[i],expectedRows.get(i).getAsInt(),"Row draw");}samplingCases++;}
        System.out.println("{\"unmodified_reference_cases\":"+index+",\"sampling_cases\":"+samplingCases+",\"numeric_comparisons\":"+fields+",\"maximum_absolute_difference\":"+maximum+"}");
    }
}
