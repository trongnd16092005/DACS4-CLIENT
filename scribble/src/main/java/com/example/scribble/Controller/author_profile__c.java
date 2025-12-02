package com.example.scribble.Controller;

import com.example.scribble.AppState_c;
import com.example.scribble.nav_bar__c;

import com.example.scribble.common.api.IBookService;
import com.example.scribble.common.dto.AuthorDTO;
import com.example.scribble.common.dto.BookDTO;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for author profile using RMI only.
 * - Assumes IBookService exposes getAuthorById(int) and listBooksByAuthor(int).
 * - All network calls run off the JavaFX thread; UI updated via Platform.runLater.
 */
public class author_profile__c {
    private static final Logger LOGGER = Logger.getLogger(author_profile__c.class.getName());

    // FXML injections
    @FXML private Button back_button;
    @FXML private ImageView cover_photo;
    @FXML private Label author_name;
    @FXML private Label author_email;
    @FXML private Label joined_at;
    @FXML private Label total_number_of_author_works;
    @FXML private VBox all_button_work;
    @FXML private VBox vbox_container;
    @FXML private HBox book_card_container;
    @FXML private HBox book_card_hbox;
    @FXML private ImageView book_cover;
    @FXML private VBox label_vbox;
    @FXML private Label book_name;
    @FXML private Label post_on_date;

    // state
    private int authorId = 0;
    private int bookId = 0;
    private nav_bar__c mainController;

    // Use DTOs from common for internal representation
    private AuthorDTO author;             // com.example.scribble.common.dto.AuthorDTO
    private List<BookDTO> books;          // com.example.scribble.common.dto.BookDTO

    // RMI config + cached service
    private static final String RMI_HOST = "localhost";
    private static final int RMI_PORT = 1099;
    private static final String BOOK_SERVICE_BINDING = "BookService";
    private volatile IBookService bookService = null;
    private final AtomicBoolean rmiInitAttempted = new AtomicBoolean(false);

    /* ---------------------- setters ---------------------- */

    /**
     * Set author id and begin (non-blocking) load of author + books via RMI.
     */
    public void setAuthorId(int authorId) {
        this.authorId = authorId;
        LOGGER.info("setAuthorId called with authorId: " + authorId);
        if (authorId > 0) {
            // offload network work to background
            loadAuthorAndBooksAsync();
        } else {
            LOGGER.warning("Invalid authorId: " + authorId + ", skipping data fetch");
            // show placeholder UI
            if (author_name != null) author_name.setText("Unknown Author");
            if (author_email != null) author_email.setText("N/A");
            if (joined_at != null) joined_at.setText("N/A");
            if (total_number_of_author_works != null) total_number_of_author_works.setText("(0)");
        }
    }

    public void setBookId(int bookId) {
        this.bookId = bookId;
        try {
            AppState_c.getInstance().setPreviousFXML(String.valueOf(bookId));
        } catch (Exception e) {
            LOGGER.fine("AppState_c interaction skipped or failed: " + e.getMessage());
        }
        LOGGER.info("setBookId called with bookId: " + bookId);
    }

    public void setMainController(nav_bar__c mainController) {
        this.mainController = mainController;
        LOGGER.info("setMainController called with mainController: " + (mainController != null ? "non-null" : "null"));
    }

    /* ---------------------- initialize ---------------------- */

    @FXML
    public void initialize() {
        LOGGER.info("Initializing author_profile__c. authorId=" + authorId + ", bookId=" + bookId);
        logFXMLInjections();
        // initial placeholders
        if (author_name != null) author_name.setText("Loading...");
        if (total_number_of_author_works != null) total_number_of_author_works.setText("(0)");
    }

    private void logFXMLInjections() {
        LOGGER.info("FXML Injection Status:");
        LOGGER.info("back_button: " + (back_button != null ? "injected" : "null"));
        LOGGER.info("cover_photo: " + (cover_photo != null ? "injected" : "null"));
        LOGGER.info("author_name: " + (author_name != null ? "injected" : "null"));
        LOGGER.info("author_email: " + (author_email != null ? "injected" : "null"));
        LOGGER.info("joined_at: " + (joined_at != null ? "injected" : "null"));
        LOGGER.info("total_number_of_author_works: " + (total_number_of_author_works != null ? "injected" : "null"));
        LOGGER.info("book_card_container: " + (book_card_container != null ? "injected" : "null"));
    }

    /* ---------------------- navigation ---------------------- */

