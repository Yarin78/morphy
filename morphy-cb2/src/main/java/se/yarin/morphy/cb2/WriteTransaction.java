package se.yarin.morphy.cb2;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Chess;
import se.yarin.chess.Chess960;
import se.yarin.chess.Date;
import se.yarin.chess.Eco;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.GameResult;
import se.yarin.chess.NAG;
import se.yarin.chess.Position;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.cb2.annotations.AnnotationBlockCodec;
import se.yarin.morphy.cb2.entities.Entity;
import se.yarin.morphy.cb2.entities.EntityFile;
import se.yarin.morphy.cb2.entities.EntityType;
import se.yarin.morphy.cb2.entities.GameTag;
import se.yarin.morphy.cb2.entities.Player;
import se.yarin.morphy.cb2.entities.Source;
import se.yarin.morphy.cb2.entities.Team;
import se.yarin.morphy.cb2.entities.Tournament;
import se.yarin.morphy.cb2.games.AnalysisHeader;
import se.yarin.morphy.cb2.games.Dates;
import se.yarin.morphy.cb2.games.EcoField;
import se.yarin.morphy.cb2.games.FinalMaterial;
import se.yarin.morphy.cb2.games.GameHeader;
import se.yarin.morphy.cb2.games.GameHeaderFile;
import se.yarin.morphy.cb2.games.GameRecord;
import se.yarin.morphy.cb2.games.RatingType;
import se.yarin.morphy.cb2.games.TextHeader;
import se.yarin.morphy.cb2.games.Timestamps;
import se.yarin.morphy.cb2.indexes.EntityOrder;
import se.yarin.morphy.cb2.indexes.GameListFile;
import se.yarin.morphy.cb2.indexes.GameListFile.Role;
import se.yarin.morphy.cb2.indexes.SortIndexFile;
import se.yarin.morphy.cb2.indexes.SortIndexFile.StandardIndex;
import se.yarin.morphy.cb2.moves.GuidingTextCodec;
import se.yarin.morphy.cb2.moves.MoveStreamCodec;
import se.yarin.morphy.cb2.storage.RecordFile;
import se.yarin.morphy.chessbase.DatabaseLocks;
import se.yarin.morphy.chessbase.GameHeaderFlags;
import se.yarin.morphy.chessbase.Medal;
import se.yarin.morphy.chessbase.Nation;
import se.yarin.morphy.chessbase.TournamentTimeControl;
import se.yarin.morphy.chessbase.TournamentType;
import se.yarin.morphy.chessbase.annotations.AnnotationStatistics;
import se.yarin.morphy.chessbase.annotations.GraphicalArrowsAnnotation;
import se.yarin.morphy.chessbase.annotations.GraphicalSquaresAnnotation;
import se.yarin.morphy.chessbase.annotations.StatisticalAnnotation;
import se.yarin.morphy.chessbase.annotations.SymbolAnnotation;
import se.yarin.morphy.chessbase.annotations.TextAfterMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.TextBeforeMoveAnnotation;
import se.yarin.morphy.chessbase.annotations.TimeSpentAnnotation;
import se.yarin.morphy.chessbase.annotations.TrainingAnnotation;
import se.yarin.morphy.chessbase.text.TextContentsModel;
import se.yarin.morphy.chessbase.text.TextHeaderModel;
import se.yarin.morphy.chessbase.text.TextLanguage;
import se.yarin.morphy.chessbase.text.TextModel;

/**
 * A transaction that changes the database. It holds the update lock while open, so at most one
 * write transaction exists at a time, while read transactions go on.
 *
 * <p>Changes are gathered in the transaction, which sees them itself, and nothing reaches the
 * files until {@link #commit()}, which takes the write lock and applies them all: records in the
 * move and annotation files, game records, entities, game lists and sort orders. A commit is
 * refused if another transaction committed since this one began. After a commit or a {@link
 * #rollback()} the transaction can be used again.
 *
 * <p>A game refers to its entities by id when its header is bound to them; otherwise each entity
 * is looked up by name and created if it doesn't exist. A field left blank refers to the empty
 * placeholder entity, created if needed, except the teams, which are then −1. An entity no record
 * refers to any more is deleted.
 */
public final class WriteTransaction extends DatabaseTransaction {
  private static final Logger log = LoggerFactory.getLogger(WriteTransaction.class);

  // Keeps creation timestamps unique within the process
  private static long lastCreationTimestamp;

