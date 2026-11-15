-- Clean PostgreSQL schema baseline. Contains no application data.
SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

CREATE FUNCTION public.mf_expense_folder_validate_hierarchy() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.parent_id IS NULL AND NEW.depth <> 0 THEN
        RAISE EXCEPTION 'Root expense folder must have depth 0';
    END IF;
    IF NEW.parent_id IS NOT NULL THEN
        IF NEW.depth <> 1 THEN
            RAISE EXCEPTION 'Child expense folder must have depth 1';
        END IF;
        IF NOT EXISTS (
            SELECT 1
            FROM mf_expense_folder
            WHERE mf_expense_folder.id = NEW.parent_id
              AND mf_expense_folder.owner_id = NEW.owner_id
              AND mf_expense_folder.parent_id IS NULL
              AND mf_expense_folder.depth = 0
        ) THEN
            RAISE EXCEPTION 'Expense folder parent must be an owner root folder';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

SET default_tablespace = '';

SET default_table_access_method = heap;

CREATE TABLE public.mf_bond_interest_value (
    id integer NOT NULL,
    bond_issue_id integer NOT NULL,
    base_valuation_date date NOT NULL,
    accrued_interest numeric(38,16) NOT NULL,
    interest_period_number integer NOT NULL,
    source_pdf_url text,
    imported_at timestamp(0) without time zone NOT NULL
);

CREATE SEQUENCE public.mf_bond_interest_value_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_bond_interest_value_id_seq OWNED BY public.mf_bond_interest_value.id;

CREATE TABLE public.mf_bond_issue (
    id integer NOT NULL,
    symbol character varying(20) NOT NULL,
    bond_type character varying(20) NOT NULL,
    nominal_value numeric(38,16) NOT NULL,
    sale_start_date date NOT NULL,
    sale_end_date date NOT NULL,
    maturity_years integer NOT NULL,
    first_year_interest_rate numeric(10,4) DEFAULT NULL::numeric,
    interest_margin numeric(10,4) DEFAULT NULL::numeric,
    capitalization_frequency character varying(50) DEFAULT NULL::character varying,
    interest_payment_type character varying(100) DEFAULT NULL::character varying,
    issue_letter_url text,
    interest_table_url text,
    created_at timestamp(0) without time zone NOT NULL,
    updated_at timestamp(0) without time zone NOT NULL,
    CONSTRAINT mf_bond_issue_bond_type_check CHECK (((bond_type)::text = ANY ((ARRAY['EDO'::character varying, 'COI'::character varying, 'ROD'::character varying, 'TOS'::character varying, 'OTS'::character varying, 'ROR'::character varying, 'DOR'::character varying, 'ROS'::character varying])::text[])))
);

CREATE SEQUENCE public.mf_bond_issue_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_bond_issue_id_seq OWNED BY public.mf_bond_issue.id;

CREATE TABLE public.mf_bond_position (
    id integer NOT NULL,
    wallet_id integer NOT NULL,
    bond_issue_id integer NOT NULL,
    available_units numeric(38,16) NOT NULL,
    blocked_units numeric(38,16) NOT NULL,
    purchase_date date NOT NULL,
    maturity_date date NOT NULL,
    purchase_cost numeric(38,16) NOT NULL,
    source_file_path text,
    source_value_date date,
    source_current_value numeric(38,16) DEFAULT NULL::numeric,
    created_at timestamp(0) without time zone NOT NULL,
    updated_at timestamp(0) without time zone NOT NULL
);

CREATE SEQUENCE public.mf_bond_position_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_bond_position_id_seq OWNED BY public.mf_bond_position.id;

CREATE TABLE public.mf_currency (
    id integer NOT NULL,
    code character varying(10) NOT NULL,
    name character varying(255) NOT NULL,
    stooqname character varying(255) DEFAULT NULL::character varying
);

CREATE SEQUENCE public.mf_currency_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_currency_id_seq OWNED BY public.mf_currency.id;

CREATE TABLE public.mf_currency_rate (
    id integer NOT NULL,
    currency_id integer NOT NULL,
    rate numeric(20,8) NOT NULL,
    date timestamp(0) without time zone NOT NULL
);

CREATE SEQUENCE public.mf_currency_rate_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_currency_rate_id_seq OWNED BY public.mf_currency_rate.id;

