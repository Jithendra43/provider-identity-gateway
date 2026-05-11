-- V013: Activate DTR & PAS endpoints now that the Mock PA controller
-- ships with the matching /mock-pa/fhir/** handlers. V011 originally seeded
-- these endpoints with active=FALSE because downstream readiness was
-- unverified; the in-process MockPaController has since implemented all
-- nine routes (Questionnaire/$questionnaire-package, Questionnaire,
-- Library, QuestionnaireResponse [POST/GET], Claim/$submit,
-- Claim/$inquire, ClaimResponse), so they can safely participate in
-- routing decisions.
--
-- Operators can still toggle individual endpoints back to active=FALSE
-- via the admin Directory page or by SQL UPDATE without re-applying a
-- migration.

UPDATE directory.directory_endpoints
   SET active = TRUE,
       updated_at = CURRENT_TIMESTAMP
 WHERE endpoint_id IN (
     'EP-DTR-Q-PACKAGE',
     'EP-DTR-Q-READ',
     'EP-DTR-LIBRARY-READ',
     'EP-DTR-RESPONSE-SUBMIT',
     'EP-DTR-RESPONSE-READ',
     'EP-PAS-CLAIM-SUBMIT',
     'EP-PAS-CLAIM-INQUIRE',
     'EP-PAS-CLAIM-RESPONSE-READ'
   )
   AND active = FALSE;
