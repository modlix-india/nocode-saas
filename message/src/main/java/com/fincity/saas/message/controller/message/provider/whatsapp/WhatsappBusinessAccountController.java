package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.message.controller.base.BaseUpdatableController;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappBusinessAccountDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappBusinessAccount;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappBusinessAccountsRecord;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappBusinessAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * WhatsApp configuration, restricted to tenant owners.
 *
 * <p>Annotated on the controller rather than the service, which is the usual place in this
 * codebase, because these services are shared with the inbound webhook path. That path runs
 * with no user at all, so a class-level rule on the service would gate HTTP access and break
 * message delivery at the same time. The controller is the boundary that only humans cross.
 *
 * <p>Annotated per method, never on the class. A class-level rule applies to every public method
 * including the inherited {@code @InitBinder}, and reactive method security only supports methods
 * returning a {@code Publisher}, so a {@code void} binder callback makes every request to this
 * controller fail with a 500 before it is even routed. It fails for owners too, so it is not the
 * kind of mistake that shows up only in an access test.
 *
 * <p>What that leaves open is deliberate rather than incidental. The declared methods here are all
 * administrative writes and carry the gate; the generic read endpoints inherited from
 * {@code BaseUpdatableController} do not, because the deal profile calls them as an ordinary sales
 * agent to list approved templates and business numbers in order to send a message. Reading the
 * templates you are allowed to send is deal work, not settings administration. Those reads are
 * closed by moving them behind entity-processor and blocking {@code /api/message/**} at nginx, not
 * by an authority a salesperson will never hold.
 */
@RestController
@RequestMapping("/api/message/whatsapp/accounts/business")
public class WhatsappBusinessAccountController
        extends BaseUpdatableController<
                MessageWhatsappBusinessAccountsRecord,
                WhatsappBusinessAccount,
                WhatsappBusinessAccountDAO,
                WhatsappBusinessAccountService> {

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PostMapping("/sync")
    public Mono<ResponseEntity<WhatsappBusinessAccount>> syncBusinessAccount(
            @RequestParam final String connectionName) {
        return this.service.syncBusinessAccount(connectionName).map(ResponseEntity::ok);
    }

    /**
     * Subscribes our Meta app to this business account, which is what makes its events start
     * arriving. Surfaced in the UI as "Connect events".
     *
     * <p>The path still says {@code webhook/override} while the behaviour no longer overrides
     * anything. Kept so the settings page keeps working; the name is the thing to correct, not the
     * route, and doing both at once would mean a page change for no user-visible gain.
     */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @PostMapping("/webhook/override/{id}")
    public Mono<ResponseEntity<WhatsappBusinessAccount>> subscribeApp(
            @PathVariable(PATH_VARIABLE_ID) final Identity identity, @RequestParam final String connectionName) {
        return this.service.subscribeApp(connectionName, identity).map(ResponseEntity::ok);
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    @GetMapping("/fb" + PATH_ID)
    public Mono<ResponseEntity<WhatsappBusinessAccount>> getBusinessAccount(@PathVariable final String id) {
        return this.service.getBusinessAccount(id).map(ResponseEntity::ok);
    }
}
