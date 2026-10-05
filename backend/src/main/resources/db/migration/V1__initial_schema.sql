CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- ============================================================
-- USERS
-- ============================================================

CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                       email VARCHAR(320) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,

                       created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
-- USER PROFILES
-- ============================================================

CREATE TABLE user_profiles (
                               user_id UUID PRIMARY KEY,

                               date_of_birth DATE,
                               country_code CHAR(2),
                               base_currency CHAR(3) NOT NULL DEFAULT 'EUR',

                               employment_status VARCHAR(50),

                               monthly_income NUMERIC(20, 2),
                               monthly_expenses NUMERIC(20, 2),
                               monthly_savings NUMERIC(20, 2),

                               financial_goal VARCHAR(100),

                               created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_user_profiles_user
                                   FOREIGN KEY (user_id)
                                       REFERENCES users(id)
                                       ON DELETE CASCADE,

                               CONSTRAINT chk_monthly_income_positive
                                   CHECK (monthly_income IS NULL OR monthly_income >= 0),

                               CONSTRAINT chk_monthly_expenses_positive
                                   CHECK (monthly_expenses IS NULL OR monthly_expenses >= 0),

                               CONSTRAINT chk_monthly_savings_positive
                                   CHECK (monthly_savings IS NULL OR monthly_savings >= 0)
);


-- ============================================================
-- USER PREFERENCES
-- ============================================================

CREATE TABLE user_preferences (
                                  user_id UUID PRIMARY KEY,

                                  language VARCHAR(10) NOT NULL DEFAULT 'es',
                                  currency CHAR(3) NOT NULL DEFAULT 'EUR',

                                  theme VARCHAR(20) NOT NULL DEFAULT 'system',

                                  show_net_worth BOOLEAN NOT NULL DEFAULT TRUE,

                                  show_comparisons BOOLEAN NOT NULL DEFAULT TRUE,

                                  analytics_consent BOOLEAN NOT NULL DEFAULT FALSE,

                                  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  CONSTRAINT fk_user_preferences_user
                                      FOREIGN KEY (user_id)
                                          REFERENCES users(id)
                                          ON DELETE CASCADE
);


-- ============================================================
-- FINANCIAL INSTITUTIONS
-- ============================================================

