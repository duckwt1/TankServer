package com.tank2d.masterserver.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AccountRepository {

    public boolean register(String username, String password) {
        String sql = "INSERT INTO user (username, password) VALUES (?, ?)";
        try (Connection conn = Connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.executeUpdate();
            System.out.println("Registered new user: " + username);
            return true;
        } catch (SQLException e) {
            if (e.getMessage().contains("Duplicate")) {
                System.out.println("Username already exists: " + username);
            } else {
                System.out.println("Register error: " + e.getMessage());
            }
            return false;
        }
    }

    public boolean login(String username, String password) {
        String sql = "SELECT * FROM user WHERE username = ? AND password = ?";
        try (Connection conn = Connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            boolean ok = rs.next();
            System.out.println(ok ? "Login OK for " + username : "Login failed for " + username);
            return ok;
        } catch (SQLException e) {
            System.out.println("Login error: " + e.getMessage());
            return false;
        }
    }

    public int getUserIdByUsername(String username) {
        String sql = "SELECT id FROM user WHERE username = ?";
        System.out.println("[getUserIdByUsername] Searching for username: '" + username + "'");
        
        try (Connection localConn = Connector.getConnection();
             PreparedStatement stmt = localConn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int userId = rs.getInt("id");
                System.out.println("[getUserIdByUsername] FOUND userId: " + userId);
                return userId;
            } else {
                System.out.println("[getUserIdByUsername] NOT FOUND in database!");
            }
        } catch (SQLException e) {
            System.out.println("[getUserIdByUsername] SQL Error: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    public int getUserGold(int userId) {
        String sql = "SELECT gold FROM user WHERE id = ?";
        try (Connection conn = Connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("gold");
            }
        } catch (SQLException e) {
            System.out.println("Get gold error: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Cập nhật gold của user (dùng trong transaction)
     * @param conn Connection đang trong transaction
     * @param userId ID của user
     * @param amount Số lượng thay đổi (âm = trừ, dương = cộng)
     */
    public void updateGold(Connection conn, int userId, int amount) throws SQLException {
        String sql = "UPDATE user SET gold = gold + ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, amount);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        }
    }
}