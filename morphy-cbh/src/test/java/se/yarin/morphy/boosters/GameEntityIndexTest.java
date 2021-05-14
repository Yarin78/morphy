package se.yarin.morphy.boosters;

import org.junit.Test;
import se.yarin.morphy.Database;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.validation.Validator;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static se.yarin.morphy.validation.Validator.Checks.*;

public class GameEntityIndexTest {

    @Test
    public void testReading() {
        Database db = ResourceLoader.openWorldChDatabase();

        new Validator().validate(db, EnumSet.of(ENTITY_DB_INTEGRITY,
                ENTITY_SORT_ORDER,
                ENTITY_STATISTICS,
                ENTITY_PLAYERS,
                ENTITY_TOURNAMENTS,
                ENTITY_ANNOTATORS,
                ENTITY_SOURCES,
                ENTITY_TEAMS,
                ENTITY_GAME_TAGS,
                GAME_ENTITY_INDEX), true, true, false);
    }

}
