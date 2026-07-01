CREATE TYPE payment_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'APPROVED',
    'FAILED',
    'CANCELLED'
);

CREATE TABLE payments (
                          id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          payer_id    VARCHAR(100) NOT NULL,
                          payee_id    VARCHAR(100) NOT NULL,
                          amount      NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
                          currency    VARCHAR(3) NOT NULL DEFAULT 'BRL',
                          status      payment_status NOT NULL DEFAULT 'PENDING',
                          description VARCHAR(255),
                          created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                          updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_payer   ON payments(payer_id);
CREATE INDEX idx_payments_status  ON payments(status);
CREATE INDEX idx_payments_created ON payments(created_at DESC);