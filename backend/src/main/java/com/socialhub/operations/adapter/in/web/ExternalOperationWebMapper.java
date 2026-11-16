package com.socialhub.operations.adapter.in.web;

import com.socialhub.api.model.ExternalOperationDto;
import com.socialhub.operations.domain.ExternalOperation;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;

@Mapper
interface ExternalOperationWebMapper {

    ExternalOperationDto toDto(ExternalOperation operation);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
