package se.yarin.cbhlib;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GameHeaderBaseTest {

    @Test
    public void createInMemoryBase() {
        GameHeaderBase base = new GameHeaderBase();
        assertEquals(1, base.getNextGameId());
    }
}
