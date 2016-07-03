package yarin.cbhlib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yarin.cbhlib.exceptions.CBHException;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.Board;
import yarin.chess.GameMetaData;
import yarin.chess.Piece;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.LinkedHashMap;

/**
 * Represents a game header record in the CBH file
 */
public class GameHeader extends DataRecord {

    private static final Logger log = LoggerFactory.getLogger(GameHeader.class);

    private boolean deleted; // If true, game has been marked as deleted but no physical deletion has been done yet
    private boolean guidingText; // If true, this is a text document and not a chess game
    private Date playedDate;
    private int round; // 0 = not set
    private int subRound; // 0 = not set
    private int whiteElo; // 0 = not set
    private int blackElo; // 0 = not set
    private Eco eco;
    private int whitePlayerId;
    private int blackPlayerId;
    private int tournamentId;
    private int annotatorId;
    private int sourceId;
    private int gameDataPosition; // Position in the .cbg file where the actual moves are stored
    private int annotationDataPosition; // Position in the .cba file where the actual annotation start (0 = no yarin.cbhlib.annotations)
    private GameResult result;
    private LineEvaluation lineEvaluation;
    private Medals medals;
    private boolean setupPosition; // If true, the game starts from a different position than the regular start position.
    private int hasVariants; // 0 = no, 1 = <= 50, 2 = <= 300, 3 = <= 1000, 4 = > 1000
    private int hasCommentaries; // 0 = no, 1 = few, 2 = many
    private int hasSymbols; // 0 = no, 1 = few, 2 = many
    private boolean containsCriticalPosition;
    private boolean containsCorrespondenceHeader;
    private boolean containsEmbeddedAudio;
    private boolean containsEmbeddedPicture;
    private boolean containsEmbeddedVideo;
    private boolean containsGameQuotation;
    private boolean containsPathStructure;
    private boolean containsPiecePath;
    private boolean containsTrainingAnnotation;
    private boolean containsTimeLeft;
    private int annotatedWithColoredSqures; // 0 = no, 1 = < 10 positions, 2 >= 10 positions
    private int annotatedWithArrows; // 0 = no, 1 = < 10 positions, 2 >= 10 positions

    private AnnotatedGame game;
    private GameHeaderExtra extraHeader;

    private int noMoves; // -1 = More than 255 moves. Count the exact number upon demand.

    LinkedHashMap<Language, String> title = new LinkedHashMap<>(); // For guiding texts

    private ByteBuffer rawData;
    private ByteBuffer moveData;
    private ByteBuffer annotationData;

    public int getGameDataPosition() { return gameDataPosition; }
    
    public boolean isDeleted() {
        return deleted;
    }

    public boolean isGuidingText() {
        return guidingText;
    }

    public int getWhiteElo() {
        return whiteElo;
    }

    public int getBlackElo() {
        return blackElo;
    }

    public int getRound() {
        return round;
    }

    public int getSubRound() {
        return subRound;
    }

    public int getNoMoves() {
        if (noMoves == -1) {
            // TODO: Count moves
        }
        return noMoves;
    }

    public String getWhiteEloString() {
        return isGuidingText() || getWhiteElo() == 0 ? "" : Integer.toString(getWhiteElo());
    }

    public String getBlackEloString() {
        return isGuidingText() || getBlackElo() == 0 ? "" : Integer.toString(getBlackElo());
    }

    public String getNoMovesString() {
        return isGuidingText() || getNoMoves() == 0 ? "" : Integer.toString(getNoMoves());
    }

    public Player getWhitePlayer() throws IOException {
        return isGuidingText() ? null : getOwnerBase().getPlayer(whitePlayerId);
    }

    public Player getBlackPlayer() throws IOException {
        return isGuidingText() ? null : getOwnerBase().getPlayer(blackPlayerId);
    }

    public String getWhitePlayerString() throws IOException {
        if (!isGuidingText())
            return getWhitePlayer().toString();
        if (title.size() == 0) return "";
        return title.values().iterator().next();
    }

    public String getBlackPlayerString() throws IOException {
        if (!isGuidingText())
            return getBlackPlayer().toString();
        return "";
    }

