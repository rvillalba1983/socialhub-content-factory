package com.socialhub.pieces;

import com.socialhub.api.PiecesApi;
import com.socialhub.api.model.PieceOrderDto;
import com.socialhub.api.model.PieceOrderInputDto;
import com.socialhub.api.model.PieceSpecDto;
import com.socialhub.api.model.PieceSpecListDto;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class PiecesController implements PiecesApi {

    private final PieceOrders orders;

    @Override
    public ResponseEntity<PieceSpecListDto> listPieceSpecs(String promotionId) {
        PieceOrders.Watched watched = orders.specs(promotionId);
        PieceSpecListDto dto = new PieceSpecListDto(watched.items().stream().map(s -> {
            PieceSpecDto item = new PieceSpecDto(s.path(), s.name(), s.modifiedAt().atOffset(ZoneOffset.UTC));
            item.setTaskId(s.taskId());
            return item;
        }).toList());
        dto.setFolder(watched.folder());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<PieceOrderDto> createPieceOrder(String promotionId, String requestGuard, PieceOrderInputDto input) {
        String taskId = orders.order(promotionId, input.getSpecPath());
        return ResponseEntity.status(HttpStatus.CREATED).body(new PieceOrderDto(taskId, input.getSpecPath()));
    }
}
