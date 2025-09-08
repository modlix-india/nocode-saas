package com.fincity.security.dao;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dto.SSOBundle;
import com.fincity.security.jooq.tables.records.SecurityAppSsoBundleRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.fincity.security.jooq.tables.SecurityAppSsoBundle.SECURITY_APP_SSO_BUNDLE;
import static com.fincity.security.jooq.tables.SecurityBundledApp.SECURITY_BUNDLED_APP;

@Component
public class SSOBundleDAO extends AbstractUpdatableDAO<SecurityAppSsoBundleRecord, ULong, SSOBundle> {

    protected SSOBundleDAO() {
        super(SSOBundle.class, SECURITY_APP_SSO_BUNDLE, SECURITY_APP_SSO_BUNDLE.ID);
    }

    @Override
    public Mono<SSOBundle> create(SSOBundle bundle) {

        return FlatMapUtil.flatMapMono(
                () -> super.create(bundle),

                created -> this.createBundledApps(created, bundle)

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "SSOBundleDAO.create"));
    }

    @Override
    public Mono<SSOBundle> readById(ULong id) {
        return FlatMapUtil.flatMapMono(
                () -> super.readById(id),

                bundle -> Flux.from(this.dslContext.selectFrom(SECURITY_BUNDLED_APP).where(SECURITY_BUNDLED_APP.BUNDLE_ID.eq(id)))
                        .map(rec -> rec.into(SSOBundle.SSOBundledApp.class))
                        .collectList()
                        .map(bundle::setApps)
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "SSOBundleDAO.readById"));

    }

    @Override
    public <A extends AbstractUpdatableDTO<ULong, ULong>> Mono<SSOBundle> update(A entity) {

        if (!(entity instanceof SSOBundle bundle))
            return Mono.error(new IllegalArgumentException("Invalid entity type"));

        return FlatMapUtil.flatMapMono(
                () -> super.update(bundle),

                updated -> Mono.from(this.dslContext.deleteFrom(SECURITY_BUNDLED_APP).where(SECURITY_BUNDLED_APP.BUNDLE_ID.eq(bundle.getId()))),

                (updated, deleted) -> this.createBundledApps(updated, bundle)

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "SSOBundleDAO.update"));
    }

    public Mono<SSOBundle> createBundledApps(SSOBundle newOne, SSOBundle bundle) {
        return FlatMapUtil.flatMapMono(
                () -> {
                    if (bundle.getApps() == null || bundle.getApps().isEmpty()) return Mono.just(List.<ULong>of());

                    return Flux.fromIterable(bundle.getApps())
                            .flatMap(e -> Mono.from(this.dslContext.insertInto(SECURITY_BUNDLED_APP,
                                    SECURITY_BUNDLED_APP.BUNDLE_ID,
                                    SECURITY_BUNDLED_APP.CREATED_BY,
                                    SECURITY_BUNDLED_APP.APP_CODE,
                                    SECURITY_BUNDLED_APP.APP_URL_ID
                            ).values(newOne.getId(), bundle.getCreatedBy(), e.getAppCode(), e.getAppUrlId()).returning(SECURITY_BUNDLED_APP.ID)))
                            .map(rec -> rec.get(SECURITY_BUNDLED_APP.ID))
                            .collectList();
                },

                appListIds -> Flux.from(this.dslContext.selectFrom(SECURITY_BUNDLED_APP).where(SECURITY_BUNDLED_APP.ID.in(appListIds)))
                        .map(rec -> rec.into(SSOBundle.SSOBundledApp.class))
                        .collectList().map(newOne::setApps)
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "SSOBundleDAO.createBundledApps"));
    }

    public Mono<ArrayList<SSOBundle>> readByClientCodeAppcode(String clientCode, String appCode) {

        return FlatMapUtil.flatMapMono(
                () -> Flux.from(this.dslContext.select(SECURITY_APP_SSO_BUNDLE.ID, SECURITY_APP_SSO_BUNDLE.BUNDLE_NAME, SECURITY_APP_SSO_BUNDLE.CLIENT_CODE)
                        .from(SECURITY_BUNDLED_APP)
                        .leftJoin(SECURITY_APP_SSO_BUNDLE).on(SECURITY_APP_SSO_BUNDLE.ID.eq(SECURITY_BUNDLED_APP.BUNDLE_ID))
                        .where(SECURITY_APP_SSO_BUNDLE.CLIENT_CODE.eq(clientCode).and(SECURITY_BUNDLED_APP.APP_CODE.eq(appCode)))
                ).map(rec -> rec.into(SSOBundle.class)).collectMap(SSOBundle::getId, Function.identity()),

                bundles -> Flux.from(this.dslContext.selectFrom(SECURITY_BUNDLED_APP).where(SECURITY_BUNDLED_APP.BUNDLE_ID.in(bundles.keySet())))
                        .map(rec -> rec.into(SSOBundle.SSOBundledApp.class)).collectMultimap(SSOBundle.SSOBundledApp::getBundleId)
                        .map(bundledApps -> {

                            for (Map.Entry<ULong, Collection<SSOBundle.SSOBundledApp>> entry : bundledApps.entrySet()) {
                                List<SSOBundle.SSOBundledApp> list = new ArrayList<>(entry.getValue());
                                if (!bundles.containsKey(list.getFirst().getBundleId())) continue;
                                bundles.get(list.getFirst().getBundleId()).setApps(list);
                            }

                            return new ArrayList<>(bundles.values());
                        })
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "SSOBundleDAO.readByClientCodeAppcode"));
    }
}
