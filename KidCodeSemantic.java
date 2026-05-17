import java.util.*;
import java.util.regex.*;

public class KidCodeSemantic {
    private ParseTreeNode root; // from parser

        public static class SymbolEntry { // enhanced symbol table entry
        String name;
        String type;        
        String scope;     // global, local, or skill   
        int line;
        String returnType;        
        List<String> paramTypes = new ArrayList<>();  // for skills
        int paramCount;            
    }
    //  the enhanced symbol table
    private Stack<Map<String, SymbolEntry>> scopeStack = new Stack<>();
    private List<String> errors = new ArrayList<>();
    private String currentScope = "global";

    public KidCodeSemantic(ParseTreeNode root) {
        this.root = root;
        enterScope(); // global scope
    }


    private void enterScope() {
        scopeStack.push(new LinkedHashMap<>());
    }

    private void exitScope() {
        if (!scopeStack.isEmpty()) scopeStack.pop();
    }

    private void addEntry(SymbolEntry entry) {
        scopeStack.peek().put(entry.name.toLowerCase(), entry);
    }

    private SymbolEntry lookup(String name) {
        for (int i = scopeStack.size() - 1; i >= 0; i--) {
            SymbolEntry e = scopeStack.get(i).get(name.toLowerCase());
            if (e != null) return e;
        }
        return null;
    }

    private boolean existsInCurrentScope(String name) {
        return !scopeStack.isEmpty() && scopeStack.peek().containsKey(name.toLowerCase());
    }



    public List<String> getErrors() { return errors; }
    
    // Returns all entries across all scopes for GUI display
    public List<SymbolEntry> getAllEntries() {
        List<SymbolEntry> all = new ArrayList<>();
        for (Map<String, SymbolEntry> scope : scopeStack)
            all.addAll(scope.values());
        return all;
    }
}