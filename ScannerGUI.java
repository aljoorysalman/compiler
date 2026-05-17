import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class ScannerGUI extends JFrame {
    // text area and labels
    JTextArea inputArea = new JTextArea();
    JTextArea errorArea = new JTextArea();
    JLabel countLabel = new JLabel("Total Lexemes: 0");
    // tables
    JTable tokenTable = new JTable(new DefaultTableModel(
            new String[] { "Type", "Line", "Value" }, 0));
    JTable symbolTable = new JTable(new DefaultTableModel(
            new String[] { "Identifier", "Line" }, 0));

    public ScannerGUI() {
        setTitle("Lexical Analyzer");
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
        errorArea.setForeground(Color.RED);
        errorArea.setBorder(BorderFactory.createTitledBorder("Errors"));
        main.add(new JScrollPane(errorArea));
        // TOP BAR
        JButton runBtn = new JButton("Run");
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
            // 2. Initialize Scanner
            KidCode.ScannerCore scanner = new KidCode.ScannerCore();
            String sourceCode = inputArea.getText();
            String[] lines = sourceCode.split("\n");
            // 3. Process each line
            for (int i = 0; i < lines.length; i++) {
                scanner.processLine(lines[i], i + 1);
            }
            scanner.tokens.add(new KidCode.Token(KidCode.TokenType.EOF, -1, "EOF"));
            // 4. Update UI
            countLabel.setText("Total Lexemes: " + scanner.totalLexemes);
            // fill token table and errors
            boolean hasErrors = false;
            for (KidCode.Token t : scanner.tokens) {
                if (t.type == KidCode.TokenType.ERROR) {
                    errorArea.append("Line " + t.line + ": " + t.lexeme + "\n");
                    hasErrors = true;
                }
                tokenModel.addRow(new Object[] { t.type, t.line, t.lexeme });
            }
            if (!hasErrors)

                errorArea.setText("No errors.");
            // Update Symbol Table UI
            for (var entry : scanner.symbolTable.table.entrySet()) {
                symbolModel.addRow(new Object[] { entry.getKey(), entry.getValue() });
            }
        });
        // add components
        add(topBar, BorderLayout.NORTH);
        add(main, BorderLayout.CENTER);
    }

    public static void main(String[] args) {
        new ScannerGUI().setVisible(true);
    }
}