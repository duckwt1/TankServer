package com.tank2d.tankserver.utils;

public class Constant {
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
    
    // Server Configuration (Playit.gg)
    public static final String SERVER_HOST = "restaurants-expenditures.gl.at.ply.gg";
    public static final int SERVER_PORT = 11640;
    
    // Game Relay Server (UDP tunnel)
    public static final String GAME_RELAY_HOST = "takes-concerts.gl.at.ply.gg";
    public static final int GAME_RELAY_PORT = 4972;
}
