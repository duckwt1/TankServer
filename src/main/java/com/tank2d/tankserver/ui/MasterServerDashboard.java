package com.tank2d.tankserver.ui;

import com.tank2d.tankserver.core.MasterServer;
import com.tank2d.tankserver.core.room.Room;
import com.tank2d.tankserver.core.room.RoomManager;
import com.tank2d.tankserver.db.AccountRepository;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tank2D Master Server Dashboard Controller
 * (Manages server lifecycle, clients, rooms, and logs)
 */
public class MasterServerDashboard implements Initializable {

    // ================== FXML COMPONENTS ==================
    @FXML private Button btnStartStop;
    @FXML private Label lblServerStatus, lblPort, lblTotalClients, lblTotalUsers, lblTotalRooms, lblUptime;
    @FXML private TextField txtPort;
    
    // Client Table
    @FXML private TableView<ClientInfo> tblClients;
    @FXML private TableColumn<ClientInfo, String> colClientIP, colUsername, colConnectTime;
    
    // Room Table
    @FXML private TableView<RoomInfo> tblRooms;
    @FXML private TableColumn<RoomInfo, String> colRoomName, colRoomPlayers, colRoomStatus;
    
    // User Management
    @FXML private TextField txtSearchUser;
    @FXML private TableView<UserInfo> tblUsers;
    @FXML private TableColumn<UserInfo, String> colUserUsername;
    @FXML private TableColumn<UserInfo, Integer> colUserLevel;
    @FXML private TableColumn<UserInfo, Integer> colUserGold;
    @FXML private TableColumn<UserInfo, String> colUserStatus;
    
    @FXML private TextArea txtLog;

    // ================== PANELS ==================
    @FXML private  HBox paneDashboard;
    @FXML private VBox paneUsers;
    @FXML private VBox paneRooms;
    @FXML private HBox paneTanks;
    @FXML private VBox paneItems;
    @FXML private Label lblCurrentPage;

    // ================== INTERNAL STATE ==================
    private MasterServer server;
    private boolean serverRunning = false;
    private LocalDateTime startTime;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final AccountRepository accountRepo = new AccountRepository();

    private final ObservableList<ClientInfo> clientList = FXCollections.observableArrayList();
    private final ObservableList<RoomInfo> roomList = FXCollections.observableArrayList();
    private final ObservableList<UserInfo> userList = FXCollections.observableArrayList();

