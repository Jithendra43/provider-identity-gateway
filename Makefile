# Local end-to-end test driver. Run from tefca-gateway/ root.
SHELL := /bin/bash

.PHONY: help build up up-mtls down logs e2e clean reset shell-pg load-test \
        certs certs-clean test-security postman-mtls postman-security

help:
	@echo "Local E2E targets:"
	@echo "  make certs    - generate the local mTLS PKI (root CA + server + 3 partner certs)"
	@echo "  make build    - mvn package + docker compose build"
	@echo "  make up       - bring up the full stack (mTLS-enforced on :8443)"
	@echo "  make up-mtls  - certs + build + up + wait for handshake"
	@echo "  make down     - stop the stack (keeps volumes)"
	@echo "  make clean    - stop + remove volumes"
	@echo "  make certs-clean - delete generated PKI material and R004 seed"
	@echo "  make reset    - clean + certs + build + up + e2e"
	@echo "  make e2e      - run end-to-end smoke test"
	@echo "  make test-security - run the security-focused JUnit suite"
	@echo "  make postman-mtls  - run the Postman collection through the mTLS edge"
	@echo "  make logs     - tail logs (CTRL-C to exit)"
	@echo "  make shell-pg - psql shell into postgres"

# ── Cert lifecycle ──────────────────────────────────────────────────────────
certs:
	@bash ./test-harness/certs/gen-certs.sh

