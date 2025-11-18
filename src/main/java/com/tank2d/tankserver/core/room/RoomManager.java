package com.tank2d.masterserver.core.room;

import com.tank2d.masterserver.core.ClientHandler;
import com.tank2d.masterserver.ui.MasterServerDashboard;

import java.util.*;

/**
 * RoomManager
 * Quản lý toàn bộ phòng (Room) trên server:
 *  - Tạo, xóa, lấy danh sách
 *  - Broadcast cập nhật danh sách phòng cho client và Dashboard
 */
public class RoomManager {

    private static final Map<Integer, Room> rooms = new HashMap<>();
    private static int nextId = 1;

    /** Liên kết UI dashboard để cập nhật danh sách phòng */
    public static MasterServerDashboard dashboard;

    // -------------------------------
    // 🔹 CREATE / REMOVE / GET
    // -------------------------------
    public static synchronized Room createRoom(String name, ClientHandler host, int maxPlayers, String password) {
        Room room = new Room(nextId++, name, host, maxPlayers, password);
        rooms.put(room.getId(), room);

        broadcastRoomList(); // cập nhật UI và client
        return room;
    }

    public static synchronized void removeRoom(int id) {
        rooms.remove(id);
        broadcastRoomList();
    }

    public static synchronized Room getRoomById(int id) {
        return rooms.get(id);
    }

    public static synchronized Room getRoomByName(String name) {
        for (Room r : rooms.values()) {
            if (r.getName().equalsIgnoreCase(name)) return r;
        }
        return null;
    }

    public static synchronized Collection<Room> getRooms() {
        return new ArrayList<>(rooms.values());
    }

    public static synchronized void removeEmptyRooms() {
        rooms.entrySet().removeIf(e -> e.getValue().getPlayers().isEmpty());
        broadcastRoomList();
    }

    public static int getRoomCount() {
        return rooms.size();
    }

    // -------------------------------
    // 🔹 DASHBOARD + CLIENT UPDATES
    // -------------------------------

    /**
     * Tạo danh sách phòng gửi về client hoặc dashboard
     */
    private static List<Map<String, Object>> generateRoomList() {
        List<Map<String, Object>> list = new ArrayList<>();

        for (Room r : rooms.values()) {
            Map<String, Object> info = new HashMap<>();
            info.put("id", r.getId());
            info.put("name", r.getName());
            info.put("host", (r.getHost() != null && r.getHost() != null)
                    ? r.getHost()
                    : "Unknown");
            info.put("players", r.getPlayers().size());
            info.put("maxPlayers", r.getMaxPlayers());
            info.put("hasPassword", r.hasPassword());
            info.put("status", r.isFull() ? "Full" : "Open");
            list.add(info);
        }

        return list;
    }

    /**
     * Gửi danh sách phòng cho Dashboard + Client
     */
    public static synchronized void broadcastRoomList() {
        List<Map<String, Object>> roomData = generateRoomList();

        // 🔹 Cập nhật UI Dashboard
        if (dashboard != null) {
            dashboard.onServerEvent(
                    new MasterServerDashboard.ServerEvent(
                            MasterServerDashboard.ServerEvent.Type.ROOM_UPDATED,
                            "SERVER",
                            "Room list updated",
                            roomData
                    )
            );
        }

        // 🔹 Có thể thêm phần gửi danh sách phòng cho tất cả client sau này (ROOM_LIST_DATA)
        // Ví dụ:
        // ConnectedClients.broadcast(packet);
    }

    // -------------------------------
    // 🔹 DEBUG / LOGGING
    // -------------------------------

    public static void printRooms() {
        System.out.println("===== ROOM LIST =====");
        if (rooms.isEmpty()) {
            System.out.println("No active rooms.");
        } else {
            for (Room r : rooms.values()) {
                System.out.printf("ID: %d | Name: %s | Players: %d/%d | Locked: %s\n",
                        r.getId(), r.getName(), r.getPlayers().size(),
                        r.getMaxPlayers(), r.hasPassword());
            }
        }
    }
}
