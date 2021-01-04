package se.yarin.morphy.gui;

import de.codecentric.centerdevice.javafxsvg.SvgImageLoaderFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.caprica.vlcj.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.version.LibVlcVersion;
import uk.co.caprica.vlcj.version.Version;

public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static void setupLibVLC() throws RuntimeException {

        new NativeDiscovery().discover();

        // discovery()'s method return value is WRONG on Linux
        try {
            Version version = LibVlcVersion.getVersion();
            log.info("Found libvlc version " + version);
        } catch (Exception e) {
            log.info("Failed to locate libvlc; have you installed it in a custom location?");
            throw new RuntimeException();
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception{
        //SvgImageLoaderFactory.install();


        setupLibVLC();
        log.info("Starting OpenCBMPlayer");


        Parent root = FXMLLoader.load(getClass().getResource("/fxml/main.fxml"));

        primaryStage.setTitle("ChessBase Media Player (Proof of Concept)");
        primaryStage.setScene(new Scene(root, 800, 600));

        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });

        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
