package com.tank2d.tankserver.db;

import com.tank2d.tankserver.core.shop.ShopItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TankShopRepository {
    
    /**
     * Lấy tất cả tanks có sẵn trong shop
     */
    public List<ShopItem> getAllAvailableTanks() {
        List<ShopItem> tanks = new ArrayList<>();
        
        String sql = """
            SELECT 
                t.id,
                t.name,
                t.description,
                t.base_price
            FROM tank t
            WHERE t.id > 1
            ORDER BY t.base_price ASC
        """;
        
        String attrSql = """
            SELECT a.name, ta.attribute_value
            FROM tank_attribute ta
            JOIN attribute a ON ta.attribute_id = a.id
            WHERE ta.tank_id = ?
        """;
        
        try (Connection conn = Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             PreparedStatement attrPs = conn.prepareStatement(attrSql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                int tankId = rs.getInt("id");
                
                // Load attributes
                Map<String, Double> attributes = new HashMap<>();
                attrPs.setInt(1, tankId);
                ResultSet attrRs = attrPs.executeQuery();
                while (attrRs.next()) {
                    attributes.put(
                        attrRs.getString("name"),
                        attrRs.getDouble("attribute_value")
                    );
                }
                
                tanks.add(new ShopItem(
                    tankId,
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getInt("base_price"),
                    0.0, // No discount for tanks
                    -1,  // Unlimited stock
                    attributes
                ));
            }
            
            System.out.println("[TankShopRepository] Loaded " + tanks.size() + " tanks for shop");
            
        } catch (Exception e) {
            System.out.println("[TankShopRepository] Error loading tanks: " + e.getMessage());
            e.printStackTrace();
        }
        
        return tanks;
    }
    
    /**
     * Mua tank cho user
     */
    public boolean buyTank(Connection conn, int userId, int tankId) throws SQLException {
        // Check if user already has this tank
        String checkSql = "SELECT 1 FROM user_tank WHERE user_id = ? AND tank_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setInt(1, userId);
            ps.setInt(2, tankId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return false; // Already owned
            }
        }
        
        // Add tank to user's collection
        String insertSql = "INSERT INTO user_tank(user_id, tank_id, is_equipped) VALUES (?, ?, 0)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setInt(1, userId);
            ps.setInt(2, tankId);
            ps.executeUpdate();
        }
        
        return true;
    }
    
    /**
     * Lấy giá tank
     */
    public int getTankPrice(Connection conn, int tankId) throws SQLException {
        String sql = "SELECT base_price FROM tank WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tankId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("base_price");
            }
        }
        return 0;
    }
    
    /**
     * Equip tank cho user
     */
    public boolean equipTank(int userId, int tankId) {
        try (Connection conn = Connector.getConnection()) {
            conn.setAutoCommit(false);
            
            // Check if user owns this tank
            String checkSql = "SELECT 1 FROM user_tank WHERE user_id = ? AND tank_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, tankId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    conn.rollback();
                    return false; // User doesn't own this tank
                }
            }
            
            // Unequip all tanks
            String unequipSql = "UPDATE user_tank SET is_equipped = 0 WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(unequipSql)) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            }
            
            // Equip selected tank
            String equipSql = "UPDATE user_tank SET is_equipped = 1 WHERE user_id = ? AND tank_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(equipSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, tankId);
                ps.executeUpdate();
            }
            
            conn.commit();
            System.out.println("[TankShopRepository] User " + userId + " equipped tank " + tankId);
            return true;
            
        } catch (Exception e) {
            System.out.println("[TankShopRepository] Error equipping tank: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
