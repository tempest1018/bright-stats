package org.brightstats.data;

import java.util.*;
import com.google.gson.*;

/** Strict primitive/schema helpers for current statistical configuration formats. */
public final class StatisticalInput {
    private StatisticalInput(){}
    public static JsonObject object(JsonElement value,Set<String> allowed,String label,boolean optional){if(value==null||value.isJsonNull()){if(optional)return new JsonObject();throw new IllegalArgumentException("Provide "+label+".");}if(!value.isJsonObject())throw new IllegalArgumentException("Expected an object for "+label+".");var object=value.getAsJsonObject();for(String key:object.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unsupported "+label+" field: "+key);return object;}
    public static String text(JsonElement value){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected a JSON text label.");return value.getAsString();}
    private static JsonPrimitive numeric(JsonElement value){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!Double.isFinite(value.getAsDouble()))throw new IllegalArgumentException("Statistical numeric settings require finite JSON numbers.");return value.getAsJsonPrimitive();}
    public static double number(JsonObject object,String key,double fallback){return object.has(key)?numeric(object.get(key)).getAsDouble():fallback;}
    public static int integer(JsonObject object,String key,int fallback){return object.has(key)?numeric(object.get(key)).getAsBigDecimal().intValueExact():fallback;}
    public static long integer64(JsonObject object,String key,long fallback){return object.has(key)?numeric(object.get(key)).getAsBigDecimal().longValueExact():fallback;}
    public static boolean bool(JsonObject object,String key,boolean fallback){if(!object.has(key))return fallback;var value=object.get(key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Declare "+key+" as a JSON boolean.");return value.getAsBoolean();}
    public static <T extends Enum<T>>T enumeration(JsonObject object,String key,Class<T> type,T fallback){return object.has(key)?Enum.valueOf(type,text(object.get(key))):fallback;}
}
