-- ───────────────────────────────────────────────────────────────────────────
-- V009__redirect_qhin_endpoints_to_mock.sql
-- ───────────────────────────────────────────────────────────────────────────
-- Production runs without external QHIN connectivity (no DNS for
-- gateway.commonwell.local, gateway.ehealthexchange.local, etc.). Redirect
-- the QHIN/SUB endpoints to the in-process mock-fhir loopback so the Test
-- Console PATIENT_DISCOVERY → ORG-QHIN-001 round-trip succeeds.
-- ───────────────────────────────────────────────────────────────────────────

UPDATE directory.directory_endpoints
   SET url = 'http://127.0.0.1:8080/mock-fhir/xcpd'
 WHERE endpoint_id IN ('EP-CW-XCPD', 'EP-EHX-XCPD');

UPDATE directory.directory_endpoints
   SET url = 'http://127.0.0.1:8080/mock-fhir/xca-q'
 WHERE endpoint_id IN ('EP-CW-XCA-Q', 'EP-EHX-XCA-Q');

UPDATE directory.directory_endpoints
   SET url = 'http://127.0.0.1:8080/mock-fhir/xca-r'
 WHERE endpoint_id IN ('EP-CW-XCA-R', 'EP-EHX-XCA-R');

UPDATE directory.directory_endpoints
   SET url = 'http://127.0.0.1:8080/mock-fhir'
 WHERE endpoint_id IN ('EP-CW-FHIR', 'EP-EHX-FHIR', 'EP-HOSP-FHIR');

UPDATE directory.directory_endpoints
   SET url = 'http://127.0.0.1:8080/mock-fhir/xdr'
 WHERE endpoint_id = 'EP-HOSP-XDR';
