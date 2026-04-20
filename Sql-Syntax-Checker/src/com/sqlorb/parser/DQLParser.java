package com.sqlorb;

import java.util.List;
import java.util.ArrayList;
import com.sqlorb.ast.*;

/**
 * DQLParser is a syntax-only SQL parser for SELECT queries.
 * It validates queries without executing them.
 * 
 * Features:
 * - SELECT statements with DISTINCT
 * - Nested subqueries in FROM, WHERE, IN, and functions
 * - JOINs (INNER, LEFT, RIGHT, FULL, CROSS, NATURAL, OUTER)
 * - WHERE conditions with AND, OR, comparison operators
 * - GROUP BY, HAVING
 * - ORDER BY with ASC/DESC
 * - LIMIT clause
 * - Functions and CASE expressions
 * - Detailed error messages with suggestions
 */
public class DQLParser {

    private final ParserContext ctx;

    public DQLParser(ParserContext ctx) {
        this.ctx = ctx;
    }

    // =========================================================
    // ENTRY POINT: parseQuery()
    // =========================================================
    // Starts parsing a SELECT query. Handles optional top-level
    // set operators (UNION, INTERSECT, EXCEPT) and final semicolon.
    public SelectNode parseQuery() {
        return parseQuery(false); // top-level query, not subquery
    }

    /**
     * parseQuery(boolean isSubquery)
     * @param isSubquery true if this query is nested inside parentheses or IN
     */
    private SelectNode parseQuery(boolean isSubquery) {
        if (ctx.peek().type != TokenType.SELECT) {
            throw new ParseException(
                "Syntax Error: Query must start with SELECT.",
                "Start your query with 'SELECT <columns> FROM <table>'.", ctx.peek().line, ctx.peek().column
            );
        }

        SelectNode selectNode = parseSelect();

        // Handle set operators (UNION, INTERSECT, EXCEPT)
        while (isSetOperator(ctx.peek().type)) {
            TokenType op = ctx.peek().type;
            ctx.consume();

            // Optional UNION ALL
            if (op == TokenType.UNION && ctx.peek().type == TokenType.ALL) ctx.consume();

            if (ctx.peek().type != TokenType.SELECT) {
                throw new ParseException(
                    "Syntax Error: '" + op + "' must be followed by a SELECT.",
                    "Add a full 'SELECT ...' query after '" + op + "'.", ctx.peek().line, ctx.peek().column
                );
            }
            parseSelect();
        }

        // Optional semicolon
        if (ctx.peek().type == TokenType.SEMICOLON) ctx.consume();

        // Only enforce EOF if not a subquery
        if (!isSubquery && ctx.peek().type != TokenType.EOF) {
            throw new ParseException(
                "Syntax Error: Unexpected text '" + ctx.peek().value + "' at end of query.",
                "Remove extra text or check your query.", ctx.peek().line, ctx.peek().column
            );
        }
        
        return selectNode;
    }

    // =========================================================
    // SELECT Clause
    // =========================================================
    public SelectNode parseSelect() {
        require(TokenType.SELECT, "Ensure the query starts with 'SELECT'.");

        if (ctx.peek().type == TokenType.DISTINCT) ctx.consume();

        List<ExpressionNode> cols = parseColumns();

        if (ctx.peek().type == TokenType.WHERE) {
            throw new ParseException(
                "Error: WHERE clause cannot appear before FROM.",
                "Move 'WHERE ...' after 'FROM <table>'.", ctx.peek().line, ctx.peek().column
            );
        }

        require(TokenType.FROM, "Add 'FROM' keyword after your column list.");
        String table = parseTableOrSubquery();

        while (isJoinStart(ctx.peek().type)) parseJoin();

        ExpressionNode whereCondition = null;
        if (ctx.peek().type == TokenType.WHERE) {
            ctx.consume();
            whereCondition = parseCondition();
        }

        if (ctx.peek().type == TokenType.GROUP_BY) {
            ctx.consume();
            parseGroupByList();

            if (ctx.peek().type == TokenType.HAVING) {
                ctx.consume();
                parseCondition();
            }
        }

        if (ctx.peek().type == TokenType.ORDER_BY) {
            ctx.consume();
            parseOrderByList();
        }

        if (ctx.peek().type == TokenType.LIMIT) {
            ctx.consume();
            require(TokenType.NUMBER, "Provide a number for the LIMIT (e.g., LIMIT 10).");
        }
        
        return new SelectNode(cols, table, whereCondition);
    }

