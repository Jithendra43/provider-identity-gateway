package chit.tefca.ingress.service;

import chit.tefca.common.security.SecurityConstants;
import chit.tefca.ingress.dto.PartnerContext;
import chit.tefca.ingress.model.Partner;
import chit.tefca.ingress.model.PartnerRateLimit;
import chit.tefca.ingress.repository.PartnerRateLimitRepository;
import chit.tefca.ingress.repository.PartnerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Resolves PartnerContext from JWT claims enriched with database-stored partner configuration.
 * Falls back to JWT-only context if the partner is not found in the DB.
 */
@Slf4j
@Service
public class PartnerContextResolver {

    private final PartnerRepository partnerRepository;
    private final PartnerRateLimitRepository rateLimitRepository;

    public PartnerContextResolver(
            @Nullable PartnerRepository partnerRepository,
            @Nullable PartnerRateLimitRepository rateLimitRepository) {
        this.partnerRepository = partnerRepository;
        this.rateLimitRepository = rateLimitRepository;
    }

    @Value("${tefca.rate-limit.requests-per-minute:100}")
    private int defaultRpm;

    public PartnerContext resolve(Jwt jwt) {
        String orgId = jwt.getClaimAsString(SecurityConstants.CLAIM_ORG_ID);
        String nodeId = jwt.getClaimAsString(SecurityConstants.CLAIM_NODE_ID);
        List<String> roles = jwt.getClaimAsStringList(SecurityConstants.CLAIM_ROLES);

        if (partnerRepository == null) {
            log.debug("PartnerRepository not available, using JWT-only context for orgId={}", orgId);
            return PartnerContext.builder()
                    .orgId(orgId)
                    .nodeId(nodeId)
                    .roles(roles != null ? roles : List.of())
                    .requestsPerMinute(defaultRpm)
                    .burstCapacity(defaultRpm)
                    .dbResolved(false)
                    .build();
        }

        Optional<Partner> partnerOpt = partnerRepository.findByOrgId(orgId);

        if (partnerOpt.isEmpty()) {
            log.debug("No DB partner record for orgId={}, using JWT-only context", orgId);
            return PartnerContext.builder()
                    .orgId(orgId)
                    .nodeId(nodeId)
                    .roles(roles != null ? roles : List.of())
                    .requestsPerMinute(defaultRpm)
                    .burstCapacity(defaultRpm)
                    .dbResolved(false)
                    .build();
        }

        Partner partner = partnerOpt.get();
        int rpm = defaultRpm;
        int burst = defaultRpm;

        if (rateLimitRepository != null) {
            Optional<PartnerRateLimit> rl = rateLimitRepository.findByPartnerIdAndActiveTrue(partner.getPartnerId());
            if (rl.isPresent()) {
                rpm = rl.get().getRequestsPerMinute();
                burst = rl.get().getBurstCapacity();
            }
        }

        return PartnerContext.builder()
                .partnerId(partner.getPartnerId())
                .orgId(orgId)
                .nodeId(nodeId)
                .partnerName(partner.getName())
                .environment(partner.getEnvironment())
                .requestsPerMinute(rpm)
                .burstCapacity(burst)
                .roles(roles != null ? roles : List.of())
                .dbResolved(true)
                .build();
    }
}
