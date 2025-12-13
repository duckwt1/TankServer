package com.tank2d.tankserver.core;

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

import com.tank2d.tankserver.core.room.Room;
import com.tank2d.tankserver.core.room.RoomManager;
import com.tank2d.tankserver.core.shop.BuyResult;
import com.tank2d.tankserver.db.InventoryRepository;
import com.tank2d.tankserver.ui.MasterServerDashboard.ServerEvent;
import com.tank2d.tankserver.utils.Packet;
import com.tank2d.tankserver.utils.PacketType;

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
            case PacketType.SELECT_MAP -> handleSelectMap(p);
            case PacketType.ROOM_LIST -> handleRoomList(p);
            case PacketType.SHOP_LIST -> handleShopList(p);
            case PacketType.BUY_ITEM -> handleBuyItem(p);
            case PacketType.TANK_SHOP_LIST -> handleTankShopList(p);
            case PacketType.BUY_TANK -> handleBuyTank(p);
            case PacketType.EQUIP_TANK -> handleEquipTank(p);
            case PacketType.INVENTORY_REQUEST -> handleInventoryRequest(p);
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
        resp.data.put("maxPlayers", room.getMaxPlayers());
        resp.data.put("players", room.getPlayerNames());
        resp.data.put("selectedMap", room.getSelectedMap()); // Send current selected map
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
    
    private void handleSelectMap(Packet p) {
        if (currentRoom == null) {
            sendError("You are not in a room!");
            return;
        }
        
        if (!currentRoom.getHost().equals(this)) {
            sendError("Only host can select map!");
            return;
        }
        
        String selectedMap = (String) p.data.get("map");
        if (selectedMap == null || selectedMap.isEmpty()) {
            selectedMap = "map1";
        }
        
        // Validate map name
        if (!selectedMap.matches("map[1-3]")) {
            sendError("Invalid map selection!");
            return;
        }
        
        currentRoom.setSelectedMap(selectedMap);
        System.out.println("Host " + username + " selected map: " + selectedMap);
        
        // Broadcast to all players in room
        Packet resp = new Packet(PacketType.MAP_SELECTED);
        resp.data.put("map", selectedMap);
        
        for (ClientHandler client : currentRoom.getPlayers()) {
            client.send(resp);
        }
    }

    private void handleStartGame(Packet p) {
        if (currentRoom == null) return;

        if (!currentRoom.getHost().equals(this)) {
            sendError("Only host can start!");
            return;
        }

        System.out.println("Starting game in room: " + currentRoom.getName());

        // Use relay server instead of P2P
        String relayHost = com.tank2d.tankserver.utils.Constant.GAME_RELAY_HOST;
        int relayPort = com.tank2d.tankserver.utils.Constant.GAME_RELAY_PORT;
        int roomId = currentRoom.getId();
        
        System.out.println("[START_GAME] Sending relay info: " + relayHost + ":" + relayPort + " (Room " + roomId + ")");

        // Map configuration - use selected map from room
        String selectedMap = currentRoom.getSelectedMap();
        int mapId = switch (selectedMap) {
            case "map1" -> 1;
            case "map2" -> 2;
            case "map3" -> 3;
            default -> 1;
        };
        
        // Player list
        List<Map<String, Object>> playersData = new ArrayList<>();
        for (ClientHandler c : currentRoom.getPlayers()) {
            Map<String, Object> playerInfo = new HashMap<>();
            playerInfo.put("name", c.getUsername());
            playerInfo.put("tankId", 1);
            playerInfo.put("gunId", 1);
            playersData.add(playerInfo);
        }

        // Send to all clients
        for (ClientHandler client : currentRoom.getPlayers()) {
            Packet start = new Packet(PacketType.START_GAME);
            start.data.put("msg", "Game is starting!");
            start.data.put("relay_host", relayHost);
            start.data.put("relay_port", relayPort);
            start.data.put("room_id", roomId);
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
    
    private void handleInventoryRequest(Packet p) {
        if (username == null) {
            sendError("You must be logged in!");
            return;
        }
        
        int userId = AccountManager.getUserIdByUsername(username);
        if (userId <= 0) {
            sendError("User not found!");
            return;
        }
        
        // Get user's tanks from tank table
        var userTanks = com.tank2d.tankserver.db.TankRepository.getUserTanks(userId);
        
        // Get user's items from item table
        var inventoryItems = InventoryRepository.getUserInventory(userId);
        
        // Get user's gold
        int gold = AccountManager.getUserGold(userId);
        
        Packet resp = new Packet(PacketType.INVENTORY_DATA);
        resp.data.put("tanks", userTanks);
        resp.data.put("items", inventoryItems);
        resp.data.put("gold", gold);
        
        System.out.println("Sent inventory: " + userTanks.size() + " tanks, " + inventoryItems.size() + " items to " + username);
        send(resp);
    }

    private void broadcastToRoom(Room room, int type, String msg) {
        Packet p = new Packet(type);
        p.data.put("msg", msg);
        p.data.put("players", room.getPlayerNames());
        p.data.put("maxPlayers", room.getMaxPlayers());
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
    
    private void handleTankShopList(Packet p) {
        var tanks = TankShopManager.getAllTanks();
        
        // Convert to serializable format
        List<Map<String, Object>> tankList = new ArrayList<>();
        for (var tank : tanks) {
            Map<String, Object> t = new HashMap<>();
            t.put("id", tank.id);
            t.put("name", tank.name);
            t.put("description", tank.description);
            t.put("price", tank.price);
            t.put("discount", tank.discount);
            t.put("stock", tank.stock);
            t.put("attributes", tank.attributes);
            tankList.add(t);
        }
        
        int gold = AccountManager.getUserGold(AccountManager.getUserIdByUsername(username));
        
        Packet resp = new Packet(PacketType.TANK_SHOP_LIST_DATA);
        resp.data.put("tanks", tankList);
        resp.data.put("gold", gold);
        send(resp);
    }
    
    private void handleBuyTank(Packet p) {
        int tankId = (int) p.data.get("tankId");
        int userId = AccountManager.getUserIdByUsername(username);
        
        BuyResult result = TankShopManager.buyTank(userId, tankId);
        
        if (result.status.equals("SUCCESS")) {
            Packet resp = new Packet(PacketType.BUY_SUCCESS);
            resp.data.put("msg", "Tank purchased successfully!");
            resp.data.put("remainingGold", result.remainingGold);
            send(resp);
        } else {
            Packet resp = new Packet(PacketType.BUY_FAIL);
            resp.data.put("msg", result.status);
            send(resp);
        }
    }
    
    private void handleEquipTank(Packet p) {
        int tankId = (int) p.data.get("tankId");
        int userId = AccountManager.getUserIdByUsername(username);
        
        boolean success = TankShopManager.equipTank(userId, tankId);
        
        if (success) {
            Packet resp = new Packet(PacketType.EQUIP_TANK_SUCCESS);
            resp.data.put("tankId", tankId);
            send(resp);
        } else {
            Packet resp = new Packet(PacketType.EQUIP_TANK_FAIL);
            resp.data.put("msg", "Failed to equip tank");
            send(resp);
        }
    }
}
