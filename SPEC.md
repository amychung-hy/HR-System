# 招募系統 規格書（權威版 / Source of Truth）

> 本文件為此專案的**唯一權威規格**。任何 AI 代理（含 Claude Code）或工程師在修改前，**必須先讀完本文件**，並嚴格遵守第 0 節的界線。與程式碼衝突時，以「程式碼現況 + 本文件」為準；不得依「常見做法」自行擴充。
>
> 最後更新：2026-09-04。

---

## 0. 給 AI 代理的重要界線（先讀，務必遵守）

**MUST（一定要）**
- **只做被明確要求的事**，不多做。需求不清楚時**先問**，不要自行臆測補功能。
- Spring IoC 一律 **Field Injection**（`@Autowired` 標在欄位上）。這是使用者的硬性要求，**不要**改成 constructor injection。
- 例外一律走 `BusinessException` + `ErrorCode`，由 `GlobalExceptionHandler` 統一包成回應信封。
- 資料庫為 **H2 in-memory**，每次啟動由 `src/main/resources/db/sql_script.sql` 重建並載入假資料；顯式 id 匯入後由 `reset-identity.sql` 校正 IDENTITY 起始值。改結構時**兩個 SQL 檔都要同步**。
- 中文字面欄位（`exam_type`、`location`）在 DB 以中文字串儲存（「基本」「AI」「南港」「板橋」），程式以字串比對。

**MUST NOT（禁止）**
- **不要**把「性別 / 上機考題目(examType) / 面試地點(location)」放回邀約紀錄（InvitationRecord）。這三項「基本資料」只存在於**面試安排（InterviewArrangement）**，由 HR 於「排面試時間」動作填。
- `examType` **只有** `基本` / `AI`；`location` **只有** `南港` / `板橋`。不要新增其他值域。
- **不要**加登入 / 權限 / 帳號功能（本期暫緩）。主管欄位白名單以商業邏輯把關即可。
- **一面邀請信、二次邀請信、與求職者的溝通信，系統一律不寄**。這些是 HR 於外部平台（104 / Email 等）手動操作，系統只**記錄時間 / 狀態**。系統會「寄」（mock）的只有：**面試通知信**（trigger-mail）與**人才推薦信**（send-recommendation）。
- **不要**新增非必要功能（例如行事曆整合、通知中心、批次匯入等），除非使用者明講。
- `candidate_reply_link`（面試者回傳資料）目前沒有真實問卷系統，是在寄信成功時**模擬**回寫，不要當成真的 webhook 去接。
- claude.ai 上的 規格書/藍圖 artifact 連結，Claude **讀不到**（需登入 session），不要假裝讀得到；以本文件與程式碼為準。

---

## 1. 專案概觀

HR 招募流程系統，主線：**邀約紀錄 → 面試安排 → 寄信 → 結果**，以單一 `invitationId` 串接三個資源。

- 技術：Spring Boot 3.3.4 / Spring Web / Spring Data JPA / Bean Validation
- 語言/建置：Java 21、Maven
- 資料庫：H2 in-memory（開發/展示用）
- 前端：兩支純靜態 HTML（`src/main/resources/static/`），可離線示範，連得到後端就走真實 API

---

## 2. 環境與建置／執行

機器上 **JDK / Maven 皆不在 PATH**，以可攜版放在 `C:\Users\hyt\tools\`：
- JDK 21：`C:\Users\hyt\tools\jdk-21.0.12.1+1`
- Maven 3.9.9：`C:\Users\hyt\tools\apache-maven-3.9.9`

建置與執行（PowerShell）：
```
$env:JAVA_HOME="C:\Users\hyt\tools\jdk-21.0.12.1+1"
& "$env:USERPROFILE\tools\apache-maven-3.9.9\bin\mvn.cmd" -B clean package
& "$env:JAVA_HOME\bin\java.exe" -jar target\recruitment-system-1.0.0.jar
```
- API base：`http://localhost:8080/api/v1`
- H2 Console：`http://localhost:8080/h2-console`（JDBC `jdbc:h2:mem:recruitment`，使用者 `sa`，無密碼）
- 前端：`http://localhost:8080/index.html`（邀約紀錄）、`/arrangements.html`（面試安排）
- 注意：Console 顯示中文為亂碼是 PowerShell 顯示編碼問題，實際 JSON 為 UTF-8。
- 測試連 8080 若被 IDE 的 `spring-boot:run` 佔用，改用 `--server.port=8081` 另起驗證。

