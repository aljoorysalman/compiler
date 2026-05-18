import java.util.*;

public class KidCodeParser {
    private List<KidCode.Token> tokens; 
    private int index = 0;
    private Stack<String> stack = new Stack<>();
    private Stack<ParseTreeNode> nodeStack = new Stack<>();
    private Map<String, Map<String, String[]>> table = new HashMap<>();

    private StringBuilder parseTreeLog = new StringBuilder();
    private ParseTreeNode root;

    public KidCodeParser(List<KidCode.Token> tokens) {
        this.tokens = tokens;
        initializeTable();
    }

    public ParseTreeNode getRoot() {
        return root;
    }

private void initializeTable() {
    // ─── 1. Lookahead / Follow Set Token Collections ───
    String[] firstFactor = {"ID", "INT", "FLOAT", "STRING", "TRUE", "FALSE", "(", "DO"};
    String[] firstStmt = {"NUMBER", "FLOAT", "NAME", "FACT", "ID", "IF", "REPEAT", "WHILE", "SAY", "DO", "GIVE", "SKILL", "STRING"};
    String[] followStmtList = {"THEEND", "DONE", "ELSE"};
    
String[] followExpr = {
    ")", "DONE", ",", "THEEND", "THEN", "DO", 
    "==", "!=", "<", ">", "<=", ">=", 
    "NUMBER", "FLOAT", "NAME", "FACT", "ID", "SAY", "IF", "WHILE", "REPEAT", "BACK", "GIVE", "GIVES", "SKILL", "STRING"
};

String[] followPower = {
    "+", "-", "*", "/", "%", ")", "DONE", ",", "THEEND", "THEN", "DO", 
    "NUMBER", "NAME", "FLOAT", "FACT", "ID", "SAY", "IF", "WHILE", "REPEAT", 
    "BACK", "GIVE", "GIVES", "==", "!=", "<", ">", "<=", ">=", "SKILL", "STRING"
};

String[] followTerm  = {
    "+", "-", ")", "DONE", ",", "THEEND", "THEN", "DO", 
    "NUMBER", "FLOAT", "NAME", "FACT", "ID", "SAY", "IF", "WHILE", "REPEAT", 
    "BACK", "GIVE", "GIVES", "==", "!=", "<", ">", "<=", ">=", "SKILL", "STRING"
};

String[] followAssignExprPrime = {
    "+", "-", "*", "/", "%", ")", "DONE", ",", "THEEND", "theend", "THEN", "DO", 
    "NUMBER", "FLOAT", "NAME", "FACT", "ID", "STRING",
    "IF", "WHILE", "REPEAT", "SAY", "GIVE", "SKILL"
}; 



// ─── 2. Program Structural Components ───
    addRule("Program", new String[]{"START"}, new String[]{"START", "StmtList", "THEEND", "Skills"});
    addRule("Skills", new String[]{"SKILL"}, new String[]{"SkillDecl", "Skills"});
    addRule("Skills", new String[]{"$"}, new String[]{});
    addRule("SkillDecl", new String[]{"SKILL"}, new String[]{"SKILL", "ID", "(", "ParamList", ")", "GIVES", "Type", "StmtList", "DONE"});
    addRule("SkillCall", new String[]{"DO"}, new String[]{"DO", "ID", "(", "ArgList", ")"});

    // ─── 3. Parameters and Arguments ───
    addRule("ArgList", new String[]{"ID", "INT", "FLOAT", "STRING", "TRUE", "FALSE", "("}, new String[]{"Expr", "ArgList'"});
    addRule("ArgList", new String[]{")"}, new String[]{}); 
    addRule("ArgList'", new String[]{","}, new String[]{",", "Expr", "ArgList'"});
    addRule("ArgList'", new String[]{")"}, new String[]{}); 

    // ─── 4. Type Layout Mapping Engine ───
    addRule("Type", new String[]{"NUMBER"}, new String[]{"NUMBER"});
    addRule("Type", new String[]{"FLOAT"}, new String[]{"FLOAT"});
    addRule("Type", new String[]{"NAME"}, new String[]{"NAME"});
    addRule("Type", new String[]{"FACT"}, new String[]{"FACT"});
    addRule("Type", new String[]{"VOID"}, new String[]{"VOID"});

    addRule("ParamList", new String[]{"NUMBER", "FLOAT", "NAME", "FACT"}, new String[]{"Type", "ID", "ParamList'"});
    addRule("ParamList'", new String[]{","}, new String[]{",", "Type", "ID", "ParamList'"});
    addRule("ParamList'", new String[]{")"}, new String[]{});  
    
    // ─── 5. Core Statement Routes ───
    addRule("StmtList", firstStmt, new String[]{"Stmt", "StmtList"});
    addRule("StmtList", followStmtList, new String[]{}); 
    
    addRule("Stmt", new String[]{"SKILL"}, new String[]{"SkillDecl"});
    addRule("Stmt", new String[]{"STRING"}, new String[]{"Decl"});
    addRule("Stmt", new String[]{"GIVE"}, new String[]{"ReturnStmt"});
    addRule("Stmt", new String[]{"NUMBER", "FLOAT", "NAME", "FACT"}, new String[]{"Decl"});
    addRule("Stmt", new String[]{"ID"}, new String[]{"Assign"});
    addRule("Stmt", new String[]{"IF"}, new String[]{"IfStmt"});
    addRule("Stmt", new String[]{"REPEAT"}, new String[]{"RepeatStmt"});
    addRule("Stmt", new String[]{"WHILE"}, new String[]{"WhileStmt"});
    addRule("Stmt", new String[]{"SAY"}, new String[]{"SayStmt"});
    addRule("Stmt", new String[]{"DO"}, new String[]{"SkillCall"});

    addRule("ReturnStmt", new String[]{"GIVE"}, new String[]{"GIVE", "BACK", "Expr"});
    
    // ─── 6. Variable Declarations & Assignments ───
    addRule("Decl", new String[]{"NUMBER", "FLOAT", "NAME", "FACT"}, new String[]{"Type", "ID", "=", "AssignExpr"});
    addRule("Assign", new String[]{"ID"}, new String[]{"ID", "=", "AssignExpr"});
  
    addRule("AssignExpr", firstFactor, new String[]{"Expr", "AssignExpr'"});
    addRule("AssignExpr'", new String[]{"==", "!=", "<", ">", "<=", ">="}, new String[]{"Relop", "Expr"});
    addRule("AssignExpr'", followAssignExprPrime, new String[]{}); 
  
    // ─── 7. Arithmetic Evaluation Stack (LL(1) Standard Chain) ───
    addRule("Expr", firstFactor, new String[]{"Term", "Expr'"});
    addRule("Expr'", new String[]{"+"}, new String[]{"+", "Term", "Expr'"});
    addRule("Expr'", new String[]{"-"}, new String[]{"-", "Term", "Expr'"});
    addRule("Expr'", followExpr, new String[]{}); 

    addRule("Term", firstFactor, new String[]{"Power", "Term'"});
    addRule("Term'", new String[]{"*"}, new String[]{"*", "Power", "Term'"});
    addRule("Term'", new String[]{"/"}, new String[]{"/", "Power", "Term'"});
    addRule("Term'", new String[]{"%"}, new String[]{"%", "Power", "Term'"});
    addRule("Term'", followTerm, new String[]{}); 

    addRule("Power", firstFactor, new String[]{"Factor", "Power'"});
    addRule("Power'", new String[]{"^"}, new String[]{"^", "Factor", "Power'"});
    addRule("Power'", followPower, new String[]{}); 

    // ─── 8. Basic Factor Definitions ───
    addRule("Factor", new String[]{"INT"}, new String[]{"INT"});
    addRule("Factor", new String[]{"FLOAT"}, new String[]{"FLOAT"});
    addRule("Factor", new String[]{"ID"}, new String[]{"ID"});
    addRule("Factor", new String[]{"STRING"}, new String[]{"STRING"});
    addRule("Factor", new String[]{"TRUE"}, new String[]{"TRUE"});
    addRule("Factor", new String[]{"FALSE"}, new String[]{"FALSE"});
    addRule("Factor", new String[]{"("}, new String[]{"(", "AssignExpr", ")"});
    addRule("Factor", new String[]{"DO"}, new String[]{"SkillCall"});

    // ─── 9. Code Control Structures & Conditionals ───
    addRule("SayStmt", new String[]{"SAY"}, new String[]{"SAY", "(", "Expr", ")"});
    addRule("IfStmt", new String[]{"IF"}, new String[]{"IF", "(", "BoolExpr", ")", "THEN", "StmtList", "ElsePart", "DONE"});
    addRule("ElsePart", new String[]{"ELSE"}, new String[]{"ELSE", "StmtList"});
    addRule("ElsePart", new String[]{"DONE"}, new String[]{});
    addRule("WhileStmt", new String[]{"WHILE"}, new String[]{"WHILE", "(", "BoolExpr", ")", "DO", "StmtList", "DONE"});
    addRule("RepeatStmt", new String[]{"REPEAT"}, new String[]{"REPEAT", "(", "Expr", ")", "DO", "StmtList", "DONE"});
    
    // ─── 10. Logical Expression Elements ───
    addRule("BoolExpr", firstFactor, new String[]{"Expr", "Relop", "Expr"});
    
    String[] relationalTerminals = {"==", "!=", "<", ">", "<=", ">="};
    addRule("Relop", relationalTerminals, new String[]{"(Matching Relational Operator)"});
}

