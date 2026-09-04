package com.hytech.recruitment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 招募系統 · 入口。
 * <p>邀約紀錄 → 面試安排 → 寄信 → 結果 主線；H2 in-memory，附檔假資料。</p>
 * <p>Spring IoC 一律採 Field Injection（@Autowired 於欄位）。</p>
 */
@SpringBootApplication
@EnableScheduling
public class RecruitmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecruitmentApplication.class, args);
    }
}
