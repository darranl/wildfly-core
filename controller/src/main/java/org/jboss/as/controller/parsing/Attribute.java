/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.parsing;

import java.util.HashMap;
import java.util.Map;

/**
 * An enumeration of all the recognized core configuration XML attributes, by local name.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public enum Attribute {
    // always first
    UNKNOWN(null),

    // xsi attributes in alpha order
    NO_NAMESPACE_SCHEMA_LOCATION("noNamespaceSchemaLocation"),
    SCHEMA_LOCATION("schemaLocation"),

    // domain attributes in alpha order
    ACTIVE_SERVER_GROUPS("active-server-groups"),
    ACTIVE_SOCKET_BINDING_GROUPS("active-socket-binding-groups"),
    ADMIN_ONLY_POLICY("admin-only-policy"),
    ALIAS("alias"),
    ALLOW_EMPTY_PASSWORDS("allow-empty-passwords"),
    ALLOWED_ORIGINS("allowed-origins"),
    ALLOWED_USERS("allowed-users"),
    ALWAYS_SEND_CLIENT_CERT("always-send-client-cert"),
    APP_NAME("app-name"),
    APPLICATION("application"),
    ARCHIVE("archive"),
    ASSIGN_GROUPS("assign-groups"),
    ATTRIBUTE("attribute"),
    AUTHENTICATION_CONTEXT("authentication-context"),
    AUTO_START("auto-start"),
    BACKLOG("backlog"),
    BASE_DN("base-dn"),
    BASE_ROLE("base-role"),
    BOOT_TIME("boot-time"),
    CACHE_FAILURES("cache-failures"),
    CODE("code"),
    COMPACT("compact"),
    CONNECTION("connection"),
    CONNECTION_HIGH_WATER("connection-high-water"),
    CONNECTION_LOW_WATER("connection-low-water"),
    CONNECTOR("connector"),
    CONSOLE_ENABLED("console-enabled"),
    CONTENT("content"),
    DATE_FORMAT("date-format"),
    DATE_SEPARATOR("date-separator"),
    DEBUG("debug"),
    DEFAULT_INTERFACE("default-interface"),
    DEFAULT_USER("default-user"),
    DEBUG_ENABLED("debug-enabled"),
    DEBUG_OPTIONS("debug-options"),
    DEPLOYMENT("deployment"),
    DEPLOYMENT_OVERLAY("deployment-overlay"),
    DESTINATION_ADDRESS("destination-address"),
    DIRECTORY_GROUPING("directory-grouping"),
    DESTINATION_PORT("destination-port"),
    DOMAIN_ORGANIZATION("domain-organization"),
    ENABLED("enabled"),
    ENABLED_CIPHER_SUITES("enabled-cipher-suites"),
    ENABLED_PROTOCOLS("enabled-protocols"),
    ENV_CLASSPATH_IGNORED("env-classpath-ignored"),
    ESCAPE_CONTROL_CHARACTERS("escape-control-characters"),
    ESCAPE_NEW_LINE("escape-new-line"),
    EVICTION_TIME("eviction-time"),
    FACILITY("facility"),
    FILE("file"),
    FILTER("filter"),
    FIXED_PORT("fixed-port"),
    FIXED_SOURCE_PORT("fixed-source-port"),
    FOR_HOSTS("for-hosts"),
    FORCE("force"),
    FORMATTER("formatter"),
    GENERATE_SELF_SIGNED_CERTIFICATE_HOST("generate-self-signed-certificate-host"),
    GRACEFUL_STARTUP("graceful-startup"),
    GROUP("group"),
    GROUP_ATTRIBUTE("group-attribute"),
    GROUP_DN_ATTRIBUTE("group-dn-attribute"),
    GROUP_NAME_ATTRIBUTE("group-name-attribute"),
    GROUP_NAME("group-name"),
    HANDLES_REFERRALS_FOR("handles-referrals-for"),
    HEADER("header"),
    HOST("host"),
    HTTP("http"),
    HTTP_AUTHENTICATION_FACTORY("http-authentication-factory"),
    HTTP_UPGRADE_ENABLED("http-upgrade-enabled"),
    HTTPS("https"),
    ID("id"),
    INCLUDES("includes"),
    IGNORE_UNUSED_CONFIG("ignore-unused-configuration"),
    INCLUDE_ALL("include-all"),
    INCLUDE_DATE("include-date"),
    INFLOW_SECURITY_DOMAINS("inflow-security-domains"),
    INITIAL_CONTEXT_FACTORY("initial-context-factory"),
    INTERFACE("interface"),
    ITERATIVE("iterative"),
    JAVA_HOME("java-home"),
    KEY_PASSWORD("key-password"),
    KEYSTORE_PASSWORD("keystore-password"),
    LOG_BOOT("log-boot"),
    LOG_READ_ONLY("log-read-only"),
    MAJOR_VERSION("major-version"),
    MANAGEMENT_SUBSYSTEM_ENDPOINT("management-subsystem-endpoint"),
    MAP_GROUPS_TO_ROLES("map-groups-to-roles"),
    MAX_HISTORY("max-history"),
    MAX_BACKUP_INDEX("max-backup-index"),
    MAX_CACHE_SIZE("max-cache-size"),
    MAX_FAILURE_COUNT("max-failure-count"),
    MAX_LENGTH("max-length"),
    MAX_SIZE("max-size"),
    MAX_THREADS("max-threads"),
    MECHANISM("mechanism"),
    MESSAGE_TRANSFER("message-transfer"),
    MICRO_VERSION("micro-version"),
    MINOR_VERSION("minor-version"),
    MODULE("module"),
    MULTICAST_ADDRESS("multicast-address"),
    MULTICAST_PORT("multicast-port"),
    NAME("name"),
    NATIVE("native"),
    NO_REQUEST_TIMEOUT("no-request-timeout"),
    ORGANIZATION("organization"),
    PARSE_ROLES_FROM_DN("parse-group-name-from-dn"),
    PASSWORD("password"),
    PATH("path"),
    PATTERN("pattern"),
    PERMISSION_COMBINATION_POLICY("permission-combination-policy"),
    PLAIN_TEXT("plain-text"),
    PORT("port"),
    PORT_OFFSET("port-offset"),
    PREFER_ORIGINAL_CONNECTION("prefer-original-connection"),
    PREFIX("prefix"),
    PRINCIPAL_ATTRIBUTE("principal-attribute"),
    PRINCIPAL("principal"),
    PROFILE("profile"),
    PROTOCOL("protocol"),
    PROVIDER("provider"),
    REALM("realm"),
    RECONNECT_TIMEOUT("reconnect-timeout"),
    RECURSIVE("recursive"),
    REF("ref"),
    REFERRALS("referrals"),
    REGULAR_EXPRESSION("regular-expression"),
    RESULT_PATTERN("result-pattern"),
    RELATIVE_TO("relative-to"),
    REMOVE_REALM("remove-realm"),
    REQUIRES_ADDRESSABLE("requires-addressable"),
    REQUIRES_READ("requires-read"),
    REQUIRES_WRITE("requires-write"),
    REVERSE_GROUP("reverse-group"),
    ROTATE_AT_STARTUP("rotate-at-startup"),
    ROTATE_SIZE("rotate-size"),
    RUNTIME_NAME("runtime-name"),
    SASL_AUTHENTICATION_FACTORY("sasl-authentication-factory"),
    SASL_PROTOCOL("sasl-protocol"),
    SEARCH_CREDENTIAL("search-credential"),
    SEARCH_BY("search-by"),
    SEARCH_DN("search-dn"),
    SECURE_INTERFACE("secure-interface"),
    SECURE_PORT("secure-port"),
    SECURITY_DOMAIN("security-domain"),
    SECURITY_REALM("security-realm"),
    SERVER_NAME("server-name"),
    SHA1("sha1"),
    SIZE("size"),
    SKIP_GROUP_LOADING("skip-group-loading"),
    SKIP_MISSING_GROUPS("skip-missing-groups"),
    SOCKET_BINDING_GROUP("socket-binding-group"),
    SOCKET_BINDING_REF("socket-binding-ref"),
    SOURCE_INTERFACE("source-interface"),
    SOURCE_NETWORK("source-network"),
    SOURCE_PORT("source-port"),
    SSL_CONTEXT("ssl-context"),
    SSL_PROTOCOL("ssl-protocol"),
    SUFFIX("suffix"),
    SYSLOG_FORMAT ("syslog-format"),
    TRUNCATE("truncate"),
    TRUST_MANAGER_ALGORITHM("trust-manager-algorithm"),
    TRUSTSTORE_PASSWORD("truststore-password"),
    TRUSTSTORE_PATH("truststore-path"),
    TRUSTSTORE_TYPE("truststore-type"),
    TYPE("type"),
    UPDATE_AUTO_START_WITH_SERVER_STATUS("update-auto-start-with-server-status"),
    URL("url"),
    USE_IDENTITY_ROLES("use-identity-roles"),
    USER("user"),
    USER_DN("user-dn"),
    USER_DN_ATTRIBUTE("user-dn-attribute"),
    USERNAME("username"),
    USERNAME_ATTRIBUTE("username-attribute"),
    USERNAME_LOAD("username-load"),
    VALUE("value"),
    WILDCARD("wildcard")
    ;

    private final String name;

    Attribute(final String name) {
        this.name = name;
    }

    /**
     * Get the local name of this element.
     *
     * @return the local name
     */
    public String getLocalName() {
        return name;
    }

    private static final Map<String, Attribute> MAP;

    static {
        final Map<String, Attribute> map = new HashMap<String, Attribute>();
        for (Attribute element : values()) {
            final String name = element.getLocalName();
            if (name != null) map.put(name, element);
        }
        MAP = map;
    }

    public static Attribute forName(String localName) {
        final Attribute element = MAP.get(localName);
        return element == null ? UNKNOWN : element;
    }
}
