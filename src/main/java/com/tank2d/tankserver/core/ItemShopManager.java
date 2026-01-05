package com.tank2d.tankserver.core;

import com.tank2d.tankserver.db.ShopRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business Logic Layer for Item Management (Admin)
 */
public class ItemShopManager {
    
    private static final ShopRepository shopRepo = new ShopRepository();
    
    /**
     * Get all items for management (includes all fields)
     */
    public static List<Map<String, Object>> getAllItemsForManagement() {
        List<Map<String, Object>> items = new ArrayList<>();
        
        String sql = """
            SELECT i.id, i.name, i.description, i.base_price, 
                   i.item_type, i.rarity
            FROM item i
            ORDER BY i.id DESC
        """;
        
        try (Connection conn = com.tank2d.tankserver.db.Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                int itemId = rs.getInt("id");
                
                item.put("id", itemId);
                item.put("name", rs.getString("name"));
                item.put("description", rs.getString("description"));
                item.put("price", rs.getInt("base_price"));
                item.put("type", rs.getString("item_type"));
                item.put("rarity", rs.getString("rarity"));
                
                // Load attributes
                Map<String, Double> attributes = getItemAttributes(conn, itemId);
                item.put("attributes", attributes);
                
                items.add(item);
            }
            
            System.out.println("[ItemShopManager] Loaded " + items.size() + " items");
            
        } catch (Exception e) {
            System.err.println("[ItemShopManager] Error loading items: " + e.getMessage());
            e.printStackTrace();
        }
        
        return items;
    }
    
    /**
     * Get item attributes
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
            System.err.println("[ItemShopManager] Error loading attributes: " + e.getMessage());
        }
        
        return attributes;
    }
    
    /**
     * Create new item
     */
    public static boolean createItem(String name, String description, int basePrice, 
                                    String itemType, String rarity, 
                                    Map<String, Double> attributes) {
        String itemSql = "INSERT INTO item (name, description, base_price, item_type, rarity) VALUES (?, ?, ?, ?, ?)";
        String attrSql = "INSERT INTO item_attribute (item_id, attribute_id, attribute_value) VALUES (?, ?, ?)";
        
        try (Connection conn = com.tank2d.tankserver.db.Connector.getConnection()) {
            conn.setAutoCommit(false);
            
            // Insert item
            int itemId;
            try (PreparedStatement ps = conn.prepareStatement(itemSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, description);
                ps.setInt(3, basePrice);
                ps.setString(4, itemType);
                ps.setString(5, rarity);
                ps.executeUpdate();
                
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    itemId = rs.getInt(1);
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
                        ps.setInt(1, itemId);
                        ps.setInt(2, attrId);
                        ps.setDouble(3, entry.getValue());
                        ps.executeUpdate();
                    }
                }
            }
            
            conn.commit();
            System.out.println("[ItemShopManager] Created item: " + name + " (ID: " + itemId + ")");
            return true;
            
        } catch (Exception e) {
            System.err.println("[ItemShopManager] Error creating item: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Update item
     */
    public static boolean updateItem(int itemId, String name, String description, int basePrice,
                                    String itemType, String rarity,
                                    Map<String, Double> attributes) {
        String itemSql = "UPDATE item SET name = ?, description = ?, base_price = ?, item_type = ?, rarity = ? WHERE id = ?";
        String deleteAttrSql = "DELETE FROM item_attribute WHERE item_id = ?";
        String insertAttrSql = "INSERT INTO item_attribute (item_id, attribute_id, attribute_value) VALUES (?, ?, ?)";
        
        try (Connection conn = com.tank2d.tankserver.db.Connector.getConnection()) {
            conn.setAutoCommit(false);
            
            // Update item
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                ps.setString(1, name);
                ps.setString(2, description);
                ps.setInt(3, basePrice);
                ps.setString(4, itemType);
                ps.setString(5, rarity);
                ps.setInt(6, itemId);
                ps.executeUpdate();
            }
            
            // Delete old attributes
            try (PreparedStatement ps = conn.prepareStatement(deleteAttrSql)) {
                ps.setInt(1, itemId);
                ps.executeUpdate();
            }
            
            // Insert new attributes
            try (PreparedStatement ps = conn.prepareStatement(insertAttrSql)) {
                for (Map.Entry<String, Double> entry : attributes.entrySet()) {
                    int attrId = getAttributeId(conn, entry.getKey());
                    if (attrId > 0) {
                        ps.setInt(1, itemId);
                        ps.setInt(2, attrId);
                        ps.setDouble(3, entry.getValue());
                        ps.executeUpdate();
                    }
                }
            }
            
            conn.commit();
            System.out.println("[ItemShopManager] Updated item: " + itemId);
            return true;
            
        } catch (Exception e) {
            System.err.println("[ItemShopManager] Error updating item: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Delete item
     */
    public static boolean deleteItem(int itemId) {
        String sql = "DELETE FROM item WHERE id = ?";
        
        try (Connection conn = com.tank2d.tankserver.db.Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, itemId);
            int affected = ps.executeUpdate();
            
            if (affected > 0) {
                System.out.println("[ItemShopManager] Deleted item: " + itemId);
                return true;
            }
            
        } catch (Exception e) {
            System.err.println("[ItemShopManager] Error deleting item: " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    /**
     * Get attribute ID by name
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
            System.err.println("[ItemShopManager] Error getting attribute ID: " + e.getMessage());
        }
        return 0;
    }
}
