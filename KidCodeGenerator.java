import java.io.*;

public class KidCodeGenerator {

    private ParseTreeNode root;
    private StringBuilder code = new StringBuilder();

    public void KidCodeJavaFileGenerator(ParseTreeNode root) {
        this.root = root;
    }

    public void generate(String fileName) throws IOException {

        code.append("public class GeneratedProgram {\n");
        code.append("    public static void main(String[] args) {\n\n");

        visit(root);

        code.append("\n    }\n");
        code.append("}\n");

        BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
        writer.write(code.toString());
        writer.close();
    }

    private void visit(ParseTreeNode node) {
        if (node == null) return;

        String label = node.label;

        if (label.equals("Decl")) {
            String type = get(node.children.get(0));
            String id = get(node.children.get(1));
            String expr = eval(node.children.get(3));

            code.append("        ")
                .append(mapType(type)).append(" ")
                .append(id).append(" = ")
                .append(expr).append(";\n");
        }

        else if (label.equals("Assign")) {
            String id = get(node.children.get(0));
            String expr = eval(node.children.get(2));

            code.append("        ")
                .append(id).append(" = ")
                .append(expr).append(";\n");
        }

        else if (label.equals("SayStmt")) {
            String expr = eval(node.children.get(2));

            code.append("        System.out.println(")
                .append(expr)
                .append(");\n");
        }

        else if (label.equals("IfStmt")) {
            String cond = eval(node.children.get(2));

            code.append("        if (")
                .append(cond)
                .append(") {\n");

            visit(node.children.get(5));

            code.append("        }\n");
        }

        else if (label.equals("WhileStmt")) {
            String cond = eval(node.children.get(2));

            code.append("        while (")
                .append(cond)
                .append(") {\n");

            visit(node.children.get(5));

            code.append("        }\n");
        }

        else {
            for (ParseTreeNode c : node.children) {
                visit(c);
            }
        }
    }

    private String eval(ParseTreeNode node) {
        if (node == null) return "";

        String label = node.label;

        if (label.startsWith("Match: ")) {
            return label.replace("Match: ", "");
        }

        if (node.children.isEmpty()) {
            return label;
        }

        if (label.equals("Expr")) {
            if (node.children.size() == 1)
                return eval(node.children.get(0));

            String left = eval(node.children.get(0));
            String right = eval(node.children.get(1));
            return left + " + " + right;
        }

        return "";
    }

    private String get(ParseTreeNode node) {
        return node.label.replace("Match: ", "");
    }

    private String mapType(String type) {
        switch (type) {
            case "NUMBER": return "int";
            case "FACT": return "boolean";
            case "NAME": return "String";
            default: return "int";
        }
    }
}