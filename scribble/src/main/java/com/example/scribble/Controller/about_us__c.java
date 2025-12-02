package com.example.scribble.Controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

/**
 * Controller cho About Us view.
 * Giữ nguyên tên biến / tên class theo yêu cầu của bạn.
 */
public class about_us__c {

    @FXML
    private Button back_button;

    /**
     * Controller chính (nav bar) do parent set khi load FXML.
     * Đảm bảo parent gọi setMainController(...) sau khi tạo view này.
     */
    @FXML
    private nav_bar__c mainController;

    /**
     * Called when Back button is clicked in the About view.
     * Gọi mainController.loadFXML("Home.fxml") nếu mainController đã được set.
     */
    @FXML
    private void handle_back_button(ActionEvent event) {
        System.out.println("[about_us__c] Back button clicked");
        if (mainController == null) {
            System.out.println("[about_us__c] mainController is null, cannot load Home.fxml");
            return;
        }

        try {
            System.out.println("[about_us__c] Calling loadFXML for Home.fxml via mainController");
            // Ensure UI operations run on JavaFX Application Thread
            Platform.runLater(() -> {
                try {
                    mainController.loadFXML("Home.fxml");
                } catch (Exception e) {
                    // Nếu loadFXML ném exception, log rõ ràng
                    System.err.println("[about_us__c] Exception while calling mainController.loadFXML: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception ex) {
            System.err.println("[about_us__c] Unexpected exception in handle_back_button: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Setter được parent (nav_bar__c) gọi để inject controller cha.
     * Đảm bảo parent gọi setMainController(thisNavBar) sau khi load FXMLLoader.
     */
    public void setMainController(nav_bar__c mainController) {
        this.mainController = mainController;
        System.out.println("[about_us__c] setMainController called; mainController set to: " + mainController);
    }

    /**
     * Optional initialize method để debug khi FXML được load.
     */
    @FXML
    private void initialize() {
        System.out.println("[about_us__c] initialize called. back_button = " + (back_button != null));
    }
}
