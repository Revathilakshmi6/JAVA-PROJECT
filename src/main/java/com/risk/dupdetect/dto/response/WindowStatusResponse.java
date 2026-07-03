package com.risk.dupdetect.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for sliding window health and status queries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WindowStatusResponse {
    private int size;
    private double oldestEntryAgeSeconds;
    private String storeType;
    private long windowSeconds;
}
