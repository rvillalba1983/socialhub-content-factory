package com.socialhub.ai.application;

import com.socialhub.ai.ClaudeActivity;
import com.socialhub.ai.domain.AiRequestRepository;
import com.socialhub.ai.domain.AiRequestType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ClaudeActivityService implements ClaudeActivity {

    private final AiRequestRepository requests;

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> lastResult(boolean onPc) {
        return Optional.ofNullable(requests.lastFinished(types(onPc)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean everSent(boolean onPc) {
        return requests.anyOfTypes(types(onPc));
    }

    private static List<AiRequestType> types(boolean onPc) {
        return Arrays.stream(AiRequestType.values()).filter(t -> t.onPc() == onPc).toList();
    }
}