    private void addRule(String nt, String[] terminals, String[] prod) {
        table.putIfAbsent(nt, new HashMap<>());
        for (String t : terminals) table.get(nt).put(t, prod);
    }

    public void parse() throws Exception {
        stack.push("$");
        stack.push("Program");

        root = new ParseTreeNode("Program", 1);
        nodeStack.push(null); 
        nodeStack.push(root); 

        KidCode.Token currentToken = getNextToken();
        String a = getLookahead(currentToken);

        parseTreeLog.append("--- Parsing Started ---\n");
        
        while (!stack.peek().equals("$")) {
            String X = stack.peek(); 
            
            if (isTerminal(X)) {
                boolean isPlaceholderMatch = X.equals("(Matching Token)") && 
                    (a.equals("ID") || a.equals("INT") || a.equals("FLOAT") || a.equals("STRING") || a.equals("TRUE") || a.equals("FALSE"));

                boolean isRelopMatch = X.equals("(Matching Relational Operator)") && 
                    (a.equals("==") || a.equals("!=") || a.equals("<") || a.equals(">") || a.equals("<=") || a.equals(">="));

                if (X.equals(a) || isPlaceholderMatch || isRelopMatch) {
                    // FIX: Pop BOTH matching symbol and its matching node target synchronously
                    stack.pop();
                    ParseTreeNode currentNode = nodeStack.pop();
                    
                    currentNode.label = "Match: " + currentToken.lexeme;
                    currentNode.lineInfo = currentToken.line; // Set the correct dynamic source code line
                    
                    currentToken = getNextToken();
                    a = getLookahead(currentToken);
                } else {
                    throw new Exception("Syntax Error: Terminal mismatch. Expected " + X + " but found " + a + " at line " + currentToken.line);
                }
            } else {
                Map<String, String[]> row = table.get(X);
                if (row != null && row.containsKey(a)) {
                    String[] production = row.get(a);
                    
                    // Pop non-terminal state cleanly
                    stack.pop();
                    ParseTreeNode currentNode = nodeStack.pop();
                    
                    if (production.length > 0) {
                        List<ParseTreeNode> childrenList = new ArrayList<>();
                        for (String symbol : production) {
                            ParseTreeNode child = new ParseTreeNode(symbol, currentToken.line);
                            currentNode.addChild(child);
                            childrenList.add(child);
                        }
                        
                        // FIX: Push to stacks in REVERSE order to maintain proper AST layout direction
                        for (int i = production.length - 1; i >= 0; i--) {
                            stack.push(production[i]);
                            nodeStack.push(childrenList.get(i)); 
                        }
                    } else {
                        // Handle dynamic Epsilon visual node mapping injection
                        currentNode.addChild(new ParseTreeNode("ε", currentToken.line));
                    }
                    
                    parseTreeLog.append(X + " -> " + (production.length == 0 ? "ε" : String.join(" ", production)) + "\n");
                } else {
                    throw new Exception("Syntax Error: No rule M[" + X + ", " + a + "] at line " + currentToken.line);
                }
            }
        }

        if (stack.peek().equals("$") && a.equals("$")) {
            parseTreeLog.setLength(0);
            parseTreeLog.append("Parsing Successful!\n\n--- Visual Parse Tree ---\n");
            parseTreeLog.append(root.printTree("", true));
        } else {
            throw new Exception("Syntax Error: Stack finished but input remains.");
        }
    }

