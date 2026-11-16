package com.socialhub.armory.adapter.in.web;

import com.socialhub.api.model.ArmoryItemDto;
import com.socialhub.armory.ArmoryQueries;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Vista de la armería para la API. La usa la vista completa del anillo ({@code /api/factory}): así no necesita
 * tipos de dominio de este módulo, solo los DTO del contrato.
 */
@Component
@RequiredArgsConstructor
public class ArmoryView {

    private final ArmoryQueries queries;
    private final ArmoryWebMapper mapper;

    public List<ArmoryItemDto> items() { return mapper.toDtos(queries.items()); }
}
