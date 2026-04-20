package com.sqlorb.ast;

public class SubqueryNode extends ExpressionNode {
    private final SelectNode query;

    public SubqueryNode(SelectNode query) {
        this.query = query;
    }

    @Override
    public String toString() {
        return "(" + query + ")";
    }
}
