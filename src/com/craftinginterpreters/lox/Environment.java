package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class Environment {

    final Environment enclosing;

    // There’s a Java Map in there to store the bindings. It uses bare strings for
    // the keys, not tokens.
    // A token represents a unit of code at a specific place in the source text,
    // but when it comes to looking up variables,
    // all identifier tokens with the same name should refer to the same variable
    // (ignoring scope for now).
    // Using the raw string ensures all of those tokens refer to the same map key.j
    private final Map<String, Object> values = new HashMap<String, Object>();

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    void define(String name, Object value) {
        values.put(name, value);
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }

        if (enclosing != null)
            return enclosing.get(name);

        // This is a little more semantically interesting.
        // If the variable is found, it simply returns the value bound to it.
        // But what if it’s not? Again, we have a choice:
        // Make it a syntax error.
        // Make it a runtime error.
        // Allow it and return some default value like nil.
        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }
}
