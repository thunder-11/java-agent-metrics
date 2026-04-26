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
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Production-grade APM dashboard with light/dark theme toggle.
 * <p>
 * Design system: custom-painted rounded components, 8px spacing grid,
 * dual colour palettes, gradient accents, smooth hover transitions.
 * <p>
 * Tabs:
 * <ol>
 *   <li><b>All Methods</b> — full metrics table with search, column sorting, color-coded status</li>
 *   <li><b>Slow Methods</b> — methods exceeding the configurable slow threshold</li>
 *   <li><b>Errors</b> — methods that have thrown exceptions, with last exception type</li>
 *   <li><b>Call Traces</b> — JTree visualisation of recent parent→child call trees</li>
 *   <li><b>Configuration</b> — runtime agent config controls (enable/disable, threshold)</li>
 * </ol>
 * <p>
 * Features: live JVM heap bar, CSV export, reset with confirmation, light/dark toggle,
 * theme persistence via Java Preferences API.
 *
 * @author Ali
 */
@SuppressWarnings("unused")
public class MetricsDashboard {

    // ═══════════════════════════════════════════════════════════════════
    //  THEME STATE
    // ═══════════════════════════════════════════════════════════════════

    private static boolean isDarkTheme;
    private static JFrame mainFrame;
    private static javax.swing.Timer refreshTimer;

    /** Preference key for persisting theme choice. */
    private static final String PREF_DARK_THEME = "darkTheme";

    // ═══════════════════════════════════════════════════════════════════
    //  DESIGN SYSTEM — Mutable Colour Tokens (swapped on theme toggle)
    // ═══════════════════════════════════════════════════════════════════

    // Surface layers
    private static Color SURFACE_0;    // window background
    private static Color SURFACE_1;    // panels / header
    private static Color SURFACE_2;    // cards / tables
    private static Color SURFACE_3;    // elevated cards / inputs
    private static Color SURFACE_4;    // hover / alt rows

    // Borders
    private static Color BORDER_0;     // subtle separator
    private static Color BORDER_1;     // input / card border
    private static Color BORDER_2;     // focus ring

    // Text
    private static Color TEXT_PRIMARY;
    private static Color TEXT_SECONDARY;
    private static Color TEXT_TERTIARY;
    private static Color TEXT_LINK;

    // Semantic status
    private static Color GREEN;
    private static Color YELLOW;
    private static Color ORANGE;
    private static Color RED;

    // Accent
    private static Color BLUE;
    private static Color PURPLE;
    private static Color CYAN;

    // Row tint backgrounds (very subtle)
    private static Color ROW_GREEN_BG;
    private static Color ROW_YELLOW_BG;
    private static Color ROW_RED_BG;

    // ═══════════════════════════════════════════════════════════════════
    //  PALETTE DEFINITIONS
    // ═══════════════════════════════════════════════════════════════════

    private static void applyDarkPalette() {
        SURFACE_0    = new Color(12, 13, 18);
        SURFACE_1    = new Color(18, 20, 28);
        SURFACE_2    = new Color(24, 26, 36);
        SURFACE_3    = new Color(32, 35, 48);
        SURFACE_4    = new Color(40, 44, 58);

        BORDER_0     = new Color(38, 41, 56);
        BORDER_1     = new Color(52, 56, 74);
        BORDER_2     = new Color(65, 70, 92);

        TEXT_PRIMARY   = new Color(230, 232, 242);
        TEXT_SECONDARY = new Color(145, 150, 175);
        TEXT_TERTIARY  = new Color(100, 105, 130);
        TEXT_LINK      = new Color(105, 165, 255);

        GREEN   = new Color(62, 207, 142);
        YELLOW  = new Color(250, 195, 65);
        ORANGE  = new Color(240, 150, 55);
        RED     = new Color(245, 78, 78);

        BLUE    = new Color(75, 140, 255);
        PURPLE  = new Color(148, 100, 255);
        CYAN    = new Color(55, 205, 210);

        ROW_GREEN_BG  = new Color(20, 45, 35);
        ROW_YELLOW_BG = new Color(42, 38, 18);
        ROW_RED_BG    = new Color(48, 18, 22);
    }

    private static void applyLightPalette() {
        SURFACE_0    = new Color(244, 245, 250);
        SURFACE_1    = new Color(234, 236, 244);
        SURFACE_2    = new Color(255, 255, 255);
        SURFACE_3    = new Color(240, 242, 248);
        SURFACE_4    = new Color(232, 234, 242);

        BORDER_0     = new Color(214, 218, 230);
        BORDER_1     = new Color(196, 200, 216);
        BORDER_2     = new Color(175, 180, 200);

        TEXT_PRIMARY   = new Color(26, 28, 40);
        TEXT_SECONDARY = new Color(90, 95, 115);
        TEXT_TERTIARY  = new Color(135, 140, 162);
        TEXT_LINK      = new Color(42, 105, 218);

        GREEN   = new Color(22, 163, 90);
        YELLOW  = new Color(200, 145, 10);
        ORANGE  = new Color(205, 115, 10);
        RED     = new Color(220, 52, 52);

        BLUE    = new Color(42, 105, 218);
        PURPLE  = new Color(110, 60, 220);
        CYAN    = new Color(8, 140, 162);

        ROW_GREEN_BG  = new Color(228, 248, 238);
        ROW_YELLOW_BG = new Color(253, 245, 222);
        ROW_RED_BG    = new Color(252, 228, 232);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  DESIGN SYSTEM — Typography (immutable)
    // ═══════════════════════════════════════════════════════════════════

    private static final Font FONT_MONO;
    private static final Font FONT_MONO_SM;
    private static final Font FONT_BODY;
    private static final Font FONT_BODY_BOLD;
    private static final Font FONT_CAPTION;
    private static final Font FONT_CAPTION_BOLD;
    private static final Font FONT_H1;
    private static final Font FONT_H2;
    private static final Font FONT_H3;
    private static final Font FONT_STAT_VALUE;
    private static final Font FONT_STAT_LABEL;

    static {
        // ── Fonts ──
        String mono = "Consolas";
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (f.equals("JetBrains Mono")) { mono = f; break; }
        }
        FONT_MONO        = new Font(mono, Font.PLAIN, 12);
        FONT_MONO_SM     = new Font(mono, Font.PLAIN, 11);
        FONT_BODY        = new Font("Segoe UI", Font.PLAIN, 13);
        FONT_BODY_BOLD   = new Font("Segoe UI", Font.BOLD, 13);
        FONT_CAPTION     = new Font("Segoe UI", Font.PLAIN, 11);
        FONT_CAPTION_BOLD= new Font("Segoe UI", Font.BOLD, 11);
        FONT_H1          = new Font("Segoe UI", Font.BOLD, 22);
        FONT_H2          = new Font("Segoe UI", Font.BOLD, 16);
        FONT_H3          = new Font("Segoe UI", Font.BOLD, 14);
        FONT_STAT_VALUE  = new Font("Segoe UI", Font.BOLD, 28);
        FONT_STAT_LABEL  = new Font("Segoe UI", Font.PLAIN, 10);

        // ── Theme: load persisted preference, default to dark ──
        isDarkTheme = Preferences.userNodeForPackage(MetricsDashboard.class)
                .getBoolean(PREF_DARK_THEME, true);
        if (isDarkTheme) applyDarkPalette(); else applyLightPalette();
    }

