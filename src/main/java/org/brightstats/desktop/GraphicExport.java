package org.brightstats.desktop;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
final class GraphicExport {
    private GraphicExport(){}
    static Color background(Node node){for(Node current=node;current!=null;current=current.getParent()){if(current.getStyleClass().contains("theme-dark"))return Color.web("#111d2a");if(current.getStyleClass().contains("theme-high-contrast"))return Color.BLACK;if(current.getStyleClass().contains("theme-light"))return Color.web("#f2f5fa");}return Color.WHITE;}

    static void png(Path path,Node node,double scale)throws Exception{if(!Double.isFinite(scale)||scale<.5||scale>4)throw new IllegalArgumentException("Choose a graphic scale between 0.5 and 4.");UiMotion.stopAll();while(node instanceof javafx.scene.control.ScrollPane scroll)node=scroll.getContent();node.applyCss();double width=Math.ceil(node.getBoundsInLocal().getWidth()*scale),height=Math.ceil(node.getBoundsInLocal().getHeight()*scale);if(width<1||height<1||width*height>16000000)throw new IllegalArgumentException("Graphic exceeds the 16-megapixel export limit.");if(Files.exists(path))throw new FileAlreadyExistsException(path.toString());SnapshotParameters parameters=new SnapshotParameters();parameters.setFill(background(node));parameters.setTransform(new Scale(scale,scale));WritableImage image=node.snapshot(parameters,new WritableImage((int)width,(int)height));BufferedImage bitmap=new BufferedImage((int)width,(int)height,BufferedImage.TYPE_INT_ARGB);int[] pixels=((java.awt.image.DataBufferInt)bitmap.getRaster().getDataBuffer()).getData();image.getPixelReader().getPixels(0,0,(int)width,(int)height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,(int)width);try(var output=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){if(!ImageIO.write(bitmap,"png",output))throw new IllegalStateException("PNG encoder unavailable.");}}
}
