package com.fincity.security.dao.wallet;

import static com.fincity.security.jooq.tables.SecurityWalletBudget.SECURITY_WALLET_BUDGET;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.wallet.WalletBudget;
import com.fincity.security.jooq.tables.records.SecurityWalletBudgetRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class WalletBudgetDAO extends AbstractUpdatableDAO<SecurityWalletBudgetRecord, ULong, WalletBudget> {

    public WalletBudgetDAO() {
        super(WalletBudget.class, SECURITY_WALLET_BUDGET, SECURITY_WALLET_BUDGET.ID);
    }

    public Mono<WalletBudget> findByWalletAndApp(ULong walletId, ULong appId) {
        return Mono.from(this.dslContext.selectFrom(SECURITY_WALLET_BUDGET)
                .where(SECURITY_WALLET_BUDGET.WALLET_ID.eq(walletId)
                        .and(SECURITY_WALLET_BUDGET.APP_ID.eq(appId))))
                .map(r -> r.into(WalletBudget.class));
    }

    public Mono<List<WalletBudget>> findByWallet(ULong walletId) {
        return Flux.from(this.dslContext.selectFrom(SECURITY_WALLET_BUDGET)
                .where(SECURITY_WALLET_BUDGET.WALLET_ID.eq(walletId)))
                .map(r -> r.into(WalletBudget.class))
                .collectList();
    }
}
