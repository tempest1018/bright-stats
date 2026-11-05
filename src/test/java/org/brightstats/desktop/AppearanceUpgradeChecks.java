package org.brightstats.desktop;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import javafx.application.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.brightstats.data.UserPreferences;
import static org.brightstats.data.UserPreferences.*;

/** Real JavaFX preference/palette controls, simulated OS events and persisted legacy migration. */
public final class AppearanceUpgradeChecks extends Application {
    static int checks;static Throwable failure;
    static void require(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static UserPreferences settings(Theme theme,Motion motion,Accent accent,Density density){return new UserPreferences(theme,Language.ENGLISH,"en-US",6,13,100,8,5000000,motion,accent,density);}
    public void start(Stage primary){
        try{
            Path out=Path.of(getParameters().getRaw().getFirst());Files.createDirectories(out);
            var original=settings(Theme.SYSTEM,Motion.OFF,Accent.VIOLET,Density.COMPACT);Path file=out.resolve("preferences.json");original.save(file);
            require(UserPreferences.load(file).preferences().equals(original),"Appearance preferences roundtrip");
            JsonObject legacy=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var saved=legacy.getAsJsonObject("preferences");saved.remove("motion");saved.remove("accent");saved.remove("density");saved.addProperty("theme","DARK");Files.writeString(file,legacy.toString());var migrated=UserPreferences.load(file);
            require(migrated.warning().isEmpty()&&migrated.preferences().theme()==Theme.DARK&&migrated.preferences().motion()==Motion.FULL&&migrated.preferences().accent()==Accent.OCEAN,"Version-one preference migration");
            var title=new Label("Find your next insight");title.getStyleClass().add("section-title");var subtitle=new Label("Appearance is personal. Your calculations stay reproducible.");subtitle.getStyleClass().add("muted");var card=new VBox(12,new Label("A clearer research workspace"),new Button("Explore analyses"),new TextField("Name your model"));card.getStyleClass().add("studio-card");var body=new VBox(16,title,subtitle,card);body.setStyle("-fx-padding:24;");primary.setScene(new Scene(body,720,360));primary.show();Stage secondary=new Stage();var second=new VBox(new Label("Open dialog"));secondary.setScene(new Scene(second,250,120));secondary.show();
            for(Theme theme:Theme.values())for(Accent accent:Accent.values()){
                var preference=settings(theme,Motion.OFF,accent,Density.COMFORTABLE);UiAppearance.install(preference);body.applyCss();body.layout();
                require(body.getStyleClass().contains("theme-"+UiAppearance.effectiveTheme(theme,Platform.getPreferences().getColorScheme()).name().toLowerCase().replace('_','-')),"Theme resolves to a renderable palette");
                require(second.getStyleClass().stream().anyMatch(v->v.startsWith("theme-")),"Open dialogs inherit appearance");
                require(body.getStyleClass().contains("accent-"+accent.name().toLowerCase()),"Accent class follows preference");
                GraphicExport.png(out.resolve(theme.name().toLowerCase()+"-"+accent.name().toLowerCase()+".png"),body,1);
                if(accent==Accent.OCEAN){VBox transparent=new VBox(new Label("An exported plot caption"));transparent.setPrefHeight(60);body.getChildren().add(transparent);body.applyCss();body.layout();Path sample=out.resolve(theme.name().toLowerCase()+"-transparent.png");GraphicExport.png(sample,transparent,1);var png=javax.imageio.ImageIO.read(sample.toFile());int pixel=png.getRGB(png.getWidth()-1,png.getHeight()-1);var expected=GraphicExport.background(transparent);require(Math.abs(((pixel>>16)&255)-(int)Math.round(expected.getRed()*255))<3&&Math.abs((pixel&255)-(int)Math.round(expected.getBlue()*255))<3,"Transparent export keeps the inherited theme background");body.getChildren().remove(transparent);}

            }
            UiAppearance.install(settings(Theme.SYSTEM,Motion.OFF,Accent.OCEAN,Density.COMFORTABLE));UiAppearance.systemSchemeChanged(ColorScheme.DARK);
            require(body.getStyleClass().contains("theme-dark")&&second.getStyleClass().contains("theme-dark"),"Simulated native dark notification updates every open window");
            UiAppearance.systemSchemeChanged(ColorScheme.LIGHT);require(body.getStyleClass().contains("theme-light"),"Simulated native light notification updates an existing scene");
            UiAppearance.install(settings(Theme.DARK,Motion.OFF,Accent.OCEAN,Density.COMFORTABLE));UiAppearance.systemSchemeChanged(ColorScheme.LIGHT);require(body.getStyleClass().contains("theme-dark"),"Explicit Dark overrides system changes");
            UiAppearance.install(settings(Theme.LIGHT,Motion.OFF,Accent.OCEAN,Density.COMFORTABLE));UiAppearance.systemSchemeChanged(ColorScheme.DARK);require(body.getStyleClass().contains("theme-light"),"Explicit Light overrides system changes");
            UiMotion.reveal(card);require(card.getOpacity()==1,"Off motion changes no opacity");
            UiAppearance.install(settings(Theme.LIGHT,Motion.FULL,Accent.OCEAN,Density.COMFORTABLE));UiMotion.reveal(card);UiAppearance.install(settings(Theme.LIGHT,Motion.OFF,Accent.OCEAN,Density.COMPACT));require(card.getOpacity()==1,"Disabling motion stops ongoing transitions");require(body.getStyleClass().contains("density-compact"),"Compact density applied");
            UiAppearance.systemSchemeChanged(Platform.getPreferences().getColorScheme());
            var configured=PreferencesDialog.smoke(primary,out.resolve("preferences-dialog.png"));require(configured.fontSize()==16,"Actual dialog controls validate and bind");
            Files.writeString(out.resolve("receipt.json"),"{\"state\":\"passed\",\"checks\":"+checks+",\"palette_combinations\":16,\"simulated_live_os_notifications\":true,\"actual_os_setting_changed\":false}");System.out.println("Appearance checks: "+checks);secondary.close();primary.close();Platform.exit();
        }catch(Throwable error){failure=error;error.printStackTrace();Platform.exit();}
    }
    public static void main(String[] args){launch(args);if(failure!=null)System.exit(1);}
}
