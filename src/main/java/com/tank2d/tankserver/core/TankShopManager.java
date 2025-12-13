package com.tank2d.tankserver.core;

import com.tank2d.tankserver.core.shop.BuyResult;
import com.tank2d.tankserver.core.shop.ShopItem;
import com.tank2d.tankserver.db.AccountRepository;
import com.tank2d.tankserver.db.Connector;
import com.tank2d.tankserver.db.TankShopRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Business Logic Layer for Tank Shop
 */
public class TankShopManager {
    
    private static final TankShopRepository tankRepo = new TankShopRepository();
    private static final AccountRepository accountRepo = new AccountRepository();
    
    /**
     * Lấy danh sách tanks trong shop
     */
    public static List<ShopItem> getAllTanks() {
        return tankRepo.getAllAvailableTanks();
    }
    
    /**
     * Xử lý logic mua tank
     */
    public static BuyResult buyTank(int userId, int tankId) {
        BuyResult result = new BuyResult();
        Connection conn = null;
        
        try {
            conn = Connector.getConnection();
            conn.setAutoCommit(false);
            
            // 1. Get tank price
            int price = tankRepo.getTankPrice(conn, tankId);
            if (price == 0) {
                result.status = "TANK_NOT_FOUND";
                conn.rollback();
                return result;
            }
            
            // 2. Check user gold
            int userGold = accountRepo.getUserGold(userId);
            if (userGold < price) {
                result.status = "NOT_ENOUGH_GOLD";
                conn.rollback();
                return result;
            }
            
            // 3. Try to buy tank (check if already owned)
            boolean success = tankRepo.buyTank(conn, userId, tankId);
            if (!success) {
                result.status = "ALREADY_OWNED";
                conn.rollback();
                return result;
            }
            
            // 4. Deduct gold
            accountRepo.updateGold(conn, userId, -price);
            
            // 5. Commit transaction
            conn.commit();
            
            result.status = "SUCCESS";
            result.remainingGold = userGold - price;
            
            System.out.println("[TankShopManager] User " + userId + " bought tank " + tankId + " for " + price + " gold");
            
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                    System.out.println("[TankShopManager] Transaction rolled back: " + e.getMessage());
                } catch (SQLException rollbackEx) {
                    System.out.println("[TankShopManager] Rollback failed: " + rollbackEx.getMessage());
                }
            }
            e.printStackTrace();
            result.status = "ERROR: " + e.getMessage();
            
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException closeEx) {
                    System.out.println("[TankShopManager] Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
        
        return result;
    }
    
    /**
     * Equip tank cho user
     */
    public static boolean equipTank(int userId, int tankId) {
        return tankRepo.equipTank(userId, tankId);
    }
}
