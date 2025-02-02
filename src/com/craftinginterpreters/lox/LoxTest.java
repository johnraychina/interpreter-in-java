package com.craftinginterpreters.lox;

public class LoxTest {

    public static void main(String[] args) {
        LoxTest test = new LoxTest();
        test.testRun();
        test.testRunBraces();
    }
    
    public void testRunBraces() {
        Lox.run("(( )){}");
    }

    public void testRun() {
        // 正常情况测试
        String line = "print 123";
        Lox.run(line);

        // // 空行测试
        // line = "";
        // Lox.run(line);

        // // 边界情况测试
        // line = "   ";
        // Lox.run(line);
    }
}
