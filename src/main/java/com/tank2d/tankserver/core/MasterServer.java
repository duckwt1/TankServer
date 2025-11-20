package com.tank2d.tankserver.core;

import com.tank2d.tankserver.ui.MasterServerDashboard.ServerEvent;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MasterServer {
    private final List<ClientHandler> clients = new ArrayList<>();
    private Consumer<ServerEvent> eventCallback;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private RendezvousServer rendezvousServer;

    public MasterServer() {
        // Default constructor for console mode
    }

    public MasterServer(Consumer<ServerEvent> eventCallback) {
        this.eventCallback = eventCallback;
    }

    public void start(int port) {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Master Server started on TCP port " + port);
            
            // Start Rendezvous server for UDP hole punching
            rendezvousServer = new RendezvousServer(port + 1000);
            rendezvousServer.start();
            System.out.println("Rendezvous Server started on UDP port " + (port + 1000));

            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    String clientIP = socket.getInetAddress().getHostAddress();
                    
                    ClientHandler handler = new ClientHandler(socket, this::notifyEvent);
                    clients.add(handler);
                    new Thread(handler).start();
                    
                    notifyEvent(new ServerEvent(ServerEvent.Type.CLIENT_CONNECTED, clientIP, ""));
                } catch (IOException e) {
                    if (running) {
                        notifyEvent(new ServerEvent(ServerEvent.Type.SERVER_ERROR, "", "Accept failed: " + e.getMessage()));
                    }
                }
            }

        } catch (IOException e) {
            notifyEvent(new ServerEvent(ServerEvent.Type.SERVER_ERROR, "", "Server start failed: " + e.getMessage()));
        }
    }

    public void stop() {
        running = false;
        
        // Stop Rendezvous server
        if (rendezvousServer != null) {
            rendezvousServer.stopServer();
        }
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            // Disconnect all clients
            for (ClientHandler client : clients) {
                client.disconnect();
            }
            clients.clear();
            
        } catch (IOException e) {
            System.out.println("Error stopping server: " + e.getMessage());
        }
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
    }

    private void notifyEvent(ServerEvent event) {
        if (eventCallback != null) {
            eventCallback.accept(event);
        }
        // Also print to console
        System.out.println("[SERVER] " + event.getType() + ": " + event.getMessage());
    }
}