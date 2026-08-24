package com.fincity.saas.entity.processor.dao.message;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_MESSAGE_TEMPLATES;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.message.MessageTemplate;
import com.fincity.saas.entity.processor.enums.message.MessageTemplateChannel;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorMessageTemplatesRecord;
import java.util.List;
import org.jooq.Record1;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class MessageTemplateDAO extends BaseUpdatableDAO<EntityProcessorMessageTemplatesRecord, MessageTemplate> {

    protected MessageTemplateDAO() {
        super(MessageTemplate.class, ENTITY_PROCESSOR_MESSAGE_TEMPLATES, ENTITY_PROCESSOR_MESSAGE_TEMPLATES.ID);
    }

    /** The library for one channel, which is what the picker in a rule popup lists. */
    public Mono<List<MessageTemplate>> readByChannel(String appCode, String clientCode, MessageTemplateChannel channel) {

        return Flux.from(this.dslContext
                        .selectFrom(ENTITY_PROCESSOR_MESSAGE_TEMPLATES)
                        .where(ENTITY_PROCESSOR_MESSAGE_TEMPLATES.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_MESSAGE_TEMPLATES.CLIENT_CODE.eq(clientCode))
                        .and(channel == null
                                ? org.jooq.impl.DSL.trueCondition()
                                : ENTITY_PROCESSOR_MESSAGE_TEMPLATES.CHANNEL.eq(channel))
                        .and(this.isActiveTrue())
                        .orderBy(ENTITY_PROCESSOR_MESSAGE_TEMPLATES.NAME.asc()))
                .map(rec -> rec.into(MessageTemplate.class))
                .collectList();
    }

    /**
     * Whether a name is already taken in this tenant.
     *
     * <p>Checked in the service rather than left to the unique key, so the editor can say which name
     * clashed. A rule references a message by name in the UI, and two called "Welcome" is how the
     * wrong one ends up attached to a stage.
     */
    public Mono<Boolean> nameExists(String appCode, String clientCode, String name, org.jooq.types.ULong excludeId) {

        return Mono.from(this.dslContext
                        .selectCount()
                        .from(ENTITY_PROCESSOR_MESSAGE_TEMPLATES)
                        .where(ENTITY_PROCESSOR_MESSAGE_TEMPLATES.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_MESSAGE_TEMPLATES.CLIENT_CODE.eq(clientCode))
                        .and(ENTITY_PROCESSOR_MESSAGE_TEMPLATES.NAME.eq(name))
                        .and(excludeId == null
                                ? org.jooq.impl.DSL.trueCondition()
                                : ENTITY_PROCESSOR_MESSAGE_TEMPLATES.ID.ne(excludeId)))
                .map(Record1::value1)
                .map(count -> count > 0)
                .defaultIfEmpty(Boolean.FALSE);
    }
}
