package com.ali.javaagent.ui;

import com.ali.javaagent.metrics.MethodMetrics;
import com.ali.javaagent.metrics.MetricsRegistry;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Live Swing dashboard that visualises method-level runtime metrics.
 * <p>
 * Features:
 * <ul>
 *   <li>Auto-refreshes every 2 seconds via {@link javax.swing.Timer}</li>
 *   <li>Sorted by total execution time (descending) by default</li>
 *   <li>Highlights methods with average time &gt; 50 ms in red/orange</li>
 * </ul>
 *
 * @author Ali
 */
public class MetricsDashboard {

    /** Threshold in milliseconds — rows slower than this are highlighted. */
    private static final double SLOW_THRESHOLD_MS = 50.0;

    private static final String[] COLUMN_NAMES = {
            "Method Name", "Call Count", "Total Time (ms)", "Avg Time (ms)"
    };

    // ------------------------------------------------------------------ model

    private static final class MetricsTableModel extends AbstractTableModel {

        private List<Object[]> rows = new ArrayList<>();

        void refreshData() {
            Map<String, MethodMetrics> snapshot = MetricsRegistry.getInstance().getMetricsSnapshot();
            List<Object[]> newRows = new ArrayList<>(snapshot.size());
            for (Map.Entry<String, MethodMetrics> entry : snapshot.entrySet()) {
                MethodMetrics m = entry.getValue();
                double totalMs = m.getTotalTimeNanos() / 1_000_000.0;
                double avgMs   = m.getAverageTimeNanos() / 1_000_000.0;
                newRows.add(new Object[]{
                        entry.getKey(),
                        m.getCallCount(),
                        totalMs,
                        avgMs
                });
            }
            // Sort by total time descending
            newRows.sort(Comparator.<Object[], Double>comparing(r -> (Double) r[2]).reversed());
            rows = newRows;
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMN_NAMES.length; }
        @Override public String getColumnName(int col) { return COLUMN_NAMES[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            return rows.get(row)[col];
        }

        @Override
        public Class<?> getColumnClass(int col) {
            switch (col) {
                case 1:  return Long.class;
                case 2:
                case 3:  return Double.class;
                default: return String.class;
            }
        }

        double getAvgMs(int row) {
            if (row < 0 || row >= rows.size()) return 0;
            return (Double) rows.get(row)[3];
        }
    }

    // --------------------------------------------------------------- renderer

    private static final class SlowMethodRenderer extends DefaultTableCellRenderer {

        private final MetricsTableModel model;

        SlowMethodRenderer(MetricsTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);

            if (!isSelected) {
                int modelRow = table.convertRowIndexToModel(row);
                if (model.getAvgMs(modelRow) > SLOW_THRESHOLD_MS) {
                    c.setBackground(new Color(255, 235, 230));   // light red
                    c.setForeground(new Color(180, 30, 20));     // dark red text
                } else {
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
                }
            }

            // Right-align numeric columns
            if (col >= 1) {
                ((JLabel) c).setHorizontalAlignment(SwingConstants.RIGHT);
            } else {
                ((JLabel) c).setHorizontalAlignment(SwingConstants.LEFT);
            }

            // Format doubles to 3 decimal places
            if (value instanceof Double) {
                setText(String.format("%.3f", value));
            }

            return c;
        }
    }

    // --------------------------------------------------------- public API

    /**
     * Creates and shows the dashboard window.
     * Safe to call from any thread — internally delegates to the EDT.
     */
    public static void launch() {
        SwingUtilities.invokeLater(MetricsDashboard::createAndShow);
    }

    private static void createAndShow() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }

        JFrame frame = new JFrame("Method Metrics Dashboard");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(900, 500);
        frame.setLocationRelativeTo(null);

        MetricsTableModel tableModel = new MetricsTableModel();
        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        table.setFont(new Font("Consolas", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoCreateRowSorter(true);

        // Apply custom renderer to all columns
        SlowMethodRenderer renderer = new SlowMethodRenderer(tableModel);
        for (int i = 0; i < COLUMN_NAMES.length; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        // Set preferred column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(400);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Header label
        JLabel header = new JLabel("  \u26A1 Method Metrics Dashboard — refreshes every 1 s");
        header.setFont(new Font("Segoe UI", Font.BOLD, 15));
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(header, BorderLayout.NORTH);
        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);

        // Auto-refresh every 1 second
        Timer refreshTimer = new Timer(1000, e -> tableModel.refreshData());
        refreshTimer.setInitialDelay(500);
        refreshTimer.start();

        frame.setVisible(true);
    }

    /**
     * Standalone entry point — useful for testing the dashboard independently.
     */
    public static void main(String[] args) {
        // Seed some sample data for standalone testing
        MetricsRegistry reg = MetricsRegistry.getInstance();
        reg.updateMetric("com.example.Foo#bar", 120_000_000L);
        reg.updateMetric("com.example.Foo#bar", 80_000_000L);
        reg.updateMetric("com.example.Baz#process", 5_000_000L);
        reg.updateMetric("com.example.Baz#process", 3_000_000L);
        reg.updateMetric("com.example.SlowService#heavyWork", 200_000_000L);
        launch();
    }
}