---

## 3. 架構與套件結構

```
com.hytech.recruitment
├─ RecruitmentApplication            入口（@EnableScheduling）
├─ common/                           ApiResponse、ErrorCode、BusinessException、GlobalExceptionHandler
├─ domain/entity/                    InvitationRecord、InterviewArrangement、ManagerDirectory、MailTemplate、MailSendRecord
├─ domain/enums/                     ContactStatus、Gender、ExamType、Location、Role、MailSendStatus
├─ dto/request、dto/response         皆為 record
├─ repository/                       Spring Data JPA（5 個）
├─ service/                          InvitationService、InterviewArrangementService、MailService、
│                                    StatusMachine、ManagerResolver、MailSender
├─ controller/                       InvitationController、ArrangementController、MailController
└─ scheduler/                        RecruitmentScheduler（每日排程：二邀偵測、主管反灰提示）

src/main/resources
├─ application.yml
├─ db/sql_script.sql                 DDL + 每表假資料
├─ db/reset-identity.sql             IDENTITY 起始值校正
└─ static/index.html、arrangements.html
```

---

## 4. 領域模型（資料表與欄位）

### 4.1 invitation_record（邀約主檔，單一真實來源；id = invitationId）
| 欄位 | 型別 | 說明 |
|---|---|---|
| id | BIGINT IDENTITY PK | 貫穿三資源 |
| name | VARCHAR(50) NOT NULL | 姓名 |
| email | VARCHAR(120) NOT NULL | |
| phone | VARCHAR(20) | 選填；有填則須 09 開頭共 10 碼，後端統一存為 `0966-888-999`（4-3-3）格式（見 7.1） |
| resume_no | VARCHAR(30) | 有值＝104 來源；無值＝其他管道 |
| **resume_link** | VARCHAR(255) | **履歷連結**；新增求職者成功後由系統自動帶入（本期假資料 `https://drive.example.invalid/resume/{invitationId}`）。進入 COMMUNICATING 建面試安排時，未另填則沿用此連結 |
| channel | VARCHAR(40) | 招募管道；**建立時必填**（DTO `@NotBlank`，DB 欄位仍可空以相容舊資料） |
| job_title | VARCHAR(60) | 職稱；**建立時必填**（DTO `@NotBlank`，DB 欄位仍可空以相容舊資料） |
| **inviter** | VARCHAR(60) | **邀請人**（發出邀約的 HR，英文名.英文姓氏，如 `amy.chung`）；HR 於新增求職者時自填，未做登入，留空則帶預設 `amy.chung` |
| contact_status | VARCHAR(30) NOT NULL | ContactStatus |
| invite_sent_at | TIMESTAMP | 一面邀請「記錄」時間（三個月防重複基準） |
| second_invite_at | TIMESTAMP | 二次邀約「記錄」時間 |
| decline_reason | VARCHAR(60) | 婉拒原因；黑名單／婉拒（含無故缺席、拒絕聘約、HR 編輯結果）之**選填**理由亦寫此欄 |
| result | VARCHAR(30) | 最終結果 OFFER_ACCEPTED/THANKS_LETTER/DECLINED/BLACKLIST（由 applyResult 設定；OFFER_EXTENDED 為過渡狀態、非最終結果，不寫此欄；中途婉拒亦不寫此欄） |
| created_at / updated_at | TIMESTAMP | |

> ⚠️ 本表**不含**性別 / 上機考題目 / 面試地點。

