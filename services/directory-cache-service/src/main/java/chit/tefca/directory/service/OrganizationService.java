package chit.tefca.directory.service;

import chit.tefca.common.exception.DirectoryLookupException;
import chit.tefca.directory.cache.DirectoryCaffeineCache;
import chit.tefca.directory.dto.OrganizationDto;
import chit.tefca.directory.model.DirectoryOrganization;
import chit.tefca.directory.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;
    private final DirectoryCaffeineCache caffeineCache;

    public List<OrganizationDto> listOrganizations(String type) {
        List<DirectoryOrganization> orgs;
        if (type != null && !type.isBlank()) {
            orgs = organizationRepository.findByOrgType(type);
        } else {
            orgs = organizationRepository.findByActiveTrue();
        }
        return orgs.stream().map(this::toDto).collect(Collectors.toList());
    }

    public OrganizationDto getOrganization(String orgId) {
        // Try cache first
        Optional<OrganizationDto> cached = caffeineCache.getCachedOrganization(orgId);
        if (cached.isPresent()) {
            return cached.get();
        }

        DirectoryOrganization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new DirectoryLookupException("Organization not found: " + orgId));

        OrganizationDto dto = toDto(org);
        caffeineCache.cacheOrganization(orgId, dto);
        return dto;
    }

    public OrganizationDto getOrganizationByOid(String oid) {
        DirectoryOrganization org = organizationRepository.findByOid(oid)
                .orElseThrow(() -> new DirectoryLookupException("Organization not found for OID: " + oid));
        return toDto(org);
    }

    private OrganizationDto toDto(DirectoryOrganization org) {
        return OrganizationDto.builder()
                .orgId(org.getOrgId())
                .name(org.getName())
                .oid(org.getOid())
                .orgType(org.getOrgType())
                .active(org.isActive())
                .homeCommunityId(org.getHomeCommunityId())
                .lastSyncedAt(org.getLastSyncedAt())
                .build();
    }
}
