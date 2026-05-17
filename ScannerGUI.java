import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class ScannerGUI extends JFrame {
    //text area and labels
    JTextArea inputArea = new JTextArea();
    JTextArea errorArea = new JTextArea();
    JLabel countLabel = new JLabel("Total Lexemes: 0");

   // tables
    JTable tokenTable = new JTable(new DefaultTableModel(
            new String[] { "Type", "Line", "Value" }, 0));

 JTable symbolTable = new JTable(new DefaultTableModel(
    new String[] { "Identifier", "Type", "Scope", "Line", "Return", "Params" }, 0));
    public ScannerGUI() {
        setTitle("Kid-Code Compiler");
        setSize(900, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // MAIN PANEL (3 EQUAL ROWS)
        JPanel main = new JPanel(new GridLayout(3, 1));

        // INPUT
        inputArea.setBorder(BorderFactory.createTitledBorder("Source Code"));
        main.add(new JScrollPane(inputArea));

        // TABLES
        JPanel tablesPanel = new JPanel(new GridLayout(1, 2));

        JPanel tokenPanel = new JPanel(new BorderLayout());
        tokenPanel.setBorder(BorderFactory.createTitledBorder("Token List"));
        tokenPanel.add(new JScrollPane(tokenTable));

        JPanel symbolPanel = new JPanel(new BorderLayout());
        symbolPanel.setBorder(BorderFactory.createTitledBorder("Symbol Table"));
        symbolPanel.add(new JScrollPane(symbolTable));

        tablesPanel.add(tokenPanel);
        tablesPanel.add(symbolPanel);
        main.add(tablesPanel);

        // ERRORS
        errorArea.setForeground(Color.BLACK); 
        errorArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        errorArea.setBorder(BorderFactory.createTitledBorder("Output & Parsing Tree"));
        main.add(new JScrollPane(errorArea));


        // TOP BAR
        JButton runBtn = new JButton("Run Compiler");
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topBar.add(countLabel);
        topBar.add(runBtn);
        // Add this inside the ScannerGUI() constructor after creating runBtn
        runBtn.addActionListener(e -> {
    // 1. Clear previous results
    DefaultTableModel tokenModel = (DefaultTableModel) tokenTable.getModel();
    DefaultTableModel symbolModel = (DefaultTableModel) symbolTable.getModel();
    tokenModel.setRowCount(0);
    symbolModel.setRowCount(0);
    errorArea.setText("");
    errorArea.setForeground(Color.BLACK); // Reset color to black

    // 2. Initialize Scanner
    KidCode.ScannerCore scanner = new KidCode.ScannerCore();
    String sourceCode = inputArea.getText();
    String[] lines = sourceCode.split("\n");

    // 3. Process each line
    for (int i = 0; i < lines.length; i++) {
        scanner.processLine(lines[i], i + 1);
    }
    // Ensure the EOF token is added for the parser to terminate correctly
    scanner.tokens.add(new KidCode.Token(KidCode.TokenType.EOF, -1, "$"));

    // 4. Update Token and Symbol Tables
    countLabel.setText("Total Lexemes: " + scanner.totalLexemes);
    boolean lexicalError = false;

    for (KidCode.Token t : scanner.tokens) {
        if (t.type == KidCode.TokenType.ERROR) {
            errorArea.setForeground(Color.RED);
            errorArea.append("Lexical Error Line " + t.line + ": " + t.lexeme + "\n");
            lexicalError = true;
        }
        tokenModel.addRow(new Object[] { t.type, t.line, t.lexeme });
    }

    for (var entry : scanner.symbolTable.table.entrySet()) {
        symbolModel.addRow(new Object[] { entry.getKey(), entry.getValue() });
    }

    // 5. Run Parser (Only if Lexical phase passed)
    if (!lexicalError) {
        try {
            // Initialize parser with the scanned tokens
            KidCodeParser parser = new KidCodeParser(scanner.tokens);
            
            // Execute the predictive parsing algorithm
            parser.parse(); 

            // Display the SUCCESS and the Parsing Trace Table
            errorArea.setForeground(new Color(0, 100, 0)); // Dark Green for success
            errorArea.setText(parser.getParseTree());


                   KidCodeSemantic semantic = new KidCodeSemantic(parser.getRoot());
        semantic.analyze();

          // Refresh symbol table with enhanced data
                symbolModel.setRowCount(0);
for (KidCodeSemantic.SymbolEntry e4 : semantic.getAllEntries()) {
    symbolModel.addRow(new Object[] {
        e4.name,
        e4.type,
        e4.scope,
        e4.line,
        e4.returnType != null ? e4.returnType : "-",
        e4.paramTypes.isEmpty() ? "-" : e4.paramTypes.toString()
    });

}


        // Show semantic errors or success
        if (semantic.getErrors().isEmpty()) {
            errorArea.append("\n\n✓ Semantic Analysis Passed");
        } else {
            errorArea.setForeground(Color.RED);
            errorArea.append("\n\n--- Semantic Errors ---\n");
            for (String err : semantic.getErrors()) {
                errorArea.append(err + "\n");
            }
        }

     }     catch (Exception ex) {
            // Display Syntax Errors
            errorArea.setForeground(Color.RED);
            errorArea.setText("--- Syntax Error ---\n");
            errorArea.append(ex.getMessage());
        }
    }
});
        // add components 
        add(topBar, BorderLayout.NORTH);
        add(main, BorderLayout.CENTER);

    }

    
    public static void main(String[] args) {
        new ScannerGUI().setVisible(true);
    }}