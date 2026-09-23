package se.yarin.morphy.tools;

import se.yarin.cbhlib.DatabaseCbh;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;

import java.io.File;
import java.io.IOException;

public class ExploreCbbFile {
  public static void main(String[] args) throws IOException, ChessBaseException {
    DatabaseCbh db = DatabaseCbh.open(new File("/Users/yarin/chess/Mega2021/Mega Database 2021.cbh"));
    Game game = db.getGame(2936406);

    File targetFile = new File("/Users/yarin/Dropbox/ChessBase/test/cbbtest2/cbbtest2.cbh");
    DatabaseCbh.delete(targetFile);
    DatabaseCbh targetDb = DatabaseCbh.create(targetFile);
    GameMovesModel.Node currentSourceNode = game.getModel().moves().root();

    GameMovesModel targetModel = new GameMovesModel();
    GameMovesModel.Node currentTargetNode = targetModel.root();
    while (currentSourceNode.hasMoves()) {
      Move nextMove = currentSourceNode.mainMove();

      currentTargetNode = currentTargetNode.addMove(nextMove);
      currentSourceNode = currentSourceNode.mainNode();

      GameHeaderModel headerModel = new GameHeaderModel();
      headerModel.setEvent(currentTargetNode.ply() + " " + nextMove);
      targetDb.addGame(new GameModel(headerModel, targetModel));
    }

    db.close();
    targetDb.close();
  }
}