    // =========================================================
    // TABLE OR SUBQUERY
    // =========================================================
    private String parseTableOrSubquery() {
        String name = "subquery";
        if (ctx.peek().type == TokenType.LPAREN && ctx.peekNext().type == TokenType.SELECT) {
            ctx.consume(); // '('
            parseQuery(true); // <-- parse as subquery
            require(TokenType.RPAREN, "Close the subquery with ')'.");

            if (ctx.peek().type == TokenType.AS) {
                ctx.consume();
                name = ctx.peek().value;
                require(TokenType.IDENTIFIER, "Provide an alias after AS.");
            } else if (ctx.peek().type == TokenType.IDENTIFIER && !isClauseKeyword(ctx.peek().type)) {
                name = ctx.peek().value;
                ctx.consume(); // implicit alias
            }
        } else {
            name = parseIdentifierWithDot();

            if (ctx.peek().type == TokenType.AS) {
                ctx.consume();
                require(TokenType.IDENTIFIER, "Provide an alias after AS.");
            } else if (ctx.peek().type == TokenType.IDENTIFIER && !isClauseKeyword(ctx.peek().type)) {
                ctx.consume(); // implicit alias
            }
        }
        return name;
    }

    // =========================================================
    // JOINs
    // =========================================================
    private void parseJoin() {
        boolean requiresOn = true;

        TokenType type = ctx.peek().type;
        if (type == TokenType.NATURAL || type == TokenType.CROSS) {
            requiresOn = false;
            ctx.consume();
        } else {
            ctx.consume();
            if (ctx.peek().type == TokenType.OUTER) ctx.consume();
        }

        require(TokenType.JOIN, "Complete join syntax with 'JOIN'.");
        parseTableOrSubquery();

        if (requiresOn) {
            require(TokenType.ON, "Add an 'ON' clause to define how tables connect.");
            parseCondition();
        }
    }

    // =========================================================
    // COLUMNS
    // =========================================================
    private List<ExpressionNode> parseColumns() {
        if (ctx.peek().type == TokenType.STAR) {
            List<ExpressionNode> star = new ArrayList<>();
            star.add(new LiteralNode("*"));
            ctx.consume();
            return star;
        }
        return parseColumnList();
    }

    private List<ExpressionNode> parseColumnList() {
        List<ExpressionNode> cols = new ArrayList<>();
        while (true) {
            cols.add(parseExpression());

            if (ctx.peek().type == TokenType.AS) {
                ctx.consume();
                require(TokenType.IDENTIFIER, "Provide an alias after AS.");
            }

            if (ctx.peek().type == TokenType.COMMA) {
                ctx.consume();
                if (ctx.peek().type == TokenType.FROM) {
                    throw new ParseException(
                        "Syntax Error: Trailing comma before FROM.",
                        "Remove the last comma.", ctx.peek().line, ctx.peek().column
                    );
                }
                continue;
            }

            Token next = ctx.peek();
            if (next.type == TokenType.IDENTIFIER || next.type == TokenType.NUMBER || next.type == TokenType.STRING) {
                throw new ParseException(
                    "Error: Missing comma before '" + next.value + "'.",
                    "Insert a comma (,) between column names.", next.line, next.column
                );
            }
            break;
        }
        return cols;
    }

    // =========================================================
    // GROUP BY
    // =========================================================
    private void parseGroupByList() {
        while (true) {
            if (ctx.peek().type == TokenType.NUMBER) ctx.consume();
            else parseExpression();

            if (ctx.peek().type == TokenType.COMMA) {
                ctx.consume();
                continue;
            }
            break;
        }
    }

