package chit.tefca.ingress.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Structured access logging for all ingress requests.
 */
@Service
public class AccessLogService {

    private static final Logger accessLog = LoggerFactory.getLogger("ACCESS_LOG");

    public void logAccess(String correlationId, String method, String path, String orgId,
                          int status, long durationMs) {
        accessLog.info("correlationId={} method={} path={} orgId={} status={} durationMs={}",
                correlationId, method, path, orgId, status, durationMs);
    }
}
