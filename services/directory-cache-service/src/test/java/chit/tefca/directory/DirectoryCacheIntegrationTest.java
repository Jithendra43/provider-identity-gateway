package chit.tefca.directory;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.NodeStatus;
import chit.tefca.directory.model.DirectoryEndpoint;
import chit.tefca.directory.model.DirectoryNode;
import chit.tefca.directory.model.DirectoryOrganization;
import chit.tefca.directory.repository.EndpointRepository;
import chit.tefca.directory.repository.NodeRepository;
import chit.tefca.directory.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test using H2 in-memory database (local profile).
 * Cache is in-process Caffeine — no external dependency required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DirectoryCacheIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository orgRepo;
    @Autowired private NodeRepository nodeRepo;
    @Autowired private EndpointRepository epRepo;

    @BeforeEach
    void setUp() {
        epRepo.deleteAll();
        nodeRepo.deleteAll();
        orgRepo.deleteAll();

        DirectoryOrganization org = DirectoryOrganization.builder()
                .orgId("org-int-1")
                .name("Integration Test Org")
                .oid("2.16.840.1.113883.3.1234")
                .orgType("QHIN")
                .active(true)
                .homeCommunityId("urn:oid:2.16.840.1.113883.3.1234")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        orgRepo.save(org);

        DirectoryNode node = DirectoryNode.builder()
                .nodeId("node-int-1")
                .orgId("org-int-1")
                .name("Integration Test Node")
                .homeCommunityId("urn:oid:2.16.840.1.113883.3.1234.1")
                .status(NodeStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        nodeRepo.save(node);

        DirectoryEndpoint ep = DirectoryEndpoint.builder()
                .endpointId("ep-int-1")
                .nodeId("node-int-1")
                .url("https://fhir.example.com/r4")
                .modality(Modality.XCA_QUERY)
                .active(true)
                .certificateAlias("cert-int-1")
                .build();
        epRepo.save(ep);
    }

    @Test
    void listOrganizations_returnsActiveOrgs() throws Exception {
        mockMvc.perform(get("/api/v1/directory/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].orgId").value("org-int-1"))
                .andExpect(jsonPath("$[0].name").value("Integration Test Org"));
    }

    @Test
    void getOrganizationById_returnsOrg() throws Exception {
        mockMvc.perform(get("/api/v1/directory/organizations/org-int-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgId").value("org-int-1"))
                .andExpect(jsonPath("$.oid").value("2.16.840.1.113883.3.1234"));
    }

    @Test
    void getOrganizationByOid_returnsOrg() throws Exception {
        mockMvc.perform(get("/api/v1/directory/organizations/by-oid/2.16.840.1.113883.3.1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgId").value("org-int-1"));
    }

    @Test
    void lookupEndpoints_returnsEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/directory/endpoints/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetOrgId\":\"org-int-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].endpointId").value("ep-int-1"))
                .andExpect(jsonPath("$[0].url").value("https://fhir.example.com/r4"));
    }

    @Test
    void getOrgEndpoints_returnsEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/directory/organizations/org-int-1/endpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getNode_returnsNodeDto() throws Exception {
        mockMvc.perform(get("/api/v1/directory/nodes/node-int-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value("node-int-1"))
                .andExpect(jsonPath("$.name").value("Integration Test Node"))
                .andExpect(jsonPath("$.endpointCount").value(1));
    }

    @Test
    void getNodes_byOrgId() throws Exception {
        mockMvc.perform(get("/api/v1/directory/nodes").param("orgId", "org-int-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nodeId").value("node-int-1"));
    }

    @Test
    void healthEndpoint_returnsUp() throws Exception {
        mockMvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
