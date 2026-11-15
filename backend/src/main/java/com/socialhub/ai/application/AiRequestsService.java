package com.socialhub.ai.application;

import com.socialhub.ai.AiRequests;
import com.socialhub.ai.domain.AiRequestPage;
import com.socialhub.ai.domain.AiRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AiRequestsService implements AiRequests {

    private final AiRequestRepository requests;

    @Override
    @Transactional(readOnly = true)
    public AiRequestPage page(int offset, int limit) { return requests.page(offset, limit); }
}