    // =========================================================
    // ORDER BY
    // =========================================================
    private void parseOrderByList() {
        while (true) {
            if (ctx.peek().type == TokenType.NUMBER) ctx.consume();
            else parseExpression();

            if (ctx.peek().type == TokenType.ASC || ctx.peek().type == TokenType.DESC) ctx.consume();

            if (ctx.peek().type == TokenType.COMMA) {
                ctx.consume();
                continue;
            }
            break;
        }
    }

    // =========================================================
    // EXPRESSIONS & CONDITIONS
    // =========================================================
    public ExpressionNode parseCondition() {
        return parseOr();
    }

    private ExpressionNode parseOr() {
        ExpressionNode left = parseAnd();
        while (ctx.peek().type == TokenType.OR) {
            String op = ctx.peek().value;
            ctx.consume();
            ExpressionNode right = parseAnd();
            left = new BinaryExpressionNode(left, op, right);
        }
        return left;
    }

    private ExpressionNode parseAnd() {
        ExpressionNode left = parseAtomic();
        while (ctx.peek().type == TokenType.AND) {
            String op = ctx.peek().value;
            ctx.consume();
            ExpressionNode right = parseAtomic();
            left = new BinaryExpressionNode(left, op, right);
        }
        return left;
    }

    private ExpressionNode parseAtomic() {
        ExpressionNode expr = parseExpression();

        TokenType opType = ctx.peek().type;
        String op = ctx.peek().value;

        if (isComparisonOperator(opType)) {
            ctx.consume();
            ExpressionNode right = parseExpression();
            return new BinaryExpressionNode(expr, op, right);
        } else if (opType == TokenType.BETWEEN) {
            ctx.consume();
            ExpressionNode lower = parseExpression();
            require(TokenType.AND, "Use 'AND' inside a BETWEEN clause (e.g., BETWEEN 10 AND 20).");
            ExpressionNode upper = parseExpression();
            return new BinaryExpressionNode(expr, "BETWEEN", new BinaryExpressionNode(lower, "AND", upper));
        } else if (opType == TokenType.IN) {
            ctx.consume();
            parseInList();
            return new BinaryExpressionNode(expr, "IN", new LiteralNode("(...)"));
        } else if (opType == TokenType.LIKE) {
            ctx.consume();
            ExpressionNode right = parseExpression();
            return new BinaryExpressionNode(expr, "LIKE", right);
        } else if (opType == TokenType.IS) {
            ctx.consume();
            boolean not = false;
            if (ctx.peek().type == TokenType.NOT) {
                not = true;
                ctx.consume();
            }
            require(TokenType.NULL, "IS must be followed by NULL (or NOT NULL).");
            return new BinaryExpressionNode(expr, not ? "IS NOT NULL" : "IS NULL", null);
        }
        return expr;
    }

    // =========================================================
    // EXPRESSIONS
    // =========================================================
    public ExpressionNode parseExpression() {
        TokenType type = ctx.peek().type;
        ExpressionNode node = null;

        if (type == TokenType.IDENTIFIER) {
            if (ctx.peekNext().type == TokenType.LPAREN) {
                String funcName = ctx.peek().value;
                parseFunctionCall();
                node = new ColumnNode(funcName + "(...)");
            } else {
                node = new ColumnNode(parseIdentifierWithDot());
            }
        } else if (type == TokenType.NUMBER || type == TokenType.STRING || type == TokenType.STAR || type == TokenType.NULL) {
            node = new LiteralNode(ctx.peek().value);
            ctx.consume();
        } else if (type == TokenType.LPAREN) {
            ctx.consume();
            if (ctx.peek().type == TokenType.SELECT) {
                node = new SubqueryNode(parseQuery(true));
            } else {
                node = parseCondition();
            }
            require(TokenType.RPAREN, "Unbalanced parenthesis. Add a closing ')'.");
        } else if (type == TokenType.CASE) {
            parseCaseExpression();
            node = new LiteralNode("CASE_EXPR");
        } else {
            throw new ParseException(
                "Syntax Error: Expected a column, number, or string near '" + ctx.peek().value + "'.", "Check your expression syntax", ctx.peek().line, ctx.peek().column
            );
        }

        if (ctx.peek().type == TokenType.PLUS || ctx.peek().type == TokenType.MINUS ||
            ctx.peek().type == TokenType.STAR || ctx.peek().type == TokenType.SLASH ||
            ctx.peek().type == TokenType.PERCENT) {
            String op = ctx.peek().value;
            ctx.consume();
            ExpressionNode right = parseExpression();
            return new BinaryExpressionNode(node, op, right);
        }
        return node;
    }

