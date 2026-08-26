package org.lpu.dev.codes.helpdesk.service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    public record CategorySelection(TicketCategoryDefinition parent, TicketCategoryDefinition child) {
    }

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
        return listNested(true, false);
    }

    @Transactional(readOnly = true)
    public List<TicketCategoryOption> listForOnline() {
        return listNested(false, true);
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> listAll() {
        List<TicketCategoryDefinition> all = ticketCategoryRepository.findAllOrdered().stream()
                .filter(c -> !isEmailLinkCategory(c.getCode()))
                .toList();
        Map<Long, List<TicketCategoryDefinition>> childrenByParent = groupChildren(all);
        List<AdminCategoryResponse> roots = new ArrayList<>();
        for (TicketCategoryDefinition parent : all) {
            if (!parent.isRoot()) {
                continue;
            }
            List<AdminCategoryResponse> children = childrenByParent.getOrDefault(parent.getId(), List.of())
                    .stream()
                    .map(AdminCategoryResponse::from)
                    .toList();
            roots.add(AdminCategoryResponse.from(parent, children));
        }
        return roots;
    }

    @Transactional(readOnly = true)
    public CategorySelection requireLeafForKiosk(String parentCode, String childCode) {
        CategorySelection selection = requireLeaf(parentCode, childCode);
        if (!selection.parent().isShowOnKiosk() || isEmailLinkCategory(selection.parent().getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That concern is not available on the kiosk");
        }
        return selection;
    }

    @Transactional(readOnly = true)
    public CategorySelection requireLeafForOnline(String parentCode, String childCode) {
        CategorySelection selection = requireLeaf(parentCode, childCode);
        if (!selection.parent().isShowOnline()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That concern type is not available");
        }
        return selection;
    }

    /** Validates an active parent + child pair for walk-in / either channel. */
    @Transactional(readOnly = true)
    public CategorySelection requireLeaf(String parentCode, String childCode) {
        TicketCategoryDefinition parent = requireByCode(parentCode, "Category is required");
        TicketCategoryDefinition child = requireByCode(childCode, "Problem is required");
        if (!parent.isRoot()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a main category first");
        }
        if (child.getParentId() == null || !child.getParentId().equals(parent.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "That problem does not belong to the chosen category"
            );
        }
        if (!parent.isActive() || !child.isActive() || isEmailLinkCategory(parent.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That concern type is inactive");
        }
        return new CategorySelection(parent, child);
    }

    @Transactional(readOnly = true)
    public String labelOf(String code) {
        return categoryLabelCache.labelOf(code);
    }

    @Transactional
    public AdminCategoryResponse create(CreateCategoryRequest request) {
        String label = request.label().trim();
        if (label.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Label is required");
        }

        TicketCategoryDefinition parent = null;
        if (request.parentId() != null) {
            parent = ticketCategoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Main category not found"));
            if (!parent.isRoot()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Problems can only be added under a main category");
            }
            if (isEmailLinkCategory(parent.getCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That choice is reserved");
            }
        }

        String code = request.code() != null && !request.code().isBlank()
                ? normalizeCode(request.code())
                : generateCode(label, parent);
        if (isEmailLinkCategory(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That choice is reserved");
        }
        if (ticketCategoryRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A choice with that code already exists");
        }

        TicketCategoryDefinition category = new TicketCategoryDefinition();
        category.setParentId(parent != null ? parent.getId() : null);
        category.setCode(code);
        category.setLabel(label);
        category.setSortOrder(request.sortOrder() != null ? request.sortOrder() : nextSortOrder(category.getParentId()));
        category.setActive(true);
        if (parent == null) {
            category.setShowOnKiosk(request.showOnKiosk() == null || request.showOnKiosk());
            category.setShowOnline(request.showOnline() == null || request.showOnline());
            category.setRequiresDetail(false);
        } else {
            category.setShowOnKiosk(parent.isShowOnKiosk());
            category.setShowOnline(parent.isShowOnline());
            category.setRequiresDetail(Boolean.TRUE.equals(request.requiresDetail()));
        }
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
        if (category.isRoot()) {
            if (request.showOnKiosk() != null) {
                category.setShowOnKiosk(request.showOnKiosk());
            }
            if (request.showOnline() != null) {
                category.setShowOnline(request.showOnline());
            }
            category.setRequiresDetail(false);
        } else if (request.requiresDetail() != null) {
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
        if (category.isRoot() && ticketCategoryRepository.countByParentId(category.getId()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Remove the problems under this category first"
            );
        }
        ticketCategoryRepository.delete(category);
        categoryLabelCache.reload();
    }

    private List<TicketCategoryOption> listNested(boolean kiosk, boolean online) {
        List<TicketCategoryDefinition> all = ticketCategoryRepository.findAllOrdered().stream()
                .filter(c -> !isEmailLinkCategory(c.getCode()))
                .toList();
        Map<Long, List<TicketCategoryDefinition>> childrenByParent = groupChildren(all);
        List<TicketCategoryOption> roots = new ArrayList<>();
        for (TicketCategoryDefinition parent : all) {
            if (!parent.isRoot() || !parent.isActive()) {
                continue;
            }
            if (kiosk && !parent.isShowOnKiosk()) {
                continue;
            }
            if (online && !parent.isShowOnline()) {
                continue;
            }
            List<TicketCategoryOption> children = childrenByParent.getOrDefault(parent.getId(), List.of()).stream()
                    .filter(TicketCategoryDefinition::isActive)
                    .map(c -> new TicketCategoryOption(c.getCode(), c.getLabel(), c.isRequiresDetail()))
                    .toList();
            if (children.isEmpty()) {
                continue;
            }
            roots.add(new TicketCategoryOption(parent.getCode(), parent.getLabel(), false, children));
        }
        return roots;
    }

    private static Map<Long, List<TicketCategoryDefinition>> groupChildren(List<TicketCategoryDefinition> all) {
        Map<Long, List<TicketCategoryDefinition>> childrenByParent = new LinkedHashMap<>();
        for (TicketCategoryDefinition category : all) {
            if (category.getParentId() == null) {
                continue;
            }
            childrenByParent.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
        }
        return childrenByParent;
    }

    private static boolean isEmailLinkCategory(String code) {
        return PendingRequesterEmail.LINK_LPU_EMAIL_CATEGORY.equals(code);
    }

    private TicketCategoryDefinition requireByCode(String rawCode, String missingMessage) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, missingMessage);
        }
        String code = normalizeCode(rawCode);
        return ticketCategoryRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown ticket category: " + rawCode
                ));
    }

    private int nextSortOrder(Long parentId) {
        return ticketCategoryRepository.findAllOrdered().stream()
                .filter(c -> Objects.equals(c.getParentId(), parentId))
                .mapToInt(TicketCategoryDefinition::getSortOrder)
                .max()
                .orElse(0) + 10;
    }

    private String generateCode(String label, TicketCategoryDefinition parent) {
        String slug = slugify(label);
        if (slug.length() < 2) {
            slug = parent == null ? "CAT" : "PROBLEM";
        }
        if (parent != null) {
            String prefix = parent.getCode() + "_";
            int remain = 40 - prefix.length();
            if (remain < 2) {
                prefix = parent.getCode().substring(0, Math.min(28, parent.getCode().length())) + "_";
                remain = 40 - prefix.length();
            }
            if (slug.length() > remain) {
                slug = slug.substring(0, remain);
            }
            slug = prefix + slug;
        } else if (slug.length() > 40) {
            slug = slug.substring(0, 40);
        }
        String candidate = slug;
        int n = 2;
        while (ticketCategoryRepository.existsByCode(candidate)) {
            String suffix = "_" + n;
            int cut = Math.min(slug.length(), 40 - suffix.length());
            candidate = slug.substring(0, Math.max(1, cut)) + suffix;
            n++;
        }
        return candidate;
    }

    private static String slugify(String raw) {
        String slug = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        slug = slug.replaceAll("^_+", "").replaceAll("_+$", "");
        return slug;
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