  // Bits of the annotation magnitudes of a game record
  private static final int MAG_COMMENTARY = 1 << 2, MAG_SYMBOLS = 1 << 3, MAG_SQUARES = 1 << 4;
  private static final int MAG_ARROWS = 1 << 5, MAG_TIME_SPENT = 1 << 7, MAG_TRAINING = 1 << 9;

  /** A record and its data, as they will be written. */
  private record Pending(
      @NotNull GameRecord record,
      @NotNull RecordFile.Record moves,
      byte @Nullable [] annotations) {}

  /** A reference from a record to an entity, in a role. */
  private record Ref(@NotNull EntityType type, long id, @NotNull Role role) {}

  private int version;
  private int count;
  private final TreeMap<Integer, Pending> pending = new TreeMap<>();
  // New and changed entities, by type and id; new ones in the order they were created
  private final Map<EntityType, LinkedHashMap<Integer, Entity>> newEntities =
      new EnumMap<>(EntityType.class);
  private final Map<EntityType, Map<Integer, Entity>> changedEntities = new EnumMap<>(EntityType.class);
  private final Map<EntityType, Map<String, Integer>> newIdentities = new EnumMap<>(EntityType.class);

  public WriteTransaction(@NotNull Database2Cbh database) {
    super(database, DatabaseLocks.Lock.UPDATE);
    if (!database.isWritable()) {
      close();
      throw new IllegalStateException("Database " + database.name() + " is read-only");
    }
    reset();
  }

  private void reset() {
    pending.clear();
    newEntities.clear();
    changedEntities.clear();
    newIdentities.clear();
    count = database().gameHeaderFile().count();
    version = database().context().version();
  }

  /** Whether the transaction holds changes not yet committed. */
  public boolean hasUncommittedChanges() {
    return !pending.isEmpty() || !newEntities.isEmpty() || !changedEntities.isEmpty();
  }

  // ── Reading, with the transaction's changes ─────────────────────────────

  @Override
  public int count() {
    return count;
  }

  @Override
  public @NotNull GameRecord record(int id) {
    ensureOpen();
    Pending p = pending.get(id);
    if (p != null) {
      return p.record();
    }
    return super.record(id);
  }

  @Override
  protected @NotNull RecordFile.Record movesData(@NotNull GameRecord record) {
    Pending p = pending.get(record.id());
    return p != null ? p.moves() : super.movesData(record);
  }

  @Override
  protected byte @Nullable [] annotationData(@NotNull GameRecord record) {
    Pending p = pending.get(record.id());
    return p != null ? p.annotations() : super.annotationData(record);
  }

  @Override
  public int entityCount(@NotNull EntityType type) {
    int n = super.entityCount(type);
    for (int id : newEntities.getOrDefault(type, new LinkedHashMap<>()).keySet()) {
      n = Math.max(n, id + 1);
    }
    return n;
  }

  @Override
  public @Nullable Entity entity(@NotNull EntityType type, long id) {
    Entity changed = changedEntities.getOrDefault(type, Map.of()).get((int) id);
    if (changed != null) {
      return changed;
    }
    return super.entity(type, id);
  }

  // ── Games ────────────────────────────────────────────────────────────────

  /**
   * Adds a game.
   *
   * @return the id it will have
   * @throws IllegalArgumentException if the header is bound to an entity that doesn't exist, or a
   *     move can't be stored
   */
  public int addGame(@NotNull GameModel game) {
    ensureOpen();
    int id = count + 1;
    putGame(id, null, game);
    count = id;
    return id;
  }

  /**
   * Replaces a game.
   *
   * @throws IllegalArgumentException if there is no game with that id, or as for {@link #addGame}
   */
  public void replaceGame(int id, @NotNull GameModel game) {
    ensureOpen();
    if (!(record(id) instanceof GameHeader previous)) {
      throw new IllegalArgumentException("Record " + id + " is not a game");
    }
    putGame(id, previous, game);
  }

