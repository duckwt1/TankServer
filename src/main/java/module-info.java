module com.tank2d.tankserver {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.json;
    requires jdk.httpserver;


    opens com.tank2d.tankserver.ui to javafx.fxml;
    exports com.tank2d.tankserver;
    exports com.tank2d.tankserver.ui;
}