### 4.2 interview_arrangement（面試安排，一位一列；invitation_id UNIQUE）
| 欄位 | 型別 | 可寫角色 | 說明 |
|---|---|---|---|
| id | BIGINT IDENTITY PK | 系統 | |
| invitation_id | BIGINT NOT NULL UNIQUE | 系統 | 對應邀約主檔 |
| candidate_name / job_title / resume_link | | 系統帶入 | 進入 COMMUNICATING 時單向帶入 |
| gender | VARCHAR(10) | **HR** | 下拉 MALE/FEMALE；於「排面試時間」填 |
| exam_type | VARCHAR(10) | **HR** | 下拉「基本」/「AI」；上機考題目 |
| location | VARCHAR(10) | **HR** | 下拉「南港」/「板橋」 |
| interview_manager | VARCHAR(50) | **主管** | 可多人（`/`、`&`、`、`、`,` 分隔） |
| selected_manager | VARCHAR(50) | **HR** | 多主管時選定一位（寄信只 CC 這位） |
| manager_preferred_dates | VARCHAR(500) | **主管** | 可面試日期，**支援換行**（一主管一行） |
| interview_time | TIMESTAMP | **HR** | 一面時間（改期走此欄，不可過去） |
| manager_remark | VARCHAR(255) | **主管** | **主管備註**（例：優先給 Sherry 面試）。HR 不於此備註 |
| interview_prep_sheet | VARCHAR(255) | 系統 | 面試準備表連結 |
| basic_info_sheet | VARCHAR(255) | 系統 | 基本資料表連結（{{面試前資料}} 後備來源） |
| **candidate_reply_link** | VARCHAR(255) | 系統 | **面試者回傳資料**（雲端問卷填畢後回寫；供主管檢視）。目前於寄信成功時**模擬**回寫 |
| mail_system_status | VARCHAR(20) | 系統 | 寄信成功寫「已處理」 |
| manager_assigned_at | TIMESTAMP | 系統 | 首次填 interview_manager 時記；供 7 天反灰判斷 |
| calendar_id | VARCHAR(120) | — | 保留，本期不做 |
| created_at / updated_at | | | |

### 4.3 manager_directory（面試主管信箱，名字唯一，10 筆）
`id, name, email`。供寄信解析 CC 主管信箱。名字忽略大小寫比對；查無則由名字推導 `<name>@hy-tech.com.tw`。

### 4.4 mail_template（信件範本）
`id, exam_type, location, job_title, body(CLOB)`，UNIQUE(exam_type, location, job_title)。選取鍵＝ examType × location × jobTitle。變數：`{{姓氏}}`、`{{先生/小姐}}`、`{{面試時間}}`、`{{面試前資料}}`。

### 4.5 mail_send_record（寄信結果＝冪等鍵）
`id, invitation_id UNIQUE, email, gender, job_title, exam_type, location, interview_time, form_link, cc_manager_email, sent_at, status(SUCCESS/FAILED), error_message`。`invitation_id` UNIQUE ＋寄前查詢 = 冪等。

---

## 5. 列舉（enums）

- **ContactStatus**：`EMAIL_CONTACT`(信件聯繫)、`SECOND_INVITE_PENDING`(二次邀約未回覆)、`COMMUNICATING`(信件溝通中)、`TIME_CONFIRMING`(時間確認中)、`INTERVIEW_CONFIRMED`(確認面試)、`OFFER_EXTENDED`(錄取人選)、`OFFER_ACCEPTED`(接受聘約)、`THANKS_LETTER`(感謝函)、`BLACKLIST`(黑名單)、`DECLINED`(婉拒)。**終止狀態＝ `OFFER_ACCEPTED` / `THANKS_LETTER` / `BLACKLIST` / `DECLINED`**；`OFFER_EXTENDED`（錄取人選）非終止（尚待人選回覆接受／婉拒）。
- **Gender**：`MALE` / `FEMALE`。
- **ExamType**：`BASIC`("基本") / `AI`("AI")。DB 存中文字面。
- **Location**：`NANGANG`("南港") / `BANQIAO`("板橋")。DB 存中文字面。
- **MailSendStatus**：`SUCCESS` / `FAILED`。

---

## 6. 聯繫狀態機（違反回 422 `ILLEGAL_STATE_TRANSITION`）

