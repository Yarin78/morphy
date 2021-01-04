package se.yarin.morphy.gui;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.annotations.GraphicalAnnotationColor;
import se.yarin.cbhlib.annotations.GraphicalArrowsAnnotation;
import se.yarin.cbhlib.annotations.GraphicalSquaresAnnotation;
import se.yarin.cbhlib.media.ChessBaseMediaException;
import se.yarin.cbhlib.media.ChessBaseMediaLoader;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotations;
import se.yarin.chess.timeline.GameEventException;
import se.yarin.chess.timeline.NavigableGameModelTimeline;
import se.yarin.chess.timeline.ReplaceAllEvent;
import uk.co.caprica.vlcj.player.direct.DirectMediaPlayer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

import static se.yarin.chess.Chess.*;

public class Controller implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(Controller.class);

    private static Image chessImages = new Image("/images/pieces.png");
    private static Image boardBackground = new Image("/images/wooden-background.jpg");

    private final double BOARD_EDGE_SIZE = 0.15; // Size of board edge relative to the size of a square

    private final double GRAPHICAL_ARROW_OPACITY = 0.6;
    private final double GRAPHICAL_SQUARE_OPACITY = 0.4;
    private final int GRAPHICAL_COLOR_INTENSITY = 220;
    private final int VLC_RENDER_WIDTH = 300;
    private final int VLC_RENDER_HEIGHT = 200;

    private double squareSize, boardSize, xMargin, yMargin, edgeSize;

    @FXML private Canvas board;
    @FXML private TilePane leftPane;
    @FXML private SplitPane leftSplitter;
    @FXML private SplitPane rightSplitter;
    @FXML private VBox notationBox;
    @FXML private VBox videoBox;
    @FXML private TextFlow playerNames;
    @FXML private TextFlow gameDetails;
    @FXML private Slider slider;
    @FXML private Label currentTime;
    @FXML private ImageView videoImage;
    @FXML private Pane playerHolder;
    @FXML private MovesPane movesPane;

    private CanvasPlayerComponent mediaPlayerComponent;
    private Timer videoTimer;

    private NavigableGameModelTimeline model = new NavigableGameModelTimeline();

    public Controller() {
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        movesPane.setModel(model.getModel());

        board.widthProperty().bind(leftPane.widthProperty().subtract(20));
        board.heightProperty().bind(leftPane.heightProperty().subtract(20));

//        movePane.prefHeightProperty().bind(rightSplitter.heightProperty());
        videoBox.prefHeightProperty().bind(rightSplitter.heightProperty());
        //notationBox.prefWidthProperty().bind(rightSplitter.widthProperty());

        slider.prefWidthProperty().bind(videoBox.widthProperty().subtract(80));
        movesPane.prefWidthProperty().bind(rightSplitter.widthProperty());

        board.widthProperty().addListener(observable -> drawBoard());
        board.heightProperty().addListener(observable -> drawBoard());

        videoImage.setImage(new WritableImage(VLC_RENDER_WIDTH, VLC_RENDER_HEIGHT));
        mediaPlayerComponent = new CanvasPlayerComponent(VLC_RENDER_WIDTH, VLC_RENDER_HEIGHT, (WritableImage) videoImage.getImage());

        playerHolder.prefWidthProperty().bind(videoBox.widthProperty());
        playerHolder.prefHeightProperty().bind(videoBox.heightProperty().subtract(40)); // Compensate for slider underneath

        playerHolder.widthProperty().addListener((observable, oldValue, newValue) -> {
            fitImageViewSize(newValue.floatValue(), (float) playerHolder.getHeight());
        });

        playerHolder.heightProperty().addListener((observable, oldValue, newValue) -> {
            fitImageViewSize((float) playerHolder.getWidth(), newValue.floatValue());
        });

        mediaPlayerComponent.getVideoSourceRatioProperty().addListener((observable, oldValue, newValue) -> {
            fitImageViewSize((float) playerHolder.getWidth(), (float) playerHolder.getHeight());
        });


//        reloadGame(null);
//        reloadVideo();
//        reloadManualGame();
    }

    private void initBoardSize() {
        double w = board.getWidth(), h = board.getHeight();
        squareSize = Math.min(w, h) / (8 + BOARD_EDGE_SIZE * 2);
        boardSize = (8 + BOARD_EDGE_SIZE * 2) * squareSize;
        xMargin = (w - boardSize) / 2;
        yMargin = (h - boardSize) / 2;
        edgeSize = BOARD_EDGE_SIZE * squareSize;
    }

    private Point2D getSquareMidpoint(int x, int y) {
        return new Point2D(
                xMargin + squareSize * (x + BOARD_EDGE_SIZE) + squareSize / 2,
                yMargin + squareSize * (7 - y + BOARD_EDGE_SIZE) + squareSize / 2);
    }

    private Rectangle getSquareRect(int x, int y) {
        return new Rectangle(
                xMargin + squareSize * (x + BOARD_EDGE_SIZE),
                yMargin + squareSize * (7 - y + BOARD_EDGE_SIZE),
                squareSize,
                squareSize);
    }


    private void drawBoard() {
        log.debug("starting to draw board");
        long start = System.currentTimeMillis();
//        log.debug("rightSplitter width: " + rightSplitter.getWidth());
//        log.debug("movePane width: " + movePane.getWidth());
//        log.debug("moveBox width: " + moveBox.getWidth());

        GraphicsContext gc = board.getGraphicsContext2D();

        // Overwrite entire canvas is necessary when resizing
        board.getParent().setStyle("-fx-background-color: darkgray;");
        gc.setFill(Color.DARKGRAY);
        gc.fillRect(0, 0, board.getWidth(), board.getHeight());

        initBoardSize();

//        gc.drawImage(boardBackground, 0, 0, 2000, 1500, xMargin, yMargin, boardSize, boardSize);
        gc.drawImage(boardBackground, 0, 0, 3800, 2900, xMargin, yMargin, boardSize, boardSize);

        gc.setFill(Color.rgb(128, 0, 0, 0.5));
        gc.fillRect(xMargin, yMargin, boardSize, edgeSize);
        gc.fillRect(xMargin, yMargin + boardSize - edgeSize, boardSize, edgeSize);
        gc.fillRect(xMargin, yMargin + edgeSize, edgeSize, boardSize - edgeSize * 2);
        gc.fillRect(xMargin + boardSize - edgeSize , yMargin + edgeSize, edgeSize, boardSize - edgeSize * 2);
        gc.setLineWidth(1.0);
        gc.setStroke(Color.rgb(64, 0, 0, 0.3));
        gc.strokeRect(xMargin, yMargin, boardSize, boardSize);
        gc.strokeRect(xMargin + edgeSize , yMargin + edgeSize, boardSize - 2 * edgeSize, boardSize - 2 * edgeSize);

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                Rectangle sq = getSquareRect(x, y);
                if ((x+y)%2 == 0) {
                    gc.setFill(Color.rgb(139, 69, 19, 0.6));
//                    gc.setFill(Color.rgb(160, 82, 45, 0.6));
                    gc.fillRect(sq.getX(), sq.getY(), sq.getWidth(), sq.getHeight());
                }
            }
        }

        drawGraphicalSquares(gc);

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                Stone piece = model.getModel().cursor().position().stoneAt(x, y);
                drawPiece(gc, x, y, piece);
            }
        }

        drawGraphicalArrows(gc);

        long stop = System.currentTimeMillis();
        log.debug("done in " + (stop-start) + " ms");
    }

    private void drawGraphicalSquares(GraphicsContext gc) {
        GraphicalSquaresAnnotation gsa = model.getModel().cursor().getAnnotation(GraphicalSquaresAnnotation.class);
        if (gsa == null) {
            return;
        }
        for (GraphicalSquaresAnnotation.Square gsq : gsa.getSquares()) {
            int x = Chess.sqiToCol(gsq.getSqi()), y = Chess.sqiToRow(gsq.getSqi());
            Paint p;
            switch (gsq.getColor()) {
                case GREEN:
                    p = Color.rgb(255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_SQUARE_OPACITY);
                    break;
                case YELLOW:
                    p = Color.rgb(GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_SQUARE_OPACITY);
                    break;
                case RED:
                    p = Color.rgb(GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_SQUARE_OPACITY);
                    break;
                default:
                    continue;
            }
            gc.setFill(p);
            Rectangle sq = getSquareRect(x, y);
            gc.fillRect(sq.getX(), sq.getY(), sq.getWidth(), sq.getHeight());
        }
    }

    private void drawGraphicalArrows(GraphicsContext gc) {
        Annotations annotations = model.getModel().cursor().getAnnotations();
        log.info("# annotations: " + annotations.size());
        GraphicalArrowsAnnotation gsa = annotations.getByClass(GraphicalArrowsAnnotation.class);
        if (gsa == null) {
            return;
        }
        for (GraphicalArrowsAnnotation.Arrow ga : gsa.getArrows()) {
            int src = ga.getFromSqi(), dest = ga.getToSqi();
            int x1 = Chess.sqiToCol(src), y1 = Chess.sqiToRow(src);
            int x2 = Chess.sqiToCol(dest), y2 = Chess.sqiToRow(dest);
            Paint p;
            switch (ga.getColor()) {
                case GREEN:
                    p = Color.rgb(255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_ARROW_OPACITY);
                    break;
                case YELLOW:
                    p = Color.rgb(GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_ARROW_OPACITY);
                    break;
                case RED:
                    p = Color.rgb(GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_ARROW_OPACITY);
                    break;
                default:
                    continue;
            }
            gc.setFill(p);
            Point2D p1 = getSquareMidpoint(x1, y1);
            Point2D p2 = getSquareMidpoint(x2, y2);
            drawArrow(gc, p1.getX(), p1.getY(), p2.getX(), p2.getY(), p);
        }
    }

    void drawArrow(GraphicsContext gc, double x1, double y1, double x2, double y2, Paint color) {
        gc.setFill(color);

        double dx = x2 - x1, dy = y2 - y1;
        double angle = Math.atan2(dy, dx);
        double len = Math.sqrt(dx * dx + dy * dy);

        Transform transform = Transform.translate(x1, y1);
        transform = transform.createConcatenation(Transform.rotate(Math.toDegrees(angle), 0, 0));
        gc.setTransform(new Affine(transform));

        double arrowStart = squareSize * 0.05;
        double arrowLength = len - squareSize * 0.2;
        double arrowHeadWidth  = squareSize * 0.22;
        double arrowHeadHeight = squareSize * 0.45;
        double arrowHeadHeight2 = arrowHeadHeight * 0.8;
        double arrowWidth = squareSize * 0.05;
        gc.fillPolygon(
                new double[] {arrowLength, arrowLength - arrowHeadHeight, arrowLength - arrowHeadHeight2, arrowStart, arrowStart, arrowLength - arrowHeadHeight2, arrowLength-arrowHeadHeight, arrowLength},
                new double[] {0, -arrowHeadWidth, -arrowWidth, -arrowWidth, arrowWidth, arrowWidth, arrowHeadWidth, 0},
                8);
        gc.setTransform(new Affine());
    }

    private void drawPiece(GraphicsContext gc, int x, int y, Stone stone) {
        if (stone.isNoStone()) return;

        int sx = 0, sy = stone.toPlayer() == Player.WHITE ? 0 : 1;

        switch (stone.toPiece()) {
            case PAWN:   sx = 5; break;
            case KNIGHT: sx = 1; break;
            case BISHOP: sx = 2; break;
            case ROOK:   sx = 0; break;
            case QUEEN:  sx = 3; break;
            case KING:   sx = 4; break;
        }

        Rectangle sq = getSquareRect(x, y);

        gc.drawImage(chessImages, sx * 132, sy * 132, 132, 132,
                sq.getX(), sq.getY(), sq.getWidth(), sq.getHeight());
    }

    private void drawGameHeader() {
        drawGameHeaderFirstRow();
        drawGameHeaderSecondRow();
    }

    private void drawGameHeaderFirstRow() {
        playerNames.getChildren().clear();
        GameHeaderModel header = model.getModel().header();
        String white = header.getWhite();
        String black = header.getBlack();
        Integer whiteRating = header.getWhiteElo();
        Integer blackRating = header.getBlackElo();
        GameResult result = header.getResult();

        if (white != null && white.length() > 0) {
            Text txtWhite = new Text(white);
            txtWhite.getStyleClass().add("player-name");
            playerNames.getChildren().add(txtWhite);
            if (whiteRating != null && whiteRating > 0) {
                txtWhite = new Text(" " + whiteRating);
                txtWhite.getStyleClass().add("player-rating");
                playerNames.getChildren().add(txtWhite);
            }
        }

        if (white != null && white.length() > 0 && black != null && black.length() > 0) {
            Text txtVs = new Text(" - ");
            txtVs.getStyleClass().add("player-name");
            playerNames.getChildren().add(txtVs);
        }

        if (black != null && black.length() > 0) {
            Text txtBlack = new Text(black);
            txtBlack.getStyleClass().add("player-name");
            playerNames.getChildren().add(txtBlack);
            if (blackRating != null && blackRating > 0) {
                txtBlack = new Text(" " + blackRating);
                txtBlack.getStyleClass().add("player-rating");
                playerNames.getChildren().add(txtBlack);
            }
        }

        if (result != null) {
            Text txtResult = new Text(" " + result.toString());
            txtResult.getStyleClass().add("game-result");
            playerNames.getChildren().add(txtResult);
        }
    }

    private void drawGameHeaderSecondRow() {
        gameDetails.getChildren().clear();
        GameHeaderModel header = model.getModel().header();

        Eco eco = header.getEco();
        String tournament = header.getEvent();
        String annotator = header.getAnnotator();
        Integer round = header.getRound();
        Integer subRound = header.getSubRound();
        if (round == null) round = 0;
        if (subRound == null) subRound = 0;
        Date playedDate = header.getDate();
        String whiteTeam = header.getWhiteTeam();
        String blackTeam = header.getBlackTeam();

        if (eco != null && eco.toString().length() > 0) {
            Text txtECO = new Text(eco.toString() + " ");
            txtECO.getStyleClass().add("eco");
            gameDetails.getChildren().add(txtECO);
        }
        if (tournament != null && tournament.length() > 0) {
            Text txtTournament = new Text(tournament + " ");
            txtTournament.getStyleClass().add("tournament");
            gameDetails.getChildren().add(txtTournament);
        }
        if (whiteTeam != null && whiteTeam.length() > 0 && blackTeam != null && blackTeam.length() > 0) {
            Text txtTeams = new Text(String.format("[%s-%s] ", whiteTeam, blackTeam));
            txtTeams.getStyleClass().add("team");
            gameDetails.getChildren().add(txtTeams);
        }
        if (round > 0 || subRound > 0) {
            String roundString;
            if (round > 0 && subRound > 0) {
                roundString = String.format("(%d.%d)", round, subRound);
            } else if (round > 0) {
                roundString = String.format("(%d)", round);
            } else {
                roundString = String.format("(%d)", subRound);
            }
            Text txtRound = new Text(roundString + " ");
            txtRound.getStyleClass().add("round");
            gameDetails.getChildren().add(txtRound);
        }
        if (playedDate != null) {
            Text txtDate = new Text(playedDate.toString() + " ");
            txtDate.getStyleClass().add("date");
            gameDetails.getChildren().add(txtDate);
        }
        if (annotator != null &&  annotator.length() > 0) {
            Text txtAnnotator = new Text(String.format("[%s]", annotator));
            txtAnnotator.getStyleClass().add("annotator");
            gameDetails.getChildren().add(txtAnnotator);
        }
    }

    private void fitImageViewSize(float width, float height) {
        Platform.runLater(() -> {
            float fitHeight = mediaPlayerComponent.getVideoSourceRatioProperty().get() * width;
            if (fitHeight > height) {
                videoImage.setFitHeight(height);
                double fitWidth = height / mediaPlayerComponent.getVideoSourceRatioProperty().get();
                videoImage.setFitWidth(fitWidth);
                videoImage.setX((width - fitWidth) / 2);
                videoImage.setY(0);
            } else {
                videoImage.setFitWidth(width);
                videoImage.setFitHeight(fitHeight);
                videoImage.setY((height - fitHeight) / 2);
                videoImage.setX(0);
            }
        });
    }

    public void updateVideoPosition(int time) {
        // TODO: This will be a race condition with the timer. It needs to be paused.
        if (time < model.getCurrentTimestamp()) {
            model.jumpTo(time);
        } else {
            model.playTo(time);
        }
        this.currentTime.setText(String.format("%d:%02d", time/1000/60, time/1000%60));
        DirectMediaPlayer mp = mediaPlayerComponent.getMediaPlayer();

        if (!mp.isPlaying()) {
            mp.start();
        }
        mp.setTime(time);

        drawGameHeader();
        movesPane.drawMoves();
        drawBoard();
    }

    public void reloadVideo(String mediaFile) {
//        String mediaFile = "/Users/yarin/chessbasemedia/mediafiles/TEXT/Ari Ziegler - French Defence/2.wmv";
//        String mediaFile = "/Users/yarin/chessbasemedia/mediafiles/TEXT/Garry Kasparov - Queens Gambit/3.wmv";
        //String mediaFile = "/Users/yarin/src/cbhlib/testmediafiles/GA/Viswanathan Anand - My Career - Volume 1/10.wmv";
//        String mediaFile = "/Users/yarin/chessbasemedia/mediafiles/TEXT/Jacob Aagaard - Queen's Indian Defence/Queen's Indian Defence.avi/8.wmv";
//        String mediaFile = "/Users/yarin/chessbasemedia/mediafiles/HEADER/Simon Williams - Most Amazing Moves/Game 15 Spassky-Fischer/Game 15 Spassky-Fischer000.wmv";
//        String mediaFile = "/Users/yarin/chessbasemedia/mediafiles/CBM168/Festival Biel 2015.html/Biel 2015 round 04 Navara-Wojtaszek.wmv";
//        String mediaFile = "/Users/yarin/chessbasemedia/mediafiles/CBM168/168Tactics.html/CBM168Taktikeng2/rn1qr3zp3kp2z2p1pR1Qz4P2pz3P3Pz6P1zP5PKz8 w - - 0 1x0y0v4u0.wmv";
        mediaPlayerComponent.getMediaPlayer().prepareMedia(mediaFile);
        try {
            this.model = ChessBaseMediaLoader.loadMedia(new File(mediaFile));

            int duration = this.model.getLastEventTimestamp();
            this.slider.setMax(duration);
            this.slider.setMajorTickUnit(120*1000);
            this.slider.setShowTickMarks(true);
            this.slider.setShowTickLabels(true);
            this.slider.setLabelFormatter(new StringConverter<Double>() {
                @Override
                public String toString(Double value) {
                    return String.format("%d:%02d", (int) Math.floor(value/1000)/60, (int) Math.floor(value/1000)%60);
                }

                @Override
                public Double fromString(String string) {
                    throw new RuntimeException("Not needed");
                }
            });

            this.slider.valueProperty().addListener((observable, oldValue, newValue) -> {
                log.debug("Value changing: " + this.slider.isValueChanging());
                if (oldValue.equals(newValue)) return;
                updateVideoPosition(newValue.intValue());
            });

            if (this.model.getModel().cursor() == null)
                this.model.getModel().setCursor(this.model.getModel().moves().root());
        } catch (IOException | ChessBaseMediaException e) {
            throw new RuntimeException("Failed to load the media", e);
        }

        // TODO: Use MediaPlayerEventListener instead of the timer below
        //mediaPlayerComponent.getMediaPlayer().addMediaPlayerEventListener();

        this.videoTimer = new Timer("videoTimer");
        this.videoTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                int curTime = (int) mediaPlayerComponent.getMediaPlayer().getTime();
                log.info("Video at " + curTime);

                Platform.runLater(() -> {
                    // TODO: This is duplicated code
                    Controller.this.currentTime.setText(String.format("%d:%02d", curTime/1000/60, curTime/1000%60));
//                    Controller.this.slider.setValue(curTime);
                });

                // TODO: Ugly, fix
                // TODO: Also fix the fact that the user may have changed the model (selected move in particular) since last event was applied
                int actionsApplied = model.playTo(curTime);
                if (actionsApplied > 0) {
                    Platform.runLater(() -> {
                        drawBoard();
                        movesPane.drawMoves();
                        drawGameHeader();
                        log.info("selecting position " + model.getModel().cursor().lastMove() + " is valid " + model.getModel().cursor().isValid());
                        movesPane.selectPosition(model.getModel().cursor());
                    });
                }
            }
        }, 500, 500);

        updateVideoPosition(0);
    }

    public void reloadManualGame() {
        NavigableGameModel start = new NavigableGameModel();
        start.addMove(new Move(start.cursor().position(), E2, E4));
        start.addMove(new Move(start.cursor().position(), E7, E5));
        start.addAnnotation(new GraphicalSquaresAnnotation(Arrays.asList(
                new GraphicalSquaresAnnotation.Square(
                        GraphicalAnnotationColor.GREEN, 1))
        ));
        start.addAnnotation(new GraphicalArrowsAnnotation(Arrays.asList(
                new GraphicalArrowsAnnotation.Arrow(
                        GraphicalAnnotationColor.GREEN, 1, 2))
        ));
        this.model = new NavigableGameModelTimeline();
        this.model.addEvent(0, new ReplaceAllEvent(start));
        try {
            this.model.applyNextEvent();
        } catch (GameEventException e) {
            e.printStackTrace();
        }
    }

    public void openMediaFile(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Chessbase Media File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Chessbase Meda file", "*.wmv"));
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            log.info("Selected file " + file.getAbsoluteFile());
            reloadVideo(file.getAbsoluteFile().toString());
        }
    }
/*
    public void reloadGame(ActionEvent actionEvent) {
        //        String cbhFile = "/Users/yarin/Dropbox/ChessBase/My Games/My White Openings.cbh";
//        String cbhFile = "/Users/yarin/Dropbox/ChessBase/My Games/jimmy.cbh";
        String cbhFile = "/Users/yarin/src/cbhlib/src/test/java/yarin/cbhlib/databases/cbhlib_test.cbh";
//        String cbhFile = "/Users/yarin/src/opencbmplayer/src/main/resources/cbmplayertest.cbh";


        try {
            Database db = Database.open(cbhFile);
            GameHeader header = db.getGameHeader(9);
            this.gameHeader = header.toGameMetaData();
            this.game = header.getGame();
            this.gameCursor = this.game;
        } catch (IOException | CBHException e) {
            Text txtError = new Text("Failed to get game details");
            txtError.getStyleClass().add("load-error");
            gameDetails.getChildren().add(txtError);
            throw new RuntimeException("Failed to load the game", e);
        }

        drawGameHeader();
        drawMoves();
        drawBoard();
    }
    */
}
