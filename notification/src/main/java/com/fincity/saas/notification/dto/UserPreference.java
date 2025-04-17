package com.fincity.saas.notification.dto;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.enums.PreferenceLevel;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import java.io.Serial;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
@FieldNameConstants
public class UserPreference extends AbstractUpdatableDTO<ULong, ULong> {

    private static final Map<String, List<String>> DEFAULT_PREF = Arrays.stream(PreferenceLevel.values())
            .collect(Collectors.toMap(PreferenceLevel::getLiteral, PreferenceLevel::getDefaultList));

    @Serial
    private static final long serialVersionUID = 6629796623611093778L;

    private ULong appId;
    private ULong userId;
    private String code = UniqueUtil.shortUUID();
    private boolean enabled;

    private Map<String, List<String>> preferences;

    public static Map<String, List<String>> getDefaultPref() {
        return CloneUtil.cloneMapObject(DEFAULT_PREF);
    }

    public UserPreference setEnabled(boolean enabled) {
        if (!enabled) this.preferences = getDefaultPref();

        this.enabled = enabled;
        return this;
    }

    public UserPreference setPreferences(Map<String, List<String>> preferences) {

        if (preferences == null || preferences.isEmpty()) return this;

        this.preferences = CloneUtil.cloneMapObject(preferences);
        this.enabled = this.hasAnyPreference();

        return this;
    }

    public boolean hasPreference(String pref) {
        return preferences.entrySet().stream()
                .anyMatch(entry -> entry.getValue().contains(pref)
                        && !PreferenceLevel.lookupLiteral(entry.getKey()).isReverseSave());
    }

    public boolean hasPreference(NotificationChannelType channelType) {
        Collection<String> channelPreferences = this.preferences.get(PreferenceLevel.CHANNEL.getLiteral());
        if (channelPreferences == null) return false;
        return channelPreferences.contains(channelType.getLiteral());
    }

    public boolean hasAnyPreference() {

        if (this.preferences == null || this.preferences.isEmpty()) return false;

        return preferences.entrySet().stream()
                .anyMatch(entry -> entry.getValue() != null
                        && !entry.getValue().isEmpty()
                        && !PreferenceLevel.lookupLiteral(entry.getKey()).isDefault(entry.getValue()));
    }
}
