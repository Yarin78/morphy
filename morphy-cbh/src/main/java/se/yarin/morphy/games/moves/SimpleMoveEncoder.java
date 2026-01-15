package se.yarin.morphy.games.moves;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;
import se.yarin.morphy.exceptions.MorphyMoveDecodingException;
import se.yarin.morphy.util.KeyProvider;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Stack;

/**
 * This is a simple move encoder used to encode moves of a ChessBase game. All moves are encoded as
 * two bytes. Only a handful of games in Mega Database 2016 are encoded using this encoder.
 */
public class SimpleMoveEncoder implements MoveEncoder {
  private static final Logger log = LoggerFactory.getLogger(SimpleMoveEncoder.class);

  private final boolean modifierFlag;
  private final boolean inverseSquareOrder;
  private final short[] encryptionMap;
  private final short[] decryptionMap;

  private int modifier;

  public SimpleMoveEncoder(int keyNo, boolean modifierFlag, boolean inverseSquareOrder) {
    this.encryptionMap = KeyProvider.getMoveSerializationKey(keyNo);
    this.decryptionMap = KeyProvider.getMoveSerializationKey(keyNo + 1);
    this.inverseSquareOrder = inverseSquareOrder;
    this.modifierFlag = modifierFlag;
  }

  @Override
  public synchronized void encode(ByteBuffer buf, GameMovesModel movesModel) {
    this.modifier = 0;
    encode(buf, movesModel.root());
  }

  private void encode(ByteBuffer buf, GameMovesModel.Node current) {
    List<GameMovesModel.Node> children = current.children();
    for (int i = 0; i < children.size(); i++) {
      GameMovesModel.Node child = children.get(i);
      Move move = child.lastMove();
      int value;
      if (move.isNullMove()) {
        value = 0;
      } else if (!inverseSquareOrder) {
        value = move.fromSqi() + move.toSqi() * 64;
      } else {
        value = move.toSqi() + move.fromSqi() * 64;
      }
      switch (move.promotionStone().toPiece()) {
        case QUEEN -> value += 0;
        case ROOK -> value += 1 << 12;
        case BISHOP -> value += 2 << 12;
        case KNIGHT -> value += 3 << 12;
      }
      if (i + 1 < children.size()) {
        value += 1 << 15;
      }
      if (!child.hasMoves()) {
        value += 1 << 14;
      }

      if (log.isDebugEnabled()) {
        log.debug(String.format("Outputting %s with flags %d", move.toString(), value >> 14));
      }

      put(buf, value);
      modifier++;
      encode(buf, child);
    }
  }

  @Override
  public synchronized void decode(
      ByteBuffer buf, GameMovesModel movesModel, boolean checkLegalMoves)
      throws MorphyMoveDecodingException {
    if (!buf.hasRemaining()) {
      // An empty game can't have a proper end marker, so it needs a special check
      return;
    }

    this.modifier = 0;

    GameMovesModel.Node current = movesModel.root();
    Stack<GameMovesModel.Node> stack = new Stack<>();
    stack.add(null);

    while (current != null) {
      int value = get(buf);

      if ((value & (1 << 15)) > 0) {
        stack.push(current);
      }
      int fromSqi, toSqi;
      if (!inverseSquareOrder) {
        fromSqi = value % 64;
        toSqi = (value / 64) % 64;
      } else {
        toSqi = value % 64;
        fromSqi = (value / 64) % 64;
      }

      Move move;
      if (fromSqi == 0 && toSqi == 0) {
        move = Move.nullMove(current.position());
      } else {
        move = new Move(current.position(), fromSqi, toSqi);
      }

      int toRow = Chess.sqiToRow(toSqi);
      if ((toRow == 0 || toRow == 7)
          && current.position().stoneAt(fromSqi).toPiece() == Piece.PAWN) {
        Piece promotionPiece =
            switch ((value / 4096) % 4) {
              case 0 -> Piece.QUEEN;
              case 1 -> Piece.ROOK;
              case 2 -> Piece.BISHOP;
              case 3 -> Piece.KNIGHT;
              default -> Piece.NO_PIECE;
            };
        Stone promotionStone = promotionPiece.toStone(current.position().playerToMove());
        move = new Move(current.position(), fromSqi, toSqi, promotionStone);
      }

      if (log.isDebugEnabled()) {
        log.debug(String.format("Decoded move %s with flags %d", move, value >> 14));
      }
      try {
        current = checkLegalMoves ? current.addMoveUnsafe(move) : current.addMove(move);
      } catch (IllegalMoveException e) {
        throw new MorphyMoveDecodingException("Decoded illegal move: " + move);
      }
      if ((value & (1 << 14)) > 0) {
        current = stack.pop();
      }

      modifier++;
    }
  }

  public void put(ByteBuffer buf, int value) {
    int v1 = value / 256, v2 = value % 256;
    if (!modifierFlag) {
      v1 = (v1 + modifier) % 256;
      v2 = (v2 + modifier) % 256;
    }
    int k1 = encryptionMap[v1];
    int k2 = encryptionMap[v2];
    if (modifierFlag) {
      k1 = (k1 + modifier) % 256;
      k2 = (k2 + modifier) % 256;
    }
    ByteBufferUtil.putByte(buf, k1);
    ByteBufferUtil.putByte(buf, k2);
  }

  public int get(ByteBuffer buf) {
    int k1 = ByteBufferUtil.getUnsignedByte(buf);
    int k2 = ByteBufferUtil.getUnsignedByte(buf);
    if (modifierFlag) {
      k1 = ((k1 - modifier) % 256 + 256) % 256;
      k2 = ((k2 - modifier) % 256 + 256) % 256;
    }
    int v1 = decryptionMap[k1];
    int v2 = decryptionMap[k2];
    if (!modifierFlag) {
      v1 = ((v1 - modifier) % 256 + 256) % 256;
      v2 = ((v2 - modifier) % 256 + 256) % 256;
    }
    return v1 * 256 + v2;
  }
}
