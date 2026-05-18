import java.util.*;

public class KidCodeSemantic {

    // ── 5.1.1 Symbol Table Entry ──────────────────────────────────────────
    public static class SymbolEntry {
        String name;
        String type;        // "int", "float", "string", "bool", "skill", etc.
        String scope;       // "global" or the specific function/skill name
        int line;
        
        // For functions/skills
        int paramCount;
        List<String> paramTypes = new ArrayList<>();
        String returnType;

        @Override
        public String toString() {
            return String.format("%s | Type: %s | Scope: %s | Line: %d %s", 
                name, type, scope, line, 
                type.equals("skill") ? ("| Params: " + paramTypes + " | Returns: " + returnType) : "");
        }
    }

    // ── Fields ──────────────────────────────────────────────────────────────
    private final ParseTreeNode root;
    private final List<String> errors = new ArrayList<>();
    private final List<SymbolEntry> symbolCache = new ArrayList<>();
    
    // Stack of Maps to support nested local scopes shielding global scopes
    private final Stack<Map<String, SymbolEntry>> scopeStack = new Stack<>();
    
    // Track scope string names hierarchically 
    private final Stack<String> scopeNameStack = new Stack<>();
    private String currentScopeName = "global";
    private int blockCounter = 0; // Fixed: Moved to main class for global block indexing!

    public KidCodeSemantic(ParseTreeNode root) {
        this.root = root;
        enterScope("global"); // Initialize the global scope layer
    }

    //  Public API 
    public void analyze() {
        visit(root);
        exitScope(); // Close out global scope at the end
    }

    public List<String> getErrors() { return errors; }
    public List<SymbolEntry> getSymbolTable() { return symbolCache; }
    //  Scope Management Lifecycle 
    public void enterScope(String baseName) {
        // 1. Compute the structural hierarchical path name
        if (scopeNameStack.isEmpty()) {
            scopeNameStack.push(baseName);
        } else {
            String parentScope = scopeNameStack.peek();
            scopeNameStack.push(parentScope + "." + baseName);
        }
        currentScopeName = scopeNameStack.peek();

        // 2. Push a brand new active symbol layer map onto our environment memory stack
        scopeStack.push(new LinkedHashMap<>());
    }

    public void exitScope() {
        if (!scopeStack.isEmpty()) {
            scopeStack.pop();
        }
        if (!scopeNameStack.isEmpty()) {
            scopeNameStack.pop();
        }
        currentScopeName = scopeNameStack.isEmpty() ? "global" : scopeNameStack.peek();
    }

    private void addSymbol(SymbolEntry entry) {
        if (!scopeStack.isEmpty()) {
            scopeStack.peek().put(entry.name.toLowerCase(), entry);
            symbolCache.add(entry);
        }
    }

    public List<SymbolEntry> getAllEntries() {
        return symbolCache;
    }

    private boolean existsInCurrentScope(String name) {
        return !scopeStack.isEmpty() && scopeStack.peek().containsKey(name.toLowerCase());
    }

     
    private void handleIfStatement(ParseTreeNode node) {
        ParseTreeNode condition = findChildByLabel(node, "Expr");
        if (condition != null) visit(condition);

        // Generate unique block name using our relocated main counter variable
        blockCounter++;
        enterScope("IF_" + blockCounter); 

        ParseTreeNode body = findChildByLabel(node, "StmtList");
        if (body != null) {
            visit(body); 
        }

        exitScope();
    }

