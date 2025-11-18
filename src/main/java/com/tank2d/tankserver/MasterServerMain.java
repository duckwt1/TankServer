package com.tank2d.masterserver;

import com.tank2d.masterserver.core.MasterServer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MasterServerMain extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tank2d/masterserver/ui/dashboard.fxml"));
        Scene scene = new Scene(loader.load());
        
        stage.setTitle("Tank2D Master Server Dashboard");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(700);
        stage.show();
        
        // Handle window close event
        stage.setOnCloseRequest(event -> {
            System.exit(0);
        });
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--console")) {
            // Run in console mode
            System.out.println("Starting Master Server in console mode...");
            new MasterServer().start(5000);
        } else {
            // Run with GUI
            launch(args);
        }
    }
}