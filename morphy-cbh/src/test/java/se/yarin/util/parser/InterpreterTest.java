package se.yarin.util.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InterpreterTest {

    @Test
    public void integerEvaluation() {
        assertEquals(1, evaluate("1"));

        assertEquals(8, evaluate("5+3"));
        assertEquals(17/3, evaluate("17/3"));
        assertEquals(-9*11, evaluate("-9*11"));
        assertEquals(32-157, evaluate("32-157"));

        assertEquals(1+2*3-4+6*7*8+9-10, evaluate("1+2*3-4+6*7*8+9-10"));
        assertEquals(1+2+3+4-5-6+7+8-9, evaluate("1+2+3+4-5-6+7+8-9"));

        assertEquals((5+3)*2, evaluate("(5+3)*2"));
        assertEquals((5+3)*-(12+3)-(9*32), evaluate("(5+3)*-(12+3)-(9*32)"));

        assertEquals(2*3*4*8/6*2*9*13/7, evaluate("2*3*4*8/6*2*9*13/7"));

        assertEquals(10, evaluate("1+2+3+4"));
        assertEquals(2*3*4*5, evaluate("2*3*4*5"));
    }

    @Test
    public void booleanEvaluation() {
        assertEquals(true, evaluate("true"));
        assertEquals(false, evaluate("false"));

        assertEquals(false, evaluate("false||false"));
        assertEquals(true, evaluate("false||true"));
        assertEquals(true, evaluate("true||false"));
        assertEquals(true, evaluate("false||false||true"));

        assertEquals(true, evaluate("true&&true"));
        assertEquals(false, evaluate("false&&true"));
        assertEquals(false, evaluate("true&&false"));
        assertEquals(false, evaluate("true&&true&&false"));
    }

    @Test
    public void comparisonEvaluation() {
        assertEquals(true, evaluate("5 > 3"));
        assertEquals(false, evaluate("-12 > 7"));
        assertEquals(true, evaluate("100 < 0200"));
        assertEquals(false, evaluate("100 < 100"));

        assertEquals(true, evaluate("100 <= 100"));
        assertEquals(false, evaluate("100 <= 99"));
        assertEquals(true, evaluate("59313 >= 99"));
        assertEquals(false, evaluate("-2 >= -1"));
    }

    @Test
    public void equalityEvaluation() {
        assertEquals(true, evaluate("5 ==5"));
        assertEquals(true, evaluate("-142 == -0142"));
        assertEquals(false, evaluate("93 == 92"));
        assertEquals(true, evaluate("true == true"));
        assertEquals(false, evaluate("true == false"));
        assertEquals(false, evaluate("true == 1"));

        assertEquals(true, evaluate("true != 1"));
        assertEquals(true, evaluate("5 != 3"));
        assertEquals(false, evaluate("1 != 01"));
        assertEquals(false, evaluate("19 != (23-4)"));
        assertEquals(false, evaluate("false != false"));
    }

    @Test
    public void bitwiseEvaluation() {
        assertEquals(1473&12983, evaluate("1473&12983"));
        assertEquals(-1943|1234, evaluate("-1943|1234"));
        assertEquals(123123^939823, evaluate("123123^939823"));
        assertEquals(~1, evaluate("~1"));
        assertEquals(~-3239, evaluate("~-3239"));
    }

    @Test
    public void unaryEvaluation() {
        assertEquals(false, evaluate("!true"));
        assertEquals(true, evaluate("!!true"));
        assertEquals(false, evaluate("!!(!true)"));

        assertEquals(-5, evaluate("---5"));
        assertEquals(91, evaluate("---(-91)"));
        assertEquals(111, evaluate("--111"));

        assertEquals(100, evaluate("~~100"));
    }

    @Test
    public void functionEvaluation() {
        byte[] data = {12, 7, 3, 100, 11, 19, 15, 11};

        assertEquals(100, evaluate("byte(3)", data));
        assertEquals(3*256+100, evaluate("shortb(2)", data));
        assertEquals(11*256+100, evaluate("shortl(3)", data));
        assertEquals(11*256*256*256+19*256*256+15*256+11, evaluate("intb(4)", data));
        assertEquals(11*256*256*256+100*256*256+3*256+7, evaluate( "intl(1)", data));
    }

    @Test
    public void complex() {
        assertEquals(true, evaluate("(73&1) > 0 && (-5+3==-2)"));
    }

    private Object evaluate(String expression) {
        return evaluate(expression, new byte[0]);
    }

    private Object evaluate(String expression, byte[] data) {
        Scanner scanner = new Scanner(expression);
        Parser parser = new Parser(scanner.scanTokens());
        Expr expr = parser.parse();

        Interpreter interpreter = new Interpreter(data);
        return interpreter.evaluate(expr);
    }
}
