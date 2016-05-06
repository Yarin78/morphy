package yarin.cbhlib;

import yarin.cbhlib.exceptions.CBHException;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an instance of a CBH database on disk
 */
public class Database
{
    private Path _directory; // e.g. "/Users/yarin/chessbase/bases"
    private String _baseName; // e.g. "mybase"

    private int numberOfGames;
    private int numberOfPlayers;
    private int numberOfTournaments;
    private int numberOfAnnotators;
    private int numberOfSources;
    private int numberOfTeams;

    private int firstPlayerOffset;
    private int playerRecordLength;

    private static final int CBH_HEADER_LENGTH = 46;
    private static final int CBH_RECORD_LENGTH = 46;
    private static final int CBP_HEADER_LENGTH = 28;
//    private static final int CBP_RECORD_LENGTH = 67;
    private static final int CBT_HEADER_LENGTH = 28;
    private static final int CBT_RECORD_LENGTH = 99;
    private static final int CBC_HEADER_LENGTH = 28;
    private static final int CBC_RECORD_LENGTH = 62;
    private static final int CBS_HEADER_LENGTH = 28;
    private static final int CBS_RECORD_LENGTH = 68;
    private static final int CBE_HEADER_LENGTH = 28;
    private static final int CBE_RECORD_LENGTH = 72;

    /**
     * Gets the number of games in the database.
     * The games are numbered sequentially starting from 1.
     * @return the number of games in the database
     */
    public int getNumberOfGames()
    {
        return numberOfGames;
    }

    /**
     * Gets the number of players in the database.
     * The players are numbered sequentially starting from 1.
     * @return The number of players in the database
     */
    public int getNumberOfPlayers()
    {
        return numberOfPlayers;
    }

    /**
     * Gets the number of tournaments in the database.
     * The tournaments are numbered sequentially starting from 1.
     * @return The number of tournaments in the database
     */
    public int getNumberOfTournaments()
    {
        return numberOfTournaments;
    }

    /**
     * Gets the number of annotators in the database.
     * The annotators are numbered sequentially starting from 1.
     * @return The number of annotators in the database
     */
    public int getNumberOfAnnotators()
    {
        return numberOfAnnotators;
    }

    /**
     * Gets the number of sources in the database.
     * The sources are numbered sequentially starting from 1.
     * @return The number of sources in the database
     */
    public int getNumberOfSources()
    {
        return numberOfSources;
    }

    /**
     * Gets the number of teams in the database.
     * The teams are numbered sequentially starting from 1.
     * @return The number of teams in the database
     */
    public int getNumberOfTeams()
    {
        return numberOfTeams;
    }

    private Database(Path directory, String baseName)
    {
        _directory = directory;
        _baseName = baseName;
        numberOfGames = 0;
        numberOfPlayers = 0;
        numberOfTournaments = 0;
        numberOfAnnotators = 0;
        numberOfSources = 0;
        numberOfTeams = 0;
    }

    /**
     * Loads a CBH database.
     * @param cbhFile The CBH file to open. The extension .cbh will be added if missing.
     * @return An instance of a valid CBH database
     */
    public static Database open(String cbhFile) throws IOException {
        if (cbhFile == null)
            throw new IllegalArgumentException("cbhFile must not be null");

        if (cbhFile.toLowerCase().endsWith(".cbh"))
            cbhFile = cbhFile.substring(0, cbhFile.length() - 4);

        Path path = Paths.get(cbhFile).toAbsolutePath();

        Database database = new Database(path.getParent(), path.getFileName().toString());
        database.invalidate();

        return database;
    }

    /**
     * Clears any cached data and reloads the file headers
     */
    public void invalidate() throws IOException {
        refreshHeaders();
    }

