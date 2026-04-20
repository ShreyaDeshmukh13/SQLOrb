package com.sqlorb.ast;

import java.util.List;

public class InsertNode extends Statement {
    private final String table;
    private final List<String> columns;
    private final List<List<ExpressionNode>> values;

    public InsertNode(String table, List<String> columns, List<List<ExpressionNode>> values) {
        this.table = table;
        this.columns = columns;
        this.values = values;
    }

    public String getTable() { return table; }
    public List<String> getColumns() { return columns; }
    public List<List<ExpressionNode>> getValues() { return values; }

    @Override
    public String toString() {
        return "InsertNode{table='" + table + "', cols=" + columns + ", values=" + values + "}";
    }
}
