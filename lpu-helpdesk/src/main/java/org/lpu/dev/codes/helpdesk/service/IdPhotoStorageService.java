package org.lpu.dev.codes.helpdesk.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.config.StorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdPhotoStorageService {

    private static final Logger log = LogManager.getLogger(IdPhotoStorageService.class);

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );

    private static final Map<String, String> EXT_BY_CONTENT_TYPE = Map.of(
            MediaType.IMAGE_JPEG_VALUE, ".jpg",
            MediaType.IMAGE_PNG_VALUE, ".png",
            "image/webp", ".webp",
            MediaType.APPLICATION_PDF_VALUE, ".pdf"
    );

    private final StorageProperties storageProperties;

    public IdPhotoStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    public String storeTicketIdPhoto(Long ticketId, MultipartFile file) {
        validate(file);
        return store(ticketIdDir(), "ticket-" + ticketId + "-", file);
    }

    /** JPEG, PNG, WEBP, or PDF for chat / ticket thread attachments. */
    public StoredMessageFile storeMessageAttachment(Long ticketId, MultipartFile file) {
        byte[] bytes = readLimited(file);
        String contentType = requireMessageContentType(file, bytes);
        String filename = storeBytes(messageAttachmentDir(), "msg-" + ticketId + "-", bytes, contentType);
        return new StoredMessageFile(filename, contentType);
    }

    public record StoredMessageFile(String filename, String contentType) {}

    public Resource load(String filename) {
        return loadFrom(ticketIdDir(), filename, "No ID photo on file");
    }

    public Resource loadMessageAttachment(String filename) {
        return loadFrom(messageAttachmentDir(), filename, "Attachment not found");
    }

    public MediaType mediaTypeFor(String filename) {
        String name = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        if (name.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (name.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (name.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        return MediaType.IMAGE_JPEG;
    }

    public MediaType mediaTypeForContentType(String contentType, String filename) {
        String type = normalizeContentType(contentType);
        if (!type.isBlank()) {
            try {
                return MediaType.parseMediaType(type);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return mediaTypeFor(filename);
    }

    public void deleteQuietly(String filename) {
        deleteQuietlyFrom(ticketIdDir(), filename);
    }

    public void deleteMessageAttachmentQuietly(String filename) {
        deleteQuietlyFrom(messageAttachmentDir(), filename);
    }

    private String store(Path dir, String prefix, MultipartFile file) {
        try {
            return storeBytes(dir, prefix, file.getBytes(), normalizeContentType(file.getContentType()));
        } catch (IOException ex) {
            log.error("Failed to read upload for {}", dir, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the file");
        }
    }

    private String storeBytes(Path dir, String prefix, byte[] bytes, String contentType) {
        String extension = EXT_BY_CONTENT_TYPE.get(contentType);
        if (extension == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPG, PNG, WEBP, or PDF files are allowed");
        }
        String filename = prefix + UUID.randomUUID().toString().substring(0, 8) + extension;
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename).normalize();
            if (!target.startsWith(dir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
            }
            Files.write(target, bytes);
            return filename;
        } catch (IOException ex) {
            log.error("Failed to store attachment in {}", dir, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the file");
        }
    }

    private Resource loadFrom(Path dir, String filename, String notFoundMessage) {
        if (filename == null || filename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
        Path path = dir.resolve(filename).normalize();
        if (!path.startsWith(dir) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
        return new FileSystemResource(path);
    }

    private void deleteQuietlyFrom(Path dir, String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        try {
            Path path = dir.resolve(filename).normalize();
            if (path.startsWith(dir)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a file");
        }
        if (file.getSize() > storageProperties.getMaxIdBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is too large (max 5 MB)");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPG, PNG, or WEBP images are allowed");
        }
    }

    private byte[] readLimited(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a file");
        }
        if (file.getSize() > storageProperties.getMaxIdBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is too large (max 5 MB)");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > storageProperties.getMaxIdBytes()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is too large (max 5 MB)");
            }
            return bytes;
        } catch (IOException ex) {
            log.error("Failed to read chat attachment", ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the file");
        }
    }

    private String requireMessageContentType(MultipartFile file, byte[] bytes) {
        String contentType = normalizeContentType(file.getContentType());
        if (IMAGE_CONTENT_TYPES.contains(contentType)) {
            return contentType;
        }
        boolean declaredPdf = MediaType.APPLICATION_PDF_VALUE.equals(contentType);
        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase(Locale.ROOT)
                : "";
        boolean namedPdf = name.endsWith(".pdf");
        boolean looseType = contentType.isBlank() || "application/octet-stream".equals(contentType);
        if (declaredPdf || (namedPdf && looseType)) {
            if (!looksLikePdf(bytes)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That file is not a valid PDF");
            }
            return MediaType.APPLICATION_PDF_VALUE;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPG, PNG, WEBP, or PDF files are allowed");
    }

    private static boolean looksLikePdf(byte[] bytes) {
        return bytes.length >= 5
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F'
                && bytes[4] == '-';
    }

    private Path ticketIdDir() {
        return Path.of(storageProperties.getPicturesDir(), "ticket-ids").toAbsolutePath().normalize();
    }

    private Path messageAttachmentDir() {
        return Path.of(storageProperties.getPicturesDir(), "ticket-message-attachments")
                .toAbsolutePath()
                .normalize();
    }

    private static String normalizeContentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String type = raw.trim().toLowerCase(Locale.ROOT);
        int semi = type.indexOf(';');
        return semi >= 0 ? type.substring(0, semi).trim() : type;
    }
}
