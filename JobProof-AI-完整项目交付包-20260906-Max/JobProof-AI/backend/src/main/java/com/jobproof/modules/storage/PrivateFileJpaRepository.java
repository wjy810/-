package com.jobproof.modules.storage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivateFileJpaRepository extends JpaRepository<PrivateFileEntity, String> {
    List<PrivateFileEntity> findByOwnerId(String ownerId);
}