    @FXML
    private void handle_back_button(ActionEvent event) {
        LOGGER.info("Back button clicked for authorId: " + authorId + ", bookId: " + bookId);
        try {
            int targetBookId = bookId > 0 ? bookId : 1;
            LOGGER.info("Navigating back to read_book.fxml with bookId: " + targetBookId);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/scribble/read_book.fxml"));
            Parent content = loader.load();
            com.example.scribble.read_book__c controller = loader.getController();
            controller.setBookId(targetBookId);
            controller.setMainController(mainController);

            if (mainController != null) {
                mainController.getCenterPane().getChildren().setAll(content);
                LOGGER.info("Navigated back via mainController with bookId: " + targetBookId);
            } else {
                LOGGER.warning("mainController is null, using direct navigation");
                Scene scene = new Scene(content);
                Stage stage = (Stage) back_button.getScene().getWindow();
                stage.setScene(scene);
                LOGGER.info("Direct navigation done with bookId: " + targetBookId);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to navigate back to read_book.fxml: {0}", e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Navigation Error", "Failed to navigate back.");
        }
    }

    /* ---------------------- RMI helpers & async fetching ---------------------- */

    /**
     * Lazy lookup for IBookService. Only attempts lookup once per controller life
     * (retries require restarting controller or resetting rmiInitAttempted).
     */
    private synchronized void ensureBookServiceLookup() throws Exception {
        if (bookService != null) return;
        if (rmiInitAttempted.get()) {
            // already tried and failed earlier
            throw new IllegalStateException("RMI init was attempted and failed previously");
        }
        rmiInitAttempted.set(true);
        String url = String.format("rmi://%s:%d/%s", RMI_HOST, RMI_PORT, BOOK_SERVICE_BINDING);
        Object looked = Naming.lookup(url);
        if (looked instanceof IBookService) {
            bookService = (IBookService) looked;
            LOGGER.info("IBookService obtained from " + url);
        } else {
            throw new IllegalStateException("RMI lookup returned wrong type for: " + url);
        }
    }

