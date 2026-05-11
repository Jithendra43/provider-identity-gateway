-- Compose-only seed: rewrites the .local URLs from R001 to point at the
-- in-cluster wiremock partner so the routing service can actually forward
-- traffic for end-to-end testing.

UPDATE directory.directory_endpoints SET url = 'http://wiremock:8080/xcpd'         WHERE endpoint_id = 'EP-CW-XCPD';
UPDATE directory.directory_endpoints SET url = 'http://wiremock:8080/xca/query'    WHERE endpoint_id = 'EP-CW-XCA-Q';
UPDATE directory.directory_endpoints SET url = 'http://wiremock:8080/xca/retrieve' WHERE endpoint_id = 'EP-CW-XCA-R';
UPDATE directory.directory_endpoints SET url = 'http://wiremock:8080/xcpd'         WHERE endpoint_id = 'EP-EHX-XCPD';
UPDATE directory.directory_endpoints SET url = 'http://wiremock:8080/fhir'         WHERE endpoint_id = 'EP-EHX-FHIR';
UPDATE directory.directory_endpoints SET url = 'http://wiremock:8080/xdr'          WHERE endpoint_id = 'EP-HOSP-XDR';
