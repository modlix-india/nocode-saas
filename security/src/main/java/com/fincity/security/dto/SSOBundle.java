package com.fincity.security.dto;

import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

import java.io.Serial;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class SSOBundle extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private String clientCode;
    private String bundleName;

    private List<SSOBundledApp> apps;

    @Data
    @EqualsAndHashCode(callSuper = true)
    @Accessors(chain = true)
    @ToString(callSuper = true)
    public static class SSOBundledApp extends AbstractDTO<ULong, ULong> {

        @Serial
        private static final long serialVersionUID = 2L;

        private String appCode;
        private ULong appUrlId;
        private ULong bundleId;

        private App app;
        private String url;
    }
}
