package com.socialhub.publishing.adapter.in.web;

import com.socialhub.api.model.PublicationDto;
import com.socialhub.publishing.PublishingQueries;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Vista de publicaciones para la API. La usa la vista completa del anillo ({@code /api/factory}): así no necesita
 * tipos de dominio de este módulo, solo los DTO del contrato.
 */
@Component
@RequiredArgsConstructor
public class PublicationView {

    private final PublishingQueries queries;
    private final PublicationWebMapper mapper;

    public List<PublicationDto> all() { return mapper.toDtos(queries.all(), queries.insights()); }
}
