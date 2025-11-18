module com.tank2d.tankserver {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.tank2d.tankserver to javafx.fxml;
    exports com.tank2d.tankserver;
}