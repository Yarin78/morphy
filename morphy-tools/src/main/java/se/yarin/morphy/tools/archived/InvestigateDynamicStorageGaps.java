package se.yarin.morphy.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.GameHeaderBase;
import se.yarin.cbhlib.storage.FileBlobStorage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

public class InvestigateDynamicStorageGaps {
  private static final Logger log = LogManager.getLogger();

  public static void main(String[] args) throws IOException {
    Files.walk(Paths.get("testbases/tmp/Move data fragmentation/testing.cbh"))
        .forEach(
            filePath -> {
              //        Files.walk(Paths.get("testbases/CHESS LITERATURE 3/Сборник книг в формате
              // ChessBase/Тимощенко_Дебютный репартуар/Тимощенко_Дебютный
              // репартуар.cbh")).forEach(filePath -> {
              if (Files.isRegularFile(filePath) && filePath.toString().endsWith(".cbh")) {
                log.info("Reading " + filePath);
                Database base = null;
                try {
                  base = Database.open(filePath.toFile());

                  checkMoves(base);
                  System.out.println();
                  checkAnnos(base);

                } catch (NoSuchFileException e) {
                  log.error("File missing " + filePath);
                } catch (IOException e) {
                  log.error("IO error reading " + filePath, e);
                } finally {
                  if (base != null) {
                    try {
                      base.close();
                    } catch (IOException e) {
                      e.printStackTrace();
                    }
                  }
                }
              }
            });
  }

  private static void checkMoves(Database base) throws IOException {
    GameHeaderBase headerBase = base.getHeaderBase();
    FileBlobStorage movesStorage = base.getMovesBase().getStorage();

    int lastMoves = movesStorage.getHeaderSize();
    for (int i = 1; i <= headerBase.size(); i++) {
      try {
        GameHeader gameHeader = headerBase.getGameHeader(i);
        if (gameHeader.isGuidingText()) continue;
        int movesOffset = gameHeader.getMovesOffset();
        //                            int movesOffset = gameHeader.getAnnotationOffset();

        ByteBuffer movesRaw = movesStorage.readBlob(movesOffset);
        int movesLen = base.getMovesBase().getBlobSize(movesRaw);

        if (lastMoves > movesOffset) {
          System.out.println("OVERLAP");
        }
        if (lastMoves < movesOffset) {
          System.out.println(
              String.format(
                  "Gap       [%4d,%4d) (length = %4d)",
                  lastMoves, movesOffset, movesOffset - lastMoves));
        }

        System.out.println(
            String.format(
                "Game #%2d: [%4d,%4d) (length = %4d)",
                i, movesOffset, movesOffset + movesLen, movesLen));
        lastMoves = movesOffset + movesLen;
      } catch (RuntimeException e) {
        log.error("Error reading game " + i, e);
        break;
      }
    }
    if (lastMoves < movesStorage.getSize()) {
      System.out.println(
          String.format(
              "Gap       [%4d,%4d) (length = %4d)",
              lastMoves, movesStorage.getSize(), movesStorage.getSize() - lastMoves));
    }
  }

  private static void checkAnnos(Database base) throws IOException {

    GameHeaderBase headerBase = base.getHeaderBase();
    FileBlobStorage annoStorage = base.getAnnotationBase().getStorage();
    int lastAnnos = annoStorage.getHeaderSize();
    for (int i = 1; i <= headerBase.size(); i++) {
      try {
        GameHeader gameHeader = headerBase.getGameHeader(i);
        if (gameHeader.isGuidingText()) continue;
        int annosOffset = gameHeader.getAnnotationOffset();
        if (annosOffset == 0) continue;

        ByteBuffer annosRaw = annoStorage.readBlob(annosOffset);
        int movesLen = base.getAnnotationBase().getBlobSize(annosRaw);
        //                System.out.println(CBUtil.toHexString(annosRaw));

        if (lastAnnos > annosOffset) {
          System.out.println("OVERLAP");
        }
        if (lastAnnos < annosOffset) {
          System.out.println(
              String.format(
                  "Gap       [%4d,%4d) (length = %4d)",
                  lastAnnos, annosOffset, annosOffset - lastAnnos));
        }

        System.out.println(
            String.format(
                "Game #%2d: [%4d,%4d) (length = %4d)",
                i, annosOffset, annosOffset + movesLen, movesLen));
        lastAnnos = annosOffset + movesLen;
      } catch (RuntimeException e) {
        log.error("Error reading game " + i, e);
        break;
      }
    }
    if (lastAnnos < annoStorage.getSize()) {
      System.out.println(
          String.format(
              "Gap       [%4d,%4d) (length = %4d)",
              lastAnnos, annoStorage.getSize(), annoStorage.getSize() - lastAnnos));
    }
  }
}
