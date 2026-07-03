package com.modlix.saas.adzump.platform.google;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.google.ads.googleads.lib.GoogleAdsClient;
import com.google.ads.googleads.v24.services.CustomerServiceClient;
import com.google.ads.googleads.v24.services.GenerateKeywordIdeaResult;
import com.google.ads.googleads.v24.services.GenerateKeywordIdeasRequest;
import com.google.ads.googleads.v24.services.GeoTargetConstantServiceClient;
import com.google.ads.googleads.v24.services.GeoTargetConstantSuggestion;
import com.google.ads.googleads.v24.services.GoogleAdsRow;
import com.google.ads.googleads.v24.services.GoogleAdsServiceClient;
import com.google.ads.googleads.v24.services.GoogleAdsVersion;
import com.google.ads.googleads.v24.services.KeywordAndUrlSeed;
import com.google.ads.googleads.v24.services.KeywordPlanIdeaServiceClient;
import com.google.ads.googleads.v24.services.KeywordSeed;
import com.google.ads.googleads.v24.services.ListAccessibleCustomersRequest;
import com.google.ads.googleads.v24.services.MutateGoogleAdsResponse;
import com.google.ads.googleads.v24.services.MutateOperation;
import com.google.ads.googleads.v24.services.SuggestGeoTargetConstantsRequest;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * The thin, injectable seam around the official {@code google-ads-java} {@link GoogleAdsClient} (J4
 * §5.1). Every real Google Ads I/O the platform needs goes through exactly these methods, so
 * {@link GooglePlatform} / {@link GoogleLifecycle} / {@link GoogleGaqlReader} never touch the raw
 * {@code GoogleAdsClient} and the unit tests <b>mock this facade</b> — no network, no gRPC.
 *
 * <p>A {@code GoogleAdsClient} is built per call from three inputs the design requires together: the
 * developer token (pinned in config, {@code adzump.google.developer-token}), the OAuth access token
 * that J2 {@code ConnectionService.resolve(GOOGLE)} produced (carried on the SPI {@link Token}), and
 * the {@code login-customer-id} MCC (also on the {@link Token}). The API version is pinned to
 * {@code v24} both here (the {@code com.google.ads.googleads.v24} import package + {@link #version})
 * and in {@code adzump.google.api-version}; the two must move together.
 *
 * <p>All SDK / gRPC failures are mapped to a {@link GenericException} via
 * {@link AdzumpMessageResourceService#GOOGLE_API_ERROR} so no {@code google-ads} exception type leaks
 * above the facade.
 *
 * <p><b>Live-path note:</b> the actual paused-launch against a real Google Ads account is a
 * deferred, connected-account step (needs Kiran's MCC + a linked customer); this class builds the
 * real client code for it but is exercised offline only through its mock.
 */
@Component
public class GoogleAdsClientFacade {

    private final String developerToken;
    private final String apiVersion;
    private final AdzumpMessageResourceService msg;

    public GoogleAdsClientFacade(
            @Value("${adzump.google.developer-token:}") String developerToken,
            @Value("${adzump.google.api-version:v24}") String apiVersion,
            AdzumpMessageResourceService msg) {
        this.developerToken = developerToken;
        this.apiVersion = apiVersion;
        this.msg = msg;
    }

    /** The pinned Google Ads API version (informational; the code is bound to the v24 import package). */
    public String apiVersion() {
        return this.apiVersion;
    }

    // --- discovery ------------------------------------------------------------------------------

    /** Accessible customers under the MCC ({@code CustomerService.listAccessibleCustomers}); resource names. */
    public List<String> listAccessibleCustomers(Token t) {
        GoogleAdsClient client = client(t);
        try (CustomerServiceClient svc = version(client).createCustomerServiceClient()) {
            return new ArrayList<>(
                    svc.listAccessibleCustomers(ListAccessibleCustomersRequest.newBuilder().build())
                            .getResourceNamesList());
        } catch (GenericException ge) {
            throw ge;
        } catch (Exception e) {
            return fail(e);
        }
    }

    /** Geo-target-constant suggestions for a free-text query. */
    public List<GeoTargetConstantSuggestion> suggestGeoTargets(Token t, String query, String locale) {
        GoogleAdsClient client = client(t);
        try (GeoTargetConstantServiceClient svc = version(client).createGeoTargetConstantServiceClient()) {
            SuggestGeoTargetConstantsRequest.Builder req = SuggestGeoTargetConstantsRequest.newBuilder()
                    .setLocationNames(SuggestGeoTargetConstantsRequest.LocationNames.newBuilder().addNames(query));
            if (locale != null && !locale.isBlank())
                req.setLocale(locale);
            return new ArrayList<>(svc.suggestGeoTargetConstants(req.build()).getGeoTargetConstantSuggestionsList());
        } catch (GenericException ge) {
            throw ge;
        } catch (Exception e) {
            return fail(e);
        }
    }

    /** Keyword ideas from seed terms and/or a seed URL ({@code KeywordPlanIdeaService}). */
    public List<GenerateKeywordIdeaResult> generateKeywordIdeas(Token t, String customerId, List<String> seeds,
            String url) {
        GoogleAdsClient client = client(t);
        try (KeywordPlanIdeaServiceClient svc = version(client).createKeywordPlanIdeaServiceClient()) {
            GenerateKeywordIdeasRequest.Builder req = GenerateKeywordIdeasRequest.newBuilder().setCustomerId(customerId);
            boolean hasSeeds = seeds != null && !seeds.isEmpty();
            boolean hasUrl = url != null && !url.isBlank();
            if (hasSeeds && hasUrl) {
                KeywordAndUrlSeed.Builder seed = KeywordAndUrlSeed.newBuilder().setUrl(url);
                seeds.forEach(seed::addKeywords);
                req.setKeywordAndUrlSeed(seed);
            } else if (hasUrl) {
                req.setKeywordAndUrlSeed(KeywordAndUrlSeed.newBuilder().setUrl(url));
            } else if (hasSeeds) {
                KeywordSeed.Builder seed = KeywordSeed.newBuilder();
                seeds.forEach(seed::addKeywords);
                req.setKeywordSeed(seed);
            }
            List<GenerateKeywordIdeaResult> out = new ArrayList<>();
            svc.generateKeywordIdeas(req.build()).iterateAll().forEach(out::add);
            return out;
        } catch (GenericException ge) {
            throw ge;
        } catch (Exception e) {
            return fail(e);
        }
    }

    // --- read + mutate --------------------------------------------------------------------------

    /** A GAQL read against the operating customer; returns the raw rows for the caller to parse. */
    public List<GoogleAdsRow> search(Token t, String customerId, String gaql) {
        GoogleAdsClient client = client(t);
        try (GoogleAdsServiceClient svc = version(client).createGoogleAdsServiceClient()) {
            List<GoogleAdsRow> rows = new ArrayList<>();
            svc.search(customerId, gaql).iterateAll().forEach(rows::add);
            return rows;
        } catch (GenericException ge) {
            throw ge;
        } catch (Exception e) {
            return fail(e);
        }
    }

    /**
     * An <b>atomic</b> mutate ({@code GoogleAdsService.mutate}, partial-failure off) so a launch either
     * lands entirely or leaves nothing — the design's guard against partial ACTIVE objects.
     */
    public MutateGoogleAdsResponse mutate(Token t, String customerId, List<MutateOperation> operations) {
        GoogleAdsClient client = client(t);
        try (GoogleAdsServiceClient svc = version(client).createGoogleAdsServiceClient()) {
            return svc.mutate(customerId, operations);
        } catch (GenericException ge) {
            throw ge;
        } catch (Exception e) {
            return fail(e);
        }
    }

    // --- client construction --------------------------------------------------------------------

    private GoogleAdsClient client(Token t) {
        String accessToken = GoogleTokens.requireAccessToken(t, this.msg);
        long loginCustomerId = GoogleTokens.requireLoginCustomerId(t, this.msg);
        if (this.developerToken == null || this.developerToken.isBlank())
            this.msg.throwMessage(m -> new GenericException(HttpStatus.BAD_REQUEST, m),
                    AdzumpMessageResourceService.FIELDS_MISSING, "adzump.google.developer-token");

        Credentials credentials = GoogleCredentials.create(new AccessToken(accessToken, null));
        return GoogleAdsClient.newBuilder()
                .setDeveloperToken(this.developerToken)
                .setLoginCustomerId(loginCustomerId)
                .setCredentials(credentials)
                .build();
    }

    /** The pinned versioned service factory; the one place the v24 binding is chosen. */
    private GoogleAdsVersion version(GoogleAdsClient client) {
        return client.getVersion24();
    }

    private <T> T fail(Exception e) {
        return this.msg.throwMessage(m -> new GenericException(HttpStatus.BAD_GATEWAY, m, e),
                AdzumpMessageResourceService.GOOGLE_API_ERROR, e.getMessage());
    }
}
