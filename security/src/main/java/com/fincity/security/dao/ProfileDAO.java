package com.fincity.security.dao;

import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dto.Profile;
import com.fincity.security.dto.ProfileArrangement;
import com.fincity.security.jooq.tables.SecurityProfile;
import com.fincity.security.jooq.tables.SecurityProfileArrangement;
import com.fincity.security.jooq.tables.SecurityV2Role;
import com.fincity.security.jooq.tables.records.SecurityProfileRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
public class ProfileDAO extends AbstractClientCheckDAO<SecurityProfileRecord, ULong, Profile> {

    public ProfileDAO() {
        super(Profile.class, SecurityProfile.SECURITY_PROFILE, SecurityProfile.SECURITY_PROFILE.CLIENT_ID);
    }

    @Override
    public Field<ULong> getClientIDField() {
        return SecurityProfile.SECURITY_PROFILE.CLIENT_ID;
    }

    @Override
    public Mono<Profile> readById(ULong profileId) {
        return FlatMapUtil.flatMapMono(
                () -> super.readById(profileId),
                (profile) -> {
                    if (profile.getRootProfileId() == null) {
                        return Mono.just(profile);
                    }
                    return this.readById(profile.getRootProfileId());
                },
                (profile, rootProfile) -> {
                    if (rootProfile.getId().equals(profile.getId())) {
                        return this.fillProfile(profile);
                    }

                    return this.fillProfileWithRootProfile(profile, rootProfile);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDAO.readById"));
    }

    private Mono<Profile> fillProfile(Profile profile) {
        return FlatMapUtil.flatMapMono(
                () -> Flux.from(this.dslContext.select(
                        SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ID,
                        SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.PROFILE_ID,
                        SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ROLE_ID,
                        DSL.coalesce(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.NAME,
                                SecurityV2Role.SECURITY_V2_ROLE.NAME).as("NAME"),
                        DSL.coalesce(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.DESCRIPTION,
                                SecurityV2Role.SECURITY_V2_ROLE.DESCRIPTION).as("DESCRIPTION"),
                        DSL.coalesce(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.SHORT_NAME,
                                SecurityV2Role.SECURITY_V2_ROLE.SHORT_NAME).as("SHORT_NAME"),
                        SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ASSIGNABLE,
                        SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ORDER)
                        .from(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT)
                        .leftJoin(SecurityV2Role.SECURITY_V2_ROLE)
                        .on(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ROLE_ID
                                .eq(SecurityV2Role.SECURITY_V2_ROLE.ID))
                        .where(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.PROFILE_ID.eq(profile.getId()))
                        .orderBy(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ORDER.asc()))
                        .map(r -> r.into(ProfileArrangement.class))
                        .collectList(),

                (arrangements) -> {
                    return Mono.just(profile);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDAO.fillProfile"));
    }

    private Mono<Profile> fillProfileWithRootProfile(Profile profile, Profile rootProfile) {
        return Mono.just(profile);
    }
}