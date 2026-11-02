package com.socialhub.armory.adapter.in.web;

import com.socialhub.api.ArmoryApi;
import com.socialhub.api.model.ArmoryItemListDto;
import com.socialhub.armory.ArmoryQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ArmoryController implements ArmoryApi {

    private final ArmoryQueries queries;
    private final ArmoryWebMapper mapper;

    @Override
    public ResponseEntity<ArmoryItemListDto> listArmoryItems() {
        return ResponseEntity.ok(new ArmoryItemListDto(mapper.toDtos(queries.items())));
    }
}
