# 招募系統 API（Recruitment System）

依《招募系統 API 規格書 v2》實作：邀約紀錄 → 面試安排 → 寄信 → 結果 主線。

- **技術**：Spring Boot 3.3 / Spring Web / Spring Data JPA / Bean Validation
- **Spring IoC**：一律 **Field Injection**（`@Autowired` 於欄位）
- **資料庫**：**H2 in-memory**，啟動時以附檔 SQL 建表並載入每表 5 筆假資料
- **寄信**：同步、無佇列；以 `mail_send_record.invitation_id` UNIQUE ＋寄前查詢作冪等
  - 觸發前把關必填（**皆由 HR 於面試安排「排面試時間」以下拉填**）：**性別**、**上機考題目**（基本/AI）、**面試地點**（南港/板橋），缺漏回 422
  - 這三項「基本資料」原在邀約紀錄，已**移置面試安排的「排面試時間」動作**一併填；邀約建立不再收這些欄位（新增求職者不帶性別）
  - **排面試時間即觸發寄信**：HR 與主管配對成功後，於 `interview-time` 一併帶入基本資料與選定主管，落地後若「已確認面試且基本資料齊備」即自動觸發寄信
  - **CC 面試主管**：單一主管自動帶入；多位主管須 HR 於「排面試時間」以 `selectedManager` 選定一位，只 CC 該位（信箱查 `manager_directory`）
  - `{{面試前資料}}` 來源：request.formLink，缺則帶入面試安排的**基本資料表連結**
- **登入／授權**：本期暫緩；主管欄位白名單以商業邏輯把關。**邀請人**（`inviter`，英文名.英文姓氏，記錄發出邀約的 HR）因未做登入，建立時以寫死的預設 HR（`amy.chung`）帶入

## 專案結構

```
src/main/java/com/hytech/recruitment
├─ RecruitmentApplication.java      入口（@EnableScheduling）
├─ common/                          統一回應信封、錯誤碼、例外、全域處理
├─ domain/ + domain/enums/          JPA Entity 與列舉
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

## 端點總覽

| 方法 | 路徑 | 說明 |
|------|------|------|
| POST | `/invitations` | 建立邀約（三個月防重複，命中 409） |
| GET | `/invitations` | 列表（?status=&channel=&keyword=&page=&size=） |
| GET | `/invitations/{id}` | 單筆 |
| PATCH | `/invitations/{id}` | 欄位級更新 |
| GET | `/invitations/check-duplicate` | 防重複檢查（?name=&email=&phone=；同名＋手機／無手機則同名＋信箱） |
| POST | `/invitations/{id}/send-invite` | HR 於他平台寄出一面邀請後記錄 inviteSentAt（系統不代寄信）；狀態維持 EMAIL_CONTACT |
| POST | `/invitations/{id}/second-invite` | 記二邀寄送時間，狀態轉 SECOND_INVITE_PENDING（二次邀約未回覆）；可由 EMAIL_CONTACT（逾 24hr）或 SECOND_INVITE_PENDING（重寄）觸發 |
| POST | `/invitations/{id}/reply-received` | → COMMUNICATING，建立面試安排＋寄推薦信 |
| POST | `/invitations/{id}/confirm-time` | → TIME_CONFIRMING |
| POST | `/invitations/{id}/confirm-interview` | → INTERVIEW_CONFIRMED |
| POST | `/invitations/{id}/decline` | 主線婉拒 → DECLINED（body: declineReason 必填） |
| POST | `/invitations/{id}/blacklist` | 無故缺席快捷 → BLACKLIST（無 body） |
| POST | `/invitations/{id}/result` | 設定／編輯結果（body: `{result, reason?}`；result∈ OFFER_EXTENDED/OFFER_ACCEPTED/THANKS_LETTER/DECLINED/BLACKLIST，reason 選填僅黑名單／婉拒採用；確認面試後可於五者間互轉） |
| POST | `/invitations/{id}/trigger-mail` | 同步寄面試通知（body 僅可選 formLink；性別/上機考題目/面試地點/職稱取自面試安排） |
| POST | `/invitations/{id}/send-recommendation` | 寄推薦信給主管 |
| GET | `/arrangements` | 面試安排列表（?manager=） |
| GET | `/arrangements/{id}` | 單筆 |
| PATCH | `/arrangements/{id}/manager-fields` | 主管白名單欄位 interviewManager／managerPreferredDates／managerRemark，皆可多值（日期可換行；其他欄 403） |
| PATCH | `/arrangements/{id}/interview-time` | HR「排面試時間」單一動作：一面時間（改期，狀態不變；不可為過去時間，否則 400）＋基本資料 gender／examType 基本-AI／location 南港-板橋（下拉，非法值 400）＋多主管時 selectedManager（須在清單內，否則 422）；資料齊備且已確認面試時自動觸發寄信 |
| GET | `/mail-records?invitationId=` | 查寄信結果 |
| GET | `/mail-templates` | 範本清單（?examType=&location=&jobTitle=） |

## 回應信封

```jsonc
// 成功
{ "success": true, "data": { ... } }
// 失敗
{ "success": false, "error": { "code": "DUPLICATE_INVITE_WITHIN_3M",
  "message": "此人 90 天內已發送一面邀請", "existingInvitationId": 1 } }
```

| 情境 | HTTP | code |
|------|------|------|
| 三個月內重複邀請 | 409 | `DUPLICATE_INVITE_WITHIN_3M` |
| 非法狀態轉移 | 422 | `ILLEGAL_STATE_TRANSITION` |
| 主管寫非白名單欄位 | 403 | `FIELD_NOT_WRITABLE_BY_ROLE` |
| 查無信件範本 | 422 | `TEMPLATE_NOT_FOUND` |
| 寄信必填欄位缺漏（性別／上機試題類型／面試地點） | 422 | `MAIL_REQUIRED_FIELDS_MISSING` |
| 多位面試主管未先選定一位 | 422 | `MANAGER_SELECTION_REQUIRED` |
| 資源不存在 | 404 | `NOT_FOUND` |

`requests.http` 內附可直接執行的範例請求。