CREATE TABLE public.mf_expense (
    id integer NOT NULL,
    list_id integer NOT NULL,
    name character varying(255) NOT NULL,
    createdat timestamp(0) without time zone DEFAULT NULL::timestamp without time zone,
    value numeric(38,2) NOT NULL,
    color character varying(255) DEFAULT NULL::character varying
);

CREATE TABLE public.mf_expense_category (
    id integer NOT NULL,
    owner_id integer NOT NULL,
    name character varying(255) NOT NULL,
    color character varying(7) NOT NULL,
    icon character varying(100) NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp(0) without time zone NOT NULL,
    updated_at timestamp(0) without time zone NOT NULL,
    CONSTRAINT mf_expense_category_color_check CHECK (((color)::text ~ '^#[0-9A-Fa-f]{6}$'::text))
);

CREATE SEQUENCE public.mf_expense_category_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_expense_category_id_seq OWNED BY public.mf_expense_category.id;

CREATE TABLE public.mf_expense_folder (
    id integer NOT NULL,
    owner_id integer NOT NULL,
    parent_id integer,
    name character varying(255) NOT NULL,
    depth smallint NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp(0) without time zone NOT NULL,
    updated_at timestamp(0) without time zone NOT NULL,
    CONSTRAINT mf_expense_folder_depth_check CHECK ((depth = ANY (ARRAY[0, 1])))
);

CREATE SEQUENCE public.mf_expense_folder_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_expense_folder_id_seq OWNED BY public.mf_expense_folder.id;

CREATE SEQUENCE public.mf_expense_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_expense_id_seq OWNED BY public.mf_expense.id;

CREATE TABLE public.mf_expense_import_batch (
    id integer NOT NULL,
    owner_id integer NOT NULL,
    format character varying(50) NOT NULL,
    original_filename character varying(255) NOT NULL,
    checksum character varying(64) NOT NULL,
    status character varying(20) NOT NULL,
    total_rows integer NOT NULL,
    imported_rows integer NOT NULL,
    skipped_rows integer NOT NULL,
    created_at timestamp(0) without time zone NOT NULL,
    committed_at timestamp(0) without time zone DEFAULT NULL::timestamp without time zone,
    CONSTRAINT mf_expense_import_batch_counts_check CHECK (((total_rows >= 0) AND (imported_rows >= 0) AND (skipped_rows >= 0) AND ((imported_rows + skipped_rows) <= total_rows))),
    CONSTRAINT mf_expense_import_batch_status_check CHECK (((status)::text = ANY ((ARRAY['previewed'::character varying, 'committed'::character varying, 'failed'::character varying])::text[])))
);

CREATE SEQUENCE public.mf_expense_import_batch_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_expense_import_batch_id_seq OWNED BY public.mf_expense_import_batch.id;

CREATE TABLE public.mf_expense_item (
    id integer NOT NULL,
    owner_id integer NOT NULL,
    folder_id integer NOT NULL,
    payment_source_id integer NOT NULL,
    category_id integer,
    currency_id integer NOT NULL,
    import_batch_id integer,
    name character varying(255) NOT NULL,
    note text,
    amount numeric(38,2) NOT NULL,
    expense_month date NOT NULL,
    expense_date date,
    entry_type character varying(20) NOT NULL,
    source_fingerprint character varying(64) DEFAULT NULL::character varying,
    created_at timestamp(0) without time zone NOT NULL,
    updated_at timestamp(0) without time zone NOT NULL,
    counting_status character varying(40) DEFAULT 'included'::character varying NOT NULL,
    CONSTRAINT mf_expense_item_amount_check CHECK ((amount <> 'NaN'::numeric)),
    CONSTRAINT mf_expense_item_counting_status_check CHECK (((counting_status)::text = ANY ((ARRAY['included'::character varying, 'excluded_internal_transfer'::character varying, 'excluded_business'::character varying, 'excluded_investment'::character varying, 'excluded_cash_transfer'::character varying, 'excluded_reimbursed'::character varying, 'excluded_duplicate'::character varying])::text[]))),
    CONSTRAINT mf_expense_item_date_check CHECK (((expense_date IS NULL) OR ((date_trunc('month'::text, (expense_date)::timestamp with time zone))::date = expense_month))),
    CONSTRAINT mf_expense_item_month_check CHECK ((expense_month = (date_trunc('month'::text, (expense_month)::timestamp with time zone))::date)),
    CONSTRAINT mf_expense_item_type_check CHECK (((entry_type)::text = ANY ((ARRAY['expense'::character varying, 'refund'::character varying, 'adjustment'::character varying])::text[])))
);

