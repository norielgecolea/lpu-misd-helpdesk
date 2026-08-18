package org.lpu.dev.codes.helpdesk.service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.lpu.dev.codes.helpdesk.dto.AdminCategoryResponse;
import org.lpu.dev.codes.helpdesk.dto.CreateCategoryRequest;
import org.lpu.dev.codes.helpdesk.dto.TicketCategoryOption;
import org.lpu.dev.codes.helpdesk.dto.UpdateCategoryRequest;
import org.lpu.dev.codes.helpdesk.model.PendingRequesterEmail;
import org.lpu.dev.codes.helpdesk.model.TicketCategoryDefinition;
import org.lpu.dev.codes.helpdesk.repository.TicketCategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketCategoryService {

    private final TicketCategoryRepository ticketCategoryRepository;
    private final CategoryLabelCache categoryLabelCache;

    public TicketCategoryService(
            TicketCategoryRepository ticketCategoryRepository,
            CategoryLabelCache categoryLabelCache
    ) {
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.categoryLabelCache = categoryLabelCache;
    }

    @PostConstruct
    @Transactional
    void warmCache() {
        categoryLabelCache.reload();
    }

    @Transactional(readOnly = true)
    public List<TicketCategoryOption> listForKiosk() {
        return ticketCategoryRepository.findActiveForKiosk().stream()
                .filter(c -> !isEmailLinkCategory(c.getCode()))
                .map(c -> new TicketCategoryOption(c.getCode(), c.getLabel(), c.isRequiresDetail()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketCategoryOption> listForOnline() {
        return ticketCategoryRepository.findActiveForOnline().stream()
                .filter(c -> !isEmailLinkCategory(c.getCode()))
                .map(c -> new TicketCategoryOption(c.getCode(), c.getLabel(), c.isRequiresDetail()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> listAll() {
        return ticketCategoryRepository.findAllOrdered().stream()
                .filter(c -> !isEmailLinkCategory(c.getCode()))
                .map(AdminCategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketCategoryDefinition requireActiveForKiosk(String rawCode) {
        TicketCategoryDefinition category = requireByCode(rawCode);
        if (!category.isActive() || !category.isShowOnKiosk() || isEmailLinkCategory(category.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That concern is not available on the kiosk");
        }
        return category;
    }

    @Transactional(readOnly = true)
    public TicketCategoryDefinition requireActiveForOnline(String rawCode) {
        TicketCategoryDefinition category = requireByCode(rawCode);
        if (!category.isActive() || !category.isShowOnline()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That concern type is not available");
        }
        return category;
    }

    /** Validates an active category for walk-in / either channel. */
    @Transactional(readOnly = true)
    public TicketCategoryDefinition requireActive(String rawCode) {
        TicketCategoryDefinition category = requireByCode(rawCode);
        if (!category.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That concern type is inactive");
        }
        return category;
    }

    @Transactional(readOnly = true)
    public String labelOf(String code) {
        return categoryLabelCache.labelOf(code);
    }

    @Transactional
    public AdminCategoryResponse create(CreateCategoryRequest request) {
        String code = normalizeCode(request.code());
        if (isEmailLinkCategory(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That choice is reserved");
        }
        if (ticketCategoryRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A choice with that code already exists");
        }
        String label = request.label().trim();
        if (label.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Label is required");
        }

        TicketCategoryDefinition category = new TicketCategoryDefinition();
        category.setCode(code);
        category.setLabel(label);
        category.setSortOrder(request.sortOrder() != null ? request.sortOrder() : nextSortOrder());
        category.setActive(true);
        category.setShowOnKiosk(request.showOnKiosk() == null || request.showOnKiosk());
        category.setShowOnline(request.showOnline() == null || request.showOnline());
        category.setRequiresDetail(Boolean.TRUE.equals(request.requiresDetail()));
        category.setCreatedAt(Instant.now());
        category.setUpdatedAt(Instant.now());

        TicketCategoryDefinition saved = ticketCategoryRepository.persist(category);
        categoryLabelCache.reload();
        return AdminCategoryResponse.from(saved);
    }

    @Transactional
    public AdminCategoryResponse update(Long id, UpdateCategoryRequest request) {
        TicketCategoryDefinition category = ticketCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Choice not found"));

        String label = request.label().trim();
        if (label.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Label is required");
        }
        category.setLabel(label);
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
        if (request.showOnKiosk() != null) {
            category.setShowOnKiosk(request.showOnKiosk());
        }
        if (request.showOnline() != null) {
            category.setShowOnline(request.showOnline());
        }
        if (request.requiresDetail() != null) {
            category.setRequiresDetail(request.requiresDetail());
        }
        category.setUpdatedAt(Instant.now());

        if (PendingRequesterEmail.LINK_LPU_EMAIL_CATEGORY.equals(category.getCode())) {
            category.setActive(true);
            category.setShowOnKiosk(false);
            category.setShowOnline(false);
            category.setRequiresDetail(false);
        }

        TicketCategoryDefinition saved = ticketCategoryRepository.save(category);
        categoryLabelCache.reload();
        return AdminCategoryResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        TicketCategoryDefinition category = ticketCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Choice not found"));
        if (isEmailLinkCategory(category.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That choice cannot be deleted");
        }
        ticketCategoryRepository.delete(category);
        categoryLabelCache.reload();
    }

    private static boolean isEmailLinkCategory(String code) {
        return PendingRequesterEmail.LINK_LPU_EMAIL_CATEGORY.equals(code);
    }

    private TicketCategoryDefinition requireByCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category is required");
        }
        String code = normalizeCode(rawCode);
        return ticketCategoryRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown ticket category: " + rawCode
                ));
    }

    private int nextSortOrder() {
        return ticketCategoryRepository.findAllOrdered().stream()
                .mapToInt(TicketCategoryDefinition::getSortOrder)
                .max()
                .orElse(0) + 10;
    }

    private static String normalizeCode(String raw) {
        String code = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!code.matches("[A-Z0-9_]{2,40}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Code may only use letters, numbers, and underscores (2–40 chars)"
            );
        }
        return code;
    }
}
