package com.example.scribble.Controller;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global application state (singleton).
 * Giữ tên lớp và API giống bạn ban đầu; cải tiến để an toàn hơn khi đa luồng,
 * chống reflection/serialization/cloning và thêm vài tiện ích nhỏ.
 */
public final class AppState_c implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(AppState_c.class.getName());

    // volatile để đảm bảo visibility giữa các thread
    private static volatile AppState_c instance;

    // previousFXML có thể được truy cập bởi nhiều luồng -> volatile
    private volatile String previousFXML;

    // Private constructor to prevent external instantiation (reflection guard)
    private AppState_c() {
        if (instance != null) {
            throw new RuntimeException("Use getInstance() to get the singleton instance of AppState_c.");
        }
    }

    /**
     * Thread-safe lazy initialization (double-checked locking).
     */
    public static AppState_c getInstance() {
        if (instance == null) {
            synchronized (AppState_c.class) {
                if (instance == null) {
                    instance = new AppState_c();
                    LOGGER.info("AppState_c singleton instance created.");
                }
            }
        }
        return instance;
    }

    /**
     * Khi singleton bị serialize/deserialize, đảm bảo chỉ trả về instance hiện tại.
     */
    private Object readResolve() throws ObjectStreamException {
        return getInstance();
    }

    /**
     * Trả về FXML trước đó. Nếu chưa set, trả về null.
     */
    public String getPreviousFXML() {
        LOGGER.fine("Retrieving previous FXML: " + previousFXML);
        return previousFXML;
    }

    /**
     * Set FXML trước đó. Accept null để clear.
     */
    public void setPreviousFXML(String previousFXML) {
        this.previousFXML = previousFXML;
        LOGGER.log(Level.INFO, "Set previous FXML to: {0}", previousFXML);
    }

    /**
     * Reset previousFXML về null (tiện ích).
     */
    public void clearPreviousFXML() {
        this.previousFXML = null;
        LOGGER.info("Cleared previous FXML.");
    }

    /**
     * Convenience: check xem có previousFXML hay không.
     */
    public boolean hasPreviousFXML() {
        return previousFXML != null && !previousFXML.trim().isEmpty();
    }

    /**
     * Tùy chọn: cho phép ghi log hiện trạng để debug.
     */
    public void dumpState() {
        LOGGER.info(() -> "AppState_c dump: previousFXML=" + previousFXML);
    }

    /**
     * Ngăn chặn clone (nếu ai cố clone object này).
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("Cloning of AppState_c is not supported");
    }
}
