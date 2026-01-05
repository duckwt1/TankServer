package com.tank2d.tankserver.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;

/**
 * Simple HTTP server for serving tank and item images
 */
public class AssetHttpServer {
    private HttpServer server;
    private final int port;
    private final File assetRoot;

    public AssetHttpServer(int port) {
        this.port = port;
        // Assets stored in resources/images/
        this.assetRoot = new File("src/main/resources/images");
        if (!assetRoot.exists()) {
            assetRoot.mkdirs();
        }
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/assets", new AssetHandler());
        server.setExecutor(null); // Use default executor
        server.start();
        System.out.println("[AssetHttpServer] Started on port " + port);
        System.out.println("[AssetHttpServer] Serving from: " + assetRoot.getAbsolutePath());
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[AssetHttpServer] Stopped");
        }
    }

    public String getBaseUrl() {
        return "http://localhost:" + port + "/assets";
    }

    private class AssetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // Remove /assets prefix: /assets/tanks/vip_tank.png -> /tanks/vip_tank.png
            String relativePath = path.substring("/assets".length());
            
            File file = new File(assetRoot, relativePath);
            
            if (!file.exists() || !file.isFile()) {
                // File not found
                String response = "404 - Asset not found: " + relativePath;
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                System.out.println("[AssetHttpServer] 404: " + relativePath);
                return;
            }

            // Security check: prevent directory traversal
            if (!file.getCanonicalPath().startsWith(assetRoot.getCanonicalPath())) {
                String response = "403 - Forbidden";
                exchange.sendResponseHeaders(403, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                System.out.println("[AssetHttpServer] 403: Attempted directory traversal");
                return;
            }

            // Determine content type
            String contentType = "application/octet-stream";
            if (file.getName().endsWith(".png")) {
                contentType = "image/png";
            } else if (file.getName().endsWith(".jpg") || file.getName().endsWith(".jpeg")) {
                contentType = "image/jpeg";
            }

            // Send file
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400"); // Cache for 1 day
            exchange.sendResponseHeaders(200, fileBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(fileBytes);
            os.close();
            
            System.out.println("[AssetHttpServer] Served: " + relativePath + " (" + fileBytes.length + " bytes)");
        }
    }

    /**
     * Save tank image with naming convention: tankName.toLowerCase().replace(" ", "_") + ".png"
     */
    public boolean saveTankAsset(String tankName, File sourceFile) {
        try {
            File tanksDir = new File(assetRoot, "tanks");
            if (!tanksDir.exists()) {
                tanksDir.mkdirs();
            }

            String fileName = tankName.toLowerCase().replace(" ", "_") + ".png";
            File targetFile = new File(tanksDir, fileName);

            Files.copy(sourceFile.toPath(), targetFile.toPath(), 
                      java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            System.out.println("[AssetHttpServer] Saved tank asset: " + fileName);
            return true;
        } catch (IOException e) {
            System.err.println("[AssetHttpServer] Failed to save tank asset: " + e.getMessage());
            return false;
        }
    }

    /**
     * Save item image with naming convention: itemName.toLowerCase().replace(" ", "_") + ".png"
     */
    public boolean saveItemAsset(String itemName, File sourceFile) {
        try {
            File itemsDir = new File(assetRoot, "items");
            if (!itemsDir.exists()) {
                itemsDir.mkdirs();
            }

            String fileName = itemName.toLowerCase().replace(" ", "_") + ".png";
            File targetFile = new File(itemsDir, fileName);

            Files.copy(sourceFile.toPath(), targetFile.toPath(), 
                      java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            System.out.println("[AssetHttpServer] Saved item asset: " + fileName);
            return true;
        } catch (IOException e) {
            System.err.println("[AssetHttpServer] Failed to save item asset: " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete tank asset
     */
    public boolean deleteTankAsset(String tankName) {
        String fileName = tankName.toLowerCase().replace(" ", "_") + ".png";
        File file = new File(new File(assetRoot, "tanks"), fileName);
        if (file.exists() && file.delete()) {
            System.out.println("[AssetHttpServer] Deleted tank asset: " + fileName);
            return true;
        }
        return false;
    }

    /**
     * Delete item asset
     */
    public boolean deleteItemAsset(String itemName) {
        String fileName = itemName.toLowerCase().replace(" ", "_") + ".png";
        File file = new File(new File(assetRoot, "items"), fileName);
        if (file.exists() && file.delete()) {
            System.out.println("[AssetHttpServer] Deleted item asset: " + fileName);
            return true;
        }
        return false;
    }

    /**
     * Get URL for tank image
     */
    public String getTankAssetUrl(String tankName) {
        String fileName = tankName.toLowerCase().replace(" ", "_") + ".png";
        return getBaseUrl() + "/tanks/" + fileName;
    }

    /**
     * Get URL for item image
     */
    public String getItemAssetUrl(String itemName) {
        String fileName = itemName.toLowerCase().replace(" ", "_") + ".png";
        return getBaseUrl() + "/items/" + fileName;
    }
}
