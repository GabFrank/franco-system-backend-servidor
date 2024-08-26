package com.franco.dev.config.multitenant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MultiPage {
    private String tenantId;
    private int page;
    private int offset;
    private Long totalElements;
    private Long lastOffset;
    private Long lastTotalElement;
}
