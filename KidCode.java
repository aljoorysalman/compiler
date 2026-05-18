import java.util.*;
import java.util.regex.*;

public class KidCode {
    // token types
    public enum TokenType {
        KEYWORD, IDENTIFIER, INTEGER, FLOAT, STRING, OPERATOR, DELIMITER, COMMENT, EOF, ERROR,
    }

    // token class
    public static class Token {
        TokenType type;
        int line;
        String lexeme;

        Token(TokenType type, int line, String lexeme) {
            this.type = type;
            this.line = line;
            this.lexeme = lexeme;
        }

        public String toString() {
            return "<" + type + " ," + line + " ," + lexeme + ">";
        }
    }

    // symbol table
    public static class SymbolTable {
        public Map<String, Integer> table = new LinkedHashMap<>();

        void add(String name, int line) {
            String lower = name.toLowerCase();
            table.putIfAbsent(lower, line);
        }


        

        void print() {
            System.out.println("\nSymbol Table:");
            System.out.println("Identifier\tFirstLine");
            for (Map.Entry<String, Integer> e : table.entrySet()) {
                System.out.println(e.getKey() + "\t\t" + e.getValue());
            }
        }
    }

    // scanner
    public static class ScannerCore {
        public List<Token> tokens = new ArrayList<>();
        public SymbolTable symbolTable = new SymbolTable();
        public int totalLexemes = 0;
        private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
                "start", "theend", "if", "then", "else", "repeat", "while", "do", "done", "skill", "gives", "give",
                "back", "say", "ask", "number", "float", "name", "fact", "void", "and", "or", "not",
                "true", "false"));
        // regular expression part
        private static final String LETTER = "[A-Za-z]";
        private static final String DIGIT = "[0-9]";
        private static final String IDENTIFIER = LETTER + "(" + LETTER + "|" + DIGIT + ")*";
        private static final String FLOAT = "[+-]?" + DIGIT + "+\\." + DIGIT + "+";
        private static final String INTEGER = "[+-]?" + DIGIT + "+";
        private static final String STRING = "\"([^\"\\\\]|\\\\.)*\"";
        private static final String OPERATORS = "==|!=|<=|>=|\\+|-|\\*|/|=|<|>|\\^|%";
        private static final String DELIMITERS = "[\\.\\(\\)\\{\\};:\\,]";       
        private static final Pattern PATTERN_IDENTIFIER = Pattern.compile("^" + IDENTIFIER);
        private static final Pattern PATTERN_FLOAT = Pattern.compile("^" + FLOAT);
        private static final Pattern PATTERN_INTEGER = Pattern.compile("^" + INTEGER);
        private static final Pattern PATTERN_STRING = Pattern.compile("^" + STRING);
        private static final Pattern PATTERN_OPERATOR = Pattern.compile("^(" + OPERATORS + ")");
        private static final Pattern PATTERN_DELIMITER = Pattern.compile("^" + DELIMITERS);

        // process a single line
        public void processLine(String line, int lineNum) {
            int i = 0;
            int n = line.length();
            while (i < n) {
                char c = line.charAt(i);
                // skip whitespace
                if (Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                // line comment
                if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/') {
                    String comment = line.substring(i);
                    tokens.add(new Token(TokenType.COMMENT, lineNum, comment));
                    totalLexemes++;
                    break;

                }
                String remaining = line.substring(i);
                // STRING CHECK
                if (line.charAt(i) == '"') {
                    Matcher m = PATTERN_STRING.matcher(remaining);
                    if (m.find()) {
                        String lex = m.group();
                        tokens.add(new Token(TokenType.STRING, lineNum, lex));
                        totalLexemes++;
                        i += lex.length();
                        continue; // Move to next token
                    } else {
                        // Handle error: Quote started but never closed
                        tokens.add(new Token(TokenType.ERROR, lineNum, "Unterminated string"));
                        break;
                    }
                }
                // float check
                Matcher fM = PATTERN_FLOAT.matcher(remaining);
                if (fM.find()) {
                    String lex = fM.group();
                    // enforce max 8 digits after decimal
                    if (lex.contains(".")) {
                        String fractionalPart = lex.substring(lex.indexOf(".") + 1);
                        if (fractionalPart.length() > 8) {
                            tokens.add(new Token(TokenType.ERROR, lineNum, "Float precision too long (max 8): " + lex));
                            totalLexemes++;
                            i += lex.length();
                            continue;
                        }
                    }
                    // detect invalid floats
                    int nextCharIdx = i + lex.length();
                    if (nextCharIdx < n && line.charAt(nextCharIdx) == '.') {
                        tokens.add(new Token(TokenType.ERROR, lineNum, "Malformed float: " + lex + "..."));
                        // Skip until whitespace
                        while (i < n && !Character.isWhitespace(line.charAt(i)))
                            i++;
                        continue;
                    }
                    tokens.add(new Token(TokenType.FLOAT, lineNum, lex));
                    totalLexemes++;
                    i += lex.length();
                    continue;
                }
                // integer detection
                Matcher intM = PATTERN_INTEGER.matcher(remaining);
                if (intM.find()) {
                    String lex = intM.group();
                    tokens.add(new Token(TokenType.INTEGER, lineNum, lex));
                    totalLexemes++;
                    i += lex.length();
                    continue;
                }
                // identifier or keyword detection
                Matcher idM = PATTERN_IDENTIFIER.matcher(remaining);
                if (idM.find()) {
                    String lex = idM.group();
                    // enforce max identifier length= 8
                    if (lex.length() > 8) {
                        tokens.add(new Token(TokenType.ERROR, lineNum, "Identifier too long: " + lex));
                    } else {
                        String lower = lex.toLowerCase();
                        if (KEYWORDS.contains(lower)) {
                            tokens.add(new Token(TokenType.KEYWORD, lineNum, lex));
                        } else {
                            tokens.add(new Token(TokenType.IDENTIFIER, lineNum, lex));
                            symbolTable.add(lex, lineNum);
                        }
                    }
                    totalLexemes++;
                    i += lex.length();
                    continue;
                }
                // operator detection
                Matcher opM = PATTERN_OPERATOR.matcher(remaining);
                if (opM.find()) {
                    String lex = opM.group();
                    tokens.add(new Token(TokenType.OPERATOR, lineNum, lex));
                    totalLexemes++;
                    i += lex.length();
                    continue;
                }

                // delimiter detection
                Matcher dM = PATTERN_DELIMITER.matcher(remaining);
                if (dM.find()) {
                    String lex = dM.group();
                    tokens.add(new Token(TokenType.DELIMITER, lineNum, lex));
                    totalLexemes++;
                    i += lex.length();
                    continue;
                }
                // if nothing matches -> invalid character
                tokens.add(new Token(TokenType.ERROR, lineNum, "Invalid symbol: '" + c + "'"));
                totalLexemes++;
                i++;
            }
        }

        // print all token + symbol table
        void printResults() {
            System.out.println("Total lexemes: " + totalLexemes);
            System.out.println("\nTokens:");
            for (Token t : tokens) {
                System.out.println(t);
            }
            symbolTable.print();
        }
    }
}