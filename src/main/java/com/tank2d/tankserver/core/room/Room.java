package com.tank2d.tankserver.core.room;

import com.tank2d.tankserver.core.ClientHandler;
import java.util.ArrayList;
import java.util.List;

public class Room {
    private int id;
    private String name;
    private ClientHandler host;
    private List<ClientHandler> players = new ArrayList<>();
    private int maxPlayers;
    private String password;
    private String selectedMap = "map1"; // Default map
    private int botCount = 0; // Number of AI bots

    public Room(int id, String name, ClientHandler host, int maxPlayers, String password) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.maxPlayers = maxPlayers;
        this.password = (password != null && !password.isEmpty()) ? password : null;
        this.players.add(host);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public ClientHandler getHost() { return host; }
    public List<ClientHandler> getPlayers() { return players; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getPlayerCount() { return players.size(); }
    public boolean hasPassword() { return password != null && !password.isEmpty(); }
    public boolean isFull() { return players.size() >= maxPlayers; }
    
    public String getSelectedMap() { return selectedMap; }
    public void setSelectedMap(String selectedMap) { this.selectedMap = selectedMap; }
    
    public int getBotCount() { return botCount; }
    public void setBotCount(int botCount) { this.botCount = botCount; }
    
    public boolean checkPassword(String inputPassword) {
        if (!hasPassword()) return true;
        return password.equals(inputPassword);
    }
    
    public boolean addPlayer(ClientHandler player) {
        if (isFull()) return false;
        if (!players.contains(player)) {
            players.add(player);
            return true;
        }
        return false;
    }
    
    public boolean removePlayer(ClientHandler player) {
        return players.remove(player);
    }
    
    public List<String> getPlayerNames() {
        List<String> names = new ArrayList<>();
        for (ClientHandler player : players) {
            String playerName = player.getUsername();
            if (player == host) {
                playerName += " (Host)";
            }
            names.add(playerName);
        }
        return names;
    }

}
