package chit.tefca.directory.bootstrap;

import chit.tefca.directory.model.DirectoryOrganization;
import chit.tefca.directory.repository.OrganizationRepository;
import chit.tefca.directory.service.EndpointLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pre-warms the in-process directory Caffeine cache on startup so the first
 * partner PA request after a deploy is served from cache rather than incurring
 * the 80–200&nbsp;ms cold-miss against PostgreSQL. Runs asynchronously so it
 * does not block the Spring context refresh / health-check readiness.
 */
@Component
@RequiredArgsConstructor
public class CacheWarmer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmer.class);

    private final OrganizationRepository organizationRepository;
    private final EndpointLookupService endpointLookupService;

    @Override
    @Async
    public void run(ApplicationArguments args) {
        long start = System.currentTimeMillis();
        try {
            List<DirectoryOrganization> orgs = organizationRepository.findAll();
            int warmed = 0;
            for (DirectoryOrganization org : orgs) {
                if (org.getOrgId() == null) continue;
                if (!org.isActive()) continue;
                try {
                    endpointLookupService.getEndpointsForOrg(org.getOrgId());
                    warmed++;
                } catch (Exception perOrg) {
                    log.debug("Cache warm skipped org={}: {}", org.getOrgId(), perOrg.getMessage());
                }
            }
            log.info("Directory cache warm complete: {}/{} active orgs in {} ms",
                    warmed, orgs.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            // Pre-warm is a best-effort optimization; never fail startup over it.
            log.warn("Directory cache warm aborted: {}", e.getMessage());
        }
    }
}
