package com.jamb;

import javax.swing.*;

public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--server".equals(args[0])) {
            GameServer.main(args.length > 1 ? new String[]{args[1]} : new String[0]);
        } else {
            SwingUtilities.invokeLater(() -> new GameWindow().setVisible(true));
        }
    }
}
