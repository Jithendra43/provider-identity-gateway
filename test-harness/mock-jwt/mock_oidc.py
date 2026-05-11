"""
Tiny OIDC-compatible JWT issuer for local end-to-end testing.

Endpoints:
  GET  /.well-known/openid-configuration  - OIDC discovery
  GET  /.well-known/jwks.json             - JWKS public keys
  POST /token                             - mint a JWT with TEFCA claims
  GET  /                                  - usage hint

The signing key is generated fresh every container start, which is fine for
local testing because the gateway re-fetches the JWKS on demand.
"""
import base64
import json
import os
import time
import uuid

import jwt
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from flask import Flask, jsonify, request

ISSUER = os.environ.get("ISSUER", "http://mock-jwt:8090")
AUDIENCE = os.environ.get("AUDIENCE", "tefca-gateway")
KID = "mock-key-1"

# Generate signing key on startup
private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
public_key = private_key.public_key()
public_numbers = public_key.public_numbers()

private_pem = private_key.private_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PrivateFormat.PKCS8,
    encryption_algorithm=serialization.NoEncryption(),
)


def _b64url_uint(n: int) -> str:
    b = n.to_bytes((n.bit_length() + 7) // 8, "big")
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


JWKS = {
    "keys": [{
        "kty": "RSA",
        "use": "sig",
        "alg": "RS256",
        "kid": KID,
        "n": _b64url_uint(public_numbers.n),
        "e": _b64url_uint(public_numbers.e),
    }]
}

app = Flask(__name__)


@app.route("/")
def root():
    return jsonify({
        "service": "mock-jwt-issuer",
        "issuer": ISSUER,
        "audience": AUDIENCE,
        "endpoints": {
            "discovery": "/.well-known/openid-configuration",
            "jwks": "/.well-known/jwks.json",
            "mint": "POST /token",
        },
        "example_curl": (
            "curl -X POST http://localhost:8090/token "
            "-d org_id=ORG-QHIN-001 -d node_id=NODE-CW-001 -d roles=PROVIDER"
        ),
    })


@app.route("/.well-known/openid-configuration")
def discovery():
    return jsonify({
        "issuer": ISSUER,
        "jwks_uri": f"{ISSUER}/.well-known/jwks.json",
        "token_endpoint": f"{ISSUER}/token",
        "id_token_signing_alg_values_supported": ["RS256"],
    })


@app.route("/.well-known/jwks.json")
def jwks():
    return jsonify(JWKS)


@app.route("/token", methods=["POST"])
def token():
    org_id = request.values.get("org_id", "ORG-QHIN-001")
    node_id = request.values.get("node_id", "NODE-CW-001")
    roles = request.values.get("roles", "PROVIDER").split(",")
    ttl = int(request.values.get("ttl_seconds", "3600"))
    # Honor an explicit subject if supplied (e.g. from the admin login flow);
    # otherwise fall back to a synthetic id for harness compatibility.
    subject = request.values.get("subject") or request.values.get("sub") \
        or f"user-{uuid.uuid4().hex[:8]}"

    now = int(time.time())
    claims = {
        "iss": ISSUER,
        "aud": AUDIENCE,
        "sub": subject,
        "iat": now,
        "exp": now + ttl,
        "jti": str(uuid.uuid4()),
        # Match SecurityConstants in tefca-common: CLAIM_ORG_ID="org_id",
        # CLAIM_NODE_ID="node_id", CLAIM_ROLES="roles".
        "org_id": org_id,
        "node_id": node_id,
        "roles": roles,
        "scope": "tefca:read tefca:write",
    }
    token_str = jwt.encode(
        claims,
        private_pem,
        algorithm="RS256",
        headers={"kid": KID, "typ": "JWT"},
    )
    return jsonify({
        "access_token": token_str,
        "token_type": "Bearer",
        "expires_in": ttl,
        "claims": claims,
    })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8090)
