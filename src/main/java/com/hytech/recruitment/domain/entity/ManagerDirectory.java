package com.hytech.recruitment.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 面試主管通訊錄：主管名字 → 公司信箱。
 * 觸發寄信 CC 面試主管時，用來把名字解析為 email。
 * （本期為附檔假資料；真實情境應接內部通訊錄／HR 系統。）
 */
@Entity
@Table(name = "manager_directory")
public class ManagerDirectory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 主管顯示名字（不分大小寫比對）。 */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "email", nullable = false, length = 120)
    private String email;

    // ---- getters / setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
