module com.tank2d.tankserver {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.json;


    opens com.tank2d.tankserver.ui to javafx.fxml;
    exports com.tank2d.tankserver;
    exports com.tank2d.tankserver.ui;
}