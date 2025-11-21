package com.tank2d.tankserver.core;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP Relay Server - forwards game state packets between all players in a room
 */
public class GameRelayServer extends Thread {
    private final int port;
    private boolean running = true;
    
    // roomId -> (username -> client address)
    private final Map<Integer, Map<String, InetSocketAddress>> roomClients = new ConcurrentHashMap<>();
    
    // roomId -> (username -> latest player state)
    private final Map<Integer, Map<String, String>> roomStates = new ConcurrentHashMap<>();

    public GameRelayServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(100);
            byte[] buffer = new byte[4096];
            
            System.out.println("========================================");
            System.out.println("[GameRelay] UDP Server STARTED");
            System.out.println("[GameRelay] Port: " + port);
            System.out.println("[GameRelay] Local address: " + socket.getLocalSocketAddress());
            System.out.println("[GameRelay] Waiting for packets...");
            System.out.println("========================================");

            int packetCount = 0;
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    packetCount++;
                    
                    String msg = new String(packet.getData(), 0, packet.getLength()).trim();
                    InetSocketAddress clientAddr = new InetSocketAddress(
                        packet.getAddress(), 
                        packet.getPort()
                    );
                    
                    if (packetCount <= 10 || packetCount % 100 == 0) {
                        System.out.println("[GameRelay] Packet #" + packetCount + " from " + clientAddr + ": " + 
                            msg.substring(0, Math.min(30, msg.length())));
                    }

                    handlePacket(msg, clientAddr, socket);
                    
                } catch (java.net.SocketTimeoutException ignored) {
                    // Normal timeout, continue
                } catch (Exception e) {
                    System.err.println("[GameRelay] Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
        } catch (Exception e) {
            System.err.println("[GameRelay] Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
        
        System.out.println("[GameRelay] Stopped");
    }

    private void handlePacket(String msg, InetSocketAddress clientAddr, DatagramSocket socket) {
        String[] parts = msg.split(" ");
        
        if (parts.length < 2) return;
        
        String command = parts[0];
        
        switch (command) {
            case "JOIN" -> handleJoin(parts, clientAddr, socket);
            case "UPDATE" -> handleUpdate(parts, clientAddr, socket);
            case "LEAVE" -> handleLeave(parts, clientAddr);
        }
    }

    /**
     * JOIN roomId username
     */
    private void handleJoin(String[] parts, InetSocketAddress clientAddr, DatagramSocket socket) {
        if (parts.length != 3) return;
        
        int roomId = Integer.parseInt(parts[1]);
        String username = parts[2];
        
        roomClients.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                   .put(username, clientAddr);
        roomStates.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
        
        System.out.println("[GameRelay] JOIN: " + username + " -> Room " + roomId + 
                          " (" + roomClients.get(roomId).size() + " players)");
        
        // Send ACK
        try {
            String ack = "JOINED " + roomId;
            byte[] data = ack.getBytes();
            socket.send(new DatagramPacket(data, data.length, clientAddr));
        } catch (Exception e) {
            System.err.println("[GameRelay] Failed to send JOIN ACK: " + e.getMessage());
        }
    }

    /**
     * UPDATE roomId username x y bodyAngle gunAngle up down left right backward
     */
    private void handleUpdate(String[] parts, InetSocketAddress clientAddr, DatagramSocket socket) {
        if (parts.length != 12) {
            System.err.println("[GameRelay] Invalid UPDATE packet (expected 12 parts, got " + parts.length + ")");
            return;
        }
        
        int roomId = Integer.parseInt(parts[1]);
        String username = parts[2];
        
        // Update client address (in case port changed)
        Map<String, InetSocketAddress> clients = roomClients.get(roomId);
        if (clients == null) {
            System.err.println("[GameRelay] Room " + roomId + " not found for UPDATE from " + username);
            return;
        }
        
        clients.put(username, clientAddr);
        
        // Store player state: "username x y bodyAngle gunAngle up down left right backward"
        String playerState = String.join(" ", Arrays.copyOfRange(parts, 2, 12));
        roomStates.get(roomId).put(username, playerState);
        
        // Build STATE message with all players in room
        StringBuilder stateMsg = new StringBuilder("STATE ");
        for (String state : roomStates.get(roomId).values()) {
            stateMsg.append(state).append("; ");
        }
        
        // Broadcast to all players in room
        byte[] data = stateMsg.toString().getBytes();
        int sent = 0;
        for (Map.Entry<String, InetSocketAddress> entry : clients.entrySet()) {
            try {
                socket.send(new DatagramPacket(data, data.length, entry.getValue()));
                sent++;
            } catch (Exception e) {
                System.err.println("[GameRelay] Failed to send to " + entry.getKey() + ": " + e.getMessage());
            }
        }
        
        //System.out.println("[GameRelay] Room " + roomId + ": broadcasted state to " + sent + " clients");
    }

    /**
     * LEAVE roomId username
     */
    private void handleLeave(String[] parts, InetSocketAddress clientAddr) {
        if (parts.length != 3) return;
        
        int roomId = Integer.parseInt(parts[1]);
        String username = parts[2];
        
        Map<String, InetSocketAddress> clients = roomClients.get(roomId);
        if (clients != null) {
            clients.remove(username);
            System.out.println("[GameRelay] LEAVE: " + username + " from Room " + roomId);
            
            if (clients.isEmpty()) {
                roomClients.remove(roomId);
                roomStates.remove(roomId);
                System.out.println("[GameRelay] Room " + roomId + " is now empty, cleaned up");
            }
        }
        
        Map<String, String> states = roomStates.get(roomId);
        if (states != null) {
            states.remove(username);
        }
    }

    public void stopServer() {
        running = false;
    }
}
