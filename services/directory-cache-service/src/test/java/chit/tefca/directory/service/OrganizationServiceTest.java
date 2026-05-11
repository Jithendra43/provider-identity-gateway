package chit.tefca.directory.service;

import chit.tefca.common.exception.DirectoryLookupException;
import chit.tefca.directory.cache.DirectoryCaffeineCache;
import chit.tefca.directory.dto.OrganizationDto;
import chit.tefca.directory.model.DirectoryOrganization;
import chit.tefca.directory.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private DirectoryCaffeineCache caffeineCache;

    @InjectMocks
    private OrganizationService organizationService;

    @Test
    void getOrganization_cacheHit() {
        OrganizationDto cached = OrganizationDto.builder()
                .orgId("org-1").name("Cached Org").build();
        when(caffeineCache.getCachedOrganization("org-1")).thenReturn(Optional.of(cached));

        OrganizationDto result = organizationService.getOrganization("org-1");

        assertThat(result.getName()).isEqualTo("Cached Org");
        verifyNoInteractions(organizationRepository);
    }

    @Test
    void getOrganization_cacheMiss_fallsBackToDb() {
        when(caffeineCache.getCachedOrganization("org-1")).thenReturn(Optional.empty());
        DirectoryOrganization org = DirectoryOrganization.builder()
                .orgId("org-1").name("DB Org").oid("2.16.840").active(true).build();
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(org));

        OrganizationDto result = organizationService.getOrganization("org-1");

        assertThat(result.getName()).isEqualTo("DB Org");
        verify(caffeineCache).cacheOrganization(eq("org-1"), any(OrganizationDto.class));
    }

    @Test
    void getOrganization_notFound() {
        when(caffeineCache.getCachedOrganization("org-missing")).thenReturn(Optional.empty());
        when(organizationRepository.findById("org-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.getOrganization("org-missing"))
                .isInstanceOf(DirectoryLookupException.class)
                .hasMessageContaining("Organization not found");
    }

    @Test
    void getOrganizationByOid_found() {
        DirectoryOrganization org = DirectoryOrganization.builder()
                .orgId("org-1").name("OID Org").oid("2.16.840").active(true).build();
        when(organizationRepository.findByOid("2.16.840")).thenReturn(Optional.of(org));

        OrganizationDto result = organizationService.getOrganizationByOid("2.16.840");

        assertThat(result.getOrgId()).isEqualTo("org-1");
    }

    @Test
    void getOrganizationByOid_notFound() {
        when(organizationRepository.findByOid("bad-oid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.getOrganizationByOid("bad-oid"))
                .isInstanceOf(DirectoryLookupException.class)
                .hasMessageContaining("Organization not found for OID");
    }

    @Test
    void listOrganizations_byType() {
        DirectoryOrganization org = DirectoryOrganization.builder()
                .orgId("org-1").name("QHIN").orgType("QHIN").active(true).build();
        when(organizationRepository.findByOrgType("QHIN")).thenReturn(List.of(org));

        List<OrganizationDto> result = organizationService.listOrganizations("QHIN");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrgType()).isEqualTo("QHIN");
    }

    @Test
    void listOrganizations_allActive() {
        DirectoryOrganization org = DirectoryOrganization.builder()
                .orgId("org-1").name("Active Org").active(true).build();
        when(organizationRepository.findByActiveTrue()).thenReturn(List.of(org));

        List<OrganizationDto> result = organizationService.listOrganizations(null);

        assertThat(result).hasSize(1);
    }
}
