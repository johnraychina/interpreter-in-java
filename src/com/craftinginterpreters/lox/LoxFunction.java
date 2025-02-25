package com.craftinginterpreters.lox;

import java.util.List;

public class LoxFunction implements LoxCallable {

    // The function’s declaration statement.
    private Stmt.Function declaration;

    private final Environment closure;

    // function execution object
    public LoxFunction(Stmt.Function declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    @Override
    public int arity() {
        return this.declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {

        Environment environment = new Environment(closure); // reference parent env
        for (int i = 0; i < declaration.params.size(); i++) {
            // bind the function’s parameters to the arguments passed in.
            environment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }

        try {
            interpreter.executeBlock(this.declaration.body, environment);
        } catch (Return returnValue) {
            return returnValue.value;
        }
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }

}
