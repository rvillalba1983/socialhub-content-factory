package com.socialhub.production.adapter.in.web;

import com.socialhub.api.StationsApi;
import com.socialhub.api.model.StationCommandDto;
import com.socialhub.api.model.StationDto;
import com.socialhub.api.model.StationListDto;
import com.socialhub.production.ProductionService;
import com.socialhub.production.domain.StationCommandType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class StationsController implements StationsApi {

    private final ProductionService service;
    private final ProductionView view;

    @Override
    public ResponseEntity<StationListDto> listStations() {
        return ResponseEntity.ok(new StationListDto(view.snapshot().stations()));
    }

    @Override
    public ResponseEntity<StationDto> executeStationCommand(String stationId, String requestGuard,
                                                            StationCommandDto command) {
        var station = service.execute(stationId, StationCommandType.valueOf(command.getType().name()));
        return ResponseEntity.ok(view.station(station));
    }
}
