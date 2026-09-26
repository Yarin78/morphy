package se.yarin.morphy.cb2;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameModel;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.cb2.entities.EntityFile;
import se.yarin.morphy.cb2.games.GameHeaderFile;
import se.yarin.morphy.cb2.indexes.GameListFile;
import se.yarin.morphy.cb2.indexes.SortIndexFile;
import se.yarin.morphy.cb2.storage.ByteStore;
import se.yarin.morphy.cb2.storage.MemoryByteStore;
import se.yarin.morphy.cb2.storage.RecordFile;
import se.yarin.morphy.chessbase.text.TextModel;

/**
 * A ChessBase v2 database: the {@code .2cbh} family of files. See format/v2.
 *
 * <p>This is the v2 engine. It owns the files, and all reading and writing goes through
 * transactions: a {@link ReadTransaction} sees the database as it was when it began, and a {@link
 * WriteTransaction} gathers changes and applies them all when committed. The vendor-neutral {@link
 * se.yarin.morphy.api.Database} over it is {@link Database2CbhFacade}.
 *
 * <p>A database must not be opened twice at the same time, in this process or another.
 */
public final class Database2Cbh implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(Database2Cbh.class);

  /** The extensions of the files making up a database; the first is the one it's opened by. */
  public static final List<String> EXTENSIONS =
      List.of(".2cbh", ".2cbg", ".2cba", ".2lid", ".2lgd", ".2lcd");

  private final @NotNull String name;
  private final boolean writable;
  private final @NotNull DatabaseContext context = new DatabaseContext();
  private final @NotNull GameHeaderFile games;
  private final @NotNull RecordFile moves;
  private final @NotNull RecordFile annotations;
  private final @NotNull EntityFile entities;
  private final @NotNull GameListFile gameLists;
  private final @NotNull SortIndexFile sortIndexes;

  private Database2Cbh(@NotNull String name, boolean writable, @NotNull ByteStore[] stores) {
    this.name = name;
    this.writable = writable;
    this.games = new GameHeaderFile(stores[0], name + ".2cbh");
    this.moves = new RecordFile(stores[1], name + ".2cbg");
    this.annotations = new RecordFile(stores[2], name + ".2cba");
    this.entities = new EntityFile(stores[3], name + ".2lid");
    this.gameLists = new GameListFile(stores[4], name + ".2lgd");
    this.sortIndexes = new SortIndexFile(stores[5], name + ".2lcd");
    if (games.version() != GameHeaderFile.VERSION) {
      log.warn("{} has format version {}, not {}", name, games.version(), GameHeaderFile.VERSION);
    }
  }

  /**
   * Opens a database.
   *
   * @param file the {@code .2cbh} file
   * @param mode how to open it; {@link AccessMode#IN_MEMORY} reads it into memory, where changes
   *     stay
   * @throws IOException if a file is missing or can't be opened
   * @throws InvalidDataException if a file doesn't follow the format
   */
  public static @NotNull Database2Cbh open(@NotNull File file, @NotNull AccessMode mode)
      throws IOException {
    requireExtension(file);
    ByteStore[] stores = new ByteStore[EXTENSIONS.size()];
    try {
      for (int i = 0; i < stores.length; i++) {
        File f = sibling(file, EXTENSIONS.get(i));
        if (!f.exists()) {
          throw new IOException("The database file " + f + " is missing");
        }
        stores[i] = ByteStore.open(f, mode);
      }
      return new Database2Cbh(baseName(file), mode != AccessMode.READ_ONLY, stores);
    } catch (IOException | RuntimeException e) {
      closeAll(stores);
      throw e;
    }
  }

  /**
   * Creates a new, empty database and opens it for reading and writing.
   *
   * @param file the {@code .2cbh} file; no file of the database may exist
   * @throws IOException if a file of the database exists or can't be created
   */
  public static @NotNull Database2Cbh create(@NotNull File file) throws IOException {
    requireExtension(file);
    for (String extension : EXTENSIONS) {
      File f = sibling(file, extension);
      if (f.exists()) {
        throw new IOException("Can't create " + file + " because " + f + " exists");
      }
    }
    ByteStore[] stores = new ByteStore[EXTENSIONS.size()];
    try {
      for (int i = 0; i < stores.length; i++) {
        stores[i] = ByteStore.open(sibling(file, EXTENSIONS.get(i)), AccessMode.READ_WRITE);
      }
      initialise(stores);
      return new Database2Cbh(baseName(file), true, stores);
    } catch (RuntimeException e) {
      closeAll(stores);
      throw e;
    }
  }

  /** Creates a new, empty database held in memory. */
  public static @NotNull Database2Cbh createInMemory(@NotNull String name) {
    ByteStore[] stores = new ByteStore[EXTENSIONS.size()];
    for (int i = 0; i < stores.length; i++) {
      stores[i] = new MemoryByteStore();
    }
    initialise(stores);
    return new Database2Cbh(name, true, stores);
  }

  private static void initialise(ByteStore[] stores) {
    GameHeaderFile.create(stores[0]);
    RecordFile.create(stores[1], 1, 0);
    RecordFile.create(stores[2], 0, 0);
    EntityFile.create(stores[3]);
    GameListFile.create(stores[4]);
    SortIndexFile.create(stores[5]);
  }

  /**
   * Deletes the files of a database.
   *
   * @param file the {@code .2cbh} file
   * @throws IOException if a file can't be deleted
   */
  public static void delete(@NotNull File file) throws IOException {
    requireExtension(file);
    List<File> existing = new ArrayList<>();
    for (String extension : EXTENSIONS) {
      File f = sibling(file, extension);
      if (f.exists()) {
        existing.add(f);
      }
    }
    File ini = sibling(file, ".ini");
    if (ini.exists()) {
      existing.add(ini);
    }
    for (File f : existing) {
      Files.delete(f.toPath());
    }
  }

  private static void requireExtension(File file) {
    if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".2cbh")) {
      throw new IllegalArgumentException("A v2 database is opened by its .2cbh file: " + file);
    }
  }

  private static File sibling(File file, String extension) {
    return new File(file.getAbsoluteFile().getParentFile(), baseName(file) + extension);
  }

  private static String baseName(File file) {
    String n = file.getName();
    return n.substring(0, n.length() - ".2cbh".length());
  }

  private static void closeAll(ByteStore[] stores) {
    for (ByteStore store : stores) {
      if (store != null) {
        try {
          store.close();
        } catch (RuntimeException e) {
          log.warn("Failed to close a database file", e);
        }
      }
    }
  }

  /** The name of the database, its file name without extension. */
  public @NotNull String name() {
    return name;
  }

  /** Whether games and entities may be written. */
  public boolean isWritable() {
    return writable;
  }

  public @NotNull DatabaseContext context() {
    return context;
  }

  /** The {@code .2cbh} file. Writing to it directly may leave the database inconsistent. */
  public @NotNull GameHeaderFile gameHeaderFile() {
    return games;
  }

  /** The {@code .2cbg} file. */
  public @NotNull RecordFile moveFile() {
    return moves;
  }

  /** The {@code .2cba} file. */
  public @NotNull RecordFile annotationFile() {
    return annotations;
  }

  /** The {@code .2lid} file. */
  public @NotNull EntityFile entityFile() {
    return entities;
  }

  /** The {@code .2lgd} file. */
  public @NotNull GameListFile gameListFile() {
    return gameLists;
  }

  /** The {@code .2lcd} file. */
  public @NotNull SortIndexFile sortIndexFile() {
    return sortIndexes;
  }

  /** The number of records: games, guiding texts and analyses, deleted ones included. */
  public int count() {
    return games.count();
  }

  /**
   * Gets a game.
   *
   * @throws IllegalArgumentException if there is no game with that id
   */
  public @NotNull Game getGame(int id) {
    try (ReadTransaction txn = new ReadTransaction(this)) {
      return txn.getGame(id);
    }
  }

  /** Adds a game, returning its id. */
  public int addGame(@NotNull GameModel game) {
    try (WriteTransaction txn = new WriteTransaction(this)) {
      int id = txn.addGame(game);
      txn.commit();
      return id;
    }
  }

  /** Replaces a game. */
  public void replaceGame(int id, @NotNull GameModel game) {
    try (WriteTransaction txn = new WriteTransaction(this)) {
      txn.replaceGame(id, game);
      txn.commit();
    }
  }

  /** Adds a guiding text, returning its id. */
  public int addText(@NotNull TextModel text) {
    try (WriteTransaction txn = new WriteTransaction(this)) {
      int id = txn.addText(text);
      txn.commit();
      return id;
    }
  }

  /** Replaces a guiding text. */
  public void replaceText(int id, @NotNull TextModel text) {
    try (WriteTransaction txn = new WriteTransaction(this)) {
      txn.replaceText(id, text);
      txn.commit();
    }
  }

  /** Writes all changes to disk. */
  public void flush() {
    games.store().flush();
    moves.store().flush();
    annotations.store().flush();
    entities.store().flush();
    gameLists.store().flush();
    sortIndexes.store().flush();
  }

  @Override
  public void close() {
    games.close();
    moves.close();
    annotations.close();
    entities.close();
    gameLists.close();
    sortIndexes.close();
  }
}
