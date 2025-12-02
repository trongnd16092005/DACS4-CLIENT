package com.example.scribble.Controller;

import com.example.scribble.common.api.IBookService;
try {
BookDTO book = bookService.getBookDetails(bookId);
if (book != null) {
        if (bookTitleLabel != null) bookTitleLabel.setText(book.getTitle() != null ? book.getTitle() : "Unknown");
        if (views != null) views.setText(String.valueOf(book.getViewCount()));
        if (reads != null) reads.setText(String.valueOf(book.getTotalReads()));
        if (status != null) status.setText(book.getStatus());
        if (genre != null) genre.setText(book.getGenre());
        if (book_description != null) book_description.setText(book.getDescription());
        if (avg_ratting != null) avg_ratting.setText(String.format("%.1f / %d", book.getAvgRating(), book.getRaterCount()));
loadCoverImage(book.getCoverPhoto());
        }
loadComments();
} catch (Exception e) { LOGGER.severe("Failed to load book data via RMI: " + e.getMessage()); }
        }


private void loadCoverImage(String coverPhoto) {
    if (coverImage == null) return;
    try {
        if (coverPhoto != null && !coverPhoto.isEmpty()) {
            File uploadFile = new File("Uploads/book_covers/" + coverPhoto);
            if (uploadFile.exists()) { Image image = new Image("file:" + uploadFile.getAbsolutePath()); if (!image.isError()) { coverImage.setImage(image); return; } }
            Image image = new Image(getClass().getResourceAsStream("/images/book_covers/demo_cover.png")); coverImage.setImage(image);
        } else { coverImage.setImage(new Image(getClass().getResourceAsStream("/images/book_covers/demo_cover.png"))); }
    } catch (Exception e) { LOGGER.severe("Error loading cover image: " + e.getMessage()); }
}


private void loadComments() {
    if (bookService == null || commentContainer == null) return;
    try {
        List<CommentDTO> comments = bookService.getComments(bookId);
        commentContainer.getChildren().clear();
        int commentCount = 0;
        int currentUserId = UserSession.getInstance().isLoggedIn() ? UserSession.getInstance().getCurrentUserId() : -1;
        for (CommentDTO c : comments) {
            commentCount++;
            HBox commentBox = new HBox(10);
            commentBox.setPadding(new Insets(5,5,5,10));
            javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(5);
            Label usernameLabel = new Label("user: " + c.getUsername()); usernameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
            Label commentLabel = new Label(c.getComment()); commentLabel.setStyle("-fx-text-fill: white;");
            content.getChildren().addAll(usernameLabel, commentLabel); commentBox.getChildren().add(content);
            if (currentUserId == c.getUserId()) {
                Button del = new Button("x");
                del.setOnAction(e -> { try { boolean ok = bookService.deleteComment(c.getRatingId(), currentUserId); if (ok) { loadComments(); showAlert(Alert.AlertType.INFORMATION, "Success", "Your comment has been deleted."); } else showAlert(Alert.AlertType.WARNING, "No Comment", "You are not authorized to delete this comment."); } catch (Exception ex) { showAlert(Alert.AlertType.ERROR, "Error", "Failed to delete comment."); } });
                commentBox.getChildren().add(del);
            }
            commentContainer.getChildren().add(commentBox);
        }
        if (total_comments != null) total_comments.setText("Comments (" + commentCount + ")");


        if (UserSession.getInstance().isLoggedIn()) {
            CommentDTO userC = bookService.getUserComment(bookId, UserSession.getInstance().getCurrentUserId());
            if (userC != null) { user_name.setText("user: " + userC.getUsername()); user_comment.setText(userC.getComment()); }
            else { user_name.setText("user: " + UserSession.getInstance().getUsername()); user_comment.setText("No comment"); }
        } else { user_name.setText("Guest"); user_comment.setText("Log in to comment"); }
    } catch (Exception e) { LOGGER.severe("Error loading comments via RMI: " + e.getMessage()); if (total_comments != null) total_comments.setText("Comments (0)"); }
}


private void handleRating() { /* implement rating via bookService.addOrUpdateComment with rating */ }
private void handleStatusUpdate() { /* call bookService.updateStatus */ }


@FXML private void handle_add_comment() {
    if (!UserSession.getInstance().isLoggedIn()) { showAlert(Alert.AlertType.WARNING, "Login Required", "Please log in to add a comment."); return; }
    if (comment_box == null) { showAlert(Alert.AlertType.ERROR, "Error", "Comment input not available."); return; }
    String comment = comment_box.getText().trim(); if (comment.isEmpty()) { showAlert(Alert.AlertType.WARNING, "Invalid Input", "Please enter a comment."); return; }
    try { boolean ok = bookService.addOrUpdateComment(bookId, UserSession.getInstance().getCurrentUserId(), comment, null); if (ok) { comment_box.clear(); loadComments(); showAlert(Alert.AlertType.INFORMATION, "Success", "Comment added."); } else showAlert(Alert.AlertType.ERROR, "Error", "Failed to add comment."); } catch (Exception e) { showAlert(Alert.AlertType.ERROR, "Error", "Failed to add comment: " + e.getMessage()); }
}


private void showAlert(Alert.AlertType type, String title, String text) { Alert a = new Alert(type); a.setTitle(title); a.setHeaderText(null); a.setContentText(text); a.showAndWait(); }
}