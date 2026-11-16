package com.jamb;

import javax.swing.SwingUtilities;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

/** Network transport; all callbacks are dispatched to the Swing EDT. */
public final class GameClient implements AutoCloseable {
    public interface Listener {
        void onWelcome(String token);
        void onState(Properties state);
        void onChat(String message);
        void onError(String message);
        void onDisconnected();
    }

    private final Socket socket;
    private final PrintWriter output;
    private final BufferedReader input;
    private final Listener listener;
    private volatile boolean closed;

    public GameClient(String host, int port, String name, String reconnectToken, Listener listener) throws IOException {
        this.listener = listener;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 7000);
        socket.setKeepAlive(true);
        input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        send("HELLO|" + base64(name) + "|" + base64(reconnectToken));
        Thread reader = new Thread(this::receive, "jamb-receiver");
        reader.setDaemon(true);
        reader.start();
    }

    public static String base64(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }

    private void receive() {
        try {
            String line;
            while ((line = input.readLine()) != null) {
                String[] p = line.split("\\|", 2);
                if (p.length != 2) continue;
                String payload;
                try { payload = new String(Base64.getDecoder().decode(p[1]), StandardCharsets.UTF_8); }
                catch (IllegalArgumentException ex) { continue; }
                switch (p[0]) {
                    case "WELCOME":
                        SwingUtilities.invokeLater(() -> listener.onWelcome(payload));
                        break;
                    case "CHAT":
                        SwingUtilities.invokeLater(() -> listener.onChat(payload));
                        break;
                    case "ERROR":
                        SwingUtilities.invokeLater(() -> listener.onError(payload));
                        break;
                    case "STATE": {
                        try {
                            Properties state = new Properties();
                            state.load(new ByteArrayInputStream(Base64.getDecoder().decode(p[1])));
                            SwingUtilities.invokeLater(() -> listener.onState(state));
                        } catch (IOException | IllegalArgumentException ex) {
                            SwingUtilities.invokeLater(() -> listener.onError("Unable to read game state."));
                        }
                        break;
                    }
                    default: break;
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (!closed) SwingUtilities.invokeLater(listener::onDisconnected);
            try { close(); } catch (IOException ignored) {}
        }
    }

    public synchronized void send(String message) {
        if (!closed) output.println(message);
    }

    @Override public void close() throws IOException {
        closed = true;
        socket.close();
    }
}