**主線**（由 `StatusMachine` 把關，至確認面試為止＋中途婉拒）：
```
EMAIL_CONTACT        → { SECOND_INVITE_PENDING, COMMUNICATING }
SECOND_INVITE_PENDING→ { COMMUNICATING, DECLINED }
COMMUNICATING        → { TIME_CONFIRMING, DECLINED }
TIME_CONFIRMING      → { INTERVIEW_CONFIRMED, DECLINED }
```

**面試結果**（確認面試後，由 `InvitationService.applyResult` 把關，**不走 StatusMachine**）：
```
INTERVIEW_CONFIRMED  → { OFFER_EXTENDED(錄取人選), THANKS_LETTER(感謝函), BLACKLIST(無故缺席) }
OFFER_EXTENDED       → { OFFER_ACCEPTED(接受聘約), DECLINED(婉拒／拒絕聘約) }
```
- **最終結果（流程結束）僅四種**：接受聘約(`OFFER_ACCEPTED`)、感謝函(`THANKS_LETTER`)、婉拒(`DECLINED`)、黑名單(`BLACKLIST`)。**錄取人選(`OFFER_EXTENDED`) 為過渡狀態、非最終結果**（result 欄留白）。有確實面試者結束於 { 接受聘約, 婉拒, 感謝函 }；無故缺席／反悔 → 黑名單。
- **HR 調整狀態（手動調整）**：確認面試後（含各結果狀態）可調整為四個最終結果之一（例：接受聘約後反悔改列黑名單）；不提供調整回「錄取人選」。後端 `applyResult` 仍接受 `OFFER_EXTENDED` 為目標（供「錄取人選」按鈕由 INTERVIEW_CONFIRMED 進入），但不寫入 result 欄。來源非「確認面試或結果狀態」→ 422；目標非結果狀態 → 400 `VALIDATION_ERROR`。
- 終止：`OFFER_ACCEPTED` / `THANKS_LETTER` / `BLACKLIST` / `DECLINED`（仍可由 HR 調整狀態）；`OFFER_EXTENDED` 非終止。

---

## 7. 業務規則（核心流程）

### 7.1 建立邀約（新增人選）與三個月防重複
- 建立時狀態＝`EMAIL_CONTACT`。**inviter 由 HR 於表單自填**（留空則後端帶預設 `amy.chung`）。
- **必填欄位**：`name`、`email`、`channel`（招募管道）、`job_title`（職位名稱）皆 `@NotBlank`；`inviter` 於前端亦要求必填。缺漏 → 400 `VALIDATION_ERROR`；前端另以**彈跳視窗（alert）**列出未填欄位並擋下儲存。
- **手機格式**：`phone` 為選填。有填時去除所有非數字後須為 **09 開頭共 10 碼**（`09\d{8}`），使用者**可不輸入「-」**；通過後後端統一格式化為 `0966-888-999`（4-3-3）儲存與顯示，非法 → 400 `VALIDATION_ERROR`（「手機格式錯誤，須為 09 開頭共 10 碼」）。`create` 與 `PATCH /invitations/{id}` 皆套用（`InvitationService.normalizePhone`）。
- **90 天防重複**（避免重複邀請）：比對鍵為**「同名＋手機」，不只擋手機號碼**（避免同號不同人或填錯誤傷）；**未提供手機時改以「同名＋同信箱」**。範圍限近 90 天內曾有 `invite_sent_at`（一面邀請記錄時間）者。命中 → 409 `DUPLICATE_INVITE_WITHIN_3M`（含 `existingInvitationId`）。`send-invite` 時也再查一次（排除自己）。
- **自動帶入履歷**：新增成功後系統以 `https://drive.example.invalid/resume/{invitationId}` 寫入 `resume_link` 並回傳（本期為假資料）。

