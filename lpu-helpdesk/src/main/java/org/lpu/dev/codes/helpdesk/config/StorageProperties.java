package org.lpu.dev.codes.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /**
     * Root directory for uploaded files (ID photos, etc.).
     * Docker maps {@code ./data/pictures} → {@code /usr/local/tomcat/pictures}.
     */
    private String picturesDir = "/usr/local/tomcat/pictures";

    private long maxIdBytes = 5 * 1024 * 1024;

    public String getPicturesDir() {
        return picturesDir;
    }

    public void setPicturesDir(String picturesDir) {
        this.picturesDir = picturesDir;
    }

    public long getMaxIdBytes() {
        return maxIdBytes;
    }

    public void setMaxIdBytes(long maxIdBytes) {
        this.maxIdBytes = maxIdBytes;
    }
}