    private void visit(ParseTreeNode node) {
      if (node == null) return;

        
        switch (node.label) {
    case "Decl" -> handleVariableDeclaration(node);
    case "SkillDecl" -> {
        handleSkillDeclaration(node);
        return; 
    }
    case "Assignment", "Assign" -> handleAssignment(node);
    case "IF" -> {
        handleIfStatement(node);
        return;
    }
    // Updated Return Cases to catch your language's exact Node label:
    case "GIVE", "ReturnStmt", "Return", "GiveBack", "GiveStmt" -> {
        handleReturnStatement(node);
    }
    default -> {
        for (ParseTreeNode child : node.children) {
            visit(child);
        }
    }
}
    }
private void handleReturnStatement(ParseTreeNode node) {
    int line = extractLine(node);
    
    // Search the children for the value or variable being returned
    ParseTreeNode exprNode = findChildByLabel(node, "Expr");
    
    // Fallback: If it doesn't find an "Expr" node wrapper, look for the literal value child
    if (exprNode == null) {
        for (ParseTreeNode child : node.children) {
            if (child.label.startsWith("Match:") && !child.label.contains("give") && !child.label.contains("back")) {
                exprNode = child;
                break;
            } else if (!child.children.isEmpty()) {
                exprNode = child; // Take the expression tree branch
            }
        }
    }

    String actualReturnType = (exprNode != null) ? inferExpressionType(exprNode) : "void";

    // Extract function context out of scope chain
    if (scopeNameStack.size() < 2) {
        errors.add("Semantic Error at line " + line + ": 'give back' cannot be written outside of a SKILL block.");
        return;
    }
    
    // Grab the active function namespace
    String currentSkillName = scopeNameStack.peek(); 
    if (currentSkillName.contains(".")) {
        currentSkillName = currentSkillName.substring(currentSkillName.lastIndexOf(".") + 1);
    }
    
    SymbolEntry skillSymbol = lookupVariable(currentSkillName);

    if (skillSymbol != null && skillSymbol.type.equals("skill")) {
        String declaredReturnType = skillSymbol.returnType.toLowerCase();

        // RUN THE MATCH COMPLIANCE EVALUATION
        if (!actualReturnType.equals("unknown") && !actualReturnType.equals(declaredReturnType)) {
            errors.add("Semantic Error at line " + line + ": Type Mismatch in function '" + currentSkillName + 
                       "'. Expected to return '" + declaredReturnType + "' but got '" + actualReturnType + "'.");
        }
    }
}

    private void handleAssignment(ParseTreeNode node) {
        // Layout assumption: [0] = ID (Target variable), [1] = '=', [2] = Expression Node
        String varName = extractValue(node.children.get(0));
        int line = extractLine(node.children.get(0));

        // Rule A: Check if target variable has been declared
        SymbolEntry variableSymbol = lookupVariable(varName);
        if (variableSymbol == null) {
            errors.add("Semantic Error at line " + line + ": Variable '" + varName + "' must be declared before it can be assigned a value.");
            return;
        }

        // Rule B: Verify target and expression value types match up safely
        if (node.children.size() > 2) {
            String expectedType = variableSymbol.type.toLowerCase();
            String derivedExprType = inferExpressionType(node.children.get(2));

            if (!derivedExprType.equals("unknown") && !derivedExprType.equals(expectedType)) {
                errors.add("Semantic Error at line " + line + ": Type mismatch. Cannot assign '" + derivedExprType + "' to a '" + expectedType + "' variable ('" + varName + "').");
            }
        }
    }