### 7.2 一邀 / 二邀（**HR 手動、系統不寄信**）
- `send-invite`：僅 `EMAIL_CONTACT` 可用，**只記錄** `invite_sent_at`＝現在，狀態不變。代表「HR 已於外部平台寄出一面邀請」。
- `second-invite`：由 `EMAIL_CONTACT`（或重寄時的 `SECOND_INVITE_PENDING`）→ 轉 `SECOND_INVITE_PENDING`（二次邀約未回覆），並記 `second_invite_at`＝現在（＝二邀寄送時間）。
- 前端「二邀時間」欄邏輯：有 `second_invite_at` → 顯示該時間；否則若 `EMAIL_CONTACT` 且 `invite_sent_at` 已逾 24hr → 顯示粉色「需要二邀」；否則「—」。
- 前端「更新聯繫狀態」以**單一按鈕「確認寄送邀請」**驅動：尚未記錄一邀 → 可點（send-invite）；一邀 < 24hr → **反灰**（disabled）；一邀 ≥ 24hr → 變「確認寄送二次邀約」（second-invite）。

### 7.3 進入溝通（轉交主管評估）
- `reply-received`：任一可轉狀態 → `COMMUNICATING`（前端按鈕文案為「回覆有意願 → 轉交主管評估」，狀態仍是信件溝通中）；**單向建立面試安排**（若尚未存在，帶入 candidateName/jobTitle/resumeLink），並寄**人才推薦信**給主管（mock）。
- 前端**不再提供手動「履歷連結」輸入**；面試安排的 `resumeLink` 沿用建立時自動帶入者（`inv.resumeLink`）。`reply-received` body 可為空。

### 7.4 排面試時間（**整併動作**，面試安排的核心）
`PATCH /arrangements/{id}/interview-time` 一次做完，並在資料齊備時觸發寄信：
- `interviewTime`：必填、`@Future`（不可過去，違反 400）。
- `gender` / `examType` / `location`：欄位級更新（只寫有傳入者）；examType 只接受 基本/AI、location 只接受 南港/板橋，非法值 400 `VALIDATION_ERROR`。
- `selectedManager`：多位主管時須在 interview_manager 清單內（忽略大小寫），否則 422 `MANAGER_SELECTION_REQUIRED`；單一主管可留空（自動帶入）。
- **落地後**（於交易外）呼叫 `autoTriggerMailIfReady`：**當狀態＝INTERVIEW_CONFIRMED 且 gender/examType/location 皆齊備**才委由 `MailService.triggerMail` 寄「面試通知信」；否則略過（不寄、不報錯）。刻意在交易外觸發，讓寄信的冪等紀錄不因排時間交易回滾。
- 前端 **防呆**：「儲存並排定」須 時間(未來) + 性別 + 上機考題目 + 面試地點 皆填選（下拉預設值為「未填」空字串），多主管時須選定一位，否則以 toast 擋下。

> 已移除的舊端點：`PATCH /arrangements/{id}/basic-info`、`PATCH /arrangements/{id}/select-manager` 已整併進 `interview-time`，**不要**再新增回來。

### 7.5 主管欄位（manager-fields，白名單）
`PATCH /arrangements/{id}/manager-fields`，body 僅允許 `interviewManager` / `managerPreferredDates` / `managerRemark`（皆可多值、日期可換行）；出現任何其他欄位 → 403 `FIELD_NOT_WRITABLE_BY_ROLE`。首次填 `interviewManager` 記 `manager_assigned_at`。

### 7.6 寄信（MailService.triggerMail，同步 + 冪等）
1. 冪等：已存在 SUCCESS 紀錄 → 直接回，不重寄。
2. 基本資料/職稱一律取自**面試安排**。
3. 狀態須 `INTERVIEW_CONFIRMED`，否則落 FAILED「尚未確認面試」（不丟例外）。
4. 必填校驗：gender / examType(基本/AI) / location(南港/板橋)，缺漏 → 落 FAILED 並丟 422 `MAIL_REQUIRED_FIELDS_MISSING`。
5. CC 主管：單一自動帶入；多位須已 `selectedManager` 選定，否則 422 `MANAGER_SELECTION_REQUIRED`。信箱查 `manager_directory`。
6. 選範本：examType × location × jobTitle，查無 → 落 FAILED 並丟 422 `TEMPLATE_NOT_FOUND`。
7. 變數合併：`{{姓氏}}`(name 首字)、`{{先生/小姐}}`(FEMALE→小姐，否則先生)、`{{面試時間}}`、`{{面試前資料}}`(request.formLink 優先，否則 basic_info_sheet)。
8. 成功：寫 SUCCESS；面試安排 `mail_system_status`＝「已處理」；並**模擬**回寫 `candidate_reply_link`＝`https://forms.example.invalid/reply/{invitationId}`（代表面試者收到雲端問卷、填完由問卷系統自動回寫；本期無真實問卷系統）。
- `MailSender` 為 mock（同步、無佇列）。`send-recommendation` 另寄人才推薦信給主管群組（mock）。

