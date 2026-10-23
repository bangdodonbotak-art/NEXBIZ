-- =================================================================================
-- NEXBIZ MULTI-TENANT OFFLINE-FIRST POS — POSTGRESQL 16+ BACKEND SCHEMA DDL
-- All monetary values are stored strictly as BIGINT (Whole IDR Rupiah).
-- All entity primary keys are UUID; all timestamps use BIGINT UTC Epoch Milliseconds.
-- Multi-tenancy enforced via indexed `business_id` + PostgreSQL Row-Level Security.
-- =================================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. TENANTS / BUSINESSES
CREATE TABLE IF NOT EXISTS businesses (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL UNIQUE,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    legal_name VARCHAR(200),
    tax_id_npwp VARCHAR(32),
    address TEXT NOT NULL,
    phone VARCHAR(32) NOT NULL,
    default_tax_rate_bps INTEGER NOT NULL DEFAULT 1100 CHECK (default_tax_rate_bps >= 0),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'IDR',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_businesses_business_id ON businesses(business_id);

-- 2. USER MEMBERSHIPS (RBAC PER TENANT)
CREATE TABLE IF NOT EXISTS user_memberships (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'CASHIER', 'INVENTORY_CLERK', 'ACCOUNTANT')),
    pin_hash VARCHAR(128) NOT NULL,
    max_discount_rp BIGINT NOT NULL DEFAULT 0 CHECK (max_discount_rp >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE (business_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_memberships_business ON user_memberships(business_id);

-- 3. CATEGORIES
CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    color_hex VARCHAR(9) NOT NULL DEFAULT '#059669',
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE (business_id, name)
);

CREATE INDEX IF NOT EXISTS idx_categories_business ON categories(business_id);

-- 4. PRODUCTS & VARIANTS
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    unit_name VARCHAR(24) NOT NULL DEFAULT 'pcs',
    base_price_rp BIGINT NOT NULL CHECK (base_price_rp >= 0),
    base_cost_rp BIGINT NOT NULL CHECK (base_cost_rp >= 0),
    is_taxable BOOLEAN NOT NULL DEFAULT TRUE,
    track_inventory BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE (business_id, sku)
);

CREATE INDEX IF NOT EXISTS idx_products_business ON products(business_id);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(business_id, category_id);

CREATE TABLE IF NOT EXISTS product_variants (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    variant_name VARCHAR(120) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    barcode VARCHAR(64),
    selling_price_rp BIGINT NOT NULL CHECK (selling_price_rp >= 0),
    average_cost_rp BIGINT NOT NULL CHECK (average_cost_rp >= 0),
    min_stock_threshold BIGINT NOT NULL DEFAULT 5,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE (business_id, sku)
);

CREATE INDEX IF NOT EXISTS idx_variants_business ON product_variants(business_id);
CREATE INDEX IF NOT EXISTS idx_variants_barcode ON product_variants(business_id, barcode);

-- 5. INVENTORY BALANCES & APPEND-ONLY MOVEMENTS
CREATE TABLE IF NOT EXISTS inventory_balances (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    server_confirmed_stock BIGINT NOT NULL DEFAULT 0,
    reserved_stock BIGINT NOT NULL DEFAULT 0,
    valuation_cost_rp BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 1,
    updated_at BIGINT NOT NULL,
    UNIQUE (business_id, variant_id)
);

CREATE INDEX IF NOT EXISTS idx_inventory_balances_business ON inventory_balances(business_id);

