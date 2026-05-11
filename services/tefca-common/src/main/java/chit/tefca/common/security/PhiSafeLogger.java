package chit.tefca.common.security;

import chit.tefca.common.logging.PhiMaskingConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logger wrapper that strips PHI (Protected Health Information) from log
 * messages and arguments. Use this for any log statement that might contain
 * patient data. Masking rules are shared with {@link PhiMaskingConverter}
 * so the same redactions apply whether sanitisation happens at the call site
 * (this class) or the appender (the converter).
 */
public final class PhiSafeLogger {

    private final Logger delegate;

    private PhiSafeLogger(Class<?> clazz) {
        this.delegate = LoggerFactory.getLogger(clazz);
    }

    public static PhiSafeLogger getLogger(Class<?> clazz) {
        return new PhiSafeLogger(clazz);
    }

    public void info(String message, Object... args) {
        delegate.info(sanitize(message), sanitizeArgs(args));
    }

    public void warn(String message, Object... args) {
        delegate.warn(sanitize(message), sanitizeArgs(args));
    }

    public void error(String message, Object... args) {
        delegate.error(sanitize(message), sanitizeArgs(args));
    }

    public void debug(String message, Object... args) {
        delegate.debug(sanitize(message), sanitizeArgs(args));
    }

    static String sanitize(String message) {
        return PhiMaskingConverter.mask(message);
    }

    private static Object[] sanitizeArgs(Object[] args) {
        if (args == null) return null;
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            out[i] = args[i] instanceof String s ? PhiMaskingConverter.mask(s) : args[i];
        }
        return out;
    }
}