### 7.7 面試結果與編輯結果（`InvitationService.applyResult`）
- 統一入口：`POST /invitations/{id}/result`，body `{ result, reason? }`；`result` 僅接受五個結果狀態，其餘 → 400 `VALIDATION_ERROR`。
- 來源狀態須為 `INTERVIEW_CONFIRMED` 或任一結果狀態，否則 422 `ILLEGAL_STATE_TRANSITION`。
- 設定 `contact_status`＝目標；`result` 欄僅在目標為最終結果時寫入，目標為 `OFFER_EXTENDED` 時 result 留白（非最終結果）。目標為 `BLACKLIST`／`DECLINED` 時把**選填** `reason` 寫入 `decline_reason`，其餘結果則清空 `decline_reason`。
- 前端動作對應：確認面試 → 錄取人選(`OFFER_EXTENDED`)／感謝函(`THANKS_LETTER`)／無故缺席→黑名單(`BLACKLIST`，理由選填)；錄取人選 → 接受聘約(`OFFER_ACCEPTED`)／婉拒(`DECLINED`，理由選填)。
- **調整狀態（手動調整）**：終止狀態與錄取人選皆提供「調整狀態」**按鈕（預設收合，點擊才展開）**（同走 `result` 端點），下拉僅四個最終結果 { 接受聘約, 感謝函, 婉拒, 黑名單 }＋選填理由；典型情境：接受聘約後人選來電反悔 → 改列黑名單並選填理由。
- 舊 `blacklist` 端點保留：等同 `applyResult(BLACKLIST, null)`（無故缺席快捷）。中途 `decline`（主線婉拒，原因必填）不變，且不寫 `result` 欄。

---

## 8. API 端點總覽（base `/api/v1`）

| 方法 | 路徑 | 說明 |
|---|---|---|
| POST | `/invitations` | 建立邀約（body 含 inviter；三個月防重命中 409） |
| GET | `/invitations` | 列表（?status=&channel=&keyword=&page=&size=） |
| GET | `/invitations/{id}` | 單筆 |
| PATCH | `/invitations/{id}` | 欄位級更新（name/email/phone/resumeNo/channel/jobTitle） |
| GET | `/invitations/check-duplicate` | ?name=&email=&phone=（同名＋手機／無手機則同名＋信箱） |
| POST | `/invitations/{id}/send-invite` | 記錄一面邀請時間（系統不寄信） |
| POST | `/invitations/{id}/second-invite` | → SECOND_INVITE_PENDING，記二邀時間（系統不寄信） |
| POST | `/invitations/{id}/reply-received` | → COMMUNICATING，建立面試安排＋寄推薦信 |
| POST | `/invitations/{id}/confirm-time` | → TIME_CONFIRMING |
| POST | `/invitations/{id}/confirm-interview` | → INTERVIEW_CONFIRMED |
| POST | `/invitations/{id}/decline` | 主線婉拒 → DECLINED（body: declineReason；前端以「人選婉拒／主管不邀約」兩鈕帶入，原因欄選填、留空以按鈕分類為原因） |
| POST | `/invitations/{id}/blacklist` | 無故缺席快捷 → BLACKLIST（無 body） |
| POST | `/invitations/{id}/result` | 設定／編輯結果（body: `{result, reason?}`；result∈五個結果狀態，reason 選填僅黑名單／婉拒採用） |
| POST | `/invitations/{id}/trigger-mail` | 同步寄面試通知（body 僅可選 formLink） |
| POST | `/invitations/{id}/send-recommendation` | 寄推薦信給主管 |
| GET | `/arrangements` | 面試安排列表（?manager=，包含比對多主管） |
| GET | `/arrangements/{id}` | 單筆 |
| PATCH | `/arrangements/{id}/manager-fields` | 主管白名單欄位（其他欄 403） |
| PATCH | `/arrangements/{id}/interview-time` | 排面試時間整併動作（時間＋基本資料＋選定主管＋條件式自動寄信） |
| GET | `/mail-records?invitationId=` | 查寄信結果 |
| GET | `/mail-templates` | 範本清單（?examType=&location=&jobTitle=） |