    private void refreshHeaders() throws IOException {
        try (FileChannel fc = getFileChannel("cbh"))
        {
            ByteBuffer header = ByteBuffer.allocate(CBH_HEADER_LENGTH);
            fc.read(header, 0);
            numberOfGames = header.getInt(6) - 1;
            //int alsoNoGamesPlusOne = ByteBufferUtil.GetBigEndianInt(header, 40);
            //if (noGamesPlusOne != alsoNoGamesPlusOne)
            //	throw new CBHFormatException("The two fields in the header supposedly containing the total number of games differed.");
        }
        try (FileChannel fc = getFileChannel("cbp"))
        {
            ByteBuffer header = ByteBuffer.allocate(CBP_HEADER_LENGTH);
            header.order(ByteOrder.LITTLE_ENDIAN);
            fc.read(header, 0);
            numberOfPlayers = header.getInt(0);
            playerRecordLength = header.getInt(12) + 9;
            firstPlayerOffset = CBP_HEADER_LENGTH + header.getInt(24);

            //		if (numberOfPlayers != ByteBufferUtil.GetLittleEndianInt(header, 20))
            //	throw new CBHFormatException("Number of players in header mismatched");
        }
        try (FileChannel fc = getFileChannel("cbt"))
        {
            ByteBuffer header = ByteBuffer.allocate(CBT_HEADER_LENGTH);
            header.order(ByteOrder.LITTLE_ENDIAN);
            fc.read(header, 0);
            numberOfTournaments = header.getInt(0);

            //if (numberOfTournaments != ByteBufferUtil.GetLittleEndianInt(header, 20))
            //	throw new CBHFormatException("Number of tournaments in header mismatched");
        }
        try (FileChannel fc = getFileChannel("cbc"))
        {
            ByteBuffer header = ByteBuffer.allocate(CBC_HEADER_LENGTH);
            header.order(ByteOrder.LITTLE_ENDIAN);
            fc.read(header, 0);
            numberOfAnnotators = header.getInt(0);

            //if (numberOfAnnotators != ByteBufferUtil.GetLittleEndianInt(header, 20))
            //	throw new CBHFormatException("Number of annotators in header mismatched");
        }
        try (FileChannel fc = getFileChannel("cbs"))
        {
            ByteBuffer header = ByteBuffer.allocate(CBS_HEADER_LENGTH);
            header.order(ByteOrder.LITTLE_ENDIAN);
            fc.read(header, 0);
            numberOfSources = header.getInt(0);

            //	if (numberOfSources != ByteBufferUtil.GetLittleEndianInt(header, 20))
            //	throw new CBHFormatException("Number of sources in header mismatched");
        }
        try (FileChannel fc = getFileChannel("cbe"))
        {
            ByteBuffer header = ByteBuffer.allocate(CBE_HEADER_LENGTH);
            header.order(ByteOrder.LITTLE_ENDIAN);
            fc.read(header, 0);
            numberOfTeams = header.getInt(0);

            //if (numberOfTeams != ByteBufferUtil.GetLittleEndianInt(header, 20))
            //	throw new CBHFormatException("Number of teams in header mismatched");
        } catch (FileNotFoundException e) {
            // This is okay for this database
            numberOfTeams = 0;
        }
    }

    /*
    // TODO: Should be internal
    public DataInputStream GetFileStream(String extension) throws IOException
    {
        File file = new File(_directory.toString(), _baseName + "." + extension);
        try {
            return new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
        } catch (FileNotFoundException e) {

        } catch (RuntimeException e) {
            throw new CBHIOException("Failed to open the " + _baseName + "." + extension + " file", e);
        }
    }
    */

    // TODO: Should be internal
    public FileChannel getFileChannel(String extension) throws FileNotFoundException
    {
        File file = new File(_directory.toString(), _baseName + "." + extension);
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        return raf.getChannel();
//            throw new IOException("Failed to open the " + _baseName + "." + extension + " file", e);
    }

    /**
     * Gets a game header from the database
     * @param gameId The ID number of the game header. The first game header has number 1.
     * @return The specified game header
     */
    public GameHeader getGameHeader(int gameId) throws IOException, CBHException {
        if (gameId < 1 || gameId > numberOfGames)
            throw new IllegalArgumentException("Invalid game id");

        ByteBuffer buffer = ByteBuffer.allocate(CBH_RECORD_LENGTH);
        try (FileChannel fc = getFileChannel("cbh"))
        {
            fc.position(CBH_HEADER_LENGTH + (gameId - 1) * CBH_RECORD_LENGTH);
            fc.read(buffer);
        }
        GameHeader game = new GameHeader(this, gameId, buffer);
        return game;
    }

    /**
     * Gets a range of game headers from the database
     * @param firstGameId The ID number of the first game header to get.
     * @param lastGameId The ID number of the last game header to get.
     * @return All game headers between the first and last id, inclusive.
     */
    public GameHeader[] getGameHeaders(int firstGameId, int lastGameId) throws IOException, CBHException {
        if (firstGameId > lastGameId)
            return new GameHeader[0];

        GameHeader[] games = new GameHeader[lastGameId - firstGameId + 1];
        for (int i = firstGameId; i <= lastGameId; i++) {
            games[i - firstGameId] = getGameHeader(i);
        }
        return games;
    }