    public String getWhiteTeamString() throws IOException, CBHException {
        Team team = getWhiteTeam();
        return team == null ? "" : team.getTitle();
    }

    public Team getWhiteTeam() throws IOException, CBHException {
        if (isGuidingText()) return null;
        int teamId = getExtraHeader().getWhitePlayerTeamId();
        if (teamId < 0) return null;
        return getOwnerBase().getTeam(teamId);
    }

    public String getBlackTeamString() throws IOException, CBHException {
        Team team = getBlackTeam();
        return team == null ? "" : team.getTitle();
    }

    public Team getBlackTeam() throws IOException, CBHException {
        if (isGuidingText()) return null;
        int teamId = getExtraHeader().getBlackPlayerTeamId();
        if (teamId < 0) return null;
        return getOwnerBase().getTeam(teamId);
    }

    public RatingDetails getWhiteRatingDetails() throws IOException, CBHException {
        if (isGuidingText()) return null;
        return getExtraHeader().getWhiteRatingDetails();
    }

    public RatingDetails getBlackRatingDetails() throws IOException, CBHException {
        if (isGuidingText()) return null;
        return getExtraHeader().getBlackRatingDetails();
    }

    public String getIdString() {
        if (isGuidingText())
            return "";
        return Integer.toString(getId());
    }

    public String getTournamentString() throws IOException {
        return getTournament().getTitle();
    }

    public Tournament getTournament() throws IOException {
        return getOwnerBase().getTournament(tournamentId);
    }

    public String getAnnotatorString() throws IOException {
        return getAnnotator().getName();
    }

    public Annotator getAnnotator() throws IOException {
        return getOwnerBase().getAnnotator(annotatorId);
    }

    public String getSourceString() throws IOException {
        return getSource().getTitle();
    }

    public Source getSource() throws IOException {
        return getOwnerBase().getSource(sourceId);
    }

    public String getRoundString() {
        return getRound() == 0 ? "" : Integer.toString(getRound());
    }

    public String getSubRoundString() {
        return getSubRound() == 0 ? "" : Integer.toString(getSubRound());
    }

    public LineEvaluation getLineEvaluation() {
        return lineEvaluation;
    }

    public GameResult getResult() {
        return result;
    }

    public String getResultString() {
        if (isGuidingText())
            return "Text";
        switch (result) {
            case BlackWon:
                return "0-1";
            case Draw:
                return "½-½";
            case WhiteWon:
                return "1-0";
            case Line:
                switch (lineEvaluation) {
                    case NoEvaluation:
                        return "Line";
                    case WhiteHasDecisiveAdvantage:
                        return "+-";
                    case WhiteHasClearAdvantage:
                        return "+/-";
                    case WhiteHasSlightAdvantage:
                        return "+/=";
                    case Equal:
                        return "=";
                    case Unclear:
                        return "unclear";
                    case BlackHasSlightAdvantage:
                        return "=/+";
                    case BlackHasClearAdvantage:
                        return "-/+";
                    case BlackHasDecisiveAdvantage:
                        return "-+";
                    case TheoreticalNovelty:
                        return "novelty";
                    case WithCompensation:
                        return "compensation";
                    case WithCounterplay:
                        return "counterplay";
                    case WithInitiative:
                        return "initiative";
                    case WithAttack:
                        return "attack";
                }
                return "Line";
            case BlackWonOnForfeit:
                return "-:+";
            case DrawOnForfeit:
                return "=:=";
            case WhiteWonOnForfeit:
                return "+:-";
            case BothLost:
                return "0-0";
        }
        return "N/A";

    }

    public String getECO() {
        if (isGuidingText())
            return "";
        return eco.toString();
    }

    public Date getPlayedDate() {
        return playedDate;
    }

    public String getVCS() {
        String s = "";
        switch (hasVariants) {
            case 1:
                s += "v";
                break;
            case 2:
                s += "V";
                break;
            case 3:
                s += "r";
                break;
            case 4:
                s += "R";
                break;
        }
        switch (hasCommentaries) {
            case 1:
                s += "c";
                break;
            case 2:
                s += "C";
                break;
        }
        switch (hasSymbols) {
            case 1:
                s += "s";
                break;
            case 2:
                s += "S";
                break;
        }
        return s;
    }

