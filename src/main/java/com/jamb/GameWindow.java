package com.jamb;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/** The whole desktop client: lobby, dice, scorecards, chat, standings and rules. */
public final class GameWindow extends JFrame implements GameClient.Listener {
    private static final Color BG = new Color(15, 23, 39);
    private static final Color PANEL = new Color(23, 35, 54);
    private static final Color CELL = new Color(30, 44, 65);
    private static final Color LIGHT = new Color(236, 244, 249);
    private static final Color MUTED = new Color(160, 176, 195);
    private static final Color MINT = new Color(94, 215, 193);
    private static final Color AMBER = new Color(249, 197, 121);
    private static final Color BAD = new Color(245, 135, 135);
    private static final Font NORMAL = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font BOLD = new Font("Segoe UI", Font.BOLD, 14);

    private final Preferences prefs = Preferences.userNodeForPackage(GameWindow.class);
    private final CardLayout cards = new CardLayout();
    private final JPanel main = new JPanel(cards);
    private GameClient client;
    private GameServer hostedServer;
    private Properties state = new Properties();
    private int me = -1, count = 0, currentRolls = 0, announced = -1;
    private String reconnectKey = "";
    private final JTextField nameField = new JTextField("Player", 18);
    private final JTextField hostField = new JTextField("localhost", 22);
    private final JTextField portField = new JTextField("5050", 6);
    private final JLabel welcomeError = text("", 13, BAD);
    private final JLabel phaseLabel = text("LOBBY", 13, MINT);
    private final JLabel turnLabel = text("Waiting for players", 21, LIGHT);
    private final JLabel activityLabel = text("Join with your friends to begin.", 13, MUTED);
    private final JLabel hintLabel = text("Roll your dice, then click an available score cell.", 13, MUTED);
    private final JButton rollButton = button("ROLL DICE", true);
    private final JButton startButton = button("START GAME", true);
    private final JButton skipButton = button("SKIP OFFLINE", false);
    private final DieButton[] dieButtons = new DieButton[5];
    private final JTabbedPane scoreTabs = new JTabbedPane();
    private final List<JTable> tables = new ArrayList<>();
    private final JPanel leaderboard = new JPanel();
    private final JTextArea chatArea = new JTextArea();
    private final JTextField chatInput = new JTextField();
    private boolean firstState = true;