    /**
     * Gets a player from the database
     * @param playerId The ID number of the player. The first player has number 0.
     * @return The specified player
     */
    public Player getPlayer(int playerId) throws IOException {
        if (playerId < 0 || playerId >= numberOfPlayers)
            throw new IllegalArgumentException("Invalid player");

        ByteBuffer buffer = ByteBuffer.allocate(playerRecordLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        try (FileChannel fc = getFileChannel("cbp"))
        {
            fc.read(buffer, firstPlayerOffset + playerId * playerRecordLength);
        }
        Player game = new Player(this, playerId, buffer);
        return game;
    }

    /**
     * Gets a range of players from the database
     * @param firstPlayerId The ID number of the first player to get.
     * @param lastPlayerId The ID number of the last player to get.
     * @return All players between the first and last id, inclusive.
     */
    public List<Player> getPlayers(int firstPlayerId, int lastPlayerId) throws IOException {
        ArrayList<Player> players = new ArrayList<>();
        if (firstPlayerId > lastPlayerId)
            return players;

        for (int i = firstPlayerId; i <= lastPlayerId; i++) {
            players.add(getPlayer(i));
        }
        return players;
    }

    /**
     * Gets a tournament from the database
     * @param tournamentId The ID number of the tournament. The first tournament has number 0.
     * @return The specified tournament
     */
    public Tournament getTournament(int tournamentId) throws IOException {
        if (tournamentId < 0 || tournamentId >= numberOfTournaments)
            throw new IllegalArgumentException("Invalid tournament");

        ByteBuffer buf = ByteBuffer.allocate(CBT_RECORD_LENGTH);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        try (FileChannel fc = getFileChannel("cbt")) {
            fc.read(buf, CBT_HEADER_LENGTH + tournamentId * CBT_RECORD_LENGTH);
        }
        Tournament game = new Tournament(this, tournamentId, buf);
        return game;
    }

    /**
     * Gets a range of tournaments from the database
     * @param firstTournamentId The ID number of the first tournament to get.
     * @param lastTournamentId The ID number of the last tournament to get.
     * @return All tournaments between the first and last id, inclusive.
     */
    public Tournament[] getTournaments(int firstTournamentId, int lastTournamentId) throws IOException {
        if (firstTournamentId > lastTournamentId)
            return new Tournament[0];

        Tournament[] tournaments = new Tournament[lastTournamentId - firstTournamentId + 1];
        for (int i = firstTournamentId; i <= lastTournamentId; i++)
        {
            tournaments[i - firstTournamentId] = getTournament(i);
        }
        return tournaments;
    }

    /**
     * Gets an annotator from the database
     * @param annotatorId
     * @return The ID number of the annotator. The first annotator has number 0.
     * @throws IOException The specified annotator
     */
    public Annotator getAnnotator(int annotatorId) throws IOException {
        if (annotatorId < 0 || annotatorId >= numberOfAnnotators)
            throw new IllegalArgumentException("Invalid annotator");

        ByteBuffer buffer = ByteBuffer.allocate(CBC_RECORD_LENGTH);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        try (FileChannel fc = getFileChannel("cbc"))
        {
            fc.read(buffer, CBC_HEADER_LENGTH + annotatorId * CBC_RECORD_LENGTH);
        }
        Annotator annotator = new Annotator(this, annotatorId, buffer);
        return annotator;
    }

    /**
     * Gets a range of annotators from the database
     * @param firstAnnotatorId The ID number of the first annotator to get.
     * @param lastAnnotatorId The ID number of the last annotator to get.
     * @return All annotators between the first and last id, inclusive.
     */
    public List<Annotator> getAnnotators(int firstAnnotatorId, int lastAnnotatorId) throws IOException {
        ArrayList<Annotator> annotators = new ArrayList<>();
        if (firstAnnotatorId > lastAnnotatorId)
            return annotators;

        for (int i = firstAnnotatorId; i <= lastAnnotatorId; i++) {
            annotators.add(getAnnotator(i));
        }
        return annotators;
    }

    /**
     * Gets an source from the database
     * @param sourceId The ID number of the source. The first source has number 0.
     * @return The specified source
     */
    public Source getSource(int sourceId) throws IOException {
        if (sourceId < 0 || sourceId >= numberOfSources)
            throw new IllegalArgumentException("Invalid source");

        ByteBuffer buffer = ByteBuffer.allocate(CBS_RECORD_LENGTH);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        try (FileChannel fc = getFileChannel("cbs")) {
            fc.read(buffer, CBS_HEADER_LENGTH + sourceId * CBS_RECORD_LENGTH);
        }
        Source source = new Source(this, sourceId, buffer);
        return source;
    }

    /**
     * Gets a range of sources from the database
     * @param firstSourceId The ID number of the first source to get.
     * @param lastSourceId The ID number of the last source to get.
     * @return All sources between the first and last id, inclusive.
     */
    public List<Source> getSources(int firstSourceId, int lastSourceId) throws IOException {
        ArrayList<Source> sources = new ArrayList<>();
        if (firstSourceId > lastSourceId)
            return sources;

        for (int i = firstSourceId; i <= lastSourceId; i++) {
            sources.add(getSource(i));
        }
        return sources;
    }

    /**
     * Gets an team from the database
     * @param teamId The ID number of the team. The first team has number 0.
     * @return The specified team
     */
    public Team getTeam(int teamId) throws IOException {
        if (teamId < 0 || teamId >= numberOfTeams)
            throw new IllegalArgumentException("Invalid team");

        ByteBuffer buffer = ByteBuffer.allocate(CBE_RECORD_LENGTH);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        try (FileChannel fc = getFileChannel("cbe"))
        {
            fc.read(buffer,CBE_HEADER_LENGTH + teamId * CBE_RECORD_LENGTH);
        }
        Team team = new Team(this, teamId, buffer);
        return team;
    }

    /**
     * Gets a range of teams from the database
     * @param firstTeamId The ID number of the first team to get.
     * @param lastTeamId The ID number of the last team to get.
     * @return All teams between the first and last id, inclusive.
     */
    public List<Team> getTeams(int firstTeamId, int lastTeamId) throws IOException {
        ArrayList<Team> teams = new ArrayList<>();
        if (firstTeamId > lastTeamId)
            return teams;

        for (int i = firstTeamId; i <= lastTeamId; i++) {
            teams.add(getTeam(i));
        }
        return teams;
    }
}
