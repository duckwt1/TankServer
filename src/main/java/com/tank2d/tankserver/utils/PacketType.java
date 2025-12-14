package com.tank2d.tankserver.utils;

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
    public static final int SELECT_MAP = 23;
    public static final int MAP_SELECTED = 24;

    // Shop
    public static final int SHOP_LIST = 18;
    public static final int SHOP_LIST_DATA = 19;
    public static final int BUY_ITEM = 20;
    public static final int BUY_SUCCESS = 21;
    public static final int BUY_FAIL = 22;
    
    // Tank Shop
    public static final int TANK_SHOP_LIST = 27;
    public static final int TANK_SHOP_LIST_DATA = 28;
    public static final int BUY_TANK = 29;
    public static final int EQUIP_TANK = 30;
    public static final int EQUIP_TANK_SUCCESS = 31;
    public static final int EQUIP_TANK_FAIL = 32;
    
    // Inventory
    public static final int INVENTORY_REQUEST = 25;
    public static final int INVENTORY_DATA = 26;
}
