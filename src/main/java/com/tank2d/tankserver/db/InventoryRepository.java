package com.tank2d.tankserver.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryRepository {
    
    /**
     * Lấy toàn bộ inventory của user (items đã mua)
     */
    public static List<Map<String, Object>> getUserInventory(int userId) {
        List<Map<String, Object>> inventory = new ArrayList<>();
        
        String sql = """
            SELECT 
                i.id as item_id,
                i.name,
                i.description,
                ui.quantity,
                i.base_price as price
            FROM user_item ui
            JOIN item i ON ui.item_id = i.id
            WHERE ui.user_id = ?
            ORDER BY i.name ASC
        """;
        
        try (Connection conn = Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("itemId", rs.getInt("item_id"));
                item.put("name", rs.getString("name"));
                item.put("description", rs.getString("description"));
                item.put("quantity", rs.getInt("quantity"));
                item.put("price", rs.getInt("price"));
                
                // Load attributes
                Map<String, Double> attributes = getItemAttributes(conn, rs.getInt("item_id"));
                item.put("attributes", attributes);
                
                inventory.add(item);
            }
            
            System.out.println("[InventoryRepository] Loaded " + inventory.size() + " items for user " + userId);
            
        } catch (Exception e) {
            System.out.println("[InventoryRepository] Error loading inventory: " + e.getMessage());
            e.printStackTrace();
        }
        
        return inventory;
    }
    
    /**
     * Lấy attributes của một item
     */
    private static Map<String, Double> getItemAttributes(Connection conn, int itemId) {
        Map<String, Double> attributes = new HashMap<>();
        
        String sql = """
            SELECT a.name, ia.attribute_value
            FROM item_attribute ia
            JOIN attribute a ON ia.attribute_id = a.id
            WHERE ia.item_id = ?
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                attributes.put(rs.getString("name"), rs.getDouble("attribute_value"));
            }
        } catch (Exception e) {
            System.out.println("[InventoryRepository] Error loading attributes: " + e.getMessage());
        }
        
        return attributes;
    }
    
    /**
     * Kiểm tra user có item trong inventory không
     */
    public static boolean hasItem(int userId, int itemId) {
        String sql = "SELECT quantity FROM user_item WHERE user_id = ? AND item_id = ? AND quantity > 0";
        
        try (Connection conn = Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, userId);
            ps.setInt(2, itemId);
            ResultSet rs = ps.executeQuery();
            
            return rs.next();
            
        } catch (Exception e) {
            System.out.println("[InventoryRepository] Error checking item: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Lấy số lượng item trong inventory
     */
    public static int getItemQuantity(int userId, int itemId) {
        String sql = "SELECT quantity FROM user_item WHERE user_id = ? AND item_id = ?";
        
        try (Connection conn = Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, userId);
            ps.setInt(2, itemId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("quantity");
            }
            
        } catch (Exception e) {
            System.out.println("[InventoryRepository] Error getting quantity: " + e.getMessage());
        }
        
        return 0;
    }
}
