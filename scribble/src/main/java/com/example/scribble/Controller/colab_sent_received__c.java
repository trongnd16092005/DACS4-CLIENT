package com.example.scribble.Controller;

import com.example.scribble.UserSession;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;
import java.util.logging.Logger;

public class colab_sent_received__c {
    private static final Logger LOGGER = Logger.getLogger(colab_sent_received__c.class.getName());

    @FXML private VBox colabSentContainer;
    @FXML private VBox colabReceivedContainer;
    @FXML private Label total_sent_record;
    @FXML private Label total_received_record;

    private static final String RMI_URL = "//localhost/CollabService"; // thay đổi nếu cần

    @FXML
    public void initialize() {
        if (!UserSession.getInstance().isLoggedIn()) {
            showAlert(Alert.AlertType.WARNING, "Login Required", "Please log in to view requests.");
            return;
        }

        // Set UI styles (giữ nguyên)
        colabSentContainer.setStyle("-fx-background-color: #005D4D;");
        colabSentContainer.setAlignment(Pos.TOP_CENTER);
        colabSentContainer.setPrefHeight(289.0);
        colabSentContainer.setPrefWidth(307.0);
        colabSentContainer.setSpacing(10.0);
        colabReceivedContainer.setStyle("-fx-background-color: #005D4D;");
        colabReceivedContainer.setAlignment(Pos.TOP_CENTER);
        colabReceivedContainer.setPrefHeight(289.0);
        colabReceivedContainer.setPrefWidth(307.0);
        colabReceivedContainer.setSpacing(10.0);

        // Load via RMI
        loadSentRequests();
        loadReceivedRequests();
    }

    // --- Helpers to call RMI service (reflection friendly) ---
    private Object lookupRemote() throws Exception {
        Object remoteObj = Naming.lookup(RMI_URL);
        if (!(remoteObj instanceof Remote)) throw new RemoteException("Lookup returned non-Remote object");
        return remoteObj;
    }

    private Method findMethodByNames(Object remote, String... names) {
        for (Method m : remote.getClass().getMethods()) {
            for (String n : names) {
                if (m.getName().equalsIgnoreCase(n)) return m;
            }
        }
        return null;
    }

    // Try to coerce an item (DTO or String) into the string format we need.
    // If DTO, try getters (see list below). If String, assume it's already pipe-separated.
    private String itemToResultLine(Object item) throws Exception {
        if (item == null) return null;
        if (item instanceof String) return ((String) item).trim();
        // assume DTO object
        Class<?> c = item.getClass();
        String inviteId = tryGetAsString(c, item, "getInviteId","getId");
        String bookId = tryGetAsString(c, item, "getBookId","getBook_id");
        String inviterId = tryGetAsString(c, item, "getInviterId","getInviter_id","getUserId");
        String inviteeEmail = tryGetAsString(c, item, "getInviteeEmail","getInvitee_email","getInvitee");
        String status = tryGetAsString(c, item, "getStatus");
        String message = tryGetAsString(c, item, "getMessage");
        String title = tryGetAsString(c, item, "getTitle","getBookTitle");
        String coverPhoto = tryGetAsString(c, item, "getCoverPhoto","getCover","getCover_photo","getImage");
        String username = tryGetAsString(c, item, "getUsername","getUser","getAuthor");
        String email = tryGetAsString(c, item, "getEmail");
        String profilePicture = tryGetAsString(c, item, "getProfilePicture","getProfile_picture","getAvatar");

        List<String> missing = new ArrayList<>();
        if (inviteId == null) missing.add("inviteId");
        if (bookId == null) missing.add("bookId");
        if (inviterId == null) missing.add("inviterId");
        if (inviteeEmail == null) missing.add("inviteeEmail");
        if (status == null) missing.add("status");
        if (title == null) missing.add("title");
        // message/cover/username/email/profilePicture can be optional; fill defaults if null
        if (!missing.isEmpty()) {
            throw new IllegalStateException("DTO missing getters: " + String.join(", ", missing) + " on class " + c.getName());
        }

        if (message == null) message = "";
        if (coverPhoto == null) coverPhoto = "hollow_rectangle.png";
        if (username == null) username = "";
        if (email == null) email = "";
        if (profilePicture == null) profilePicture = "";

        return String.join("|", Arrays.asList(inviteId, bookId, inviterId, inviteeEmail, status, message, title, coverPhoto, username, email, profilePicture));
    }

