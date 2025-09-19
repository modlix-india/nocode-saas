package com.fincity.saas.ui.service;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.MobileApp;
import com.fincity.saas.ui.model.MobileAppStatusUpdateRequest;
import com.fincity.saas.ui.repository.MobileAppRepository;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Service
public class MobileAppService {

    private final UIMessageResourceService uiMessageResourceService;

    private final MobileAppRepository repo;

    public MobileAppService(MobileAppRepository repo, UIMessageResourceService uiMessageResourceService) {
        this.repo = repo;
        this.uiMessageResourceService = uiMessageResourceService;
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
        String alias = "modlix";
        String storePass = new BigInteger(130, new SecureRandom()).toString(32);

        // Generate keypair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        // Self-signed cert (valid ~25 years)
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 86400000L);
        Date notAfter = new Date(now + 10000L * 24 * 60 * 60 * 1000); // 10000 days
        X500Name subject = new X500Name("CN=Modlix Team, OU=Mobile Development, O=Modlix, L=Karnataka, ST=Karnataka, C=IN");
        BigInteger serial = new BigInteger(64, new SecureRandom());
        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter, subject, kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        cert.checkValidity(new Date());
        cert.verify(kp.getPublic());

        // Store into JKS
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        ks.setKeyEntry(alias, kp.getPrivate(), storePass.toCharArray(), new java.security.cert.Certificate[]{cert});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ks.store(out, storePass.toCharArray());

        return new KeystoreBundle(Base64.getEncoder().encodeToString(out.toByteArray()), storePass, storePass, alias);
    }

    public Mono<MobileApp> update(MobileApp mobileApp) {
        if (mobileApp.getId() == null) {
            try {
                KeystoreBundle bundle = createKeystore();
                mobileApp.setAndroidKeystore(bundle.jksBase64);
                mobileApp.setAndroidStorePass(bundle.storePass);
                mobileApp.setAndroidKeyPass(bundle.keyPass);
                mobileApp.setAndroidAlias(bundle.alias);
            } catch (Exception ex) {
                return this.uiMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg, ex), UIMessageResourceService.MOBILE_APP_UNABLE_TO_GEN_KEYSTORE);
            }
            return this.repo.save(mobileApp);
        }

        return this.repo.findById(mobileApp.getId())
                .flatMap(existing -> {
                    if (existing.getAndroidKeystore() == null) {
                        try {
                            KeystoreBundle bundle = createKeystore();
                            mobileApp.setAndroidKeystore(bundle.jksBase64);
                            mobileApp.setAndroidStorePass(bundle.storePass);
                            mobileApp.setAndroidKeyPass(bundle.keyPass);
                            mobileApp.setAndroidAlias(bundle.alias);
                        } catch (Exception ex) {
                            return this.uiMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg, ex), UIMessageResourceService.MOBILE_APP_UNABLE_TO_GEN_KEYSTORE);
                        }
                    } else {
                        mobileApp.setAndroidKeystore(existing.getAndroidKeystore());
                        mobileApp.setAndroidStorePass(existing.getAndroidStorePass());
                        mobileApp.setAndroidKeyPass(existing.getAndroidKeyPass());
                        mobileApp.setAndroidAlias(existing.getAndroidAlias());
                    }
                    mobileApp.getDetails().setVersion(mobileApp.getId() == null ? 0 : existing.getDetails().getVersion() + 1);
                    return this.repo.save(mobileApp);
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
