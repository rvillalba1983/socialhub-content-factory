package com.socialhub.operations;

import com.socialhub.api.model.ExternalOperationDto;
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
