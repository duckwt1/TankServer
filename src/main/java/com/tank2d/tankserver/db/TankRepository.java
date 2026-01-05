package com.tank2d.tankserver.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TankRepository {
    
    /**
     * Lấy tất cả tanks của user từ bảng user_tank
     */
    public static List<Map<String, Object>> getUserTanks(int userId) {
        List<Map<String, Object>> tanks = new ArrayList<>();
        
        String sql = """
            SELECT 
                t.id as tank_id,
                t.name,
                t.description,
                t.base_price as price,
                ut.is_equipped
            FROM user_tank ut
            JOIN tank t ON ut.tank_id = t.id
            WHERE ut.user_id = ?
            ORDER BY ut.is_equipped DESC, t.name ASC
        """;
        
        try (Connection conn = Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> tank = new HashMap<>();
                tank.put("tankId", rs.getInt("tank_id"));
                tank.put("name", rs.getString("name"));
                tank.put("description", rs.getString("description"));
                tank.put("price", rs.getInt("price"));
                tank.put("isEquipped", rs.getInt("is_equipped"));
                
                // Load tank attributes
                Map<String, Double> attributes = getTankAttributes(conn, rs.getInt("tank_id"));
                tank.put("attributes", attributes);
                
                tanks.add(tank);
            }
            
            System.out.println("[TankRepository] Loaded " + tanks.size() + " tanks for user " + userId);
            
        } catch (Exception e) {
            System.out.println("[TankRepository] Error loading tanks: " + e.getMessage());
            e.printStackTrace();
        }
        
        return tanks;
    }
    
    /**
     * Lấy attributes của một tank
     */
    private static Map<String, Double> getTankAttributes(Connection conn, int tankId) {
        Map<String, Double> attributes = new HashMap<>();
        
        String sql = """
            SELECT a.name, ta.attribute_value
            FROM tank_attribute ta
            JOIN attribute a ON ta.attribute_id = a.id
            WHERE ta.tank_id = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tankId);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                attributes.put(rs.getString("name"), rs.getDouble("attribute_value"));
            }
        } catch (Exception e) {
            System.out.println("[TankRepository] Error loading tank attributes: " + e.getMessage());
        }
        
        return attributes;
    }
    
    /**
     * Kiểm tra user có tank không
     */
    public static boolean hasTank(int userId, int tankId) {
        String sql = "SELECT 1 FROM user_tank WHERE user_id = ? AND tank_id = ?";
        
        try (Connection conn = Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, userId);
            ps.setInt(2, tankId);
            ResultSet rs = ps.executeQuery();
            
            return rs.next();
            
        } catch (Exception e) {
            System.out.println("[TankRepository] Error checking tank: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Tạo tank mới trong database
     */
    public static boolean createTank(String name, String description, int basePrice, Map<String, Double> attributes) {
        String tankSql = "INSERT INTO tank (name, description, base_price) VALUES (?, ?, ?)";
        String attrSql = "INSERT INTO tank_attribute (tank_id, attribute_id, attribute_value) VALUES (?, ?, ?)";
        
        try (Connection conn = Connector.getConnection()) {
            conn.setAutoCommit(false);
            
            // Insert tank
            int tankId;
            try (PreparedStatement ps = conn.prepareStatement(tankSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, description);
                ps.setInt(3, basePrice);
                ps.executeUpdate();
                
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    tankId = rs.getInt(1);
                } else {
                    conn.rollback();
                    return false;
                }
            }
            
            // Insert attributes
            try (PreparedStatement ps = conn.prepareStatement(attrSql)) {
                for (Map.Entry<String, Double> entry : attributes.entrySet()) {
                    int attrId = getAttributeId(conn, entry.getKey());
                    if (attrId > 0) {
                        ps.setInt(1, tankId);
                        ps.setInt(2, attrId);
                        ps.setDouble(3, entry.getValue());
                        ps.executeUpdate();
                    }
                }
            }
            
            conn.commit();
            System.out.println("[TankRepository] Created tank: " + name);
            return true;
            
        } catch (Exception e) {
            System.out.println("[TankRepository] Error creating tank: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Cập nhật thông tin tank
     */
    public static boolean updateTank(int tankId, String name, String description, int basePrice, Map<String, Double> attributes) {
        String tankSql = "UPDATE tank SET name = ?, description = ?, base_price = ? WHERE id = ?";
        String deleteAttrSql = "DELETE FROM tank_attribute WHERE tank_id = ?";
        String insertAttrSql = "INSERT INTO tank_attribute (tank_id, attribute_id, attribute_value) VALUES (?, ?, ?)";
        
        try (Connection conn = Connector.getConnection()) {
            conn.setAutoCommit(false);
            
            // Update tank
            try (PreparedStatement ps = conn.prepareStatement(tankSql)) {
                ps.setString(1, name);
                ps.setString(2, description);
                ps.setInt(3, basePrice);
                ps.setInt(4, tankId);
                ps.executeUpdate();
            }
            
            // Delete old attributes
            try (PreparedStatement ps = conn.prepareStatement(deleteAttrSql)) {
                ps.setInt(1, tankId);
                ps.executeUpdate();
            }
            
            // Insert new attributes
            try (PreparedStatement ps = conn.prepareStatement(insertAttrSql)) {
                for (Map.Entry<String, Double> entry : attributes.entrySet()) {
                    int attrId = getAttributeId(conn, entry.getKey());
                    if (attrId > 0) {
                        ps.setInt(1, tankId);
                        ps.setInt(2, attrId);
                        ps.setDouble(3, entry.getValue());
                        ps.executeUpdate();
                    }
                }
            }
            
            conn.commit();
            System.out.println("[TankRepository] Updated tank: " + tankId);
            return true;
            
        } catch (Exception e) {
            System.out.println("[TankRepository] Error updating tank: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Xóa tank khỏi database
     */
    public static boolean deleteTank(int tankId) {
        // Kiểm tra xem tank có đang được sử dụng không
        String checkSql = "SELECT COUNT(*) FROM user_tank WHERE tank_id = ?";
        String deleteAttrSql = "DELETE FROM tank_attribute WHERE tank_id = ?";
        String deleteTankSql = "DELETE FROM tank WHERE id = ?";
        
        try (Connection conn = Connector.getConnection()) {
            // Check if tank is being used
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, tankId);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    System.out.println("[TankRepository] Cannot delete tank " + tankId + ": in use by users");
                    return false;
                }
            }
            
            conn.setAutoCommit(false);
            
            // Delete attributes
            try (PreparedStatement ps = conn.prepareStatement(deleteAttrSql)) {
                ps.setInt(1, tankId);
                ps.executeUpdate();
            }
            
            // Delete tank
            try (PreparedStatement ps = conn.prepareStatement(deleteTankSql)) {
                ps.setInt(1, tankId);
                ps.executeUpdate();
            }
            
            conn.commit();
            System.out.println("[TankRepository] Deleted tank: " + tankId);
            return true;
            
        } catch (Exception e) {
            System.out.println("[TankRepository] Error deleting tank: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Lấy attribute ID từ tên
     */
    private static int getAttributeId(Connection conn, String attributeName) {
        String sql = "SELECT id FROM attribute WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attributeName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (Exception e) {
            System.out.println("[TankRepository] Error getting attribute ID: " + e.getMessage());
        }
        return 0;
    }
}