    private static String tryGetAsString(Class<?> cls, Object instance, String... getters) {
        for (String g : getters) {
            try {
                Method m = cls.getMethod(g);
                Object val = m.invoke(instance);
                if (val != null) return String.valueOf(val);
            } catch (NoSuchMethodException ns) {
                // ignore
            } catch (Exception ex) {
                // ignore runtime reflection errors per-field
            }
        }
        return null;
    }

    // --- Loading lists via RMI ---
    private void loadSentRequests() {
        int currentUserId = UserSession.getInstance().getCurrentUserId();
        List<String> results = new ArrayList<>();
        try {
            Object remote = lookupRemote();
            // candidate method names: listSentInvites, getSentInvites, fetchSentInvites
            Method m = findMethodByNames(remote, "listSentInvites","getSentInvites","fetchSentInvites");
            if (m == null) {
                showAlert(Alert.AlertType.ERROR, "RMI Error", "No suitable remote method for listSentInvites found on CollabService.");
                return;
            }
            Object out;
            // try different param variants
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 2 && params[0]==int.class && params[1]==int.class) {
                out = m.invoke(remote, currentUserId, 100);
            } else if (params.length == 1 && params[0]==int.class) {
                out = m.invoke(remote, currentUserId);
            } else if (params.length==0) {
                out = m.invoke(remote);
            } else {
                out = m.invoke(remote, currentUserId);
            }

            if (out instanceof List) {
                for (Object item : (List<?>) out) {
                    try {
                        String line = itemToResultLine(item);
                        if (line != null) results.add(line);
                    } catch (IllegalStateException ise) {
                        showAlert(Alert.AlertType.ERROR, "DTO Getter Missing", ise.getMessage());
                        return;
                    }
                }
            } else if (out instanceof String[]) {
                for (String s : (String[]) out) results.add(s);
            } else if (out instanceof String) {
                for (String s : ((String) out).split("\\r?\\n")) if (!s.trim().isEmpty()) results.add(s.trim());
            } else {
                showAlert(Alert.AlertType.ERROR, "RMI Return Type", "Unsupported return type for listSentInvites: " + out.getClass().getName());
                return;
            }

            // populate UI from results (same UI as DB version)
            populateSentUI(results);
            total_sent_record.setText("(" + results.size() + ")");

        } catch (NotBoundException nb) {
            showAlert(Alert.AlertType.ERROR, "RMI Not Bound", nb.getMessage());
        } catch (RemoteException re) {
            showAlert(Alert.AlertType.ERROR, "RMI Error", re.getMessage());
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
        }
    }

    private void loadReceivedRequests() {
        int currentUserId = UserSession.getInstance().getCurrentUserId();
        List<String> results = new ArrayList<>();
        try {
            Object remote = lookupRemote();
            Method m = findMethodByNames(remote, "listReceivedInvites","getReceivedInvites","fetchReceivedInvites");
            if (m == null) {
                showAlert(Alert.AlertType.ERROR, "RMI Error", "No suitable remote method for listReceivedInvites found on CollabService.");
                return;
            }
            Object out;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 2 && params[0]==int.class && params[1]==int.class) {
                out = m.invoke(remote, currentUserId, 100);
            } else if (params.length == 1 && params[0]==int.class) {
                out = m.invoke(remote, currentUserId);
            } else {
                out = m.invoke(remote);
            }

            if (out instanceof List) {
                for (Object item : (List<?>) out) {
                    try {
                        String line = itemToResultLine(item);
                        if (line != null) results.add(line);
                    } catch (IllegalStateException ise) {
                        showAlert(Alert.AlertType.ERROR, "DTO Getter Missing", ise.getMessage());
                        return;
                    }
                }
            } else if (out instanceof String[]) {
                for (String s : (String[]) out) results.add(s);
            } else if (out instanceof String) {
                for (String s : ((String) out).split("\\r?\\n")) if (!s.trim().isEmpty()) results.add(s.trim());
            } else {
                showAlert(Alert.AlertType.ERROR, "RMI Return Type", "Unsupported return type for listReceivedInvites: " + out.getClass().getName());
                return;
            }

            populateReceivedUI(results);
            total_received_record.setText("(" + results.size() + ")");

        } catch (NotBoundException nb) {
            showAlert(Alert.AlertType.ERROR, "RMI Not Bound", nb.getMessage());
        } catch (RemoteException re) {
            showAlert(Alert.AlertType.ERROR, "RMI Error", re.getMessage());
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
        }
    }

    // --- UI population logic: reuse existing layout code but data-driven from 'result lines' ---
    private void populateSentUI(List<String> results) {
        colabSentContainer.getChildren().clear();
        if (results.isEmpty()) {
            colabSentContainer.getChildren().add(new Label("No sent requests found."));
            return;
        }
        for (String line : results) {
            // expected format:
            // inviteId|bookId|inviterId|inviteeEmail|status|message|title|coverPhoto|username|email|profilePicture
            String[] parts = line.split("\\|", -1);
            int inviteId = Integer.parseInt(parts[0]);
            String title = parts.length>6 && !parts[6].isEmpty() ? parts[6] : "Unknown Book";
            String inviteeEmail = parts.length>3 ? parts[3] : "";
            String status = parts.length>4 ? parts[4] : "";
            String message = parts.length>5 ? parts[5] : "";
            String coverPath = parts.length>7 ? parts[7] : null;

            HBox requestHBox = new HBox();
            requestHBox.setAlignment(Pos.CENTER);
            requestHBox.setStyle("-fx-background-color: #F28888; -fx-background-radius: 5; -fx-border-color: #fff; -fx-border-radius: 5; -fx-padding: 5;");
            requestHBox.setPrefSize(270, 105);

            Region spacer = new Region();
            spacer.setPrefWidth(26.0);
            spacer.setPrefHeight(104.0);

            ImageView bookCover = new ImageView();
            bookCover.setFitHeight(76);
            bookCover.setFitWidth(50);
            bookCover.setPreserveRatio(true);
            bookCover.setPickOnBounds(true);
            try {
                bookCover.setImage(loadBookCoverImage(coverPath));
            } catch (Exception ignored) {}

            HBox.setMargin(bookCover, new Insets(5,5,5,5));

            VBox details = new VBox(5);
            details.setPrefHeight(76.0);
            details.setPrefWidth(141.0);
            details.setPadding(new Insets(0,10,0,10));
            HBox.setMargin(details, new Insets(5,5,5,5));
            HBox.setHgrow(details, javafx.scene.layout.Priority.ALWAYS);

            Label titleLabel = new Label(title);
            titleLabel.setTextFill(javafx.scene.paint.Color.WHITE);
            titleLabel.setWrapText(true);
            titleLabel.setMaxWidth(122);
            titleLabel.setPrefHeight(37.0);
            titleLabel.setFont(Font.font("System", FontWeight.BOLD, 12.0));

            Label emailLabel = new Label("To: " + inviteeEmail);
            emailLabel.setTextFill(javafx.scene.paint.Color.WHITE);
            emailLabel.setStyle("-fx-font-size: 9;");

            Label statusLabel = new Label("Status: " + status);
            statusLabel.setTextFill(javafx.scene.paint.Color.WHITE);
            statusLabel.setStyle("-fx-font-size: 9;");

            Button editButton = new Button("Edit Message");
            editButton.setStyle("-fx-background-color: #D9D9D9; -fx-border-radius: 5; -fx-font-size: 9;");
            editButton.setPrefSize(80, 18);
            editButton.setOnAction(e -> handleEditMessage(inviteId, message));

            details.getChildren().addAll(titleLabel, emailLabel, statusLabel, editButton);

            VBox deleteButtonBox = new VBox();
            deleteButtonBox.setAlignment(Pos.TOP_RIGHT);
            deleteButtonBox.setPrefHeight(104.0);
            deleteButtonBox.setPrefWidth(22.0);
            deleteButtonBox.setPadding(new Insets(5.0, 0, 0, 0));

            Button deleteButton = new Button();
            deleteButton.setId("delete_record");
            deleteButton.setPrefHeight(18.0);
            deleteButton.setPrefWidth(18.0);
            deleteButton.setStyle("-fx-background-color: #F82020; -fx-background-radius: 50;");
            ImageView deleteIcon = new ImageView(new Image(getClass().getResource("/images/icons/cross.png").toExternalForm()));
            deleteIcon.setFitHeight(15.0);
            deleteIcon.setFitWidth(15.0);
            deleteIcon.setPickOnBounds(true);
            deleteIcon.setPreserveRatio(true);
            deleteButton.setGraphic(deleteIcon);
            deleteButton.setUserData(inviteId);
            deleteButton.setOnAction(e -> remoteDeleteRecord(inviteId));

            deleteButtonBox.getChildren().add(deleteButton);

            requestHBox.getChildren().addAll(spacer, bookCover, details, deleteButtonBox);
            colabSentContainer.getChildren().add(requestHBox);
        }
    }
    private void handleEditMessage(int inviteId, String currentMessage) {
        // Delegate to the RMI implementation which shows the edit popup and performs remote update
        handleEditMessageRemote(inviteId, currentMessage);
    }

    private void populateReceivedUI(List<String> results) {
        colabReceivedContainer.getChildren().clear();
        if (results.isEmpty()) {
            colabReceivedContainer.getChildren().add(new Label("No received requests found."));
            return;
        }
        int currentUserId = UserSession.getInstance().getCurrentUserId();
        for (String line : results) {
            String[] parts = line.split("\\|", -1);
            int inviteId = Integer.parseInt(parts[0]);
            int bookId = parts.length>1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : -1;
            int inviterId = parts.length>2 && !parts[2].isEmpty() ? Integer.parseInt(parts[2]) : -1;
            String status = parts.length>4 ? parts[4] : "";
            String username = parts.length>8 ? parts[8] : "";
            String title = parts.length>6 && !parts[6].isEmpty() ? parts[6] : "Unknown Book";
            String coverPath = parts.length>7 ? parts[7] : null;

            HBox requestHBox = new HBox();
            requestHBox.setAlignment(Pos.CENTER);
            requestHBox.setStyle("-fx-background-color: #F28888; -fx-background-radius: 5; -fx-border-color: #fff; -fx-border-radius: 5; -fx-padding: 5;");
            requestHBox.setPrefSize(270, 105);

            Region spacer = new Region();
            spacer.setPrefWidth(26.0);
            spacer.setPrefHeight(104.0);

            ImageView bookCover = new ImageView();
            bookCover.setFitHeight(76);
            bookCover.setFitWidth(50);
            bookCover.setPreserveRatio(true);
            bookCover.setPickOnBounds(true);
            try { bookCover.setImage(loadBookCoverImage(coverPath)); } catch (Exception ignored) {}
            HBox.setMargin(bookCover, new Insets(5,5,5,5));

            VBox details = new VBox(5);
            details.setPrefHeight(76.0);
            details.setPrefWidth(141.0);
            details.setPadding(new Insets(0,10,0,10));
            HBox.setMargin(details, new Insets(5,5,5,5));
            HBox.setHgrow(details, javafx.scene.layout.Priority.ALWAYS);

            Label titleLabel = new Label(title);
            titleLabel.setTextFill(javafx.scene.paint.Color.WHITE);
            titleLabel.setWrapText(true);
            titleLabel.setMaxWidth(122);
            titleLabel.setPrefHeight(37.0);
            titleLabel.setFont(Font.font("System", FontWeight.BOLD, 12.0));

            Label userLabel = new Label("From: " + username);
            userLabel.setTextFill(javafx.scene.paint.Color.WHITE);
            userLabel.setStyle("-fx-font-size: 9;");

            Button viewRequestButton = new Button("View Request");
            viewRequestButton.setStyle("-fx-border-radius: 5; -fx-border-color: #fff; -fx-background-color: " +
                    (status.equals("Pending") ? "#D9D9D9" : status.equals("Accepted") ? "#4CAF50" : "#F44336") + "; -fx-font-size: 8;");
            viewRequestButton.setPrefSize(80, 18);

            Button editStatusButton = new Button("Edit Status");
            editStatusButton.setStyle("-fx-background-color: #D9D9D9; -fx-border-radius: 5; -fx-font-size: 8;");
            editStatusButton.setPrefSize(80, 18);

            // enable only if current user is owner -> need remote check: use returned data (we assume server filtered owner rights)
            boolean isOwner = true; // assume server returned only requests where current user can act; otherwise server should provide owner flag
            if (isOwner) {
                viewRequestButton.setOnAction(e -> handleOpenRequestRemote(inviteId));
                editStatusButton.setOnAction(e -> handleEditStatusRemote(inviteId, status, bookId, inviterId));
            } else {
                viewRequestButton.setDisable(true);
                editStatusButton.setDisable(true);
            }

            details.getChildren().addAll(titleLabel, userLabel, viewRequestButton, editStatusButton);

            VBox deleteButtonBox = new VBox();
            deleteButtonBox.setAlignment(Pos.TOP_RIGHT);
            deleteButtonBox.setPrefHeight(104.0);
            deleteButtonBox.setPrefWidth(22.0);
            deleteButtonBox.setPadding(new Insets(5.0, 0, 0, 0));

            Button deleteButton = new Button();
            deleteButton.setId("delete_record");
            deleteButton.setPrefHeight(18.0);
            deleteButton.setPrefWidth(18.0);
            deleteButton.setStyle("-fx-background-color: #F82020; -fx-background-radius: 50;");
            ImageView deleteIcon = new ImageView(new Image(getClass().getResource("/images/icons/cross.png").toExternalForm()));
            deleteIcon.setFitHeight(15.0);
            deleteIcon.setFitWidth(15.0);
            deleteIcon.setPickOnBounds(true);
            deleteIcon.setPreserveRatio(true);
            deleteButton.setGraphic(deleteIcon);
            deleteButton.setUserData(inviteId);
            deleteButton.setOnAction(e -> remoteDeleteRecord(inviteId));

            deleteButtonBox.getChildren().add(deleteButton);

            requestHBox.getChildren().addAll(spacer, bookCover, details, deleteButtonBox);
            colabReceivedContainer.getChildren().add(requestHBox);
        }
    }

    // --- Remote operations for delete / update status / view details ---
    private void remoteDeleteRecord(int inviteId) {
        try {
            Object remote = lookupRemote();
            Method m = findMethodByNames(remote, "deleteInvite","removeInvite","deleteCollabInvite");
            if (m == null) {
                showAlert(Alert.AlertType.ERROR, "RMI Error", "No suitable deleteInvite method found on CollabService.");
                return;
            }
            Object out = m.invoke(remote, inviteId);
            boolean ok = (out instanceof Boolean) ? (Boolean) out : "ok".equalsIgnoreCase(String.valueOf(out));
            if (ok) {
                loadSentRequests();
                loadReceivedRequests();
                showAlert(Alert.AlertType.INFORMATION, "Success", "Collaboration request deleted successfully.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "Server reported failure deleting invite.");
            }
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "RMI Error", ex.getMessage());
        }
    }

    private void handleOpenRequestRemote(int inviteId) {
        try {
            Object remote = lookupRemote();
            Method m = findMethodByNames(remote, "getInviteDetails","getCollabInvite","fetchInvite");
            if (m == null) {
                showAlert(Alert.AlertType.ERROR, "RMI Error", "No suitable getInviteDetails method found on CollabService.");
                return;
            }
            Object out = m.invoke(remote, inviteId);
            String line;
            if (out instanceof String) line = (String) out;
            else if (out instanceof List && !((List<?>) out).isEmpty() && ((List<?>) out).get(0) instanceof String) line = (String) ((List<?>) out).get(0);
            else line = itemToResultLine(out);
            // parse and show popup similar to DB version (extract username,email,profilePicture,message)
            String[] parts = line.split("\\|", -1);
            String message = parts.length>5 ? parts[5] : "No message provided";
            String username = parts.length>8 ? parts[8] : "";
            String email = parts.length>9 ? parts[9] : "";
            String profilePicture = parts.length>10 ? parts[10] : null;

            Stage popupStage = new Stage();
            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.setTitle("Collaboration Request Details");
            VBox vbox = new VBox(10);
            vbox.setAlignment(Pos.CENTER);
            Label usernameLabel = new Label("Username: " + username);
            usernameLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
            Label emailLabel = new Label("Email: " + email);
            emailLabel.setStyle("-fx-font-size: 12;");
            ImageView profileImageView = new ImageView();
            profileImageView.setFitWidth(100);
            profileImageView.setFitHeight(100);
            profileImageView.setImage(loadImage(profilePicture != null && !profilePicture.isEmpty() ?
                    "/images/profiles/" + profilePicture : "/images/profiles/demo_profile.png"));
            Label messageLabel = new Label("Message: " + message);
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(300);
            messageLabel.setStyle("-fx-font-size: 12;");
            Button closeButton = new Button("Close");
            closeButton.setStyle("-fx-background-color: #D9D9D9; -fx-border-radius: 5;");
            closeButton.setOnAction(e -> popupStage.close());
            vbox.getChildren().addAll(profileImageView, usernameLabel, emailLabel, messageLabel, closeButton);
            vbox.setPadding(new Insets(10));
            Scene scene = new Scene(vbox, 400, 300);
            popupStage.setScene(scene);
            popupStage.show();

        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "RMI Error", ex.getMessage());
        }
    }

    private void handleEditMessageRemote(int inviteId, String currentMessage) {
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Edit Collaboration Request Message");

        VBox vbox = new VBox(10);
        vbox.setAlignment(Pos.CENTER);
        Label messageLabel = new Label("Edit Message:");
        messageLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        TextArea messageArea = new TextArea(currentMessage);
        messageArea.setWrapText(true);
        messageArea.setPrefSize(300, 100);
        Button saveButton = new Button("Save");
        saveButton.setStyle("-fx-background-color: #4CAF50; -fx-border-radius: 5; -fx-text-fill: white;");
        saveButton.setOnAction(e -> {
            try {
                Object remote = lookupRemote();
                Method m = findMethodByNames(remote, "updateInviteMessage","editInviteMessage","setInviteMessage");
                if (m == null) {
                    showAlert(Alert.AlertType.ERROR, "RMI Error", "No suitable updateInviteMessage method found on CollabService.");
                    return;
                }
                Object out = m.invoke(remote, inviteId, messageArea.getText());
                boolean ok = (out instanceof Boolean) ? (Boolean) out : "ok".equalsIgnoreCase(String.valueOf(out));
                if (ok) {
                    loadSentRequests();
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Message updated successfully!");
                    popupStage.close();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Error", "Server reported failure updating message.");
                }
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "RMI Error", ex.getMessage());
            }
        });
        vbox.getChildren().addAll(messageLabel, messageArea, saveButton);
        vbox.setPadding(new Insets(10));
        Scene scene = new Scene(vbox, 350, 200);
        popupStage.setScene(scene);
        popupStage.show();
    }

    private void handleEditStatusRemote(int inviteId, String currentStatus, int bookId, int inviterId) {
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Edit Collaboration Request Status");

        VBox vbox = new VBox(10);
        vbox.setAlignment(Pos.CENTER);
        Label statusLabel = new Label("Edit Status:");
        statusLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        ChoiceBox<String> statusChoiceBox = new ChoiceBox<>();
        statusChoiceBox.getItems().addAll("Pending", "Accepted", "Declined");
        statusChoiceBox.setValue(currentStatus);
        Button saveButton = new Button("Save");
        saveButton.setStyle("-fx-background-color: #4CAF50; -fx-border-radius: 5; -fx-text-fill: white;");
        saveButton.setOnAction(e -> {
            String newStatus = statusChoiceBox.getValue();
            // If switching from Accepted -> Declined/Pending need to remove co-author via remote
            try {
                Object remote = lookupRemote();
                Method mUpdate = findMethodByNames(remote, "updateInviteStatus","setInviteStatus");
                if (mUpdate == null) {
                    showAlert(Alert.AlertType.ERROR, "RMI Error", "No suitable updateInviteStatus method found on CollabService.");
                    return;
                }
                Object out = mUpdate.invoke(remote, inviteId, newStatus);
                boolean ok = (out instanceof Boolean) ? (Boolean) out : "ok".equalsIgnoreCase(String.valueOf(out));
                if (!ok) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Server reported failure updating status.");
                    return;
                }
                if (currentStatus.equals("Accepted") && (newStatus.equals("Declined") || newStatus.equals("Pending"))) {
                    Method mRemove = findMethodByNames(remote, "removeCoAuthor","deleteCoAuthor");
                    if (mRemove != null) mRemove.invoke(remote, bookId, inviterId);
                } else if (!currentStatus.equals("Accepted") && newStatus.equals("Accepted")) {
                    Method mAdd = findMethodByNames(remote, "addCoAuthor","insertCoAuthor");
                    if (mAdd != null) mAdd.invoke(remote, bookId, inviterId);
                }
                loadSentRequests();
                loadReceivedRequests();
                showAlert(Alert.AlertType.INFORMATION, "Success", "Status updated to " + newStatus);
                popupStage.close();
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "RMI Error", ex.getMessage());
            }
        });
        vbox.getChildren().addAll(statusLabel, statusChoiceBox, saveButton);
        vbox.setPadding(new Insets(10));
        Scene scene = new Scene(vbox, 300, 150);
        popupStage.setScene(scene);
        popupStage.show();
    }

    // --- Utilities for images / alerts (reuse original) ---
    private Image loadBookCoverImage(String coverPath) {
        if (coverPath != null && !coverPath.isEmpty()) {
            try {
                java.io.File uploadFile = new java.io.File("Uploads/book_covers/" + coverPath);
                if (uploadFile.exists()) {
                    Image image = new Image("file:" + uploadFile.getAbsolutePath(), true);
                    if (!image.isError()) return image;
                } else {
                    java.net.URL resource = getClass().getResource("/images/book_covers/" + coverPath);
                    if (resource != null) {
                        Image image = new Image(resource.toExternalForm(), true);
                        if (!image.isError()) return image;
                    }
                }
            } catch (Exception e) { LOGGER.warning("Failed to load book cover: " + e.getMessage()); }
        }
        return new Image(getClass().getResource("/images/book_covers/hollow_rectangle.png").toExternalForm());
    }

    private Image loadImage(String path) {
        try {
            Image image = new Image(getClass().getResource(path).toExternalForm());
            if (image.isError()) return new Image(getClass().getResource("/images/profiles/demo_profile.png").toExternalForm());
            return image;
        } catch (Exception e) {
            return new Image(getClass().getResource("/images/profiles/demo_profile.png").toExternalForm());
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
