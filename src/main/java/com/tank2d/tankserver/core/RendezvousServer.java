package com.tank2d.tankserver.core;

import com.tank2d.tankserver.core.room.Room;
import com.tank2d.tankserver.core.room.RoomManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP Hole Punching Rendezvous Server
 * Helps clients discover each other's public IP:Port for P2P connection
 */
public class RendezvousServer extends Thread {
    private final int port;
    private volatile boolean running = true;
    private DatagramSocket socket;
    
    // Map: roomId -> Map<username, address>
    private final Map<Integer, Map<String, InetSocketAddress>> roomClients = new ConcurrentHashMap<>();

    public RendezvousServer(int port) {
        this.port = port;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            System.out.println("[RendezvousServer] Started on UDP port " + port);

            byte[] buffer = new byte[1024];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength()).trim();
                InetSocketAddress clientAddr = new InetSocketAddress(
                    packet.getAddress(), 
                    packet.getPort()
                );

                handleRegistration(msg, clientAddr);
            }

        } catch (Exception e) {
            if (running) {
                System.err.println("[RendezvousServer] Error: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private void handleRegistration(String msg, InetSocketAddress clientAddr) {
        try {
            // Expected format: "REGISTER roomId username"
            String[] parts = msg.split(" ");
            
            if (parts.length == 3 && parts[0].equals("REGISTER")) {
                int roomId = Integer.parseInt(parts[1]);
                String username = parts[2];
                
                // Get or create room client map
                Map<String, InetSocketAddress> clients = roomClients.computeIfAbsent(
                    roomId, 
                    k -> new ConcurrentHashMap<>()
                );
                
                // Register this client
                clients.put(username, clientAddr);
                System.out.println("[Rendezvous] Registered: " + username + 
                                 " in room " + roomId + " -> " + clientAddr);
                
                // Send back all other clients in the room
                StringBuilder response = new StringBuilder("PEERS ");
                for (Map.Entry<String, InetSocketAddress> entry : clients.entrySet()) {
                    if (!entry.getKey().equals(username)) {
                        response.append(entry.getKey()).append(":")
                               .append(entry.getValue().getAddress().getHostAddress()).append(":")
                               .append(entry.getValue().getPort()).append(";");
                    }
                }
                
                byte[] responseData = response.toString().getBytes();
                DatagramPacket responsePacket = new DatagramPacket(
                    responseData, 
                    responseData.length, 
                    clientAddr
                );
                socket.send(responsePacket);
                
                System.out.println("[Rendezvous] Sent peers to " + username + ": " + response);
                
            } else if (parts.length == 3 && parts[0].equals("UNREGISTER")) {
                int roomId = Integer.parseInt(parts[1]);
                String username = parts[2];
                
                Map<String, InetSocketAddress> clients = roomClients.get(roomId);
                if (clients != null) {
                    clients.remove(username);
                    if (clients.isEmpty()) {
                        roomClients.remove(roomId);
                    }
                    System.out.println("[Rendezvous] Unregistered: " + username + " from room " + roomId);
                }
            }
            
        } catch (Exception e) {
            System.err.println("[Rendezvous] Error handling message: " + msg);
            e.printStackTrace();
        }
    }

    public void stopServer() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