---

## 9. 回應信封與錯誤碼

```jsonc
// 成功
{ "success": true, "data": { ... } }
// 失敗
{ "success": false, "error": { "code": "DUPLICATE_INVITE_WITHIN_3M", "message": "...", "existingInvitationId": 1 } }
```

| 情境 | HTTP | code |
|---|---|---|
| 三個月內重複邀請 | 409 | `DUPLICATE_INVITE_WITHIN_3M` |
| 非法狀態轉移 | 422 | `ILLEGAL_STATE_TRANSITION` |
| 主管寫非白名單欄位 | 403 | `FIELD_NOT_WRITABLE_BY_ROLE` |
| 查無信件範本 | 422 | `TEMPLATE_NOT_FOUND` |
| 寄信必填缺漏（性別/試題類型/地點） | 422 | `MAIL_REQUIRED_FIELDS_MISSING` |
| 多位主管未先選定一位 | 422 | `MANAGER_SELECTION_REQUIRED` |
| 資源不存在 | 404 | `NOT_FOUND` |
| 參數驗證失敗（含必填缺漏、手機非 09 開頭共 10 碼、examType/location 非法、時間過去） | 400 | `VALIDATION_ERROR` |

---

## 10. 前端（兩支靜態頁；連不到 API 時走離線示範模式，資料寫死於各頁 SEED）

### 10.1 index.html（邀約紀錄）
- 表格欄：編號、**邀請人**、狀態、姓名、職位名稱、招募管道、履歷編號、E-Mail、手機、一面邀請時間、二邀時間、**最終結果、備註**（`最終結果` 在 `備註` 左側；`備註` 即 `decline_reason` 欄，涵蓋婉拒／黑名單／無故缺席／拒絕聘約等理由）、**更新時間**（顯示 `updatedAt`，離線 SEED 無值則退回 `createdAt`）、操作。
- 新增求職者表單欄位：姓名*、E-Mail*、手機、履歷編號、招募管道*、職位名稱*、**邀請人***（HR 自填，英文名.英文姓氏）。**不含性別**。
  - **必填防呆**：表單 `novalidate`（關閉瀏覽器原生提示氣泡）。送出前檢核 姓名／E-Mail／招募管道／職位名稱／邀請人；任一未填 → 於該輸入欄下方顯示紅字「此為必填欄位，請勿空白」、輸入框標紅、擋下儲存（**不跳彈跳視窗、無原生氣泡**）。E-Mail 已填但格式錯誤 → 顯示「E-Mail 格式錯誤」。使用者重新輸入即清除該欄錯誤。
  - **手機驗證**：選填；有填則去除非數字後須為 09 開頭共 10 碼。可不輸入「-」，建立成功後畫面顯示為 `0966-888-999` 格式（後端格式化）。格式未過時同樣於手機欄下方顯示紅字並標紅、擋下儲存。
  - **履歷編號**：僅 104 求職管道才有；其他管道留空。**招募管道含「104」時履歷編號必填**，未填則於欄下顯示紅字「此為必填欄位，請勿空白」、擋下儲存（改動招募管道會清除此錯誤）。
  - **履歷連結**：index 頁不提供任何履歷連結輸入或提示（「進入溝通」不再有履歷連結欄、建立成功亦不再跳履歷連結 toast）。後端建立時仍自動寫入 `resume_link`，供面試安排的履歷欄使用。
