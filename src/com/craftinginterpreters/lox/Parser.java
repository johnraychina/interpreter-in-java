package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// top-down operator precedence parsing
// the most top level has the lowest precedence
// the most bottom level has the highest precedence
////program        → statement* EOF ;
//
// program        → declaration* EOF ;
//
// declaration    → varDecl
//                | funDecl
//                | statement ;
//
// varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;
// 
// funDecl        → "fun" function ;
// function       → IDENTIFIER "(" parameters? ")" block ;
//
// # function parameters are just similar to arguments rule, 
// # except that each parameter is an identifier, not an expression. 
// parameters     → IDENTIFIER ( "," IDENTIFIER )* ;
// 
// statement      → exprStmt
//                | ifStmt
//                | printStmt
//                | whileStmt
//                | forStmt
//                | block;

// exprStmt       → expression ";" ;
// printStmt      → "print" expression ";" ;
// block          → "{" declaration* "}" ;
// ifStmt         → "if" "(" expression ")" statement
//                ( "else" statement )? ;
// whileStmt      → "while" "(" expression ")" statement ;
// forStmt        → "for" "(" ( varDecl | exprStmt | ";" )
//                expression? ";"
//                expression? ")"
//                statement ;
//
//
//// expression     → equality ;
// expression     → assignment ;
//// assignment     → IDENTIFIER "=" assignment
////                | equality ;
//
// assignment     → IDENTIFIER "=" assignment
//                | logic_or ;
// logic_or       → logic_and ( "or" logic_and )* ;
// logic_and      → equality ( "and" equality )* ;
//
// equality       → comparison ( ( "!=" | "==" ) comparison )* ;
// comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
// term           → factor ( ( "-" | "+" ) factor )* ;
// factor         → unary ( ( "/" | "*" ) unary )* ;
// unary          → ( "!" | "-" ) unary | call;
// call           → primary ( "(" arguments? ")" )* ;
// 
// arguments      → expression ( "," expression )* ;
//
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

            if (match(FUN)) {
                return function("function");
            }
            if (match(VAR)) {
                return varDeclaration();
            }
            return statement();

        } catch (Exception e) {
            synchronize();
            return null;
        }
    }

    private Stmt function(String kind) {
        consume(IDENTIFIER, "Expect " + kind + " name.");
        Token name = previous();
        consume(LEFT_PAREN, "Expect '(' after function name.");
        List<Token> parameters = new ArrayList<>();

        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters.");
                }

                parameters.add(
                        consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }

        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        // Consuming LEFT_BRACE here lets us report a more precise error message if the
        // { isn’t
        // found since we know it’s in the context of a function declaration.
        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();

        return new Stmt.Function(name, parameters, body);
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
        if (match(IF)) {
            return ifStatement();
        }
        if (match(PRINT)) {
            return printStatement();
        }
        if (match(WHILE)) {
            return whileStatement();
        }
        if (match(FOR)) {
            return forStatement();
        }

        if (match(LEFT_BRACE)) {
            return new Stmt.Block(block());
        }
        if (match(RETURN)) {
            return returnStatement();
        }

        return expressionStatement();
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");
        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");
        Stmt body = statement();

        // We’ve parsed all of the various pieces of the for loop
        // and the resulting AST nodes are sitting in a handful of Java local variables.
        // This is where the desugaring comes in.
        // We take those and use them to synthesize syntax tree nodes that express the
        // semantics of the for loop.
        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(
                    body,
                    new Stmt.Expression(increment))); // execute increment expression after body
        }

        // condition + while loop
        if (condition == null)
            condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        // initializer
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        // That’s it. Our interpreter now supports C-style for loops
        // and we didn’t have to touch the Interpreter class at all.
        // Since we desugared to nodes the interpreter already knows how to visit,
        // there is no more work to do.

        return body;
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after 'while' condition.");
        Stmt body = statement();
        return new Stmt.While(condition, body);
    }

    private Stmt returnStatement() {
        // returnStmt → "return" expression? ";" ;
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }
        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after 'if' condition.");

        Stmt thenStmt = statement();
        Stmt elseStmt = null;
        if (match(ELSE)) {
            elseStmt = statement();
        }
        return new Stmt.If(condition, thenStmt, elseStmt);
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

    private Expr or() {
        Expr expr = and();
        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();
        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr assignment() {

        // expr: makeList().head.next
        // Expr expr = equality();

        Expr expr = or();

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
        // return primary();
        return call();
    }

    private Expr call() {
        Expr expr = primary();
        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else {
                break;
            }
        }
        return expr;
    }

    private Expr finishCall(Expr callee) {

        // if arguement list not empty, collect them
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }

        // we don't ignore paren here, pass it to Expr.Call to report wrong argument
        // position.
        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
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
