package org.brightstats.model;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;

/** Named, independent model snapshots sharing the project's dataset by column binding. */
public final class ModelLibrary {
    public record Entry(String id,String name,TypedDiagram.Document document,String createdUtc,String modifiedUtc){
        public Entry{if(id==null||!id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))throw new IllegalArgumentException("Invalid library model identity.");name=label(name);Objects.requireNonNull(document);Instant.parse(createdUtc);Instant.parse(modifiedUtc);}
    }
    private final List<Entry> entries;
    public ModelLibrary(){this(List.of());}
    public ModelLibrary(List<Entry> entries){this.entries=List.copyOf(entries);if(entries.size()>256)throw new IllegalArgumentException("A project supports at most 256 named model snapshots.");Set<String> ids=new HashSet<>(),names=new HashSet<>();for(var entry:entries)if(!ids.add(entry.id())||!names.add(entry.document().family()+":"+entry.name().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Model identities and family/name combinations must be unique.");}
    public List<Entry> entries(){return entries;}
    public Entry get(String id){return entries.stream().filter(entry->entry.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Named model is missing."));}
    public ModelLibrary add(String name,TypedDiagram.Document document){String now=Instant.now().toString();List<Entry> copy=new ArrayList<>(entries);copy.add(new Entry(UUID.randomUUID().toString(),name,document,now,now));return new ModelLibrary(copy);}
    public ModelLibrary duplicate(String id,String name){return add(name,get(id).document());}
    public ModelLibrary rename(String id,String name){var old=get(id);List<Entry> copy=new ArrayList<>(entries);copy.set(copy.indexOf(old),new Entry(id,name,old.document(),old.createdUtc(),Instant.now().toString()));return new ModelLibrary(copy);}
    public ModelLibrary replace(String id,TypedDiagram.Document document){var old=get(id);if(old.document().family()!=document.family())throw new IllegalArgumentException("A saved model cannot change its estimator family.");List<Entry> copy=new ArrayList<>(entries);copy.set(copy.indexOf(old),new Entry(id,old.name(),document,old.createdUtc(),Instant.now().toString()));return new ModelLibrary(copy);}
    public ModelLibrary remove(String id){get(id);return new ModelLibrary(entries.stream().filter(entry->!entry.id().equals(id)).toList());}
    public JsonArray json(){JsonArray output=new JsonArray();for(var entry:entries){JsonObject value=new JsonObject();value.addProperty("id",entry.id());value.addProperty("name",entry.name());value.add("document",entry.document().json());value.addProperty("createdUtc",entry.createdUtc());value.addProperty("modifiedUtc",entry.modifiedUtc());output.add(value);}return output;}
    public static ModelLibrary read(JsonElement input){if(input==null||input.isJsonNull())return new ModelLibrary();if(!input.isJsonArray())throw new IllegalArgumentException("Saved model library must be an array.");List<Entry> entries=new ArrayList<>();for(var value:input.getAsJsonArray()){var object=value.getAsJsonObject();entries.add(new Entry(object.get("id").getAsString(),object.get("name").getAsString(),TypedDiagram.parse(object.get("document").toString()),object.get("createdUtc").getAsString(),object.get("modifiedUtc").getAsString()));}return new ModelLibrary(entries);}
    private static String label(String value){if(value==null)throw new IllegalArgumentException("Name the model.");String name=java.text.Normalizer.normalize(value.strip(),java.text.Normalizer.Form.NFC);if(name.isBlank()||name.length()>120||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Use a model name of 1-120 visible characters.");return name;}
}
