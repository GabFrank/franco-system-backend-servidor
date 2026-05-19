package com.franco.dev.graphql.activos.dto;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.domain.activos.Ente;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EnteSearchResponse {
    private CustomPage<Ente> page;
    private EnteDashboardSummary summary;
}
