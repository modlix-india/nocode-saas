package com.fincity.saas.entity.processor.platform;

import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AdPlatformRegistry {

    private final Map<CampaignPlatform, AbstractAdPlatformService> platformMap;

    public AdPlatformRegistry(List<AbstractAdPlatformService> platforms) {
        this.platformMap = platforms.stream()
                .collect(Collectors.toMap(AbstractAdPlatformService::getPlatform, Function.identity()));
    }

    public AbstractAdPlatformService getService(CampaignPlatform platform) {
        return Optional.ofNullable(platformMap.get(platform))
                .orElseThrow(() -> new UnsupportedOperationException(
                        "Platform not yet supported: " + platform));
    }

    public boolean isSupported(CampaignPlatform platform) {
        return platformMap.containsKey(platform);
    }
}
