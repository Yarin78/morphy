package yarin.cbhlib.annotations;

import yarin.cbhlib.ByteBufferUtil;
import yarin.cbhlib.LineEvaluation;
import yarin.cbhlib.MoveComment;
import yarin.cbhlib.MovePrefix;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class SymbolAnnotation extends Annotation {
    private MoveComment moveComment;
    private LineEvaluation positionEval;
    private MovePrefix movePrefix;

    public MoveComment getMoveComment() {
        return moveComment;
    }

    public LineEvaluation getPositionEval() {
        return positionEval;
    }

    public MovePrefix getMovePrefix() {
        return movePrefix;
    }

    public SymbolAnnotation(ByteBuffer data, int symbolBytes) {
        int moveCommentVal = symbolBytes >= 1 ? ByteBufferUtil.getUnsignedByte(data) : 0;
        int positionEvalVal = symbolBytes >= 2 ? ByteBufferUtil.getUnsignedByte(data) : 0;
        int movePrefixVal = symbolBytes >= 3 ? ByteBufferUtil.getUnsignedByte(data) : 0;

        moveComment = MoveComment.decode(moveCommentVal);
        positionEval = LineEvaluation.decode(positionEvalVal);
        movePrefix = MovePrefix.decode(movePrefixVal);
    }

    public String getPostText() {
        if (moveComment == MoveComment.Nothing && positionEval == LineEvaluation.NoEvaluation)
            return null;
        String s = "";
        switch (moveComment) {
            case GoodMove:
                s += "!";
                break;
            case BadMove:
                s += "?";
                break;
            case ExcellentMove:
                s += "!!";
                break;
            case Blunder:
                s += "??";
                break;
            case InterestingMove:
                s += "!?";
                break;
            case DubiousMove:
                s += "?!";
                break;
            case ZugZwang:
            case ZugZwang2:
                s += " ZugZwang"; // TODO
                break;
            case OnlyMove:
                s += " OnlyMove"; // TODO
                break;
        }
        switch (positionEval) {
            case WhiteHasDecisiveAdvantage:
                s += " +-";
                break;
            case WhiteHasClearAdvantage:
                s += " +/-";
                break;
            case WhiteHasSlightAdvantage:
                s += " +/=";
                break;
            case Equal:
                s += " =";
                break;
            case Unclear:
                s += " unclear"; // TODO
                break;
            case BlackHasSlightAdvantage:
                s += " =/+";
                break;
            case BlackHasClearAdvantage:
                s += " -/+";
                break;
            case BlackHasDecisiveAdvantage:
                s += " -+";
                break;
            case TheoreticalNovelty:
                s += "N";
                break;
            case WithCompensation:
                s += " comp";
                break;
            case WithCounterplay:
                s += " counter";
                break;
            case WithInitiative:
                s += " initiative";
                break;
            case WithAttack:
                s += " attack";
                break;
            case DevelopmentAdvantage:
                s += " dev";
                break;
            case ZeitNot:
                s += " zeitnot";
                break;
        }
        return s;

    }

    public String getPreText() {
        switch (movePrefix) {
            case EditorialAnnotation:
                return "editorial ";
            case BetterIs:
                return "better is ";
            case WorseIs:
                return "worse is ";
            case EquivalentIs:
                return "equivalent is ";
            case WithTheIdea:
                return "with the idea ";
            case DirectedAgainst:
                return "directed against ";
        }
        return null;
    }

}
