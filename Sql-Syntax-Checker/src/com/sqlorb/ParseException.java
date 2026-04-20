package com.sqlorb;

public class ParseException extends RuntimeException {

    private final String suggestion;
    private final int line;
    private final int column;

    public ParseException(String message) {
        super(message);
        this.suggestion = null;
        this.line = -1;
        this.column = -1;
    }

    public ParseException(String message, String suggestion) {
        super(message);
        this.suggestion = suggestion;
        this.line = -1;
        this.column = -1;
    }

    public ParseException(String message, String suggestion, int line, int column) {
        super(message + " at line " + line + ", column " + column);
        this.suggestion = suggestion;
        this.line = line;
        this.column = column;
    }

    public String getSuggestion() {
        return suggestion;
    }
    public int getLine() { return line; }
    public int getColumn() { return column; }
}