CREATE TABLE IF NOT EXISTS inventory_movements (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    movement_type VARCHAR(32) NOT NULL,
    quantity_delta BIGINT NOT NULL,
    stock_before BIGINT NOT NULL,
    stock_after BIGINT NOT NULL,
    unit_cost_rp BIGINT NOT NULL CHECK (unit_cost_rp >= 0),
    reference_id VARCHAR(64),
    reference_type VARCHAR(32),
    notes TEXT,
    created_by_user_id UUID NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_inv_movements_business_created ON inventory_movements(business_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_inv_movements_variant ON inventory_movements(business_id, variant_id);

-- 6. SALES, SALE ITEMS, AND SPLIT PAYMENTS
CREATE TABLE IF NOT EXISTS sales (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    receipt_number VARCHAR(64) NOT NULL,
    cashier_user_id UUID NOT NULL,
    customer_name VARCHAR(160),
    subtotal_rp BIGINT NOT NULL CHECK (subtotal_rp >= 0),
    discount_total_rp BIGINT NOT NULL DEFAULT 0 CHECK (discount_total_rp >= 0),
    tax_total_rp BIGINT NOT NULL DEFAULT 0 CHECK (tax_total_rp >= 0),
    grand_total_rp BIGINT NOT NULL CHECK (grand_total_rp >= 0),
    total_paid_rp BIGINT NOT NULL CHECK (total_paid_rp >= 0),
    change_due_rp BIGINT NOT NULL DEFAULT 0 CHECK (change_due_rp >= 0),
    total_cogs_rp BIGINT NOT NULL DEFAULT 0 CHECK (total_cogs_rp >= 0),
    payment_method VARCHAR(32) NOT NULL,
    is_voided BOOLEAN NOT NULL DEFAULT FALSE,
    void_reason TEXT,
    voided_at BIGINT,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE (business_id, receipt_number)
);

CREATE INDEX IF NOT EXISTS idx_sales_business_created ON sales(business_id, created_at DESC);

CREATE TABLE IF NOT EXISTS sale_items (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    sale_id UUID NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    product_name_snapshot VARCHAR(160) NOT NULL,
    variant_name_snapshot VARCHAR(120) NOT NULL,
    sku_snapshot VARCHAR(64) NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    unit_price_rp BIGINT NOT NULL CHECK (unit_price_rp >= 0),
    unit_cost_rp BIGINT NOT NULL CHECK (unit_cost_rp >= 0),
    discount_rp BIGINT NOT NULL DEFAULT 0 CHECK (discount_rp >= 0),
    tax_rp BIGINT NOT NULL DEFAULT 0 CHECK (tax_rp >= 0),
    line_total_rp BIGINT NOT NULL CHECK (line_total_rp >= 0),
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sale_items_sale ON sale_items(business_id, sale_id);

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    sale_id UUID NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    payment_method VARCHAR(32) NOT NULL,
    amount_rp BIGINT NOT NULL CHECK (amount_rp >= 0),
    tendered_rp BIGINT NOT NULL CHECK (tendered_rp >= 0),
    change_rp BIGINT NOT NULL DEFAULT 0 CHECK (change_rp >= 0),
    reference_code VARCHAR(128),
    paid_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_payments_sale ON payments(business_id, sale_id);

-- 7. CHART OF ACCOUNTS (SAK EMKM) & DOUBLE-ENTRY JOURNAL LEDGER
CREATE TABLE IF NOT EXISTS accounts (
    id VARCHAR(96) PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    code VARCHAR(16) NOT NULL,
    name VARCHAR(160) NOT NULL,
    account_type VARCHAR(32) NOT NULL CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'COST_OF_GOODS_SOLD', 'EXPENSE')),
    normal_debit BOOLEAN NOT NULL,
    current_balance_rp BIGINT NOT NULL DEFAULT 0,
    is_system_account BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE (business_id, code)
);

CREATE INDEX IF NOT EXISTS idx_accounts_business ON accounts(business_id);

CREATE TABLE IF NOT EXISTS journal_entries (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    entry_number VARCHAR(64) NOT NULL,
    reference_type VARCHAR(32) NOT NULL,
    reference_id VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    total_debit_rp BIGINT NOT NULL CHECK (total_debit_rp >= 0),
    total_credit_rp BIGINT NOT NULL CHECK (total_credit_rp >= 0),
    is_balanced BOOLEAN NOT NULL DEFAULT TRUE CHECK (is_balanced = TRUE AND total_debit_rp = total_credit_rp),
    posted_by_user_id UUID NOT NULL,
    entry_date BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (business_id, entry_number)
);

CREATE INDEX IF NOT EXISTS idx_journal_entries_business_date ON journal_entries(business_id, entry_date DESC);

CREATE TABLE IF NOT EXISTS journal_lines (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_id VARCHAR(96) NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    account_code VARCHAR(16) NOT NULL,
    debit_rp BIGINT NOT NULL DEFAULT 0 CHECK (debit_rp >= 0),
    credit_rp BIGINT NOT NULL DEFAULT 0 CHECK (credit_rp >= 0),
    memo TEXT,
    created_at BIGINT NOT NULL,
    CHECK ((debit_rp > 0 AND credit_rp = 0) OR (credit_rp > 0 AND debit_rp = 0))
);

CREATE INDEX IF NOT EXISTS idx_journal_lines_entry ON journal_lines(business_id, journal_entry_id);

-- 8. IDEMPOTENT SYNC OUTBOX DEDUPLICATION LOG & AUDIT TRAIL
CREATE TABLE IF NOT EXISTS sync_idempotency_ledger (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    device_id VARCHAR(64) NOT NULL,
    operation VARCHAR(48) NOT NULL,
    entity_type VARCHAR(48) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    server_version BIGINT NOT NULL,
    processed_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sync_idempotency_business ON sync_idempotency_ledger(business_id, processed_at DESC);

-- 9. ROW-LEVEL SECURITY (RLS) MULTI-TENANT ISOLATION
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_variants ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_balances ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales ENABLE ROW LEVEL SECURITY;
ALTER TABLE sale_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_lines ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_sales ON sales
    USING (business_id = current_setting('app.current_business_id', true)::UUID);

CREATE POLICY tenant_isolation_inventory ON inventory_balances
    USING (business_id = current_setting('app.current_business_id', true)::UUID);

CREATE POLICY tenant_isolation_journals ON journal_entries
    USING (business_id = current_setting('app.current_business_id', true)::UUID);
