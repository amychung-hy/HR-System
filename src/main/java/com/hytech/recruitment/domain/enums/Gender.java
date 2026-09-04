package com.hytech.recruitment.domain.enums;

/** 性別（寄信稱謂用）。 */
public enum Gender {
    MALE("先生"),
    FEMALE("小姐");

    private final String honorific;

    Gender(String honorific) {
        this.honorific = honorific;
    }

    /** 稱謂：先生／小姐。 */
    public String getHonorific() {
        return honorific;
    }
}
