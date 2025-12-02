module com.example.scribble {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.logging;
    requires mysql.connector.j; // nếu tên module connector khác, xem note bên dưới
    requires java.desktop;
    requires java.rmi;

    // Add the common automatic module produced from libs/common.jar
    // IntelliJ will typically show the automatic module name as "common"
    requires common;

    // Allow JavaFX to access these packages via reflection (FXML controllers)
    opens com.example.scribble to javafx.graphics, javafx.fxml;
    opens com.example.scribble.Controller to javafx.fxml;
    opens com.example.scribble.communityChat.ui to javafx.fxml; // Added for ChatAreaController

    // Allow other modules to use these public classes
    exports com.example.scribble;
}
