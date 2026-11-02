package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PendienteLotePageDto {
    private List<PendienteLoteDto> content;
    private Long totalElements;
    private Integer page;
    private Integer size;
}