    /**
     * Load author + books in background thread, then update UI on FX thread.
     */
    private void loadAuthorAndBooksAsync() {
        // set temporary UI state
        Platform.runLater(() -> {
            if (author_name != null) author_name.setText("Loading...");
            if (author_email != null) author_email.setText("");
            if (joined_at != null) joined_at.setText("");
            if (total_number_of_author_works != null) total_number_of_author_works.setText("(0)");
            if (book_card_container != null) book_card_container.getChildren().clear();
        });

        new Thread(() -> {
            try {
                // attempt RMI lookup (throws on failure)
                ensureBookServiceLookup();

                // RMI calls
                try {
                    AuthorDTO fetchedAuthor = bookService.getAuthorById(authorId);
                    List<BookDTO> fetchedBooks = bookService.listBooksByAuthor(authorId);

                    // Normalize nulls
                    if (fetchedBooks == null) fetchedBooks = new ArrayList<>();

                    // assign to fields
                    this.author = fetchedAuthor;
                    this.books = fetchedBooks;

                    // update UI
                    Platform.runLater(() -> {
                        displayAuthorData();
                        displayAuthorBooks();
                    });

                } catch (RemoteException re) {
                    LOGGER.log(Level.SEVERE, "RemoteException during data fetch: {0}", re.getMessage());
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.ERROR, "Service Error", "Failed to fetch data from server: " + re.getMessage());
                        // set fallback UI state
                        displayAuthorData();
                        displayAuthorBooks();
                    });
                }
            } catch (Exception e) {
                // Lookup failed
                String msg = "Cannot initialize BookService via RMI: " + e.getMessage();
                LOGGER.log(Level.WARNING, msg, e);
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Service Error", msg);
                    // ensure UI shows something sensible
                    author = null;
                    books = new ArrayList<>();
                    displayAuthorData();
                    displayAuthorBooks();
                });
            }
        }, "author-profile-loader").start();
    }

    /* ---------------------- display ---------------------- */

    private void displayAuthorData() {
        if (author == null) {
            LOGGER.fine("No author data to display for authorId: " + authorId);
            if (author_name != null) author_name.setText("Unknown Author");
            if (author_email != null) author_email.setText("N/A");
            if (joined_at != null) joined_at.setText("N/A");
            return;
        }

        if (author_name != null) author_name.setText(Objects.toString(author.getUsername(), "Unknown"));
        if (author_email != null) author_email.setText(Objects.toString(author.getEmail(), "N/A"));
        if (joined_at != null) {
            String created = "N/A";
            if (author.getCreatedAt() != null) {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM d, yyyy");
                created = fmt.format((TemporalAccessor) author.getCreatedAt());
            }
            joined_at.setText("Member since " + created);
        }

        if (cover_photo != null) {
            try {
                String profilePic = author.getProfilePicture();
                if (profilePic != null && !profilePic.isEmpty()) {
                    cover_photo.setImage(new Image(getClass().getResource("/images/profiles/" + profilePic).toExternalForm()));
                } else {
                    cover_photo.setImage(new Image(getClass().getResource("/images/profiles/hollow_circle.png").toExternalForm()));
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load profile picture: {0}", e.getMessage());
                try {
                    cover_photo.setImage(new Image(getClass().getResource("/images/profiles/hollow_circle.png").toExternalForm()));
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "Fallback profile picture also failed: {0}", ex.getMessage());
                }
            }
        }
    }

    private void displayAuthorBooks() {
        if (books == null) books = new ArrayList<>();
        if (total_number_of_author_works != null) {
            total_number_of_author_works.setText("(" + books.size() + ")");
        }

        if (book_card_container == null) {
            LOGGER.severe("book_card_container is null, cannot display books");
            return;
        }

        book_card_container.getChildren().clear();

        if (books.isEmpty()) {
            Label noBooksLabel = new Label("No works found for this author.");
            noBooksLabel.setTextFill(javafx.scene.paint.Color.WHITE);
            book_card_container.getChildren().add(noBooksLabel);
            return;
        }

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        for (BookDTO book : books) {
            try {
                HBox bookHBox = new HBox();
                bookHBox.setAlignment(javafx.geometry.Pos.CENTER);
                bookHBox.setPrefSize(306, 154);
                bookHBox.setMaxSize(306, 154);
                bookHBox.setMinSize(306, 154);
                bookHBox.setStyle("-fx-background-color: #F28888; -fx-background-radius: 5; -fx-border-color: #FFFFFF; -fx-border-radius: 5;");

                ImageView bookCover = new ImageView();
                bookCover.setFitHeight(118.0);
                bookCover.setFitWidth(80.0);
                bookCover.setPickOnBounds(true);
                bookCover.setPreserveRatio(true);
                if (book.getCoverPhoto() != null && !book.getCoverPhoto().isEmpty()) {
                    try {
                        bookCover.setImage(new Image(getClass().getResource("/images/book_covers/" + book.getCoverPhoto()).toExternalForm()));
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to load book cover: {0} - {1}", new Object[]{book.getCoverPhoto(), e.getMessage()});
                        bookCover.setImage(new Image(getClass().getResource("/images/book_covers/hollow_rectangle.png").toExternalForm()));
                    }
                } else {
                    bookCover.setImage(new Image(getClass().getResource("/images/book_covers/hollow_rectangle.png").toExternalForm()));
                }

                VBox labelVBox = new VBox();
                labelVBox.setPrefSize(182, 114);
                labelVBox.setMaxSize(182, 114);
                labelVBox.setMinSize(182, 114);
                labelVBox.setSpacing(5.0);
                labelVBox.setPadding(new javafx.geometry.Insets(0, 10, 0, 10));

                Label bookName = new Label(Objects.toString(book.getTitle(), "Untitled"));
                bookName.setPrefHeight(57.0);
                bookName.setPrefWidth(122.0);
                bookName.setWrapText(true);
                bookName.setTextFill(javafx.scene.paint.Color.WHITE);
                bookName.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 18));

                String postedOn = "N/A";
                if (book.getCreatedAt() != null) postedOn = dateFmt.format(book.getCreatedAt());
                Label postDate = new Label("Posted on " + postedOn);
                postDate.setPrefHeight(42.0);
                postDate.setPrefWidth(160.0);
                postDate.setWrapText(true);
                postDate.setTextFill(javafx.scene.paint.Color.WHITE);
                postDate.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

                labelVBox.getChildren().addAll(bookName, postDate);
                bookHBox.getChildren().addAll(bookCover, labelVBox);
                book_card_container.getChildren().add(bookHBox);

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to render book UI: {0}", e.getMessage());
            }
        }
    }

    /* ---------------------- utilities ---------------------- */

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        // ensure runs on FX thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showAlert(alertType, title, message));
            return;
        }
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
