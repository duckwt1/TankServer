package com.tank2d.tankserver.core;

import com.tank2d.tankserver.utils.Constant;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameRelayServer extends Thread {

    private final int port;
    private volatile boolean running = true;

    // roomId -> (username -> address)
    private final Map<Integer, Map<String, InetSocketAddress>> roomClients = new ConcurrentHashMap<>();

    // roomId -> (username -> state)
    private final Map<Integer, Map<String, String>> roomStates = new ConcurrentHashMap<>();

    public GameRelayServer(int port) {
        this.port = Constant.GAME_RELAY_PORT;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            socket.setSoTimeout(1);

            System.out.println("[GameRelay] Server started on UDP port " + port);

            // Thread 1: RECEIVE LOOP
            Thread receiver = new Thread(() -> receiveLoop(socket), "ReceiverThread");
            receiver.start();

            // Thread 2: BROADCAST LOOP (fixed 60 FPS)
            Thread broadcaster = new Thread(() -> broadcastLoop(socket), "BroadcastThread");
            broadcaster.start();

            receiver.join();
            broadcaster.join();

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("[GameRelay] Server stopped.");
    }

    // ============================================================
    // RECEIVE LOOP → NO DELAY
    // ============================================================
    private void receiveLoop(DatagramSocket socket) {
        byte[] buffer = new byte[4096];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength()).trim();
                InetSocketAddress addr = new InetSocketAddress(packet.getAddress(), packet.getPort());

                handlePacket(msg, addr);

            } catch (SocketTimeoutException ignored) {
                // no packet this tick
            } catch (Exception e) {
                System.err.println("[GameRelay] Receive error: " + e.getMessage());
            }
        }
    }

    // ============================================================
    // BROADCAST LOOP 60 FPS (16ms)
    // ============================================================
    private void broadcastLoop(DatagramSocket socket) {
        long interval = 16; // 60 FPS
        long last = System.currentTimeMillis();

        while (running) {
            long now = System.currentTimeMillis();

            if (now - last >= interval) {
                broadcastAllRooms(socket);
                last = now;
            }

            try { Thread.sleep(1); } catch (Exception ignored) {}
        }
    }

    private void broadcastAllRooms(DatagramSocket socket) {
        for (Integer roomId : roomStates.keySet()) {
            Map<String, String> states = roomStates.get(roomId);
            Map<String, InetSocketAddress> clients = roomClients.get(roomId);

            if (states == null || clients == null) continue;

            // Build STATE packet
            StringBuilder msg = new StringBuilder("STATE ");
            for (String state : states.values()) {
                msg.append(state).append("; ");
            }

            byte[] data = msg.toString().getBytes();

            // broadcast to everyone
            for (InetSocketAddress addr : clients.values()) {
                try {
                    DatagramPacket p = new DatagramPacket(data, data.length, addr);
                    socket.send(p);
                } catch (Exception e) {
                    System.err.println("[GameRelay] Broadcast error: " + e.getMessage());
                }
            }
        }
    }

    // ============================================================
    // PACKET HANDLING
    // ============================================================
    private void handlePacket(String msg, InetSocketAddress addr) {
        String[] parts = msg.split(" ");
        if (parts.length < 2) return;

        String cmd = parts[0];

        switch (cmd) {
            case "JOIN" -> onJoin(parts, addr);
            case "UPDATE" -> onUpdate(parts, addr);
            case "LEAVE" -> onLeave(parts);
        }
    }

    private void onJoin(String[] parts, InetSocketAddress addr) {
        int roomId = Integer.parseInt(parts[1]);
        String username = parts[2];

        roomClients.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(username, addr);
        roomStates.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());

        // ACK
        try {
            String ack = "JOINED " + roomId;
            DatagramPacket p = new DatagramPacket(ack.getBytes(), ack.length(), addr);
            new DatagramSocket().send(p);
        } catch (Exception ignored) {}
    }

    private void onUpdate(String[] parts, InetSocketAddress addr) {
        int roomId = Integer.parseInt(parts[1]);
        String username = parts[2];

        // store address
        roomClients.get(roomId).put(username, addr);

        // store state
// include ALL fields after username
        String state = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
        roomStates.get(roomId).put(username, state);
    }

    private void onLeave(String[] parts) {
        int roomId = Integer.parseInt(parts[1]);
        String username = parts[2];

        Map<String, InetSocketAddress> clients = roomClients.get(roomId);
        Map<String, String> states = roomStates.get(roomId);

        if (clients != null) clients.remove(username);
        if (states != null) states.remove(username);
    }

    public void stopServer() {
        running = false;
    }
}