    private void handleSkillDeclaration(ParseTreeNode node) {
        String skillName  = extractValue(node.children.get(1)); 
        int line          = extractLine(node.children.get(1));
        
        String returnType = "void"; 
        if (node.children.size() > 6) {
            returnType = extractValue(node.children.get(6));
        }

        if (existsInCurrentScope(skillName)) {
            errors.add("Semantic Error at line " + line + ": Function '" + skillName + "' already declared globally.");
            return;
        }

        SymbolEntry skillEntry = new SymbolEntry();
        skillEntry.name = skillName;
        skillEntry.type = "skill";
        skillEntry.scope = currentScopeName;
        skillEntry.line = line;
        skillEntry.returnType = returnType.toLowerCase(); 
        
        List<String> cleanParams = new ArrayList<>();
        ParseTreeNode paramList = findChildByLabel(node, "ParamList");
        if (paramList != null) {
            extractParametersRecursive(paramList, cleanParams);
        }
        skillEntry.paramTypes = cleanParams;
        skillEntry.paramCount = cleanParams.size();

        addSymbol(skillEntry);
        enterScope(skillName); 

        if (paramList != null) {
            injectParametersAsLocalVariables(paramList, line);
        }
      
        ParseTreeNode bodyNode = findChildByLabel(node, "StmtList");
        if (bodyNode != null) {
            visit(bodyNode);
        }

        exitScope();
    }

private void handleVariableDeclaration(ParseTreeNode node) {
    String type = extractValue(node.children.get(0)).toLowerCase(); // "number", "fact", etc.
    String varName = extractValue(node.children.get(1));
    int line = extractLine(node.children.get(1));

    // Check for duplicate declarations
    if (existsInCurrentScope(varName)) {
        errors.add("Semantic Error at line " + line + ": Variable '" + varName + "' is already declared.");
        return;
    }

    // Add to symbol table
    SymbolEntry entry = new SymbolEntry();
    entry.name = varName;
    entry.type = type; 
    entry.scope = currentScopeName;
    entry.line = line;
    addSymbol(entry);

    //: Search for the actual Expression child node 
    ParseTreeNode expressionValueNode = findChildByLabel(node, "Expr");
    
    // Fallback: if your tree doesn't use the label "Expr", the value is at index 3
    if (expressionValueNode == null && node.children.size() > 3) {
        expressionValueNode = node.children.get(3); 
    }

    if (expressionValueNode != null) {
        String derivedExprType = inferExpressionType(expressionValueNode);
        
       
        if (derivedExprType.equals("integer")) {
            derivedExprType = "number";
        }

        // Strict validation check
        if (!derivedExprType.equals("unknown") && !derivedExprType.equals(type)) {
            errors.add("Semantic Error at line " + line + ": Type mismatch. Cannot initialize '" + type + "' variable '" + varName + "' with a '" + derivedExprType + "' value.");
        }
    }
}


    //  Parser Helpers 
    private String extractValue(ParseTreeNode node) {
        if (node == null) return "unknown";
        if (node.label.startsWith("Match:")) {
            return node.label.substring(6).trim();
        }
        if (!node.children.isEmpty()) {
            return extractValue(node.children.get(0));
        }
        return node.label;
    }

    private int extractLine(ParseTreeNode node) {
        return node.lineInfo; 
    }

    private ParseTreeNode findChildByLabel(ParseTreeNode parent, String label) {
        for (ParseTreeNode child : parent.children) {
            if (child.label.equals(label)) return child;
        }
        return null;
    }

    private void extractParametersRecursive(ParseTreeNode node, List<String> cleanParams) {
        if (node == null) return;

        if (node.label.equals("Type")) {
            String typeValue = extractValue(node); 
            cleanParams.add(typeValue.toLowerCase());
        }

        for (ParseTreeNode child : node.children) {
            extractParametersRecursive(child, cleanParams);
        }
    }

    private void injectParametersAsLocalVariables(ParseTreeNode node, int line) {
        if (node == null) return;

        if (node.label.equals("ParamList") || node.label.equals("ParamList'")) {
            ParseTreeNode typeNode = null;
            ParseTreeNode idNode = null;

            for (ParseTreeNode child : node.children) {
                if (child.label.equals("Type")) {
                    typeNode = child;
                } else if (child.label.startsWith("Match:") && typeNode != null && idNode == null) {
                    String cleanVal = child.label.substring(6).trim();
                    if (!cleanVal.equals(",")) {
                        idNode = child;
                    }
                }
            }

            if (typeNode != null && idNode != null) {
                SymbolEntry pVar = new SymbolEntry();
                pVar.name = idNode.label.substring(6).trim(); 
                pVar.type = extractValue(typeNode);           
                pVar.scope = currentScopeName;                
                pVar.line = line;
                addSymbol(pVar);
            }
        }

        for (ParseTreeNode child : node.children) {
            injectParametersAsLocalVariables(child, line);
        }
    }



//  (Static Scoping Walk) 
  
