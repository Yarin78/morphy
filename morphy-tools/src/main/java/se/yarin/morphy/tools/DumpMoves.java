package se.yarin.morphy.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import se.yarin.chess.Castles;
import se.yarin.chess.Chess;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.chess.Stone;
import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;

/**
 * Writes the move tree of every game in a v1 database, one game per line, as the reference for
 * checking a decoder of the v2 format (see cbreveng/MOVES.md) against a database that exists in
 * both formats.
 *
 * <p>Each line is tab separated: the game id, then the start position as FEN followed by the ply
 * (or "-" for the ordinary start position), then the moves in UCI notation with variations in
 * parentheses after the move they are an alternative to. Castling is written as the king's move,
 * a null move as 0000. Guiding texts are written as "TEXT", and games that can't be decoded as
 * "ERROR" and the reason.
 *
 * <p>The output goes to a file rather than stdout, where the library's logging would get mixed in.
 *
 * <p>Usage: DumpMoves &lt;database.cbh&gt; &lt;output file&gt;
 */
public class DumpMoves {
  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.err.println("Usage: DumpMoves <database.cbh> <output file>");
      System.exit(1);
    }
    try (DatabaseCbh db = DatabaseCbh.open(new File(args[0]), DatabaseMode.READ_ONLY);
        DatabaseReadTransaction txn = new DatabaseReadTransaction(db);
        PrintWriter out = new PrintWriter(args[1])) {
      for (int id = 1; id <= db.count(); id++) {
        Game game = txn.getGame(id);
        if (game.guidingText()) {
          out.println(id + "\tTEXT");
          continue;
        }
        try {
          GameMovesModel moves = game.getModel().moves();
          StringBuilder sb = new StringBuilder();
          writeLine(moves.root(), sb);
          String start =
              moves.isSetupPosition()
                  ? toFen(moves.root().position()) + " " + moves.root().ply()
                  : "-";
          out.println(id + "\t" + start + "\t" + sb.toString().trim());
        } catch (Exception e) {
          out.println(id + "\tERROR " + e);
        }
      }
    }
  }

  /** Writes the line starting at node, with each alternative in parentheses after the main move. */
  private static void writeLine(GameMovesModel.Node node, StringBuilder sb) {
    while (node.hasMoves()) {
      List<GameMovesModel.Node> children = node.children();
      sb.append(' ').append(toUci(children.get(0).lastMove()));
      for (int i = 1; i < children.size(); i++) {
        sb.append(" ( ").append(toUci(children.get(i).lastMove()));
        writeLine(children.get(i), sb);
        sb.append(" )");
      }
      node = children.get(0);
    }
  }

  private static String toUci(Move move) {
    if (move.isNullMove()) {
      return "0000";
    }
    String uci = Chess.sqiToStr(move.fromSqi()) + Chess.sqiToStr(move.toSqi());
    if (!move.promotionStone().isNoStone()) {
      uci += Character.toLowerCase(move.promotionStone().toChar());
    }
    return uci;
  }

  /** The position as the first four fields of a FEN string. */
  private static String toFen(Position position) {
    StringBuilder sb = new StringBuilder();
    for (int rank = 7; rank >= 0; rank--) {
      int empty = 0;
      for (int file = 0; file < 8; file++) {
        Stone stone = position.stoneAt(file * 8 + rank);
        if (stone.isNoStone()) {
          empty++;
          continue;
        }
        if (empty > 0) {
          sb.append(empty);
          empty = 0;
        }
        sb.append(stone.toChar());
      }
      if (empty > 0) {
        sb.append(empty);
      }
      if (rank > 0) {
        sb.append('/');
      }
    }
    boolean whiteToMove = position.playerToMove() == Player.WHITE;
    sb.append(whiteToMove ? " w " : " b ");
    String castles = "";
    if (position.isCastles(Castles.WHITE_SHORT_CASTLE)) castles += "K";
    if (position.isCastles(Castles.WHITE_LONG_CASTLE)) castles += "Q";
    if (position.isCastles(Castles.BLACK_SHORT_CASTLE)) castles += "k";
    if (position.isCastles(Castles.BLACK_LONG_CASTLE)) castles += "q";
    sb.append(castles.isEmpty() ? "-" : castles);
    int epFile = position.getEnPassantCol();
    sb.append(epFile < 0 ? " -" : " " + (char) ('a' + epFile) + (whiteToMove ? "6" : "3"));
    return sb.toString();
  }
}
