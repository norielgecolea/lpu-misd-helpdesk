package org.lpu.dev.codes.helpdesk.service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.lpu.dev.codes.helpdesk.model.TicketCategoryDefinition;
import org.lpu.dev.codes.helpdesk.repository.TicketCategoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-memory labels for ticket category codes so {@code TicketResponse} can resolve
 * display names without a DB hit on every mapping.
 */
@Component
public class CategoryLabelCache {

    private static volatile CategoryLabelCache instance;

    private final TicketCategoryRepository ticketCategoryRepository;
    private final Map<String, String> labelsByCode = new ConcurrentHashMap<>();
    private final Map<String, Boolean> requiresDetailByCode = new ConcurrentHashMap<>();

    public CategoryLabelCache(TicketCategoryRepository ticketCategoryRepository) {
        this.ticketCategoryRepository = ticketCategoryRepository;
    }

    @PostConstruct
    void register() {
        instance = this;
    }

    @Transactional(readOnly = true)
    public void reload() {
        List<TicketCategoryDefinition> all = ticketCategoryRepository.findAllOrdered();
        labelsByCode.clear();
        requiresDetailByCode.clear();
        for (TicketCategoryDefinition category : all) {
            labelsByCode.put(category.getCode(), category.getLabel());
            requiresDetailByCode.put(category.getCode(), category.isRequiresDetail());
        }
    }

    public String labelOf(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String label = labelsByCode.get(code);
        if (label != null && !label.isBlank()) {
            return label;
        }
        return humanize(code);
    }

    public boolean requiresDetail(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(requiresDetailByCode.get(code));
    }

    public static String labelFor(String code) {
        CategoryLabelCache cache = instance;
        if (cache == null) {
            return humanize(code);
        }
        return cache.labelOf(code);
    }

    public static boolean requiresDetailFor(String code) {
        CategoryLabelCache cache = instance;
        if (cache == null) {
            return "OTHERS".equalsIgnoreCase(code);
        }
        return cache.requiresDetail(code);
    }

    private static String humanize(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        return code.replace('_', ' ').trim();
    }
}