    public String getP() {
        return setupPosition ? "P" : "";
    }

    public String getMedals() {
        if (medals.size() == 0)
            return "";
        return "Yes"; // TODO: Specify this with colors etc
    }

    // TODO: Change to internal
    public ByteBuffer getMoveData() throws IOException, CBHFormatException {
        if (moveData == null)
            moveData = getMoveDataPrivate();
        return moveData;
    }

    // TODO: Change to internal
    public ByteBuffer getAnnotationData() throws IOException, CBHFormatException {
        if (annotationData == null)
            annotationData = getAnnotationDataPrivate();
        return annotationData;
    }

    /**
     * Creates a new game in an opened CBH base. The game will only be created
     * in memory, and not saved to disk until explicitly done.
     *
     * @param database the database to create the game in
     */
    public GameHeader(Database database) {
        super(database);
    }

		/*
		private void DecodeGame(byte[] buffer, int offset)
		{
			int modifier = 0, position = offset, stackDepth = 1;

			// This also verifies that we don't run into unknown opcodes
			while (stackDepth > 0)
			{
				if (position >= buffer.Length)
					throw new CBHFormatException("End of game not reached before game buffer ended");
				int data = _gameDataDecryptMap[(buffer[position] + modifier) % 256];
				buffer[position++] = (byte) data;

				if (data == OPCODE_TWO_BYTES)
				{
					if (position + 1 >= buffer.Length)
						throw new CBHFormatException("End of game not reached before game buffer ended");
					int msb = _gameDataDecryptMap[(buffer[position] + modifier) % 256];
					buffer[position++] = (byte) msb;
					int lsb = _gameDataDecryptMap[(buffer[position] + modifier) % 256];
					buffer[position++] = (byte) lsb;
				}

				if (data <= OPCODE_TWO_BYTES)
				{
					modifier = (modifier + 255) % 256;
				}
				else if (data == OPCODE_IGNORE)
				{
					// Not sure what this opcode do. Just ignoring it seems works fine.
					// Chessbase 9 removes this opcode when replacing the game.
				}
				else if (data < OPCODE_START_VARIANT)
				{
					throw new CBHFormatException("Unknown opcode: 0x" + data.ToString("X2"));
				}
				else if (data == OPCODE_START_VARIANT)
				{
					stackDepth++;
				}
				else if (data == OPCODE_END_VARIANT)
				{
					// Also used as end of game
					stackDepth--;
				}
			}
		}
		*/

    private ByteBuffer getMoveDataPrivate() throws IOException, CBHFormatException {
        if (gameDataPosition == 0)
            return ByteBuffer.allocate(0);

        ByteBuffer moveBuf = ByteBuffer.allocate(4);
        boolean localSetupPosition, encoded;
        try (FileChannel fc = getOwnerBase().getFileChannel("cbg")) {
            fc.read(moveBuf, gameDataPosition);
            int bufferSize = moveBuf.getInt(0);
            //encoded = (bufferSize & 0x80000000) == 0;
            localSetupPosition = (bufferSize & 0x40000000) > 0;
            if (localSetupPosition != setupPosition)
                throw new CBHFormatException("Flags for setup position differs in CBH and CBG files");
            bufferSize &= 0x3FFFFFFF;
            if (bufferSize < 4 || bufferSize >= 0x1000000)
                throw new CBHFormatException("Game size error");
            moveBuf = ByteBuffer.allocate(bufferSize - 4);
            fc.read(moveBuf, gameDataPosition + 4);
            moveBuf.position(0);
        }

			/*
			if (encoded)
			{
				if (setupPosition)
					DecodeGame(moveBuf, 32);
				else
					DecodeGame(moveBuf, 4);
			}
			*/
        return moveBuf;
    }

