package se.yarin.morphy.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.yarin.cbhlib.games.EndgameType;
import se.yarin.cbhlib.games.ExtendedGameHeaderBase;
import se.yarin.cbhlib.games.GameHeaderBase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.HashSet;

public class InvestigateSecondaryHeader {
  private static final Logger log = LogManager.getLogger();

  public static void main(String[] args) throws IOException {
    //        Files.walk(Paths.get("testbases/CHESS LITERATURE 3")).forEach(filePath -> {
    Files.walk(
            Paths.get(
                "testbases/CHESS LITERATURE 3/DYNAMICS AND TACTICS/Enciclopedia de Combinaciones Inforchess/Enciclopedia de Combinaciones Inforchess/Intermedia.cbj"))
        .forEach(
            filePath -> {
              //        Files.walk(Paths.get("testmediafiles")).forEach(filePath -> {
              //        Files.walk(Paths.get("testbases/Mega Database 2016")).forEach(filePath -> {
              //        Files.walk(Paths.get("testbases/Jimmys bases/jimmy.cbj")).forEach(filePath
              // -> {
              //
              // Files.walk(Paths.get("testbases/tmp/cbjtest/timestamp.cbj")).forEach(filePath -> {
              if (Files.isRegularFile(filePath) && filePath.toString().endsWith(".cbj")) {
                try {
                  HashSet<EndgameType> seen = new HashSet<>();
                  ExtendedGameHeaderBase xbase = ExtendedGameHeaderBase.open(filePath.toFile());
                  GameHeaderBase base =
                      GameHeaderBase.open(new File(filePath.toString().replace(".cbj", ".cbh")));
                  log.info("Reading " + filePath.toFile() + " " + xbase.version());
                  if (base.size() != xbase.size() && xbase.size() != 0) {
                    log.info("Size mismatch: " + base.size() + " != " + xbase.size());
                  }

                  /*
                                      for (int i = 1; i <= base.size(); i++) {
                                          GameHeader gh = base.getGameHeader(i);
                                          log.info(String.format("Game #%d: moves = %d, annotations = %d", i, gh.getMovesOffset(), gh.getAnnotationOffset()));
                                      }
                                      for (int i = 1; i <= xbase.size(); i++) {
                                          ExtendedGameHeader gh = xbase.getExtendedGameHeader(i);
                                          log.info(String.format("Game #%d: moves = %d, annotations = %d", i, gh.getMovesOffset(), gh.getAnnotationOffset()));
                                      }
                  */
                  /*
                  //                    log.info(xbase.size() + " " + base.getNextGameId());
                                      for (int gameId = 1; gameId < base.getNextGameId(); gameId++) {
                  //                    for (int gameId = 1; gameId <= 1000; gameId++) {
                  //                        log.info("Game " + gameId);
                                          ExtendedGameHeader xheader = xbase.getExtendedGameHeader(gameId);
                                          if (xheader == null) continue;
                  //                        if (xheader.getGameVersion() < 1 || xheader.getGameVersion() > 1000) continue;
                  //                        if (xheader.getCreationTimestamp() == 0) continue;

                                          EndgameInfo e = xheader.getEndgameInfo();
                                          if (e != null) {
                                              EndgameType type = e.getLongestType();
                                              if (!seen.contains(type)) {
                                                  seen.add(type);
                                                  log.info(String.format("Game #%d: %02X %s", gameId,
                                                          CBUtil.encodeEndgameType(type),
                                                          type.getDescription()));
                                              }
                                          }

                                      }
                                      */
                  xbase.close();
                  base.close();
                } catch (NoSuchFileException ignored) {

                } catch (IOException e) {
                  e.printStackTrace();
                }
              }
            });
  }
}