  private void putGame(int id, @Nullable GameHeader previous, GameModel game) {
    GameHeaderModel h = game.header();
    GameMovesModel moves = game.moves();

    long whiteId = entityId(EntityType.PLAYER, h.getWhiteId(), () -> Player.ofFullName(text(h.getWhite())));
    long blackId = entityId(EntityType.PLAYER, h.getBlackId(), () -> Player.ofFullName(text(h.getBlack())));
    long tournamentId = entityId(EntityType.TOURNAMENT, h.getEventId(), () -> tournament(h));
    long annotatorId =
        entityId(EntityType.PLAYER, h.getAnnotatorId(), () -> Player.ofFullName(text(h.getAnnotator())));
    long sourceId =
        entityId(
            EntityType.SOURCE,
            h.getSourceId(),
            () ->
                new Source(
                    text(h.getSourceTitle()),
                    text(h.getSource()),
                    h.getSourceDate() == null ? 0 : Dates.encode(h.getSourceDate()),
                    0,
                    0,
                    0));
    long whiteTeamId = teamId(h.getWhiteTeamId(), h.getWhiteTeam());
    long blackTeamId = teamId(h.getBlackTeamId(), h.getBlackTeam());
    long gameTagId =
        entityId(
            EntityType.GAME_TAG,
            h.getGameTagId(),
            () -> text(h.getGameTag()).isEmpty() ? GameTag.empty() : GameTag.of(h.getGameTag()));

    MoveStreamCodec.Encoded encoded;
    try {
      encoded = MoveStreamCodec.encode(moves);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("The moves of the game can't be stored: " + e.getMessage(), e);
    }
    byte[] annotations = AnnotationBlockCodec.encode(moves);

    GameResult result = h.getResult() == null ? GameResult.NOT_FINISHED : h.getResult();
    NAG lineEvaluation = h.getLineEvaluation();
    int sp = moves.root().position().chess960StartPosition();
    boolean chess960 = sp != Chess960.REGULAR_CHESS_SP;
    Eco eco = h.getEco() == null ? Eco.unset() : h.getEco();
    int whiteElo = h.getWhiteElo() == null ? 0 : h.getWhiteElo();
    int blackElo = h.getBlackElo() == null ? 0 : h.getBlackElo();
    int[] material = FinalMaterial.ordered(lastPosition(moves));
    Date date = h.getDate() == null ? Date.unset() : h.getDate();

    GameHeader record =
        new GameHeader(
            id,
            false,
            0,
            0,
            whiteId,
            blackId,
            tournamentId,
            annotatorId,
            sourceId,
            whiteTeamId,
            blackTeamId,
            gameTagId,
            result.ordinal(),
            result == GameResult.NOT_FINISHED && lineEvaluation != null ? lineEvaluation.ordinal() : 0,
            h.getRound() == null ? 0 : h.getRound(),
            h.getSubRound() == null ? 0 : h.getSubRound(),
            h.getBoard() == null ? 0 : h.getBoard(),
            whiteElo,
            whiteElo > 0 ? ratingType(previous, true) : RatingType.NONE,
            blackElo,
            blackElo > 0 ? ratingType(previous, false) : RatingType.NONE,
            chess960 ? EcoField.ofChess960(sp) : EcoField.of(eco),
            Medal.encode(statistics(moves).getMedals()),
            flags(moves),
            magnitudes(moves),
            moveCount(moves),
            material[0],
            material[1],
            previous == null ? nextCreationTimestamp() : previous.creationTimestamp(),
            previous == null ? 0 : Timestamps.lastChanged(Instant.now()),
            0,
            0,
            previous == null ? 1 : previous.version() + 1,
            Dates.encode(date),
            previous == null ? null : previous.raw());
    pending.put(
        id,
        new Pending(
            record,
            new RecordFile.Record(encoded.tag(), encoded.content(), 0),
            annotations));
  }

  private static RatingType ratingType(@Nullable GameHeader previous, boolean white) {
    if (previous != null) {
      RatingType kept = white ? previous.whiteRating() : previous.blackRating();
      if (!kept.equals(RatingType.NONE)) {
        return kept;
      }
    }
    return RatingType.FIDE;
  }

  /**
   * Adds a guiding text.
   *
   * @return the id it will have
   */
  public int addText(@NotNull TextModel text) {
    ensureOpen();
    int id = count + 1;
    putText(id, null, text);
    count = id;
    return id;
  }

  /**
   * Replaces a guiding text.
   *
   * @throws IllegalArgumentException if there is no guiding text with that id
   */
  public void replaceText(int id, @NotNull TextModel text) {
    ensureOpen();
    if (!(record(id) instanceof TextHeader previous)) {
      throw new IllegalArgumentException("Record " + id + " is not a guiding text");
    }
    putText(id, previous, text);
  }

