# 招募系統 · 系統設計說明（精簡版）

> 一頁看懂本系統。詳規以 [SPEC.md](SPEC.md) 與程式碼現況為準。

## 1. 系統定位
HR 招募流程管理系統。核心主線以單一 `invitationId` 串接三個資源：

```
邀約紀錄(InvitationRecord) → 面試安排(InterviewArrangement) → 寄信(MailSendRecord) → 結果
```

系統只做「記錄與流轉」；一/二面邀請信、與求職者的溝通信皆由 HR 於外部平台手動處理，系統僅存時間與狀態。系統真正會寄（mock/SMTP）的只有**面試通知信**與**人才推薦信**。

## 2. 技術棧
| 項目 | 選型 |
|---|---|
| 框架 | Spring Boot 3.3.4（Web / Data JPA / Bean Validation / Mail） |
| 語言/建置 | Java 21、Maven |
| 資料庫 | H2 in-memory（啟動由 `db/sql_script.sql` 建表＋載入假資料） |
| IoC | 一律 Field Injection（`@Autowired` 於欄位，硬性要求） |
| 前端 | 兩支純靜態 HTML，離線可示範、連得到後端即走真實 API |
| 授權 | 本期暫緩，主管欄位以商業邏輯白名單把關 |

## 3. 分層架構
```
Controller → Service → Repository(JPA) → H2
   ├ common/     ApiResponse 統一信封、ErrorCode、BusinessException、GlobalExceptionHandler
   ├ service/    InvitationService、InterviewArrangementService、MailService
   │             StatusMachine(狀態機)、ManagerResolver(主管信箱)、MailSender(mock/SMTP)
   └ scheduler/  RecruitmentScheduler 每日排程（二邀偵測、主管反灰提示）
```
所有錯誤走 `BusinessException + ErrorCode`，由 `GlobalExceptionHandler` 統一包成回應信封。

## 4. 領域模型（5 張表）
| 表 | 角色 | 要點 |
|---|---|---|
| `invitation_record` | 邀約主檔（單一真實來源，id＝invitationId） | 含 inviter/狀態/一邀·二邀時間/結果。**不含**性別·上機考題目·地點 |
| `interview_arrangement` | 面試安排（一位一列，invitation_id UNIQUE） | 基本資料(gender/examType/location)於此由 HR 填；主管欄位＋回傳資料連結 |
| `manager_directory` | 面試主管信箱（名字唯一） | 供寄信解析 CC 主管；查無則推導 `<name>@hy-tech.com.tw` |
| `mail_template` | 信件範本 | 選取鍵＝examType × location × jobTitle |
| `mail_send_record` | 寄信結果（invitation_id UNIQUE＝冪等鍵） | SUCCESS/FAILED |

**設計重點**：性別 / 上機考題目(examType 只有 基本·AI) / 面試地點(location 只有 南港·板橋) 三項「基本資料」只存在於面試安排，由 HR 於「排面試時間」填，不放回邀約主檔。

## 5. 聯繫狀態機
```
EMAIL_CONTACT ─┬→ SECOND_INVITE_PENDING ─→ COMMUNICATING
               └→ COMMUNICATING ─→ TIME_CONFIRMING ─→ INTERVIEW_CONFIRMED
INTERVIEW_CONFIRMED ─→ { OFFER_EXTENDED(錄取人選) | THANKS_LETTER | BLACKLIST(無故缺席) }
OFFER_EXTENDED ─→ { OFFER_ACCEPTED(接受聘約) | DECLINED(婉拒/拒絕聘約) }
主線任一階段可 ─→ DECLINED（原因必填）
終止態：OFFER_ACCEPTED / THANKS_LETTER / BLACKLIST / DECLINED；OFFER_EXTENDED 非終止
HR 編輯結果：確認面試後可於五個結果狀態間手動調整（黑名單/婉拒理由選填）
```
主線非法轉移回 422 `ILLEGAL_STATE_TRANSITION`；結果流轉由 `applyResult` 把關（來源須確認面試後、目標須為結果狀態）。

## 6. 核心業務規則
- **三個月防重複**：同一 email/phone 90 天內曾有 `invite_sent_at` → 建立回 409（含 `existingInvitationId`）；send-invite 再查一次。
- **一邀/二邀不寄信**：send-invite 只記 `invite_sent_at`；一邀逾 24hr 未回可轉二邀。系統只記錄，HR 在外部平台實際寄。
- **排面試時間（整併動作）**：`PATCH /arrangements/{id}/interview-time` 一次帶入 時間(不可過去)＋基本資料＋選定主管；落地後於**交易外**判斷「已確認面試且基本資料齊備」即自動觸發寄信（交易外觸發，避免冪等紀錄隨排時間交易回滾）。
- **寄信（同步＋冪等）**：已 SUCCESS 直接返回；狀態須 INTERVIEW_CONFIRMED；校驗基本資料 → 選範本 → 合併變數(`{{姓氏}}`等) → CC 選定主管 → 寫紀錄，並模擬回寫面試者回傳資料連結。
- **主管欄位白名單**：`manager-fields` 僅允許 interviewManager/managerPreferredDates/managerRemark，其他欄回 403。

## 7. API 與回應信封
Base：`/api/v1`。三組端點：`/invitations`（邀約＋狀態流轉動作）、`/arrangements`（面試安排）、`/mail-records`·`/mail-templates`（寄信）。

```jsonc
成功 { "success": true,  "data": { ... } }
失敗 { "success": false, "error": { "code": "...", "message": "...", "existingInvitationId": 1 } }
```

| 情境 | HTTP / code |
|---|---|
| 三個月內重複 | 409 `DUPLICATE_INVITE_WITHIN_3M` |
| 非法狀態轉移 | 422 `ILLEGAL_STATE_TRANSITION` |
| 主管寫非白名單欄位 | 403 `FIELD_NOT_WRITABLE_BY_ROLE` |
| 寄信必填缺漏 | 422 `MAIL_REQUIRED_FIELDS_MISSING` |
| 多主管未選定 | 422 `MANAGER_SELECTION_REQUIRED` |
| 查無範本 | 422 `TEMPLATE_NOT_FOUND` |
| 參數驗證失敗 | 400 `VALIDATION_ERROR` |

## 8. 前端
兩支靜態頁：`index.html`（邀約紀錄，含新增求職者/管理抽屜/聯繫狀態流轉）、`arrangements.html`（面試安排，HR/主管視角切換、主管下拉篩選、排面試時間防呆）。連不到 API 時走各頁 SEED 離線示範。