CREATE SEQUENCE public.mf_expense_item_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_expense_item_id_seq OWNED BY public.mf_expense_item.id;

CREATE TABLE public.mf_expense_item_tag (
    expense_item_id integer NOT NULL,
    tag_id integer NOT NULL
);

CREATE TABLE public.mf_expense_payment_source (
    id integer NOT NULL,
    owner_id integer NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(20) NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp(0) without time zone NOT NULL,
    updated_at timestamp(0) without time zone NOT NULL,
    CONSTRAINT mf_expense_payment_source_type_check CHECK (((type)::text = ANY ((ARRAY['bank'::character varying, 'cash'::character varying, 'manual'::character varying, 'legacy'::character varying])::text[])))
);

CREATE SEQUENCE public.mf_expense_payment_source_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_expense_payment_source_id_seq OWNED BY public.mf_expense_payment_source.id;

CREATE TABLE public.mf_expense_tag (
    id integer NOT NULL,
    owner_id integer NOT NULL,
    name character varying(100) NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp(0) without time zone NOT NULL,
    updated_at timestamp(0) without time zone NOT NULL
);

CREATE SEQUENCE public.mf_expense_tag_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_expense_tag_id_seq OWNED BY public.mf_expense_tag.id;

CREATE TABLE public.mf_expenses_list (
    id integer NOT NULL,
    owner_id integer NOT NULL,
    name character varying(255) NOT NULL,
    createdat timestamp(0) without time zone DEFAULT NULL::timestamp without time zone
);

CREATE SEQUENCE public.mf_expenses_list_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_expenses_list_id_seq OWNED BY public.mf_expenses_list.id;

CREATE TABLE public.mf_piggy_bank (
    id integer NOT NULL,
    owner_id integer NOT NULL,
    name character varying(255) NOT NULL,
    aimamount numeric(38,2) NOT NULL,
    currentamount numeric(38,2) NOT NULL,
    duedate date,
    link character varying(255) DEFAULT NULL::character varying
);

CREATE SEQUENCE public.mf_piggy_bank_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_piggy_bank_id_seq OWNED BY public.mf_piggy_bank.id;

CREATE TABLE public.mf_stock (
    id integer NOT NULL,
    name character varying(255) NOT NULL,
    color character varying(255) DEFAULT NULL::character varying,
    currency_id integer,
    stooqname character varying(255) DEFAULT NULL::character varying
);

