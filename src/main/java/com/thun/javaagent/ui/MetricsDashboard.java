package com.thun.javaagent.ui;

import com.thun.javaagent.agent.AgentConfig;
import com.thun.javaagent.metrics.MethodMetrics;
import com.thun.javaagent.metrics.MetricsRegistry;
import com.thun.javaagent.tracing.CallNode;
import com.thun.javaagent.tracing.CallTracer;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Premium dark-themed APM dashboard with tabbed interface.
 * <p>
 * Tabs:
 * <ol>
 *   <li><b>All Methods</b> — full metrics table with search, filtering, color-coded rows</li>
 *   <li><b>Slow Methods</b> — methods exceeding threshold, sorted by avg time</li>
 *   <li><b>Call Traces</b> — JTree of recent call trees</li>
 *   <li><b>Configuration</b> — live agent config controls</li>
 * </ol>
 *
 * @author Ali
 */
public class MetricsDashboard {

    // ─────────────────────────────────────────────── Color Palette (Dark Theme)
    private static final Color BG_DARK       = new Color(24, 24, 32);
    private static final Color BG_PANEL      = new Color(32, 33, 44);
    private static final Color BG_CARD       = new Color(40, 42, 56);
    private static final Color BG_TABLE      = new Color(36, 38, 50);
    private static final Color BG_TABLE_ALT  = new Color(42, 44, 58);
    private static final Color BG_HEADER     = new Color(50, 52, 68);
    private static final Color BG_INPUT      = new Color(48, 50, 66);

    private static final Color FG_PRIMARY    = new Color(230, 232, 240);
    private static final Color FG_SECONDARY  = new Color(160, 165, 185);
    private static final Color FG_ACCENT     = new Color(100, 180, 255);
    private static final Color FG_HEADER     = new Color(180, 185, 200);

    private static final Color SLOW_BG       = new Color(80, 30, 35);
    private static final Color SLOW_FG       = new Color(255, 100, 100);
    private static final Color WARN_BG       = new Color(70, 55, 25);
    private static final Color WARN_FG       = new Color(255, 200, 80);
    private static final Color OK_FG         = new Color(80, 210, 130);
    private static final Color ERROR_FG      = new Color(255, 85, 85);

    private static final Color ACCENT_BLUE   = new Color(80, 140, 255);
    private static final Color ACCENT_PURPLE = new Color(155, 100, 255);
    private static final Color ACCENT_CYAN   = new Color(60, 210, 210);

    private static final Font FONT_MONO      = new Font("JetBrains Mono", Font.PLAIN, 12);
    private static final Font FONT_MONO_FALLBACK = new Font("Consolas", Font.PLAIN, 12);
    private static final Font FONT_UI        = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_UI_BOLD   = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_TITLE     = new Font("Segoe UI", Font.BOLD, 18);
    private static final Font FONT_STAT       = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font FONT_STAT_LABEL = new Font("Segoe UI", Font.PLAIN, 11);

