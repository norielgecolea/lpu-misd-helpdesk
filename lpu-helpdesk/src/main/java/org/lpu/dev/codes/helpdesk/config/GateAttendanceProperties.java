package org.lpu.dev.codes.helpdesk.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gate-attendance")
public class GateAttendanceProperties {

    /** IP or hostname of the gate attendance Postgres server. */
    private String dbHost = "127.0.0.1";
    private int dbPort = 5432;
    private String dbName = "postgres";
    private String dbUsername = "postgres";
    private String dbPassword = "";
    /** Optional gate web base URL for photos. Empty → http://{dbHost} */
    private String url = "";
    /** Public path on the gate Tomcat WAR for profile pictures. */
    private String picturesPath = "/attendance-system/pictures";
    /**
     * Public WAR context path used in browser-facing photo proxy URLs
     * (same-origin HTTPS via nginx/Cloudflare — avoids Safari mixed-content blocks).
     */
    private String publicContextPath = "/lpu-helpdesk";

    public String jdbcUrl() {
        return "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
    }

    public String resolvedWebUrl() {
        if (url != null && !url.isBlank()) {
            return url.replaceAll("/+$", "");
        }
        return "http://" + dbHost;
    }

    /**
     * Browser-facing photo URL served by this app over the same origin/protocol.
     * Upstream gate photos stay on HTTP and are fetched server-side by
     * {@code GatePhotoProxyService}.
     */
    public String resolvePhotoUrl(String photo) {
        String filename = extractFilename(photo);
        if (filename == null) {
            return null;
        }
        String context = publicContextPath == null || publicContextPath.isBlank()
                ? ""
                : publicContextPath.replaceAll("/+$", "");
        if (!context.isEmpty() && !context.startsWith("/")) {
            context = "/" + context;
        }
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return context + "/api/directory/photos/" + encoded;
    }

    /** Absolute HTTP(S) URL on the gate Tomcat used by the photo proxy. */
    public String upstreamPhotoUrl(String filename) {
        String safe = extractFilename(filename);
        if (safe == null) {
            throw new IllegalArgumentException("Photo filename is required");
        }
        String basePath = picturesPath.startsWith("/") ? picturesPath : "/" + picturesPath;
        return resolvedWebUrl() + basePath.replaceAll("/+$", "") + "/" + safe;
    }

    private static String extractFilename(String photo) {
        if (photo == null || photo.isBlank()) {
            return null;
        }
        String value = photo.trim();
        if (value.startsWith("data:")) {
            return null;
        }
        // Strip query/fragment if a full URL was stored.
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int hash = value.indexOf('#');
        if (hash >= 0) {
            value = value.substring(0, hash);
        }
        String filename = value.contains("/") ? value.substring(value.lastIndexOf('/') + 1) : value;
        if (filename.isBlank() || filename.contains("..")) {
            return null;
        }
        return filename;
    }

    public String getDbHost() {
        return dbHost;
    }

    public void setDbHost(String dbHost) {
        this.dbHost = dbHost;
    }

    public int getDbPort() {
        return dbPort;
    }

    public void setDbPort(int dbPort) {
        this.dbPort = dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getDbUsername() {
        return dbUsername;
    }

    public void setDbUsername(String dbUsername) {
        this.dbUsername = dbUsername;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPicturesPath() {
        return picturesPath;
    }

    public void setPicturesPath(String picturesPath) {
        this.picturesPath = picturesPath;
    }

    public String getPublicContextPath() {
        return publicContextPath;
    }

    public void setPublicContextPath(String publicContextPath) {
        this.publicContextPath = publicContextPath;
    }
}
