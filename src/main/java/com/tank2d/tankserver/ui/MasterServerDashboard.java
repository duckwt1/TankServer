package com.tank2d.tankserver.ui;

import com.tank2d.tankserver.core.AssetHttpServer;
import com.tank2d.tankserver.core.ItemShopManager;
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

import java.io.File;
import java.io.IOException;
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
    
    // Tank Management
    @FXML private TableView<TankInfo> tblTanks;
    @FXML private TableColumn<TankInfo, Integer> colTankId;
    @FXML private TableColumn<TankInfo, String> colTankName;
    @FXML private TableColumn<TankInfo, Double> colTankHP;
    @FXML private TableColumn<TankInfo, Double> colTankAttack;
    @FXML private TableColumn<TankInfo, Double> colTankSpeed;
    @FXML private TableColumn<TankInfo, Integer> colTankPrice;
    @FXML private TextField txtTankName;
    @FXML private TextArea txtTankDesc;
    @FXML private TextField txtTankPrice;
    @FXML private Slider sldHP;
    @FXML private Slider sldAttack;
    @FXML private Slider sldSpeed;
    @FXML private Label lblHPValue;
    @FXML private Label lblAttackValue;
    @FXML private Label lblSpeedValue;
    @FXML private Button btnCreateTank;
    @FXML private Button btnUpdateTank;
    @FXML private Button btnDeleteTank;
    @FXML private javafx.scene.image.ImageView imgTankPreview;
    @FXML private Label lblTankIconPath;
    @FXML private Button btnUploadTankIcon;
    
    // Item Management
    @FXML private TableView<ItemInfo> tblItems;
    @FXML private TableColumn<ItemInfo, Integer> colItemId;
    @FXML private TableColumn<ItemInfo, String> colItemName;
    @FXML private TableColumn<ItemInfo, String> colItemType;
    @FXML private TableColumn<ItemInfo, String> colItemRarity;
    @FXML private TableColumn<ItemInfo, Integer> colItemPrice;
    @FXML private TextField txtItemName;
    @FXML private TextArea txtItemDesc;
    @FXML private TextField txtItemPrice;
    @FXML private ComboBox<String> cmbItemType;
    @FXML private ComboBox<String> cmbItemRarity;
    @FXML private Slider sldItemHP;
    @FXML private Slider sldItemMP;
    @FXML private Label lblItemHPValue;
    @FXML private Label lblItemMPValue;
    @FXML private Button btnCreateItem;
    @FXML private Button btnUpdateItem;
    @FXML private Button btnDeleteItem;
    @FXML private javafx.scene.image.ImageView imgItemPreview;
    @FXML private Label lblItemIconPath;
    @FXML private Button btnUploadItemIcon;
    
    @FXML private TextArea txtLog;

    // ================== PANELS ==================
    @FXML private  HBox paneDashboard;
    @FXML private VBox paneUsers;
    @FXML private VBox paneRooms;
    @FXML private HBox paneTanks;
    @FXML private HBox paneItems;
    @FXML private Label lblCurrentPage;

    // ================== INTERNAL STATE ==================
    private MasterServer server;
    private AssetHttpServer assetServer;
    private boolean serverRunning = false;
    private LocalDateTime startTime;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final AccountRepository accountRepo = new AccountRepository();

    private final ObservableList<ClientInfo> clientList = FXCollections.observableArrayList();
    private final ObservableList<RoomInfo> roomList = FXCollections.observableArrayList();
    private final ObservableList<UserInfo> userList = FXCollections.observableArrayList();
    private final ObservableList<TankInfo> tankList = FXCollections.observableArrayList();
    private final ObservableList<ItemInfo> itemList = FXCollections.observableArrayList();

    private File selectedTankIcon; // For tank upload
    private File selectedItemIcon; // For item upload

    // =====================================================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUI();
        setupClientTable();
        setupRoomTable();
        setupUserTable();
        setupTankTable();
        setupItemTable();
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
    
    private void setupTankTable() {
        if (colTankId != null) colTankId.setCellValueFactory(new PropertyValueFactory<>("id"));
        if (colTankName != null) colTankName.setCellValueFactory(new PropertyValueFactory<>("name"));
        if (colTankHP != null) colTankHP.setCellValueFactory(new PropertyValueFactory<>("health"));
        if (colTankAttack != null) colTankAttack.setCellValueFactory(new PropertyValueFactory<>("damage"));
        if (colTankSpeed != null) colTankSpeed.setCellValueFactory(new PropertyValueFactory<>("speed"));
        if (colTankPrice != null) colTankPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        if (tblTanks != null) {
            tblTanks.setItems(tankList);
            
            // Selection listener
            tblTanks.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    populateTankEditor(newVal);
                }
            });
        }
        
        // Slider value listeners
        if (sldHP != null && lblHPValue != null) {
            sldHP.valueProperty().addListener((obs, oldVal, newVal) -> 
                lblHPValue.setText(String.format("%.0f", newVal)));
        }
        if (sldAttack != null && lblAttackValue != null) {
            sldAttack.valueProperty().addListener((obs, oldVal, newVal) -> 
                lblAttackValue.setText(String.format("%.0f", newVal)));
        }
        if (sldSpeed != null && lblSpeedValue != null) {
            sldSpeed.valueProperty().addListener((obs, oldVal, newVal) -> 
                lblSpeedValue.setText(String.format("%.1f", newVal)));
        }
    }
    
    private void setupItemTable() {
        if (colItemId != null) colItemId.setCellValueFactory(new PropertyValueFactory<>("id"));
        if (colItemName != null) colItemName.setCellValueFactory(new PropertyValueFactory<>("name"));
        if (colItemType != null) colItemType.setCellValueFactory(new PropertyValueFactory<>("type"));
        if (colItemRarity != null) colItemRarity.setCellValueFactory(new PropertyValueFactory<>("rarity"));
        if (colItemPrice != null) colItemPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        if (tblItems != null) {
            tblItems.setItems(itemList);
            
            // Selection listener
            tblItems.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    populateItemEditor(newVal);
                }
            });
        }
        
        // Slider value listeners
        if (sldItemHP != null && lblItemHPValue != null) {
            sldItemHP.valueProperty().addListener((obs, oldVal, newVal) -> 
                lblItemHPValue.setText(String.format("%.0f", newVal)));
        }
        if (sldItemMP != null && lblItemMPValue != null) {
            sldItemMP.valueProperty().addListener((obs, oldVal, newVal) -> 
                lblItemMPValue.setText(String.format("%.0f", newVal)));
        }
        
        // Setup ComboBox items
        if (cmbItemType != null) {
            cmbItemType.getItems().addAll("consumable", "equipment", "upgrade");
        }
        if (cmbItemRarity != null) {
            cmbItemRarity.getItems().addAll("common", "rare", "epic", "legendary");
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

            // Start asset HTTP server on port 8080
            assetServer = new AssetHttpServer(8080);
            assetServer.start();

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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
        }
        if (assetServer != null) {
            assetServer.stop();
        }
        serverRunning = false;
        onServerStopped();
        addLog("Server stopped");
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
        lblCurrentPage.setText("QUẢN LÝ TANK");        loadAllTanks();
    }
    
    private void loadAllTanks() {
        new Thread(() -> {
            List<Map<String, Object>> tanks = com.tank2d.tankserver.core.TankShopManager.getAllTanksForManagement();
            Platform.runLater(() -> {
                tankList.clear();
                for (var tank : tanks) {
                    Map<String, Double> attrs = (Map<String, Double>) tank.get("attributes");
                    tankList.add(new TankInfo(
                        (int) tank.get("id"),
                        (String) tank.get("name"),
                        attrs.getOrDefault("hp", 100.0),
                        attrs.getOrDefault("dmg", 50.0),
                        attrs.getOrDefault("spd", 5.0),
                        (int) tank.get("price")
                    ));
                }
                addLog("Loaded " + tankList.size() + " tanks");
            });
        }).start();    }
    
    private void populateTankEditor(TankInfo tank) {
        if (txtTankName != null) txtTankName.setText(tank.getName());
        if (txtTankPrice != null) txtTankPrice.setText(String.valueOf(tank.getPrice()));
        if (sldHP != null) sldHP.setValue(tank.getHealth());
        if (sldAttack != null) sldAttack.setValue(tank.getDamage());
        if (sldSpeed != null) sldSpeed.setValue(tank.getSpeed());
        
        // Load tank icon if server is running
        System.out.println("[Dashboard] Loading icon for tank: " + tank.getName());
        System.out.println("[Dashboard] assetServer: " + assetServer);
        System.out.println("[Dashboard] imgTankPreview: " + imgTankPreview);
        System.out.println("[Dashboard] lblTankIconPath: " + lblTankIconPath);
        
        if (assetServer != null && imgTankPreview != null && lblTankIconPath != null) {
            try {
                String imageUrl = assetServer.getTankAssetUrl(tank.getName());
                System.out.println("[Dashboard] Loading image from: " + imageUrl);
                
                javafx.scene.image.Image image = new javafx.scene.image.Image(imageUrl, true);
                imgTankPreview.setImage(image);
                
                String fileName = tank.getName().toLowerCase().replace(" ", "_") + ".png";
                lblTankIconPath.setText(fileName);
                System.out.println("[Dashboard] Image loaded successfully");
            } catch (Exception e) {
                System.err.println("[Dashboard] Failed to load icon: " + e.getMessage());
                e.printStackTrace();
                imgTankPreview.setImage(null);
                lblTankIconPath.setText("No icon found");
            }
        } else {
            System.out.println("[Dashboard] Cannot load icon - missing components");
        }
    }
    
    private void clearTankEditor() {
        if (txtTankName != null) txtTankName.clear();
        if (txtTankDesc != null) txtTankDesc.clear();
        if (txtTankPrice != null) txtTankPrice.clear();
        if (sldHP != null) sldHP.setValue(100);
        if (sldAttack != null) sldAttack.setValue(50);
        if (sldSpeed != null) sldSpeed.setValue(5);
        if (tblTanks != null) tblTanks.getSelectionModel().clearSelection();
        if (imgTankPreview != null) imgTankPreview.setImage(null);
        if (lblTankIconPath != null) lblTankIconPath.setText("No file selected");
        selectedTankIcon = null;
    }
    
    @FXML
    private void onCreateTank() {
        try {
            String name = txtTankName.getText().trim();
            String desc = txtTankDesc != null ? txtTankDesc.getText().trim() : "New tank";
            int price = Integer.parseInt(txtTankPrice.getText().trim());
            
            if (name.isEmpty()) {
                showAlert("Error", "Tank name is required!");
                return;
            }
            
            Map<String, Double> attributes = new java.util.HashMap<>();
            attributes.put("hp", sldHP.getValue());
            attributes.put("dmg", sldAttack.getValue());
            attributes.put("spd", sldSpeed.getValue());
            
            new Thread(() -> {
                boolean success = com.tank2d.tankserver.core.TankShopManager.createTank(name, desc, price, attributes);
                
                // Save asset if uploaded
                if (success && selectedTankIcon != null && assetServer != null) {
                    assetServer.saveTankAsset(name, selectedTankIcon);
                }
                
                Platform.runLater(() -> {
                    if (success) {
                        addLog("Created tank: " + name);
                        clearTankEditor();
                        loadAllTanks();
                        showAlert("Success", "Tank created successfully!");
                    } else {
                        showAlert("Error", "Failed to create tank!");
                    }
                });
            }).start();
            
        } catch (NumberFormatException e) {
            showAlert("Error", "Invalid price value!");
        }
    }
    
    @FXML
    private void onUpdateTank() {
        TankInfo selected = tblTanks.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Error", "Please select a tank to update!");
            return;
        }
        
        try {
            String name = txtTankName.getText().trim();
            String desc = txtTankDesc != null ? txtTankDesc.getText().trim() : selected.getName();
            int price = Integer.parseInt(txtTankPrice.getText().trim());
            
            if (name.isEmpty()) {
                showAlert("Error", "Tank name is required!");
                return;
            }
            
            Map<String, Double> attributes = new java.util.HashMap<>();
            attributes.put("hp", sldHP.getValue());
            attributes.put("dmg", sldAttack.getValue());
            attributes.put("spd", sldSpeed.getValue());
            
            int tankId = selected.getId();
            new Thread(() -> {
                boolean success = com.tank2d.tankserver.core.TankShopManager.updateTank(tankId, name, desc, price, attributes);
                
                // Save new asset if uploaded
                if (success && selectedTankIcon != null && assetServer != null) {
                    assetServer.saveTankAsset(name, selectedTankIcon);
                }
                
                Platform.runLater(() -> {
                    if (success) {
                        addLog("Updated tank: " + name);
                        clearTankEditor();
                        loadAllTanks();
                        showAlert("Success", "Tank updated successfully!");
                    } else {
                        showAlert("Error", "Failed to update tank!");
                    }
                });
            }).start();
            
        } catch (NumberFormatException e) {
            showAlert("Error", "Invalid price value!");
        }
    }
    
    @FXML
    private void onDeleteTank() {
        TankInfo selected = tblTanks.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Error", "Please select a tank to delete!");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete Tank: " + selected.getName());
        confirm.setContentText("Are you sure you want to delete this tank? This action cannot be undone.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                int tankId = selected.getId();
                String tankName = selected.getName();
                new Thread(() -> {
                    boolean success = com.tank2d.tankserver.core.TankShopManager.deleteTank(tankId);
                    if (success && assetServer != null) {
                        assetServer.deleteTankAsset(tankName);
                    }
                    Platform.runLater(() -> {
                        if (success) {
                            addLog("Deleted tank: " + selected.getName());
                            clearTankEditor();
                            loadAllTanks();
                            showAlert("Success", "Tank deleted successfully!");
                        } else {
                            showAlert("Error", "Failed to delete tank! Tank may be in use by players.");
                        }
                    });
                }).start();
            }
        });
    }

    @FXML
    private void onUploadTankIcon() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Tank Icon");
        fileChooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("PNG Images", "*.png")
        );
        
        File file = fileChooser.showOpenDialog(btnUploadTankIcon.getScene().getWindow());
        if (file != null) {
            selectedTankIcon = file;
            lblTankIconPath.setText(file.getName());
            
            // Show preview
            try {
                javafx.scene.image.Image preview = new javafx.scene.image.Image(file.toURI().toString());
                imgTankPreview.setImage(preview);
            } catch (Exception e) {
                addLog("Failed to load image preview: " + e.getMessage());
            }
        }
    }

    // -------------------- ITEM MANAGEMENT --------------------
    
    private void loadAllItems() {
        new Thread(() -> {
            List<Map<String, Object>> items = ItemShopManager.getAllItemsForManagement();
            Platform.runLater(() -> {
                itemList.clear();
                for (var item : items) {
                    Map<String, Double> attrs = (Map<String, Double>) item.get("attributes");
                    itemList.add(new ItemInfo(
                        (int) item.get("id"),
                        (String) item.get("name"),
                        (String) item.get("type"),
                        (String) item.get("rarity"),
                        attrs.getOrDefault("hp", 0.0),
                        attrs.getOrDefault("mp", 0.0),
                        (int) item.get("price")
                    ));
                }
                addLog("Loaded " + itemList.size() + " items");
            });
        }).start();
    }
    
    private void populateItemEditor(ItemInfo item) {
        if (txtItemName != null) txtItemName.setText(item.getName());
        if (txtItemPrice != null) txtItemPrice.setText(String.valueOf(item.getPrice()));
        if (cmbItemType != null) cmbItemType.setValue(item.getType());
        if (cmbItemRarity != null) cmbItemRarity.setValue(item.getRarity());
        if (sldItemHP != null) sldItemHP.setValue(item.getHpBoost());
        if (sldItemMP != null) sldItemMP.setValue(item.getMpBoost());
        
        // Load item icon
        System.out.println("[Dashboard] Loading icon for item: " + item.getName());
        if (assetServer != null && imgItemPreview != null && lblItemIconPath != null) {
            try {
                String imageUrl = assetServer.getItemAssetUrl(item.getName());
                System.out.println("[Dashboard] Loading image from: " + imageUrl);
                
                javafx.scene.image.Image image = new javafx.scene.image.Image(imageUrl, true);
                imgItemPreview.setImage(image);
                
                String fileName = item.getName().toLowerCase().replace(" ", "_") + ".png";
                lblItemIconPath.setText(fileName);
                System.out.println("[Dashboard] Image loaded successfully");
            } catch (Exception e) {
                System.err.println("[Dashboard] Failed to load icon: " + e.getMessage());
                imgItemPreview.setImage(null);
                lblItemIconPath.setText("No icon found");
            }
        }
    }
    
    private void clearItemEditor() {
        if (txtItemName != null) txtItemName.clear();
        if (txtItemDesc != null) txtItemDesc.clear();
        if (txtItemPrice != null) txtItemPrice.clear();
        if (cmbItemType != null) cmbItemType.setValue(null);
        if (cmbItemRarity != null) cmbItemRarity.setValue(null);
        if (sldItemHP != null) sldItemHP.setValue(0);
        if (sldItemMP != null) sldItemMP.setValue(0);
        if (tblItems != null) tblItems.getSelectionModel().clearSelection();
        if (imgItemPreview != null) imgItemPreview.setImage(null);
        if (lblItemIconPath != null) lblItemIconPath.setText("No file selected");
        selectedItemIcon = null;
    }
    
    @FXML
    private void onCreateItem() {
        try {
            String name = txtItemName.getText().trim();
            String desc = txtItemDesc != null ? txtItemDesc.getText().trim() : "New item";
            int price = Integer.parseInt(txtItemPrice.getText().trim());
            String type = cmbItemType.getValue();
            String rarity = cmbItemRarity.getValue();
            
            if (name.isEmpty()) {
                showAlert("Error", "Item name is required!");
                return;
            }
            if (type == null) {
                showAlert("Error", "Please select item type!");
                return;
            }
            if (rarity == null) {
                showAlert("Error", "Please select rarity!");
                return;
            }
            
            Map<String, Double> attributes = new java.util.HashMap<>();
            if (sldItemHP.getValue() > 0) attributes.put("hp", sldItemHP.getValue());
            if (sldItemMP.getValue() > 0) attributes.put("mp", sldItemMP.getValue());
            
            new Thread(() -> {
                boolean success = ItemShopManager.createItem(name, desc, price, type, rarity, attributes);
                
                // Save asset if uploaded
                if (success && selectedItemIcon != null && assetServer != null) {
                    assetServer.saveItemAsset(name, selectedItemIcon);
                }
                
                Platform.runLater(() -> {
                    if (success) {
                        addLog("Created item: " + name);
                        clearItemEditor();
                        loadAllItems();
                        showAlert("Success", "Item created successfully!");
                    } else {
                        showAlert("Error", "Failed to create item!");
                    }
                });
            }).start();
            
        } catch (NumberFormatException e) {
            showAlert("Error", "Invalid price value!");
        }
    }
    
    @FXML
    private void onUpdateItem() {
        ItemInfo selected = tblItems.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Error", "Please select an item to update!");
            return;
        }
        
        try {
            String name = txtItemName.getText().trim();
            String desc = txtItemDesc != null ? txtItemDesc.getText().trim() : selected.getName();
            int price = Integer.parseInt(txtItemPrice.getText().trim());
            String type = cmbItemType.getValue();
            String rarity = cmbItemRarity.getValue();
            
            if (name.isEmpty()) {
                showAlert("Error", "Item name is required!");
                return;
            }
            
            Map<String, Double> attributes = new java.util.HashMap<>();
            if (sldItemHP.getValue() > 0) attributes.put("hp", sldItemHP.getValue());
            if (sldItemMP.getValue() > 0) attributes.put("mp", sldItemMP.getValue());
            
            int itemId = selected.getId();
            new Thread(() -> {
                boolean success = ItemShopManager.updateItem(itemId, name, desc, price, type, rarity, attributes);
                
                // Save new asset if uploaded
                if (success && selectedItemIcon != null && assetServer != null) {
                    assetServer.saveItemAsset(name, selectedItemIcon);
                }
                
                Platform.runLater(() -> {
                    if (success) {
                        addLog("Updated item: " + name);
                        clearItemEditor();
                        loadAllItems();
                        showAlert("Success", "Item updated successfully!");
                    } else {
                        showAlert("Error", "Failed to update item!");
                    }
                });
            }).start();
            
        } catch (NumberFormatException e) {
            showAlert("Error", "Invalid price value!");
        }
    }
    
    @FXML
    private void onDeleteItem() {
        ItemInfo selected = tblItems.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Error", "Please select an item to delete!");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete Item: " + selected.getName());
        confirm.setContentText("Are you sure you want to delete this item?");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                int itemId = selected.getId();
                new Thread(() -> {
                    boolean success = ItemShopManager.deleteItem(itemId);
                    
                    // Delete asset file
                    if (success && assetServer != null) {
                        assetServer.deleteItemAsset(selected.getName());
                    }
                    
                    Platform.runLater(() -> {
                        if (success) {
                            addLog("Deleted item: " + selected.getName());
                            clearItemEditor();
                            loadAllItems();
                            showAlert("Success", "Item deleted successfully!");
                        } else {
                            showAlert("Error", "Failed to delete item!");
                        }
                    });
                }).start();
            }
        });
    }
    
    @FXML
    private void onUploadItemIcon() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Item Icon");
        fileChooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("PNG Images", "*.png")
        );
        
        File file = fileChooser.showOpenDialog(btnUploadItemIcon.getScene().getWindow());
        if (file != null) {
            selectedItemIcon = file;
            lblItemIconPath.setText(file.getName());
            
            // Show preview
            try {
                javafx.scene.image.Image preview = new javafx.scene.image.Image(file.toURI().toString());
                imgItemPreview.setImage(preview);
            } catch (Exception e) {
                addLog("Failed to load image preview: " + e.getMessage());
            }
        }
    }

    // -------------------- NAVIGATION --------------------
    @FXML
    private void showItemManagement(){
        hideAllPanels();
        paneItems.setVisible(true);
        lblCurrentPage.setText("QUẢN LÝ ITEMS");
        loadAllItems();
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
    
    public static class TankInfo {
        private int id;
        private String name;
        private double health;
        private double damage;
        private double speed;
        private int price;

        public TankInfo(int id, String name, double health, double damage, double speed, int price) {
            this.id = id;
            this.name = name;
            this.health = health;
            this.damage = damage;
            this.speed = speed;
            this.price = price;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public double getHealth() { return health; }
        public double getDamage() { return damage; }
        public double getSpeed() { return speed; }
        public int getPrice() { return price; }
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

    public static class ItemInfo {
        private int id;
        private String name;
        private String type;
        private String rarity;
        private double hpBoost;
        private double mpBoost;
        private int price;

        public ItemInfo(int id, String name, String type, String rarity, double hpBoost, double mpBoost, int price) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.rarity = rarity;
            this.hpBoost = hpBoost;
            this.mpBoost = mpBoost;
            this.price = price;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getType() { return type; }
        public String getRarity() { return rarity; }
        public double getHpBoost() { return hpBoost; }
        public double getMpBoost() { return mpBoost; }
        public int getPrice() { return price; }
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
