package com.socialhub.armory.application;

import com.socialhub.armory.ArmoryQueries;
import com.socialhub.armory.domain.ArmoryItem;
import com.socialhub.armory.domain.ArmoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ArmoryQueriesService implements ArmoryQueries {

    private final ArmoryRepository repository;

    @Override
    public List<ArmoryItem> items() { return repository.findAll(); }
}
