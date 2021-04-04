package se.yarin.morphy.games;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.chess.GameResult;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static java.nio.file.StandardOpenOption.READ;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static se.yarin.morphy.storage.MorphyOpenOption.IGNORE_NON_CRITICAL_ERRORS;

public class GameHeaderIndexTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File gameHeaderFile;

    @Before
    public void setupEntityTest() throws IOException {
        gameHeaderFile = ResourceLoader.materializeStream(
                this.getClass().getResourceAsStream("cbh_test.cbh"),
                folder.newFile("temp.cbh"));
    }

    @Test
    public void createInMemoryIndex() {
        GameHeaderIndex base = new GameHeaderIndex();
        assertEquals(0, base.count());
    }

    @Test
    public void createIndex() throws IOException {
        File file = folder.newFile("newbase.cbh");
        file.delete();
        GameHeaderIndex index = GameHeaderIndex.create(file);
        assertEquals(0, index.count());
        index.close();
    }

    @Test
    public void getGameWithoutExtendedHeaderWrite() throws IOException {
        // cbj file is missing
        File cbh_only = ResourceLoader.materializeDatabaseStream(getClass(), "cbh_only");
        GameHeaderIndex index = GameHeaderIndex.open(cbh_only);
        assertEquals(4, index.count());

        GameHeader game1 = index.getGameHeader(1);
        assertEquals(0, game1.whitePlayerId());
        assertEquals(2200, game1.whiteElo());
        assertEquals(2, game1.round());
        assertEquals(1, game1.subRound());
        assertEquals(GameResult.DRAW, game1.result());
        assertEquals(-1, game1.extended().gameTagId());

        GameHeader game2 = index.getGameHeader(2);
        assertEquals(2820, game2.blackElo());
        assertEquals(-1, game2.extended().gameTagId());

        GameHeader game4 = index.getGameHeader(4);
        assertEquals(-1, game4.extended().whiteTeamId());
        assertEquals(Nation.NONE, game4.extended().whiteRatingType().nation());
    }

    @Test
    public void getGameWithoutExtendedHeaderReadOnly() throws IOException {
        File cbh_only = ResourceLoader.materializeDatabaseStream(getClass(), "cbh_only");
        GameHeaderIndex index = GameHeaderIndex.open(cbh_only, Set.of(READ));
        assertEquals(4, index.count());

        GameHeader game1 = index.getGameHeader(1);
        assertEquals(-1, game1.extended().gameTagId());

        GameHeader game4 = index.getGameHeader(4);
        assertEquals(-1, game4.extended().whiteTeamId());
        assertNull(game4.extended().whiteRatingType().nation());
    }

    @Test
    public void getGameWithExtendedHeader() throws IOException {
        // cbj file exists
        File cbh_cbj = ResourceLoader.materializeDatabaseStream(getClass(), "cbh_cbj_test");
        GameHeaderIndex index = GameHeaderIndex.open(cbh_cbj);
        assertEquals(4, index.count());

        GameHeader game1 = index.getGameHeader(1);
        assertEquals(0, game1.whitePlayerId());
        assertEquals(2200, game1.whiteElo());
        assertEquals(2, game1.round());
        assertEquals(1, game1.subRound());
        assertEquals(GameResult.DRAW, game1.result());
        assertEquals(0, game1.extended().gameTagId());

        GameHeader game2 = index.getGameHeader(2);
        assertEquals(2820, game2.blackElo());
        assertEquals(1, game2.extended().gameTagId());

        GameHeader game4 = index.getGameHeader(4);
        assertEquals(2, game4.extended().whiteTeamId());
        assertEquals(Nation.SWEDEN, game4.extended().whiteRatingType().nation());
    }

    @Test
    public void getGameWithShorterExtendedHeader() throws IOException {
        // cbj file exists but contains fewer games than the cbh file
        // Should work in non-strict mode
        File cbh_cbj = ResourceLoader.materializeDatabaseStream(getClass(), "shorter_cbj_test");
        GameHeaderIndex index = GameHeaderIndex.open(cbh_cbj, Set.of(READ, IGNORE_NON_CRITICAL_ERRORS));
        assertEquals(4, index.count());

        GameHeader game1 = index.getGameHeader(1);
        assertEquals(2200, game1.whiteElo());

        GameHeader game2 = index.getGameHeader(2);
        assertEquals(2820, game2.blackElo());
        assertEquals(1, game2.extended().gameTagId());

        GameHeader game4 = index.getGameHeader(4);  // missing from the cbj file
        assertEquals(-1, game4.extended().whiteTeamId());
        assertNull(game4.extended().whiteRatingType().nation());
    }

    @Test(expected = MorphyInvalidDataException.class)
    public void getGameWithShorterExtendedHeaderStrict() throws IOException {
        // cbj file exists but contains fewer games than the cbh file
        // Should fail to even open index
        File cbh_cbj = ResourceLoader.materializeDatabaseStream(getClass(), "shorter_cbj_test");
        GameHeaderIndex.open(cbh_cbj, Set.of(READ));
    }

    @Test
    public void getGameWithExtendedHeaderOldVersionReadOnly() throws IOException {
        // Older version of cbj file
        File hedgehog = ResourceLoader.materializeDatabaseStream(getClass(), "hedgehog_old");
        GameHeaderIndex index = GameHeaderIndex.open(hedgehog, Set.of(READ));
        assertEquals(8, index.getExtendedStorage().getStorageVersion());
        GameHeader game16 = index.getGameHeader(16);
        assertEquals(2555, game16.whiteElo());
        assertEquals(2, game16.extended().whiteTeamId());  // In cbj file
        assertEquals(-1, game16.extended().gameTagId());   // Not in this version of cbj, so default value
    }

    @Test
    public void getGameWithExtendedHeaderOldVersionWriteUpgrade() throws IOException {
        // Older version of cbj file, will get upgraded
        File hedgehog = ResourceLoader.materializeDatabaseStream(getClass(), "hedgehog_old");
        GameHeaderIndex index = GameHeaderIndex.open(hedgehog);
        assertEquals(ExtendedGameHeaderStorage.ExtProlog.DEFAULT_VERSION, index.getExtendedStorage().getStorageVersion());
        GameHeader game16 = index.getGameHeader(16);
        assertEquals(2555, game16.whiteElo());
        assertEquals(2, game16.extended().whiteTeamId());  // In cbj file
        assertEquals(-1, game16.extended().gameTagId());   // Not in this version of cbj, so default value
    }

    @Test
    public void getGameAfterOpenInMemoryWithNoExtendedHeaders() throws IOException {
        // cbj file is missing
        File cbh_only = ResourceLoader.materializeDatabaseStream(getClass(), "cbh_only");
        GameHeaderIndex index = GameHeaderIndex.openInMemory(cbh_only);
        assertEquals(4, index.count());

        GameHeader game1 = index.getGameHeader(1);
        assertEquals(2200, game1.whiteElo());
        assertEquals(-1, game1.extended().gameTagId());

        GameHeader game2 = index.getGameHeader(2);
        assertEquals(2820, game2.blackElo());
        assertEquals(-1, game2.extended().gameTagId());

        GameHeader game4 = index.getGameHeader(4);
        assertEquals(-1, game4.extended().whiteTeamId());
        assertEquals(Nation.NONE, game4.extended().whiteRatingType().nation());
    }

    @Test
    public void getGameAfterOpenInMemoryWithExtendedHeaders() throws IOException {
        // cbj file exists
        File cbh_cbj = ResourceLoader.materializeDatabaseStream(getClass(), "cbh_cbj_test");
        GameHeaderIndex index = GameHeaderIndex.openInMemory(cbh_cbj);
        assertEquals(4, index.count());

        GameHeader game1 = index.getGameHeader(1);
        assertEquals(0, game1.whitePlayerId());
        assertEquals(2200, game1.whiteElo());
        assertEquals(0, game1.extended().gameTagId());

        GameHeader game2 = index.getGameHeader(2);
        assertEquals(2820, game2.blackElo());
        assertEquals(1, game2.extended().gameTagId());

        GameHeader game4 = index.getGameHeader(4);
        assertEquals(2, game4.extended().whiteTeamId());
        assertEquals(Nation.SWEDEN, game4.extended().whiteRatingType().nation());
    }

    @Test
    public void getGameAfterOpenInMemoryWithShorterExtendedHeaders() throws IOException {
        // Should work in non-strict mode
        File cbh_cbj = ResourceLoader.materializeDatabaseStream(getClass(), "shorter_cbj_test");
        GameHeaderIndex index = GameHeaderIndex.openInMemory(cbh_cbj, Set.of(READ, IGNORE_NON_CRITICAL_ERRORS));
        assertEquals(4, index.count());

        GameHeader game1 = index.getGameHeader(1);
        assertEquals(2200, game1.whiteElo());

        GameHeader game2 = index.getGameHeader(2);
        assertEquals(2820, game2.blackElo());
        assertEquals(1, game2.extended().gameTagId());

        GameHeader game4 = index.getGameHeader(4);  // missing from the cbj file
        assertEquals(-1, game4.extended().whiteTeamId());
        assertNull(game4.extended().whiteRatingType().nation());
    }

    @Test(expected = MorphyInvalidDataException.class)
    public void getGameAfterOpenInMemoryWithShorterExtendedHeadersStrict() throws IOException {
        // Should fail
        File cbh_cbj = ResourceLoader.materializeDatabaseStream(getClass(), "shorter_cbj_test");
        GameHeaderIndex.openInMemory(cbh_cbj, Set.of(READ));
    }

    private ImmutableGameHeader emptyGame() {
        return ImmutableGameHeader.builder()
                .movesOffset(0)
                .whitePlayerId(0)
                .blackPlayerId(0)
                .tournamentId(0)
                .annotatorId(0)
                .sourceId(0)
                .noMoves(0)
                .build();
    }

    @Test
    public void testAddGameHeaderToFileIndex() throws IOException {
        GameHeaderIndex index = GameHeaderIndex.open(gameHeaderFile);
        int oldSize = index.count();

        GameHeader newGame = emptyGame().withWhiteElo(2100).withExtended(
                ImmutableExtendedGameHeader.builder().gameTagId(5).build());
        int id = index.add(newGame);
        assertEquals(oldSize + 1, id);
        assertEquals(oldSize + 1, index.count());
        GameHeader gameHeader = index.getGameHeader(id);
        assertEquals(id, gameHeader.id());
        assertEquals(2100, gameHeader.whiteElo());
        assertEquals(5, gameHeader.extended().gameTagId());

        assertEquals(oldSize + 2, index.add(emptyGame()));
        assertEquals(oldSize + 2, index.count());

        index.close();
    }

    @Test
    public void testAddGameHeaderToInMemoryIndex() throws IOException {
        GameHeaderIndex index = GameHeaderIndex.openInMemory(gameHeaderFile);
        int oldSize = index.count();

        GameHeader newGame = emptyGame().withWhiteElo(2100).withExtended(
                ImmutableExtendedGameHeader.builder().gameTagId(5).build());
        int id = index.add(newGame);
        assertEquals(oldSize + 1, id);
        assertEquals(oldSize + 1, index.count());
        GameHeader gameHeader = index.getGameHeader(id);
        assertEquals(id, gameHeader.id());
        assertEquals(2100, gameHeader.whiteElo());
        assertEquals(5, gameHeader.extended().gameTagId());

        assertEquals(oldSize + 2, index.add(emptyGame()));
        assertEquals(oldSize + 2, index.count());

        index.close();
    }

    @Test
    public void testUpdateGameHeader() throws IOException {
        GameHeaderIndex index = GameHeaderIndex.openInMemory(gameHeaderFile);
        int oldSize = index.count();

        GameHeader oldGame = index.getGameHeader(3);
        assertEquals(4, oldGame.whitePlayerId());
        assertEquals(1, oldGame.blackPlayerId());

        GameHeader newGame = ImmutableGameHeader.builder().whitePlayerId(2).blackPlayerId(3).build();
        index.update(3, newGame);

        newGame = index.getGameHeader(3);
        assertEquals(2, newGame.whitePlayerId());
        assertEquals(3, newGame.blackPlayerId());
        assertEquals(oldSize, index.count());

        index.close();
    }
