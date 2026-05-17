import java.util.*;

public class KidCodeParser {
    private List<KidCode.Token> tokens; 
    private int index = 0;
    private Stack<String> stack = new Stack<>();
    // Stack to track the current parent node in the tree
    private Stack<ParseTreeNode> nodeStack = new Stack<>();
    private Map<String, Map<String, String[]>> table = new HashMap<>();

    private StringBuilder parseTreeLog = new StringBuilder();
    //  Reference to the root of the tree
    private ParseTreeNode root;

    public KidCodeParser(List<KidCode.Token> tokens) {
        this.tokens = tokens;
        initializeTable();
    }

    public ParseTreeNode getRoot() {
    return root;
}
private void initializeTable() {
 //1. Follow Sets (For Epsilon Handling)
    String[] followStmtList = {"THEEND", "DONE", "ELSE"};
    String[] followExpr = {")", "DONE", "==", "!=", "<", ">", "<=", ">=", ",", "THEEND", "SAY", "IF", "WHILE", "REPEAT", "ID"};

    // 2. Program Structure
    addRule("Program", new String[]{"START"}, new String[]{"START", "StmtList", "THEEND", "Skills"});
    addRule("Skills", new String[]{"SKILL"}, new String[]{"SkillDecl", "Skills"});
    addRule("Skills", new String[]{"$"}, new String[]{});

    // 3. Statement List 
    // This tells the parser: "If you see a type or a keyword, it's a statement. Keep going."
    String[] firstStmt = {"NUMBER", "NAME", "FACT", "ID", "IF", "REPEAT", "WHILE", "SAY", "DO"};
    addRule("StmtList", firstStmt, new String[]{"Stmt", "StmtList"});
    addRule("StmtList", followStmtList, new String[]{}); // Only stop at these!

    // 4. Statement Types
    addRule("Stmt", new String[]{"NUMBER", "NAME", "FACT"}, new String[]{"Decl"});
    addRule("Stmt", new String[]{"ID"}, new String[]{"Assign"});
    addRule("Stmt", new String[]{"IF"}, new String[]{"IfStmt"});
    addRule("Stmt", new String[]{"REPEAT"}, new String[]{"RepeatStmt"});
    addRule("Stmt", new String[]{"WHILE"}, new String[]{"WhileStmt"});
    addRule("Stmt", new String[]{"SAY"}, new String[]{"SayStmt"});
    addRule("Stmt", new String[]{"DO"}, new String[]{"SkillCall"});

    // 5. Declaration & Assignment
    addRule("Decl", new String[]{"NUMBER", "NAME", "FACT"}, new String[]{"Type", "ID", "=", "Expr"});
    addRule("Assign", new String[]{"ID"}, new String[]{"ID", "=", "Expr"});
    addRule("Type", new String[]{"NUMBER"}, new String[]{"NUMBER"});
    addRule("Type", new String[]{"NAME"}, new String[]{"NAME"});
    addRule("Type", new String[]{"FACT"}, new String[]{"FACT"});

    // 6. Arithmetic (The Expression Chain)
    String[] firstFactor = {"ID", "INT", "FLOAT", "STRING", "TRUE", "FALSE", "(", "DO"};

    addRule("Expr", firstFactor, new String[]{"Term", "Expr'"});
    addRule("Expr'", new String[]{"+"}, new String[]{"+", "Term", "Expr'"});
    addRule("Expr'", new String[]{"-"}, new String[]{"-", "Term", "Expr'"});
    addRule("Expr'", followExpr, new String[]{}); // Epsilon

    addRule("Term", firstFactor, new String[]{"Power", "Term'"});
    addRule("Term'", new String[]{"*"}, new String[]{"*", "Power", "Term'"});
    addRule("Term'", new String[]{"/"}, new String[]{"/", "Power", "Term'"});
    addRule("Term'", new String[]{"%"}, new String[]{"%", "Power", "Term'"});
    addRule("Term'", followExpr, new String[]{}); // Epsilon

    addRule("Power", firstFactor, new String[]{"Factor", "Power'"});
    addRule("Power'", new String[]{"^"}, new String[]{"^", "Factor", "Power'"});
    addRule("Power'", followExpr, new String[]{}); // Epsilon

    // 7. Factor 
    // 
    addRule("Factor", new String[]{"INT"}, new String[]{"INT"});
    addRule("Factor", new String[]{"FLOAT"}, new String[]{"FLOAT"});
    addRule("Factor", new String[]{"ID"}, new String[]{"ID"});
    addRule("Factor", new String[]{"STRING"}, new String[]{"STRING"});
    addRule("Factor", new String[]{"TRUE"}, new String[]{"TRUE"});
    addRule("Factor", new String[]{"FALSE"}, new String[]{"FALSE"});
    addRule("Factor", new String[]{"("}, new String[]{"(", "Expr", ")"});
    addRule("Factor", new String[]{"DO"}, new String[]{"SkillCall"});

    // 8. Control Flow & Output
    addRule("SayStmt", new String[]{"SAY"}, new String[]{"SAY", "(", "Expr", ")"});
    addRule("IfStmt", new String[]{"IF"}, new String[]{"IF", "(", "BoolExpr", ")", "THEN", "StmtList", "ElsePart", "DONE"});
    addRule("ElsePart", new String[]{"ELSE"}, new String[]{"ELSE", "StmtList"});
    addRule("ElsePart", new String[]{"DONE"}, new String[]{});
    addRule("WhileStmt", new String[]{"WHILE"}, new String[]{"WHILE", "(", "BoolExpr", ")", "DO", "StmtList", "DONE"});
    addRule("RepeatStmt", new String[]{"REPEAT"}, new String[]{"REPEAT", "(", "Expr", ")", "DO", "StmtList", "DONE"});

    // 9. Booleans
    addRule("BoolExpr", firstFactor, new String[]{"Expr", "Relop", "Expr"});
    addRule("Relop", new String[]{"==", "!=", "<", ">", "<=", ">="}, new String[]{"(Matching Relational Operator)"});
}



