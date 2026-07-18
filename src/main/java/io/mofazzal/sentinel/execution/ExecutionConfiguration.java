package io.mofazzal.sentinel.execution;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ExecutionConfiguration {

    @Bean
    RemediationStrategy restartServiceStrategy(JdbcTemplate jdbc, Clock clock) {
        return new SimulatedRemediationStrategy(RemediationActionType.RESTART_SERVICE, jdbc, clock);
    }

    @Bean
    RemediationStrategy rollbackDeploymentStrategy(JdbcTemplate jdbc, Clock clock) {
        return new SimulatedRemediationStrategy(RemediationActionType.ROLLBACK_DEPLOYMENT, jdbc, clock);
    }

    @Bean
    RemediationStrategy scaleOutStrategy(JdbcTemplate jdbc, Clock clock) {
        return new SimulatedRemediationStrategy(RemediationActionType.SCALE_OUT, jdbc, clock);
    }

    @Bean
    RemediationStrategy clearCacheStrategy(JdbcTemplate jdbc, Clock clock) {
        return new SimulatedRemediationStrategy(RemediationActionType.CLEAR_CACHE, jdbc, clock);
    }
}
