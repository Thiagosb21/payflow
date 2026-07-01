CREATE TABLE payment_events (
                                id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                payment_id  UUID NOT NULL REFERENCES payments(id),
                                event_type  VARCHAR(50) NOT NULL,
                                old_status  payment_status,
                                new_status  payment_status NOT NULL,
                                metadata    TEXT,
                                occurred_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_events_payment_id ON payment_events(payment_id);