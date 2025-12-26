package com.tank2d.tankserver.core;

import com.tank2d.tankserver.db.InventoryRepository;

import java.util.List;
import java.util.Map;

/**
 * Business Logic Layer for Inventory
 */
public class InventoryManager {
    
    /**
     * Lấy toàn bộ inventory của user
     */
    public static List<Map<String, Object>> getUserInventory(int userId) {
        return InventoryRepository.getUserInventory(userId);
    }
}