    private void addRule(String nt, String[] terminals, String[] prod) {
        table.putIfAbsent(nt, new HashMap<>());
        for (String t : terminals) table.get(nt).put(t, prod);
    }


public void parse() throws Exception {
        // Initialize: Push $ then Start Symbol onto stack
        stack.push("$");
        stack.push("Program");

        //  Initialize the Tree Root
        root = new ParseTreeNode("Program");
        nodeStack.push(null); // Corresponds to $
        nodeStack.push(root); // Corresponds to "Program"

        KidCode.Token currentToken = getNextToken();
        String a = getLookahead(currentToken);

        parseTreeLog.append("--- Parsing Started ---\n");
        
        while (!stack.peek().equals("$")) {
            String X = stack.peek(); 
            //  Get the current node we are working on
            ParseTreeNode currentNode = nodeStack.pop();

            if (isTerminal(X)) {
                boolean isPlaceholderMatch = X.equals("(Matching Token)") && 
                    (a.equals("ID") || a.equals("INT") || a.equals("FLOAT") || a.equals("STRING") || a.equals("TRUE") || a.equals("FALSE"));

                boolean isRelopMatch = X.equals("(Matching Relational Operator)") && 
                    (a.equals("==") || a.equals("!=") || a.equals("<") || a.equals(">") || a.equals("<=") || a.equals(">="));

                if (X.equals(a) || isPlaceholderMatch || isRelopMatch) {
                    stack.pop();
                    //  Update the tree node label with the actual value
                    currentNode.label = "Match: " + currentToken.lexeme;
                    
                    currentToken = getNextToken();
                    a = getLookahead(currentToken);
                } else {
                    throw new Exception("Syntax Error: Terminal mismatch. Expected " + X + " but found " + a + " at line " + currentToken.line);
                }
            } else {
                Map<String, String[]> row = table.get(X);
                if (row != null && row.containsKey(a)) {
                    String[] production = row.get(a);
                    stack.pop();
                    
                    if (production.length > 0) {
                        //  Create children nodes and push them to nodeStack in REVERSE
                        List<ParseTreeNode> children = new ArrayList<>();
                        for (String symbol : production) {
                            ParseTreeNode child = new ParseTreeNode(symbol);
                            currentNode.addChild(child);
                            children.add(child);
                        }
                        
                        for (int i = production.length - 1; i >= 0; i--) {
                            stack.push(production[i]);
                            nodeStack.push(children.get(i)); // Keep tree stack in sync
                        }
                    } else {
                        //Handle Epsilon in the tree
                        currentNode.addChild(new ParseTreeNode("ε"));
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
    
       // Call the  printTree 
        parseTreeLog.append(root.printTree("", true));

        } else {
            throw new Exception("Syntax Error: Stack finished but input remains.");
        }
    }


    private KidCode.Token getNextToken() {
        if (index < tokens.size()) {
            return tokens.get(index++);
        }
        // Return a virtual $ token if we run out of tokens
        return new KidCode.Token(KidCode.TokenType.EOF, -1, "$");
    }


private String getLookahead(KidCode.Token t) {
    if (t.lexeme.equals("$")) return "$";

    // 1. Keywords, Operators, and Delimiters use their Lexeme (UPPERCASE)
    // This handles "START", "THEEND", "NUMBER", "IF", "(", "=", etc
    if (t.type == KidCode.TokenType.KEYWORD || 
        t.type == KidCode.TokenType.OPERATOR || 
        t.type == KidCode.TokenType.DELIMITER) {
        return t.lexeme.toUpperCase();
    }

     
    // 2. Map Literal Types to match your Parsing Table keys
    if (t.type == KidCode.TokenType.INTEGER) return "INT"; 
    if (t.type == KidCode.TokenType.FLOAT)   return "FLOAT";
    if (t.type == KidCode.TokenType.STRING)  return "STRING";
    if (t.type == KidCode.TokenType.IDENTIFIER) return "ID";

    return t.type.toString();

    
}
  
private boolean isTerminal(String s) {
    // If it's a key in the table, it's a Non-Terminal (should be expanded)
    // Otherwise, it's a Terminal (should be matched)
    return !table.containsKey(s);
}

 public String getParseTree() {
        return parseTreeLog.toString();
    }

}

class ParseTreeNode {
    String label;
    List<ParseTreeNode> children = new ArrayList<>();

    public ParseTreeNode(String label) {
        this.label = label;
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

        // Calculate new prefix for children
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