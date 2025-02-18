package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;
import java.util.ArrayList;
import java.util.List;

// top-down operator precedence parsing
// the most top level has the lowest precedence
// the most bottom level has the highest precedence
////program        → statement* EOF ;
//
// program        → declaration* EOF ;
//
// declaration    → varDecl
//                | statement ;
//
// varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;
// 
// statement      → exprStmt
//                | printStmt ;
//                | block

// exprStmt       → expression ";" ;
// printStmt      → "print" expression ";" ;
// block          → "{" declaration* "}" ;
//
//// expression     → equality ;
// expression     → assignment ;
// assignment     → IDENTIFIER "=" assignment
//                | equality ;
//
// equality       → comparison ( ( "!=" | "==" ) comparison )* ;
// comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
// term           → factor ( ( "-" | "+" ) factor )* ;
// factor         → unary ( ( "/" | "*" ) unary )* ;
// unary          → ( "!" | "-" ) unary
//                | primary ;
// primary        → NUMBER | STRING | "true" | "false" | "nil"
//                | "(" expression ")"
//                | IDENTIFIER ;

public class Parser {

    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt declaration() {
        try {

            if (match(VAR)) {
                return varDeclaration();
            }
            return statement();

        } catch (Exception e) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if (match(PRINT)) {
            return printStatement();
        }
        if (match(LEFT_BRACE)) {
            return new Stmt.Block(block());
        }

        return expressionStatement();
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
          statements.add(declaration());
        }
    
        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }
    
    private Stmt printStatement() {
        Expr val = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(val);
    }

    private Stmt expressionStatement() {
        Expr val = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Expression(val);
    }

    private Expr expression() {
        // return equality();

        // We want the syntax tree to reflect that an l-value isn’t evaluated like a
        // normal expression.
        // That’s why the Expr.Assign node has a Token for the left-hand side, not an
        // Expr.
        // The problem is that the parser doesn’t know it’s parsing an l-value until it
        // hits the =.
        // In a complex l-value, that may occur many tokens later.
        // makeList().head.next = node;
        // We have only a single token of lookahead, so what do we do?
        return assignment();
    }

    private Expr assignment() {

        // expr: makeList().head.next
        Expr expr = equality();

        if (match(EQUAL)) {
            Token equals = previous();

            // One slight difference from binary operators is that we don’t loop to build up
            // a sequence of the same operator.
            // Since assignment is right-associative, we instead recursively call
            // assignment() to parse the right-hand side.
            Expr value = assignment();

            // The trick is that right before we create the assignment expression node,
            // we look at the left-hand side expression and figure out what kind of
            // assignment target it is.
            // We convert the r-value expression node into an l-value representation.
            // This conversion works because it turns out that every valid assignment target
            // happens to also be valid syntax as a normal expression.
            // This means we can parse the left-hand side as if it were an expression
            // and then after the fact produce a syntax tree that turns it into an
            // assignment target.
            // If the left-hand side expression isn’t a valid assignment target, we fail
            // with a syntax error.
            // That ensures we report an error on code like this: a + b = c;
            if (expr instanceof Expr.Variable v) {
                Token name = v.name;
                return new Expr.Assign(name, value);
            }

            // We report an error if the left-hand side isn’t a valid assignment target,
            // but we don’t throw it because the parser isn’t in a confused state where we
            // need to go into panic mode and synchronize.
            error(equals, "Invalid assignment target.");
        }
        return expr;
    }

    private Expr equality() {
        // left part
        Expr expr = comparison();

        // right part
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private boolean match(TokenType... types) {
        for (TokenType tkType : types) {
            if (check(tkType)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) {
            return false;
        }
        return peek().type == type;
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        // In order of precedence, first addition and subtraction:
        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        // And finally, multiplication and division:
        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return primary();
    }

    private Expr primary() {
        // the highest level of precedence, primary expressions
        if (match(FALSE)) {
            return new Expr.Literal(false);
        }
        if (match(TRUE)) {
            return new Expr.Literal(true);
        }
        if (match(NIL)) {
            return new Expr.Literal(null);
        }

        if (match(STRING, NUMBER)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        throw error(peek(), "Expect expression.");
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {

            if (previous().type == SEMICOLON) {
                return;
            }

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

}