    private KidCode.Token getNextToken() {
        if (index < tokens.size()) {
            return tokens.get(index++);
        }
        return new KidCode.Token(KidCode.TokenType.EOF, -1, "$");
    }

    private String getLookahead(KidCode.Token t) {
        if (t.lexeme.equals("$")) return "$";

        if (t.type == KidCode.TokenType.KEYWORD || 
            t.type == KidCode.TokenType.OPERATOR || 
            t.type == KidCode.TokenType.DELIMITER) {
            return t.lexeme.toUpperCase();
        }
         
        if (t.type == KidCode.TokenType.INTEGER) return "INT"; 
        if (t.type == KidCode.TokenType.FLOAT)   return "FLOAT";
        if (t.type == KidCode.TokenType.STRING)  return "STRING";
        if (t.type == KidCode.TokenType.IDENTIFIER) return "ID";

        return t.type.toString();
    }
      
    private boolean isTerminal(String s) {
        return !table.containsKey(s);
    }

    public String getParseTree() {
        return parseTreeLog.toString();
    }
}

// ── Updated ParseTreeNode Supporting Semantic Metadata Line Checks ───────────
class ParseTreeNode {
    String label;
    int lineInfo; // Added to satisfy semantic verification checks
    List<ParseTreeNode> children = new ArrayList<>();

    public ParseTreeNode(String label, int lineInfo) {
        this.label = label;
        this.lineInfo = lineInfo;
    }

    public void addChild(ParseTreeNode child) {
        children.add(child);
    }

    public String printTree(String prefix, boolean isLast) {
        StringBuilder sb = new StringBuilder();
        
        if (!label.equals("Program")) {
            sb.append(prefix).append(isLast ? "└── " : "├── ").append(label).append("\n");
        } else {
            sb.append(label).append("\n");
        }

        String newPrefix = prefix;
        if (!label.equals("Program")) {
            newPrefix += isLast ? "    " : "│   ";
        }

        for (int i = 0; i < children.size(); i++) {
            boolean lastChild = (i == children.size() - 1);
            sb.append(children.get(i).printTree(newPrefix, lastChild));
        }
        return sb.toString();
    }
}