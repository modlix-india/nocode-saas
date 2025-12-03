package com.fincity.saas.ui.service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.MobileApp;
import com.fincity.saas.ui.model.MobileAppStatusUpdateRequest;
import com.fincity.saas.ui.repository.MobileAppRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@Service
public class MobileAppService {

    private final UIMessageResourceService uiMessageResourceService;

    private final MobileAppRepository repo;

    private final WebClient webClient;

    @Value("${ui.apple.apiKeyId:}")
    private String appleApiKeyId;

    @Value("${ui.apple.apiIssuerId:}")
    private String appleApiIssuerId;

    @Value("${ui.apple.apiKeyContent:}")
    private String appleApiKeyContent;

    @Value("${ui.apple.bundleIdPrefix:com.modlix.apps}")
    private String bundleIdPrefix;

    @Value("${ui.apple.teamId:}")
    private String appleTeamId;

    @Value("${ui.apple.certificateId:}")
    private String appleCertificateId;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MobileAppService.class);

    public MobileAppService(MobileAppRepository repo, UIMessageResourceService uiMessageResourceService) {
        this.repo = repo;
        this.uiMessageResourceService = uiMessageResourceService;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.appstoreconnect.apple.com")
                .build();
    }

    @PostConstruct
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public Mono<List<MobileApp>> list(String appCode, String clientCode) {
        return this.repo.findByAppCodeAndClientCode(appCode, clientCode).collectList();
    }

    public record KeystoreBundle(String jksBase64, String storePass, String keyPass, String alias) {
    }

    public static KeystoreBundle createKeystore() throws Exception {
        // Ensure BC provider is registered, even if PostConstruct hasn't run for some
        // reason
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        String alias = "modlix";
        String storePass = new BigInteger(130, new SecureRandom()).toString(32);

        // Generate keypair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        // Self-signed cert (valid ~25 years)
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 86400000L);
        Date notAfter = new Date(now + 10000L * 24 * 60 * 60 * 1000); // 10000 days
        X500Name subject = new X500Name(
                "CN=Modlix Team, OU=Mobile Development, O=Modlix, L=Karnataka, ST=Karnataka, C=IN");
        BigInteger serial = new BigInteger(64, new SecureRandom());
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter,
                subject, kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(kp.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
        cert.checkValidity(new Date());
        cert.verify(kp.getPublic());

        // Store into JKS
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        ks.setKeyEntry(alias, kp.getPrivate(), storePass.toCharArray(), new java.security.cert.Certificate[] { cert });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ks.store(out, storePass.toCharArray());

        return new KeystoreBundle(Base64.getEncoder().encodeToString(out.toByteArray()), storePass, storePass, alias);
    }

    /**
     * Generates a JWT for Apple App Store Connect API authentication.
     * The JWT is signed with the ES256 algorithm using the private key from the .p8
     * file.
     */
    private String generateAppleJWT() throws Exception {
        if (StringUtil.safeIsBlank(appleApiKeyContent) || StringUtil.safeIsBlank(appleApiKeyId)
                || StringUtil.safeIsBlank(appleApiIssuerId)) {
            throw new IllegalStateException("Apple API credentials are not configured");
        }

        long now = System.currentTimeMillis();

        // Decode the base64 encoded .p8 file content
        String privateKeyPEM = new String(Base64.getDecoder().decode(appleApiKeyContent))
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(spec);

        return Jwts.builder()
                .setIssuer(appleApiIssuerId)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 20 * 60 * 1000)) // 20 minutes
                .setAudience("appstoreconnect-v1")
                .setHeaderParam("kid", appleApiKeyId)
                .setHeaderParam("typ", "JWT")
                .signWith(privateKey, SignatureAlgorithm.ES256)
                .compact();
    }

    /**
     * Result object containing Bundle ID identifier and its internal Apple ID.
     */
    public record BundleIdResult(String identifier, String appleId) {
    }

    /**
     * Creates a Bundle ID in Apple Developer Portal using the App Store Connect
     * API.
     * Returns both the bundle identifier and Apple's internal ID for the bundle.
     */
    public Mono<BundleIdResult> createAppleBundleId(String clientCode, String appName) {
        String sanitizedAppName = appName.toLowerCase().replaceAll("[^a-z0-9]", "");
        String sanitizedClientCode = clientCode.toLowerCase().replaceAll("[^a-z0-9]", "");
        String bundleId = bundleIdPrefix + "." + sanitizedClientCode + "." + sanitizedAppName;

        try {
            String jwt = generateAppleJWT();

            Map<String, Object> request = Map.of(
                    "data", Map.of(
                            "type", "bundleIds",
                            "attributes", Map.of(
                                    "identifier", bundleId,
                                    "name", appName + " - " + clientCode,
                                    "platform", "IOS")));

            return webClient.post()
                    .uri("/v1/bundleIds")
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(response -> {
                        String appleId = response.path("data").path("id").asText();
                        logger.info("Created Bundle ID: {} with Apple ID: {}", bundleId, appleId);
                        return new BundleIdResult(bundleId, appleId);
                    })
                    .onErrorResume(e -> {
                        // If bundle ID already exists, try to fetch its Apple ID
                        if (e.getMessage() != null && e.getMessage().contains("ENTITY_ERROR")) {
                            logger.info("Bundle ID {} already exists, fetching its Apple ID", bundleId);
                            return getAppleBundleIdByIdentifier(bundleId);
                        }
                        return Mono.error(e);
                    });
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    /**
     * Fetches an existing Bundle ID's Apple internal ID by its identifier.
     * Returns empty Mono if not found.
     */
    private Mono<BundleIdResult> getAppleBundleIdByIdentifier(String bundleIdentifier) {
        try {
            String jwt = generateAppleJWT();

            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/bundleIds")
                            .queryParam("filter[identifier]", bundleIdentifier)
                            .build())
                    .header("Authorization", "Bearer " + jwt)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .flatMap(response -> {
                        JsonNode data = response.path("data");
                        if (data.isArray() && data.size() > 0) {
                            String appleId = data.get(0).path("id").asText();
                            logger.info("Found existing Bundle ID: {} with Apple ID: {}", bundleIdentifier, appleId);
                            return Mono.just(new BundleIdResult(bundleIdentifier, appleId));
                        }
                        return Mono.empty();
                    });
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    /**
     * Checks if a Bundle ID exists in Apple Developer Portal.
     * Returns the BundleIdResult if found, empty Mono otherwise.
     */
    public Mono<BundleIdResult> checkBundleIdExists(String clientCode, String appName) {
        String sanitizedAppName = appName.toLowerCase().replaceAll("[^a-z0-9]", "");
        String sanitizedClientCode = clientCode.toLowerCase().replaceAll("[^a-z0-9]", "");
        String bundleId = bundleIdPrefix + "." + sanitizedClientCode + "." + sanitizedAppName;

        logger.info("Checking if Bundle ID exists: {}", bundleId);
        return getAppleBundleIdByIdentifier(bundleId)
                .doOnNext(result -> logger.info("Bundle ID check result: EXISTS - {} (Apple ID: {})",
                        result.identifier(), result.appleId()))
                .doOnTerminate(() -> logger.debug("Bundle ID check completed for: {}", bundleId));
    }

    /**
     * Gets or creates a Bundle ID. First checks if it exists, creates if not.
     */
    public Mono<BundleIdResult> getOrCreateBundleId(String clientCode, String appName) {
        String sanitizedAppName = appName.toLowerCase().replaceAll("[^a-z0-9]", "");
        String sanitizedClientCode = clientCode.toLowerCase().replaceAll("[^a-z0-9]", "");
        String bundleId = bundleIdPrefix + "." + sanitizedClientCode + "." + sanitizedAppName;

        logger.info("Getting or creating Bundle ID: {} (client: {}, app: {})", bundleId, clientCode, appName);

        return getAppleBundleIdByIdentifier(bundleId)
                .doOnNext(result -> logger.info("Bundle ID status: ALREADY EXISTS - {} (Apple ID: {})",
                        result.identifier(), result.appleId()))
                .switchIfEmpty(Mono.defer(() -> {
                    logger.info("Bundle ID status: NOT FOUND - Creating new Bundle ID: {}", bundleId);
                    return createAppleBundleId(clientCode, appName)
                            .doOnNext(
                                    result -> logger.info("Bundle ID status: CREATED SUCCESSFULLY - {} (Apple ID: {})",
                                            result.identifier(), result.appleId()))
                            .doOnError(e -> logger.error("Bundle ID status: CREATION FAILED - {} - Error: {}", bundleId,
                                    e.getMessage()));
                }));
    }

    /**
     * Gets or creates an App Store distribution provisioning profile for the given
     * Bundle ID.
     * First checks if a profile exists, then creates one if it doesn't exist.
     * Returns the base64 encoded provisioning profile content.
     */
    public Mono<String> createProvisioningProfile(String bundleIdAppleId, String profileName) {
        logger.info("=== createProvisioningProfile START ===");
        logger.info("Parameters: bundleIdAppleId={}, profileName={}", bundleIdAppleId, profileName);
        logger.info("Using certificateId: {}", appleCertificateId);

        if (StringUtil.safeIsBlank(appleCertificateId)) {
            logger.warn("Apple certificate ID not configured, skipping provisioning profile creation");
            return Mono.just("");
        }

        // First, check if a profile already exists for this bundle ID
        logger.info("Checking if provisioning profile already exists for bundle ID: {}", bundleIdAppleId);
        return getExistingProvisioningProfile(bundleIdAppleId)
                .flatMap(existingProfile -> {
                    if (!StringUtil.safeIsBlank(existingProfile)) {
                        logger.info("Found existing provisioning profile for bundle ID: {} (content length: {} chars)",
                                bundleIdAppleId, existingProfile.length());
                        return Mono.just(existingProfile);
                    }
                    // Profile doesn't exist, try to create it
                    logger.info("No existing provisioning profile found, creating new profile: {}", profileName);
                    return createNewProvisioningProfile(bundleIdAppleId, profileName);
                });
    }

    /**
     * Creates a new provisioning profile via Apple API.
     * This method is called when no existing profile is found.
     */
    private Mono<String> createNewProvisioningProfile(String bundleIdAppleId, String profileName) {
        logger.info("=== createNewProvisioningProfile START ===");
        logger.info("Creating new provisioning profile: {} for bundle ID: {}", profileName, bundleIdAppleId);

        try {
            String jwt = generateAppleJWT();
            logger.info("JWT generated successfully (length: {} chars)", jwt.length());

            Map<String, Object> request = Map.of(
                    "data", Map.of(
                            "type", "profiles",
                            "attributes", Map.of(
                                    "name", profileName,
                                    "profileType", "IOS_APP_STORE"),
                            "relationships", Map.of(
                                    "bundleId", Map.of(
                                            "data", Map.of(
                                                    "type", "bundleIds",
                                                    "id", bundleIdAppleId)),
                                    "certificates", Map.of(
                                            "data", List.of(
                                                    Map.of(
                                                            "type", "certificates",
                                                            "id", appleCertificateId))))));

            logger.info("Sending request to Apple API: POST /v1/profiles");
            logger.debug("Request body: {}", request);

            return webClient.post()
                    .uri("/v1/profiles")
                    .header("Authorization", "Bearer " + jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(body -> {
                                    logger.error("Apple API error response: status={}, body={}",
                                            clientResponse.statusCode(), body);
                                    return Mono.error(new RuntimeException("Apple API error: " + body));
                                });
                    })
                    .bodyToMono(JsonNode.class)
                    .doOnNext(response -> logger.info("Apple API response received: {}",
                            response.toString().substring(0, Math.min(500, response.toString().length()))))
                    .map(response -> {
                        String profileContent = response.path("data").path("attributes").path("profileContent")
                                .asText();
                        logger.info("Successfully created provisioning profile: {} (content length: {} chars)",
                                profileName, profileContent.length());
                        return profileContent; // Already base64 encoded
                    })
                    .onErrorResume(e -> {
                        logger.error("Failed to create provisioning profile: {} - Full error: ", e.getMessage(), e);
                        String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

                        // Check if the error is related to certificate issues - this should fail
                        // immediately
                        // Certificate errors can be ENTITY_ERRORs, so check this first
                        if (errorMessage.contains("certificate") || errorMessage.contains("no current certificates")
                                || errorMessage.contains("certificate ids")
                                || errorMessage.contains("certificates on this team")) {
                            logger.error(
                                    "Certificate error detected. Certificate may be invalid, expired, or not compatible with IOS_APP_STORE profiles.");
                            return Mono.error(new RuntimeException(
                                    "Failed to create provisioning profile. Certificate may be invalid, expired, or not compatible with IOS_APP_STORE profiles. Please verify the certificate ID is correct and the certificate is valid for App Store distribution. Error: "
                                            + e.getMessage(),
                                    e));
                        }
                        // For other ENTITY_ERRORs (like profile already exists), try to fetch it again
                        if (errorMessage.contains("entity_error")) {
                            logger.info(
                                    "Profile creation failed with ENTITY_ERROR (non-certificate), attempting to fetch existing profile again...");
                            return getExistingProvisioningProfile(bundleIdAppleId)
                                    .flatMap(existingProfile -> {
                                        if (!StringUtil.safeIsBlank(existingProfile)) {
                                            logger.info(
                                                    "Found existing profile after creation attempt (content length: {} chars)",
                                                    existingProfile.length());
                                            return Mono.just(existingProfile);
                                        }
                                        // Still no profile found - this is an error
                                        logger.error(
                                                "Cannot create provisioning profile and no existing profile found after retry.");
                                        return Mono.error(new RuntimeException(
                                                "Failed to create or retrieve provisioning profile: " + e.getMessage(),
                                                e));
                                    });
                        }
                        // For other errors, fail the operation
                        logger.error("Provisioning profile creation failed: {}", e.getMessage());
                        return Mono.error(
                                new RuntimeException("Failed to create provisioning profile: " + e.getMessage(), e));
                    });
        } catch (Exception e) {
            logger.error("Error creating provisioning profile", e);
            return Mono.error(new RuntimeException("Failed to create provisioning profile: " + e.getMessage(), e));
        }
    }

    /**
     * Fetches an existing provisioning profile for a Bundle ID.
     */
    private Mono<String> getExistingProvisioningProfile(String bundleIdAppleId) {
        logger.info("=== getExistingProvisioningProfile START: bundleIdAppleId={} ===", bundleIdAppleId);
        try {
            String jwt = generateAppleJWT();

            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/profiles")
                            .queryParam("filter[profileType]", "IOS_APP_STORE")
                            .queryParam("include", "bundleId")
                            .build())
                    .header("Authorization", "Bearer " + jwt)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(body -> {
                                    logger.error("Apple API error fetching profiles: status={}, body={}",
                                            clientResponse.statusCode(), body);
                                    return Mono.error(new RuntimeException("Apple API error: " + body));
                                });
                    })
                    .bodyToMono(JsonNode.class)
                    .doOnNext(response -> logger.info("Fetched {} profiles from Apple", response.path("data").size()))
                    .map(response -> {
                        JsonNode data = response.path("data");
                        if (data.isArray()) {
                            logger.info("Searching through {} profiles for bundleIdAppleId: {}", data.size(),
                                    bundleIdAppleId);
                            for (JsonNode profile : data) {
                                String profileBundleId = profile.path("relationships").path("bundleId")
                                        .path("data").path("id").asText();
                                String profileName = profile.path("attributes").path("name").asText();
                                logger.debug("Profile: name={}, bundleIdRef={}", profileName, profileBundleId);
                                if (bundleIdAppleId.equals(profileBundleId)) {
                                    String profileContent = profile.path("attributes").path("profileContent").asText();
                                    logger.info(
                                            "Found existing provisioning profile: {} for bundle ID: {} (content length: {} chars)",
                                            profileName, bundleIdAppleId, profileContent.length());
                                    return profileContent;
                                }
                            }
                        }
                        logger.warn("No existing provisioning profile found for bundle ID: {}", bundleIdAppleId);
                        return "";
                    })
                    .onErrorResume(e -> {
                        logger.error("Error fetching existing provisioning profiles: {}", e.getMessage(), e);
                        return Mono.just("");
                    });
        } catch (Exception e) {
            logger.error("Error fetching existing provisioning profile", e);
            return Mono.just("");
        }
    }

    /**
     * Lists all available certificates for the team.
     * Useful for verifying certificate IDs and finding App Store Distribution
     * certificates.
     */
    public Mono<List<Map<String, String>>> listCertificates() {
        logger.info("=== listCertificates START ===");
        try {
            String jwt = generateAppleJWT();

            return webClient.get()
                    .uri("/v1/certificates")
                    .header("Authorization", "Bearer " + jwt)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(body -> {
                                    logger.error("Apple API error fetching certificates: status={}, body={}",
                                            clientResponse.statusCode(), body);
                                    return Mono.error(new RuntimeException("Apple API error: " + body));
                                });
                    })
                    .bodyToMono(JsonNode.class)
                    .map(response -> {
                        List<Map<String, String>> certificates = new java.util.ArrayList<>();
                        JsonNode data = response.path("data");
                        if (data.isArray()) {
                            logger.info("Found {} certificates", data.size());
                            for (JsonNode cert : data) {
                                String certId = cert.path("id").asText();
                                String certType = cert.path("attributes").path("certificateType").asText();
                                String displayName = cert.path("attributes").path("displayName").asText();
                                String expirationDate = cert.path("attributes").path("expirationDate").asText();

                                Map<String, String> certInfo = new java.util.HashMap<>();
                                certInfo.put("id", certId);
                                certInfo.put("type", certType);
                                certInfo.put("displayName", displayName);
                                certInfo.put("expirationDate", expirationDate);
                                certificates.add(certInfo);

                                logger.info("Certificate: ID={}, Type={}, Name={}, Expires={}",
                                        certId, certType, displayName, expirationDate);
                            }
                        }
                        return certificates;
                    })
                    .doOnNext(certs -> {
                        logger.info("Total certificates found: {}", certs.size());
                        // Check if configured certificate ID exists
                        if (!StringUtil.safeIsBlank(appleCertificateId)) {
                            boolean found = certs.stream()
                                    .anyMatch(c -> appleCertificateId.equals(c.get("id")));
                            if (found) {
                                Map<String, String> cert = certs.stream()
                                        .filter(c -> appleCertificateId.equals(c.get("id")))
                                        .findFirst()
                                        .orElse(null);
                                if (cert != null) {
                                    logger.info("Configured certificate ID {} found: Type={}, Name={}, Expires={}",
                                            appleCertificateId, cert.get("type"), cert.get("displayName"),
                                            cert.get("expirationDate"));
                                    // Check if it's compatible with IOS_APP_STORE
                                    if (!cert.get("type").equals("IOS_DISTRIBUTION") &&
                                            !cert.get("type").equals("APPLE_DISTRIBUTION")) {
                                        logger.warn(
                                                "WARNING: Certificate {} is type {}, not IOS_DISTRIBUTION or APPLE_DISTRIBUTION. It may not be compatible with IOS_APP_STORE profiles.",
                                                appleCertificateId, cert.get("type"));
                                    }
                                }
                            } else {
                                logger.error("ERROR: Configured certificate ID {} NOT FOUND in available certificates!",
                                        appleCertificateId);
                            }
                        }
                    });
        } catch (Exception e) {
            logger.error("Error listing certificates", e);
            return Mono.error(new RuntimeException("Failed to list certificates: " + e.getMessage(), e));
        }
    }

    /**
     * Creates Bundle ID and Provisioning Profile for PLATFORM_ACCOUNT mode.
     * Returns a record containing all the iOS configuration.
     */
    public record IosConfigResult(String bundleId, String teamId, String provisioningProfile) {
    }

    public Mono<IosConfigResult> setupIosForPlatformAccount(String clientCode, String appName) {
        logger.info("Starting iOS PLATFORM_ACCOUNT setup for client: {}, app: {}", clientCode, appName);

        return getOrCreateBundleId(clientCode, appName)
                .flatMap(bundleIdResult -> {
                    String profileName = appName + " - " + clientCode + " - App Store";
                    logger.info("Provisioning Profile status: CREATING - {} for Bundle ID: {}", profileName,
                            bundleIdResult.identifier());

                    return createProvisioningProfile(bundleIdResult.appleId(), profileName)
                            .flatMap(profileContent -> {
                                if (StringUtil.safeIsBlank(profileContent)) {
                                    // If certificate ID is configured, we expected a profile - this is an error
                                    if (!StringUtil.safeIsBlank(appleCertificateId)) {
                                        logger.error(
                                                "Provisioning Profile status: FAILED - Certificate ID is configured but profile creation/retrieval failed");
                                        return Mono.error(new RuntimeException(
                                                "Failed to create or retrieve provisioning profile. Certificate ID is configured but no profile could be obtained."));
                                    } else {
                                        logger.warn(
                                                "Provisioning Profile status: NOT CREATED (certificate ID not configured - this is acceptable)");
                                    }
                                } else {
                                    logger.info(
                                            "Provisioning Profile status: SUCCESS - Profile created/retrieved for Bundle ID: {}",
                                            bundleIdResult.identifier());
                                }
                                return Mono.just(new IosConfigResult(
                                        bundleIdResult.identifier(),
                                        appleTeamId,
                                        profileContent));
                            });
                })
                .doOnSuccess(result -> logger.info(
                        "iOS PLATFORM_ACCOUNT setup completed: bundleId={}, teamId={}, hasProfile={}",
                        result.bundleId(), result.teamId(), !StringUtil.safeIsBlank(result.provisioningProfile())))
                .doOnError(e -> logger.error("iOS PLATFORM_ACCOUNT setup failed: {}", e.getMessage()));
    }

    /**
     * Checks if Apple API is configured for Bundle ID creation and basic
     * PLATFORM_ACCOUNT mode.
     * Only requires API credentials and team ID - certificate ID is optional (for
     * auto provisioning profile creation).
     */
    private boolean isAppleApiConfiguredForBundleId() {
        boolean apiKeyIdConfigured = !StringUtil.safeIsBlank(appleApiKeyId);
        boolean apiIssuerIdConfigured = !StringUtil.safeIsBlank(appleApiIssuerId);
        boolean apiKeyContentConfigured = !StringUtil.safeIsBlank(appleApiKeyContent);
        boolean teamIdConfigured = !StringUtil.safeIsBlank(appleTeamId);

        boolean configured = apiKeyIdConfigured && apiIssuerIdConfigured && apiKeyContentConfigured && teamIdConfigured;

        logger.info(
                "Apple API Configuration Check (Bundle ID): apiKeyId={}, apiIssuerId={}, apiKeyContent={}, teamId={} => CONFIGURED={}",
                apiKeyIdConfigured, apiIssuerIdConfigured, apiKeyContentConfigured, teamIdConfigured, configured);

        if (!configured) {
            logger.warn("Apple API not configured for Bundle ID creation. Missing: {}{}{}{}",
                    !apiKeyIdConfigured ? "apiKeyId " : "",
                    !apiIssuerIdConfigured ? "apiIssuerId " : "",
                    !apiKeyContentConfigured ? "apiKeyContent " : "",
                    !teamIdConfigured ? "teamId " : "");
        }

        // Log certificate ID status separately (optional for auto provisioning profile)
        boolean certificateIdConfigured = !StringUtil.safeIsBlank(appleCertificateId);
        logger.info("Apple Certificate ID configured (for auto provisioning profile): {}", certificateIdConfigured);

        return configured;
    }

    public Mono<MobileApp> update(MobileApp mobileApp) {
        logger.info("=== MobileApp Update/Create Started ===");
        logger.info("MobileApp ID: {}, ClientCode: {}, AppCode: {}",
                mobileApp.getId(), mobileApp.getClientCode(), mobileApp.getAppCode());

        if (mobileApp.getDetails() != null) {
            logger.info("Details: name={}, ios={}, android={}, iosPublishMode={}",
                    mobileApp.getDetails().getName(),
                    mobileApp.getDetails().isIos(),
                    mobileApp.getDetails().isAndroid(),
                    mobileApp.getDetails().getIosPublishMode());
        } else {
            logger.warn("MobileApp details is NULL!");
        }

        if (mobileApp.getId() == null) {
            logger.info("Creating NEW MobileApp");

            // New app - generate Android keystore
            try {
                KeystoreBundle bundle = createKeystore();
                mobileApp.setAndroidKeystore(bundle.jksBase64);
                mobileApp.setAndroidStorePass(bundle.storePass);
                mobileApp.setAndroidKeyPass(bundle.keyPass);
                mobileApp.setAndroidAlias(bundle.alias);
                logger.info("Android keystore generated successfully");
            } catch (Exception ex) {
                logger.error("Failed to generate Android keystore", ex);
                return this.uiMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg, ex),
                        UIMessageResourceService.MOBILE_APP_UNABLE_TO_GEN_KEYSTORE);
            }

            // iOS handling for PLATFORM_ACCOUNT mode
            boolean hasDetails = mobileApp.getDetails() != null;
            boolean isIos = hasDetails && mobileApp.getDetails().isIos();
            boolean isPlatformAccount = hasDetails
                    && MobileApp.IosPublishMode.PLATFORM_ACCOUNT == mobileApp.getDetails().getIosPublishMode();
            boolean apiConfigured = isAppleApiConfiguredForBundleId();

            logger.info("iOS Setup Check: hasDetails={}, isIos={}, isPlatformAccount={}, apiConfigured={}",
                    hasDetails, isIos, isPlatformAccount, apiConfigured);

            if (hasDetails && isIos && isPlatformAccount && apiConfigured) {
                logger.info("Proceeding with iOS PLATFORM_ACCOUNT setup...");
                return setupIosForPlatformAccount(mobileApp.getClientCode(), mobileApp.getDetails().getName())
                        .flatMap(iosConfig -> {
                            mobileApp.setIosBundleId(iosConfig.bundleId());
                            mobileApp.setIosTeamId(iosConfig.teamId());
                            if (!StringUtil.safeIsBlank(iosConfig.provisioningProfile())) {
                                mobileApp.setIosProvisioningProfile(iosConfig.provisioningProfile());
                                logger.info("Provisioning profile SET on MobileApp (length: {} chars)",
                                        iosConfig.provisioningProfile().length());
                            } else {
                                logger.warn("Provisioning profile is EMPTY - not setting on MobileApp");
                            }
                            logger.info("iOS PLATFORM_ACCOUNT setup complete: bundleId={}, teamId={}, hasProfile={}",
                                    iosConfig.bundleId(), iosConfig.teamId(),
                                    !StringUtil.safeIsBlank(iosConfig.provisioningProfile()));
                            return this.repo.save(mobileApp);
                        })
                        .doOnSuccess(saved -> logger.info(
                                "MobileApp SAVED with iOS config: iosBundleId={}, iosTeamId={}, hasProvisioningProfile={}",
                                saved.getIosBundleId(), saved.getIosTeamId(),
                                !StringUtil.safeIsBlank(saved.getIosProvisioningProfile())))
                        .onErrorResume(e -> {
                            // If iOS setup fails, don't save the app - fail the operation
                            logger.error("Failed to setup iOS for PLATFORM_ACCOUNT mode: {}", e.getMessage(), e);
                            return Mono.error(new RuntimeException(
                                    "Failed to setup iOS configuration for mobile app: " + e.getMessage(), e));
                        });
            } else {
                logger.info("Skipping iOS PLATFORM_ACCOUNT setup (conditions not met)");
            }

            return this.repo.save(mobileApp)
                    .doOnSuccess(saved -> logger.info("MobileApp SAVED (new): id={}", saved.getId()));
        }

        logger.info("Updating EXISTING MobileApp with ID: {}", mobileApp.getId());

        return this.repo.findById(mobileApp.getId())
                .doOnNext(existing -> logger.info(
                        "Found existing MobileApp: iosBundleId={}, iosTeamId={}, hasProvisioningProfile={}",
                        existing.getIosBundleId(), existing.getIosTeamId(),
                        !StringUtil.safeIsBlank(existing.getIosProvisioningProfile())))
                .flatMap(existing -> {
                    // Handle Android keystore
                    if (existing.getAndroidKeystore() == null) {
                        try {
                            KeystoreBundle bundle = createKeystore();
                            mobileApp.setAndroidKeystore(bundle.jksBase64);
                            mobileApp.setAndroidStorePass(bundle.storePass);
                            mobileApp.setAndroidKeyPass(bundle.keyPass);
                            mobileApp.setAndroidAlias(bundle.alias);
                            logger.info("Android keystore generated for existing app");
                        } catch (Exception ex) {
                            logger.error("Failed to generate Android keystore for existing app", ex);
                            return this.uiMessageResourceService.throwMessage(
                                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg, ex),
                                    UIMessageResourceService.MOBILE_APP_UNABLE_TO_GEN_KEYSTORE);
                        }
                    } else {
                        mobileApp.setAndroidKeystore(existing.getAndroidKeystore());
                        mobileApp.setAndroidStorePass(existing.getAndroidStorePass());
                        mobileApp.setAndroidKeyPass(existing.getAndroidKeyPass());
                        mobileApp.setAndroidAlias(existing.getAndroidAlias());
                    }

                    // Handle iOS Bundle ID and Provisioning Profile for PLATFORM_ACCOUNT mode
                    boolean hasDetails = mobileApp.getDetails() != null;
                    boolean isIos = hasDetails && mobileApp.getDetails().isIos();
                    boolean isPlatformAccount = hasDetails
                            && MobileApp.IosPublishMode.PLATFORM_ACCOUNT == mobileApp.getDetails().getIosPublishMode();
                    boolean needsBundleId = StringUtil.safeIsBlank(existing.getIosBundleId());
                    boolean apiConfigured = isAppleApiConfiguredForBundleId();

                    logger.info(
                            "iOS Setup Check (update): hasDetails={}, isIos={}, isPlatformAccount={}, needsBundleId={}, apiConfigured={}",
                            hasDetails, isIos, isPlatformAccount, needsBundleId, apiConfigured);

                    if (hasDetails && isIos && isPlatformAccount && needsBundleId && apiConfigured) {
                        logger.info("Proceeding with iOS PLATFORM_ACCOUNT setup for existing app...");
                        mobileApp.getDetails().setVersion(existing.getDetails().getVersion() + 1);
                        return setupIosForPlatformAccount(mobileApp.getClientCode(), mobileApp.getDetails().getName())
                                .flatMap(iosConfig -> {
                                    mobileApp.setIosBundleId(iosConfig.bundleId());
                                    mobileApp.setIosTeamId(iosConfig.teamId());
                                    if (!StringUtil.safeIsBlank(iosConfig.provisioningProfile())) {
                                        mobileApp.setIosProvisioningProfile(iosConfig.provisioningProfile());
                                        logger.info("Provisioning profile SET on existing MobileApp (length: {} chars)",
                                                iosConfig.provisioningProfile().length());
                                    } else {
                                        logger.warn("Provisioning profile is EMPTY for existing app - not setting");
                                    }
                                    logger.info(
                                            "iOS PLATFORM_ACCOUNT setup complete (update): bundleId={}, teamId={}, hasProfile={}",
                                            iosConfig.bundleId(), iosConfig.teamId(),
                                            !StringUtil.safeIsBlank(iosConfig.provisioningProfile()));
                                    return this.repo.save(mobileApp);
                                })
                                .onErrorResume(e -> {
                                    // If iOS setup fails, don't save the app - fail the operation
                                    logger.error("Failed to setup iOS for PLATFORM_ACCOUNT mode (update): {}",
                                            e.getMessage(), e);
                                    return Mono.error(new RuntimeException(
                                            "Failed to setup iOS configuration for mobile app: " + e.getMessage(), e));
                                });
                    } else {
                        // Bundle ID exists, but check if provisioning profile needs to be created
                        boolean hasBundleId = !StringUtil.safeIsBlank(existing.getIosBundleId());
                        boolean needsProvisioningProfile = StringUtil.safeIsBlank(existing.getIosProvisioningProfile());

                        logger.info(
                                "iOS Update Check: hasBundleId={}, needsProvisioningProfile={}, isIos={}, isPlatformAccount={}, apiConfigured={}",
                                hasBundleId, needsProvisioningProfile, isIos, isPlatformAccount, apiConfigured);

                        // If bundle ID exists but provisioning profile is missing, create it
                        if (hasDetails && isIos && isPlatformAccount && hasBundleId && needsProvisioningProfile
                                && apiConfigured) {
                            logger.info(
                                    "Bundle ID exists but provisioning profile is missing, creating provisioning profile...");
                            mobileApp.getDetails().setVersion(existing.getDetails().getVersion() + 1);

                            // Get the bundle ID's Apple ID to create provisioning profile
                            return getOrCreateBundleId(mobileApp.getClientCode(), mobileApp.getDetails().getName())
                                    .flatMap(bundleIdResult -> {
                                        String profileName = mobileApp.getDetails().getName() + " - "
                                                + mobileApp.getClientCode() + " - App Store";
                                        logger.info("Creating provisioning profile: {} for existing bundle ID: {}",
                                                profileName, bundleIdResult.identifier());

                                        return createProvisioningProfile(bundleIdResult.appleId(), profileName)
                                                .flatMap(profileContent -> {
                                                    // Preserve existing iOS fields
                                                    mobileApp.setIosBundleId(existing.getIosBundleId());
                                                    if (!StringUtil.safeIsBlank(existing.getIosTeamId())) {
                                                        mobileApp.setIosTeamId(existing.getIosTeamId());
                                                    } else {
                                                        mobileApp.setIosTeamId(appleTeamId);
                                                    }

                                                    if (!StringUtil.safeIsBlank(profileContent)) {
                                                        mobileApp.setIosProvisioningProfile(profileContent);
                                                        logger.info(
                                                                "Provisioning profile CREATED and SET on existing MobileApp (length: {} chars)",
                                                                profileContent.length());
                                                    } else {
                                                        // If certificate ID is configured, we expected a profile - this
                                                        // is an error
                                                        if (!StringUtil.safeIsBlank(appleCertificateId)) {
                                                            logger.error(
                                                                    "Provisioning Profile status: FAILED - Certificate ID is configured but profile creation/retrieval failed");
                                                            return Mono.error(new RuntimeException(
                                                                    "Failed to create or retrieve provisioning profile. Certificate ID is configured but no profile could be obtained."));
                                                        } else {
                                                            logger.warn(
                                                                    "Provisioning Profile status: NOT CREATED (certificate ID not configured - this is acceptable)");
                                                        }
                                                    }

                                                    logger.info(
                                                            "iOS PLATFORM_ACCOUNT provisioning profile setup complete (update): bundleId={}, teamId={}, hasProfile={}",
                                                            mobileApp.getIosBundleId(), mobileApp.getIosTeamId(),
                                                            !StringUtil.safeIsBlank(
                                                                    mobileApp.getIosProvisioningProfile()));
                                                    return this.repo.save(mobileApp);
                                                })
                                                .onErrorResume(e -> {
                                                    // If provisioning profile creation fails, don't save the app - fail
                                                    // the operation
                                                    logger.error(
                                                            "Failed to create provisioning profile for existing app: {}",
                                                            e.getMessage(), e);
                                                    return Mono.error(new RuntimeException(
                                                            "Failed to create provisioning profile for mobile app: "
                                                                    + e.getMessage(),
                                                            e));
                                                });
                                    });
                        } else {
                            // Preserve existing iOS fields if present
                            logger.info("Preserving existing iOS fields (not creating new)");
                            if (!StringUtil.safeIsBlank(existing.getIosBundleId())) {
                                mobileApp.setIosBundleId(existing.getIosBundleId());
                            }
                            if (!StringUtil.safeIsBlank(existing.getIosTeamId())) {
                                mobileApp.setIosTeamId(existing.getIosTeamId());
                            }
                            if (!StringUtil.safeIsBlank(existing.getIosProvisioningProfile())) {
                                mobileApp.setIosProvisioningProfile(existing.getIosProvisioningProfile());
                            }
                            logger.info("After preserving: iosBundleId={}, iosTeamId={}, hasProvisioningProfile={}",
                                    mobileApp.getIosBundleId(), mobileApp.getIosTeamId(),
                                    !StringUtil.safeIsBlank(mobileApp.getIosProvisioningProfile()));
                        }
                    }

                    mobileApp.getDetails().setVersion(existing.getDetails().getVersion() + 1);
                    return this.repo.save(mobileApp)
                            .doOnSuccess(saved -> logger.info(
                                    "MobileApp SAVED (update): iosBundleId={}, iosTeamId={}, hasProvisioningProfile={}",
                                    saved.getIosBundleId(), saved.getIosTeamId(),
                                    !StringUtil.safeIsBlank(saved.getIosProvisioningProfile())));
                });
    }

    public Mono<MobileApp> getNextMobileAppToGenerate() {

        return this.repo.findFirstByStatusOrderByUpdatedAtAsc(MobileApp.Status.PENDING);
    }

    public Mono<Boolean> updateStatus(String id, MobileAppStatusUpdateRequest request) {

        return this.repo.findById(id)
                .flatMap(e -> {
                    e.setUpdatedAt(LocalDateTime.now());
                    if (!StringUtil.safeIsBlank(request.getErrorMessage())) {
                        e.setStatus(MobileApp.Status.FAILED);
                        e.setErrorMessage(request.getErrorMessage());
                    } else {
                        e.setStatus(request.getStatus());
                        if (request.getStatus() == MobileApp.Status.SUCCESS) {
                            e.setErrorMessage(null);
                        }
                    }
                    e.setAndroidAppURL(request.getAndroidAppURL());
                    e.setIosAppURL(request.getIosAppURL());
                    return this.repo.save(e);
                })
                .map(e -> true);
    }

    public Mono<MobileApp> readMobileApp(String id) {
        return this.repo.findById(id);
    }

    public Mono<Boolean> deleteMobileApp(String id) {
        return this.repo.deleteById(id).thenReturn(true);
    }
}
