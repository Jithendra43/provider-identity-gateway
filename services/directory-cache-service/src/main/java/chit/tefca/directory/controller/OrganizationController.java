package chit.tefca.directory.controller;

import chit.tefca.directory.dto.OrganizationDto;
import chit.tefca.directory.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/directory/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping
    public ResponseEntity<List<OrganizationDto>> listOrganizations(
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(organizationService.listOrganizations(type));
    }

    @GetMapping("/{orgId}")
    public ResponseEntity<OrganizationDto> getOrganization(@PathVariable String orgId) {
        return ResponseEntity.ok(organizationService.getOrganization(orgId));
    }

    @GetMapping("/by-oid/{oid}")
    public ResponseEntity<OrganizationDto> getOrganizationByOid(@PathVariable String oid) {
        return ResponseEntity.ok(organizationService.getOrganizationByOid(oid));
    }
}