  private void putText(int id, @Nullable TextHeader previous, TextModel text) {
    TextHeaderModel h = text.header();
    long tournamentId =
        entityId(
            EntityType.TOURNAMENT,
            null,
            () -> Tournament.of(h.tournament(), "", Dates.encode(h.tournamentDate())));
    long sourceId = entityId(EntityType.SOURCE, null, () -> Source.of(h.source()));
    long annotatorId = entityId(EntityType.PLAYER, null, () -> Player.ofFullName(h.annotator()));
    long titleId = entityId(EntityType.GAME_TAG, null, () -> textTitle(text.contents()));
    TextHeader record =
        new TextHeader(
            id,
            false,
            0,
            tournamentId,
            sourceId,
            annotatorId,
            titleId,
            previous == null ? nextCreationTimestamp() : previous.creationTimestamp(),
            previous == null ? 0 : previous.mediaOffset(),
            previous == null ? 1 : previous.version() + 1,
            previous == null ? null : previous.raw());
    byte[] body = GuidingTextCodec.encode(text.contents());
    pending.put(id, new Pending(record, new RecordFile.Record(RecordFile.TAG_TEXT, body, 0), null));
  }

  /** The title entity of a text: its title in each language, with every offered language listed. */
  private static GameTag textTitle(TextContentsModel contents) {
    TreeMap<Integer, String> titles = new TreeMap<>();
    for (int language : GameTag.OFFERED_LANGUAGES) {
      titles.put(language, "");
    }
    for (Map.Entry<TextLanguage, String> e : contents.titles().entrySet()) {
      titles.put(GuidingTextCodec.code(e.getKey()), e.getValue());
    }
    List<GameTag.Title> list = new ArrayList<>();
    titles.forEach((language, title) -> list.add(new GameTag.Title(language, title)));
    return new GameTag(list);
  }

  // ── Entities ─────────────────────────────────────────────────────────────

  /**
   * Changes the fields of an existing entity. Which records refer to it is unchanged.
   *
   * @throws IllegalArgumentException if there is no such entity, or another one equal to it
   *     exists
   */
  public void updateEntity(long id, @NotNull Entity entity) {
    ensureOpen();
    EntityType type = entity.type();
    Entity old = entity(type, id);
    if (old == null) {
      throw new IllegalArgumentException("No " + type + " with id " + id);
    }
    Integer other = findEntity(entity);
    if (other != null && other != id) {
      throw new IllegalArgumentException(
          "Cannot update " + type + " " + id + ": another one with the same fields exists");
    }
    if (entity.encode().length > database().entityFile().containerSize(type)) {
      throw new IllegalArgumentException("The " + type + " is too large to be stored");
    }
    if (newEntities.getOrDefault(type, new LinkedHashMap<>()).containsKey((int) id)) {
      newIdentities.get(type).remove(EntityLookup.identity(old));
      newEntities.get(type).put((int) id, entity);
      newIdentities.get(type).put(EntityLookup.identity(entity), (int) id);
    }
    changedEntities.computeIfAbsent(type, t -> new HashMap<>()).put((int) id, entity);
  }

  /** An existing or pending entity equal to this one, or null. */
  private @Nullable Integer findEntity(Entity entity) {
    Integer id = newIdentities.getOrDefault(entity.type(), Map.of()).get(EntityLookup.identity(entity));
    if (id != null) {
      return id;
    }
    id = database().entityLookup().find(entity);
    // An entity changed in this transaction is found by its new fields only
    if (id != null && changedEntities.getOrDefault(entity.type(), Map.of()).containsKey(id)) {
      Entity changed = changedEntities.get(entity.type()).get(id);
      if (!EntityLookup.identity(changed).equals(EntityLookup.identity(entity))) {
        return null;
      }
    }
    return id;
  }

  /**
   * The id of the entity a header field refers to: the bound id, which must exist, or else an
   * equal entity, created if there is none.
   */
  private long entityId(EntityType type, @Nullable Long bound, Supplier<Entity> entity) {
    if (bound != null) {
      if (entity(type, bound) == null) {
        throw new IllegalArgumentException("The game refers to " + type + " " + bound + ", which doesn't exist");
      }
      return bound;
    }
    Entity e = entity.get();
    Integer id = findEntity(e);
    if (id != null) {
      return id;
    }
    return createEntity(e);
  }

  private long teamId(@Nullable Long bound, @Nullable String title) {
    if (bound == null && text(title).isEmpty()) {
      return -1;
    }
    return entityId(EntityType.TEAM, bound, () -> Team.of(text(title)));
  }