    // =====================================================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUI();
        setupClientTable();
        setupRoomTable();
        setupUserTable();
        addLog("Dashboard initialized");
        updateTotalUsersCount();
    }

    // -------------------- SETUP --------------------

    private void setupUI() {
        txtPort.setText("11640");
        lblServerStatus.setText("Stopped");
        lblServerStatus.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        lblPort.setText("N/A");
        lblTotalClients.setText("0");
        lblTotalUsers.setText("0");
        lblTotalRooms.setText("0");
        lblUptime.setText("Uptime: 00:00:00");
        btnStartStop.setText("Start Server");
    }

    private void setupClientTable() {
        colClientIP.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colConnectTime.setCellValueFactory(new PropertyValueFactory<>("connectTime"));
        tblClients.setItems(clientList);
    }

    private void setupRoomTable() {
        colRoomName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colRoomPlayers.setCellValueFactory(new PropertyValueFactory<>("playerCount"));
        colRoomStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        tblRooms.setItems(roomList);
    }

    private void setupUserTable() {
        colUserUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colUserLevel.setCellValueFactory(new PropertyValueFactory<>("level"));
        colUserGold.setCellValueFactory(new PropertyValueFactory<>("gold"));
        colUserStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        tblUsers.setItems(userList);
        
        // Search listener
        if (txtSearchUser != null) {
            txtSearchUser.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == null || newVal.trim().isEmpty()) {
                    loadAllUsers();
                } else {
                    searchUsers(newVal);
                }
            });
        }
    }
    
    private void updateTotalUsersCount() {
        new Thread(() -> {
            int total = accountRepo.getTotalUsers();
            Platform.runLater(() -> lblTotalUsers.setText(String.valueOf(total)));
        }).start();
    }

    // -------------------- SERVER CONTROL --------------------

    @FXML
    private void onStartStopClick() {
        if (!serverRunning) startServer();
        else stopServer();
    }

    private void startServer() {
        try {
            int port = Integer.parseInt(txtPort.getText());
            server = new MasterServer(this::onServerEvent);
            RoomManager.dashboard = this; // connect UI <-> server logic

            new Thread(() -> {
                try {
                    server.start(port);
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        addLog("Error starting server: " + e.getMessage());
                        onServerStopped();
                    });
                }
            }).start();

            startTime = LocalDateTime.now();
            serverRunning = true;

            Platform.runLater(() -> {
                btnStartStop.setText("Stop Server");
                lblServerStatus.setText("Running");
                lblServerStatus.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                lblPort.setText(String.valueOf(port));
                txtPort.setDisable(true);
                addLog("Server started on port " + port);
            });

            // Start uptime timer
            new Thread(this::updateUptimeLoop).start();

        } catch (NumberFormatException e) {
            addLog("Invalid port number!");
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            serverRunning = false;
            onServerStopped();
            addLog("Server stopped");
        }
    }

    private void onServerStopped() {
        Platform.runLater(() -> {
            btnStartStop.setText("Start Server");
            lblServerStatus.setText("Stopped");
            lblServerStatus.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            lblPort.setText("N/A");
            txtPort.setDisable(false);
            clientList.clear();
            roomList.clear();
            lblTotalClients.setText("0");
            lblTotalRooms.setText("0");
        });
    }

    // -------------------- EVENT HANDLING --------------------

    public void onServerEvent(ServerEvent event) {
        Platform.runLater(() -> {
            switch (event.getType()) {
                case CLIENT_CONNECTED -> {
                    clientList.add(new ClientInfo(
                            event.getClientIP(),
                            "Unknown",
                            LocalDateTime.now().format(timeFormatter)
                    ));
                    lblTotalClients.setText(String.valueOf(clientList.size()));
                    addLog("Client connected: " + event.getClientIP());
                }
                case CLIENT_DISCONNECTED -> {
                    clientList.removeIf(c -> c.getIpAddress().equals(event.getClientIP()));
                    lblTotalClients.setText(String.valueOf(clientList.size()));
                    addLog("Client disconnected: " + event.getClientIP());
                }
                case CLIENT_LOGIN -> {
                    for (ClientInfo c : clientList) {
                        if (c.getIpAddress().equals(event.getClientIP())) {
                            c.setUsername(event.getMessage());
                            tblClients.refresh();
                            break;
                        }
                    }
                    addLog("Login: " + event.getMessage() + " from " + event.getClientIP());
                }
                case CLIENT_REGISTER -> addLog("Register: " + event.getMessage());
                case ROOM_UPDATED -> updateRoomTable(event.getRooms());
                case SERVER_ERROR -> addLog("ERROR: " + event.getMessage());
            }
        });
    }

    /** Called from RoomManager.broadcastRoomList() */
    public void updateRoomTable(List<Map<String, Object>> rooms) {
        Platform.runLater(() -> {
            roomList.clear();
            for (Map<String, Object> data : rooms) {
                roomList.add(new RoomInfo(
                        (String) data.get("name"),
                        data.get("players") + "/" + data.get("maxPlayers"),
                        (boolean) data.getOrDefault("hasPassword", false) ? "Locked" : "Open"
                ));
            }
            lblTotalRooms.setText(String.valueOf(roomList.size()));
        });
    }

    @FXML
    private void onCloseRoom() {
        RoomInfo selected = tblRooms.getSelectionModel().getSelectedItem();
        if (selected == null) {
            addLog("⚠ Please select a room to close!");
            return;
        }

        Room room = RoomManager.getRoomByName(selected.getName());
        if (room != null) {
            RoomManager.removeRoom(room.getId());
            addLog("❌ Room closed manually: " + selected.getName());
            RoomManager.broadcastRoomList();
        } else {
            addLog("⚠ Room not found: " + selected.getName());
        }
    }

    @FXML
    private void showDashboard(){
        hideAllPanels();
        paneDashboard.setVisible(true);
        lblCurrentPage.setText("SERVER DASHBOARD");
    }

    @FXML
    private void showUserManagement(){
        hideAllPanels();
        paneUsers.setVisible(true);
        lblCurrentPage.setText("QUẢN LÝ USER");
        loadAllUsers();
    }

    @FXML
    private void showRoomManagement(){
        hideAllPanels();
        paneRooms.setVisible(true);
        lblCurrentPage.setText("QUẢN LÝ ROOM");
    }

    @FXML
    private void showTankManagement(){
        hideAllPanels();
        paneTanks.setVisible(true);
        lblCurrentPage.setText("QUẢN LÝ TANK");
    }

    @FXML
    private void showItemManagement(){
        hideAllPanels();
        paneItems.setVisible(true);
        lblCurrentPage.setText("QUẢN LÝ ITEMS");
    }
    
    private void hideAllPanels() {
        if (paneDashboard != null) paneDashboard.setVisible(false);
        if (paneUsers != null) paneUsers.setVisible(false);
        if (paneRooms != null) paneRooms.setVisible(false);
        if (paneTanks != null) paneTanks.setVisible(false);
        if (paneItems != null) paneItems.setVisible(false);
    }


    // -------------------- USER MANAGEMENT --------------
    
    private void loadAllUsers() {
        new Thread(() -> {
            List<Map<String, Object>> users = accountRepo.getAllUsers();
            Platform.runLater(() -> {
                userList.clear();
                for (Map<String, Object> userData : users) {
                    userList.add(new UserInfo(
                        (int) userData.get("id"),
                        (String) userData.get("username"),
                        (int) userData.get("level"),
                        (int) userData.get("gold"),
                        (boolean) userData.get("isBanned") ? "Banned" : "Active"
                    ));
                }
                addLog("Loaded " + userList.size() + " users");
            });
        }).start();
    }
    
    private void searchUsers(String searchTerm) {
        new Thread(() -> {
            List<Map<String, Object>> users = accountRepo.searchUsers(searchTerm);
            Platform.runLater(() -> {
                userList.clear();
                for (Map<String, Object> userData : users) {
                    userList.add(new UserInfo(
                        (int) userData.get("id"),
                        (String) userData.get("username"),
                        (int) userData.get("level"),
                        (int) userData.get("gold"),
                        (boolean) userData.get("isBanned") ? "Banned" : "Active"
                    ));
                }
            });
        }).start();
    }
    
    @FXML
    private void onBanUser() {
        UserInfo selected = tblUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Warning", "Please select a user to ban!");
            return;
        }
        
        if (selected.getStatus().equals("Banned")) {
            showAlert("Warning", "User is already banned!");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Ban User");
        confirm.setHeaderText("Ban user: " + selected.getUsername() + "?");
        confirm.setContentText("This user will not be able to login.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    boolean success = accountRepo.banUser(selected.getId());
                    Platform.runLater(() -> {
                        if (success) {
                            addLog("✅ Banned user: " + selected.getUsername());
                            loadAllUsers();
                        } else {
                            showAlert("Error", "Failed to ban user!");
                        }
                    });
                }).start();
            }
        });
    }
    
    @FXML
    private void onUnbanUser() {
        UserInfo selected = tblUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Warning", "Please select a user to unban!");
            return;
        }
        
        if (!selected.getStatus().equals("Banned")) {
            showAlert("Warning", "User is not banned!");
            return;
        }
        
        new Thread(() -> {
            boolean success = accountRepo.unbanUser(selected.getId());
            Platform.runLater(() -> {
                if (success) {
                    addLog("✅ Unbanned user: " + selected.getUsername());
                    loadAllUsers();
                } else {
                    showAlert("Error", "Failed to unban user!");
                }
            });
        }).start();
    }
    
    @FXML
    private void onAddGold() {
        UserInfo selected = tblUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Warning", "Please select a user!");
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog("1000");
        dialog.setTitle("Add Gold");
        dialog.setHeaderText("Add gold to: " + selected.getUsername());
        dialog.setContentText("Amount:");
        
        dialog.showAndWait().ifPresent(amount -> {
            try {
                int goldAmount = Integer.parseInt(amount);
                if (goldAmount <= 0) {
                    showAlert("Error", "Amount must be positive!");
                    return;
                }
                
                int newGold = selected.getGold() + goldAmount;
                
                new Thread(() -> {
                    boolean success = accountRepo.updateUserGold(selected.getId(), newGold);
                    Platform.runLater(() -> {
                        if (success) {
                            addLog("💰 Added " + goldAmount + " gold to " + selected.getUsername());
                            loadAllUsers();
                        } else {
                            showAlert("Error", "Failed to add gold!");
                        }
                    });
                }).start();
                
            } catch (NumberFormatException e) {
                showAlert("Error", "Invalid amount!");
            }
        });
    }
    
    @FXML
    private void onDeleteUser() {
        UserInfo selected = tblUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Warning", "Please select a user to delete!");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete User");
        confirm.setHeaderText("Delete user: " + selected.getUsername() + "?");
        confirm.setContentText("⚠️ This action CANNOT be undone! All user data will be permanently deleted.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    boolean success = accountRepo.deleteUser(selected.getId());
                    Platform.runLater(() -> {
                        if (success) {
                            addLog("🗑️ Deleted user: " + selected.getUsername());
                            loadAllUsers();
                            updateTotalUsersCount();
                        } else {
                            showAlert("Error", "Failed to delete user!");
                        }
                    });
                }).start();
            }
        });
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    // -------------------- UTILITIES --------------------

    private void addLog(String message) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        txtLog.appendText("[" + ts + "] " + message + "\n");
    }

    @FXML
    private void onClearLog() {
        txtLog.clear();
    }

    private void updateUptimeLoop() {
        while (serverRunning && startTime != null) {
            Platform.runLater(() -> {
                Duration duration = Duration.between(startTime, LocalDateTime.now());
                long h = duration.toHours();
                long m = duration.toMinutesPart();
                long s = duration.toSecondsPart();
                lblUptime.setText(String.format("Uptime: %02d:%02d:%02d", h, m, s));
            });
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
    }

    // -------------------- INNER DATA CLASSES --------------------

    public static class ClientInfo {
        private String ipAddress;
        private String username;
        private String connectTime;

        public ClientInfo(String ipAddress, String username, String connectTime) {
            this.ipAddress = ipAddress;
            this.username = username;
            this.connectTime = connectTime;
        }

        public String getIpAddress() { return ipAddress; }
        public String getUsername() { return username; }
        public String getConnectTime() { return connectTime; }

        public void setUsername(String username) { this.username = username; }
    }

    public static class RoomInfo {
        private String name;
        private String playerCount;
        private String status;

        public RoomInfo(String name, String playerCount, String status) {
            this.name = name;
            this.playerCount = playerCount;
            this.status = status;
        }

        public String getName() { return name; }
        public String getPlayerCount() { return playerCount; }
        public String getStatus() { return status; }
    }
    
    public static class UserInfo {
        private int id;
        private String username;
        private int level;
        private int gold;
        private String status;

        public UserInfo(int id, String username, int level, int gold, String status) {
            this.id = id;
            this.username = username;
            this.level = level;
            this.gold = gold;
            this.status = status;
        }

        public int getId() { return id; }
        public String getUsername() { return username; }
        public int getLevel() { return level; }
        public int getGold() { return gold; }
        public String getStatus() { return status; }
    }

    public static class ServerEvent {
        public enum Type {
            CLIENT_CONNECTED, CLIENT_DISCONNECTED, CLIENT_LOGIN, CLIENT_REGISTER,
            ROOM_UPDATED, SERVER_ERROR
        }

        private final Type type;
        private final String clientIP;
        private final String message;
        private final List<Map<String, Object>> rooms;

        public ServerEvent(Type type, String clientIP, String message) {
            this(type, clientIP, message, null);
        }

        public ServerEvent(Type type, String clientIP, String message, List<Map<String, Object>> rooms) {
            this.type = type;
            this.clientIP = clientIP;
            this.message = message;
            this.rooms = rooms;
        }

        public Type getType() { return type; }
        public String getClientIP() { return clientIP; }
        public String getMessage() { return message; }
        public List<Map<String, Object>> getRooms() { return rooms; }
    }
}
