package com.socialhub.armory;

import com.socialhub.armory.domain.ArmoryItem;
import com.socialhub.armory.domain.ArmoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@PrimaryPort
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArmoryQueries {

    private final ArmoryRepository repository;

    public List<ArmoryItem> items() { return repository.findAll(); }
}