    public GameWindow() {
        super("JAMB · Online Multiplayer");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 750));
        setSize(1240, 840);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        add(main);
        main.add(connectionPanel(), "CONNECT");
        main.add(gamePanel(), "GAME");
        cards.show(main, "CONNECT");
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (client != null) try { client.close(); } catch (IOException ignored) {}
                if (hostedServer != null) try { hostedServer.close(); } catch (IOException ignored) {}
                dispose();
            }
        });
        setupEvents();
    }

    private JPanel connectionPanel() {
        JPanel wrapper = panel(BG, new GridBagLayout());
        JPanel box = panel(PANEL, new GridBagLayout());
        box.setBorder(new CompoundBorder(new LineBorder(new Color(53, 76, 99), 1, true),
                new EmptyBorder(30, 42, 36, 42)));
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = 0; g.anchor = GridBagConstraints.CENTER;
        g.insets = new Insets(6, 6, 6, 6); g.fill = GridBagConstraints.HORIZONTAL;
        JLabel title = text("J A M B", 42, LIGHT);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        box.add(title, g);
        g.gridy++;
        JLabel subtitle = text("Five dice. Up to five friends. One champion.", 15, MUTED);
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        box.add(subtitle, g);
        g.gridy++; g.insets = new Insets(18, 6, 3, 6);
        box.add(text("YOUR PLAYER NAME", 12, MUTED), g);
        g.gridy++; g.insets = new Insets(3, 6, 8, 6);
        box.add(input(nameField), g);
        g.gridy++; g.insets = new Insets(10, 6, 3, 6);
        box.add(text("SERVER IP / HOSTNAME", 12, MUTED), g);
        g.gridy++; g.insets = new Insets(3, 6, 8, 6);
        box.add(input(hostField), g);
        g.gridy++; g.insets = new Insets(10, 6, 3, 6);
        box.add(text("PORT", 12, MUTED), g);
        g.gridy++; g.insets = new Insets(3, 6, 8, 6);
        box.add(input(portField), g);
        g.gridy++; g.insets = new Insets(20, 6, 6, 6);
        JButton connect = button("JOIN FRIEND'S GAME", true);
        connect.addActionListener(e -> connect(false));
        box.add(connect, g);
        g.gridy++; g.insets = new Insets(3, 6, 6, 6);
        JButton host = button("HOST & PLAY ON THIS PC", false);
        host.addActionListener(e -> connect(true));
        box.add(host, g);
        g.gridy++; box.add(welcomeError, g);
        g.gridy++; g.insets = new Insets(16, 6, 0, 6);
        JLabel small = text("Host creates a local TCP server. Friends connect to its IP.", 12, MUTED);
        small.setHorizontalAlignment(SwingConstants.CENTER);
        box.add(small, g);
        wrapper.add(box);
        return wrapper;
    }

    private JPanel gamePanel() {
        JPanel base = panel(BG, new BorderLayout(14, 12));
        base.setBorder(new EmptyBorder(16, 18, 16, 18));
        JPanel top = panel(BG, new BorderLayout(6, 11));
        JPanel head = panel(BG, new BorderLayout());
        JPanel headings = panel(BG, new GridLayout(3, 1, 0, 4));
        headings.add(phaseLabel);
        headings.add(turnLabel);
        headings.add(activityLabel);
        head.add(headings, BorderLayout.CENTER);
        JButton rules = button("?  RULES", false);
        rules.addActionListener(e -> showRules());
        head.add(rules, BorderLayout.EAST);
        top.add(head, BorderLayout.NORTH);
        JPanel dice = panel(PANEL, new BorderLayout(10, 5));
        dice.setBorder(new CompoundBorder(new LineBorder(new Color(46, 67, 88), 1, true), new EmptyBorder(10, 17, 10, 17)));
        JPanel dieRow = panel(PANEL, new FlowLayout(FlowLayout.LEFT, 14, 2));
        for (int i = 0; i < 5; i++) {
            final int index = i;
            DieButton die = new DieButton();
            die.addActionListener(e -> send("HOLD|" + index));
            dieButtons[i] = die;
            dieRow.add(die);
        }
        dice.add(dieRow, BorderLayout.CENTER);
        JPanel actions = panel(PANEL, new GridLayout(2, 1, 4, 6));
        rollButton.setPreferredSize(new Dimension(174, 45));
        actions.add(rollButton);
        actions.add(hintLabel);
        dice.add(actions, BorderLayout.EAST);
        top.add(dice, BorderLayout.CENTER);
        base.add(top, BorderLayout.NORTH);

        scoreTabs.setFont(BOLD);
        scoreTabs.setBackground(PANEL);
        scoreTabs.setForeground(LIGHT);
        scoreTabs.setBorder(new EmptyBorder(2, 2, 2, 2));
        base.add(scoreTabs, BorderLayout.CENTER);

        JPanel sidebar = panel(PANEL, new BorderLayout(10, 10));
        sidebar.setPreferredSize(new Dimension(300, 400));
        sidebar.setBorder(new CompoundBorder(new LineBorder(new Color(46, 67, 88), 1, true), new EmptyBorder(15, 12, 15, 12)));
        JPanel upperSide = panel(PANEL, new BorderLayout(6, 5));
        upperSide.add(text("LEADERBOARD", 13, MINT), BorderLayout.NORTH);
        leaderboard.setLayout(new BoxLayout(leaderboard, BoxLayout.Y_AXIS));
        leaderboard.setBackground(PANEL);
        upperSide.add(leaderboard, BorderLayout.CENTER);
        JPanel controls = panel(PANEL, new GridLayout(2, 1, 0, 5));
        controls.add(startButton);
        controls.add(skipButton);
        upperSide.add(controls, BorderLayout.SOUTH);
        sidebar.add(upperSide, BorderLayout.NORTH);
        JPanel chat = panel(PANEL, new BorderLayout(5, 6));
        chat.add(text("GAME CHAT", 13, MINT), BorderLayout.NORTH);
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(NORMAL);
        chatArea.setBackground(CELL);
        chatArea.setForeground(LIGHT);
        chatArea.setBorder(new EmptyBorder(9, 8, 9, 8));
        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setBorder(new LineBorder(new Color(43, 62, 83)));
        chat.add(scroll, BorderLayout.CENTER);
        JPanel chatSend = panel(PANEL, new BorderLayout(5, 0));
        chatSend.add(input(chatInput), BorderLayout.CENTER);
        JButton send = button("SEND", false);
        send.addActionListener(e -> submitChat());
        chatSend.add(send, BorderLayout.EAST);
        chat.add(chatSend, BorderLayout.SOUTH);
        sidebar.add(chat, BorderLayout.CENTER);
        base.add(sidebar, BorderLayout.EAST);
        JLabel bottom = text("CLICK A DIE to keep it  •  Click a glowing score cell to write  •  NAJAVA: announce after roll 1", 12, MUTED);
        base.add(bottom, BorderLayout.SOUTH);
        return base;
    }

    private void setupEvents() {
        rollButton.addActionListener(e -> send("ROLL"));
        startButton.addActionListener(e -> send("FINISHED".equals(state.getProperty("phase")) ? "RESTART" : "START"));
        skipButton.addActionListener(e -> send("SKIP"));
        chatInput.addActionListener(e -> submitChat());
    }

    private void connect(boolean hostOwn) {
        String name = nameField.getText().trim();
        String hostname = hostOwn ? "127.0.0.1" : hostField.getText().trim();
        int port;
        try { port = Integer.parseInt(portField.getText().trim()); }
        catch (NumberFormatException e) { welcomeError.setText("Port must be a number."); return; }
        if (name.isEmpty() || name.length() > 18) { welcomeError.setText("Name must be 1–18 characters."); return; }
        if (hostname.isEmpty() || port < 1 || port > 65535) { welcomeError.setText("Enter a valid hostname and TCP port."); return; }
        welcomeError.setText("Connecting…");
        reconnectKey = Integer.toHexString((hostname.toLowerCase(Locale.ROOT) + ":" + port + ":" + name.toLowerCase(Locale.ROOT)).hashCode());
        String oldToken = prefs.get(reconnectKey, "");
        new Thread(() -> {
            try {
                if (hostOwn) {
                    hostedServer = new GameServer(port);
                    hostedServer.serveAsync();
                }
                client = new GameClient(hostname, port, name, oldToken, this);
            } catch (Exception ex) {
                if (hostOwn && hostedServer != null) {
                    try { hostedServer.close(); } catch (IOException ignored) {}
                    hostedServer = null;
                }
                SwingUtilities.invokeLater(() -> welcomeError.setText("Connection failed: " + ex.getMessage()));
            }
        }, "jamb-connect").start();
    }

    private void submitChat() {
        String text = chatInput.getText().trim();
        if (!text.isEmpty() && text.length() <= 280) {
            send("CHAT|" + GameClient.base64(text));
            chatInput.setText("");
        }
    }

    private void send(String message) { if (client != null) client.send(message); }

    @Override public void onWelcome(String token) {
        prefs.put(reconnectKey, token);
        cards.show(main, "GAME");
        chatArea.append("Connected! Chat, watch the scoreboard, and get ready to roll.\n");
    }

    @Override public void onState(Properties newState) {
        state = newState;
        me = intProp("you", -1);
        currentRolls = intProp("rolls", 0);
        announced = intProp("announced", -1);
        int players = intProp("players", 0);
        if (count != players) {
            int selected = scoreTabs.getSelectedIndex();
            count = players;
            createScoreTabs();
            if (selected >= 0 && selected < count) scoreTabs.setSelectedIndex(selected);
        }
        if (firstState && me >= 0 && me < count) { scoreTabs.setSelectedIndex(me); firstState = false; }
        String phase = state.getProperty("phase", "LOBBY");
        int turn = intProp("turn", 0);
        boolean started = "PLAYING".equals(phase), finished = "FINISHED".equals(phase);
        boolean myTurn = started && me == turn;
        phaseLabel.setText(finished ? "★ FINISHED" : started ? "● LIVE GAME  ·  ROUND " + (intProp("move", 0) / Math.max(1, count) + 1) : "● LOBBY  ·  " + count + "/5 PLAYERS");
        String turnName = state.getProperty("p" + turn + ".name", "Player");
        turnLabel.setText(finished ? "Final scoreboard" : started ? myTurn ? "YOUR TURN  —  make it count!" : "Playing now: " + turnName : "Waiting for the host to start");
        activityLabel.setText(state.getProperty("activity", ""));
        rollButton.setEnabled(myTurn && currentRolls < 3);
        rollButton.setText(currentRolls == 0 ? "ROLL DICE" : "ROLL AGAIN " + currentRolls + "/3");
        for (int i = 0; i < 5; i++) {
            dieButtons[i].setFace(intProp("die" + i, 0));
            dieButtons[i].setHeld("true".equals(state.getProperty("held" + i)));
            dieButtons[i].setEnabled(myTurn && currentRolls > 0 && currentRolls < 3);
        }
        boolean isHost = me == intProp("host", 0);
        startButton.setVisible((!started || finished) && isHost);
        startButton.setText(finished ? "PLAY AGAIN" : "START GAME");
        startButton.setEnabled(finished ? allConnected() : count >= 2);
        skipButton.setVisible(started && isHost && "false".equals(state.getProperty("p" + turn + ".online")));
        hintLabel.setText(!myTurn ? "Wait for your turn" : currentRolls == 0 ? "3 rolls available" : announced >= 0 ? "NAJAVA LOCKED · choose your cell" : (3 - currentRolls) + " rerolls remain");
        for (int i = 0; i < tables.size(); i++) {
            scoreTabs.setTitleAt(i, state.getProperty("p" + i + ".name", "Player") + "  [" + intProp("p" + i + ".total", 0) + "]");
            ((AbstractTableModel) tables.get(i).getModel()).fireTableDataChanged();
        }
        updateLeaderboard(turn);
        repaint();
    }

    private void createScoreTabs() {
        scoreTabs.removeAll();
        tables.clear();
        for (int p = 0; p < count; p++) {
            final int player = p;
            ScoreModel model = new ScoreModel(p);
            JTable table = new JTable(model);
            table.setFont(NORMAL);
            table.setRowHeight(32);
            table.getTableHeader().setFont(BOLD);
            table.getTableHeader().setReorderingAllowed(false);
            table.getTableHeader().setResizingAllowed(false);
            table.getTableHeader().setPreferredSize(new Dimension(0, 38));
            table.getTableHeader().setBackground(CELL);
            table.getTableHeader().setForeground(LIGHT);
            table.setBackground(PANEL);
            table.setForeground(LIGHT);
            table.setGridColor(new Color(43, 63, 84));
            table.setShowGrid(true);
            table.setSelectionBackground(CELL);
            table.setSelectionForeground(LIGHT);
            table.setRowSelectionAllowed(false);
            table.setCellSelectionEnabled(false);
            table.setDefaultRenderer(Object.class, new ScoreRenderer(p));
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            table.getColumnModel().getColumn(0).setPreferredWidth(182);
            for (int c = 1; c < 5; c++) table.getColumnModel().getColumn(c).setPreferredWidth(124);
            table.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int row = table.rowAtPoint(e.getPoint()), col = table.columnAtPoint(e.getPoint());
                    if (row >= 0 && col > 0) scoreClick(player, row, col - 1);
                }
            });
            JScrollPane pane = new JScrollPane(table);
            pane.setBorder(new LineBorder(new Color(43, 63, 84)));
            pane.getViewport().setBackground(PANEL);
            scoreTabs.addTab("Player " + (p + 1), pane);
            tables.add(table);
        }
    }

    private void scoreClick(int player, int displayRow, int column) {
        if (player != me || !"PLAYING".equals(state.getProperty("phase")) || me != intProp("turn", -1) || currentRolls == 0) return;
        int row = scoreRow(displayRow);
        if (row < 0 || !available(player, column, row)) return;
        if (column == 3 && announced < 0) {
            if (currentRolls != 1) return;
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Announce " + GameRules.ROW_NAMES[row] + "?\nYou MUST score this cell before ending your turn.",
                    "Lock Najava", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) send("ANNOUNCE|" + row);
            return;
        }
        if (announced >= 0 && (column != 3 || row != announced)) return;
        if (column == 3 && announced != row) return;
        int[] dice = currentDice();
        int score = GameRules.score(row, dice, currentRolls);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Write " + score + " points for " + GameRules.ROW_NAMES[row] + "\nin " + GameRules.COLUMN_NAMES[column] + "?\nThis cannot be undone.",
                "Record score", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) send("SCORE|" + column + "|" + row);
    }

    private boolean available(int player, int col, int row) {
        if (intProp("p" + player + ".c" + col + ".r" + row, -1) != -1) return false;
        if (col == 0) for (int r = 0; r < row; r++) if (intProp("p" + player + ".c0.r" + r, -1) == -1) return false;
        if (col == 1) for (int r = 12; r > row; r--) if (intProp("p" + player + ".c1.r" + r, -1) == -1) return false;
        return true;
    }

    private boolean clickable(int player, int col, int row) {
        if (player != me || me != intProp("turn", -1) || !"PLAYING".equals(state.getProperty("phase"))
                || currentRolls == 0 || !available(player, col, row)) return false;
        if (announced >= 0) return col == 3 && row == announced;
        return col != 3 || currentRolls == 1;
    }

    private int[] currentDice() {
        int[] arr = new int[5];
        for (int i = 0; i < 5; i++) arr[i] = intProp("die" + i, 0);
        return arr;
    }

    private boolean allConnected() {
        for (int i = 0; i < count; i++)
            if (!Boolean.parseBoolean(state.getProperty("p" + i + ".online", "false"))) return false;
        return true;
    }

    private int intProp(String key, int fallback) {
        try { return Integer.parseInt(state.getProperty(key, String.valueOf(fallback))); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private static int scoreRow(int displayRow) {
        if (displayRow <= 5) return displayRow;
        if (displayRow == 7) return 6;
        if (displayRow == 8) return 7;
        if (displayRow >= 10 && displayRow <= 14) return displayRow - 2;
        return -1;
    }

    private final class ScoreModel extends AbstractTableModel {
        final int player;
        ScoreModel(int player) { this.player = player; }
        @Override public int getRowCount() { return 16; }
        @Override public int getColumnCount() { return 5; }
        @Override public String getColumnName(int c) { return c == 0 ? "CATEGORY" : GameRules.COLUMN_NAMES[c - 1]; }
        @Override public Object getValueAt(int r, int c) {
            if (c == 0) {
                if (r == 6) return "BONUS · 60+";
                if (r == 9) return "(Max − Min) × Ones";
                if (r == 15) return "TOTAL";
                return GameRules.ROW_NAMES[scoreRow(r)];
            }
            int col = c - 1;
            if (r == 6 || r == 9 || r == 15) {
                int[] values = new int[13];
                for (int i = 0; i < 13; i++) values[i] = intProp("p" + player + ".c" + col + ".r" + i, -1);
                return r == 6 ? String.valueOf(GameRules.numberBonus(values))
                        : r == 9 ? String.valueOf(GameRules.maxMinBonus(values))
                        : String.valueOf(GameRules.columnTotal(values));
            }
            int row = scoreRow(r);
            int filled = intProp("p" + player + ".c" + col + ".r" + row, -1);
            if (filled >= 0) return String.valueOf(filled);
            if (clickable(player, col, row)) return "+ " + GameRules.score(row, currentDice(), currentRolls);
            return "·";
        }
    }

    private final class ScoreRenderer extends DefaultTableCellRenderer {
        final int player;
        ScoreRenderer(int player) { this.player = player; setHorizontalAlignment(SwingConstants.CENTER); }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setFont(column == 0 || row == 15 ? BOLD : NORMAL);
            setBorder(new EmptyBorder(0, column == 0 ? 12 : 4, 0, 4));
            if (column == 0) setHorizontalAlignment(SwingConstants.LEFT);
            else setHorizontalAlignment(SwingConstants.CENTER);
            boolean summary = row == 6 || row == 9 || row == 15;
            setBackground(summary ? new Color(37, 54, 73) : row % 2 == 0 ? PANEL : CELL);
            setForeground(summary ? MINT : column == 0 ? LIGHT : MUTED);
            int category = scoreRow(row);
            if (column > 0 && category >= 0) {
                int col = column - 1;
                int filled = intProp("p" + player + ".c" + col + ".r" + category, -1);
                if (filled >= 0) setForeground(filled == 0 ? MUTED : LIGHT);
                else if (clickable(player, col, category)) {
                    setBackground(col == 3 ? new Color(72, 64, 48) : new Color(31, 69, 69));
                    setForeground(col == 3 ? AMBER : MINT);
                    setFont(BOLD);
                }
            }
            return this;
        }
    }

    private void updateLeaderboard(int turn) {
        leaderboard.removeAll();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < count; i++) order.add(i);
        order.sort((a, b) -> Integer.compare(intProp("p" + b + ".total", 0), intProp("p" + a + ".total", 0)));
        for (int i = 0; i < order.size(); i++) {
            int player = order.get(i);
            JPanel item = panel(i % 2 == 0 ? CELL : PANEL, new BorderLayout(6, 0));
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 33));
            item.setBorder(new EmptyBorder(8, 7, 8, 7));
            String name = state.getProperty("p" + player + ".name", "Player");
            boolean connected = Boolean.parseBoolean(state.getProperty("p" + player + ".online", "false"));
            JLabel who = text((player == turn && "PLAYING".equals(state.getProperty("phase")) ? "▶ " : "")
                    + name + (connected ? "" : " (offline)"), 13, player == me ? MINT : LIGHT);
            item.add(who, BorderLayout.CENTER);
            item.add(text(String.valueOf(intProp("p" + player + ".total", 0)), 14, AMBER), BorderLayout.EAST);
            leaderboard.add(item);
        }
        leaderboard.revalidate(); leaderboard.repaint();
    }

    @Override public void onChat(String message) {
        chatArea.append(message + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    @Override public void onError(String message) {
        if (!main.isShowing() || !"GAME".equalsIgnoreCase(getActiveCard())) {
            welcomeError.setText(message);
        } else {
            JOptionPane.showMessageDialog(this, message, "Jamb", JOptionPane.WARNING_MESSAGE);
        }
    }

    private String getActiveCard() { return me < 0 ? "CONNECT" : "GAME"; }

    @Override public void onDisconnected() {
        JOptionPane.showMessageDialog(this, "Disconnected from the Jamb server.\nReopen the app using the same player name to reclaim your seat.", "Connection lost", JOptionPane.WARNING_MESSAGE);
        if (hostedServer == null) { client = null; cards.show(main, "CONNECT"); }
    }

    private void showRules() {
        String rules = String.join("\n",
                "SERBIAN JAMB  •  FIVE DICE, FOUR COLUMNS",
                "",
                "Each player takes up to 3 rolls per turn. After a roll, click dice",
                "you want to keep. Click a highlighted score cell to write points.",
                "An invalid combination is written as 0 (crossed out).",
                "",
                "↓ DOLE: fill from Ones at the top to Jamb at the bottom.",
                "↑ GORE: fill from Jamb at the bottom to Ones at the top.",
                "↔ SLOBODNA: score any empty row in any order.",
                "★ NAJAVA: after the FIRST roll, click a row to announce it.",
                "Once announced you MUST fill that same row (even if zero).",
                "Click the announced cell again to write your score.",
                "",
                "1–6: sum only dice matching the row number.",
                "UPPER BONUS: +30 when all six number rows sum to 60+.",
                "MAX and MIN: sum of all five dice, then (MAX − MIN) × Ones.",
                "TRILING: three matching dice × their value, +20.",
                "KENTA: 1-2-3-4-5 or 2-3-4-5-6; 66/56/46 on roll 1/2/3.",
                "FUL: three of a kind + a different pair; sum all dice +30.",
                "POKER: four matching dice × their value, +40.",
                "JAMB: five matching dice × their value, +50.",
                "",
                "Each column is totaled separately, then added for your result.",
                "The highest total wins. A tie is possible."
        );
        JTextArea area = new JTextArea(rules);
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setEditable(false);
        area.setBackground(PANEL);
        area.setForeground(LIGHT);
        area.setBorder(new EmptyBorder(18, 18, 18, 18));
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "How to play Jamb", JOptionPane.PLAIN_MESSAGE);
    }

    private static JPanel panel(Color color, LayoutManager layout) {
        JPanel p = new JPanel(layout); p.setBackground(color); return p;
    }
    private static JLabel text(String value, int size, Color color) {
        JLabel l = new JLabel(value);
        l.setFont(new Font("Segoe UI", size >= 19 ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(color);
        return l;
    }
    private static JButton button(String value, boolean primary) {
        JButton b = new JButton(value);
        b.setFont(BOLD);
        b.setBackground(primary ? MINT : CELL);
        b.setForeground(primary ? BG : LIGHT);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new CompoundBorder(new LineBorder(primary ? MINT : new Color(73, 96, 117)), new EmptyBorder(10, 12, 10, 12)));
        return b;
    }
    private static JTextField input(JTextField f) {
        f.setFont(NORMAL);
        f.setBackground(CELL);
        f.setForeground(LIGHT);
        f.setCaretColor(LIGHT);
        f.setBorder(new CompoundBorder(new LineBorder(new Color(70, 91, 111)), new EmptyBorder(9, 9, 9, 9)));
        return f;
    }

    /** Draw dice ourselves so no OS-specific emoji fonts are required. */
    private static final class DieButton extends JButton {
        private int face;
        private boolean held;
        DieButton() {
            setPreferredSize(new Dimension(66, 66));
            setMinimumSize(new Dimension(66, 66));
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Click to keep/release this die");
        }
        void setFace(int face) { this.face = face; repaint(); }
        void setHeld(boolean held) { this.held = held; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D x = (Graphics2D) g.create();
            x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            x.setColor(held ? MINT : face == 0 ? new Color(67, 83, 101) : LIGHT);
            x.fillRoundRect(2, 2, 61, 61, 12, 12);
            x.setColor(new Color(12, 28, 42));
            int a = 18, b = 33, c = 47;
            if (face == 1 || face == 3 || face == 5) pip(x, b, b);
            if (face >= 2) { pip(x, a, a); pip(x, c, c); }
            if (face >= 4) { pip(x, c, a); pip(x, a, c); }
            if (face == 6) { pip(x, a, b); pip(x, c, b); }
            if (held) { x.setColor(BG); x.setFont(new Font("Segoe UI", Font.BOLD, 9)); x.drawString("KEEP", 20, 62); }
            x.dispose();
        }
        private static void pip(Graphics2D g, int xx, int yy) { g.fillOval(xx - 5, yy - 5, 10, 10); }
    }
}