CREATE TABLE institutions (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                              name VARCHAR(150) NOT NULL,
                              type VARCHAR(50) NOT NULL,
                              country_code CHAR(2),

                              logo_url VARCHAR(500),

                              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
-- ACCOUNTS
-- ============================================================

CREATE TABLE accounts (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                          user_id UUID NOT NULL,
                          institution_id UUID,

                          name VARCHAR(150) NOT NULL,

                          account_type VARCHAR(50) NOT NULL,

                          currency CHAR(3) NOT NULL DEFAULT 'EUR',

                          current_balance NUMERIC(20, 8),

                          is_active BOOLEAN NOT NULL DEFAULT TRUE,

                          created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT fk_accounts_user
                              FOREIGN KEY (user_id)
                                  REFERENCES users(id)
                                  ON DELETE CASCADE,

                          CONSTRAINT fk_accounts_institution
                              FOREIGN KEY (institution_id)
                                  REFERENCES institutions(id)
                                  ON DELETE SET NULL
);


CREATE INDEX idx_accounts_user_id
    ON accounts(user_id);

CREATE INDEX idx_accounts_institution_id
    ON accounts(institution_id);


-- ============================================================
-- ASSETS
-- ============================================================

CREATE TABLE assets (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                        symbol VARCHAR(50),
                        name VARCHAR(200) NOT NULL,

                        asset_type VARCHAR(50) NOT NULL,

                        isin VARCHAR(20),
                        cusip VARCHAR(20),

                        currency CHAR(3),

                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE INDEX idx_assets_symbol
    ON assets(symbol);

CREATE INDEX idx_assets_isin
    ON assets(isin);


-- ============================================================
-- HOLDINGS
-- ============================================================

CREATE TABLE holdings (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                          user_id UUID NOT NULL,
                          account_id UUID NOT NULL,
                          asset_id UUID NOT NULL,

                          quantity NUMERIC(30, 12) NOT NULL DEFAULT 0,

                          average_cost NUMERIC(30, 12),

                          created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT fk_holdings_user
                              FOREIGN KEY (user_id)
                                  REFERENCES users(id)
                                  ON DELETE CASCADE,

                          CONSTRAINT fk_holdings_account
                              FOREIGN KEY (account_id)
                                  REFERENCES accounts(id)
                                  ON DELETE CASCADE,

                          CONSTRAINT fk_holdings_asset
                              FOREIGN KEY (asset_id)
                                  REFERENCES assets(id)
                                  ON DELETE RESTRICT,

                          CONSTRAINT chk_holdings_quantity
                              CHECK (quantity >= 0),

                          CONSTRAINT uq_holdings_account_asset
                              UNIQUE (account_id, asset_id)
);


CREATE INDEX idx_holdings_user_id
    ON holdings(user_id);

CREATE INDEX idx_holdings_asset_id
    ON holdings(asset_id);


-- ============================================================
-- TRANSACTION CATEGORIES
-- ============================================================

CREATE TABLE transaction_categories (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                        user_id UUID,

                                        name VARCHAR(100) NOT NULL,

                                        type VARCHAR(50) NOT NULL,

                                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                        CONSTRAINT fk_transaction_categories_user
                                            FOREIGN KEY (user_id)
                                                REFERENCES users(id)
                                                ON DELETE CASCADE
);


CREATE INDEX idx_transaction_categories_user_id
    ON transaction_categories(user_id);


-- ============================================================
-- TRANSACTIONS
-- ============================================================

CREATE TABLE transactions (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                              user_id UUID NOT NULL,
                              account_id UUID NOT NULL,

                              asset_id UUID,
                              category_id UUID,

                              document_id UUID,

                              type VARCHAR(50) NOT NULL,

                              amount NUMERIC(30, 8) NOT NULL,
                              currency CHAR(3) NOT NULL,

                              quantity NUMERIC(30, 12),
                              price NUMERIC(30, 12),

                              transaction_date TIMESTAMPTZ NOT NULL,

                              description TEXT,

                              source VARCHAR(50),

                              transfer_reference VARCHAR(100),

                              external_id VARCHAR(255),

                              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT fk_transactions_user
                                  FOREIGN KEY (user_id)
                                      REFERENCES users(id)
                                      ON DELETE CASCADE,

                              CONSTRAINT fk_transactions_account
                                  FOREIGN KEY (account_id)
                                      REFERENCES accounts(id)
                                      ON DELETE CASCADE,

                              CONSTRAINT fk_transactions_asset
                                  FOREIGN KEY (asset_id)
                                      REFERENCES assets(id)
                                      ON DELETE RESTRICT,

                              CONSTRAINT fk_transactions_category
                                  FOREIGN KEY (category_id)
                                      REFERENCES transaction_categories(id)
                                      ON DELETE SET NULL,

                              CONSTRAINT chk_transaction_quantity
                                  CHECK (quantity IS NULL OR quantity >= 0),

                              CONSTRAINT chk_transaction_price
                                  CHECK (price IS NULL OR price >= 0)
);


CREATE INDEX idx_transactions_user_id
    ON transactions(user_id);

CREATE INDEX idx_transactions_account_id
    ON transactions(account_id);

CREATE INDEX idx_transactions_asset_id
    ON transactions(asset_id);

CREATE INDEX idx_transactions_category_id
    ON transactions(category_id);

CREATE INDEX idx_transactions_date
    ON transactions(transaction_date);

CREATE INDEX idx_transactions_external_id
    ON transactions(external_id);


-- ============================================================
-- DOCUMENTS
-- ============================================================

CREATE TABLE documents (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                           user_id UUID NOT NULL,
                           account_id UUID,

                           filename VARCHAR(500) NOT NULL,

                           storage_path VARCHAR(1000) NOT NULL,

                           document_type VARCHAR(50) NOT NULL,

                           status VARCHAR(50) NOT NULL DEFAULT 'UPLOADED',

                           uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           processed_at TIMESTAMPTZ,

                           CONSTRAINT fk_documents_user
                               FOREIGN KEY (user_id)
                                   REFERENCES users(id)
                                   ON DELETE CASCADE,

                           CONSTRAINT fk_documents_account
                               FOREIGN KEY (account_id)
                                   REFERENCES accounts(id)
                                   ON DELETE SET NULL
);


CREATE INDEX idx_documents_user_id
    ON documents(user_id);

CREATE INDEX idx_documents_account_id
    ON documents(account_id);


-- ============================================================
-- MARKET PRICES
-- ============================================================

CREATE TABLE market_prices (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                               asset_id UUID NOT NULL,

                               timestamp TIMESTAMPTZ NOT NULL,

                               price NUMERIC(30, 12) NOT NULL,

                               currency CHAR(3) NOT NULL,

                               source VARCHAR(100),

                               created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_market_prices_asset
                                   FOREIGN KEY (asset_id)
                                       REFERENCES assets(id)
                                       ON DELETE CASCADE,

                               CONSTRAINT chk_market_prices_price
                                   CHECK (price >= 0)
);


CREATE INDEX idx_market_prices_asset_timestamp
    ON market_prices(asset_id, timestamp DESC);


-- ============================================================
-- PORTFOLIO SNAPSHOTS
-- ============================================================

CREATE TABLE portfolio_snapshots (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                     user_id UUID NOT NULL,

                                     timestamp TIMESTAMPTZ NOT NULL,

                                     total_value NUMERIC(30, 8) NOT NULL,

                                     cash_value NUMERIC(30, 8) NOT NULL DEFAULT 0,

                                     investment_value NUMERIC(30, 8) NOT NULL DEFAULT 0,

                                     currency CHAR(3) NOT NULL DEFAULT 'EUR',

                                     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                     CONSTRAINT fk_portfolio_snapshots_user
                                         FOREIGN KEY (user_id)
                                             REFERENCES users(id)
                                             ON DELETE CASCADE
);


CREATE INDEX idx_portfolio_snapshots_user_timestamp
    ON portfolio_snapshots(user_id, timestamp DESC);