certs-clean:
	@rm -f ./test-harness/certs/*.crt ./test-harness/certs/*.key \
	       ./test-harness/certs/*.p12 ./test-harness/certs/*.srl
	@rm -f ./database/seed/R004__partner_certificates.sql
	@echo "✓ removed generated certs and R004 seed"

build:
	cd services && mvn -B -q -DskipTests package
	docker compose build

# `up` now boots the production-style mTLS edge. The ingress service has no
# host port — the only way in is https://localhost:8443 via the NGINX sidecar.
up: certs
	docker compose up -d
	@echo "Waiting for the mTLS sidecar to be healthy..."
	@for i in $$(seq 1 60); do \
		if curl -fsk https://localhost:8443/sidecar/health >/dev/null 2>&1 && \
		   curl -fsk https://localhost:8444/sidecar/health >/dev/null 2>&1; then \
			echo "✓ stack is up"; \
			echo "    partner edge (mTLS) → https://localhost:8443"; \
			echo "    admin edge   (TLS)  → https://localhost:8444/admin/login/"; \
			exit 0; \
		fi; sleep 2; \
	done; \
	echo "✗ stack failed to come up"; docker compose ps; exit 1

# Convenience: wipe + rebuild + boot in one shot.
up-mtls: clean certs build up

down:
	docker compose down

clean:
	docker compose down -v

reset: clean certs build up e2e

e2e:
	./test-harness/e2e-test.sh

# ── Security-focused tests ──────────────────────────────────────────────────
test-security:
	cd services && mvn -B -pl ingress-auth-service -am test \
	  -Dtest='MtlsValidationFilterTest,PartnerTrustStoreTest,PartnerTrustStoreCertParsingTest,PartnerCertificateLoaderTest,JwtReplayCacheTest,JwtTokenValidatorTest,JwtAuthenticationFilterTest,RateLimitFilterTest' \
	  -Dsurefire.failIfNoSpecifiedTests=false

# Run Postman collection through the mTLS edge using the partner client cert.
# Requires `npm i -g newman` and the cert material in test-harness/certs/.
postman-mtls:
	@command -v newman >/dev/null 2>&1 || { echo "::error::newman not installed (npm i -g newman)"; exit 1; }
	newman run test-harness/postman/TEFCA-Gateway.postman_collection.json \
	  -e test-harness/postman/TEFCA-Local-mTLS.postman_environment.json \
	  --ssl-client-cert     test-harness/certs/PART-CW-001.crt \
	  --ssl-client-key      test-harness/certs/PART-CW-001.key \
	  --ssl-extra-ca-certs  test-harness/certs/rootCA.crt

# Run the security-focused Postman collection (mTLS, JWT, RBAC, replay,
# rate-limit, admin edge, actuator hardening). Each folder needs its own
# cert flag set, so we invoke newman 4 times. See docs/SECURITY-TEST-RESULTS.md.
postman-security:
	@command -v newman >/dev/null 2>&1 || { echo "::error::newman not installed (npm i -g newman)"; exit 1; }
	@COL=test-harness/postman/TEFCA-Security-Tests.postman_collection.json; \
	 ENV=test-harness/postman/TEFCA-Local-mTLS.postman_environment.json; \
	 CERTS=test-harness/certs; \
	 echo "── (a) bootstrap + folders 2a/3/4/5 — WITH PART-CW-001 cert ──"; \
	 newman run $$COL -e $$ENV \
	   --folder "0. Bootstrap — mint partner JWT (mock OIDC)" \
	   --folder "2a. mTLS — trusted partner cert + valid JWT — run WITH PART-CW-001" \
	   --folder "3. JWT authentication — run WITH PART-CW-001" \
	   --folder "4. Authorization (RBAC) — run WITH PART-CW-001" \
	   --folder "5. Rate limiting — run WITH PART-CW-001" \
	   --ssl-client-cert    $$CERTS/PART-CW-001.crt \
	   --ssl-client-key     $$CERTS/PART-CW-001.key \
	   --ssl-extra-ca-certs $$CERTS/rootCA.crt || exit 1; \
	 echo ""; echo "── (b) folder 1 — WITHOUT any client cert (expect 400) ──"; \
	 newman run $$COL -e $$ENV \
	   --folder "1. TLS edge — run WITHOUT any client cert" \
	   --ssl-extra-ca-certs $$CERTS/rootCA.crt || exit 1; \
	 echo ""; echo "── (c) folder 2b — WITH self-signed rogue cert (expect 400) ──"; \
	 [ -f /tmp/rogue.crt ] || openssl req -x509 -newkey rsa:2048 -days 1 -nodes \
	   -keyout /tmp/rogue.key -out /tmp/rogue.crt -subj "/CN=rogue" 2>/dev/null; \
	 newman run $$COL -e $$ENV \
	   --folder "2b. mTLS — untrusted self-signed cert — run WITH /tmp/rogue.crt" \
	   --ssl-client-cert /tmp/rogue.crt \
	   --ssl-client-key  /tmp/rogue.key \
	   --ssl-extra-ca-certs $$CERTS/rootCA.crt || exit 1; \
	 echo ""; echo "── (d) folders 6 & 7 — admin edge :8444, no client cert ──"; \
	 newman run $$COL -e $$ENV \
	   --folder "6. Admin edge (separate TLS-only listener) — NO client cert" \
	   --folder "7. Actuator hardening — NO client cert" \
	   --ssl-extra-ca-certs $$CERTS/rootCA.crt || exit 1; \
	 echo ""; echo "✓ all security folders passed"

logs:
	docker compose logs -f --tail=100

shell-pg:
	docker compose exec postgres psql -U tefca -d tefca_gateway

load-test:
	@command -v k6 >/dev/null 2>&1 || { echo "::error::k6 not installed (brew install k6)"; exit 1; }
	@test -n "$$SMOKE_JWT" || { echo "::error::SMOKE_JWT env var required (run scripts/issue-smoke-jwt.sh)"; exit 1; }
	@mkdir -p target/load-results
	k6 run --summary-export=target/load-results/patient-discovery.json test-harness/load/patient-discovery.js
	k6 run --summary-export=target/load-results/message-delivery.json   test-harness/load/message-delivery.js
	k6 run --summary-export=target/load-results/policy-evaluate.json    test-harness/load/policy-evaluate.js