/*

    @Test
    public void testIterable() throws IOException {
        GameHeaderIndex index = GameHeaderIndex.open(gameHeaderFile);
        Iterator<GameHeader> iterator = index.iterable().iterator();
        int id = 0;
        while (iterator.hasNext()) {
            assertEquals(++id, iterator.next().getId());
        }
        assertEquals(19, id);
        index.close();
    }

    @Test(expected = IllegalStateException.class)
    public void testIteratorWhileUpdating() throws IOException {
        GameHeaderIndex index = GameHeaderIndex.open(gameHeaderFile);
        Iterator<GameHeader> iterator = index.iterable().iterator();
        iterator.next();
        iterator.next();
        index.add(ImmutableGameHeader.builder().build());
        iterator.next();
    }

    @Test
    public void testSerializedGameHeaderFilter() throws IOException {
        File manyHeaderFile = ResourceLoader.materializeStream(
                this.getClass().getResourceAsStream("many_headers.cbh"),
                folder.newFile("many.cbh"));
        GameHeaderIndex headerBase = GameHeaderIndex.open(manyHeaderFile);
        // Create an iterator that will use the raw level filter to pick a handful of headers
        // The sparsity of this will cause some batches in the internal iterator to be empty
        List<Integer> lookupIds = Arrays.asList(1024, 5191, 5192, 5195, 5823, 9015);
        Stream<GameHeader> stream = headerBase.stream(1, serializedGameHeader -> {
            int id = ByteBufferUtil.getUnsigned24BitB(serializedGameHeader, 9); // id of white player offset
            return lookupIds.contains(id);
        });
        List<Integer> found = stream.map(GameHeader::getId).collect(Collectors.toList());
        assertEquals(lookupIds, found);
    }

 */
}