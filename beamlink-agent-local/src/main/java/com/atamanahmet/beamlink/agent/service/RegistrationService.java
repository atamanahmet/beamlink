package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.exception.NexusOfflineException;
import com.atamanahmet.beamlink.agent.util.NetworkUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final AgentConfig config;
    private final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private final AgentInfoService agentInfoService;
            ;

    /**
     * Register this agent with nexus server
     */
    public boolean registerWithNexus() {

            Agent agent = new Agent();
            agent.setAgentId(agentInfoService.getAgentId());
            agent.setName(agentInfoService.getAgentName());
            agent.setIpAddress(NetworkUtil.getLocalIp());
            agent.setPort(config.getPort());

            WebClient client = WebClient.create();
            String url = config.getNexusUrl() + "/api/agents/register";
        try {

           String result = client.post()
                    .uri(url)
                    .bodyValue(agent)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(error ->
                        Mono.empty()
                    )
                    .block();


           if(result!=null){
               log.info("✓ Registered with nexus: {}", config.getNexusUrl());
               return true;
           }
           else{
               log.warn("Initial register with nexus failed. Continuing with peer cache");
               return false;
           }

        } catch (Exception e) {
            log.warn("Failed to report status with exception: {}. Continuing with peer cache", e.getMessage());
            return false;
        }
    }
}