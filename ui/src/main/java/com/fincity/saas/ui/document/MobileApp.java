package com.fincity.saas.ui.document;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'objectAppCode': 1, 'objectName': 1, 'clientCode': 1}", name = "mobileAppGenerationStatusFilteringIndex")
@Accessors(chain = true)
public class MobileApp extends AbstractUpdatableDTO<String, String> {

    @Serial
    private static final long serialVersionUID = -2421283179190414360L;

    private String appCode;
    private String clientCode;
    private Status status;
    private String errorMessage;
    private AppDetails details;
    private String androidAppURL;
    private String iosAppURL;
    private String androidKeystore;
    private String androidStorePass;
    private String androidKeyPass;
    private String androidAlias;
    private String iosCertificate;
    private String iosCertificatePassword;
    private String iosProvisioningProfile;
    private String iosTeamId;
    private String iosBundleId;

    public enum Status {
        PENDING,
        IN_PROGRESS,
        SUCCESS,
        FAILED;
    }

    public enum IosPublishMode {
        TENANT_ACCOUNT,
        PLATFORM_ACCOUNT;
    }

    @Data
    @Accessors(chain = true)
    public static class AppDetails {
        private String name;
        private String description;
        private String startURL;
        private boolean android;
        private boolean ios;
        private IosPublishMode iosPublishMode;
        private String icon;
        private SplashScreenDetails splashScreen;
        private int version = 0;
    }

    @Data
    @Accessors(chain = true)
    public static class SplashScreenDetails {
        private String image;
        private String color;
        private String backgroundImage;
        private boolean fullScreen;
        private String gravity;
        private String color_dark;
        private String background_image_dark;
        private String image_dark;
    }
}