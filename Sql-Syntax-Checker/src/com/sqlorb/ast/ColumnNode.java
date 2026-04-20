package com.sqlorb.ast;

public class ColumnNode extends ExpressionNode {
    private final String columnName;

    public ColumnNode(String columnName) {
        this.columnName = columnName;
    }

    @Override
    public String toString() {
        return columnName;
    }
}
