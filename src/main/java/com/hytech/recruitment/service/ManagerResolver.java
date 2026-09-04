package com.hytech.recruitment.service;

import com.hytech.recruitment.common.BusinessException;
import com.hytech.recruitment.common.ErrorCode;
import com.hytech.recruitment.domain.entity.InterviewArrangement;
import com.hytech.recruitment.repository.ManagerDirectoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 面試主管名字的解析與 CC 信箱決策。
 * <p>面試主管欄可多人，生產環境以 {@code /} 或 {@code &} 分隔（亦相容 {@code 、}、逗號）。</p>
 * <p>CC 規則（依需求）：單一主管自動帶入；多位主管由 HR 於面試安排選擇——
 * 「不選定」則全部主管一起 CC，選定某位則只 CC 該位主管信箱。</p>
 */
@Service
public class ManagerResolver {

    /** 主管分隔符：斜線、和號、頓號、半形逗號。 */
    private static final String SPLIT_REGEX = "[/、,&]";

    @Autowired
    private ManagerDirectoryRepository directoryRepository;

    /** 解析面試主管欄為去重、保序的名字清單。 */
    public List<String> parseManagers(String interviewManager) {
        List<String> result = new ArrayList<>();
        if (interviewManager == null || interviewManager.isBlank()) {
            return result;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String part : interviewManager.split(SPLIT_REGEX)) {
            String name = part.trim();
            if (!name.isEmpty() && seen.add(name.toLowerCase())) {
                result.add(name);
            }
        }
        return result;
    }

    /**
     * 決定觸發寄信時要 CC 的面試主管名字清單。
     * <ul>
     *   <li>無主管 → 空清單（不 CC）。</li>
     *   <li>單一主管 → 該位（自動帶入）。</li>
     *   <li>多位主管且未選定（不選定）→ 全部主管一起 CC。</li>
     *   <li>多位主管且已選定 → 只 CC 選定該位；selectedManager 不在清單 → 422 MANAGER_SELECTION_REQUIRED。</li>
     * </ul>
     * 回傳清單中的正規名字（保留原始大小寫、保序）。
     */
    public List<String> chooseCcManagers(InterviewArrangement arr) {
        List<String> managers = parseManagers(arr.getInterviewManager());
        if (managers.isEmpty()) {
            return List.of();
        }
        if (managers.size() == 1) {
            return List.of(managers.get(0));
        }
        String selected = arr.getSelectedManager();
        if (selected == null || selected.isBlank()) {
            // 不選定：多位主管一起 CC
            return List.copyOf(managers);
        }
        String canonical = matchInList(managers, selected)
                .orElseThrow(() -> new BusinessException(ErrorCode.MANAGER_SELECTION_REQUIRED,
                        "selectedManager「" + selected + "」不在面試主管清單內：" + arr.getInterviewManager())
                        .with("selectedManager", selected));
        return List.of(canonical);
    }

    /** 於清單中以忽略大小寫比對，回傳清單內的正規名字。 */
    public java.util.Optional<String> matchInList(List<String> managers, String name) {
        if (name == null) return java.util.Optional.empty();
        String target = name.trim();
        return managers.stream().filter(m -> m.equalsIgnoreCase(target)).findFirst();
    }

    /**
     * 名字 → CC 信箱。先查通訊錄（忽略大小寫）；查無則以名字推導（模擬）。
     */
    public String resolveEmail(String managerName) {
        if (managerName == null || managerName.isBlank()) {
            return null;
        }
        return directoryRepository.findByNameIgnoreCase(managerName.trim())
                .map(m -> m.getEmail())
                .orElseGet(() -> managerName.trim().toLowerCase().replaceAll("\\s+", ".") + "@hy-tech.com.tw");
    }

    /** 名字清單 → CC 信箱清單（去重、保序；略過解不出信箱者）。 */
    public List<String> resolveEmails(List<String> managerNames) {
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        for (String name : managerNames) {
            String email = resolveEmail(name);
            if (email != null && !email.isBlank()) {
                emails.add(email);
            }
        }
        return new ArrayList<>(emails);
    }
}
