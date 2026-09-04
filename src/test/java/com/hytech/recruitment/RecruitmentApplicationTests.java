package com.hytech.recruitment;

import com.hytech.recruitment.repository.InvitationRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 冒煙測試：驗證 Spring 容器啟動、SQL 初始化與假資料載入。 */
@SpringBootTest
class RecruitmentApplicationTests {

    @Autowired
    private InvitationRecordRepository invitationRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void seedDataLoaded() {
        assertEquals(5, invitationRepository.count(), "應載入 5 筆邀約假資料");
    }
}
