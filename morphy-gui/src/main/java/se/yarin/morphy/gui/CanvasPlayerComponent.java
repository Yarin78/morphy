package se.yarin.morphy.gui;

import com.sun.jna.Memory;
import javafx.application.Platform;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import uk.co.caprica.vlcj.component.DirectMediaPlayerComponent;
import uk.co.caprica.vlcj.player.direct.BufferFormat;
import uk.co.caprica.vlcj.player.direct.BufferFormatCallback;
import uk.co.caprica.vlcj.player.direct.DirectMediaPlayer;
import uk.co.caprica.vlcj.player.direct.format.RV32BufferFormat;

import java.nio.ByteBuffer;

public class CanvasPlayerComponent extends DirectMediaPlayerComponent {

    private WritablePixelFormat<ByteBuffer> pixelFormat = PixelFormat.getByteBgraPreInstance();
    private WritableImage image;
    private FloatProperty videoSourceRatioProperty = new SimpleFloatProperty(0.75f);

    public FloatProperty getVideoSourceRatioProperty() {
        return videoSourceRatioProperty;
    }

    public CanvasPlayerComponent(int renderWidth, int renderHeight, WritableImage image) {
        super(new CanvasBufferFormatCallback(renderWidth, renderHeight));
        this.image = image;
    }

    @Override
    public void display(DirectMediaPlayer mediaPlayer, Memory[] nativeBuffers, BufferFormat bufferFormat) {
        Platform.runLater(() -> {
            Memory nativeBuffer = mediaPlayer.lock()[0];
            try {
                ByteBuffer byteBuffer = nativeBuffer.getByteBuffer(0, nativeBuffer.size());
                image.getPixelWriter().setPixels(0, 0, bufferFormat.getWidth(), bufferFormat.getHeight(), pixelFormat, byteBuffer, bufferFormat.getPitches()[0]);
            } finally {
                mediaPlayer.unlock();
            }
        });
    }

    private static class CanvasBufferFormatCallback implements BufferFormatCallback {

        private int renderWidth;
        private int renderHeight;

        public CanvasBufferFormatCallback(int renderWidth, int renderHeight) {
            this.renderWidth = renderWidth;
            this.renderHeight = renderHeight;
        }

        @Override
        public BufferFormat getBufferFormat(int sourceWidth, int sourceHeight) {
            Rectangle2D visualBounds = new Rectangle2D(0, 0, renderWidth, renderHeight);
//            Platform.runLater(() -> videoSourceRatioProperty.set((float) sourceHeight / (float) sourceWidth));
            return new RV32BufferFormat((int) visualBounds.getWidth(), (int) visualBounds.getHeight());
        }
    }
}
