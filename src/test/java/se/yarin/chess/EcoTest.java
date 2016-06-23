package se.yarin.chess;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EcoTest {

    @Test
    public void testInitEcoFromString() {
        assertEquals("A53", new Eco("A53").toString());
        assertEquals("B09", new Eco("B09").toString());
        assertEquals("C90", new Eco("C90").toString());
        assertEquals("D24", new Eco("D24").toString());
        assertEquals("E37", new Eco("E37").toString());
    }

    @Test
    public void testInitEcoFromInt() {
        assertEquals("B23", Eco.fromInt(123).toString());
        assertEquals("E99", Eco.fromInt(499).toString());
        assertEquals("A00", Eco.fromInt(0).toString());
    }

    @Test
    public void testUnsetEco() {
        assertEquals("???", Eco.unset().toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateInvalidEco1() {
        Eco.fromInt(500);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateInvalidEco2() {
        Eco.fromInt(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateInvalidEco3() {
        new Eco("A5");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateInvalidEco4() {
        new Eco("F03");
    }
}
