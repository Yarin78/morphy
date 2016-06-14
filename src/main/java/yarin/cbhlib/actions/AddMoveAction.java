package yarin.cbhlib.actions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yarin.cbhlib.exceptions.CBMException;
import yarin.chess.*;

public class AddMoveAction extends RecordedAction {
    private static final Logger log = LoggerFactory.getLogger(AddMoveAction.class);

    private final int fromSquare, toSquare;
    private final Piece.PieceType promotionPiece;
    private final boolean appendMove, overwriteMove, insertMove, newMainline;
    private final int lineNo;

    public AddMoveAction(int actionFlags, int actionFlags2, int fromSquare, int toSquare, int moveFlags)
            throws CBMException {
        this.fromSquare = fromSquare;
        this.toSquare = toSquare;

        // actionFlags:
        // 0000  Move becomes variation 0
        // 0001  Move becomes variation 1
        // 0002  Move becomes variation 2
        // 0003  Move becomes variation 3
        // 0040  Add move in current variation (only seems to occur if there are no other moves at this point)
        // 0080  Overwrite
        // 0400  Insert in current variation (change move but keep remaining moves if possible)
        //       This occurs 17:20 in Jacob Aagaard - The Nimzoindian Defence - The easy way (only audio)/The Nimzoindian Defence - The easy way.html/6.wmv
        // 0800  New Main Line, the old main line becomes variation 0
        // 0801  New Main Line, the old main line becomes variation 1
        // 0802  New Main Line, the old main line becomes variation 2
        // 0803  New Main Line, the old main line becomes variation 3
        this.lineNo = actionFlags & 15;
        this.appendMove = (actionFlags & 64) == 64;
        this.insertMove = (actionFlags & 1024) == 1024;
        this.overwriteMove = (actionFlags & 128) == 128;
        this.newMainline = (actionFlags & 2048) == 2048;
        if ((actionFlags & ~0x0CCF) != 0)
            throw new RuntimeException(String.format("Unknown AddMove action flags: %04x", actionFlags));
        if ((appendMove || overwriteMove || insertMove) && lineNo > 0) {
            // This combination doesn't make sense (maybe with appendMove?) and shouldn't exist. Probably.
            throw new RuntimeException(String.format("Unknown AddMove action flags: %04x", actionFlags));
        }
        if (actionFlags2 != 0) {
            throw new RuntimeException(String.format("Unknown AddMove action flags 2: %04x", actionFlags2));
        }

        // moveFlags:
        // 0000  default
        // 0001  white queen side castle
        // 0002  white king side castle
        // 0004  black queen side castle
        // 0008  black king side castle
        // 0010  en-passant
        // 0020  pawn moves two square (enables en-passant)
        // 0040  promote
        // 0080  is capture
        // 0240  promote to queen
        // 0340  promote to knight
        // 02c0  capture and promote to queen

        if ((moveFlags & 64) == 64) {
            switch (moveFlags & 0x0700) {
                case 0x0200 :
                    promotionPiece = Piece.PieceType.QUEEN;
                    break;
                case 0x0300 :
                    promotionPiece = Piece.PieceType.KNIGHT;
                    break;
                default:
                    throw new RuntimeException("Unknown promotion piece: " + moveFlags);
            }
        } else if ((moveFlags & 0xFF00) > 0) {
            throw new RuntimeException("Unknown move flags: " + moveFlags);
        } else {
            promotionPiece = Piece.PieceType.QUEEN;
        }

        if (fromSquare < 0 || fromSquare >= 64 || toSquare < 0 || toSquare >= 64)
            throw new RuntimeException("Illegal move: " + fromSquare + " " + toSquare);
    }

    @Override
    public void apply(GameModel currentModel) throws ApplyActionException {
        GamePosition selectedMove = currentModel.getSelectedMove();
//        log.info("Selected move is " + selectedMove.getPosition().toString() + " " + selectedMove.getMoveNumber() + " " + selectedMove.getPlayerToMove());
//        log.info(String.format("%d %d %d %d %d %d", fromSquare, toSquare, appendMove?1:0, overwriteMove?1:0, insertMove?1:0, lineNo));
        try {
            Move move;
            if (fromSquare == 0 && toSquare == 0) {
                move = new NullMove();
            } else {
                move = new Move(selectedMove.getPosition(), fromSquare / 8, fromSquare % 8, toSquare / 8, toSquare % 8, promotionPiece);
            }

            if (appendMove) {
                // This should only be allowed when there are no other moves at this position
                if (selectedMove.getForwardPositions().size() > 0) {
                    throw new ApplyActionException(this, "Invalid AddMove flag, there were moves at the current position which there shouldn't have been.");
                }
                GamePosition position = selectedMove.addMove(move);
                currentModel.setSelectedMove(position);
            } else if (insertMove) {
                // TODO: This should replace the existing move but keep the line if possible
                // (corresponds to the "Insert move" option in ChessBase)
                // This occurs 17:20 in Jacob Aagaard - The Nimzoindian Defence - The easy way (only audio)/The Nimzoindian Defence - The easy way.html/6.wmv
                throw new ApplyActionException(this, "Insert move is not yet supported.");
            } else if (overwriteMove) {
                // Delete all moves at this position and add a new one
                GamePosition position = selectedMove.replaceMove(move);
                currentModel.setSelectedMove(position);
            } else if (newMainline) {
                // TODO: Should be a suitable primitive for this
                GamePosition position = selectedMove.addMove(move);
                currentModel.setSelectedMove(position);
                // TODO: This is probably not correct. The old mainline should become the last variation
                // (which seems to be the same as lineNo)
                position.promoteVariation();
            } else {
                // Add the move and create a new variation
                GamePosition position = selectedMove.addMove(move);
                currentModel.setSelectedMove(position);
            }
        } catch (IllegalArgumentException e) {
            // This can happen due to errors in the ChessBase media recording
            // Example: Alexei Shirov - My Best Games in the Najdorf (only audio)/My best games in the Sicilian Najdorf.html/10.wmv
            throw new ApplyActionException(this, "Illegal move: " + toString());
        }
    }

    @Override
    public String toString() {
        return String.format("AddMoveAction{%c%c%c%c %s%s%s%s %d}",
                (char)('a'+fromSquare/8),
                (char)('1'+fromSquare%8),
                (char)('a'+toSquare/8),
                (char)('1'+toSquare%8),
                appendMove ? "ADD " : "",
                overwriteMove ? "OVERWRITE " : "",
                insertMove ? "INSERT " : "",
                newMainline ? "NEWMAINLINE " : "",
                lineNo);
    }
}