    // Spacing constants (8px grid)
    private static final int SP_XS = 4;
    private static final int SP_SM = 8;
    private static final int SP_MD = 12;
    private static final int SP_LG = 16;
    private static final int SP_XL = 24;
    private static final int SP_2XL= 32;

    // Border radius
    private static final int RADIUS_SM = 6;
    private static final int RADIUS_MD = 10;
    private static final int RADIUS_LG = 14;

    // ═══════════════════════════════════════════════════════════════════
    //  REUSABLE UI PRIMITIVES
    // ═══════════════════════════════════════════════════════════════════

    /** A panel with a rounded-rectangle fill and optional border. */
    private static JPanel roundedPanel(Color bg, Color borderColor, int radius) {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = gfx(g);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
                if (borderColor != null) {
                    g2.setColor(borderColor);
                    g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1, getHeight()-1, radius, radius));
                }
                g2.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }

    /** Styled button with rounded bg, hover effect, and accent colour. */
    private static JButton styledButton(String text, Color accent) {
        JButton btn = new JButton(text) {
            boolean hovered = false;
            {
                setOpaque(false);
                setContentAreaFilled(false);
                setFocusPainted(false);
                setBorderPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setFont(FONT_BODY_BOLD);
                setForeground(accent);
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = gfx(g);
                Color bg = hovered ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30)
                                   : SURFACE_3;
                Color border = hovered ? accent : BORDER_1;
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS_SM, RADIUS_SM));
                g2.setColor(border);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1, getHeight()-1, RADIUS_SM, RADIUS_SM));
                g2.dispose();
                setForeground(hovered ? (isDarkTheme ? Color.WHITE : accent.darker()) : accent);
                super.paintComponent(g);
            }
        };
        btn.setBorder(BorderFactory.createEmptyBorder(SP_SM, SP_LG, SP_SM, SP_LG));
        return btn;
    }

    /** Consistent Graphics2D setup with antialiasing. */
    private static Graphics2D gfx(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        return g2;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  COLUMN DEFINITIONS
    // ═══════════════════════════════════════════════════════════════════

    private static final String[] ALL_COLUMNS = {
            "Status", "Method", "Calls", "Total (ms)", "Avg (ms)", "Min (ms)", "Max (ms)", "Errors", "Err%"
    };
    private static final String[] SLOW_COLUMNS = {
            "Status", "Method", "Calls", "Avg (ms)", "Max (ms)", "Errors"
    };
    private static final String[] ERROR_COLUMNS = {
            "Method", "Errors", "Calls", "Err%", "Last Exception"
    };

    // ═══════════════════════════════════════════════════════════════════
    //  TABLE MODEL
    // ═══════════════════════════════════════════════════════════════════

    private static final class MetricsTableModel extends AbstractTableModel {
        private final String[] columns;
        private final int mode; // 0 = all, 1 = slow, 2 = errors
        private List<Object[]> rows = new ArrayList<>();
        private String filter = "";

        MetricsTableModel(String[] cols, int mode) { this.columns = cols; this.mode = mode; }
        void setFilter(String f) { this.filter = f.toLowerCase(); }

        void refreshData() {
            Map<String, MethodMetrics> snap = MetricsRegistry.getInstance().getMetricsSnapshot();
            long threshold = AgentConfig.getInstance().getSlowThresholdMs();
            List<Object[]> out = new ArrayList<>(snap.size());

            for (Map.Entry<String, MethodMetrics> e : snap.entrySet()) {
                MethodMetrics m = e.getValue();
                String key = e.getKey();
                if (!filter.isEmpty() && !key.toLowerCase().contains(filter)) continue;

                if (mode == 0) {
                    out.add(new Object[]{
                        statusOf(m.getAverageTimeMs(), m.getErrorRate()*100, threshold),
                        key, m.getCallCount(), m.getTotalTimeMs(), m.getAverageTimeMs(),
                        m.getMinTimeMs(), m.getMaxTimeMs(), m.getErrorCount(), m.getErrorRate()*100.0
                    });
                } else if (mode == 1) {
                    if (m.getAverageTimeMs() <= threshold) continue;
                    out.add(new Object[]{
                        m.getAverageTimeMs() > threshold ? "SLOW" : "WARN",
                        key, m.getCallCount(), m.getAverageTimeMs(), m.getMaxTimeMs(), m.getErrorCount()
                    });
                } else {
                    if (m.getErrorCount() == 0) continue;
                    out.add(new Object[]{
                        key, m.getErrorCount(), m.getCallCount(), m.getErrorRate()*100.0,
                        m.getLastExceptionType().isEmpty() ? "—" : m.getLastExceptionType()
                    });
                }
            }

            if (mode == 2) out.sort((a, b) -> Long.compare((Long)b[1], (Long)a[1]));
            else { int c = mode == 1 ? 3 : 4; out.sort((a, b) -> Double.compare((Double)b[c], (Double)a[c])); }
            rows = out;
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }
        @Override public Object getValueAt(int r, int c) { return r < rows.size() ? rows.get(r)[c] : null; }
        @Override public Class<?> getColumnClass(int c) {
            String n = columns[c];
            if ("Status".equals(n) || "Method".equals(n) || "Last Exception".equals(n)) return String.class;
            if ("Calls".equals(n) || "Errors".equals(n)) return Long.class;
            return Double.class;
        }

        double avgMs(int r) {
            if (r < 0 || r >= rows.size()) return 0;
            return mode == 0 ? (Double) rows.get(r)[4] : mode == 1 ? (Double) rows.get(r)[3] : 0;
        }
        double errRate(int r) {
            if (r < 0 || r >= rows.size()) return 0;
            return mode == 0 ? (Double) rows.get(r)[8] : mode == 2 ? (Double) rows.get(r)[3] : 0;
        }

        private static String statusOf(double avg, double errPct, long threshold) {
            if (errPct > 10.0)       return "ERROR";
            if (avg > threshold)     return "SLOW";
            if (avg > threshold*0.5) return "WARN";
            return "OK";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CUSTOM TABLE CELL RENDERER — status pill + colour-coded rows
    // ═══════════════════════════════════════════════════════════════════

    private static final class ApmCellRenderer extends DefaultTableCellRenderer {
        private final MetricsTableModel model;
        private String statusValue = "";

        ApmCellRenderer(MetricsTableModel m) { this.model = m; }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object val,
                                                       boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, val, sel, focus, row, col);
            setFont(FONT_MONO);
            setBorder(BorderFactory.createEmptyBorder(0, SP_MD, 0, SP_MD));

            int mr = t.convertRowIndexToModel(row);
            long thr = AgentConfig.getInstance().getSlowThresholdMs();
            double avg = model.avgMs(mr);
            double err = model.errRate(mr);
            String colName = t.getColumnName(col);

            if (!sel) {
                Color bg, fg;
                if (err > 10) { bg = ROW_RED_BG; fg = RED; }
                else if (avg > thr)   { bg = ROW_RED_BG; fg = RED; }
                else if (avg > thr*.5){ bg = ROW_YELLOW_BG; fg = YELLOW; }
                else { bg = row%2==0 ? SURFACE_2 : SURFACE_4; fg = TEXT_PRIMARY; }
                setBackground(bg);
                setForeground(fg);

                if ("Method".equals(colName) || "Last Exception".equals(colName)) setForeground(TEXT_PRIMARY);
                if ("Status".equals(colName)) {
                    statusValue = val != null ? val.toString() : "";
                    setText("");
                }
            } else {
                setBackground(new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 50));
                setForeground(isDarkTheme ? Color.WHITE : TEXT_PRIMARY);
                if ("Status".equals(colName)) { statusValue = val != null ? val.toString() : ""; setText(""); }
            }

            if ("Method".equals(colName) || "Last Exception".equals(colName))
                setHorizontalAlignment(SwingConstants.LEFT);
            else if ("Status".equals(colName))
                setHorizontalAlignment(SwingConstants.CENTER);
            else
                setHorizontalAlignment(SwingConstants.RIGHT);

            if (val instanceof Double) setText(String.format("%.2f", val));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!statusValue.isEmpty()) {
                Graphics2D g2 = gfx(g);
                Color pill, pillText;
                String label;
                switch (statusValue) {
                    case "OK":    pill = new Color(GREEN.getRed(), GREEN.getGreen(), GREEN.getBlue(), isDarkTheme ? 35 : 50);
                                  pillText = GREEN; label = "OK"; break;
                    case "WARN":  pill = new Color(YELLOW.getRed(), YELLOW.getGreen(), YELLOW.getBlue(), isDarkTheme ? 35 : 50);
                                  pillText = YELLOW; label = "WARN"; break;
                    case "SLOW":  pill = new Color(RED.getRed(), RED.getGreen(), RED.getBlue(), isDarkTheme ? 35 : 50);
                                  pillText = RED; label = "SLOW"; break;
                    case "ERROR": pill = new Color(RED.getRed(), RED.getGreen(), RED.getBlue(), isDarkTheme ? 50 : 65);
                                  pillText = RED; label = "ERR"; break;
                    default:      pill = SURFACE_3; pillText = TEXT_SECONDARY; label = statusValue; break;
                }
                g2.setFont(FONT_CAPTION_BOLD);
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(label);
                int pw = tw + 16, ph = 20;
                int px = (getWidth() - pw) / 2, py = (getHeight() - ph) / 2;
                g2.setColor(pill);
                g2.fill(new RoundRectangle2D.Float(px, py, pw, ph, ph, ph));
                g2.setColor(pillText);
                g2.drawString(label, px + 8, py + fm.getAscent() + (ph - fm.getHeight()) / 2);
                g2.dispose();
                statusValue = "";
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CUSTOM TABLE HEADER RENDERER
    // ═══════════════════════════════════════════════════════════════════

    private static final class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object val,
                                                       boolean sel, boolean focus, int row, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, val, sel, focus, row, col);
            l.setFont(FONT_CAPTION_BOLD);
            l.setForeground(TEXT_SECONDARY);
            l.setBackground(SURFACE_1);
            l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_0),
                    BorderFactory.createEmptyBorder(SP_SM, SP_MD, SP_SM, SP_MD)
            ));
            l.setHorizontalAlignment(col == 0 ? SwingConstants.CENTER
                    : ("Method".equals(val) || "Last Exception".equals(val)) ? SwingConstants.LEFT
                    : SwingConstants.RIGHT);
            return l;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  JVM MEMORY BAR (custom-painted)
    // ═══════════════════════════════════════════════════════════════════

    private static final class MemoryBar extends JPanel {
        private double pct = 0;
        private String text = "";

        MemoryBar() {
            setPreferredSize(new Dimension(0, 26));
            setOpaque(false);
        }

        void update(long usedMB, long totalMB) {
            pct = totalMB > 0 ? (double)usedMB/totalMB : 0;
            text = String.format("JVM Heap   %d MB / %d MB   (%.0f%%)", usedMB, totalMB, pct*100);
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = gfx(g);
            int x=SP_LG, y=3, w=getWidth()-SP_LG*2, h=getHeight()-6;

            // Track
            g2.setColor(SURFACE_3);
            g2.fill(new RoundRectangle2D.Float(x, y, w, h, h, h));

            // Fill
            int fw = Math.max(0, (int)(w * pct));
            if (fw > 0) {
                Color c = pct<0.6 ? GREEN : pct<0.85 ? YELLOW : RED;
                g2.setPaint(new GradientPaint(x, y, c, x+fw, y, c.darker()));
                g2.fill(new RoundRectangle2D.Float(x, y, fw, h, h, h));
            }

            // Text
            g2.setFont(FONT_CAPTION);
            g2.setColor(TEXT_PRIMARY);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, x+(w-fm.stringWidth(text))/2, y+(h+fm.getAscent()-fm.getDescent())/2);

            g2.dispose();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CUSTOM TABBED PANE UI — flat tabs with bottom accent line
    // ═══════════════════════════════════════════════════════════════════

    private static final class FlatTabbedPaneUI extends BasicTabbedPaneUI {
        @Override protected void installDefaults() {
            super.installDefaults();
            tabAreaInsets = new Insets(0, SP_MD, 0, SP_MD);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            tabInsets = new Insets(SP_SM+2, SP_LG, SP_SM+2, SP_LG);
            contentBorderInsets = new Insets(0, 0, 0, 0);
        }
        @Override protected void paintTabBorder(Graphics g, int placement, int idx,
                                                 int x, int y, int w, int h, boolean sel) { }
        @Override protected void paintTabBackground(Graphics g, int placement, int idx,
                                                     int x, int y, int w, int h, boolean sel) {
            Graphics2D g2 = gfx(g);
            if (sel) {
                g2.setColor(new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), isDarkTheme ? 20 : 30));
                g2.fill(new RoundRectangle2D.Float(x+2, y+2, w-4, h-2, RADIUS_SM, RADIUS_SM));
                g2.setColor(BLUE);
                g2.fillRect(x+4, y+h-3, w-8, 3);
            }
            g2.dispose();
        }
        @Override protected void paintContentBorder(Graphics g, int placement, int sel) { }
        @Override protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects,
                                                      int idx, Rectangle icon, Rectangle text, boolean sel) { }
        @Override protected int calculateTabWidth(int placement, int idx, FontMetrics fm) {
            return super.calculateTabWidth(placement, idx, fm) + SP_LG;
        }
        @Override protected int calculateTabHeight(int placement, int idx, int fontH) {
            return fontH + SP_LG + SP_SM;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ENTRY POINT
    // ═══════════════════════════════════════════════════════════════════

    public static void launch() {
        SwingUtilities.invokeLater(MetricsDashboard::createAndShow);
    }

    private static void createAndShow() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}

        mainFrame = new JFrame("Java Agent APM — Performance Dashboard");
        mainFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        mainFrame.setSize(1300, 850);
        mainFrame.setMinimumSize(new Dimension(980, 640));
        mainFrame.setLocationRelativeTo(null);

        buildUI(mainFrame);
        mainFrame.setVisible(true);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BUILD UI — called on initial launch AND on theme toggle
    // ═══════════════════════════════════════════════════════════════════

    private static void buildUI(JFrame frame) {
        // Apply UIManager defaults for current theme
        UIManager.put("Panel.background", SURFACE_0);
        UIManager.put("OptionPane.background", SURFACE_2);
        UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);

        frame.getContentPane().setBackground(SURFACE_0);

        // ── Header ──
        JPanel header = buildHeader(frame);

        // ── Memory Bar ──
        MemoryBar memBar = new MemoryBar();

        // ── Stats ──
        JLabel sM = statVal("0"), sI = statVal("0"), sE = statVal("0"), sU = statVal("0s");
        JPanel stats = buildStats(sM, sI, sE, sU);

        // ── Tabs ──
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setUI(new FlatTabbedPaneUI());
        tabs.setBackground(SURFACE_0);
        tabs.setForeground(TEXT_PRIMARY);
        tabs.setFont(FONT_BODY_BOLD);
        tabs.setBorder(BorderFactory.createEmptyBorder(0, SP_SM, SP_MD, SP_SM));

        MetricsTableModel allModel  = new MetricsTableModel(ALL_COLUMNS, 0);
        MetricsTableModel slowModel = new MetricsTableModel(SLOW_COLUMNS, 1);
        MetricsTableModel errModel  = new MetricsTableModel(ERROR_COLUMNS, 2);

        tabs.addTab("  All Methods  ",  buildTableTab(allModel,  "Search all methods..."));
        tabs.addTab("  Slow Methods  ", buildTableTab(slowModel, "Search slow methods..."));
        tabs.addTab("  Errors  ",       buildTableTab(errModel,  "Search errors..."));
        tabs.addTab("  Call Traces  ",  buildTracesTab());
        tabs.addTab("  Configuration  ",buildConfigTab());

        // ── Status bar ──
        JLabel status = new JLabel("  Ready");
        status.setFont(FONT_CAPTION);
        status.setForeground(TEXT_TERTIARY);
        status.setBackground(SURFACE_1);
        status.setOpaque(true);
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_0),
                BorderFactory.createEmptyBorder(SP_SM, SP_LG, SP_SM, SP_LG)));

        // ── Assemble ──
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(SURFACE_0);
        for (JComponent c : new JComponent[]{header, memBar, stats}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            top.add(c);
        }

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(top, BorderLayout.NORTH);
        frame.getContentPane().add(tabs, BorderLayout.CENTER);
        frame.getContentPane().add(status, BorderLayout.SOUTH);

        // ── Refresh Timer (1 s) ──
        refreshTimer = new javax.swing.Timer(1000, e -> {
            allModel.refreshData();
            slowModel.refreshData();
            errModel.refreshData();

            MetricsRegistry reg = MetricsRegistry.getInstance();
            sM.setText(String.valueOf(reg.getMethodCount()));
            sI.setText(fmtNum(reg.getTotalInvocations()));
            sE.setText(String.valueOf(reg.getTotalErrors()));
            sU.setText(fmtUptime(reg.getUptimeMillis()));

            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory()-rt.freeMemory())/(1024*1024);
            long total = rt.totalMemory()/(1024*1024);
            memBar.update(used, total);

            AgentConfig cfg = AgentConfig.getInstance();
            long slow = reg.getSlowMethods(cfg.getSlowThresholdMs()).size();
            long errs = reg.getTotalErrors();

            tabs.setTitleAt(1, slow > 0 ? String.format("  Slow (%d)  ", slow) : "  Slow Methods  ");
            tabs.setTitleAt(2, errs > 0 ? String.format("  Errors (%d)  ", errs) : "  Errors  ");

            status.setText(String.format(
                "  %d methods   |   %d slow (>%dms)   |   %d errors   |   HTTP ::%d   |   Heap %dMB / %dMB",
                reg.getMethodCount(), slow, cfg.getSlowThresholdMs(), errs, cfg.getHttpPort(), used, total));
        });
        refreshTimer.setInitialDelay(300);
        refreshTimer.start();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  THEME TOGGLE — swap palette, persist preference, rebuild UI
    // ═══════════════════════════════════════════════════════════════════

    private static void toggleTheme() {
        isDarkTheme = !isDarkTheme;

        // 1. Swap the colour palette
        if (isDarkTheme) applyDarkPalette(); else applyLightPalette();

        // 2. Persist preference
        Preferences.userNodeForPackage(MetricsDashboard.class)
                .putBoolean(PREF_DARK_THEME, isDarkTheme);

        // 3. Stop old timer
        if (refreshTimer != null) refreshTimer.stop();

        // 4. Rebuild entire UI with new palette
        mainFrame.getContentPane().removeAll();
        buildUI(mainFrame);
        mainFrame.revalidate();
        mainFrame.repaint();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  HEADER BAR (with theme toggle button)
    // ═══════════════════════════════════════════════════════════════════

    private static JPanel buildHeader(JFrame frame) {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = gfx(g);
                g2.setColor(SURFACE_1);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Bottom gradient accent line
                g2.setPaint(new GradientPaint(0, getHeight()-2, BLUE, getWidth(), getHeight()-2, PURPLE));
                g2.fillRect(0, getHeight()-2, getWidth(), 2);
                g2.dispose();
            }
        };
        bar.setBorder(BorderFactory.createEmptyBorder(SP_LG, SP_XL, SP_LG, SP_XL));
        bar.setPreferredSize(new Dimension(0, 72));

        // Title
        JPanel titleGroup = new JPanel();
        titleGroup.setLayout(new BoxLayout(titleGroup, BoxLayout.Y_AXIS));
        titleGroup.setOpaque(false);

        JLabel title = new JLabel("Java Agent APM");
        title.setFont(FONT_H1);
        title.setForeground(TEXT_PRIMARY);

        JLabel sub = new JLabel("Lightweight Application Performance Monitor");
        sub.setFont(FONT_CAPTION);
        sub.setForeground(TEXT_TERTIARY);

        titleGroup.add(title);
        titleGroup.add(Box.createVerticalStrut(2));
        titleGroup.add(sub);

        // Buttons
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, SP_SM, 0));
        btns.setOpaque(false);

        // ── Theme toggle button ──
        String themeText = isDarkTheme ? "Light" : "Dark";
        JButton themeBtn = styledButton(themeText, isDarkTheme ? YELLOW : PURPLE);
        themeBtn.setToolTipText("Switch to " + (isDarkTheme ? "light" : "dark") + " theme");
        themeBtn.addActionListener(e -> toggleTheme());

        JButton exportBtn = styledButton("Export CSV", CYAN);
        exportBtn.setToolTipText("Export all metrics as a .csv file");
        exportBtn.addActionListener(e -> exportCsv(frame));

        JButton resetBtn = styledButton("Reset All", ORANGE);
        resetBtn.setToolTipText("Clear all metrics, errors, and call traces");
        resetBtn.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(frame,
                    "This will clear all collected metrics, errors,\nand call traces. Continue?",
                    "Reset Metrics", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r == JOptionPane.YES_OPTION) {
                MetricsRegistry.getInstance().clear();
                CallTracer.getInstance().clear();
            }
        });

        btns.add(themeBtn);
        btns.add(exportBtn);
        btns.add(resetBtn);

        bar.add(titleGroup, BorderLayout.WEST);
        bar.add(btns, BorderLayout.EAST);
        return bar;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  STATS STRIP
    // ═══════════════════════════════════════════════════════════════════

    private static JPanel buildStats(JLabel m, JLabel i, JLabel e, JLabel u) {
        JPanel strip = new JPanel(new GridLayout(1, 4, SP_MD, 0));
        strip.setOpaque(false);
        strip.setBorder(BorderFactory.createEmptyBorder(SP_SM, SP_LG, SP_SM, SP_LG));
        strip.add(statCard("METHODS TRACKED", m, BLUE));
        strip.add(statCard("TOTAL INVOCATIONS", i, CYAN));
        strip.add(statCard("TOTAL ERRORS", e, RED));
        strip.add(statCard("UPTIME", u, PURPLE));
        return strip;
    }

    private static JPanel statCard(String label, JLabel valLabel, Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, SP_XS)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = gfx(g);
                g2.setColor(SURFACE_2);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS_MD, RADIUS_MD));
                // Left accent strip
                g2.setPaint(new GradientPaint(0, 0, accent, 0, getHeight(), accent.darker()));
                g2.fill(new RoundRectangle2D.Float(0, 0, 4, getHeight(), 4, 4));
                // Border
                g2.setColor(BORDER_0);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1, getHeight()-1, RADIUS_MD, RADIUS_MD));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(SP_MD, SP_LG+SP_XS, SP_MD, SP_LG));

        JLabel lbl = new JLabel(label);
        lbl.setFont(FONT_STAT_LABEL);
        lbl.setForeground(TEXT_TERTIARY);

        valLabel.setFont(FONT_STAT_VALUE);
        valLabel.setForeground(accent);

        card.add(lbl, BorderLayout.NORTH);
        card.add(valLabel, BorderLayout.SOUTH);
        return card;
    }

    private static JLabel statVal(String t) {
        JLabel l = new JLabel(t);
        l.setFont(FONT_STAT_VALUE);
        l.setForeground(TEXT_LINK);
        return l;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TABLE TAB (search bar + table)
    // ═══════════════════════════════════════════════════════════════════

    private static JPanel buildTableTab(MetricsTableModel model, String placeholder) {
        JPanel panel = new JPanel(new BorderLayout(0, SP_SM));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(SP_MD, SP_SM, SP_SM, SP_SM));

        // Search bar
        JPanel searchBar = roundedPanel(SURFACE_3, BORDER_1, RADIUS_SM);
        searchBar.setLayout(new BorderLayout());
        searchBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        searchBar.setPreferredSize(new Dimension(0, 38));
        searchBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JLabel icon = new JLabel("  \uD83D\uDD0D  ");
        icon.setFont(FONT_BODY);
        icon.setForeground(TEXT_TERTIARY);

        JTextField field = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !hasFocus()) {
                    Graphics2D g2 = gfx(g);
                    g2.setFont(FONT_BODY);
                    g2.setColor(TEXT_TERTIARY);
                    Insets ins = getInsets();
                    g2.drawString(placeholder, ins.left, getHeight()/2 + g2.getFontMetrics().getAscent()/2 - 1);
                    g2.dispose();
                }
            }
        };
        field.setFont(FONT_BODY);
        field.setBackground(SURFACE_3);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(BLUE);
        field.setBorder(BorderFactory.createEmptyBorder(SP_SM, SP_SM, SP_SM, SP_SM));
        field.setOpaque(false);

        JLabel count = new JLabel("0 results  ");
        count.setFont(FONT_CAPTION);
        count.setForeground(TEXT_TERTIARY);

        searchBar.add(icon, BorderLayout.WEST);
        searchBar.add(field, BorderLayout.CENTER);
        searchBar.add(count, BorderLayout.EAST);

        // Table
        JTable table = new JTable(model);
        applyTableStyle(table, model);

        field.getDocument().addDocumentListener(new DocumentListener() {
            void u() { model.setFilter(field.getText()); model.refreshData(); count.setText(model.getRowCount()+" results  "); }
            @Override public void insertUpdate(DocumentEvent e) { u(); }
            @Override public void removeUpdate(DocumentEvent e) { u(); }
            @Override public void changedUpdate(DocumentEvent e){ u(); }
        });
        model.addTableModelListener(e -> count.setText(model.getRowCount()+" results  "));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_0));
        scroll.getViewport().setBackground(SURFACE_2);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(searchBar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static void applyTableStyle(JTable t, MetricsTableModel model) {
        t.setBackground(SURFACE_2);
        t.setForeground(TEXT_PRIMARY);
        t.setGridColor(BORDER_0);
        t.setSelectionBackground(new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 40));
        t.setSelectionForeground(isDarkTheme ? Color.WHITE : TEXT_PRIMARY);
        t.setFillsViewportHeight(true);
        t.setRowHeight(36);
        t.setFont(FONT_MONO);
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setAutoCreateRowSorter(true);

        JTableHeader h = t.getTableHeader();
        h.setDefaultRenderer(new HeaderRenderer());
        h.setPreferredSize(new Dimension(0, 36));
        h.setReorderingAllowed(false);
        h.setBackground(SURFACE_1);

        ApmCellRenderer cr = new ApmCellRenderer(model);
        for (int i = 0; i < t.getColumnCount(); i++)
            t.getColumnModel().getColumn(i).setCellRenderer(cr);

        for (int i = 0; i < t.getColumnCount(); i++) {
            String n = t.getColumnName(i);
            int w = 95;
            if ("Status".equals(n)) { w = 80; t.getColumnModel().getColumn(i).setMaxWidth(100); }
            else if ("Method".equals(n)) w = 360;
            else if ("Last Exception".equals(n)) w = 260;
            t.getColumnModel().getColumn(i).setPreferredWidth(w);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TRACES TAB
    // ═══════════════════════════════════════════════════════════════════

    private static JPanel buildTracesTab() {
        JPanel panel = new JPanel(new BorderLayout(0, SP_SM));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(SP_MD, SP_SM, SP_SM, SP_SM));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Call Traces (most recent first)");
        JTree tree = new JTree(root);
        tree.setBackground(SURFACE_2);
        tree.setForeground(TEXT_PRIMARY);
        tree.setFont(FONT_MONO);
        tree.setRowHeight(30);

        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            {
                setBackgroundNonSelectionColor(SURFACE_2);
                setBackgroundSelectionColor(new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 40));
                setTextNonSelectionColor(TEXT_PRIMARY);
                setTextSelectionColor(isDarkTheme ? Color.WHITE : TEXT_PRIMARY);
                setBorderSelectionColor(BLUE);
            }
            @Override public Component getTreeCellRendererComponent(JTree tree, Object val,
                    boolean sel, boolean exp, boolean leaf, int row, boolean focus) {
                Component c = super.getTreeCellRendererComponent(tree, val, sel, exp, leaf, row, focus);
                c.setFont(FONT_MONO);
                String txt = val.toString();
                if (txt.contains("ms") && !txt.startsWith("Call")) {
                    try {
                        double d = Double.parseDouble(txt.substring(txt.lastIndexOf("(")+1, txt.lastIndexOf("ms")).trim());
                        long thr = AgentConfig.getInstance().getSlowThresholdMs();
                        setForeground(d > thr ? RED : d > thr*0.5 ? YELLOW : GREEN);
                    } catch (Exception ignored) {}
                }
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_0));
        scroll.getViewport().setBackground(SURFACE_2);

        JButton refreshBtn = styledButton("Refresh Traces", CYAN);
        refreshBtn.addActionListener(e -> {
            root.removeAllChildren();
            List<CallNode> traces = CallTracer.getInstance().getRecentTraces(30);
            for (int i = 0; i < traces.size(); i++) {
                CallNode t2 = traces.get(i);
                DefaultMutableTreeNode n = new DefaultMutableTreeNode(
                        String.format("#%d  %s (%.2fms) [%s]", i+1, t2.getMethodKey(), t2.getDurationMs(), t2.getThreadName()));
                addChildren(n, t2);
                root.add(n);
            }
            ((DefaultTreeModel) tree.getModel()).reload();
            for (int i = 0; i < Math.min(5, tree.getRowCount()); i++) tree.expandRow(i);
        });

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        JLabel lbl = new JLabel("  Recent Call Trees");
        lbl.setFont(FONT_H3);
        lbl.setForeground(TEXT_LINK);
        topBar.add(lbl, BorderLayout.WEST);
        topBar.add(refreshBtn, BorderLayout.EAST);

        panel.add(topBar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static void addChildren(DefaultMutableTreeNode parent, CallNode node) {
        for (CallNode ch : node.getChildren()) {
            DefaultMutableTreeNode n = new DefaultMutableTreeNode(
                    String.format("%s (%.2fms)", ch.getMethodKey(), ch.getDurationMs()));
            addChildren(n, ch);
            parent.add(n);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CONFIGURATION TAB
    // ═══════════════════════════════════════════════════════════════════

    private static JPanel buildConfigTab() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.setBorder(BorderFactory.createEmptyBorder(SP_LG, SP_LG, SP_LG, SP_LG));

        JPanel card = roundedPanel(SURFACE_2, BORDER_0, RADIUS_MD);
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(SP_XL, SP_2XL, SP_XL, SP_2XL));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(SP_SM, SP_SM, SP_SM, SP_SM);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        AgentConfig cfg = AgentConfig.getInstance();

        // Section title
        g.gridx=0; g.gridy=row; g.gridwidth=2;
        JLabel title = new JLabel("Agent Configuration");
        title.setFont(FONT_H2);
        title.setForeground(TEXT_PRIMARY);
        card.add(title, g);
        row++;

        // Divider
        g.gridy=row; g.gridwidth=2;
        JPanel div = new JPanel();
        div.setPreferredSize(new Dimension(0, 1));
        div.setBackground(BORDER_0);
        card.add(div, g);
        row++;

        g.gridwidth=1;

        // Enabled toggle
        g.gridx=0; g.gridy=row;
        card.add(cfgLabel("Instrumentation"), g);
        g.gridx=1;
        JToggleButton toggle = new JToggleButton(cfg.isEnabled() ? "ENABLED" : "DISABLED");
        toggle.setSelected(cfg.isEnabled());
        toggle.setFont(FONT_BODY_BOLD);
        toggle.setForeground(cfg.isEnabled() ? GREEN : RED);
        toggle.setBackground(SURFACE_3);
        toggle.setFocusPainted(false);
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_1, 1, true),
                BorderFactory.createEmptyBorder(SP_SM, SP_LG, SP_SM, SP_LG)));
        toggle.addActionListener(e -> {
            cfg.setEnabled(toggle.isSelected());
            toggle.setText(toggle.isSelected() ? "ENABLED" : "DISABLED");
            toggle.setForeground(toggle.isSelected() ? GREEN : RED);
        });
        card.add(toggle, g);
        row++;

        // Threshold
        g.gridx=0; g.gridy=row;
        card.add(cfgLabel("Slow Threshold (ms)"), g);
        g.gridx=1;
        JSpinner spin = new JSpinner(new SpinnerNumberModel((int)cfg.getSlowThresholdMs(), 1, 10000, 10));
        spin.setFont(FONT_BODY);
        spin.setBackground(SURFACE_3);
        spin.setForeground(TEXT_PRIMARY);
        spin.addChangeListener(e -> cfg.setSlowThresholdMs((Integer) spin.getValue()));
        card.add(spin, g);
        row++;

        // Read-only fields
        String[][] info = {
            {"Target Package", cfg.getTargetPackage()},
            {"HTTP Endpoint", "http://localhost:" + cfg.getHttpPort() + "/metrics"},
            {"Metrics Export Path", cfg.getMetricsExportPath()}
        };
        for (String[] pair : info) {
            g.gridx=0; g.gridy=row;
            card.add(cfgLabel(pair[0]), g);
            g.gridx=1;
            JLabel v = new JLabel(pair[1]);
            v.setFont(FONT_MONO);
            v.setForeground(CYAN);
            card.add(v, g);
            row++;
        }

        // Endpoints card
        g.gridx=0; g.gridy=row; g.gridwidth=2; g.insets = new Insets(SP_LG, SP_SM, SP_SM, SP_SM);
        card.add(buildEndpointsCard(cfg.getHttpPort()), g);

        outer.add(card, BorderLayout.NORTH);
        return outer;
    }

    private static JPanel buildEndpointsCard(int port) {
        JPanel card = roundedPanel(SURFACE_1, BORDER_0, RADIUS_SM);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(SP_MD, SP_LG, SP_MD, SP_LG));

        JLabel h = new JLabel("HTTP Endpoints");
        h.setFont(FONT_H3);
        h.setForeground(TEXT_LINK);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(h);
        card.add(Box.createVerticalStrut(SP_SM));

        String[][] eps = {
            {"GET", "/metrics",            "Full metrics JSON"},
            {"GET", "/metrics/slow",       "Slow methods only"},
            {"GET", "/metrics/traces",     "Recent call trees"},
            {"GET", "/metrics/prometheus", "Prometheus exposition format"},
            {"GET", "/health",             "Health check"},
            {"GET", "/config",             "Current configuration"},
            {"POST","/config",             "Update configuration"},
        };
        for (String[] ep : eps) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, SP_SM, 1));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel method = new JLabel(ep[0]);
            method.setFont(FONT_CAPTION_BOLD);
            method.setForeground("POST".equals(ep[0]) ? ORANGE : GREEN);

            JLabel path = new JLabel(ep[1]);
            path.setFont(FONT_MONO_SM);
            path.setForeground(TEXT_PRIMARY);

            JLabel desc = new JLabel("— " + ep[2]);
            desc.setFont(FONT_CAPTION);
            desc.setForeground(TEXT_TERTIARY);

            row.add(method);
            row.add(path);
            row.add(desc);
            card.add(row);
        }
        return card;
    }

    private static JLabel cfgLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_BODY_BOLD);
        l.setForeground(TEXT_SECONDARY);
        return l;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CSV EXPORT
    // ═══════════════════════════════════════════════════════════════════

    private static void exportCsv(JFrame parent) {
        JFileChooser ch = new JFileChooser();
        ch.setDialogTitle("Export Metrics as CSV");
        ch.setSelectedFile(new File("agent-metrics-export.csv"));
        ch.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
        if (ch.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        File f = ch.getSelectedFile();
        if (!f.getName().endsWith(".csv")) f = new File(f.getAbsolutePath()+".csv");

        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            w.write("Method,Calls,Total (ms),Avg (ms),Min (ms),Max (ms),Errors,Error Rate (%),Last Exception");
            w.newLine();
            Map<String, MethodMetrics> snap = MetricsRegistry.getInstance().getMetricsSnapshot();
            for (Map.Entry<String, MethodMetrics> e : snap.entrySet()) {
                MethodMetrics m = e.getValue();
                w.write(String.format("\"%s\",%d,%.3f,%.3f,%.3f,%.3f,%d,%.2f,\"%s\"",
                        e.getKey(), m.getCallCount(), m.getTotalTimeMs(), m.getAverageTimeMs(),
                        m.getMinTimeMs(), m.getMaxTimeMs(), m.getErrorCount(),
                        m.getErrorRate()*100, m.getLastExceptionType()));
                w.newLine();
            }
            JOptionPane.showMessageDialog(parent,
                    "Exported "+snap.size()+" methods to:\n"+f.getAbsolutePath(),
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent,
                    "Export failed: "+ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FORMATTING HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static String fmtNum(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n/1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n/1_000.0);
        return String.valueOf(n);
    }

    private static String fmtUptime(long ms) {
        long s = ms/1000;
        if (s < 60) return s+"s";
        if (s < 3600) return (s/60)+"m "+(s%60)+"s";
        return (s/3600)+"h "+((s%3600)/60)+"m";
    }

    // ═══════════════════════════════════════════════════════════════════
    //  STANDALONE TEST
    // ═══════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        MetricsRegistry r = MetricsRegistry.getInstance();
        // Healthy
        r.updateMetric("com.example.UserService#getUser", 5_000_000L);
        r.updateMetric("com.example.UserService#getUser", 3_000_000L);
        r.updateMetric("com.example.UserService#getUser", 4_000_000L);
        r.updateMetric("com.example.AuthService#login", 12_000_000L);
        r.updateMetric("com.example.AuthService#login", 8_000_000L);
        r.updateMetric("com.example.CacheService#get", 1_000_000L);
        r.updateMetric("com.example.CacheService#get", 2_000_000L);
        // Warning
        r.updateMetric("com.example.ReportService#generate", 35_000_000L);
        r.updateMetric("com.example.ReportService#generate", 45_000_000L);
        // Slow
        r.updateMetric("com.example.SlowDAO#heavyQuery", 200_000_000L);
        r.updateMetric("com.example.SlowDAO#heavyQuery", 150_000_000L);
        // Errors
        r.updateMetric("com.example.PaymentService#charge", 50_000_000L);
        r.recordError("com.example.PaymentService#charge", "java.lang.NullPointerException");
        r.updateMetric("com.example.PaymentService#charge", 30_000_000L);
        r.recordError("com.example.PaymentService#charge", "java.io.IOException");
        r.updateMetric("com.example.EmailService#send", 20_000_000L);
        r.recordError("com.example.EmailService#send", "javax.mail.MessagingException");

        // Mock Call Traces
        CallTracer ct = CallTracer.getInstance();
        ct.enterMethod("com.example.WebApp#handleRequest");
        ct.enterMethod("com.example.UserService#getUser");
        ct.exitMethod("com.example.UserService#getUser", 45_000_000L);
        ct.enterMethod("com.example.SlowDAO#heavyQuery");
        ct.exitMethod("com.example.SlowDAO#heavyQuery", 200_000_000L);
        ct.exitMethod("com.example.WebApp#handleRequest", 245_000_000L);

        launch();
    }
}
