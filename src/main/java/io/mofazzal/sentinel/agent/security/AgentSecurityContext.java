package io.mofazzal.sentinel.agent.security;

import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class AgentSecurityContext {

    public <T> T callAsAgent(Supplier<T> operation) {
        var previous = SecurityContextHolder.getContext();
        var agentContext = SecurityContextHolder.createEmptyContext();
        agentContext.setAuthentication(new PreAuthenticatedAuthenticationToken(
                "sentinel-agent", "internal-messaging-boundary",
                AuthorityUtils.createAuthorityList("ROLE_AGENT")));
        SecurityContextHolder.setContext(agentContext);
        try {
            return operation.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
