import java.util.*;

public class KidCodeSemantic {

    // ── SymbolEntry ──────────────────────────────────────────────────────────
    public static class SymbolEntry {
        String name;
        String type;                              // "number","name","fact","skill"
        String scope;                             // "global" or skill name
        int    line;
        // only for skills
        String       returnType;
        List<String> paramTypes  = new ArrayList<>();
        int          paramCount;


        public String toString() {
            return name + " | " + type + " | " + scope + " | line " + line
                 + (returnType != null ? " | returns " + returnType : "")
                 + (paramCount  > 0    ? " | params "  + paramTypes  : "");
        }
    }

    //  Fields 
    private final ParseTreeNode root;
    private final Stack<Map<String, SymbolEntry>> scopeStack = new Stack<>();
    private final List<String> errors = new ArrayList<>();
    private String currentScope = "global";
    private final List<SymbolEntry> allRegisteredEntries = new ArrayList<>(); // ← HERE

    // ── Constructor ──────────────────────────────────────────────────────────
    public KidCodeSemantic(ParseTreeNode root) {
        this.root = root;
        enterScope();          // open global scope
    }

    // ── Public API ───────────────────────────────────────────────────────────
    public void analyze() {
         collectSkills(root);
         visitNode(root);
    }

    private void collectSkills(ParseTreeNode node) {
    if (node == null) return;
    if (node.label.equals("SkillDecl")) {
        checkSkillDecl(node);
        return; // don't recurse into it again
    }
    for (ParseTreeNode child : node.children)
        collectSkills(child);
}

    public List<String> getErrors() {
        return errors;
    }

    public List<SymbolEntry> getAllEntries() {
        return allRegisteredEntries;
    }

    // ── Scope Helpers ────────────────────────────────────────────────────────
    private void enterScope() {
        scopeStack.push(new LinkedHashMap<>());
    }

    private void exitScope() {
        if (!scopeStack.isEmpty()) scopeStack.pop();
    }


  private void addEntry(SymbolEntry e) {
    scopeStack.peek().put(e.name.toLowerCase(), e);
    allRegisteredEntries.add(e);
}

    private SymbolEntry lookup(String name) {
        for (int i = scopeStack.size() - 1; i >= 0; i--) {
            SymbolEntry e = scopeStack.get(i).get(name.toLowerCase());
            if (e != null) return e;
        }
        return null;
    }

    private boolean existsInCurrentScope(String name) {
        return !scopeStack.isEmpty()
            && scopeStack.peek().containsKey(name.toLowerCase());
    }

    // ── Tree Walker ──────────────────────────────────────────────────────────
private void visitNode(ParseTreeNode node) {
    if (node == null) return;
    switch (node.label) {
        case "Decl"      -> checkDecl(node);
        case "Assign"    -> checkAssign(node);
        case "SkillDecl" -> {} // already handled in pass 1
        case "SkillCall" -> checkSkillCall(node);
        default -> {
            for (ParseTreeNode child : node.children)
                visitNode(child);
        }
    }
}

    // ── 5.1.1  checkDecl ─────────────────────────────────────────────────────
    // Tree shape:  Decl → Type  ID  =  Expr
    //              Type → Match:number | Match:name | Match:fact
    //   children:  [0]=Type  [1]=Match:ID  [2]=Match:=  [3]=Expr
    private void checkDecl(ParseTreeNode node) {
        // --- 1. Get type from Type child (child 0 → its first child is the match)
        String type = extractType(node.children.get(0));

        // --- 2. Get identifier name from child 1  (label = "Match: x")
        String varName = extractMatchValue(node.children.get(1));

        // --- 3. Get line number by searching tokens list via the match label
        int line = extractLine(node.children.get(1));

        if (varName == null) {
            errors.add("Semantic Error: Could not read variable name in declaration.");
            return;
        }

        // --- 4. Duplicate check in SAME scope
        if (existsInCurrentScope(varName)) {
            errors.add("Semantic Error at line " + line
                + ": Variable '" + varName + "' already declared in this scope.");
            return;
        }

        // --- 5. Type-check the right-hand side expression (child 3)
        String exprType = inferType(node.children.get(3));
        if (exprType != null && !typesCompatible(type, exprType)) {
            errors.add("Type Error at line " + line
                + ": Cannot assign '" + exprType
                + "' to variable '" + varName + "' of type '" + type + "'.");
        }

        // --- 6. Register in symbol table
        SymbolEntry entry = new SymbolEntry();
        entry.name  = varName;
        entry.type  = type;
        entry.scope = currentScope;
        entry.line  = line;
        addEntry(entry);
    }

