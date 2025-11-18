package com.tank2d.masterserver.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.tank2d.masterserver.core.room.Room;
import com.tank2d.masterserver.core.room.RoomManager;
import com.tank2d.masterserver.core.shop.BuyResult;
import com.tank2d.masterserver.ui.MasterServerDashboard.ServerEvent;
import com.tank2d.shared.Packet;
import com.tank2d.shared.PacketType;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private final Consumer<ServerEvent> eventCallback;
    private final String clientIP;
    private Room currentRoom;

    public ClientHandler(Socket socket, Consumer<ServerEvent> eventCallback) {
        this.socket = socket;
        this.eventCallback = eventCallback;
        this.clientIP = socket.getInetAddress().getHostAddress();
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            System.out.println("Client connected: " + socket.getInetAddress());

            String line;
            while ((line = in.readLine()) != null) {
                try {
                    Packet p = Packet.fromJson(line);
                    handlePacket(p);
                } catch (Exception ex) {
                    System.out.println("Invalid packet: " + line);
                }
            }
        } catch (Exception e) {
            System.out.println("Client disconnected: " + (username != null ? username : clientIP));
            notifyEvent(new ServerEvent(ServerEvent.Type.CLIENT_DISCONNECTED, clientIP, username != null ? username : "Unknown"));

            if (currentRoom != null) {
                currentRoom.removePlayer(this);
                broadcastToRoom(currentRoom, PacketType.ROOM_UPDATE, "Player " + username + " left the room");
                if (currentRoom.getPlayers().isEmpty()) {
                    RoomManager.removeRoom(currentRoom.getId());
                }
            }
        } finally {
            disconnect();
        }
    }

    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing client socket: " + e.getMessage());
        }
    }

    private void handlePacket(Packet p) {
        switch (p.type) {
            case PacketType.LOGIN -> handleLogin(p);
            case PacketType.REGISTER -> handleRegister(p);
            case PacketType.CREATE_ROOM -> handleCreateRoom(p);
            case PacketType.JOIN_ROOM -> handleJoinRoom(p);
            case PacketType.LEAVE_ROOM -> handleLeaveRoom(p);
            case PacketType.PLAYER_READY -> handlePlayerReady(p);
            case PacketType.START_GAME -> handleStartGame(p);
            case PacketType.ROOM_LIST -> handleRoomList(p);
            case PacketType.SHOP_LIST -> handleShopList(p);
            case PacketType.BUY_ITEM -> handleBuyItem(p);
        }
    }

    private void handleLogin(Packet p) {
        username = (String) p.data.get("username");
        String password = (String) p.data.get("password");

        System.out.println("=== LOGIN ATTEMPT ===");
        System.out.println("Username: '" + username + "'");
        System.out.println("Password length: " + (password != null ? password.length() : "null"));

        boolean success = AccountManager.login(username, password);
        
        if (success) {
            System.out.println("✓ Login SUCCESS for: " + username);
            System.out.println("ClientHandler.username is now set to: " + this.username);
        } else {
            System.out.println("✗ Login FAILED for: " + username);
            this.username = null; // Clear username on failed login
        }
        
        Packet resp = new Packet(success ? PacketType.LOGIN_OK : PacketType.LOGIN_FAIL);
        resp.data.put("msg", success ? "Welcome " + username + "!" : "Invalid credentials!");
        send(resp);

        notifyEvent(new ServerEvent(ServerEvent.Type.CLIENT_LOGIN, clientIP, username + (success ? " (SUCCESS)" : " (FAILED)")));
    }

    private void handleRegister(Packet p) {
        String user = (String) p.data.get("username");
        String pass = (String) p.data.get("password");

        boolean ok = AccountManager.register(user, pass);
        Packet resp = new Packet(ok ? PacketType.REGISTER_OK : PacketType.REGISTER_FAIL);
        resp.data.put("msg", ok ? "Registered successfully!" : "Username already exists!");
        send(resp);

        notifyEvent(new ServerEvent(ServerEvent.Type.CLIENT_REGISTER, clientIP, user + (ok ? " (SUCCESS)" : " (FAILED)")));
    }

    private void handleRoomList(Packet p) {
        Packet resp = new Packet(PacketType.ROOM_LIST_DATA);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Room r : RoomManager.getRooms()) {
            Map<String, Object> roomInfo = new HashMap<>();
            roomInfo.put("id", Integer.valueOf(r.getId())); // ✅ ensure integer
            roomInfo.put("name", r.getName());
            roomInfo.put("players", Integer.valueOf(r.getPlayers().size())); // ✅ ensure integer
            roomInfo.put("maxPlayers", Integer.valueOf(r.getMaxPlayers()));  // ✅ ensure integer
            roomInfo.put("hasPassword", Boolean.valueOf(r.hasPassword()));   // ✅ ensure boolean, not string
            list.add(roomInfo);
        }

        resp.data.put("rooms", list);
        System.out.println("send back room list" + list.toString());
        send(resp);
    }

    private void handleCreateRoom(Packet p) {
        String roomName = (String) p.data.get("roomName");
        int maxPlayers = (int) p.data.get("maxPlayers");
        String password = (String) p.data.get("password");

        Room room = RoomManager.createRoom(roomName, this, maxPlayers, password);
        currentRoom = room;

        Packet resp = new Packet(PacketType.ROOM_CREATED);
        resp.data.put("roomId", room.getId());
        resp.data.put("roomName", room.getName());
        resp.data.put("maxPlayers", room.getMaxPlayers());
        resp.data.put("players", room.getPlayerNames());

        System.out.println("Room created: " + room.getName() + " by " + username);
        send(resp);
    }

    private void handleJoinRoom(Packet p) {
        int roomId = (int) p.data.get("roomId");
        String password = (String) p.data.get("password");

        Room room = RoomManager.getRoomById(roomId);
        if (room == null) {
            sendError("Room not found!");
            return;
        }
        if (room.isFull()) {
            sendError("Room is full!");
            return;
        }
        if (!room.checkPassword(password)) {
            sendError("Wrong password!");
            return;
        }

        room.addPlayer(this);
        currentRoom = room;

        Packet resp = new Packet(PacketType.ROOM_JOINED);
        resp.data.put("roomId", room.getId());
        resp.data.put("roomName", room.getName());
        resp.data.put("players", room.getPlayerNames());
        send(resp);

        broadcastToRoom(room, PacketType.ROOM_UPDATE, username + " joined the room");
    }

    private void handleLeaveRoom(Packet p) {
        if (currentRoom == null) return;
        Room room = currentRoom;
        room.removePlayer(this);
        broadcastToRoom(room, PacketType.ROOM_UPDATE, username + " left the room");
        currentRoom = null;
        if (room.getPlayers().isEmpty()) RoomManager.removeRoom(room.getId());
    }

    private void handlePlayerReady(Packet p) {
        if (currentRoom == null) return;
        boolean ready = (boolean) p.data.get("ready");
        broadcastToRoom(currentRoom, PacketType.ROOM_UPDATE, username + (ready ? " is ready!" : " is not ready"));
    }

    private void handleStartGame(Packet p) {
        if (currentRoom == null) return;

        if (!currentRoom.getHost().equals(this)) {
            sendError("Only host can start!");
            return;
        }

        System.out.println("Starting game in room: " + currentRoom.getName());

        int udpPort = 5000 + currentRoom.getId(); // unique per room
        String hostIp = socket.getInetAddress().getHostAddress();

        // Lấy thông tin map (ví dụ hardcode hoặc lưu trong room)
        //int mapId = currentRoom.getMapId(); // nếu chưa có, bạn có thể cho 1 giá trị mặc định như 1
        int mapId = 2;
        // Danh sách người chơi
        List<Map<String, Object>> playersData = new ArrayList<>();
        for (ClientHandler c : currentRoom.getPlayers()) {
            Map<String, Object> playerInfo = new HashMap<>();
            playerInfo.put("name", c.getUsername());
            playerInfo.put("tankId", 1); // bạn có thể cho mỗi người chọn tankId riêng
            playerInfo.put("gunId", 1);
            playersData.add(playerInfo);
        }

        // Gửi cho từng client
        for (ClientHandler client : currentRoom.getPlayers()) {
            Packet start = new Packet(PacketType.START_GAME);
            start.data.put("msg", "Game is starting!");
            start.data.put("host_ip", hostIp);
            start.data.put("host_udp_port", udpPort);
            start.data.put("isHost", currentRoom.getHost().getUsername());
            start.data.put("players", playersData);
            start.data.put("mapId", mapId);
            client.send(start);
        }

        System.out.println("Sent START_GAME to " + currentRoom.getPlayers().size() + " players.");
    }

    private void handleShopList(Packet p) {
        var items = ShopManager.getAllShopItems();
        
        Packet resp = new Packet(PacketType.SHOP_LIST_DATA);
        List<Map<String, Object>> itemsData = new ArrayList<>();
        
        for (var item : items) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("id", item.id);
            itemMap.put("name", item.name);
            itemMap.put("description", item.description);
            itemMap.put("price", item.price);
            itemMap.put("discount", item.discount);
            itemMap.put("stock", item.stock);
            // ✅ Gửi dynamic attributes
            itemMap.put("attributes", item.attributes);
            itemsData.add(itemMap);
        }
        
        resp.data.put("items", itemsData);
        
        // ✅ Gửi thêm gold của user
        if (username != null) {
            int userId = AccountManager.getUserIdByUsername(username);
            if (userId > 0) {
                int gold = AccountManager.getUserGold(userId);
                resp.data.put("gold", gold);
                System.out.println("Sent shop list with " + items.size() + " items and gold: " + gold + " to " + username);
            }
        }
        
        send(resp);
    }

    private void handleBuyItem(Packet p) {
        System.out.println("=== handleBuyItem called ===");
        System.out.println("Current username: " + username);
        
        if (username == null) {
            System.out.println("ERROR: username is NULL!");
            sendError("You must be logged in!");
            return;
        }
        
        // Lấy userId từ username
        System.out.println("Querying userId for username: '" + username + "'");
        int userId = AccountManager.getUserIdByUsername(username);
        System.out.println("Retrieved userId: " + userId);
        
        if (userId <= 0) {
            System.out.println("ERROR: User not found in database! userId=" + userId);
            sendError("User not found!");
            return;
        }
        
        int itemId = ((Number) p.data.get("itemId")).intValue();
        int quantity = ((Number) p.data.get("quantity")).intValue();
        
        System.out.println(username + " is buying item " + itemId + " x" + quantity);
        
        BuyResult result = ShopManager.buyItem(userId, itemId, quantity);
        
        Packet resp;
        if ("SUCCESS".equals(result.status)) {
            resp = new Packet(PacketType.BUY_SUCCESS);
            resp.data.put("gold", result.remainingGold);
            resp.data.put("msg", "Purchase successful!");
            System.out.println(username + " bought item successfully. Remaining gold: " + result.remainingGold);
        } else {
            resp = new Packet(PacketType.BUY_FAIL);
            resp.data.put("msg", result.status);
            System.out.println(username + " purchase failed: " + result.status);
        }
        send(resp);
    }

    private void broadcastToRoom(Room room, int type, String msg) {
        Packet p = new Packet(type);
        p.data.put("msg", msg);
        p.data.put("players", room.getPlayerNames());
        for (ClientHandler c : room.getPlayers()) c.send(p);
    }

    private void send(Packet p) {
        out.println(p.toJson());
    }

    private void sendError(String msg) {
        Packet err = new Packet(PacketType.LOGIN_FAIL);
        err.data.put("msg", msg);
        send(err);
    }

    private void notifyEvent(ServerEvent event) {
        if (eventCallback != null) eventCallback.accept(event);
    }

    public String getUsername() {
        return this.username;
    }
}
