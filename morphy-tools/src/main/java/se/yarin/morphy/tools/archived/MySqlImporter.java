package se.yarin.morphy.tools;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.*;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.games.GameHeaderFlags;
import se.yarin.cbhlib.games.Medal;
import se.yarin.cbhlib.games.search.GameSearcher;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.NAG;

import java.io.File;
import java.io.IOException;
import java.sql.*;

public class MySqlImporter {

  private Connection getConnection() throws SQLException {
    return DriverManager.getConnection(
        "jdbc:mysql://localhost/chess?user=root&password=root&rewriteBatchedStatements=true");
  }

  public static void main(String[] args) throws SQLException, IOException {
    MySqlImporter importer = new MySqlImporter();

    File megadb = new File("/Users/yarin/chess/bases/Mega2021/Mega Database 2021.cbh");
    Database db = Database.open(megadb);

    // importer.importPlayers(db.getPlayerBase());
    importer.importGames(db);
  }

  private void importGames(Database db) throws SQLException {
    long start = System.currentTimeMillis();
    try (Connection conn = getConnection()) {
      Statement stmt = conn.createStatement();
      stmt.execute("DROP TABLE IF EXISTS games;");
      stmt.execute(
          "CREATE TABLE games (\n"
              + "    id int not null primary key,\n"
              + "    deleted bit not null,\n"
              + "    guiding_text bit not null,\n"
              + "    white_id int not null,\n"
              + "    black_id int not null,\n"
              + "    tournament_id int not null,\n"
              + "    annotator_id int not null,\n"
              + "    source_id int not null,\n"
              + "    white_team_id int,\n"
              + "    black_team_id int,\n"
              + "    played_date varchar(10),\n"
              + "    result varchar(8) not null,\n"
              + "    `round` int,\n"
              + "    sub_round int,\n"
              + "    white_elo int,\n"
              + "    black_elo int,\n"
              + "    chess960 int,\n"
              + "    eco varchar(6),\n"
              + "    line_evaluation VARCHAR(5) CHARACTER SET UTF8MB4,\n"
              + "    medal_mask int not null,\n"
              + "    flags int not null,\n"
              + "    num_moves int not null,\n"
              + "    game_version int not null,\n"
              + "    creation_timestamp bigint not null,\n"
              + "    last_changed_timestamp bigint not null,\n"
              + "    game_tag_id int\n"
              + ");");
      stmt.execute("DROP TABLE IF EXISTS moves;");
      stmt.execute(
          "CREATE TABLE moves (\n"
              + "    game_id int not null,\n"
              + "    ply int not null,\n"
              + "    hash_lo bigint not null,\n"
              + "    hash_hi bigint not null,\n"
              + "    move varchar(10),\n"
              + "    position text,\n"
              + "    primary key (game_id, ply)\n"
              + ");\n");
      stmt.close();
      int gameCnt = 0, moveCnt = 0;
      PreparedStatement pstmt =
          conn.prepareStatement(
              "INSERT INTO games("
                  + "id, deleted, guiding_text, white_id, black_id, tournament_id, annotator_id, source_id,"
                  + "white_team_id, black_team_id, played_date, result, `round`, sub_round, "
                  + "white_elo, black_elo, chess960, eco, line_evaluation, medal_mask, flags, "
                  + "num_moves, game_version, creation_timestamp, last_changed_timestamp, game_tag_id) "
                  + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

      PreparedStatement mstmt =
          conn.prepareStatement(
              "INSERT INTO moves(game_id, ply, hash_lo, hash_hi, position, move) VALUES (?, ?, ?, ?, ?, ?)");
      GameSearcher searcher = new GameSearcher(db);
      /*
      searcher.addFilter(new SearchFilterBase(db) {
          @Override
          public int firstGameId() {
              return 1;
          }

          @Override
          public boolean matches(Game game) {
              return true;
          }
      });
       */
      Iterable<Game> games = searcher.iterableSearch();
      for (Game game : games) {
        pstmt.setInt(1, game.getId());
        pstmt.setBoolean(2, game.isDeleted());
        pstmt.setBoolean(3, game.isGuidingText());
        pstmt.setInt(4, game.getWhitePlayerId());
        pstmt.setInt(5, game.getBlackPlayerId());
        pstmt.setInt(6, game.getTournamentId());
        pstmt.setInt(7, game.getAnnotatorId());
        pstmt.setInt(8, game.getSourceId());
        if (game.getWhiteTeamId() >= 0) {
          pstmt.setInt(9, game.getWhiteTeamId());
        } else {
          pstmt.setNull(9, Types.INTEGER);
        }
        if (game.getBlackTeamId() >= 0) {
          pstmt.setInt(10, game.getBlackTeamId());
        } else {
          pstmt.setNull(10, Types.INTEGER);
        }
        pstmt.setString(11, game.getPlayedDate().toPrettyString());
        pstmt.setString(12, game.getResult().toString());
        if (game.getRound() > 0) {
          pstmt.setInt(13, game.getRound());
        } else {
          pstmt.setNull(13, Types.INTEGER);
        }
        if (game.getSubRound() > 0) {
          pstmt.setInt(14, game.getSubRound());
        } else {
          pstmt.setNull(14, Types.INTEGER);
        }
        if (game.getWhiteElo() > 0) {
          pstmt.setInt(15, game.getWhiteElo());
        } else {
          pstmt.setNull(15, Types.INTEGER);
        }
        if (game.getBlackElo() > 0) {
          pstmt.setInt(16, game.getWhiteElo());
        } else {
          pstmt.setNull(16, Types.INTEGER);
        }
        if (game.getHeader().getChess960StartPosition() >= 0) {
          pstmt.setInt(17, game.getHeader().getChess960StartPosition());
        } else {
          pstmt.setNull(17, Types.INTEGER);
        }
        if (game.getEco().isSet()) {
          pstmt.setString(18, game.getEco().toString());
        } else {
          pstmt.setNull(18, Types.VARCHAR);
        }
        if (game.getLineEvaluation() != NAG.NONE) {
          pstmt.setString(19, game.getLineEvaluation().toUnicodeString());
        } else {
          pstmt.setNull(19, Types.NVARCHAR);
        }
        pstmt.setInt(20, Medal.encode(game.getHeader().getMedals()));
        pstmt.setInt(21, GameHeaderFlags.encodeFlags(game.getHeader().getFlags()));
        pstmt.setInt(22, game.getNoMoves());
        pstmt.setInt(23, game.getGameVersion());
        pstmt.setLong(24, game.getCreationTimestamp());
        pstmt.setLong(25, game.getLastChangedTimestamp());
        if (game.getGameTagId() >= 0) {
          pstmt.setInt(26, game.getGameTagId());
        } else {
          pstmt.setNull(26, Types.INTEGER);
        }

        pstmt.addBatch();

        if (!game.isGuidingText()) {
          try {
            GameModel model = game.getModel();
            GameMovesModel.Node current = model.moves().root();
            while (current != null && current.ply() <= 35) {
              mstmt.setInt(1, game.getId());
              mstmt.setInt(2, current.ply());
              mstmt.setLong(3, current.position().getZobristHashLo());
              mstmt.setLong(4, current.position().getZobristHashHi());
              mstmt.setString(5, current.position().toString());
              if (current.hasMoves()) {
                mstmt.setString(6, current.mainMove().toSAN());
              } else {
                mstmt.setNull(6, Types.VARCHAR);
              }
              moveCnt += 1;
              mstmt.addBatch();

              current = current.mainNode();
            }
          } catch (ChessBaseException e) {
            System.err.println("Error getting moves for game " + game.getId());
          }
        }

        gameCnt += 1;
        if (gameCnt % 1000 == 0) {
          pstmt.executeBatch();
          mstmt.executeBatch();
          long tm = System.currentTimeMillis();
          long elapsed = tm - start;
          System.out.println(
              String.format(
                  "%d games and %d moves inserted (%d ms last batch)", gameCnt, moveCnt, elapsed));
          start = tm;
        }
      }
      pstmt.executeBatch();
      mstmt.executeBatch();
      System.out.println("Last batch inserted");

      pstmt.close();
      mstmt.close();
    }
  }

  private void importPlayers(PlayerBase playerBase) throws SQLException {
    try (Connection conn = getConnection()) {
      Statement stmt = conn.createStatement();
      stmt.execute("DROP TABLE IF EXISTS players;");
      stmt.execute(
          "CREATE TABLE players (\n"
              + "    id int not null primary key,\n"
              + "    first_name text not null,\n"
              + "    last_name text not null\n"
              + ");");
      stmt.close();

      PreparedStatement pstmt =
          conn.prepareStatement("INSERT INTO players(id, last_name, first_name) VALUES (?, ?, ?)");
      int cnt = 0;
      for (PlayerEntity p : playerBase.iterable()) {
        pstmt.setInt(1, p.getId());
        pstmt.setString(2, p.getLastName());
        pstmt.setString(3, p.getFirstName());
        pstmt.addBatch();
        cnt += 1;
        if (cnt % 1000 == 0) {
          pstmt.executeBatch();
          System.out.println(cnt + " players inserted");
        }
      }
      pstmt.executeBatch();
      pstmt.close();
    }
  }

  private void importTournaments(TournamentBase tournamentBase) throws SQLException {
    try (Connection conn = getConnection()) {
      Statement stmt = conn.createStatement();
      stmt.execute("DROP TABLE IF EXISTS tournaments;");
      stmt.execute(
          "CREATE TABLE tournaments (\n"
              + "    id int not null primary key,\n"
              + "    title text not null,\n"
              + "    start_date date,\n"
              + "    category int,\n"
              + "    rounds int,\n"
              + "    type varchar(8),\n"
              + "    complete bit not null,\n"
              + "    three_points_win bit not null,\n"
              + "    team bit not null,\n"
              + "    board_points bit not null,\n"
              + "    time_control varchar(8) not null,\n"
              + "    place text not null,\n"
              + "    nation varchar(3)\n"
              + ");");
      stmt.close();
    }
  }

  private void importAnnotators(AnnotatorBase annotatorBase) throws SQLException {
    try (Connection conn = getConnection()) {
      Statement stmt = conn.createStatement();
      stmt.execute("DROP TABLE IF EXISTS annotators;");
      stmt.execute(
          "CREATE TABLE annotators (\n"
              + "    id int not null primary key,\n"
              + "    name text not null\n"
              + ");");
      stmt.close();
    }
  }

  private void importSources(SourceBase sourceBase) throws SQLException {
    try (Connection conn = getConnection()) {
      Statement stmt = conn.createStatement();
      stmt.execute("DROP TABLE IF EXISTS sources;");
      stmt.execute(
          "CREATE TABLE sources (\n"
              + "    id int not null primary key,\n"
              + "    title text not null,\n"
              + "    publisher text,\n"
              + "    publication date,\n"
              + "    `date` date,\n"
              + "    version int,\n"
              + "    quality varchar(8)\n"
              + ");");
      stmt.close();
    }
  }

  private void importTeams(TeamBase teamBase) throws SQLException {
    try (Connection conn = getConnection()) {
      Statement stmt = conn.createStatement();
      stmt.execute("DROP TABLE IF EXISTS teams;");
      stmt.execute(
          "CREATE TABLE teams (\n"
              + "    id int not null primary key,\n"
              + "    title text not null,\n"
              + "    team_number int not null,\n"
              + "    season bit not null,\n"
              + "    year int not null,\n"
              + "    nation varchar(3) not null\n"
              + ");");
      stmt.close();
    }
  }
}
