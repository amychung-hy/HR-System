# 招募系統 API（Recruitment System）

依《招募系統 API 規格書 v2》實作：邀約紀錄 → 面試安排 → 寄信 → 結果 主線。

- **技術**：Spring Boot 3.3 / Spring Web / Spring Data JPA / Bean Validation
- **資料庫**：**H2 in-memory**，啟動時以附檔 SQL 建表並載入每表 5 筆假資料
- **登入／授權**：本期暫緩；主管欄位白名單以商業邏輯把關。**邀請人**（`inviter`，英文名.英文姓氏，記錄發出邀約的 HR）因未做登入，建立時以寫死的預設 HR（`amy.chung`）帶入

## 專案結構

```
src/main/java/com/hytech/recruitment
├─ RecruitmentApplication.java      入口（@EnableScheduling）
├─ common/                          統一回應信封、錯誤碼、例外、全域處理
├─ domain/ + entity/enums/          JPA Entity 與列舉
├─ repository/                      Spring Data JPA
├─ dto/request · dto/response       請求／回應 DTO（record）
├─ service/                         InvitationService、InterviewArrangementService、
│                                   MailService、StatusMachine、MailSender
├─ controller/                      Invitation／Arrangement／Mail 三組端點
└─ scheduler/                       每日排程：二邀偵測、主管反灰提示

src/main/resources
├─ application.yml
└─ db/sql_script.sql · db/reset-identity.sql   附檔 DDL＋假資料、IDENTITY 校正
```

## 執行

需 JDK 21+。

```bash
mvn spring-boot:run
```

啟動後：

- API base：`http://localhost:8080/api/v1`
- H2 Console：`http://localhost:8080/h2-console`
  （JDBC URL：`jdbc:h2:mem:recruitment`，使用者 `sa`，無密碼）