    private ByteBuffer getAnnotationDataPrivate() throws CBHFormatException, IOException {
        if (annotationDataPosition == 0)
            return ByteBuffer.allocate(0);
        int preRead = 14;
        ByteBuffer buf = ByteBuffer.allocate(preRead);
        try (FileChannel fc = getOwnerBase().getFileChannel("cba")) {
            fc.read(buf, annotationDataPosition);
            int bufferSize = buf.getInt(10);
            //gameSizeInBytes &= 0x3FFFFFFF;
            if (bufferSize >= 0x1000000)
                throw new CBHFormatException("Game size error");
            if (bufferSize > preRead) {
                buf = ByteBuffer.allocate(bufferSize);
                fc.read(buf, annotationDataPosition);
            }
        }
        return buf;
    }

    /**
     * Gets the actual game data
     *
     * @return The game. Returns null if this is a guiding text.
     * @throws CBHFormatException
     */
    public AnnotatedGame getGame() throws CBHException, IOException {
        if (isGuidingText())
            return null;
        if (game == null) {
            ByteBuffer moveBuf = getMoveDataPrivate();
            if (setupPosition) {
                log.debug("Parsing setup position at moveBuf pos " + moveBuf.position());
                // TODO: Move to method
                byte b = moveBuf.get();
                if (b != 1)
                    throw new CBHFormatException("First byte in setup position should be 1");
                b = moveBuf.get();
                int epFile = (b & 15) - 1;
                Piece.PieceColor sideToMove = (b & 16) == 0 ? Piece.PieceColor.WHITE : Piece.PieceColor.BLACK;
                b = moveBuf.get();
                boolean whiteQueenCastle = (b & 1) > 0;
                boolean whiteKingCastle = (b & 2) > 0;
                boolean blackQueenCastle = (b & 4) > 0;
                boolean blackKingCastle = (b & 8) > 0;
                int moveNumber = ByteBufferUtil.getUnsignedByte(moveBuf);
                Piece[][] pieces = new Piece[8][8];
                ByteBufferBitReader byteBufferBitReader = new ByteBufferBitReader(moveBuf);

                for (int x = 0; x < 8; x++) {
                    for (int y = 0; y < 8; y++) {
//                        log.debug("At bit " + curBit);
                        if (byteBufferBitReader.getBit() == 0) {
                            pieces[y][x] = new Piece();
//                            log.debug(String.format("%c%c: Empty", 'a'+x, '1'+y));
                        } else {
                            Piece.PieceColor color = byteBufferBitReader.getBit() == 0 ?
                                    Piece.PieceColor.WHITE : Piece.PieceColor.BLACK;
                            Piece.PieceType type;
                            switch (byteBufferBitReader.getInt(3)) {
                                case 1 : type = Piece.PieceType.KING; break;
                                case 2 : type = Piece.PieceType.QUEEN; break;
                                case 3 : type = Piece.PieceType.KNIGHT; break;
                                case 4 : type = Piece.PieceType.BISHOP; break;
                                case 5 : type = Piece.PieceType.ROOK; break;
                                case 6 : type = Piece.PieceType.PAWN; break;
                                default : throw new CBHException("Invalid piece in setup position");
                            }
                            pieces[y][x] = new Piece(type, color);
//                            log.debug(String.format("%c%c: %s %s", 'a'+x, '1'+y, pieces[y][x].getColor(), pieces[y][x].getPiece()));
                        }
                    }
                }


                Board board = new Board();
                try {
                    board.setup(pieces, sideToMove, whiteKingCastle, whiteQueenCastle, blackKingCastle, blackQueenCastle, epFile);
                } catch (RuntimeException e) {
                    throw new CBHException("Invalid starting position", e);
                }

                moveBuf.position(28);
                game = new AnnotatedGame(board, moveNumber, moveBuf, getAnnotationDataPrivate());
            } else {
                game = new AnnotatedGame(moveBuf, getAnnotationDataPrivate());
            }
        }
        return game;
    }

    private GameHeaderExtra getExtraHeader() throws IOException, CBHException {
        if (extraHeader == null) {
            this.extraHeader = getOwnerBase().getExtraHeader(getId());
        }
        return extraHeader;
    }

