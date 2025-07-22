package com.fincity.saas.message.model.message.whatsapp.templates.type;

import com.fasterxml.jackson.annotation.JsonValue;


public enum LanguageType {

    
    AF("af"),
    
    SQ("sq"),
    
    AR("ar"),
    
    AZ("az"),
    
    BN("bn"),
    
    BG("bg"),
    
    CA("ca"),
    
    ZH_CN("zh_CN"),
    
    ZH_HK("zh_HK"),
    
    ZH_TW("zh_TW"),
    
    HR("hr"),
    
    CS("cs"),
    
    DA("da"),
    
    NL("nl"),
    
    EN("en"),
    
    EN_GB("en_GB"),
    
    EN_US("en_US"),
    
    ET("et"),
    
    FIL("fil"),
    
    FI("fi"),
    
    FR("fr"),
    
    KA("ka"),
    
    DE("de"),
    
    EL("el"),
    
    GU("gu"),
    
    HA("ha"),
    
    ha("he"),
    
    HI("hi"),
    
    HU("hu"),
    
    ID("id"),
    
    GA("ga"),
    
    IT("it"),
    
    JA("ja"),
    
    KN("kn"),
    
    KK("kk"),
    
    RW_RW("rw_RW"),
    
    KO("ko"),
    
    KY_KG("ky_KG"),
    
    LO("lo"),
    
    LV("lv"),
    
    LT("lt"),
    
    MK("mk"),
    
    MS("ms"),
    
    ML("ml"),
    
    MR("mr"),
    
    NB("nb"),
    
    FA("fa"),
    
    PL("pl"),
    
    PT_BR("pt_BR"),
    
    PT_PT("pt_PT"),
    
    PA("pa"),
    
    RO("ro"),
    
    RU("ru"),
    
    SR("sr"),
    
    SK("sk"),
    
    SL("sl"),
    
    ES("es"),
    
    ES_AR("es_AR"),
    
    ES_ES("es_ES"),
    
    ES_MX("es_MX"),
    
    SW("sw"),
    
    SV("sv"),
    
    TA("ta"),
    
    TE("te"),
    
    TH("th"),
    
    TR("tr"),
    
    UK("uk"),
    
    UR("ur"),
    
    UZ("uz"),
    
    VI("vi"),
    
    ZU("zu");

    private final String value;

    LanguageType(String value) {
        this.value = value;
    }

    
    @JsonValue
    public String getValue() {
        return value;
    }
}
