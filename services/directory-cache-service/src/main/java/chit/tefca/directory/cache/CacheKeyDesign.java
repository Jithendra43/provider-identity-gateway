package chit.tefca.directory.cache;

/**
 * Centralized Redis key design for directory cache.
 * All keys use the "dir:" prefix to namespace within the shared Redis instance.
 */
public final class CacheKeyDesign {

    private CacheKeyDesign() {
    }

    private static final String PREFIX = "dir:";

    public static String orgKey(String orgId) {
        return PREFIX + "org:" + orgId;
    }

    public static String nodeKey(String nodeId) {
        return PREFIX + "node:" + nodeId;
    }

    public static String endpointsKey(String orgId, String modality) {
        return PREFIX + "endpoints:" + orgId + ":" + (modality != null ? modality : "ALL");
    }

    public static String capabilitiesKey(String nodeId) {
        return PREFIX + "capabilities:" + nodeId;
    }

    public static String snapshotCurrentKey() {
        return PREFIX + "snapshot:current";
    }

    public static String orgEndpointsPattern(String orgId) {
        return PREFIX + "endpoints:" + orgId + ":*";
    }

    public static String allKeysPattern() {
        return PREFIX + "*";
    }
}
