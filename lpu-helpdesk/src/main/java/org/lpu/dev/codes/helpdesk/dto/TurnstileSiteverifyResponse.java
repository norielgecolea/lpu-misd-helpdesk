package org.lpu.dev.codes.helpdesk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TurnstileSiteverifyResponse(
        boolean success,
        String hostname,
        String action,
        @JsonProperty("error-codes") List<String> errorCodes
) {
}