    /**
     * Internal constructor used when loading a game header from a CBH database.
     *
     * @param database The database this game header resides in
     * @param gameId   The game header number
     * @param cbhData  The binary record data
     * @throws CBHFormatException
     */
    GameHeader(Database database, int gameId, ByteBuffer cbhData) throws CBHException, IOException {
        super(database, gameId);
        rawData = cbhData;

        deleted = (cbhData.get(0) & 0x80) > 0;
        if ((cbhData.get(0) & 1) == 0)
            throw new CBHFormatException("Bit 0 in byte 0 is not set");
        guidingText = (cbhData.get(0) & 0x02) > 0;

        gameDataPosition = cbhData.getInt(1);

        // For now, throw exception if unknown fields are not null
        if ((cbhData.get(0) & 0x7C) > 0)
            throw new CBHFormatException(String.format("Byte 0 in game data is %02X", cbhData.get(0)));

        if (gameDataPosition == 0)
            throw new CBHFormatException("No game data"); // Can this happen?

        if (guidingText) {
            if (cbhData.get(6) != 0)
                throw new CBHFormatException("Byte 6 is not 0 in guiding text");
            tournamentId = ByteBufferUtil.getBigEndian24BitValue(cbhData, 7);
            sourceId = ByteBufferUtil.getBigEndian24BitValue(cbhData, 10);
            annotatorId = ByteBufferUtil.getBigEndian24BitValue(cbhData, 13);
            round = ByteBufferUtil.getUnsignedByte(cbhData, 16);
            // Get title
            ByteBuffer data = getMoveDataPrivate();
            data.order(ByteOrder.LITTLE_ENDIAN);
            int dummy = data.getShort(0);
//				if (dummy != 1) // Can be 3
//					throw new CBHFormatException("Unknown annotation data");
            int noTitles = data.getShort(2);
            int pos = 4;
            for (int i = 0; i < noTitles; i++) {
                Language titleLang = Language.values()[data.getShort(pos)];
                int titleLen = data.getShort(pos + 2);
                String title = ByteBufferUtil.getZeroTerminatedString(data, pos + 4, titleLen);
                this.title.put(titleLang, title);
                pos += 4 + titleLen;
            }

            for (int i = 17; i < rawData.limit(); i++) {
                if (i == 18 || i == 19) continue;
                if (cbhData.get(i) != 0)
                    throw new CBHFormatException("Byte " + i + " is not 0 in guiding text");
            }
            return;
        }

        annotationDataPosition = cbhData.getInt(5);

        whitePlayerId = ByteBufferUtil.getBigEndian24BitValue(cbhData, 9);
        blackPlayerId = ByteBufferUtil.getBigEndian24BitValue(cbhData, 12);
        tournamentId = ByteBufferUtil.getBigEndian24BitValue(cbhData, 15);
        annotatorId = ByteBufferUtil.getBigEndian24BitValue(cbhData, 18);
        sourceId = ByteBufferUtil.getBigEndian24BitValue(cbhData, 21);

        playedDate = new Date(ByteBufferUtil.getBigEndian24BitValue(cbhData, 24));

        result = GameResult.values()[cbhData.get(27)];
        lineEvaluation = LineEvaluation.decode(cbhData.get(28));

        round = ByteBufferUtil.getUnsignedByte(cbhData, 29);
        subRound = ByteBufferUtil.getUnsignedByte(cbhData, 30);

        whiteElo = cbhData.getShort(31);
        blackElo = cbhData.getShort(33);

        eco = Eco.parse(ByteBufferUtil.getUnsignedShort(cbhData, 35));

        medals = Medals.decode(cbhData.getShort(37));

        setupPosition = (cbhData.get(42) & 0x01) > 0;
        hasVariants = hasCommentaries = hasSymbols = 0;
        annotatedWithColoredSqures = annotatedWithArrows = 0;
        if ((cbhData.get(42) & 0x02) > 0)
            hasVariants = (cbhData.get(44) & 3) + 1;
        if ((cbhData.get(42) & 0x04) > 0)
            hasCommentaries = (cbhData.get(44) & 4) > 0 ? 2 : 1;
        if ((cbhData.get(42) & 0x08) > 0)
            hasSymbols = (cbhData.get(44) & 8) > 0 ? 2 : 1;

        if ((cbhData.get(42) & 16) > 0)
            annotatedWithColoredSqures = ((cbhData.get(44) & 16) > 0) ? 2 : 1;
        if ((cbhData.get(42) & 32) > 0)
            annotatedWithArrows = ((cbhData.get(44) & 32) > 0) ? 2 : 1;
        containsTimeLeft = (cbhData.get(42) & 128) > 0;
        if (containsTimeLeft) {
            containsTimeLeft = true;
        }

        containsCriticalPosition = (cbhData.get(39) & 1) > 0;
        containsCorrespondenceHeader = (cbhData.get(39) & 2) > 0;
        containsEmbeddedAudio = (cbhData.get(40) & 1) > 0;
        containsEmbeddedPicture = (cbhData.get(40) & 2) > 0;
        containsEmbeddedVideo = (cbhData.get(40) & 4) > 0;
        containsGameQuotation = (cbhData.get(40) & 8) > 0;
        containsPathStructure = (cbhData.get(40) & 16) > 0;
        containsPiecePath = (cbhData.get(40) & 32) > 0;
        containsTrainingAnnotation = (cbhData.get(41) & 2) > 0;
        noMoves = cbhData.get(45);
        if (noMoves == 255)
            noMoves = -1; // 255 is used when there are 255 or more moves. Calculate the exact number later on demand.

        // For now, throw exception if unknown fields are not null
        if ((cbhData.get(24) & 0xE0) > 0)
            throw new CBHFormatException(String.format("Byte 24 in game data is %02X", cbhData.get(0)));
        if ((cbhData.get(39) & ~3) > 0)
            throw new CBHFormatException("Byte 39 in game data had an unexpected bit set");
        if ((cbhData.get(40) & 0xC0) > 0)
            throw new CBHFormatException("Byte 40 in game data had an unexpected bit set");
        if ((cbhData.get(41) & ~2) > 0)
            throw new CBHFormatException("Byte 41 in game data had an unexpected bit set");
        if ((cbhData.get(42) & 0x40) > 0)
            throw new CBHFormatException("Byte 42 in game data had an unexpected bit set");
        if (cbhData.get(43) > 0)
            throw new CBHFormatException("Byte 43 in game data was not 0");
        if ((cbhData.get(44) & 0xC0) > 0)
            throw new CBHFormatException("Byte 44 in game data had an unexpected bit set");

        // Do this on demand later when we know it's working
        getGame();
    }

