-- Preflight for an existing V2 database BEFORE any portfolio replacement.
-- Non-versioned Flyway callback: keeps the already applied V3 checksum intact.
DO $demo_preflight$
BEGIN
  IF to_regclass('public.mf_demo_dataset_identity') IS NULL
     AND to_regclass('public.mf_bond_position') IS NOT NULL
     AND EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '2' AND success) THEN
  IF (SELECT COUNT(*) FROM mf_transaction) <> 13
     OR (SELECT SUM(mf_transaction.purchasecost) FROM mf_transaction) <> 331400
     OR (SELECT COUNT(*) FROM mf_bond_position) <> 2
     OR (SELECT SUM(mf_bond_position.purchase_cost) FROM mf_bond_position) <> 140000
     OR EXISTS (
       SELECT mf_transaction.wallet_id, mf_transaction.stock_id, mf_transaction.amount,
              mf_transaction.purchasecost, mf_transaction.purchasedate, mf_transaction.transactiontype
       FROM mf_transaction
       EXCEPT ALL SELECT * FROM (VALUES
         (2,1,250,78000,'2024-01-15'::date,'BUY'),(2,1,125,44000,'2025-04-15'::date,'BUY'),
         (2,2,300,47000,'2024-02-15'::date,'BUY'),(2,3,200,41000,'2024-03-15'::date,'BUY'),
         (2,4,50,18000,'2025-02-15'::date,'BUY'),(2,5,50,11000,'2025-03-15'::date,'BUY'),
         (2,6,50,8500,'2025-05-15'::date,'BUY'),(3,7,1200,26000,'2024-06-15'::date,'BUY'),
         (3,7,200,5000,'2025-06-15'::date,'SELL'),(3,8,400,14000,'2024-08-15'::date,'BUY'),
         (3,9,50,14500,'2025-01-15'::date,'BUY'),(3,10,10,8400,'2025-02-15'::date,'BUY'),
         (3,11,0.05,16000,'2025-03-15'::date,'BUY')) AS original_transactions
     )
     OR EXISTS (
       SELECT mf_bond_position.bond_issue_id, mf_bond_position.available_units,
              mf_bond_position.blocked_units, mf_bond_position.purchase_date, mf_bond_position.maturity_date
       FROM mf_bond_position
       EXCEPT ALL SELECT * FROM (VALUES
         (1,1000,0,'2024-01-01'::date,'2034-01-01'::date),
         (2,400,0,'2025-01-01'::date,'2035-01-01'::date)) AS original_bonds
     )
