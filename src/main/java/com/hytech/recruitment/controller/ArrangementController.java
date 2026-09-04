package com.hytech.recruitment.controller;

import com.hytech.recruitment.common.ApiResponse;
import com.hytech.recruitment.dto.request.InterviewTimeRequest;
import com.hytech.recruitment.dto.response.ArrangementResponse;
import com.hytech.recruitment.service.InterviewArrangementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 面試安排端點。Base：/api/v1/arrangements。
 * 主管欄位白名單由 service 把關。
 */
@RestController
@RequestMapping("/api/v1/arrangements")
public class ArrangementController {

    @Autowired
    private InterviewArrangementService arrangementService;

    @GetMapping
    public ApiResponse<List<ArrangementResponse>> list(@RequestParam(required = false) String manager) {
        return ApiResponse.ok(arrangementService.list(manager));
    }

    @GetMapping("/{id}")
    public ApiResponse<ArrangementResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(arrangementService.get(id));
    }

    /** 主管填 interviewManager／managerPreferredDates／managerRemark；其他欄位一律拒絕（403）。 */
    @PatchMapping("/{id}/manager-fields")
    public ApiResponse<ArrangementResponse> managerFields(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(arrangementService.updateManagerFields(id, body));
    }

    /**
     * HR「排面試時間」單一整併動作：設定一面時間 + 基本資料（性別／上機考題目／面試地點）
     * + 選定單一主管（多主管時），並於資料齊備、已確認面試時觸發寄信（CC 該主管、發送範本信）。
     * <p>先落地資料（交易內），再於交易外觸發寄信，最後回傳最新面試安排（含 mailSystemStatus）。</p>
     */
    @PatchMapping("/{id}/interview-time")
    public ApiResponse<ArrangementResponse> interviewTime(@PathVariable Long id,
                                                          @RequestParam(defaultValue = "true") boolean sendMail,
                                                          @Valid @RequestBody InterviewTimeRequest req) {
        arrangementService.updateInterviewTime(id, req);
        // sendMail=false：僅「儲存」（不發信）；sendMail=true：「儲存並發信」（資料齊備且已確認面試才實際寄出）
        if (sendMail) {
            arrangementService.autoTriggerMailIfReady(id);
        }
        return ApiResponse.ok(arrangementService.get(id));
    }
}
