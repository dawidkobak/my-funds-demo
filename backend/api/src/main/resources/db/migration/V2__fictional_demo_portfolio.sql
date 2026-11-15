-- Entirely fictional data. Valuation date: 2026-11-13.
-- Prices and FX below are illustrative constants, never market quotes.
INSERT INTO mf_user (id, username, currency) VALUES (1, 'demo', 'PLN');
INSERT INTO mf_currency (id, code, name) VALUES
  (1, 'PLN', 'Polski złoty'), (2, 'USD', 'US dollar'),
  (3, 'GBP', 'Pound sterling'), (4, 'EUR', 'Euro');
INSERT INTO mf_wallet (id, owner_id, name, wallet_type) VALUES
  (1, 1, 'Obligacje', 'BOND'), (2, 1, 'Emerytalny', 'STOCK'),
  (3, 1, 'Spekulacyjny', 'STOCK');
INSERT INTO mf_stock (id, name, color, currency_id) VALUES
  (1, 'ISAC', '#15803D', 2), (2, 'EMIM', '#0891B2', 2),
  (3, 'SGLN', '#D97706', 3), (4, 'Oracle', '#DC2626', 2),
  (5, 'Novo Nordisk', '#2563EB', 2), (6, 'Copart', '#7C3AED', 2),
  (7, 'ASBIS', '#DB2777', 1), (8, 'XTB', '#EA580C', 1),
  (9, 'NVIDIA', '#65A30D', 2), (10, 'Microsoft', '#0284C7', 2),
  (11, 'Bitcoin', '#F59E0B', 1);

-- Monthly snapshots, plus the fixed final date. Synthetic price histories.
INSERT INTO mf_currency_rate (currency_id, rate, date)
SELECT mf_currency.id,
       CASE mf_currency.code WHEN 'PLN' THEN 1 WHEN 'USD' THEN 4 WHEN 'GBP' THEN 5 ELSE 4.4 END,
       demo_dates.valuation_date
FROM mf_currency
CROSS JOIN (
  SELECT generate_series('2024-01-31'::date, '2026-10-31'::date, INTERVAL '1 month')::date AS valuation_date
  UNION SELECT '2026-11-13'::date
) AS demo_dates;

INSERT INTO mf_stock_quote (stock_id, value, date)
SELECT mf_stock.id,
       CASE mf_stock.id
         WHEN 1 THEN 100 WHEN 2 THEN 50 WHEN 3 THEN 50 WHEN 4 THEN 100
         WHEN 5 THEN 50 WHEN 6 THEN 50 WHEN 7 THEN 30 WHEN 8 THEN 50
         WHEN 9 THEN 100 WHEN 10 THEN 250 WHEN 11 THEN 400000
       END * (0.78 + 0.22 * (demo_dates.valuation_date - '2024-01-01'::date)::numeric / ('2026-11-13'::date - '2024-01-01'::date)),
       demo_dates.valuation_date
FROM mf_stock
CROSS JOIN (
  SELECT generate_series('2024-01-31'::date, '2026-10-31'::date, INTERVAL '1 month')::date AS valuation_date
  UNION SELECT '2026-11-13'::date
) AS demo_dates;

-- Retirement: 150k + 60k + 50k + 20k + 10k + 10k = 300k PLN.
-- Speculative: 30k + 20k + 20k + 10k + 20k = 100k PLN.
INSERT INTO mf_transaction (wallet_id, stock_id, amount, purchasecost, purchasedate, transactiontype) VALUES
  (2, 1, 250, 78000, '2024-01-15', 'BUY'),
  (2, 1, 125, 44000, '2025-04-15', 'BUY'),
  (2, 2, 300, 47000, '2024-02-15', 'BUY'),
  (2, 3, 200, 41000, '2024-03-15', 'BUY'),
  (2, 4, 50, 18000, '2025-02-15', 'BUY'),
  (2, 5, 50, 11000, '2025-03-15', 'BUY'),
  (2, 6, 50, 8500, '2025-05-15', 'BUY'),
  (3, 7, 1200, 26000, '2024-06-15', 'BUY'),
  (3, 7, 200, 5000, '2025-06-15', 'SELL'),
  (3, 8, 400, 14000, '2024-08-15', 'BUY'),
  (3, 9, 50, 14500, '2025-01-15', 'BUY'),
  (3, 10, 10, 8400, '2025-02-15', 'BUY'),
  (3, 11, 0.05, 16000, '2025-03-15', 'BUY');

