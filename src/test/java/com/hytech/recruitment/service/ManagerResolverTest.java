package com.hytech.recruitment.service;

import com.hytech.recruitment.common.BusinessException;
import com.hytech.recruitment.common.ErrorCode;
import com.hytech.recruitment.domain.entity.InterviewArrangement;
import com.hytech.recruitment.domain.entity.ManagerDirectory;
import com.hytech.recruitment.repository.ManagerDirectoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** 面試主管解析與 CC 決策單元測試。 */
@DisplayName("面試主管解析與 CC 決策")
@ExtendWith(MockitoExtension.class)
class ManagerResolverTest {

    @Mock
    private ManagerDirectoryRepository directoryRepository;

    @InjectMocks
    private ManagerResolver resolver;

    private InterviewArrangement arr(String interviewManager, String selectedManager) {
        InterviewArrangement a = new InterviewArrangement();
        a.setInterviewManager(interviewManager);
        a.setSelectedManager(selectedManager);
        return a;
    }

    @Test
    @DisplayName("parseManagers：去重保序並支援多分隔符")
    void parseManagers_dedupOrdered_multiSeparators() {
        assertEquals(List.of("Tom", "John", "Sherry"),
                resolver.parseManagers("Tom / John、Sherry & tom"));
    }

    @Test
    @DisplayName("parseManagers：空白回空清單")
    void parseManagers_blank_returnsEmpty() {
        assertTrue(resolver.parseManagers(null).isEmpty());
        assertTrue(resolver.parseManagers("   ").isEmpty());
    }

    @Test
    @DisplayName("chooseCc：無主管回空")
    void chooseCc_noManager_returnsEmpty() {
        assertTrue(resolver.chooseCcManagers(arr(null, null)).isEmpty());
    }

    @Test
    @DisplayName("chooseCc：單一主管自動帶入")
    void chooseCc_singleManager_autoInclude() {
        assertEquals(List.of("Tom"), resolver.chooseCcManagers(arr("Tom", null)));
    }

    @Test
    @DisplayName("chooseCc：多位未選定全部 CC")
    void chooseCc_multipleUnselected_ccAll() {
        assertEquals(List.of("Tom", "John"), resolver.chooseCcManagers(arr("Tom/John", null)));
    }

    @Test
    @DisplayName("chooseCc：多位已選定只 CC 該位（忽略大小寫）")
    void chooseCc_multipleSelected_ccOne_caseInsensitive() {
        assertEquals(List.of("John"), resolver.chooseCcManagers(arr("Tom/John", "john")));
    }

    @Test
    @DisplayName("chooseCc：多位選定不在清單丟 MANAGER_SELECTION_REQUIRED")
    void chooseCc_selectedNotInList_throwsManagerSelectionRequired() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> resolver.chooseCcManagers(arr("Tom/John", "Mary")));
        assertEquals(ErrorCode.MANAGER_SELECTION_REQUIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("matchInList：忽略大小寫回正規名字")
    void matchInList_caseInsensitive_returnsCanonical() {
        assertEquals(Optional.of("Sherry"),
                resolver.matchInList(List.of("Tom", "Sherry"), "sherry"));
        assertTrue(resolver.matchInList(List.of("Tom"), "none").isEmpty());
        assertTrue(resolver.matchInList(List.of("Tom"), null).isEmpty());
    }

    @Test
    @DisplayName("resolveEmail：查通訊錄優先")
    void resolveEmail_directoryFirst() {
        ManagerDirectory md = new ManagerDirectory();
        md.setName("Tom");
        md.setEmail("tom@hy-tech.com.tw");
        when(directoryRepository.findByNameIgnoreCase("Tom")).thenReturn(Optional.of(md));
        assertEquals("tom@hy-tech.com.tw", resolver.resolveEmail("Tom"));
    }

    @Test
    @DisplayName("resolveEmail：查無則以名字推導")
    void resolveEmail_notFound_derivesFromName() {
        when(directoryRepository.findByNameIgnoreCase("John Doe")).thenReturn(Optional.empty());
        assertEquals("john.doe@hy-tech.com.tw", resolver.resolveEmail("John Doe"));
    }

    @Test
    @DisplayName("resolveEmail：空白回 null")
    void resolveEmail_blank_returnsNull() {
        assertEquals(null, resolver.resolveEmail(" "));
    }

    @Test
    @DisplayName("resolveEmails：去重保序")
    void resolveEmails_dedupOrdered() {
        lenient().when(directoryRepository.findByNameIgnoreCase("Tom")).thenReturn(Optional.empty());
        lenient().when(directoryRepository.findByNameIgnoreCase("John")).thenReturn(Optional.empty());
        assertEquals(List.of("tom@hy-tech.com.tw", "john@hy-tech.com.tw"),
                resolver.resolveEmails(List.of("Tom", "John")));
    }
}
