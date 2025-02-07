package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
// import java.util.Scanner;
import java.util.List;

public class Lox {

    static boolean hadError = false;
    static boolean hadRuntimeError = false;
    private static final Interpreter interpreter = new Interpreter();


    public static void main(String[] args) throws IOException {

        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }


    public static void run(String line) {
//        new Scanner(line).tokens().forEach(System.out::println);
        Scanner scanner = new Scanner(line);
        List<Token> tokens = scanner.scanTokens();

        // For now, just print the tokens.
        // for (Token token : tokens) {
        //     System.out.println(token);
        // }

        // try to parse tokens into AST Expr
        Parser parser = new Parser(tokens);
        Expr expression = parser.parse();
        
        // stop on syntax error
        if (hadError) return;
        
        System.out.println(new AstPrinter().print(expression));
        interpreter.interpret(expression);
    }


    public static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of(path));
        run(new String(bytes, Charset.defaultCharset()));

        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    public static void runPrompt() throws IOException {

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        for (; ; ) {
            System.out.println("> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line);

            // If the user makes a mistake, it shouldn’t kill their entire session.
            hadError = true;
        }
    }

    /**
     * report error message.
     * Various phases of the front end will detect errors,
     * but it’s not really their job to know how to present that to a user.
     * In a full-featured language implementation,
     * you will likely have multiple ways errors get displayed:
     * on stderr, in an IDE’s error window, logged to a file, etc.
     * You don’t want that code smeared all over your scanner and parser.
     *
     * @param line    code line that generates error
     * @param message error message that's helpful for user to fix it.
     */
    static void error(int line, String message) {
        report(line, "", message);
    }

    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
          report(token.line, " at end", message);
        } else {
          report(token.line, " at '" + token.lexeme + "'", message);
        }
      }

    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }

    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() +
            "\n[line " + error.token.line + "]");
        hadRuntimeError = true;
      }
}