    public GameMetaData toGameMetaData() throws IOException, CBHException {
        GameMetaData meta = new GameMetaData();
        meta.setWhiteFirstName(getWhitePlayer().getFirstName());
        meta.setWhiteLastName(getWhitePlayer().getLastName());
        meta.setBlackFirstName(getBlackPlayer().getFirstName());
        meta.setBlackLastName(getBlackPlayer().getLastName());
        meta.setWhiteElo(getWhiteElo());
        meta.setBlackElo(getBlackElo());
        meta.setWhiteTeam(getWhiteTeamString());
        meta.setBlackTeam(getBlackTeamString());
        meta.setEco(getECO());
        Tournament t = getTournament();
        meta.setEventCountry(t.getNationString());
        meta.setEventName(t.getTitle());
        meta.setEventSite(t.getPlace());
        meta.setAnnotator(getAnnotator().getName());
        meta.setPlayedDate(getPlayedDate().toString());
        String resultString = "";
        switch (result) {
            case BlackWon:
            case BlackWonOnForfeit:
                resultString = "0-1";
                break;
            case Draw:
            case DrawOnForfeit:
                resultString = "½-½";
                break;
            case WhiteWon:
            case WhiteWonOnForfeit:
                resultString = "1-0";
                break;
        }
        meta.setResult(resultString);
        meta.setRound(getRound());
        meta.setRound(getSubRound());
        return meta;
    }
}

