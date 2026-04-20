package com.sqlorb;

import java.util.ArrayList;
import java.util.List;
import com.sqlorb.ast.*;

/**
 * DMLParser with Suggested Fixes
 * ============================================================================
 * SQL DML syntax parser (syntax validation only) + suggested corrections.
 *
 * Existing features remain intact; added capability:
 *  - Detect common user mistakes (like missing commas, FROM, or parentheses)
 *  - Provide a corrected SQL snippet as suggestion
 *
 * NOTE:
 *  - Parser still validates SYNTAX ONLY.
 *  - Suggested fixes are heuristic-based; do not perform semantic validation.
 */
public class DMLParser {

    private final ParserContext ctx;

    public DMLParser(ParserContext ctx) {
        this.ctx = ctx;
    }

    // =========================================================================
    // ENTRY POINT
    // =========================================================================
    public Statement parse() {
        return switch (ctx.peek().type) {
            case INSERT -> parseInsert();
            case UPDATE -> { parseUpdate(); yield null; } 
            case DELETE -> { parseDelete(); yield null; }
            default -> { error("Expected INSERT, UPDATE, or DELETE"); yield null; }
        };
    }

    // =========================================================================
    // INSERT
    // =========================================================================
    private InsertNode parseInsert() {
        ctx.match(TokenType.INSERT);
        ctx.match(TokenType.INTO);
        String table = ctx.peek().value;
        ctx.match(TokenType.IDENTIFIER); // table

        List<String> columnNames = new ArrayList<>();

        // Optional column list
        if (ctx.matchIf(TokenType.LPAREN)) {
            if (ctx.peek().type == TokenType.RPAREN) {
                ctx.consume();
            } else {
                columnNames = parseIdentifierListWithFix(); // <-- enhanced
                ctx.match(TokenType.RPAREN);
            }
        }

        // DEFAULT VALUES (no AST for this simple version)
        if (ctx.matchIf(TokenType.DEFAULT)) {
            ctx.match(TokenType.VALUES);
            return new InsertNode(table, columnNames, new ArrayList<>());
        }

        // INSERT ... SELECT
        if (ctx.peek().type == TokenType.SELECT) {
            DQLParser dql = new DQLParser(ctx);
            dql.parseSelect();
            return new InsertNode(table, columnNames, new ArrayList<>());
        }

        // VALUES (...), allow empty column rows if columns list empty
        ctx.match(TokenType.VALUES);
        List<List<ExpressionNode>> rows = parseRowList();
        
        // Validation: Every row must match the column count if columns were specified
        if (!columnNames.isEmpty()) {
            for (List<ExpressionNode> row : rows) {
                if (row.size() != columnNames.size()) {
                    throw new ParseException("Column count mismatch", 
                        "Expected " + columnNames.size() + " values but found " + row.size(),
                        ctx.peek().line, ctx.peek().column);
                }
            }
        }

        return new InsertNode(table, columnNames, rows);
    }

    private List<List<ExpressionNode>> parseRowList() {
        List<List<ExpressionNode>> rows = new ArrayList<>();
        rows.add(parseRow());
        while (ctx.matchIf(TokenType.COMMA)) {
            rows.add(parseRow());
        }
        return rows;
    }

    private List<ExpressionNode> parseRow() {
        ctx.match(TokenType.LPAREN);
        List<ExpressionNode> values = new ArrayList<>();
        if (ctx.peek().type != TokenType.RPAREN) {
            values = parseExpressionList();
        }
        ctx.match(TokenType.RPAREN);
        return values;
    }

    // =========================================================================
    // UPDATE
    // =========================================================================
    private void parseUpdate() {
        ctx.match(TokenType.UPDATE);
        ctx.match(TokenType.IDENTIFIER); // table
        ctx.match(TokenType.SET);

        do {
            parseQualifiedIdentifier();
            if (!ctx.matchIf(TokenType.EQUALS)) {
                errorWithFix("Expected '=' in SET clause",
                        "Add '=' between column and value, e.g., name = 'John'");
            }
            parseExpression();
        } while (ctx.matchIf(TokenType.COMMA));

        if (ctx.matchIf(TokenType.WHERE)) {
            parseCondition();
        }
    }

    // =========================================================================
    // DELETE
    // =========================================================================
    private void parseDelete() {
        ctx.match(TokenType.DELETE);
        ctx.match(TokenType.FROM);
        if (ctx.peek().type != TokenType.IDENTIFIER) {
            errorWithFix("Missing table name in DELETE",
                    "Add table name after FROM, e.g., DELETE FROM users");
        }
        ctx.match(TokenType.IDENTIFIER);

        if (ctx.matchIf(TokenType.WHERE)) {
            parseCondition();
        }
    }

    // =========================================================================
    // CONDITIONS (WHERE)
    // =========================================================================
    private ExpressionNode parseCondition() {
        DQLParser dql = new DQLParser(ctx);
        return dql.parseCondition();
    }



    // =========================================================================
    // EXPRESSIONS
    // =========================================================================
    private ExpressionNode parseExpression() { 
        DQLParser dql = new DQLParser(ctx);
        return dql.parseExpression();
    }


    // =========================================================================
    // SELECT (minimal)
    // =========================================================================


    // =========================================================================
    // HELPERS
    // =========================================================================
    private List<ExpressionNode> parseExpressionList() {
        List<ExpressionNode> list = new ArrayList<>();
        list.add(parseExpression());
        while (ctx.matchIf(TokenType.COMMA)) list.add(parseExpression());
        return list;
    }

    private List<String> parseIdentifierListWithFix() {
        List<String> list = new ArrayList<>();
        list.add(ctx.peek().value);
        parseQualifiedIdentifier();
        while (ctx.peek().type != TokenType.RPAREN && ctx.peek().type != TokenType.EOF) {
            if (!ctx.matchIf(TokenType.COMMA)) {
                // Detected missing comma between identifiers
                errorWithFix("Missing comma between columns",
                        "Add commas between identifiers, e.g., col1, col2");
            }
            list.add(ctx.peek().value);
            parseQualifiedIdentifier();
        }
        return list;
    }

    private void parseQualifiedIdentifier() {
        ctx.match(TokenType.IDENTIFIER);
        while (ctx.matchIf(TokenType.DOT)) ctx.match(TokenType.IDENTIFIER);
    }



    // =========================================================================
    // ERROR WITH SUGGESTED FIX
    // =========================================================================
    private void errorWithFix(String msg, String suggestion) {
        throw new ParseException(msg, suggestion, ctx.peek().line, ctx.peek().column);
    }

    private void error(String msg) {
        throw new ParseException(msg, null, ctx.peek().line, ctx.peek().column);
    }
}
