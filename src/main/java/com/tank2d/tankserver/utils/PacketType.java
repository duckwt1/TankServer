package com.tank2d.shared;

public class PacketType {
    public static final int LOGIN = 1;
    public static final int LOGIN_OK = 2;
    public static final int LOGIN_FAIL = 3;

    public static final int REGISTER = 4;
    public static final int REGISTER_OK = 5;
    public static final int REGISTER_FAIL = 6;

    public static final int CREATE_ROOM = 7;
    public static final int ROOM_CREATED = 8;
    public static final int ROOM_LIST = 9;
    public static final int ROOM_LIST_DATA = 10;
    public static final int JOIN_ROOM = 11;
    public static final int ROOM_JOINED = 12;
    public static final int ROOM_JOIN_FAIL = 13;
    public static final int LEAVE_ROOM = 14;
    public static final int ROOM_UPDATE = 15;
    public static final int PLAYER_READY = 16;
    public static final int START_GAME = 17;

    // Shop
    public static final int SHOP_LIST = 18;
    public static final int SHOP_LIST_DATA = 19;
    public static final int BUY_ITEM = 20;
    public static final int BUY_SUCCESS = 21;
    public static final int BUY_FAIL = 22;
}