    // ── 5.1.1  checkSkillDecl ────────────────────────────────────────────────
    // Your grammar rule (from parser):
    //   Skills    → SkillDecl Skills
    //   SkillDecl → SKILL ID ( ParamList ) GIVES Type StmtList DONE
    //
    // Because SkillDecl is not yet in the parser grammar we handle it
    // by searching children for the recognisable pattern.
    private void checkSkillDecl(ParseTreeNode node) {
        // Walk children to find:  Match:skill  Match:<name>  ...  Match:gives  Type  StmtList
        String skillName  = null;
        String returnType = null;
        int    line       = 0;
        List<String> paramTypes  = new ArrayList<>();
        List<String> paramNames  = new ArrayList<>();
        ParseTreeNode stmtListNode = null;

        // Flatten all "Match:X" labels so we can read them in order
        List<ParseTreeNode> flat = new ArrayList<>();
        flattenMatches(node, flat);

        // Find skill name (token after "skill")
        for (int i = 0; i < flat.size(); i++) {
            String val = matchValue(flat.get(i));
            if (val != null && val.equalsIgnoreCase("skill") && i + 1 < flat.size()) {
                skillName = matchValue(flat.get(i + 1));
                line      = extractLine(flat.get(i + 1));
            }
            // Find return type (token after "gives")
            if (val != null && val.equalsIgnoreCase("gives") && i + 1 < flat.size()) {
                returnType = matchValue(flat.get(i + 1));
            }
        }

        // Collect parameters: pairs of (type, name) between ( and )
        boolean inParams = false;
        for (int i = 0; i < flat.size(); i++) {
            String val = matchValue(flat.get(i));
            if ("(".equals(val))  { inParams = true;  continue; }
            if (")".equals(val))  { inParams = false; continue; }
            if (inParams && val != null) {
                // Even index = type, odd index = name
                if (paramTypes.size() == paramNames.size())
                    paramTypes.add(val.toLowerCase());
                else
                    paramNames.add(val.toLowerCase());
            }
        }

        // Find StmtList child for body analysis
        for (ParseTreeNode child : node.children)
            if (child.label.equals("StmtList")) stmtListNode = child;

        if (skillName == null) {
            errors.add("Semantic Error: Could not parse skill declaration.");
            return;
        }

        // Duplicate skill check
        if (existsInCurrentScope(skillName)) {
            errors.add("Semantic Error at line " + line
                + ": Skill '" + skillName + "' already declared.");
            return;
        }

        // Register skill in CURRENT (global) scope
        SymbolEntry skillEntry = new SymbolEntry();
        skillEntry.name       = skillName;
        skillEntry.type       = "skill";
        skillEntry.scope      = currentScope;
        skillEntry.line       = line;
        skillEntry.returnType = returnType != null ? returnType.toLowerCase() : "void";
        skillEntry.paramTypes = paramTypes;
        skillEntry.paramCount = paramTypes.size();
        addEntry(skillEntry);

        // Open new scope for skill body
        String prevScope = currentScope;
        currentScope = skillName;
        enterScope();

        // Add parameters into skill scope
        for (int i = 0; i < paramNames.size(); i++) {
            SymbolEntry param = new SymbolEntry();
            param.name  = paramNames.get(i);
            param.type  = i < paramTypes.size() ? paramTypes.get(i) : "unknown";
            param.scope = skillName;
            param.line  = line;
            addEntry(param);
        }

        // Check skill body
        if (stmtListNode != null) visitNode(stmtListNode);

        // Check return statement exists for non-void skills
        if (!"void".equals(skillEntry.returnType)) {
            boolean hasReturn = hasReturnStatement(node);
            if (!hasReturn)
                errors.add("Semantic Error at line " + line
                    + ": Skill '" + skillName
                    + "' must contain a 'give back' statement.");
        }

        exitScope();
        currentScope = prevScope;
    }

    // ── checkAssign ──────────────────────────────────────────────────────────
    // Tree: Assign → ID = Expr
    private void checkAssign(ParseTreeNode node) {
        String varName = extractMatchValue(node.children.get(0));
        int    line    = extractLine(node.children.get(0));

        if (varName == null) return;

        // Variable must be declared
        SymbolEntry entry = lookup(varName);
        if (entry == null) {
            errors.add("Semantic Error at line " + line
                + ": Variable '" + varName + "' used but not declared.");
            return;
        }

        // Type check RHS (child index 2 = Expr, after ID and =)
        if (node.children.size() > 2) {
            String exprType = inferType(node.children.get(2));
            if (exprType != null && !typesCompatible(entry.type, exprType)) {
                errors.add("Type Error at line " + line
                    + ": Cannot assign '" + exprType
                    + "' to variable '" + varName
                    + "' of type '" + entry.type + "'.");
            }
        }
    }

