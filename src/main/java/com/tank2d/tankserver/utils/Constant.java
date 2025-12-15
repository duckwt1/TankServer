package com.tank2d.tankserver.utils;

public class Constant {

    // ===== GAME CONFIG (không liên quan mạng) =====
    public static final int TILESIZE = 32;
    public static final int CHAR_SCALE = 1;
    public static final int SCALE = 3;
    public static final int SCREEN_COL = 32;
    public static final int SCREEN_ROW = 24;
    public static final int SCREEN_WIDTH = TILESIZE * SCREEN_COL;
    public static final int SCREEN_HEIGHT = TILESIZE * SCREEN_ROW;
    public static final int FPS = 60;

    public static final int PLAYER_HEIGHT = 48;
    public static final int PLAYER_WIDTH = 32;
    public static final int PLAYER_TILE_SIZE = 64;

    // ===== LAN SERVER CONFIG =====
    public static final int SERVER_PORT = 11640; // LAN port
}
