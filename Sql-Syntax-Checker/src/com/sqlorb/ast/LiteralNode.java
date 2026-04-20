package com.sqlorb.ast;

public class LiteralNode extends ExpressionNode {
    private final String value;

    public LiteralNode(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