    private static Font monoFont() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (String name : ge.getAvailableFontFamilyNames()) {
            if (name.equals("JetBrains Mono")) return FONT_MONO;
        }
        return FONT_MONO_FALLBACK;
    }

    // ─────────────────────────────────────────────── Column Defs
    private static final String[] ALL_COLUMNS = {
            "Method", "Calls", "Total (ms)", "Avg (ms)", "Min (ms)", "Max (ms)", "Errors", "Err%"
    };

    private static final String[] SLOW_COLUMNS = {
            "Method", "Calls", "Avg (ms)", "Max (ms)", "Errors"
    };

    // ─────────────────────────────────────────────── Table Model
    private static final class MetricsTableModel extends AbstractTableModel {
        private final String[] columns;
        private final boolean slowOnly;
        private List<Object[]> rows = new ArrayList<>();
        private String filter = "";

        MetricsTableModel(String[] columns, boolean slowOnly) {
            this.columns = columns;
            this.slowOnly = slowOnly;
        }

        void setFilter(String f) {
            this.filter = f.toLowerCase();
        }

        void refreshData() {
            Map<String, MethodMetrics> snapshot = MetricsRegistry.getInstance().getMetricsSnapshot();
            long threshold = AgentConfig.getInstance().getSlowThresholdMs();
            List<Object[]> newRows = new ArrayList<>(snapshot.size());

            for (Map.Entry<String, MethodMetrics> entry : snapshot.entrySet()) {
                MethodMetrics m = entry.getValue();
                String key = entry.getKey();

                // Apply text filter
                if (!filter.isEmpty() && !key.toLowerCase().contains(filter)) continue;

                // Slow-only filter
                if (slowOnly && m.getAverageTimeMs() <= threshold) continue;

                if (slowOnly) {
                    newRows.add(new Object[]{
                            key, m.getCallCount(),
                            m.getAverageTimeMs(), m.getMaxTimeMs(),
                            m.getErrorCount()
                    });
                } else {
                    newRows.add(new Object[]{
                            key, m.getCallCount(),
                            m.getTotalTimeMs(), m.getAverageTimeMs(),
                            m.getMinTimeMs(), m.getMaxTimeMs(),
                            m.getErrorCount(),
                            m.getErrorRate() * 100.0
                    });
                }
            }

            // Sort by avg time descending (col index depends on mode)
            int avgCol = slowOnly ? 2 : 3;
            newRows.sort((a, b) -> Double.compare((Double) b[avgCol], (Double) a[avgCol]));
            rows = newRows;
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            if (row >= rows.size()) return null;
            return rows.get(row)[col];
        }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col == 0) return String.class;
            if (columns[col].equals("Calls") || columns[col].equals("Errors")) return Long.class;
            return Double.class;
        }

        double getAvgMs(int row) {
            if (row < 0 || row >= rows.size()) return 0;
            int avgCol = slowOnly ? 2 : 3;
            return (Double) rows.get(row)[avgCol];
        }

        double getErrorRate(int row) {
            if (slowOnly || row < 0 || row >= rows.size()) return 0;
            return (Double) rows.get(row)[7]; // Err% column
        }
    }

    // ─────────────────────────────────────────────── Renderer
    private static final class ApmCellRenderer extends DefaultTableCellRenderer {
        private final MetricsTableModel model;

        ApmCellRenderer(MetricsTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            c.setFont(monoFont());

            if (!isSelected) {
                int modelRow = table.convertRowIndexToModel(row);
                long threshold = AgentConfig.getInstance().getSlowThresholdMs();
                double avgMs = model.getAvgMs(modelRow);
                double errRate = model.getErrorRate(modelRow);

                if (avgMs > threshold) {
                    c.setBackground(SLOW_BG);
                    c.setForeground(SLOW_FG);
                } else if (avgMs > threshold * 0.5) {
                    c.setBackground(WARN_BG);
                    c.setForeground(WARN_FG);
                } else if (errRate > 10.0) {
                    c.setBackground(new Color(60, 25, 30));
                    c.setForeground(ERROR_FG);
                } else {
                    c.setBackground(row % 2 == 0 ? BG_TABLE : BG_TABLE_ALT);
                    c.setForeground(FG_PRIMARY);
                }
            } else {
                c.setBackground(ACCENT_BLUE.darker());
                c.setForeground(Color.WHITE);
            }

            // Alignment
            JLabel label = (JLabel) c;
            if (col >= 1) {
                label.setHorizontalAlignment(SwingConstants.RIGHT);
            } else {
                label.setHorizontalAlignment(SwingConstants.LEFT);
            }

            // Formatting
            if (value instanceof Double) {
                setText(String.format("%.3f", value));
            }

            label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            return c;
        }
    }

    // ─────────────────────────────────────────────── Launch
    public static void launch() {
        SwingUtilities.invokeLater(MetricsDashboard::createAndShow);
    }

    private static void createAndShow() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }

        // Global UI defaults for dark theme
        UIManager.put("Panel.background", BG_DARK);
        UIManager.put("OptionPane.background", BG_PANEL);
        UIManager.put("OptionPane.messageForeground", FG_PRIMARY);

        JFrame frame = new JFrame("⚡ Java Agent APM Dashboard");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(1200, 750);
        frame.setMinimumSize(new Dimension(900, 550));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BG_DARK);

        // ────────────── Header bar
        JPanel headerBar = createHeaderBar();

        // ────────────── Stats strip
        JLabel statMethods = createStatLabel("0");
        JLabel statInvocations = createStatLabel("0");
        JLabel statErrors = createStatLabel("0");
        JLabel statUptime = createStatLabel("0s");
        JPanel statsPanel = createStatsStrip(statMethods, statInvocations, statErrors, statUptime);

        // ────────────── Tabbed pane
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG_PANEL);
        tabs.setForeground(FG_PRIMARY);
        tabs.setFont(FONT_UI_BOLD);
        tabs.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));

        // Tab 1: All Methods
        MetricsTableModel allModel = new MetricsTableModel(ALL_COLUMNS, false);
        JPanel allMethodsTab = createTableTab(allModel, ALL_COLUMNS, "Search methods...");
        tabs.addTab("📊 All Methods", allMethodsTab);

        // Tab 2: Slow Methods
        MetricsTableModel slowModel = new MetricsTableModel(SLOW_COLUMNS, true);
        JPanel slowTab = createTableTab(slowModel, SLOW_COLUMNS, "Filter slow methods...");
        tabs.addTab("🐌 Slow Methods", slowTab);

        // Tab 3: Call Traces
        JPanel tracesTab = createTracesTab();
        tabs.addTab("🌳 Call Traces", tracesTab);

        // Tab 4: Configuration
        JPanel configTab = createConfigTab();
        tabs.addTab("⚙️ Configuration", configTab);

        // ────────────── Status bar
        JLabel statusLabel = new JLabel("  Ready");
        statusLabel.setFont(FONT_UI);
        statusLabel.setForeground(FG_SECONDARY);
        statusLabel.setBackground(BG_CARD);
        statusLabel.setOpaque(true);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BG_HEADER),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        // ────────────── Layout
        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setBackground(BG_DARK);
        topSection.add(headerBar, BorderLayout.NORTH);
        topSection.add(statsPanel, BorderLayout.SOUTH);

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(topSection, BorderLayout.NORTH);
        frame.getContentPane().add(tabs, BorderLayout.CENTER);
        frame.getContentPane().add(statusLabel, BorderLayout.SOUTH);

        // ────────────── Refresh timer
        javax.swing.Timer refreshTimer = new javax.swing.Timer(1000, e -> {
            allModel.refreshData();
            slowModel.refreshData();

            MetricsRegistry reg = MetricsRegistry.getInstance();
            statMethods.setText(String.valueOf(reg.getMethodCount()));
            statInvocations.setText(formatNumber(reg.getTotalInvocations()));
            statErrors.setText(String.valueOf(reg.getTotalErrors()));
            statUptime.setText(formatUptime(reg.getUptimeMillis()));

            AgentConfig cfg = AgentConfig.getInstance();
            long slowCount = reg.getSlowMethods(cfg.getSlowThresholdMs()).size();
            statusLabel.setText(String.format(
                    "  ● %d methods tracked  |  %d slow (>%dms)  |  HTTP: localhost:%d  |  Threshold: %dms",
                    reg.getMethodCount(), slowCount, cfg.getSlowThresholdMs(),
                    cfg.getHttpPort(), cfg.getSlowThresholdMs()
            ));
        });
        refreshTimer.setInitialDelay(300);
        refreshTimer.start();

        frame.setVisible(true);
    }

    // ─────────────────────────────────────────────── Header Bar
    private static JPanel createHeaderBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG_PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 62, 80)),
                BorderFactory.createEmptyBorder(14, 20, 14, 20)
        ));

        JLabel title = new JLabel("⚡ Java Agent APM");
        title.setFont(FONT_TITLE);
        title.setForeground(FG_ACCENT);

        JLabel subtitle = new JLabel("Lightweight Application Performance Monitor");
        subtitle.setFont(FONT_UI);
        subtitle.setForeground(FG_SECONDARY);

        JPanel titleGroup = new JPanel();
        titleGroup.setLayout(new BoxLayout(titleGroup, BoxLayout.Y_AXIS));
        titleGroup.setBackground(BG_PANEL);
        titleGroup.add(title);
        titleGroup.add(Box.createVerticalStrut(2));
        titleGroup.add(subtitle);

        // Clear metrics button
        JButton clearBtn = createStyledButton("Clear Metrics", ERROR_FG);
        clearBtn.addActionListener(e -> {
            MetricsRegistry.getInstance().clear();
            CallTracer.getInstance().clear();
        });

        bar.add(titleGroup, BorderLayout.WEST);
        bar.add(clearBtn, BorderLayout.EAST);
        return bar;
    }

    // ─────────────────────────────────────────────── Stats Strip
    private static JPanel createStatsStrip(JLabel methods, JLabel invocations, JLabel errors, JLabel uptime) {
        JPanel strip = new JPanel(new GridLayout(1, 4, 12, 0));
        strip.setBackground(BG_DARK);
        strip.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));

        strip.add(createStatCard("Methods Tracked", methods, ACCENT_BLUE));
        strip.add(createStatCard("Total Invocations", invocations, ACCENT_CYAN));
        strip.add(createStatCard("Total Errors", errors, ERROR_FG));
        strip.add(createStatCard("Uptime", uptime, ACCENT_PURPLE));

        return strip;
    }

    private static JPanel createStatCard(String label, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(accentColor.darker(), 1, true),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        // Top accent line
        JPanel accentLine = new JPanel();
        accentLine.setPreferredSize(new Dimension(0, 3));
        accentLine.setBackground(accentColor);

        JLabel titleLabel = new JLabel(label.toUpperCase());
        titleLabel.setFont(FONT_STAT_LABEL);
        titleLabel.setForeground(FG_SECONDARY);

        valueLabel.setFont(FONT_STAT);
        valueLabel.setForeground(accentColor);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BG_CARD);
        content.add(titleLabel, BorderLayout.NORTH);
        content.add(Box.createVerticalStrut(4), BorderLayout.CENTER);
        content.add(valueLabel, BorderLayout.SOUTH);

        card.add(accentLine, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);

        return card;
    }

    private static JLabel createStatLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_STAT);
        l.setForeground(FG_ACCENT);
        return l;
    }

    // ─────────────────────────────────────────────── Table Tab
    private static JPanel createTableTab(MetricsTableModel model, String[] columns, String placeholder) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));

        // Search bar
        JTextField searchField = new JTextField();
        searchField.setFont(FONT_UI);
        searchField.setBackground(BG_INPUT);
        searchField.setForeground(FG_PRIMARY);
        searchField.setCaretColor(FG_ACCENT);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(70, 72, 90), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        searchField.putClientProperty("JTextField.placeholderText", placeholder);

        // Add placeholder rendering manually (cross-platform)
        searchField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { searchField.repaint(); }
            @Override public void focusLost(FocusEvent e)   { searchField.repaint(); }
        });

        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBackground(BG_DARK);
        JLabel searchIcon = new JLabel("  🔍 ");
        searchIcon.setFont(FONT_UI);
        searchIcon.setForeground(FG_SECONDARY);
        searchPanel.add(searchIcon, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Table
        JTable table = new JTable(model);
        styleTable(table, model);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void update() { model.setFilter(searchField.getText()); model.refreshData(); }
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(55, 57, 72), 1));
        scrollPane.getViewport().setBackground(BG_TABLE);

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private static void styleTable(JTable table, MetricsTableModel model) {
        table.setBackground(BG_TABLE);
        table.setForeground(FG_PRIMARY);
        table.setGridColor(new Color(55, 57, 72));
        table.setSelectionBackground(ACCENT_BLUE.darker());
        table.setSelectionForeground(Color.WHITE);
        table.setFillsViewportHeight(true);
        table.setRowHeight(32);
        table.setFont(monoFont());
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setAutoCreateRowSorter(true);

        // Header
        table.getTableHeader().setBackground(BG_HEADER);
        table.getTableHeader().setForeground(FG_HEADER);
        table.getTableHeader().setFont(FONT_UI_BOLD);
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT_BLUE));
        table.getTableHeader().setReorderingAllowed(false);

        // Renderer
        ApmCellRenderer renderer = new ApmCellRenderer(model);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        // Column widths
        if (table.getColumnCount() >= 8) {
            table.getColumnModel().getColumn(0).setPreferredWidth(350);
            for (int i = 1; i < 8; i++) {
                table.getColumnModel().getColumn(i).setPreferredWidth(100);
            }
        } else if (table.getColumnCount() >= 5) {
            table.getColumnModel().getColumn(0).setPreferredWidth(400);
        }
    }

    // ─────────────────────────────────────────────── Traces Tab
    private static JPanel createTracesTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Call Traces (most recent first)");
        JTree tree = new JTree(rootNode);
        tree.setBackground(BG_TABLE);
        tree.setForeground(FG_PRIMARY);
        tree.setFont(monoFont());
        tree.setRowHeight(26);

        // Custom renderer
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            {
                setBackgroundNonSelectionColor(BG_TABLE);
                setBackgroundSelectionColor(ACCENT_BLUE.darker());
                setTextNonSelectionColor(FG_PRIMARY);
                setTextSelectionColor(Color.WHITE);
            }

            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean sel, boolean expanded,
                                                          boolean leaf, int row, boolean hasFocus) {
                Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                c.setFont(monoFont());
                String text = value.toString();
                if (text.contains("ms") && !text.startsWith("Call Traces")) {
                    // Color based on duration
                    try {
                        String durStr = text.substring(text.lastIndexOf("(") + 1, text.lastIndexOf("ms")).trim();
                        double dur = Double.parseDouble(durStr);
                        if (dur > AgentConfig.getInstance().getSlowThresholdMs()) {
                            setForeground(SLOW_FG);
                        } else if (dur > AgentConfig.getInstance().getSlowThresholdMs() * 0.5) {
                            setForeground(WARN_FG);
                        } else {
                            setForeground(OK_FG);
                        }
                    } catch (Exception ignored) {}
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(55, 57, 72), 1));
        scrollPane.getViewport().setBackground(BG_TABLE);

        JButton refreshBtn = createStyledButton("Refresh Traces", ACCENT_CYAN);
        refreshBtn.addActionListener(e -> {
            rootNode.removeAllChildren();
            List<CallNode> traces = CallTracer.getInstance().getRecentTraces(30);
            for (int i = 0; i < traces.size(); i++) {
                CallNode trace = traces.get(i);
                DefaultMutableTreeNode traceNode = new DefaultMutableTreeNode(
                        String.format("#%d  %s (%.2fms) [%s]", i + 1, trace.getMethodKey(),
                                trace.getDurationMs(), trace.getThreadName())
                );
                addCallNodeChildren(traceNode, trace);
                rootNode.add(traceNode);
            }
            ((DefaultTreeModel) tree.getModel()).reload();
            // Expand first few nodes
            for (int i = 0; i < Math.min(5, tree.getRowCount()); i++) {
                tree.expandRow(i);
            }
        });

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG_DARK);
        JLabel tracesTitle = new JLabel("  🌳 Recent Call Trees  ");
        tracesTitle.setFont(FONT_UI_BOLD);
        tracesTitle.setForeground(FG_ACCENT);
        topBar.add(tracesTitle, BorderLayout.WEST);
        topBar.add(refreshBtn, BorderLayout.EAST);

        panel.add(topBar, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private static void addCallNodeChildren(DefaultMutableTreeNode parent, CallNode node) {
        for (CallNode child : node.getChildren()) {
            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(
                    String.format("%s (%.2fms)", child.getMethodKey(), child.getDurationMs())
            );
            addCallNodeChildren(childNode, child);
            parent.add(childNode);
        }
    }

    // ─────────────────────────────────────────────── Config Tab
    private static JPanel createConfigTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBackground(BG_CARD);
        grid.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(60, 62, 80), 1, true),
                BorderFactory.createEmptyBorder(20, 24, 20, 24)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        AgentConfig cfg = AgentConfig.getInstance();

        // Title
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JLabel configTitle = new JLabel("⚙️  Agent Configuration");
        configTitle.setFont(FONT_TITLE);
        configTitle.setForeground(FG_ACCENT);
        grid.add(configTitle, gbc);
        row++;

        gbc.gridwidth = 1;

        // Enabled toggle
        gbc.gridx = 0; gbc.gridy = row;
        grid.add(createConfigLabel("Instrumentation"), gbc);
        gbc.gridx = 1;
        JToggleButton enabledToggle = new JToggleButton(cfg.isEnabled() ? "● ENABLED" : "○ DISABLED");
        enabledToggle.setSelected(cfg.isEnabled());
        styleToggle(enabledToggle);
        enabledToggle.addActionListener(e -> {
            cfg.setEnabled(enabledToggle.isSelected());
            enabledToggle.setText(enabledToggle.isSelected() ? "● ENABLED" : "○ DISABLED");
            enabledToggle.setForeground(enabledToggle.isSelected() ? OK_FG : ERROR_FG);
        });
        grid.add(enabledToggle, gbc);
        row++;

        // Slow threshold
        gbc.gridx = 0; gbc.gridy = row;
        grid.add(createConfigLabel("Slow Threshold (ms)"), gbc);
        gbc.gridx = 1;
        JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(
                (int) cfg.getSlowThresholdMs(), 1, 10000, 10));
        thresholdSpinner.setFont(FONT_UI);
        thresholdSpinner.setBackground(BG_INPUT);
        thresholdSpinner.setForeground(FG_PRIMARY);
        thresholdSpinner.addChangeListener(e ->
                cfg.setSlowThresholdMs((Integer) thresholdSpinner.getValue()));
        grid.add(thresholdSpinner, gbc);
        row++;

        // Target package (read-only display)
        gbc.gridx = 0; gbc.gridy = row;
        grid.add(createConfigLabel("Target Package"), gbc);
        gbc.gridx = 1;
        JLabel targetLabel = new JLabel(cfg.getTargetPackage());
        targetLabel.setFont(monoFont());
        targetLabel.setForeground(ACCENT_CYAN);
        grid.add(targetLabel, gbc);
        row++;

        // HTTP Port (read-only display)
        gbc.gridx = 0; gbc.gridy = row;
        grid.add(createConfigLabel("HTTP Endpoint"), gbc);
        gbc.gridx = 1;
        JLabel httpLabel = new JLabel("http://localhost:" + cfg.getHttpPort() + "/metrics");
        httpLabel.setFont(monoFont());
        httpLabel.setForeground(ACCENT_CYAN);
        grid.add(httpLabel, gbc);
        row++;

        // Export path
        gbc.gridx = 0; gbc.gridy = row;
        grid.add(createConfigLabel("Metrics Export Path"), gbc);
        gbc.gridx = 1;
        JLabel exportLabel = new JLabel(cfg.getMetricsExportPath());
        exportLabel.setFont(monoFont());
        exportLabel.setForeground(FG_SECONDARY);
        grid.add(exportLabel, gbc);
        row++;

        // Endpoints info
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JPanel endpointsCard = createEndpointsCard(cfg.getHttpPort());
        grid.add(endpointsCard, gbc);

        panel.add(grid, BorderLayout.NORTH);
        return panel;
    }

    private static JPanel createEndpointsCard(int port) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(30, 35, 50));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ACCENT_BLUE.darker(), 1, true),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel title = new JLabel("📡  Available HTTP Endpoints");
        title.setFont(FONT_UI_BOLD);
        title.setForeground(FG_ACCENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(8));

        String[] endpoints = {
                "GET  /metrics       — Full metrics JSON",
                "GET  /metrics/slow  — Slow methods only",
                "GET  /metrics/traces— Recent call trees",
                "GET  /health        — Health check",
                "GET  /config        — Current config",
                "POST /config        — Update config"
        };

        for (String ep : endpoints) {
            JLabel l = new JLabel("    " + ep);
            l.setFont(monoFont());
            l.setForeground(FG_SECONDARY);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(l);
            card.add(Box.createVerticalStrut(3));
        }

        return card;
    }

    private static JLabel createConfigLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_UI_BOLD);
        l.setForeground(FG_PRIMARY);
        return l;
    }

    // ─────────────────────────────────────────────── Styled Components
    private static JButton createStyledButton(String text, Color accentColor) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_UI_BOLD);
        btn.setForeground(accentColor);
        btn.setBackground(BG_CARD);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(accentColor.darker(), 1, true),
                BorderFactory.createEmptyBorder(8, 18, 8, 18)
        ));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(accentColor.darker().darker());
                btn.setForeground(Color.WHITE);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(BG_CARD);
                btn.setForeground(accentColor);
            }
        });

        return btn;
    }

    private static void styleToggle(JToggleButton btn) {
        btn.setFont(FONT_UI_BOLD);
        btn.setForeground(btn.isSelected() ? OK_FG : ERROR_FG);
        btn.setBackground(BG_INPUT);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(70, 72, 90), 1, true),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // ─────────────────────────────────────────────── Formatting helpers
    private static String formatNumber(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    private static String formatUptime(long ms) {
        long sec = ms / 1000;
        if (sec < 60) return sec + "s";
        if (sec < 3600) return (sec / 60) + "m " + (sec % 60) + "s";
        return (sec / 3600) + "h " + ((sec % 3600) / 60) + "m";
    }

    // ─────────────────────────────────────────────── Standalone test
    public static void main(String[] args) {
        MetricsRegistry reg = MetricsRegistry.getInstance();
        reg.updateMetric("com.example.Foo#bar", 120_000_000L);
        reg.updateMetric("com.example.Foo#bar", 80_000_000L);
        reg.updateMetric("com.example.Baz#process", 5_000_000L);
        reg.updateMetric("com.example.Baz#process", 3_000_000L);
        reg.updateMetric("com.example.SlowService#heavyWork", 200_000_000L);
        reg.recordError("com.example.SlowService#heavyWork", "java.lang.RuntimeException");
        launch();
    }
}
