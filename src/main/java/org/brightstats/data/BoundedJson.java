package org.brightstats.data;
import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;
/** Strict bounded reader for Bright-Stats JSON, separate from proprietary interchange decoding. */
public final class BoundedJson {
 private BoundedJson(){}
 public static final int MAX_BYTES=64*1024*1024,MAX_DEPTH=64,MAX_NODES=8000000,MAX_STRING=4000000;
 public static JsonElement read(Path path)throws IOException{if(Files.size(path)>MAX_BYTES)throw new IllegalArgumentException("JSON exceeds 64 MiB.");byte[] bytes;try(var stream=Files.newInputStream(path)){bytes=stream.readNBytes(MAX_BYTES+1);}if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("JSON exceeds 64 MiB.");var decoder=StandardCharsets.UTF_8.newDecoder();return parse(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString());}
 public static JsonElement parse(String text){if(text==null||text.length()>MAX_BYTES||text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("JSON exceeds 64 MiB.");try(var reader=new JsonReader(new StringReader(text))){reader.setStrictness(Strictness.STRICT);var value=next(reader,0,new int[]{0});if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing JSON input.");return value;}catch(IOException|NumberFormatException error){throw new JsonSyntaxException("Malformed bounded JSON.",error);}}
 private static String string(String text){if(text.length()>MAX_STRING)throw new IllegalArgumentException("JSON string exceeds four million characters.");for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isHighSurrogate(c)){if(i+1>=text.length()||!Character.isLowSurrogate(text.charAt(++i)))throw new IllegalArgumentException("Unpaired Unicode surrogate.");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Unpaired Unicode surrogate.");}return text;}
 private static JsonElement next(JsonReader reader,int depth,int[] nodes)throws IOException{if(depth>MAX_DEPTH||++nodes[0]>MAX_NODES)throw new IllegalArgumentException("JSON depth/node budget exceeded.");switch(reader.peek()){
 case BEGIN_OBJECT:{reader.beginObject();JsonObject value=new JsonObject();while(reader.hasNext()){String key=string(reader.nextName());if(value.has(key))throw new IllegalArgumentException("Duplicate JSON key.");value.add(key,next(reader,depth+1,nodes));}reader.endObject();return value;}
 case BEGIN_ARRAY:{reader.beginArray();JsonArray value=new JsonArray();while(reader.hasNext())value.add(next(reader,depth+1,nodes));reader.endArray();return value;}
 case STRING:return new JsonPrimitive(string(reader.nextString()));
 case NUMBER:{String token=reader.nextString();if(token.length()>256)throw new IllegalArgumentException("Numeric JSON literal exceeds 256 characters.");return new JsonPrimitive(new BigDecimal(token));}
 case BOOLEAN:return new JsonPrimitive(reader.nextBoolean());
 case NULL:reader.nextNull();return JsonNull.INSTANCE;
 default:throw new IllegalArgumentException("Expected a JSON value.");}}
}
