package com.fincity.saas.entity.processor.conversions;

import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ConversionsDispatcherRegistry {

    private final Map<CampaignPlatform, AbstractConversionsDispatcher> dispatchers;

    public ConversionsDispatcherRegistry(List<AbstractConversionsDispatcher> beans) {
        this.dispatchers = beans.stream()
                .collect(Collectors.toMap(AbstractConversionsDispatcher::getPlatform, Function.identity()));
    }

    public Optional<AbstractConversionsDispatcher> get(CampaignPlatform platform) {
        return Optional.ofNullable(this.dispatchers.get(platform));
    }
}