INSERT INTO mf_bond_issue
  (id, symbol, bond_type, nominal_value, sale_start_date, sale_end_date, maturity_years, created_at, updated_at)
VALUES
  (1, 'EDO0134', 'EDO', 100, '2024-01-01', '2024-01-31', 10, '2026-11-13', '2026-11-13'),
  (2, 'EDO0135', 'EDO', 100, '2025-01-01', '2025-01-31', 10, '2026-11-13', '2026-11-13');
INSERT INTO mf_bond_position
  (wallet_id, bond_issue_id, available_units, blocked_units, purchase_date, maturity_date, purchase_cost, created_at, updated_at)
VALUES
  (1, 1, 1000, 0, '2024-01-01', '2034-01-01', 100000, '2026-11-13', '2026-11-13'),
  (1, 2, 400, 0, '2025-01-01', '2035-01-01', 40000, '2026-11-13', '2026-11-13');
-- Synthetic accrual curves: 1000 * 105 + 400 * 112.50 = 150000 PLN.
INSERT INTO mf_bond_interest_value
  (bond_issue_id, base_valuation_date, accrued_interest, interest_period_number, imported_at)
SELECT mf_bond_issue.id, demo_dates.valuation_date::date,
       CASE mf_bond_issue.id WHEN 1 THEN 5 ELSE 12.5 END
         * (LEAST(demo_dates.valuation_date::date, '2026-11-13'::date) - mf_bond_issue.sale_start_date)::numeric
         / ('2026-11-13'::date - mf_bond_issue.sale_start_date),
       1, '2026-11-13'::timestamp
FROM mf_bond_issue
CROSS JOIN LATERAL generate_series(mf_bond_issue.sale_start_date, '2026-11-15'::date, INTERVAL '1 day') AS demo_dates(valuation_date);

-- Savings goals illustrate allocations, not extra assets in the net-worth sum.
INSERT INTO mf_piggy_bank (owner_id, name, aimamount, currentamount, duedate) VALUES
  (1, 'Podróż demo', 12000, 7200, '2027-08-01'),
  (1, 'Kurs językowy demo', 5000, 3000, '2027-03-01');

INSERT INTO mf_expense_folder (id, owner_id, parent_id, name, depth, sort_order, created_at, updated_at) VALUES
  (1, 1, NULL, '2025', 0, 1, '2026-11-13', '2026-11-13'),
  (2, 1, NULL, '2026', 0, 2, '2026-11-13', '2026-11-13'),
  (3, 1, 1, 'Budżet demo', 1, 1, '2026-11-13', '2026-11-13'),
  (4, 1, 2, 'Budżet demo', 1, 1, '2026-11-13', '2026-11-13');
INSERT INTO mf_expense_category (id, owner_id, name, color, icon, is_active, created_at, updated_at) VALUES
  (1, 1, 'Mieszkanie i dom', '#2563EB', 'house', true, '2026-11-13', '2026-11-13'),
  (2, 1, 'Zakupy spożywcze', '#15803D', 'basket-shopping', true, '2026-11-13', '2026-11-13'),
  (3, 1, 'Transport', '#D97706', 'car', true, '2026-11-13', '2026-11-13'),
  (4, 1, 'Rozrywka', '#7C3AED', 'gamepad', true, '2026-11-13', '2026-11-13'),
  (5, 1, 'Subskrypcje', '#0891B2', 'tv', true, '2026-11-13', '2026-11-13');
INSERT INTO mf_expense_tag (id, owner_id, name, is_active, created_at, updated_at) VALUES
  (1, 1, 'Stałe', true, '2026-11-13', '2026-11-13'),
  (2, 1, 'Planowane', true, '2026-11-13', '2026-11-13');
INSERT INTO mf_expense_payment_source (id, owner_id, name, type, is_active, created_at, updated_at) VALUES
  (1, 1, 'Konto demonstracyjne', 'bank', true, '2026-11-13', '2026-11-13'),
  (2, 1, 'Gotówka demo', 'cash', true, '2026-11-13', '2026-11-13');
INSERT INTO mf_expense_item
  (owner_id, folder_id, payment_source_id, category_id, currency_id, name, note, amount, expense_month, expense_date, entry_type, created_at, updated_at)
