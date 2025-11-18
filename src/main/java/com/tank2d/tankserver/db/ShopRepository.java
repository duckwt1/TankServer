package com.tank2d.masterserver.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tank2d.client.entity.ShopItem;


public class ShopRepository {

    /**
     * Lấy tất cả items có sẵn trong shop với dynamic attributes
     */
    public List<ShopItem> getAllAvailableItems() {
        List<ShopItem> items = new ArrayList<>();
        
        // Query lấy thông tin cơ bản của item
        String sql = """
            SELECT item.id, item.name, item.description, item.base_price,
                   shop.discount, shop.stock
            FROM shop
            JOIN item ON item.id = shop.item_id
            WHERE shop.available = 1;
        """;
        
        // Query lấy attributes của item
        String attrSql = """
            SELECT a.name, ia.attribute_value
            FROM item_attribute ia
            JOIN attribute a ON ia.attribute_id = a.id
            WHERE ia.item_id = ?;
        """;

        try (Connection conn = Connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             PreparedStatement attrPs = conn.prepareStatement(attrSql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int itemId = rs.getInt("id");
                
                // Lấy attributes của item này
                Map<String, Double> attributes = new HashMap<>();
                attrPs.setInt(1, itemId);
                ResultSet attrRs = attrPs.executeQuery();
                while (attrRs.next()) {
                    attributes.put(
                        attrRs.getString("name"),
                        attrRs.getDouble("attribute_value")
                    );
                }
                
                // Tạo ShopItem với attributes
                items.add(new ShopItem(
                    itemId,
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getInt("base_price"),
                    rs.getDouble("discount"),
                    rs.getInt("stock"),
                    attributes
                ));
            }
            
            System.out.println("[ShopRepository] Loaded " + items.size() + " items with dynamic attributes");

        } catch (Exception e) {
            System.out.println("[ShopRepository] Error loading items: " + e.getMessage());
            e.printStackTrace();
        }

        return items;
    }

    /**
     * Lấy thông tin item từ shop
     */
    public ShopItemInfo getShopItemInfo(Connection conn, int itemId) throws SQLException {
        String sql = """
            SELECT item.base_price, shop.discount, shop.stock
            FROM shop
            JOIN item ON item.id = shop.item_id
            WHERE item.id = ? AND shop.available = 1
            FOR UPDATE;
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                return new ShopItemInfo(
                    rs.getInt("base_price"),
                    rs.getDouble("discount"),
                    rs.getInt("stock")
                );
            }
        }
        return null;
    }

    /**
     * Cập nhật stock của item
     */
    public void updateStock(Connection conn, int itemId, int quantity) throws SQLException {
        String sql = "UPDATE shop SET stock = stock - ? WHERE item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, itemId);
            ps.executeUpdate();
        }
    }

    /**
     * Kiểm tra và thêm item vào inventory của user
     */
    public void addToUserInventory(Connection conn, int userId, int itemId, int quantity) throws SQLException {
        // Kiểm tra item đã tồn tại chưa
        String checkSql = "SELECT quantity FROM user_item WHERE user_id = ? AND item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setInt(1, userId);
            ps.setInt(2, itemId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // Đã có → Update quantity
                String updateSql = "UPDATE user_item SET quantity = quantity + ? WHERE user_id = ? AND item_id = ?";
                try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                    psUpdate.setInt(1, quantity);
                    psUpdate.setInt(2, userId);
                    psUpdate.setInt(3, itemId);
                    psUpdate.executeUpdate();
                }
            } else {
                // Chưa có → Insert new
                String insertSql = "INSERT INTO user_item(user_id, item_id, quantity) VALUES (?, ?, ?)";
                try (PreparedStatement psInsert = conn.prepareStatement(insertSql)) {
                    psInsert.setInt(1, userId);
                    psInsert.setInt(2, itemId);
                    psInsert.setInt(3, quantity);
                    psInsert.executeUpdate();
                }
            }
        }
    }

    /**
     * Ghi lại transaction
     */
    public void recordTransaction(Connection conn, int userId, int itemId, int quantity, int totalPrice) throws SQLException {
        String sql = """
            INSERT INTO transactions(user_id, item_id, quantity, total_price)
            VALUES (?, ?, ?, ?)
        """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, itemId);
            ps.setInt(3, quantity);
            ps.setInt(4, totalPrice);
            ps.executeUpdate();
        }
    }

    /**
     * Inner class để lưu thông tin item
     */
    public static class ShopItemInfo {
        public final int price;
        public final double discount;
        public final int stock;

        public ShopItemInfo(int price, double discount, int stock) {
            this.price = price;
            this.discount = discount;
            this.stock = stock;
        }

        public int getFinalPrice() {
            return (int)(price * (1 - discount));
        }
    }
}