    private SymbolEntry lookupVariable(String name) {
        // Clone stack elements or walk from top (inner) to bottom (global)
        for (int i = scopeStack.size() - 1; i >= 0; i--) {
            Map<String, SymbolEntry> activeLayer = scopeStack.get(i);
            if (activeLayer.containsKey(name.toLowerCase())) {
                return activeLayer.get(name.toLowerCase());
            }
        }
        return null; // Variable not declared anywhere accessible
    }


private String inferExpressionType(ParseTreeNode node) {
    if (node == null) return "unknown";

    String label = node.label;

    // ─── RULES 1, 2, 3, & 4: Terminal Leaf Node Analysis ───
if (label.startsWith("Match:")) {
    String rawVal = label.substring(6).trim();
    
    // 1. Strict String Literal (With or without Quotes)
    if (rawVal.startsWith("\"") && rawVal.endsWith("\"")) {
        return "string";
    }
    
    // 2. Boolean Logic Check
    if (rawVal.equalsIgnoreCase("TRUE") || rawVal.equalsIgnoreCase("FALSE")) {
        return "fact";
    }
    
    // 3. Numeric Literal Check
    if (rawVal.matches("-?\\d+(\\.\\d+)?")) {
        return "number";
    }
    
    // 4. Fallback String Check: If it's a raw alphabetical string value that isn't a declared variable
    SymbolEntry entry = lookupVariable(rawVal);
    if (entry != null) {
        return entry.type.toLowerCase();
    }
    
    // If it's regular text words and NOT a registered variable name, it's a raw string value!
    if (rawVal.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
        return "string"; 
    }

    return "unknown";
}

    //  RULE 8: Skill / Function Call Evaluation 
    if (label.equals("SkillCall") || label.equals("MethodCall") || label.equals("FuncCall")) {
        // Child 0 is usually the DO keyword terminal, Child 1 is the Skill name ID
        ParseTreeNode idNode = node.children.size() > 1 ? node.children.get(1) : node.children.get(0);
        String skillName = extractValue(idNode);
        SymbolEntry skillEntry = lookupVariable(skillName);
        
        if (skillEntry != null && skillEntry.type.equals("skill")) {
            return skillEntry.returnType.toLowerCase(); // Inferred return data type profile
        }
        return "unknown";
    }

    //  RULES 5, 6, & 7: Binary Expression Node Processing 
    if (label.equals("Expr") || label.equals("Term") || label.equals("Expr'") || label.equals("Term'")) {
        
        // Look for operational structural children configurations
        if (node.children.size() >= 3) {
            String leftType = inferExpressionType(node.children.get(0));
            String operator = extractValue(node.children.get(1));
            String rightType = inferExpressionType(node.children.get(2));

            // Rule 5: Arithmetic Rules
            if (operator.matches("[+\\-*/%]")) {
                if (leftType.equals("number") && rightType.equals("number")) {
                    return "number";
                } else if (!leftType.equals("unknown") && !rightType.equals("unknown")) {
                    errors.add("Semantic Error: Mathematical operations do not support combining '" + leftType + "' and '" + rightType + "'.");
                    return "unknown";
                }
            }

            // Rule 6: Relational Operators Check
            if (operator.matches("(<|>|<=|>=)")) {
                if (leftType.equals("number") && rightType.equals("number")) {
                    return "fact"; // Inferred to condition boolean logical type
                } else {
                    errors.add("Semantic Error: Comparative sizing bounds cannot evaluate non-numeric objects.");
                    return "unknown";
                }
            }

            // Rule 7: Strict Value Equality Checks
            if (operator.equals("==") || operator.equals("!=")) {
                if (leftType.equals(rightType) && !leftType.equals("unknown")) {
                    return "fact"; 
                } else if (!leftType.equals("unknown") && !rightType.equals("unknown")) {
                    errors.add("Semantic Error: Incompatible equality check. Cannot match a '" + leftType + "' against a '" + rightType + "'.");
                    return "unknown";
                }
            }
        }
    }

    // Pass structural navigation downward recursively if node is purely wrapper formatting
    if (!node.children.isEmpty()) {
        // Single child bypass optimization
        if (node.children.size() == 1) {
            return inferExpressionType(node.children.get(0));
        }
        
        // Multi-child evaluation scanning fallbacks
        for (ParseTreeNode child : node.children) {
            String childType = inferExpressionType(child);
            if (!childType.equals("unknown") && !childType.equals("operator")) {
                return childType;
            }
        }
    }

    return "unknown";
}







}