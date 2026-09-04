package com.hytech.recruitment.domain.enums;

/**
 * 面試地點（範本選取維度之一）。
 * DB 以中文字面儲存（南港／板橋），實體與範本以字串比對；
 * 此列舉提供字面對照與驗證。
 */
public enum Location {
    NANGANG("南港"),
    BANQIAO("板橋");

    private final String label;

    Location(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 由中文字面反查列舉；查無回傳 null。 */
    public static Location fromLabel(String label) {
        if (label == null) return null;
        for (Location l : values()) {
            if (l.label.equals(label)) return l;
        }
        return null;
    }
}
