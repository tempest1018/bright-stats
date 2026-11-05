package org.brightstats.data;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

/** Local presentation and working-memory limits; never changes statistical estimators. */
public record UserPreferences(Theme theme,Language language,String numberLocale,int fractionDigits,int fontSize,int historyStates,int historyMiB,long maximumDataCells,Motion motion,Accent accent,Density density,Layout layout,ChartPreset chartPreset,boolean showGrid,boolean snapToGrid){
    public enum Theme{LIGHT,DARK,SYSTEM,HIGH_CONTRAST}
    public enum Motion{FULL,REDUCED,OFF}
    public enum Accent{OCEAN,VIOLET,FOREST,AMBER}
    public enum Density{COMFORTABLE,COMPACT}
    public enum Layout{BALANCED,MODELING,RESULTS,PRESENTATION}
    public enum ChartPreset{STUDIO,PAPER,PRESENTATION}
    public enum Language{ENGLISH,SPANISH,FRENCH,GERMAN}
    public record Loaded(UserPreferences preferences,String warning){}
    private static final Gson JSON=new GsonBuilder().serializeNulls().setPrettyPrinting().setStrictness(Strictness.STRICT).create();
    public UserPreferences{Objects.requireNonNull(theme);Objects.requireNonNull(language);if(motion==null)motion=Motion.FULL;if(accent==null)accent=Accent.OCEAN;if(density==null)density=Density.COMFORTABLE;if(layout==null){layout=Layout.BALANCED;showGrid=true;snapToGrid=false;}if(chartPreset==null)chartPreset=ChartPreset.STUDIO;if(!List.of("en-US","en-GB","es-ES","fr-FR","de-DE","pt-BR","zh-CN","ja-JP","ar-SA","hi-IN").contains(numberLocale)||fractionDigits<0||fractionDigits>12||fontSize<11||fontSize>24||historyStates<10||historyStates>250||historyMiB<1||historyMiB>64||maximumDataCells<1000||maximumDataCells>5000000)throw new IllegalArgumentException("Preference values are outside their supported display/memory limits.");}
    public UserPreferences(Theme theme,Language language,String numberLocale,int fractionDigits,int fontSize,int historyStates,int historyMiB,long maximumDataCells,Motion motion,Accent accent,Density density){this(theme,language,numberLocale,fractionDigits,fontSize,historyStates,historyMiB,maximumDataCells,motion,accent,density,Layout.BALANCED,ChartPreset.STUDIO,true,false);}
    public UserPreferences display(Layout layout,ChartPreset chartPreset,boolean showGrid,boolean snapToGrid){return new UserPreferences(theme,language,numberLocale,fractionDigits,fontSize,historyStates,historyMiB,maximumDataCells,motion,accent,density,layout,chartPreset,showGrid,snapToGrid);}
    public UserPreferences(Theme theme,Language language,String numberLocale,int fractionDigits,int fontSize,int historyStates,int historyMiB,long maximumDataCells){this(theme,language,numberLocale,fractionDigits,fontSize,historyStates,historyMiB,maximumDataCells,Motion.FULL,Accent.OCEAN,Density.COMFORTABLE);}
    public static UserPreferences defaults(){return new UserPreferences(Theme.SYSTEM,Language.ENGLISH,"en-US",6,13,100,8,5000000);}
    public static Path path(Path home){return home.resolve(".bright-stats").resolve("preferences.json");}
    public static Loaded load(Path file){if(!Files.exists(file))return new Loaded(defaults(),"");try{if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>65536)throw new IllegalArgumentException("Expected a regular preferences file smaller than 64 KiB.");var object=BoundedJson.read(file).getAsJsonObject();if(!object.get("format").getAsString().equals("bright-stats-preferences")||object.get("version").getAsBigDecimal().intValueExact()!=1)throw new IllegalArgumentException("Unsupported preferences format.");UserPreferences preferences=JSON.fromJson(object.get("preferences"),UserPreferences.class);if(preferences==null)throw new IllegalArgumentException("Missing preferences object.");return new Loaded(preferences,"");}catch(IOException|RuntimeException problem){return new Loaded(defaults(),"Preferences could not be loaded; defaults are in use. The existing file was kept unchanged: "+problem.getMessage());}}
    public void save(Path file)throws IOException{Path parent=file.toAbsolutePath().normalize().getParent();Files.createDirectories(parent);var object=new JsonObject();object.addProperty("format","bright-stats-preferences");object.addProperty("version",1);object.add("preferences",JSON.toJsonTree(this));Path temporary=Files.createTempFile(parent,".preferences-",".tmp");try{Files.writeString(temporary,JSON.toJson(object));try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temporary);}}
}
