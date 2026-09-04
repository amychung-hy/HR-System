package com.hytech.recruitment.repository;

import com.hytech.recruitment.domain.entity.ManagerDirectory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManagerDirectoryRepository extends JpaRepository<ManagerDirectory, Long> {

    /** 以名字（忽略大小寫）查主管信箱。 */
    Optional<ManagerDirectory> findByNameIgnoreCase(String name);
}
