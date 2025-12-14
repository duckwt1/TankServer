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
}