  private int createEntity(Entity entity) {
    EntityType type = entity.type();
    if (entity.encode().length > database().entityFile().containerSize(type)) {
      throw new IllegalArgumentException("The " + type + " is too large to be stored: " + entity);
    }
    LinkedHashMap<Integer, Entity> created = newEntities.computeIfAbsent(type, t -> new LinkedHashMap<>());
    // Entities are added at commit in this order, reusing deleted ids first
    List<Integer> free = database().entityFile().freeIds(type);
    int k = created.size();
    int id = k < free.size() ? free.get(k) : database().entityFile().count(type) + (k - free.size());
    created.put(id, entity);
    changedEntities.computeIfAbsent(type, t -> new HashMap<>()).put(id, entity);
    newIdentities.computeIfAbsent(type, t -> new HashMap<>()).put(EntityLookup.identity(entity), id);
    return id;
  }

  private static Tournament tournament(GameHeaderModel h) {
    int type = h.getEventType() == null ? 0 : TournamentType.fromName(h.getEventType()).ordinal();
    if (h.getEventTimeControl() != null) {
      type |=
          switch (TournamentTimeControl.fromName(h.getEventTimeControl())) {
            case BLITZ -> 0x20;
            case RAPID -> 0x40;
            case CORRESPONDENCE -> 0x80;
            case NORMAL -> 0;
          };
    }
    Tournament base = Tournament.of(text(h.getEvent()), text(h.getEventSite()), date(h.getEventDate()));
    return new Tournament(
        base.place(),
        base.title(),
        base.startDate(),
        type,
        0,
        h.getEventCountry() == null ? 0 : Nation.fromIOC(h.getEventCountry()).ordinal(),
        0,
        h.getEventCategory() == null ? 0 : h.getEventCategory(),
        0,
        h.getEventRounds() == null ? 0 : h.getEventRounds(),
        0,
        0f,
        0f,
        0,
        base.unknown21(),
        List.of(),
        date(h.getEventEndDate()),
        base.trailing());
  }

  private static int date(@Nullable Date date) {
    return date == null ? 0 : Dates.encode(date);
  }

  private static String text(@Nullable String s) {
    return s == null ? "" : s.strip();
  }

  // ── Header fields computed from the moves ────────────────────────────────

  private static Position lastPosition(GameMovesModel moves) {
    GameMovesModel.Node node = moves.root();
    while (node.hasMoves()) {
      node = node.mainNode();
    }
    return node.position();
  }

  /** The number of moves in the main line; a first move by black counts as a move. */
  private static int moveCount(GameMovesModel moves) {
    int plies = moves.countPly(false);
    boolean blackFirst = !Chess.isWhitePly(moves.root().ply());
    return plies == 0 ? 0 : (plies + (blackFirst ? 1 : 0) + 1) / 2;
  }

  private static AnnotationStatistics statistics(GameMovesModel moves) {
    AnnotationStatistics stats = new AnnotationStatistics();
    for (GameMovesModel.Node node : moves.getAllNodes()) {
      for (Annotation annotation : node.getAnnotations()) {
        if (annotation instanceof StatisticalAnnotation s) {
          s.updateStatistics(stats);
        }
      }
    }
    return stats;
  }

  private static int flags(GameMovesModel moves) {
    EnumSet<GameHeaderFlags> flags = EnumSet.noneOf(GameHeaderFlags.class);
    flags.addAll(statistics(moves).getFlags());
    if (moves.countPly(true) > moves.countPly(false)) {
      flags.add(GameHeaderFlags.VARIATIONS);
    }
    if (moves.isSetupPosition() && moves.root().position().isRegularChess()) {
      flags.add(GameHeaderFlags.SETUP_POSITION);
    }
    if (!moves.root().position().isRegularChess()) {
      flags.add(GameHeaderFlags.UNORTHODOX);
      if (moves.root().ply() != 0
          || !moves.root().position().equals(Chess960.getStartPosition(moves.root().position().chess960StartPosition()))) {
        flags.add(GameHeaderFlags.SETUP_POSITION);
      }
    }
    return GameHeaderFlags.encodeFlags(flags);
  }