    private void parseFunctionCall() {
        ctx.consume(); // function name
        require(TokenType.LPAREN, "Function call must have '('.");

        if (ctx.peek().type == TokenType.SELECT) parseQuery(true);
        else if (ctx.peek().type != TokenType.RPAREN) parseColumnList();

        require(TokenType.RPAREN, "Close the function parentheses ')'.");
    }

    private void parseCaseExpression() {
        ctx.consume(); // CASE
        require(TokenType.WHEN, "CASE must have WHEN.");
        parseExpression();
        require(TokenType.THEN, "WHEN must be followed by THEN.");
        parseExpression();

        while (ctx.peek().type == TokenType.WHEN) {
            ctx.consume();
            parseExpression();
            require(TokenType.THEN, "WHEN must be followed by THEN.");
            parseExpression();
        }

        if (ctx.peek().type == TokenType.ELSE) {
            ctx.consume();
            parseExpression();
        }

        require(TokenType.END, "CASE expression must end with END.");
    }

    private void parseInList() {
        require(TokenType.LPAREN, "Start the IN list with '('.");

        if (ctx.peek().type == TokenType.SELECT) parseQuery(true);
        else {
            parseExpression();
            while (ctx.peek().type == TokenType.COMMA) {
                ctx.consume();
                parseExpression();
            }
        }

        require(TokenType.RPAREN, "Close the IN list with ')'.");
    }

    private String parseIdentifierWithDot() {
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.peek().value);
        require(TokenType.IDENTIFIER, "Expected an identifier.");
        while (ctx.peek().type == TokenType.DOT) {
            sb.append(".");
            ctx.consume();
            sb.append(ctx.peek().value);
            require(TokenType.IDENTIFIER, "Expected column/table name after '.'");
        }
        return sb.toString();
    }

    // =========================================================
    // UTILITIES
    // =========================================================
    private void require(TokenType expected, String suggestion) {
        if (ctx.peek().type == expected) ctx.consume();
        else throw new ParseException(
            "Syntax Error: Expected '" + expected + "' but found '" + ctx.peek().value + "'.", suggestion, ctx.peek().line, ctx.peek().column
        );
    }

    private boolean isClauseKeyword(TokenType t) {
        return t == TokenType.FROM || t == TokenType.WHERE || t == TokenType.GROUP_BY ||
               t == TokenType.HAVING || t == TokenType.ORDER_BY || t == TokenType.LIMIT;
    }

    private boolean isSetOperator(TokenType t) {
        return t == TokenType.UNION || t == TokenType.INTERSECT || t == TokenType.EXCEPT;
    }

    private boolean isJoinStart(TokenType t) {
        return t == TokenType.JOIN || t == TokenType.LEFT || t == TokenType.RIGHT ||
               t == TokenType.FULL || t == TokenType.INNER || t == TokenType.CROSS || t == TokenType.NATURAL;
    }

    private boolean isComparisonOperator(TokenType t) {
        return t == TokenType.EQUALS || t == TokenType.NOT_EQUALS || t == TokenType.GT ||
               t == TokenType.LT || t == TokenType.GE || t == TokenType.LE;
    }

    private TokenType peekNext() {
        return ctx.peekNext().type;
    }
}
