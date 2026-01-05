package com.tank2d.tankserver.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        String sql = "SELECT * FROM user WHERE username = ? AND password = ? AND is_banned = 0";
        try (Connection conn = Connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            boolean ok = rs.next();
            
            // Update last login
            if (ok) {
                updateLastLogin(username);
            }
            
            System.out.println(ok ? "Login OK for " + username : "Login failed for " + username);
            return ok;
        } catch (SQLException e) {
            System.out.println("Login error: " + e.getMessage());
            return false;
        }
    }
    
    private void updateLastLogin(String username) {
        String sql = "UPDATE user SET last_login = CURRENT_TIMESTAMP WHERE username = ?";
        try (Connection conn = Connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Update last login error: " + e.getMessage());
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

    // ============= ADMIN USER MANAGEMENT =============
    
    /**
     * Lấy danh sách tất cả users
     */
    public List<Map<String, Object>> getAllUsers() {
        List<Map<String, Object>> users = new ArrayList<>();
        String sql = "SELECT id, username, gold, level, experience, is_banned, created_at, last_login FROM user ORDER BY id DESC";
        
        try (Connection conn = Connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("id", rs.getInt("id"));
                user.put("username", rs.getString("username"));
                user.put("gold", rs.getInt("gold"));
                user.put("level", rs.getInt("level"));
                user.put("experience", rs.getInt("experience"));
                user.put("isBanned", rs.getBoolean("is_banned"));
                user.put("createdAt", rs.getTimestamp("created_at"));
                user.put("lastLogin", rs.getTimestamp("last_login"));
                users.add(user);
            }
            
            System.out.println("Loaded " + users.size() + " users from database");
        } catch (SQLException e) {
            System.out.println("Get all users error: " + e.getMessage());
            e.printStackTrace();
        }
        
        return users;
    }
    
    /**
     * Tìm kiếm users theo username
     */
    public List<Map<String, Object>> searchUsers(String searchTerm) {
        List<Map<String, Object>> users = new ArrayList<>();
        String sql = "SELECT id, username, gold, level, experience, is_banned, created_at, last_login " +
                     "FROM user WHERE username LIKE ? ORDER BY id DESC";
        
        try (Connection conn = Connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, "%" + searchTerm + "%");
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("id", rs.getInt("id"));
                user.put("username", rs.getString("username"));
                user.put("gold", rs.getInt("gold"));
                user.put("level", rs.getInt("level"));
                user.put("experience", rs.getInt("experience"));
                user.put("isBanned", rs.getBoolean("is_banned"));
                user.put("createdAt", rs.getTimestamp("created_at"));
                user.put("lastLogin", rs.getTimestamp("last_login"));
                users.add(user);
            }
            
        } catch (SQLException e) {
            System.out.println("Search users error: " + e.getMessage());
        }
        
        return users;
    }
    
    /**
     * Ban user
     */
    public boolean banUser(int userId) {
        String sql = "UPDATE user SET is_banned = 1 WHERE id = ?";
        try (Connection conn = Connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("User " + userId + " has been banned");
                return true;
            }
        } catch (SQLException e) {
            System.out.println("Ban user error: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Unban user
     */
    public boolean unbanUser(int userId) {
        String sql = "UPDATE user SET is_banned = 0 WHERE id = ?";
        try (Connection conn = Connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("User " + userId + " has been unbanned");
                return true;
            }
        } catch (SQLException e) {
            System.out.println("Unban user error: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Xóa user
     */
    public boolean deleteUser(int userId) {
        String sql = "DELETE FROM user WHERE id = ?";
        try (Connection conn = Connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("User " + userId + " has been deleted");
                return true;
            }
        } catch (SQLException e) {
            System.out.println("Delete user error: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Cập nhật gold của user (không trong transaction)
     */
    public boolean updateUserGold(int userId, int newGold) {
        String sql = "UPDATE user SET gold = ? WHERE id = ?";
        try (Connection conn = Connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, newGold);
            stmt.setInt(2, userId);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("Updated gold for user " + userId + " to " + newGold);
                return true;
            }
        } catch (SQLException e) {
            System.out.println("Update user gold error: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Đếm tổng số users
     */
    public int getTotalUsers() {
        String sql = "SELECT COUNT(*) as total FROM user";
        try (Connection conn = Connector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            System.out.println("Get total users error: " + e.getMessage());
        }
        return 0;
    }

}