  /**
   * The annotation magnitudes: whether there are many of a kind of annotation. What counts is the
   * number of annotations, and for commentary the bytes of text. The variation magnitude is left
   * 0, as ChessBase does.
   */
  private static int magnitudes(GameMovesModel moves) {
    int commentary = 0, symbols = 0, squares = 0, arrows = 0, timeSpent = 0, training = 0;
    for (GameMovesModel.Node node : moves.getAllNodes()) {
      for (Annotation a : node.getAnnotations()) {
        switch (a) {
          case TextAfterMoveAnnotation t -> commentary += t.text().getBytes(StandardCharsets.UTF_8).length;
          case TextBeforeMoveAnnotation t -> commentary += t.text().getBytes(StandardCharsets.UTF_8).length;
          case SymbolAnnotation s -> symbols++;
          case GraphicalSquaresAnnotation s -> squares++;
          case GraphicalArrowsAnnotation s -> arrows++;
          case TimeSpentAnnotation s -> timeSpent++;
          case TrainingAnnotation s -> training++;
          default -> {}
        }
      }
    }
    int m = 0;
    if (commentary > 200) m |= MAG_COMMENTARY;
    if (symbols >= 10) m |= MAG_SYMBOLS;
    if (squares >= 10) m |= MAG_SQUARES;
    if (arrows >= 6) m |= MAG_ARROWS;
    if (timeSpent >= 10) m |= MAG_TIME_SPENT;
    if (training >= 6) m |= MAG_TRAINING;
    return m;
  }

  private static synchronized long nextCreationTimestamp() {
    long timestamp = Timestamps.creation(Instant.now());
    if (timestamp <= lastCreationTimestamp) {
      timestamp = lastCreationTimestamp + 1;
    }
    lastCreationTimestamp = timestamp;
    return timestamp;
  }

  // ── Commit ───────────────────────────────────────────────────────────────

  /**
   * Applies the changes to the database.
   *
   * @throws IllegalStateException if the database changed since the transaction began
   */
  public void commit() {
    ensureOpen();
    Database2Cbh db = database();
    db.context().acquire(DatabaseLocks.Lock.WRITE);
    try {
      if (db.context().version() != version) {
        throw new IllegalStateException("The database has changed since the transaction started");
      }
      GameHeaderFile headers = db.gameHeaderFile();
      int committedCount = headers.count();

      // What the records referred to before, and will refer to after. The order is that of the
      // records and the roles in them, which decides the order the entities enter the sort orders
      // and the blocks the lists take, and so the same changes give the same files every time
      Map<Ref, List<Integer>> removed = new LinkedHashMap<>(), added = new LinkedHashMap<>();
      for (Pending p : pending.values()) {
        int id = p.record().id();
        if (id <= committedCount) {
          for (Ref ref : refs(headers.get(id))) {
            removed.computeIfAbsent(ref, r -> new ArrayList<>()).add(id);
          }
        }
        for (Ref ref : refs(p.record())) {
          added.computeIfAbsent(ref, r -> new ArrayList<>()).add(id);
        }
      }

      // New entities first, in the order the transaction gave them ids, then changed ones
      EntityFile entities = db.entityFile();
      Set<Ref> resorted = new LinkedHashSet<>();
      for (Map.Entry<EntityType, LinkedHashMap<Integer, Entity>> e : newEntities.entrySet()) {
        for (Map.Entry<Integer, Entity> created : e.getValue().entrySet()) {
          int id = entities.add(created.getValue());
          if (id != created.getKey()) {
            throw new IllegalStateException(
                "A new " + e.getKey() + " got id " + id + ", not " + created.getKey());
          }
          db.entityLookup().added(id, created.getValue());
        }
      }
      for (Map.Entry<EntityType, Map<Integer, Entity>> e : changedEntities.entrySet()) {
        for (Map.Entry<Integer, Entity> changed : e.getValue().entrySet()) {
          int id = changed.getKey();
          if (newEntities.getOrDefault(e.getKey(), new LinkedHashMap<>()).containsKey(id)) {
            continue;
          }
          Entity old = entities.get(e.getKey(), id);
          entities.put(id, changed.getValue());
          if (old != null) {
            db.entityLookup().removed(id, old);
          }
          db.entityLookup().added(id, changed.getValue());
          resorted.add(new Ref(e.getKey(), id, ROLES_OF_TYPE.get(e.getKey()).getFirst()));
        }
      }

      // The moves and annotations of replaced records, which may move the records after them,
      // then those of new records, appended
      RecordPlacement moves = new RecordPlacement(db.moveFile(), headers, false);
      RecordPlacement annotations = new RecordPlacement(db.annotationFile(), headers, true);
      for (Pending p : pending.headMap(committedCount, true).values()) {
        int id = p.record().id();
        moves.replace(id, p.moves().tag(), p.moves().content());
        if (p.annotations() != null) {
          annotations.replace(id, RecordFile.TAG_ANNOTATIONS, p.annotations());
        }
      }
      for (Pending p : pending.headMap(committedCount, true).values()) {
        GameRecord current = headers.get(p.record().id());
        headers.put(
            p.record().withMovesOffset(current.movesOffset()).withAnnotationOffset(current.annotationOffset()));
      }
      for (Pending p : pending.tailMap(committedCount, false).values()) {
        boolean text = p.record() instanceof TextHeader;
        long movesOffset =
            moves.append(p.moves().tag(), p.moves().content(), text ? RecordPlacement.TEXT_SPARE : RecordPlacement.GAME_SPARE);
        long annotationOffset =
            p.annotations() == null
                ? 0
                : annotations.append(RecordFile.TAG_ANNOTATIONS, p.annotations(), RecordPlacement.GAME_SPARE);
        headers.append(p.record().withMovesOffset(movesOffset).withAnnotationOffset(annotationOffset));
      }
      if (committedCount == 0 && headers.count() > 0) {
        // The move file's header says whether the database has games
        db.moveFile().setHeaderBytes(0, GameHeaderFile.VERSION);
      }

      updateIndexes(removed, added, resorted);

      db.context().bumpVersion();
      reset();
    } finally {
      db.context().release(DatabaseLocks.Lock.WRITE);
    }
  }

