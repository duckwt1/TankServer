package com.tank2d.masterserver.core;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.tank2d.client.entity.ShopItem;
import com.tank2d.masterserver.core.shop.BuyResult;
import com.tank2d.masterserver.db.AccountRepository;
import com.tank2d.masterserver.db.Connector;
import com.tank2d.masterserver.db.ShopRepository;
import com.tank2d.masterserver.db.ShopRepository.ShopItemInfo;

/**
 * Business Logic Layer for Shop
 * Xử lý logic nghiệp vụ, validate, và điều phối các Repository
 */
public class ShopManager {
    
    private static final ShopRepository shopRepo = new ShopRepository();
    private static final AccountRepository accountRepo = new AccountRepository();

    /**
     * Lấy danh sách items trong shop
     */
    public static List<ShopItem> getAllShopItems() {
        return shopRepo.getAllAvailableItems();
    }

    /**
     * Xử lý logic mua item
     */
    public static BuyResult buyItem(int userId, int itemId, int quantity) {
        BuyResult result = new BuyResult();
        Connection conn = null;

        try {
            conn = Connector.getConnection();
            conn.setAutoCommit(false);

            // 1. Lấy thông tin item
            ShopItemInfo itemInfo = shopRepo.getShopItemInfo(conn, itemId);
            if (itemInfo == null) {
                result.status = "ITEM_NOT_FOUND";
                conn.rollback();
                return result;
            }

            // 2. Validate stock
            if (itemInfo.stock < quantity) {
                result.status = "OUT_OF_STOCK";
                conn.rollback();
                return result;
            }

            // 3. Tính giá
            int finalPrice = itemInfo.getFinalPrice();
            int totalCost = finalPrice * quantity;

            // 4. Lấy gold của user và validate
            int userGold = accountRepo.getUserGold(userId);
            if (userGold < totalCost) {
                result.status = "NOT_ENOUGH_GOLD";
                conn.rollback();
                return result;
            }

            // 5. Thực hiện giao dịch
            accountRepo.updateGold(conn, userId, -totalCost); // Trừ gold
            shopRepo.updateStock(conn, itemId, quantity);      // Trừ stock
            shopRepo.addToUserInventory(conn, userId, itemId, quantity); // Thêm vào inventory
            shopRepo.recordTransaction(conn, userId, itemId, quantity, totalCost); // Lưu lịch sử

            // 6. Commit transaction
            conn.commit();

            result.status = "SUCCESS";
            result.remainingGold = userGold - totalCost;
            
            System.out.println("[ShopManager] User " + userId + " bought item " + itemId + " x" + quantity + 
                             " for " + totalCost + " gold");

        } catch (Exception e) {
            // Rollback nếu có lỗi
            if (conn != null) {
                try {
                    conn.rollback();
                    System.out.println("[ShopManager] Transaction rolled back: " + e.getMessage());
                } catch (SQLException rollbackEx) {
                    System.out.println("[ShopManager] Rollback failed: " + rollbackEx.getMessage());
                }
            }
            e.printStackTrace();
            result.status = "ERROR: " + e.getMessage();
            
        } finally {
            // Đóng connection
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException closeEx) {
                    System.out.println("[ShopManager] Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
        
        return result;
    }
}