    // ── checkSkillCall ───────────────────────────────────────────────────────
    // Tree: SkillCall → DO ID ( ArgList )
    private void checkSkillCall(ParseTreeNode node) {
        List<ParseTreeNode> flat = new ArrayList<>();
        flattenMatches(node, flat);

        String skillName = null;
        int    line      = 0;
        List<String> argTypes = new ArrayList<>();

        boolean inArgs   = false;
        boolean nameNext = false;

        for (ParseTreeNode n : flat) {
            String val = matchValue(n);
            if (val == null) continue;
            if (val.equalsIgnoreCase("do")) { nameNext = true; continue; }
            if (nameNext) { skillName = val; line = extractLine(n); nameNext = false; continue; }
            if ("(".equals(val)) { inArgs = true;  continue; }
            if (")".equals(val)) { inArgs = false; continue; }
            if (inArgs) argTypes.add(inferLiteralType(val));
        }

        if (skillName == null) return;

        SymbolEntry skill = lookup(skillName);
        if (skill == null) {
            errors.add("Semantic Error at line " + line
                + ": Skill '" + skillName + "' not declared.");
            return;
        }

        // Argument count check
        if (argTypes.size() != skill.paramCount) {
            errors.add("Semantic Error at line " + line
                + ": Skill '" + skillName + "' expects "
                + skill.paramCount + " argument(s) but got " + argTypes.size() + ".");
            return;
        }

        // Argument type check
        for (int i = 0; i < argTypes.size(); i++) {
            if (!typesCompatible(skill.paramTypes.get(i), argTypes.get(i))) {
                errors.add("Type Error at line " + line
                    + ": Argument " + (i + 1) + " of skill '" + skillName
                    + "' expected '" + skill.paramTypes.get(i)
                    + "' but got '" + argTypes.get(i) + "'.");
            }
        }
    }

    // ── Type Inference (8 Rules) ─────────────────────────────────────────────
    private String inferType(ParseTreeNode node) {
        if (node == null) return null;

        // Rule 1: integer literal → number
        if (node.label.startsWith("Match:")) {
            String val = node.label.substring(6).trim();
            return inferLiteralType(val);
        }

        // Recurse and collect child types
        List<String> childTypes = new ArrayList<>();
        for (ParseTreeNode child : node.children) {
            String t = inferType(child);
            if (t != null) childTypes.add(t);
        }

        if (childTypes.isEmpty()) return null;
        if (childTypes.size() == 1) return childTypes.get(0);

        // Rule 2: number op number → number
        if (childTypes.stream().allMatch(t -> t.equals("number"))) return "number";
        // Rule 3: number + float → float
        if (childTypes.stream().allMatch(t -> t.equals("number") || t.equals("float"))) return "float";
        // Rule 4: name + name → name  (string concat)
        if (childTypes.stream().allMatch(t -> t.equals("name"))) return "name";
        // Rule 5: fact op fact → fact
        if (childTypes.stream().allMatch(t -> t.equals("fact"))) return "fact";

        return childTypes.get(0);
    }

    // Rule 6: literal value → type
    // Rule 7: true/false → fact
    // Rule 8: quoted string → name
    private String inferLiteralType(String val) {
        if (val == null) return null;
        if (val.matches("-?\\d+"))           return "number";   // Rule 6a
        if (val.matches("-?\\d+\\.\\d+"))    return "float";    // Rule 6b
        if (val.equalsIgnoreCase("true")
         || val.equalsIgnoreCase("false"))   return "fact";     // Rule 7
        if (val.startsWith("\""))            return "name";     // Rule 8
        // identifier — look it up
        SymbolEntry e = lookup(val);
        return e != null ? e.type : null;
    }

    // ── Compatibility ─────────────────────────────────────────────────────────
    private boolean typesCompatible(String declared, String actual) {
        if (declared == null || actual == null) return true;
        if (declared.equals(actual))            return true;
        // number and float are cross-compatible
        if ((declared.equals("number") || declared.equals("float"))
         && (actual.equals("number")   || actual.equals("float"))) return true;
        return false;
    }

    // ── Return Statement Detection ────────────────────────────────────────────
    private boolean hasReturnStatement(ParseTreeNode node) {
        if (node == null) return false;
        if (node.label.startsWith("Match:")) {
            String val = node.label.substring(6).trim();
            if (val.equalsIgnoreCase("give") || val.equalsIgnoreCase("back")) return true;
        }
        for (ParseTreeNode child : node.children)
            if (hasReturnStatement(child)) return true;
        return false;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    // Extract the KidCode type keyword from a Type node
    private String extractType(ParseTreeNode typeNode) {
        if (typeNode == null) return "unknown";
        if (!typeNode.children.isEmpty())
            return extractMatchValue(typeNode.children.get(0));
        return extractMatchValue(typeNode);
    }

    // "Match: number" → "number"
    private String extractMatchValue(ParseTreeNode node) {
        if (node == null) return null;
        if (node.label.startsWith("Match:"))
            return node.label.substring(6).trim().toLowerCase();
        if (!node.children.isEmpty())
            return extractMatchValue(node.children.get(0));
        return null;
    }

    // Try to get a line number stored in a Match node
    // (Parse tree doesn't store line numbers directly, so we return 0 as fallback)
    private int extractLine(ParseTreeNode node) {
        // Line info is not stored in ParseTreeNode labels in your current design.
        // Return 0; you can enhance ParseTreeNode later to carry line numbers.
        return 0;
    }

    // Collect all Match: nodes recursively
    private void flattenMatches(ParseTreeNode node, List<ParseTreeNode> out) {
        if (node == null) return;
        if (node.label.startsWith("Match:")) out.add(node);
        for (ParseTreeNode child : node.children)
            flattenMatches(child, out);
    }

    private String matchValue(ParseTreeNode node) {
        if (node == null || !node.label.startsWith("Match:")) return null;
        return node.label.substring(6).trim();
    }
}