  /** Discards the changes. The transaction stays open. */
  public void rollback() {
    ensureOpen();
    reset();
  }

  private static List<Ref> refs(GameRecord record) {
    List<Ref> refs = new ArrayList<>();
    switch (record) {
      case GameHeader g -> {
        refs.add(new Ref(EntityType.PLAYER, g.whiteId(), Role.PLAYER));
        refs.add(new Ref(EntityType.PLAYER, g.blackId(), Role.PLAYER));
        refs.add(new Ref(EntityType.TOURNAMENT, g.tournamentId(), Role.TOURNAMENT));
        refs.add(new Ref(EntityType.SOURCE, g.sourceId(), Role.SOURCE));
        refs.add(new Ref(EntityType.PLAYER, g.annotatorId(), Role.ANNOTATOR));
        refs.add(new Ref(EntityType.TEAM, g.whiteTeamId(), Role.TEAM));
        refs.add(new Ref(EntityType.TEAM, g.blackTeamId(), Role.TEAM));
        refs.add(new Ref(EntityType.GAME_TAG, g.gameTagId(), Role.GAME_TAG));
      }
      case TextHeader t -> {
        refs.add(new Ref(EntityType.TOURNAMENT, t.tournamentId(), Role.TOURNAMENT));
        refs.add(new Ref(EntityType.SOURCE, t.sourceId(), Role.SOURCE));
        refs.add(new Ref(EntityType.PLAYER, t.annotatorId(), Role.ANNOTATOR));
        refs.add(new Ref(EntityType.GAME_TAG, t.titleId(), Role.TEXT_TITLE));
      }
      case AnalysisHeader a -> {
        refs.add(new Ref(EntityType.SOURCE, a.sourceId(), Role.SOURCE));
        refs.add(new Ref(EntityType.PLAYER, a.annotatorId(), Role.ANNOTATOR));
        refs.add(new Ref(EntityType.GAME_TAG, a.titleId(), Role.ANALYSIS_TITLE));
      }
    }
    refs.removeIf(r -> r.id() < 0);
    return refs;
  }

  private static final Map<Role, StandardIndex> INDEX_OF_ROLE =
      Map.of(
          Role.PLAYER, StandardIndex.PLAYERS,
          Role.TOURNAMENT, StandardIndex.TOURNAMENTS,
          Role.SOURCE, StandardIndex.SOURCES,
          Role.ANNOTATOR, StandardIndex.ANNOTATORS,
          Role.TEAM, StandardIndex.TEAMS,
          Role.GAME_TAG, StandardIndex.GAME_TAGS,
          Role.TEXT_TITLE, StandardIndex.TEXT_TITLES,
          Role.ANALYSIS_TITLE, StandardIndex.ANALYSIS_TITLES);

