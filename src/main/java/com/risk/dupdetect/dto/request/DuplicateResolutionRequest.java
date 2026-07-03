package com.risk.dupdetect.dto.request;

import com.risk.dupdetect.domain.ResolutionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for resolving flagged duplicates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateResolutionRequest {

    @NotNull(message = "Resolution is required")
    private ResolutionStatus resolution;

    private String note;
}
