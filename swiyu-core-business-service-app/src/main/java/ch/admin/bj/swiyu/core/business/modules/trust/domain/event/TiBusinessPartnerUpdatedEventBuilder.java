package ch.admin.bj.swiyu.core.business.modules.trust.domain.event;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventBuilder;
import ch.admin.bit.jeap.messaging.avro.AvroMessageBuilderException;
import ch.admin.bj.swiyu.messagetype.ti.BusinessPartnerUpdatedPayload;
import ch.admin.bj.swiyu.messagetype.ti.TiBusinessPartnerUpdatedEvent;
import java.util.UUID;

/**
 * Builds the {@link TiBusinessPartnerUpdatedEvent} (EID-6988): a thin change notification
 * carrying only the partner id - consumers fetch the current state themselves.
 */
public class TiBusinessPartnerUpdatedEventBuilder
    extends AvroDomainEventBuilder<TiBusinessPartnerUpdatedEventBuilder, TiBusinessPartnerUpdatedEvent>
{

    private UUID businessPartnerId;
    private boolean isIdempotenceIdOverwritten;

    private TiBusinessPartnerUpdatedEventBuilder() {
        super(TiBusinessPartnerUpdatedEvent::new);
    }

    public static TiBusinessPartnerUpdatedEventBuilder create() {
        return new TiBusinessPartnerUpdatedEventBuilder();
    }

    public TiBusinessPartnerUpdatedEventBuilder businessPartnerId(UUID businessPartnerId) {
        this.businessPartnerId = businessPartnerId;
        return this;
    }

    @Override
    public TiBusinessPartnerUpdatedEventBuilder idempotenceId(String idempotenceId) {
        isIdempotenceIdOverwritten = true;
        return super.idempotenceId(idempotenceId);
    }

    @Override
    protected String getServiceName() {
        return EventBuilderProperties.SERVICE_NAME;
    }

    @Override
    protected String getSystemName() {
        return EventBuilderProperties.SYSTEM_NAME;
    }

    @Override
    protected TiBusinessPartnerUpdatedEventBuilder self() {
        return this;
    }

    @Override
    public TiBusinessPartnerUpdatedEvent build() {
        if (!isIdempotenceIdOverwritten) {
            super.idempotenceId(UUID.randomUUID().toString());
        }
        if (this.businessPartnerId == null) {
            throw AvroMessageBuilderException.propertyNull("businessPartnerId");
        }
        BusinessPartnerUpdatedPayload payload = BusinessPartnerUpdatedPayload.newBuilder()
            .setBusinessPartnerId(businessPartnerId)
            .build();
        setPayload(payload);
        return super.build();
    }
}
