package com.jamb;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single-room, server-authoritative TCP game. Supports 2-5 players and reconnections.
 *  TRAINEE NOTE: This starter intentionally uses FIVE dice. See docs/TRAINEE-CHALLENGE.md.
 */
public final class GameServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 5050;
    private static final int MAX_PLAYERS = 5;
    private final Object lock = new Object();
    private final List<Player> players = new ArrayList<>();
    private final Set<Peer> connections = new HashSet<>();
    private final SecureRandom random = new SecureRandom();
    private final ServerSocket listener;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private boolean started = false, finished = false;
    private int turn = 0, rolls = 0, announced = -1;
    private final int[] dice = new int[5];
    private final boolean[] held = new boolean[5];
    private String activity = "Waiting for players to join.";
    private int gameTurn = 0;

    private static final class Player {
        final String name, token;
        final int[][] board = GameRules.emptyBoard();
        Peer connection;
        Player(String name) { this.name = name; this.token = UUID.randomUUID().toString(); }
    }

    private static final class Peer {
        final Socket socket;
        final BufferedReader input;
        final PrintWriter output;
        Player player;
        Peer(Socket socket) throws IOException {
            this.socket = socket;
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }
        synchronized void send(String line) { output.println(line); }
    }

    public GameServer(int port) throws IOException {
        listener = new ServerSocket();
        listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress("0.0.0.0", port));
    }

    public int port() { return listener.getLocalPort(); }

    public void serve() throws IOException {
        System.out.println("Jamb server listening on port " + port() + " (TCP), max 5 players.");
        while (running.get()) {
            try {
                Socket socket = listener.accept();
                socket.setKeepAlive(true);
                socket.setSoTimeout(0);
                Thread t = new Thread(() -> acceptPeer(socket), "jamb-player");
                t.setDaemon(true);
                t.start();
            } catch (SocketException closed) {
                if (running.get()) throw closed;
                break;
            }
        }
    }

    public void serveAsync() {
        Thread t = new Thread(() -> {
            try { serve(); } catch (IOException ex) { if (running.get()) ex.printStackTrace(); }
        }, "jamb-server");
        t.setDaemon(true);
        t.start();
    }

    private void acceptPeer(Socket socket) {
        Peer peer = null;
        try {
            peer = new Peer(socket);
            synchronized (lock) { connections.add(peer); }
            String line;
            while ((line = peer.input.readLine()) != null) {
                synchronized (lock) {
                    try { process(peer, line); }
                    catch (IllegalArgumentException ex) { error(peer, ex.getMessage()); }
                    catch (RuntimeException ex) { error(peer, "Unexpected command error."); ex.printStackTrace(); }
                }
            }
        } catch (IOException ignored) {
            // A lost connection leaves a reserved seat that can be rejoined with the token.
        } finally {
            synchronized (lock) {
                if (peer != null) {
                    connections.remove(peer);
                    if (peer.player != null && peer.player.connection == peer) {
                        Player p = peer.player;
                        p.connection = null;
                        if (!started) players.remove(p);
                        activity = p.name + " disconnected" + (started ? " (can reconnect)." : ".");
                        broadcastState();
                    }
                }
            }
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void process(Peer peer, String line) {
        if (line.length() > 2048) throw new IllegalArgumentException("Command too long.");
        String[] parts = line.split("\\|", -1);
        String action = parts[0];
        if ("HELLO".equals(action)) { login(peer, parts); return; }
        if (peer.player == null || peer.player.connection != peer) {
            throw new IllegalArgumentException("Join the game first.");
        }
        int me = players.indexOf(peer.player);
        if ("CHAT".equals(action)) {
            if (parts.length != 2) throw new IllegalArgumentException("Bad chat command.");
            String message = decoded(parts[1]).replace('\r', ' ').replace('\n', ' ').trim();
            if (message.length() > 280) throw new IllegalArgumentException("Message too long.");
            if (!message.isEmpty()) {
                String payload = peer.player.name + ": " + message;
                for (Player p : players) if (p.connection != null) p.connection.send("CHAT|" + encoded(payload));
            }
            return;
        }
        if ("START".equals(action)) {
            if (started) throw new IllegalArgumentException("The game already started.");
            if (me != hostIndex()) throw new IllegalArgumentException("Only the host can start the game.");
            if (players.size() < 2 || players.size() > MAX_PLAYERS)
                throw new IllegalArgumentException("A game needs 2 to 5 players.");
            started = true;
            activity = "Game started! " + players.get(0).name + " rolls first.";
            broadcastState();
            return;
        }
        if ("RESTART".equals(action)) {
            if (!finished || me != hostIndex()) throw new IllegalArgumentException("Only the host can restart a finished match.");
            if (players.stream().anyMatch(p -> p.connection == null))
                throw new IllegalArgumentException("All players must reconnect before a rematch.");
            for (Player p : players) for (int[] column : p.board) Arrays.fill(column, -1);
            finished = false;
            started = true;
            turn = 0;
            rolls = 0;
            announced = -1;
            gameTurn = 0;
            Arrays.fill(dice, 0);
            Arrays.fill(held, false);
            activity = "Rematch started! " + players.get(0).name + " rolls first.";
            broadcastState();
            return;
        }
        if ("SKIP".equals(action)) {
            if (!started || finished || me != hostIndex() || players.get(turn).connection != null)
                throw new IllegalArgumentException("The host can skip only a disconnected player's turn.");
            Player absent = players.get(turn);
            int[] cell = announced >= 0 ? new int[]{3, announced} : firstAvailableCell(absent.board);
            if (cell == null) throw new IllegalArgumentException("Player has no empty cells.");
            absent.board[cell[0]][cell[1]] = 0;
            activity = absent.name + " was skipped: 0 in " + GameRules.ROW_NAMES[cell[1]] + ".";
            nextTurn();
            broadcastState();
            return;
        }
        if (!started || finished) throw new IllegalArgumentException("No active game.");
        if (me != turn) throw new IllegalArgumentException("It's not your turn.");
        switch (action) {
            case "ROLL": {
                if (rolls == 3) throw new IllegalArgumentException("Only three rolls per turn.");
                if (rolls == 1 && announced == -1 && !hasNonAnnounceCell(peer.player.board))
                    throw new IllegalArgumentException("Announce a category before your second roll.");
                for (int i = 0; i < dice.length; i++) {
                    if (rolls == 0 || !held[i]) dice[i] = random.nextInt(6) + 1;
                }
                rolls++;
                activity = peer.player.name + " rolled " + rolls + "/3.";
                broadcastState();
                break;
            }
            case "HOLD": {
                if (parts.length != 2) throw new IllegalArgumentException("Bad dice command.");
                int i = parseInt(parts[1], 0, 4);
                if (rolls < 1 || rolls >= 3) throw new IllegalArgumentException("Cannot hold dice now.");
                held[i] = !held[i];
                broadcastState();
                break;
            }
            case "ANNOUNCE": {
                if (parts.length != 2) throw new IllegalArgumentException("Bad announcement.");
                int row = parseInt(parts[1], 0, 12);
                if (rolls != 1 || announced != -1)
                    throw new IllegalArgumentException("Announce once, after the first roll only.");
                if (!GameRules.allowed(peer.player.board, 3, row))
                    throw new IllegalArgumentException("That announced cell is unavailable.");
                announced = row;
                activity = peer.player.name + " announced " + GameRules.ROW_NAMES[row] + ".";
                broadcastState();
                break;
            }
            case "SCORE": {
                if (parts.length != 3) throw new IllegalArgumentException("Bad score command.");
                int col = parseInt(parts[1], 0, 3), row = parseInt(parts[2], 0, 12);
                if (rolls == 0) throw new IllegalArgumentException("Roll before you score.");
                if (!GameRules.allowed(peer.player.board, col, row))
                    throw new IllegalArgumentException("Cell filled or column order violated.");
                if (announced >= 0 && (col != 3 || announced != row))
                    throw new IllegalArgumentException("You must score the announced cell.");
                if (announced == -1 && col == 3)
                    throw new IllegalArgumentException("Announce before choosing the Najava column.");
                int points = GameRules.score(row, dice, rolls);
                peer.player.board[col][row] = points;
                activity = peer.player.name + " scored " + points + " in " + GameRules.ROW_NAMES[row]
                        + " (" + GameRules.COLUMN_NAMES[col] + ").";
                nextTurn();
                broadcastState();
                break;
            }
            default: throw new IllegalArgumentException("Unknown command.");
        }
    }

    private static int parseInt(String s, int min, int max) {
        try {
            int n = Integer.parseInt(s);
            if (n >= min && n <= max) return n;
        } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException("Invalid number.");
    }

    private static int[] firstAvailableCell(int[][] board) {
        for (int col = 0; col < GameRules.COLUMNS; col++)
            for (int row = 0; row < GameRules.ROWS; row++)
                if (GameRules.allowed(board, col, row)) return new int[]{col, row};
        return null;
    }

    private static boolean hasNonAnnounceCell(int[][] board) {
        for (int col = 0; col < 3; col++)
            for (int row = 0; row < GameRules.ROWS; row++)
                if (GameRules.allowed(board, col, row)) return true;
        return false;
    }

    private void nextTurn() {
        rolls = 0;
        announced = -1;
        Arrays.fill(dice, 0);
        Arrays.fill(held, false);
        gameTurn++;
        boolean complete = true;
        for (Player p : players) complete &= GameRules.completed(p.board);
        if (complete) {
            finished = true;
            int best = players.stream().mapToInt(p -> GameRules.total(p.board)).max().orElse(0);
            StringJoiner winners = new StringJoiner(", ");
            for (Player p : players) if (GameRules.total(p.board) == best) winners.add(p.name);
            activity = "Game finished! Winner: " + winners + " (" + best + " points).";
            return;
        }
        for (int i = 0; i < players.size(); i++) {
            turn = (turn + 1) % players.size();
            if (!GameRules.completed(players.get(turn).board)) return;
        }
    }

    private void login(Peer peer, String[] parts) {
        if (peer.player != null) throw new IllegalArgumentException("Already joined.");
        if (parts.length != 3) throw new IllegalArgumentException("Invalid join command.");
        String name = decoded(parts[1]).trim();
        String token = decoded(parts[2]);
        if (name.isEmpty() || name.length() > 18 || name.chars().anyMatch(c -> Character.isISOControl(c)))
            throw new IllegalArgumentException("Choose a name between 1 and 18 characters.");
        Player existing = null;
        for (Player p : players) {
            if (p.name.equalsIgnoreCase(name)) { existing = p; break; }
        }
        if (existing != null) {
            if (existing.connection != null) throw new IllegalArgumentException("This player is already connected.");
            if (!existing.token.equals(token)) throw new IllegalArgumentException("Name reserved. Use the original device to reconnect.");
            peer.player = existing;
            existing.connection = peer;
            activity = name + " reconnected.";
        } else {
            if (started) throw new IllegalArgumentException("Game already started; new players cannot join.");
            if (players.size() == MAX_PLAYERS) throw new IllegalArgumentException("Room is full (5 players).");
            Player p = new Player(name);
            p.connection = peer;
            peer.player = p;
            players.add(p);
            activity = name + " joined (" + players.size() + "/5).";
        }
        peer.send("WELCOME|" + encoded(peer.player.token));
        broadcastState();
    }

    private int hostIndex() {
        for (int i = 0; i < players.size(); i++) if (players.get(i).connection != null) return i;
        return 0;
    }

    private void broadcastState() {
        Properties p = new Properties();
        p.setProperty("phase", finished ? "FINISHED" : started ? "PLAYING" : "LOBBY");
        p.setProperty("turn", String.valueOf(turn));
        p.setProperty("host", String.valueOf(hostIndex()));
        p.setProperty("rolls", String.valueOf(rolls));
        p.setProperty("announced", String.valueOf(announced));
        p.setProperty("players", String.valueOf(players.size()));
        p.setProperty("move", String.valueOf(gameTurn));
        p.setProperty("activity", activity);
        for (int d = 0; d < 5; d++) {
            p.setProperty("die" + d, String.valueOf(dice[d]));
            p.setProperty("held" + d, String.valueOf(held[d]));
        }
        for (int i = 0; i < players.size(); i++) {
            Player pl = players.get(i);
            p.setProperty("p" + i + ".name", pl.name);
            p.setProperty("p" + i + ".online", String.valueOf(pl.connection != null));
            p.setProperty("p" + i + ".total", String.valueOf(GameRules.total(pl.board)));
            for (int col = 0; col < GameRules.COLUMNS; col++)
                for (int row = 0; row < GameRules.ROWS; row++)
                    p.setProperty("p" + i + ".c" + col + ".r" + row, String.valueOf(pl.board[col][row]));
        }
        for (int i = 0; i < players.size(); i++) {
            Peer connection = players.get(i).connection;
            if (connection == null) continue;
            p.setProperty("you", String.valueOf(i));
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                p.store(output, "Jamb network state");
                connection.send("STATE|" + Base64.getEncoder().encodeToString(output.toByteArray()));
            } catch (IOException ex) { error(connection, "Unable to send game state."); }
        }
    }

    private static String encoded(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String decoded(String s) {
        try { return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Malformed message."); }
    }
    private static void error(Peer peer, String message) { peer.send("ERROR|" + encoded(message)); }

    @Override public void close() throws IOException {
        running.set(false);
        listener.close();
        synchronized (lock) {
            for (Peer peer : connections) try { peer.socket.close(); } catch (IOException ignored) {}
            connections.clear();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        try (GameServer server = new GameServer(port)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { server.close(); } catch (IOException ignored) {}
            }));
            server.serve();
        }
    }
}