SELECT 1, CASE WHEN extract(year FROM demo_months.month) = 2025 THEN 3 ELSE 4 END,
       CASE WHEN mf_expense_category.id = 4 THEN 2 ELSE 1 END,
       mf_expense_category.id, 1, mf_expense_category.name || ' — przykład', 'Fikcyjna pozycja demonstracyjna',
       CASE mf_expense_category.id WHEN 1 THEN 2400 WHEN 2 THEN 1000 WHEN 3 THEN 450 WHEN 4 THEN 300 ELSE 85 END
         + CASE WHEN mf_expense_category.id IN (2, 3, 4) THEN extract(month FROM demo_months.month)::int * 7 ELSE 0 END,
       demo_months.month::date, demo_months.month::date + 5, 'expense', '2026-11-13', '2026-11-13'
FROM mf_expense_category
CROSS JOIN generate_series('2025-01-01'::date, '2026-11-01'::date, INTERVAL '1 month') AS demo_months(month);
INSERT INTO mf_expense_item_tag (expense_item_id, tag_id)
SELECT mf_expense_item.id, CASE WHEN mf_expense_item.category_id IN (1, 5) THEN 1 ELSE 2 END FROM mf_expense_item;
INSERT INTO mf_expense_item
  (owner_id, folder_id, payment_source_id, currency_id, name, note, amount, expense_month, expense_date, entry_type, counting_status, created_at, updated_at)
VALUES (1, 4, 1, 1, 'Transfer własny — demo', 'Fikcyjny transfer wyłączony z wydatków', 1000, '2026-11-01', '2026-11-07', 'expense', 'excluded_internal_transfer', '2026-11-13', '2026-11-13');

INSERT INTO mf_stock_analysis
  (stock_id, company_name, ticker, country, industry, source_markdown, status, model, prompt_version, report_markdown, total_score, decision, metadata_json, created_at, completed_at, updated_at)
VALUES (10, 'Microsoft — przykład demonstracyjny', 'MSFT', 'USA', 'Technologia',
  'Fikcyjny materiał pokazujący format analizy. Nie zawiera danych finansowych emitenta.',
  'COMPLETED', 'demo-fixture', 'demo-v1',
  '# Przykład analizy demonstracyjnej

Ten raport został napisany wyłącznie na potrzeby prezentacji aplikacji. Nie jest wynikiem analizy spółki ani rekomendacją inwestycyjną.

## Mocne strony
- Zróżnicowany model biznesowy (przykładowa teza).

## Ryzyka
- Wycena i konkurencja (przykładowe zagadnienia).

## Decyzja
Obserwować — fikcyjna ocena pokazująca format raportu.',
  60, 'OBSERWOWAC', '{"synthetic":true}', '2026-11-13', '2026-11-13', '2026-11-13');

-- Keep generated identifiers safe for CRUD in the full local stack.
SELECT setval(pg_get_serial_sequence('mf_user', 'id'), (SELECT MAX(mf_user.id) FROM mf_user));
SELECT setval(pg_get_serial_sequence('mf_currency', 'id'), (SELECT MAX(mf_currency.id) FROM mf_currency));
SELECT setval(pg_get_serial_sequence('mf_wallet', 'id'), (SELECT MAX(mf_wallet.id) FROM mf_wallet));
SELECT setval(pg_get_serial_sequence('mf_stock', 'id'), (SELECT MAX(mf_stock.id) FROM mf_stock));
SELECT setval(pg_get_serial_sequence('mf_bond_issue', 'id'), (SELECT MAX(mf_bond_issue.id) FROM mf_bond_issue));
SELECT setval(pg_get_serial_sequence('mf_expense_folder', 'id'), (SELECT MAX(mf_expense_folder.id) FROM mf_expense_folder));
SELECT setval(pg_get_serial_sequence('mf_expense_category', 'id'), (SELECT MAX(mf_expense_category.id) FROM mf_expense_category));
SELECT setval(pg_get_serial_sequence('mf_expense_tag', 'id'), (SELECT MAX(mf_expense_tag.id) FROM mf_expense_tag));
SELECT setval(pg_get_serial_sequence('mf_expense_payment_source', 'id'), (SELECT MAX(mf_expense_payment_source.id) FROM mf_expense_payment_source));
