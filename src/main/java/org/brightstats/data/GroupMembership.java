package org.brightstats.data;

import java.util.*;
import com.google.gson.JsonObject;

/** Retained source labels in prepared-row order; missing/ignored cells never create implicit groups. */
public final class GroupMembership {
    private GroupMembership(){}
    public static Map<String,List<String>> columns(JsonObject document){
        if(document==null)return Map.of();var history=ImportHistory.read(document);var prepared=DataPreparation.prepare(history.raw(),history.settings(),history.definitions());Map<String,List<String>> columns=new LinkedHashMap<>();for(int j=0;j<history.definitions().size();j++){if(history.definitions().get(j).kind()==DataPreparation.Kind.IGNORE)continue;int column=j;List<String> labels=prepared.retainedRows().stream().map(row->DataPreparation.missing(row.get(column),history.settings())?"":row.get(column).trim()).toList();long levels=labels.stream().filter(v->!v.isBlank()).distinct().count();if(levels>=2&&levels<=100)columns.put(history.raw().headers().get(j)+" [source column "+(j+1)+"]",labels);}return Collections.unmodifiableMap(columns);
    }
}