- 「管理」抽屜：`邀約資料`（name/email/phone/resumeNo/channel/jobTitle，PATCH 更新）＋`更新聯繫狀態`（依狀態顯示動作，含 7.2 的確認寄送邀請按鈕邏輯、進入溝通、中途婉拒等）。
- **面試結果動作**（見 7.7）：確認面試 → 「錄取（發錄取通知）」(OFFER_EXTENDED)／「未錄取（發感謝函）」(THANKS_LETTER)／「黑名單」(BLACKLIST，無故缺席、理由選填)；錄取人選 → 「接受聘約」(OFFER_ACCEPTED)／「婉拒聘約」(DECLINED，理由選填)＋「調整狀態」；終止狀態顯示「此為終止狀態」並提供「**調整狀態**」按鈕（預設收合，點擊才展開：下拉四個最終結果〔接受聘約／感謝函／婉拒／黑名單〕＋選填理由，供反悔改判等）。錄取人選為過渡、非最終結果，故最終結果欄留白、下拉亦不含它。
- **主線婉拒**（declineBox，出現於 SECOND_INVITE_PENDING／COMMUNICATING／TIME_CONFIRMING）：改為兩顆按鈕「**人選婉拒**」「**主管不邀約**」皆走 `decline`→DECLINED；**婉拒原因欄改為選填**（移除「必填」字樣與 `*`），留空則以按鈕分類文字（人選婉拒／主管不邀約）作為婉拒原因，有填則用填寫內容。
- **動作按鈕配色**：面試結果／婉拒相關按鈕顏色一律與目標狀態碼一致——錄取(#b0790d)、未錄取/感謝函(#5a6577)、接受聘約(#0f766e)、黑名單(#1f2430)、婉拒聘約／人選婉拒／主管不邀約(#9aa2b1，深色字)。
- 二邀時間欄「需要二邀」/「二邀寄送時間」邏輯見 7.2。

### 10.2 arrangements.html（面試安排）
- 角色切換：HR / 主管（純前端視角，欄位可寫性由後端白名單把關）。
- 表格欄：編號、候選人、職位名稱、履歷、面試主管、可面試日期、面試時間、主管備註、**面試者回傳資料**、操作（表頭無角色標籤）。
- 主管篩選：**下拉選單**（由現有資料彙整主管名字；包含比對，選 John 也對到「Sherry/John」）。
- 主管抽屜：面試主管、可面試日期（textarea 可換行）、主管備註。
- HR 抽屜「排面試時間」：面試時間（min＝現在）＋性別/上機考題目/面試地點（下拉，預設「未填」）＋多主管時「選定單一主管」；防呆見 7.4。
- 面試者回傳資料：表格與抽屜以連結呈現（未回傳則顯示「未回傳」）。

---

## 11. 假資料重點（sql_script.sql）
- invitation_record 5 筆涵蓋各狀態；inviter 已帶入（amy.chung / john.lee / grace.cheng）；`resume_link` 皆已帶入 `.../resume/{id}`；phone 皆為 `0966-888-999` 格式。id1 王小明 EMAIL_CONTACT 且一邀逾 24hr（示範「需要二邀」）；id2 李佳穎 SECOND_INVITE_PENDING 且有二邀時間。
- interview_arrangement 5 筆；id2/id3 有 candidate_reply_link（示範已回傳）。manager_remark 僅 id2 保留「優先給 Sherry 面試」。
- manager_directory 10 筆（名字唯一，已無 Eric/Eric.J 重複）。
- mail_template 5 筆、mail_send_record 5 筆（SUCCESS/FAILED 混合）。

---

## 12. 變更本規格時的注意事項
- 動到欄位 → 同步：Entity、對應 DTO(request/response)、`sql_script.sql`、`reset-identity.sql`、前端表格/表單、README、本 SPEC。
- 動到端點 → 同步：Controller、Service、README 端點表、`requests.http` 範例、前端呼叫、本 SPEC。
- 每次改完務必 `mvn clean package` 建置通過，並以 `--server.port=8081` 實跑驗證關鍵流程，勿只改不驗。