CREATE TABLE public.mf_stock_analysis (
    id integer NOT NULL,
    stock_id integer,
    company_name character varying(255) NOT NULL,
    ticker character varying(50) DEFAULT NULL::character varying,
    country character varying(255) DEFAULT NULL::character varying,
    industry character varying(255) DEFAULT NULL::character varying,
    source_markdown text NOT NULL,
    source_file_path text,
    status character varying(20) NOT NULL,
    model character varying(100) NOT NULL,
    prompt_version character varying(100) NOT NULL,
    report_markdown text,
    total_score integer,
    decision character varying(50) DEFAULT NULL::character varying,
    metadata_json jsonb,
    error_message text,
    created_at timestamp(0) without time zone NOT NULL,
    processing_started_at timestamp(0) without time zone DEFAULT NULL::timestamp without time zone,
    completed_at timestamp(0) without time zone DEFAULT NULL::timestamp without time zone,
    updated_at timestamp(0) without time zone NOT NULL,
    CONSTRAINT mf_stock_analysis_decision_check CHECK (((decision IS NULL) OR ((decision)::text = ANY ((ARRAY['ODRZUCIC'::character varying, 'OBSERWOWAC'::character varying, 'GLEBSZA_ANALIZA'::character varying, 'KANDYDAT_DO_PORTFELA'::character varying])::text[])))),
    CONSTRAINT mf_stock_analysis_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE SEQUENCE public.mf_stock_analysis_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_stock_analysis_id_seq OWNED BY public.mf_stock_analysis.id;

CREATE SEQUENCE public.mf_stock_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_stock_id_seq OWNED BY public.mf_stock.id;

CREATE TABLE public.mf_stock_quote (
    id integer NOT NULL,
    stock_id integer NOT NULL,
    value numeric(38,16) NOT NULL,
    date timestamp(0) without time zone NOT NULL
);

CREATE SEQUENCE public.mf_stock_quote_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_stock_quote_id_seq OWNED BY public.mf_stock_quote.id;

CREATE TABLE public.mf_sub_expense (
    id integer NOT NULL,
    expense_id integer NOT NULL,
    name character varying(255) NOT NULL,
    createdat timestamp(0) without time zone DEFAULT NULL::timestamp without time zone,
    value numeric(38,2) NOT NULL
);

CREATE SEQUENCE public.mf_sub_expense_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_sub_expense_id_seq OWNED BY public.mf_sub_expense.id;

CREATE TABLE public.mf_transaction (
    id integer NOT NULL,
    stock_id integer,
    wallet_id integer NOT NULL,
    amount numeric(38,16) NOT NULL,
    purchasedate date,
    purchasecost numeric(38,16) NOT NULL,
    transactiontype character varying(4) DEFAULT 'BUY'::character varying NOT NULL
);

CREATE SEQUENCE public.mf_transaction_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_transaction_id_seq OWNED BY public.mf_transaction.id;

CREATE TABLE public.mf_user (
    id integer NOT NULL,
    username character varying(255) NOT NULL,
    currency character varying(255) DEFAULT NULL::character varying
);

CREATE SEQUENCE public.mf_user_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_user_id_seq OWNED BY public.mf_user.id;

CREATE TABLE public.mf_wallet (
    id integer NOT NULL,
    owner_id integer NOT NULL,
    name character varying(255) NOT NULL,
    wallet_type character varying(20) DEFAULT 'STOCK'::character varying NOT NULL,
    CONSTRAINT mf_wallet_wallet_type_check CHECK (((wallet_type)::text = ANY ((ARRAY['STOCK'::character varying, 'BOND'::character varying])::text[])))
);

CREATE SEQUENCE public.mf_wallet_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.mf_wallet_id_seq OWNED BY public.mf_wallet.id;

ALTER TABLE ONLY public.mf_bond_interest_value ALTER COLUMN id SET DEFAULT nextval('public.mf_bond_interest_value_id_seq'::regclass);

ALTER TABLE ONLY public.mf_bond_issue ALTER COLUMN id SET DEFAULT nextval('public.mf_bond_issue_id_seq'::regclass);

ALTER TABLE ONLY public.mf_bond_position ALTER COLUMN id SET DEFAULT nextval('public.mf_bond_position_id_seq'::regclass);

ALTER TABLE ONLY public.mf_currency ALTER COLUMN id SET DEFAULT nextval('public.mf_currency_id_seq'::regclass);

ALTER TABLE ONLY public.mf_currency_rate ALTER COLUMN id SET DEFAULT nextval('public.mf_currency_rate_id_seq'::regclass);

ALTER TABLE ONLY public.mf_expense ALTER COLUMN id SET DEFAULT nextval('public.mf_expense_id_seq'::regclass);

ALTER TABLE ONLY public.mf_expense_category ALTER COLUMN id SET DEFAULT nextval('public.mf_expense_category_id_seq'::regclass);

ALTER TABLE ONLY public.mf_expense_folder ALTER COLUMN id SET DEFAULT nextval('public.mf_expense_folder_id_seq'::regclass);

ALTER TABLE ONLY public.mf_expense_import_batch ALTER COLUMN id SET DEFAULT nextval('public.mf_expense_import_batch_id_seq'::regclass);

ALTER TABLE ONLY public.mf_expense_item ALTER COLUMN id SET DEFAULT nextval('public.mf_expense_item_id_seq'::regclass);

ALTER TABLE ONLY public.mf_expense_payment_source ALTER COLUMN id SET DEFAULT nextval('public.mf_expense_payment_source_id_seq'::regclass);

ALTER TABLE ONLY public.mf_expense_tag ALTER COLUMN id SET DEFAULT nextval('public.mf_expense_tag_id_seq'::regclass);

ALTER TABLE ONLY public.mf_expenses_list ALTER COLUMN id SET DEFAULT nextval('public.mf_expenses_list_id_seq'::regclass);

ALTER TABLE ONLY public.mf_piggy_bank ALTER COLUMN id SET DEFAULT nextval('public.mf_piggy_bank_id_seq'::regclass);

ALTER TABLE ONLY public.mf_stock ALTER COLUMN id SET DEFAULT nextval('public.mf_stock_id_seq'::regclass);

ALTER TABLE ONLY public.mf_stock_analysis ALTER COLUMN id SET DEFAULT nextval('public.mf_stock_analysis_id_seq'::regclass);

ALTER TABLE ONLY public.mf_stock_quote ALTER COLUMN id SET DEFAULT nextval('public.mf_stock_quote_id_seq'::regclass);

ALTER TABLE ONLY public.mf_sub_expense ALTER COLUMN id SET DEFAULT nextval('public.mf_sub_expense_id_seq'::regclass);

ALTER TABLE ONLY public.mf_transaction ALTER COLUMN id SET DEFAULT nextval('public.mf_transaction_id_seq'::regclass);

ALTER TABLE ONLY public.mf_user ALTER COLUMN id SET DEFAULT nextval('public.mf_user_id_seq'::regclass);

ALTER TABLE ONLY public.mf_wallet ALTER COLUMN id SET DEFAULT nextval('public.mf_wallet_id_seq'::regclass);

ALTER TABLE ONLY public.mf_bond_interest_value
    ADD CONSTRAINT mf_bond_interest_value_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_bond_issue
    ADD CONSTRAINT mf_bond_issue_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_bond_position
    ADD CONSTRAINT mf_bond_position_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_currency
    ADD CONSTRAINT mf_currency_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_currency_rate
    ADD CONSTRAINT mf_currency_rate_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_expense_category
    ADD CONSTRAINT mf_expense_category_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_expense_folder
    ADD CONSTRAINT mf_expense_folder_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_expense_import_batch
    ADD CONSTRAINT mf_expense_import_batch_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_expense_item
    ADD CONSTRAINT mf_expense_item_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_expense_item_tag
    ADD CONSTRAINT mf_expense_item_tag_pkey PRIMARY KEY (expense_item_id, tag_id);

ALTER TABLE ONLY public.mf_expense_payment_source
    ADD CONSTRAINT mf_expense_payment_source_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_expense
    ADD CONSTRAINT mf_expense_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_expense_tag
    ADD CONSTRAINT mf_expense_tag_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_expenses_list
    ADD CONSTRAINT mf_expenses_list_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_piggy_bank
    ADD CONSTRAINT mf_piggy_bank_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_stock_analysis
    ADD CONSTRAINT mf_stock_analysis_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_stock
    ADD CONSTRAINT mf_stock_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_stock_quote
    ADD CONSTRAINT mf_stock_quote_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_sub_expense
    ADD CONSTRAINT mf_sub_expense_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_transaction
    ADD CONSTRAINT mf_transaction_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_user
    ADD CONSTRAINT mf_user_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.mf_wallet
    ADD CONSTRAINT mf_wallet_pkey PRIMARY KEY (id);

CREATE UNIQUE INDEX currency_date_unique ON public.mf_currency_rate USING btree (currency_id, date);

CREATE INDEX idx_13d5f276712520f3 ON public.mf_transaction USING btree (wallet_id);

CREATE INDEX idx_13d5f276dcd6110 ON public.mf_transaction USING btree (stock_id);

CREATE INDEX idx_330026f5f395db7b ON public.mf_sub_expense USING btree (expense_id);

CREATE INDEX idx_459671837e3c61f9 ON public.mf_wallet USING btree (owner_id);

CREATE INDEX idx_814c6077e3c61f9 ON public.mf_expenses_list USING btree (owner_id);

CREATE INDEX idx_863b2cd8dcd6110 ON public.mf_stock_analysis USING btree (stock_id);

CREATE INDEX idx_968320a2dcd6110 ON public.mf_stock_quote USING btree (stock_id);

CREATE INDEX idx_977e11737e3c61f9 ON public.mf_piggy_bank USING btree (owner_id);

CREATE INDEX idx_97c4503e38248176 ON public.mf_currency_rate USING btree (currency_id);

CREATE INDEX idx_b666088338248176 ON public.mf_stock USING btree (currency_id);

CREATE INDEX idx_d4baac2a3dae168b ON public.mf_expense USING btree (list_id);

CREATE INDEX idx_mf_bond_interest_value_bond_issue_id ON public.mf_bond_interest_value USING btree (bond_issue_id);

CREATE INDEX idx_mf_bond_position_bond_issue_id ON public.mf_bond_position USING btree (bond_issue_id);

CREATE INDEX idx_mf_bond_position_wallet_id ON public.mf_bond_position USING btree (wallet_id);

CREATE UNIQUE INDEX mf_bond_interest_value_issue_date_unique ON public.mf_bond_interest_value USING btree (bond_issue_id, base_valuation_date);

CREATE INDEX mf_bond_issue_bond_type_idx ON public.mf_bond_issue USING btree (bond_type);

CREATE UNIQUE INDEX mf_bond_issue_symbol_unique ON public.mf_bond_issue USING btree (symbol);

CREATE UNIQUE INDEX mf_bond_position_wallet_issue_purchase_maturity_unique ON public.mf_bond_position USING btree (wallet_id, bond_issue_id, purchase_date, maturity_date);

CREATE UNIQUE INDEX mf_expense_category_owner_name_unique ON public.mf_expense_category USING btree (owner_id, lower((name)::text));

CREATE UNIQUE INDEX mf_expense_folder_owner_parent_name_unique ON public.mf_expense_folder USING btree (owner_id, COALESCE(parent_id, 0), lower((name)::text));

CREATE INDEX mf_expense_folder_owner_sort_index ON public.mf_expense_folder USING btree (owner_id, parent_id, sort_order, name);

CREATE INDEX mf_expense_import_batch_checksum_index ON public.mf_expense_import_batch USING btree (owner_id, checksum);

CREATE INDEX mf_expense_import_batch_owner_created_index ON public.mf_expense_import_batch USING btree (owner_id, created_at DESC);

CREATE INDEX mf_expense_item_owner_category_index ON public.mf_expense_item USING btree (owner_id, category_id);

CREATE INDEX mf_expense_item_owner_counting_month_index ON public.mf_expense_item USING btree (owner_id, counting_status, expense_month DESC);

CREATE INDEX mf_expense_item_owner_currency_index ON public.mf_expense_item USING btree (owner_id, currency_id);

CREATE UNIQUE INDEX mf_expense_item_owner_fingerprint_unique ON public.mf_expense_item USING btree (owner_id, source_fingerprint) WHERE (source_fingerprint IS NOT NULL);

CREATE INDEX mf_expense_item_owner_folder_month_index ON public.mf_expense_item USING btree (owner_id, folder_id, expense_month DESC);

CREATE INDEX mf_expense_item_owner_month_index ON public.mf_expense_item USING btree (owner_id, expense_month DESC);

CREATE INDEX mf_expense_item_owner_name_index ON public.mf_expense_item USING btree (owner_id, lower((name)::text));

CREATE INDEX mf_expense_item_owner_source_index ON public.mf_expense_item USING btree (owner_id, payment_source_id);

CREATE INDEX mf_expense_item_tag_tag_index ON public.mf_expense_item_tag USING btree (tag_id, expense_item_id);

CREATE UNIQUE INDEX mf_expense_payment_source_owner_name_unique ON public.mf_expense_payment_source USING btree (owner_id, lower((name)::text));

CREATE UNIQUE INDEX mf_expense_tag_owner_name_unique ON public.mf_expense_tag USING btree (owner_id, lower((name)::text));

CREATE INDEX mf_stock_analysis_status_created_at_idx ON public.mf_stock_analysis USING btree (status, created_at);

CREATE UNIQUE INDEX uniq_bf344f53f85e0677 ON public.mf_user USING btree (username);

CREATE TRIGGER mf_expense_folder_validate_hierarchy_trigger BEFORE INSERT OR UPDATE ON public.mf_expense_folder FOR EACH ROW EXECUTE FUNCTION public.mf_expense_folder_validate_hierarchy();

ALTER TABLE ONLY public.mf_transaction
    ADD CONSTRAINT fk_13d5f276712520f3 FOREIGN KEY (wallet_id) REFERENCES public.mf_wallet(id);

ALTER TABLE ONLY public.mf_transaction
    ADD CONSTRAINT fk_13d5f276dcd6110 FOREIGN KEY (stock_id) REFERENCES public.mf_stock(id);

ALTER TABLE ONLY public.mf_expense_item_tag
    ADD CONSTRAINT fk_2dcb3df87f741964 FOREIGN KEY (expense_item_id) REFERENCES public.mf_expense_item(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.mf_expense_item_tag
    ADD CONSTRAINT fk_2dcb3df8bad26311 FOREIGN KEY (tag_id) REFERENCES public.mf_expense_tag(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.mf_sub_expense
    ADD CONSTRAINT fk_330026f5f395db7b FOREIGN KEY (expense_id) REFERENCES public.mf_expense(id);

ALTER TABLE ONLY public.mf_wallet
    ADD CONSTRAINT fk_459671837e3c61f9 FOREIGN KEY (owner_id) REFERENCES public.mf_user(id);

ALTER TABLE ONLY public.mf_expense_import_batch
    ADD CONSTRAINT fk_500642cd7e3c61f9 FOREIGN KEY (owner_id) REFERENCES public.mf_user(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.mf_expenses_list
    ADD CONSTRAINT fk_814c6077e3c61f9 FOREIGN KEY (owner_id) REFERENCES public.mf_user(id);

ALTER TABLE ONLY public.mf_stock_quote
    ADD CONSTRAINT fk_968320a2dcd6110 FOREIGN KEY (stock_id) REFERENCES public.mf_stock(id);

ALTER TABLE ONLY public.mf_piggy_bank
    ADD CONSTRAINT fk_977e11737e3c61f9 FOREIGN KEY (owner_id) REFERENCES public.mf_user(id);

ALTER TABLE ONLY public.mf_currency_rate
    ADD CONSTRAINT fk_97c4503e38248176 FOREIGN KEY (currency_id) REFERENCES public.mf_currency(id);

ALTER TABLE ONLY public.mf_stock
    ADD CONSTRAINT fk_b666088338248176 FOREIGN KEY (currency_id) REFERENCES public.mf_currency(id);

ALTER TABLE ONLY public.mf_expense_tag
    ADD CONSTRAINT fk_ce9b15d87e3c61f9 FOREIGN KEY (owner_id) REFERENCES public.mf_user(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.mf_expense_category
    ADD CONSTRAINT fk_cf1be8887e3c61f9 FOREIGN KEY (owner_id) REFERENCES public.mf_user(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.mf_expense
    ADD CONSTRAINT fk_d4baac2a3dae168b FOREIGN KEY (list_id) REFERENCES public.mf_expenses_list(id);

ALTER TABLE ONLY public.mf_expense_item
    ADD CONSTRAINT fk_e36fbfc012469de2 FOREIGN KEY (category_id) REFERENCES public.mf_expense_category(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.mf_expense_item
    ADD CONSTRAINT fk_e36fbfc0162cb942 FOREIGN KEY (folder_id) REFERENCES public.mf_expense_folder(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.mf_expense_item
    ADD CONSTRAINT fk_e36fbfc038248176 FOREIGN KEY (currency_id) REFERENCES public.mf_currency(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.mf_expense_item
    ADD CONSTRAINT fk_e36fbfc05a310080 FOREIGN KEY (import_batch_id) REFERENCES public.mf_expense_import_batch(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.mf_expense_item
    ADD CONSTRAINT fk_e36fbfc07e3c61f9 FOREIGN KEY (owner_id) REFERENCES public.mf_user(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.mf_expense_item
    ADD CONSTRAINT fk_e36fbfc0d6153349 FOREIGN KEY (payment_source_id) REFERENCES public.mf_expense_payment_source(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.mf_expense_folder
    ADD CONSTRAINT fk_e3c36772727aca70 FOREIGN KEY (parent_id) REFERENCES public.mf_expense_folder(id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.mf_expense_folder
    ADD CONSTRAINT fk_e3c367727e3c61f9 FOREIGN KEY (owner_id) REFERENCES public.mf_user(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.mf_expense_payment_source
    ADD CONSTRAINT fk_ef155c787e3c61f9 FOREIGN KEY (owner_id) REFERENCES public.mf_user(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.mf_bond_interest_value
    ADD CONSTRAINT fk_mf_bond_interest_value_bond_issue_id FOREIGN KEY (bond_issue_id) REFERENCES public.mf_bond_issue(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.mf_bond_position
    ADD CONSTRAINT fk_mf_bond_position_bond_issue_id FOREIGN KEY (bond_issue_id) REFERENCES public.mf_bond_issue(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.mf_bond_position
    ADD CONSTRAINT fk_mf_bond_position_wallet_id FOREIGN KEY (wallet_id) REFERENCES public.mf_wallet(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.mf_stock_analysis
    ADD CONSTRAINT fk_mf_stock_analysis_stock_id FOREIGN KEY (stock_id) REFERENCES public.mf_stock(id);
