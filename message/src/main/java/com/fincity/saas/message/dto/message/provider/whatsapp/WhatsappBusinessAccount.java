package com.fincity.saas.message.dto.message.provider.whatsapp;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.model.message.whatsapp.business.BusinessAccount;
import com.fincity.saas.message.model.message.whatsapp.business.SubscribedApp;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class WhatsappBusinessAccount extends BaseUpdatableDto<WhatsappBusinessAccount> {

    private String whatsappBusinessAccountId;
    private String name;
    private String currency;
    private String timezoneId;
    private String messageTemplateNamespace;
    private SubscribedApp subscribedApp;

    /**
     * The core connection this account was synced from. Recorded because an inbound webhook has
     * only the Meta business account id to go on, and verifying its signature means reaching the
     * Meta app secret, which lives behind this connection's {@code tokenConnection}. Null on rows
     * created before the column existed; callers fall back to the configured default connection.
     */
    private String connectionName;

    public WhatsappBusinessAccount() {
        super();
    }

    public WhatsappBusinessAccount(WhatsappBusinessAccount whatsappBusinessAccount) {
        super(whatsappBusinessAccount);
        this.whatsappBusinessAccountId = whatsappBusinessAccount.whatsappBusinessAccountId;
        this.name = whatsappBusinessAccount.name;
        this.currency = whatsappBusinessAccount.currency;
        this.timezoneId = whatsappBusinessAccount.timezoneId;
        this.messageTemplateNamespace = whatsappBusinessAccount.messageTemplateNamespace;
        this.subscribedApp = whatsappBusinessAccount.subscribedApp;
        this.connectionName = whatsappBusinessAccount.connectionName;
    }

    public static WhatsappBusinessAccount of(String whatsappBusinessAccountId, BusinessAccount businessAccount) {
        return new WhatsappBusinessAccount()
                .setWhatsappBusinessAccountId(whatsappBusinessAccountId)
                .setName(businessAccount.getName())
                .setCurrency(businessAccount.getCurrency())
                .setTimezoneId(businessAccount.getTimezoneId())
                .setMessageTemplateNamespace(businessAccount.getMessageTemplateNamespace());
    }

    public WhatsappBusinessAccount update(BusinessAccount businessAccount) {
        this.name = businessAccount.getName();
        this.currency = businessAccount.getCurrency();
        this.timezoneId = businessAccount.getTimezoneId();
        this.messageTemplateNamespace = businessAccount.getMessageTemplateNamespace();
        return this;
    }
}
