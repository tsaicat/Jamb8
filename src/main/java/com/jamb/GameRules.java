package com.jamb;

import java.util.Arrays;

/** Authoritative, deterministic five-dice Serbian Jamb scoring rules. */
public final class GameRules {
    private GameRules() {}

    public static final int DICE = 5, ROWS = 13, COLUMNS = 4;
    public static final int MAX = 6, MIN = 7, TRILING = 8, KENTA = 9,
            FULL = 10, POKER = 11, JAMB = 12;
    public static final String[] ROW_NAMES = {
            "Jedinice · 1", "Dvojke · 2", "Trojke · 3", "Četvorke · 4", "Petice · 5", "Šestice · 6",
            "Maksimum", "Minimum", "Triling", "Kenta", "Ful", "Poker", "Jamb"
    };
    public static final String[] COLUMN_NAMES = {"↓ Dole", "↑ Gore", "↔ Slobodna", "★ Najava"};

    public static int score(int category, int[] dice, int rollNumber) {
        if (category < 0 || category >= ROWS || dice == null || dice.length != DICE
                || rollNumber < 1 || rollNumber > 3) {
            throw new IllegalArgumentException("Invalid category, dice or roll number");
        }
        int[] counts = new int[7];
        int sum = 0;
        for (int face : dice) {
            if (face < 1 || face > 6) throw new IllegalArgumentException("Invalid die: " + face);
            counts[face]++;
            sum += face;
        }
        if (category <= 5) return counts[category + 1] * (category + 1);
        if (category == MAX || category == MIN) return sum;
        if (category == KENTA) {
            boolean small = true, large = true;
            for (int i = 1; i <= 5; i++) small &= counts[i] == 1;
            for (int i = 2; i <= 6; i++) large &= counts[i] == 1;
            return small || large ? (rollNumber == 1 ? 66 : rollNumber == 2 ? 56 : 46) : 0;
        }
        if (category == FULL) {
            boolean triple = false, pair = false;
            for (int i = 1; i <= 6; i++) {
                triple |= counts[i] == 3;
                pair |= counts[i] == 2;
            }
            return triple && pair ? sum + 30 : 0;
        }
        for (int i = 6; i >= 1; i--) {
            if (category == TRILING && counts[i] >= 3) return 3 * i + 20;
            if (category == POKER && counts[i] >= 4) return 4 * i + 40;
            if (category == JAMB && counts[i] == 5) return 5 * i + 50;
        }
        return 0;
    }

    /** A missing cell is represented as -1; zero is a deliberate crossed-out result. */
    public static boolean allowed(int[][] board, int column, int category) {
        if (board == null || board.length != COLUMNS || column < 0 || column >= COLUMNS
                || category < 0 || category >= ROWS || board[column][category] != -1) return false;
        if (column == 0) {
            for (int r = 0; r < category; r++) if (board[column][r] == -1) return false;
        } else if (column == 1) {
            for (int r = ROWS - 1; r > category; r--) if (board[column][r] == -1) return false;
        }
        return true;
    }

    public static int[][] emptyBoard() {
        int[][] b = new int[COLUMNS][ROWS];
        for (int[] col : b) Arrays.fill(col, -1);
        return b;
    }

    public static boolean completed(int[][] board) {
        for (int[] col : board) for (int v : col) if (v == -1) return false;
        return true;
    }

    public static int numberBonus(int[] column) {
        int sum = 0;
        for (int i = 0; i < 6; i++) {
            if (column[i] < 0) return 0;
            sum += column[i];
        }
        return sum >= 60 ? 30 : 0;
    }

    /** (MAX - MIN) × scored Ones. It may legitimately be negative. */
    public static int maxMinBonus(int[] column) {
        if (column[0] == -1 || column[MAX] == -1 || column[MIN] == -1) return 0;
        return (column[MAX] - column[MIN]) * column[0];
    }

    public static int columnTotal(int[] column) {
        int total = numberBonus(column) + maxMinBonus(column);
        for (int i = 0; i < ROWS; i++) {
            if (i != MAX && i != MIN && column[i] >= 0) total += column[i];
        }
        return total;
    }

    public static int total(int[][] board) {
        int total = 0;
        for (int[] col : board) total += columnTotal(col);
        return total;
    }
}