  private static final Map<EntityType, List<Role>> ROLES_OF_TYPE =
      Map.of(
          EntityType.PLAYER, List.of(Role.PLAYER, Role.ANNOTATOR),
          EntityType.TOURNAMENT, List.of(Role.TOURNAMENT),
          EntityType.SOURCE, List.of(Role.SOURCE),
          EntityType.TEAM, List.of(Role.TEAM),
          EntityType.GAME_TAG, List.of(Role.GAME_TAG, Role.TEXT_TITLE, Role.ANALYSIS_TITLE));

  /**
   * Updates the game lists and sort orders of the entities whose references changed, re-sorts
   * the entities whose fields changed, and deletes the entities nothing refers to any more.
   */
  private void updateIndexes(Map<Ref, List<Integer>> removed, Map<Ref, List<Integer>> added, Set<Ref> resorted) {
    Database2Cbh db = database();
    GameListFile lists = db.gameListFile();
    SortIndexFile sort = db.sortIndexFile();
    EntityFile entities = db.entityFile();
    Set<Ref> touched = new LinkedHashSet<>(removed.keySet());
    touched.addAll(added.keySet());

    // Entities whose fields changed leave their sort orders, to come back in their new place
    Map<StandardIndex, Set<Long>> reinsert = new EnumMap<>(StandardIndex.class);
    for (Ref ref : resorted) {
      for (Role role : ROLES_OF_TYPE.get(ref.type())) {
        StandardIndex standard = INDEX_OF_ROLE.get(role);
        SortIndexFile.Index index = sort.index(standard);
        if (index.contains(ref.id())) {
          index.delete(ref.id());
          reinsert.computeIfAbsent(standard, s -> new HashSet<>()).add(ref.id());
        }
      }
    }

    Map<EntityType, Set<Long>> candidatesForDeletion = new EnumMap<>(EntityType.class);
    for (Ref ref : touched) {
      List<Integer> games = new ArrayList<>(lists.games((int) ref.id(), ref.role()));
      int before = games.size();
      for (Integer id : removed.getOrDefault(ref, List.of())) {
        games.remove(id);
      }
      games.addAll(added.getOrDefault(ref, List.of()));
      games.sort(null);
      lists.setGames((int) ref.id(), ref.role(), games);

      SortIndexFile.Index index = sort.index(INDEX_OF_ROLE.get(ref.role()));
      Set<Long> toReinsert = reinsert.get(INDEX_OF_ROLE.get(ref.role()));
      boolean resort = toReinsert != null && toReinsert.remove(ref.id());
      if (!games.isEmpty() && (before == 0 || resort) && !index.contains(ref.id())) {
        insert(index, ref.type(), ref.id());
      } else if (games.isEmpty() && index.contains(ref.id())) {
        index.delete(ref.id());
      }
      if (games.isEmpty()) {
        candidatesForDeletion.computeIfAbsent(ref.type(), t -> new HashSet<>()).add(ref.id());
      }
    }
    for (Map.Entry<StandardIndex, Set<Long>> e : reinsert.entrySet()) {
      SortIndexFile.Index index = sort.index(e.getKey());
      for (long id : e.getValue()) {
        insert(index, e.getKey().type(), id);
      }
    }

    // An entity nothing refers to in any role is deleted
    for (Map.Entry<EntityType, Set<Long>> e : candidatesForDeletion.entrySet()) {
      for (long id : e.getValue()) {
        boolean referred = false;
        for (Role role : ROLES_OF_TYPE.get(e.getKey())) {
          referred |= lists.count((int) id, role) > 0;
        }
        Entity entity = entities.get(e.getKey(), (int) id);
        if (!referred && entity != null) {
          entities.delete(e.getKey(), (int) id);
          db.entityLookup().removed((int) id, entity);
        }
      }
    }
  }

  private void insert(SortIndexFile.Index index, EntityType type, long id) {
    EntityFile entities = database().entityFile();
    Entity entity = entities.get(type, (int) id);
    if (entity == null) {
      log.warn("Can't sort the missing {} {}", type, id);
      return;
    }
    index.insert(id, EntityOrder.key(entity), EntityOrder.nodeComparator(i -> entities.get(type, i)));
  }

  @Override
  public void close() {
    if (!isClosed() && hasUncommittedChanges()) {
      log.debug("Closing a write transaction with uncommitted changes, which are discarded");
    }
    super.close();
  }
}
