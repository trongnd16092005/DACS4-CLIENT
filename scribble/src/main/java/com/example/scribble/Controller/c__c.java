package com.example.scribble.Controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

import java.io.IOException;
import java.net.URL;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class c__c {
    private static final Logger LOGGER = Logger.getLogger(c__c.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE d MMMM yyyy");

    @FXML private nav_bar__c mainController;
    @FXML private Button back_button;
    @FXML private Label week_session;
    @FXML private Label week_session1;
    @FXML private Button genre_fantasy;
    @FXML private Button genre_mystery;
    @FXML private Button genre_fiction;
    @FXML private Button genre_horror;
    @FXML private HBox view_result;

    private Button activeButton;
    private String selectedGenre = "Fantasy";
    private int weekIndex = 2; // 0: lastpreviousweek, 1: previousweek, 2: currentweek
    private final String[] weeks = {"lastpreviousweek", "previousweek", "currentweek"};

    @FXML
    public void initialize() {
        if (AppState_c.getInstance().getPreviousFXML() == null) {
            AppState_c.getInstance().setPreviousFXML("/com/example/scribble/contest.fxml");
            LOGGER.info("Set default previous FXML to: /com/example/scribble/contest.fxml");
        }

        week_session1.setText("Select Genre");
        updateWeekSessionLabel();
        view_result.getChildren().clear();
        view_result.getChildren().add(new Text("Select a genre to view results"));

        setActiveButton(genre_fantasy);
        loadWeeklyResults("Fantasy");

        genre_fantasy.setOnAction(this::handle_genre_fantasy);
        genre_mystery.setOnAction(this::handle_genre_mystery);
        genre_fiction.setOnAction(this::handle_genre_fiction);
        genre_horror.setOnAction(this::handle_genre_horror);
    }

    private void updateWeekSessionLabel() {
        LocalDate today = LocalDate.of(2025, 7, 20);
        LocalDate startDate;
        LocalDate endDate;

        switch (weeks[weekIndex]) {
            case "lastpreviousweek":
                startDate = today.minusDays(15);
                endDate = today.minusDays(9);
                break;
            case "previousweek":
                startDate = today.minusDays(8);
                endDate = today.minusDays(2);
                break;
            case "currentweek":
            default:
                startDate = today.minusDays(1);
                endDate = today.plusDays(5);
                break;
        }

        week_session.setText(startDate.format(DATE_FORMATTER) + " - " + endDate.format(DATE_FORMATTER));
    }

    private void setActiveButton(Button selectedButton) {
        for (Button button : new Button[]{genre_fantasy, genre_mystery, genre_fiction, genre_horror}) {
            button.setStyle("-fx-background-radius: 5; -fx-background-color: #F5E0CD;");
            button.setOpacity(1.0);
        }
        activeButton = selectedButton;
        if (activeButton != null) {
            activeButton.setStyle("-fx-background-radius: 5; -fx-background-color: #D3BFA3;");
            activeButton.setOpacity(0.6);
        }
    }

    @FXML
    private void handle_back_button(ActionEvent event) {
        if (mainController == null) {
            LOGGER.severe("Main controller is null, cannot navigate back");
            showErrorAlert("Error", "Navigation failed: main controller is not initialized.");
            return;
        }

        try {
            String previousFXML = AppState_c.getInstance().getPreviousFXML();
            if (previousFXML == null || previousFXML.isEmpty() || previousFXML.equals("/com/example/scribble/c.fxml")) {
                previousFXML = "/com/example/scribble/contest.fxml";
                LOGGER.warning("Invalid previous FXML, defaulting to: " + previousFXML);
            }

            URL fxmlResource = getClass().getResource(previousFXML);
            if (fxmlResource == null) {
                LOGGER.severe("Resource not found: " + previousFXML);
                showErrorAlert("Resource Error", "Previous page resource not found: " + previousFXML);
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlResource);
            Parent root = loader.load();
            Object controller = loader.getController();

            if (controller instanceof c__c) {
                ((c__c) controller).setMainController(mainController);
                LOGGER.info("setMainController called on c__c controller");
            }

            mainController.getCenterPane().getChildren().setAll(root);
            LOGGER.info("Navigated back to " + previousFXML);
        } catch (IOException e) {
            LOGGER.severe("Failed to load FXML: " + e.getMessage());
            showErrorAlert("Navigation Error", "Failed to load previous page: " + e.getMessage());
        }
    }

    private void handleGenreClick(String genre) {
        if (selectedGenre.equals(genre)) {
            weekIndex = (weekIndex + 1) % weeks.length;
        } else {
            selectedGenre = genre;
            weekIndex = 2; // Start with current week
        }
        setActiveButton(getButtonForGenre(genre));
        updateWeekSessionLabel();
        loadWeeklyResults(genre);
    }

    private Button getButtonForGenre(String genre) {
        switch (genre) {
            case "Fantasy": return genre_fantasy;
            case "Thriller Mystery": return genre_mystery;
            case "Youth Fiction": return genre_fiction;
            case "Crime Horror": return genre_horror;
            default: return genre_fantasy;
        }
    }

    @FXML private void handle_genre_fantasy(ActionEvent event) { handleGenreClick("Fantasy"); }
    @FXML private void handle_genre_mystery(ActionEvent event) { handleGenreClick("Thriller Mystery"); }
    @FXML private void handle_genre_fiction(ActionEvent event) { handleGenreClick("Youth Fiction"); }
    @FXML private void handle_genre_horror(ActionEvent event) { handleGenreClick("Crime Horror"); }

    /**
     * CHANGED: Only RMI. No DB fallback.
     * If remote object/method or DTO getters are missing, show alert with details.
     */
    private void loadWeeklyResults(String genre) {
        view_result.getChildren().clear();

        LocalDate today = LocalDate.of(2025, 7, 20);
        LocalDate startDate;
        LocalDate endDate;
        switch (weeks[weekIndex]) {
            case "lastpreviousweek": startDate = today.minusDays(15); endDate = today.minusDays(9); break;
            case "previousweek": startDate = today.minusDays(8); endDate = today.minusDays(2); break;
            case "currentweek":
            default: startDate = today.minusDays(1); endDate = today.plusDays(5); break;
        }
        String startStr = startDate.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endStr = endDate.atTime(23,59,59).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<String> results = new ArrayList<>();

        try {
            Object remoteObj = Naming.lookup("//localhost/ContestService"); // adjust if needed
            if (!(remoteObj instanceof Remote)) {
                showErrorAlert("RMI Error", "Lookup returned non-Remote object: " + remoteObj);
                return;
            }
            Remote remote = (Remote) remoteObj;

            String[] candidateNames = { "getTopEntries", "listTopEntries", "getWeeklyResults", "listContestEntries", "getTopContestEntries" };
            Method chosen = null;
            for (Method m : remote.getClass().getMethods()) {
                for (String cand : candidateNames) {
                    if (m.getName().equalsIgnoreCase(cand)) { chosen = m; break; }
                }
                if (chosen != null) break;
            }

            if (chosen == null) {
                showErrorAlert("RMI Error", "No suitable method found on ContestService. Checked method names: getTopEntries, listTopEntries, getWeeklyResults, listContestEntries, getTopContestEntries");
                return;
            }

            Object invokeResult;
            try {
                Class<?>[] params = chosen.getParameterTypes();
                if (params.length == 4) {
                    invokeResult = chosen.invoke(remote, genre, startStr, endStr, 3);
                } else if (params.length == 3) {
                    invokeResult = chosen.invoke(remote, genre, startStr, endStr);
                } else if (params.length == 2) {
                    try { invokeResult = chosen.invoke(remote, genre, 3); }
                    catch (Exception ex) { invokeResult = chosen.invoke(remote, genre, startStr); }
                } else if (params.length == 1) {
                    invokeResult = chosen.invoke(remote, genre);
                } else {
                    invokeResult = chosen.invoke(remote);
                }
            } catch (IllegalArgumentException ia) {
                showErrorAlert("RMI Parameter Error", "Remote method parameter mismatch: " + ia.getMessage());
                return;
            }

            if (invokeResult == null) {
                showErrorAlert("RMI Error", "Remote method returned null.");
                return;
            }

            if (invokeResult instanceof List) {
                List<?> resList = (List<?>) invokeResult;
                if (resList.isEmpty()) {
                    view_result.getChildren().add(new Text("No entries found for " + genre + " in " + week_session.getText()));
                    return;
                }
                Object first = resList.get(0);
                if (first instanceof String) {
                    for (Object o : resList) results.add((String) o);
                } else {
                    Set<String> missingGetters = new HashSet<>();
                    for (Object item : resList) {
                        Class<?> c = item.getClass();
                        String id = tryGetAsString(c, item, "getEntryId","getId","getEntryID","getEntry_id");
                        String title = tryGetAsString(c, item, "getEntryTitle","getTitle","getEntry_title");
                        String username = tryGetAsString(c, item, "getUsername","getUser","getAuthor");
                        String votes = tryGetAsString(c, item, "getVoteCount","getVotes","getVote_count");
                        String cover = tryGetAsString(c, item, "getCoverPhoto","getCover","getCover_photo","getImage");

                        List<String> thisMissing = new ArrayList<>();
                        if (id == null) thisMissing.add("entry id (e.g. getEntryId|getId)");
                        if (title == null) thisMissing.add("entry title (e.g. getEntryTitle|getTitle)");
                        if (username == null) thisMissing.add("username (e.g. getUsername|getUser)");
                        if (votes == null) thisMissing.add("vote count (e.g. getVoteCount|getVotes)");
                        if (cover == null) thisMissing.add("cover photo (e.g. getCoverPhoto|getCover)");

                        if (!thisMissing.isEmpty()) {
                            missingGetters.add(c.getName() + " missing: " + String.join(", ", thisMissing));
                        } else {
                            String composite = id + "|" + title + "|" + username + "|" + votes + "|" + cover;
                            results.add(composite);
                        }
                    }
                    if (!missingGetters.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Remote DTO objects are missing expected getters:\n");
                        for (String s : missingGetters) sb.append(" - ").append(s).append("\n");
                        sb.append("\nPlease add the missing getters to the DTO class(es) or tell me the actual getter names so I can update the client.");
                        showErrorAlert("DTO Getter Missing", sb.toString());
                        return;
                    }
                }
            } else if (invokeResult instanceof String[]) {
                for (String s : (String[]) invokeResult) if (s != null) results.add(s);
            } else if (invokeResult instanceof String) {
                String s = (String) invokeResult;
                String[] parts = s.split("\\r?\\n");
                for (String p : parts) if (!p.trim().isEmpty()) results.add(p.trim());
            } else {
                showErrorAlert("RMI Return Type", "Unsupported return type from remote method: " + invokeResult.getClass().getName());
                return;
            }

        } catch (NotBoundException nbe) {
            showErrorAlert("RMI Not Bound", "ContestService is not bound in RMI registry: " + nbe.getMessage());
            return;
        } catch (RemoteException re) {
            showErrorAlert("RMI RemoteException", "Remote exception during lookup/invoke: " + re.getMessage());
            return;
        } catch (Exception ex) {
            showErrorAlert("RMI Error", "General exception during RMI: " + ex.getMessage());
            return;
        }

        if (results.isEmpty()) {
            view_result.getChildren().add(new Text("No entries found for " + genre + " in " + week_session.getText()));
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/scribble/weekly_results.fxml"));
            Parent root = loader.load();
            weekly_results__c controller = loader.getController();
            controller.setResults(results);
            view_result.getChildren().setAll(root);
            LOGGER.info("Loaded weekly_results.fxml for genre: " + genre + ", week: " + weeks[weekIndex]);
        } catch (IOException e) {
            showErrorAlert("Load Error", "Failed to load weekly results: " + e.getMessage());
        }
    }

    // helper tries a list of getter names and returns first non-null string value
    private static String tryGetAsString(Class<?> cls, Object instance, String... getters) {
        for (String g : getters) {
            try {
                Method m = cls.getMethod(g);
                Object val = m.invoke(instance);
                if (val != null) return String.valueOf(val);
            } catch (NoSuchMethodException ns) {
                // ignore
            } catch (Exception ex) {
                // ignore other reflection errors per-item
            }
        }
        return null;
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void setMainController(nav_bar__c mainController) {
        this.mainController = mainController;
        LOGGER.info("Set mainController in c__c: " + mainController);
    }
}
