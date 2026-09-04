package com.hytech.recruitment.domain.enums;

/**
 * 上機試題類型／上機考題目（範本選取維度之一，HR 於面試安排以下拉選單填）。
 * 值域僅「基本／AI」兩種；DB 以中文字面儲存，實體與範本以字串比對，
 * 此列舉提供字面對照與驗證。
 */
public enum ExamType {
    BASIC("基本"),
    AI("AI");

    private final String label;

    ExamType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 由中文字面反查列舉；查無回傳 null。 */
    public static ExamType fromLabel(String label) {
        if (label == null) return null;
        for (ExamType e : values()) {
            if (e.label.equals(label)) return e;
        }
        return null;
    }
}
