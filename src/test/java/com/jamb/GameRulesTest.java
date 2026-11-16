package com.jamb;

/** Dependency-free regression tests; run with `java -ea`. */
public final class GameRulesTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void eq(int expected, int actual) { check(expected == actual, "Expected " + expected + ", found " + actual); }

    public static void main(String[] args) {
        // Five-dice baseline: a six-dice input is intentionally NOT supported yet.
        eq(5, GameRules.DICE);
        boolean rejectedSix = false;
        try {
            GameRules.score(GameRules.MAX, new int[]{1, 2, 3, 4, 5, 6}, 1);
        } catch (IllegalArgumentException expected) {
            rejectedSix = true;
        }
        check(rejectedSix, "Baseline rejects six dice until the trainee implements the feature");
        int[] straight = {1, 2, 3, 4, 5};
        eq(66, GameRules.score(GameRules.KENTA, straight, 1));
        eq(56, GameRules.score(GameRules.KENTA, straight, 2));
        eq(46, GameRules.score(GameRules.KENTA, straight, 3));
        eq(0, GameRules.score(GameRules.KENTA, new int[]{1, 2, 3, 4, 6}, 1));
        eq(25, GameRules.score(4, new int[]{5, 5, 5, 5, 5}, 1));
        eq(0, GameRules.score(0, new int[]{2, 2, 2, 2, 2}, 1));
        eq(35, GameRules.score(GameRules.TRILING, new int[]{5, 5, 5, 4, 2}, 1));
        eq(47, GameRules.score(GameRules.FULL, new int[]{3, 3, 3, 4, 4}, 1));
        eq(0, GameRules.score(GameRules.FULL, new int[]{3, 3, 3, 3, 3}, 1));
        eq(64, GameRules.score(GameRules.POKER, new int[]{6, 6, 6, 6, 2}, 1));
        eq(80, GameRules.score(GameRules.JAMB, new int[]{6, 6, 6, 6, 6}, 1));
        eq(0, GameRules.score(GameRules.JAMB, new int[]{6, 6, 6, 6, 5}, 1));
        int[][] board = GameRules.emptyBoard();
        check(GameRules.allowed(board, 0, 0), "First down cell available");
        check(!GameRules.allowed(board, 0, 1), "Second down cell unavailable");
        check(GameRules.allowed(board, 1, 12), "Last up cell available");
        check(!GameRules.allowed(board, 1, 11), "Next up cell unavailable");
        check(GameRules.allowed(board, 2, 10), "Free cell available");
        board[0][0] = 4;
        check(GameRules.allowed(board, 0, 1), "Second down cell now available");
        check(!GameRules.allowed(board, 0, 0), "Cannot rewrite a cell");
        int[] col = board[0];
        col[1] = 8; col[2] = 12; col[3] = 16; col[4] = 20; col[5] = 24;
        eq(30, GameRules.numberBonus(col));
        col[6] = 28; col[7] = 12;
        eq(64, GameRules.maxMinBonus(col));
        eq(178, GameRules.columnTotal(col));
        eq(178, GameRules.total(board));
        eq(0, GameRules.maxMinBonus(board[1]));
        for (int[] c : board) java.util.Arrays.fill(c, 0);
        check(GameRules.completed(board), "All boxes complete");
        System.out.println("PASS: Jamb scoring, ordering, bonuses and completion");
    }
}
