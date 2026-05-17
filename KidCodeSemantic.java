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

    // ── Public API ───────────────────────────────────────────────────────────
    public void analyze() {
        visit(root);
        exitScope(); // Close out global scope at the end
    }

    public List<String> getErrors() { return errors; }
    public List<SymbolEntry> getSymbolTable() { return symbolCache; }

    // ── Scope Management Lifecycle ───────────────────────────────────────────
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

    // ── Statement Implementations ────────────────────────────────────────────
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
            case "Decl" -> {
                handleVariableDeclaration(node);
            }
            case "SkillDecl" -> {
                handleSkillDeclaration(node);
                return; 
            }
            default -> {
                for (ParseTreeNode child : node.children) {
                    visit(child);
                }
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
        String type = extractValue(node.children.get(0));
        String varName = extractValue(node.children.get(1));
        int line = extractLine(node.children.get(1));

        if (existsInCurrentScope(varName)) {
            errors.add("Semantic Error at line " + line + ": Variable '" + varName + "' already declared in scope '" + currentScopeName + "'.");
            return;
        }

        SymbolEntry entry = new SymbolEntry();
        entry.name = varName;
        entry.type = type; 
        entry.scope = currentScopeName;
        entry.line = line;

        addSymbol(entry);
    }

    // ── Parser Helpers ────────────────────────────────────────────────────────
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
}