-- Compared against V2 before replacing any portfolio data. Equal row counts
-- plus EXCEPT ALL retain multiplicities; source/metadata fields are checked too.
     OR (SELECT COUNT(*) FROM mf_currency_rate) <> 140
     OR (SELECT COUNT(*) FROM mf_stock_quote) <> 385
     OR (SELECT COUNT(*) FROM mf_bond_issue) <> 2
     OR (SELECT COUNT(*) FROM mf_bond_interest_value) <>
        (('2026-11-15'::date - '2024-01-01'::date + 1) + ('2026-11-15'::date - '2025-01-01'::date + 1))
     OR (SELECT COUNT(*) FROM mf_wallet WHERE (mf_wallet.id, mf_wallet.owner_id, mf_wallet.name, mf_wallet.wallet_type)
       IN ((1,1,'Obligacje','BOND'),(2,1,'Emerytalny','STOCK'),(3,1,'Spekulacyjny','STOCK'))) <> 3
     OR (SELECT mf_stock.currency_id FROM mf_stock WHERE mf_stock.id = 2) IS DISTINCT FROM 2
     OR EXISTS (
       SELECT mf_currency_rate.currency_id, mf_currency_rate.rate, mf_currency_rate.date FROM mf_currency_rate
       EXCEPT ALL
       SELECT currency.id, CASE currency.id WHEN 1 THEN 1 WHEN 2 THEN 4 WHEN 3 THEN 5 ELSE 4.4 END, dates.day
       FROM generate_series(1,4) AS currency(id)
       CROSS JOIN (SELECT generate_series('2024-01-31'::date, '2026-10-31'::date, INTERVAL '1 month')::date AS day
                   UNION SELECT '2026-11-13'::date) AS dates
     )
     OR EXISTS (
       SELECT mf_stock_quote.stock_id, mf_stock_quote.value, mf_stock_quote.date FROM mf_stock_quote
       EXCEPT ALL
       SELECT stock.id,
         ROUND(CASE stock.id WHEN 1 THEN 100 WHEN 2 THEN 50 WHEN 3 THEN 50 WHEN 4 THEN 100
           WHEN 5 THEN 50 WHEN 6 THEN 50 WHEN 7 THEN 30 WHEN 8 THEN 50 WHEN 9 THEN 100
           WHEN 10 THEN 250 WHEN 11 THEN 400000 END *
         (0.78 + 0.22 * (dates.day - '2024-01-01'::date)::numeric / ('2026-11-13'::date - '2024-01-01'::date)), 16), dates.day
       FROM generate_series(1,11) AS stock(id)
       CROSS JOIN (SELECT generate_series('2024-01-31'::date, '2026-10-31'::date, INTERVAL '1 month')::date AS day
                   UNION SELECT '2026-11-13'::date) AS dates
     )
     OR EXISTS (
       SELECT mf_bond_issue.id, mf_bond_issue.symbol, mf_bond_issue.bond_type, mf_bond_issue.nominal_value,
              mf_bond_issue.sale_start_date, mf_bond_issue.sale_end_date, mf_bond_issue.maturity_years
       FROM mf_bond_issue
       EXCEPT ALL SELECT * FROM (VALUES
         (1,'EDO0134','EDO',100,'2024-01-01'::date,'2024-01-31'::date,10),
         (2,'EDO0135','EDO',100,'2025-01-01'::date,'2025-01-31'::date,10)) AS original_issues
     )
     OR EXISTS (SELECT 1 FROM mf_bond_issue WHERE mf_bond_issue.first_year_interest_rate IS NOT NULL
       OR mf_bond_issue.interest_margin IS NOT NULL OR mf_bond_issue.capitalization_frequency IS NOT NULL
       OR mf_bond_issue.interest_payment_type IS NOT NULL OR mf_bond_issue.issue_letter_url IS NOT NULL
       OR mf_bond_issue.interest_table_url IS NOT NULL OR mf_bond_issue.created_at <> '2026-11-13'::timestamp
       OR mf_bond_issue.updated_at <> '2026-11-13'::timestamp)
     OR EXISTS (SELECT 1 FROM mf_bond_position WHERE mf_bond_position.wallet_id <> 1
       OR mf_bond_position.purchase_cost <> CASE mf_bond_position.bond_issue_id WHEN 1 THEN 100000 ELSE 40000 END
       OR mf_bond_position.source_file_path IS NOT NULL OR mf_bond_position.source_value_date IS NOT NULL
       OR mf_bond_position.source_current_value IS NOT NULL OR mf_bond_position.created_at <> '2026-11-13'::timestamp
       OR mf_bond_position.updated_at <> '2026-11-13'::timestamp)
     OR EXISTS (
       SELECT mf_bond_interest_value.bond_issue_id, mf_bond_interest_value.base_valuation_date,
              mf_bond_interest_value.accrued_interest, mf_bond_interest_value.interest_period_number,
              mf_bond_interest_value.source_pdf_url, mf_bond_interest_value.imported_at
       FROM mf_bond_interest_value
       EXCEPT ALL
       SELECT issue.id, dates.day::date,
         ROUND(CASE issue.id WHEN 1 THEN 5 ELSE 12.5 END *
         (LEAST(dates.day::date,'2026-11-13'::date) - issue.start_date)::numeric /
         ('2026-11-13'::date - issue.start_date), 16), 1, NULL::text, '2026-11-13'::timestamp
       FROM (VALUES (1,'2024-01-01'::date),(2,'2025-01-01'::date)) AS issue(id,start_date)
       CROSS JOIN LATERAL generate_series(issue.start_date,'2026-11-15'::date,INTERVAL '1 day') AS dates(day)
     )
     THEN
    RAISE EXCEPTION 'Preserving edited V2 demo data: review a separate migration instead of replacing it';
  END IF;
  END IF;
END;
$demo_preflight$;
