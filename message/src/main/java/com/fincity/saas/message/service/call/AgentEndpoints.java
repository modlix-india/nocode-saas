package com.fincity.saas.message.service.call;

import com.fincity.saas.message.dto.call.ProviderUserEndpoint;
import com.fincity.saas.message.model.response.call.ProvisionedAgent;
import com.fincity.saas.message.util.SetterUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;

/** Folds an agent's per-destination endpoint rows into one entry per agent, for every provider. */
public final class AgentEndpoints {

    private AgentEndpoints() {}

    /** One entry per agent, in the order the rows arrived. */
    public static List<ProvisionedAgent> consolidate(List<ProviderUserEndpoint> endpoints) {

        Map<ULong, ProvisionedAgent> byUser = new LinkedHashMap<>();

        for (ProviderUserEndpoint endpoint : endpoints)
            fold(byUser.computeIfAbsent(endpoint.getUserId(), id -> new ProvisionedAgent()), endpoint);

        return List.copyOf(byUser.values());
    }

    /**
     * Folds one destination row into its agent by endpoint type. A phone-only agent keeps a null
     * {@code sipEndpoint} rather than being hidden, and an unrecognised type is ignored.
     */
    public static ProvisionedAgent fold(ProvisionedAgent agent, ProviderUserEndpoint endpoint) {

        agent.setUserId(endpoint.getUserId());

        SetterUtil.setIfPresent(endpoint.getProviderUserId(), agent::setProviderUserId);
        SetterUtil.setIfPresent(endpoint.getVirtualNumber(), agent::setVirtualNumber);

        if (ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP.equals(endpoint.getEndpointType()))
            agent.setSipEndpoint(endpoint.getEndpointValue());
        else if (ProviderUserEndpoint.ENDPOINT_PSTN_PHONE.equals(endpoint.getEndpointType()))
            agent.setAgentNumber(endpoint.getEndpointValue());

        if (endpoint.isActive()) agent.setActive(true);

        if (endpoint.getUpdatedAt() != null
                && (agent.getUpdatedAt() == null || endpoint.getUpdatedAt().isAfter(agent.getUpdatedAt())))
            agent.setUpdatedAt(endpoint.getUpdatedAt());

        return agent;
    }
}
