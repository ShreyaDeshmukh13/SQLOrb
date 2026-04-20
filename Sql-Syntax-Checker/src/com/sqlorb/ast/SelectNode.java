package com.sqlorb.ast;

import java.util.List;

public class SelectNode extends Statement {
    private final List<ExpressionNode> columns;
    private final String table;
    private final ExpressionNode whereCondition;

    public SelectNode(List<ExpressionNode> columns, String table, ExpressionNode whereCondition) {
        this.columns = columns;
        this.table = table;
        this.whereCondition = whereCondition;
    }

    public List<ExpressionNode> getColumns() { return columns; }
    public String getTable() { return table; }
    public ExpressionNode getWhereCondition() { return whereCondition; }

    @Override
    public String toString() {
        return "SelectNode{table='" + table + "', cols=" + columns + 
               (whereCondition != null ? ", where=" + whereCondition : "") + "}";
    }
}
