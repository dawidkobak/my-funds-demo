-- Reviewed fictional 2026 marking revision. No trades, costs, FX, issue terms or expenses change.
-- Generated offline by scripts/generate_demo_valuation.py; never regenerate an applied migration.
DO $demo_valuation$
BEGIN
  IF current_database() <> 'my-funds-demo' AND current_database() NOT LIKE 'my-funds-demo-test%' THEN
    RAISE EXCEPTION 'Valuation revision is restricted to the isolated demo database';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM mf_demo_dataset_identity
    WHERE mf_demo_dataset_identity.dataset_key = 'my-funds-fictional-demo'
      AND mf_demo_dataset_identity.synthetic AND mf_demo_dataset_identity.as_of_date = '2026-11-13') THEN
    RAISE EXCEPTION 'Missing fictional demo identity';
  END IF;
  IF (SELECT md5(string_agg(mf_stock_quote.stock_id::text || ':' || mf_stock_quote.date::text || ':' || mf_stock_quote.value::text,
       ',' ORDER BY mf_stock_quote.stock_id, mf_stock_quote.date)) FROM mf_stock_quote
       WHERE mf_stock_quote.date BETWEEN '2026-01-01' AND '2026-11-13') IS DISTINCT FROM 'd9054e8d8029ed06178c5917a0123db6'
     OR (SELECT md5(string_agg(mf_currency_rate.currency_id::text || ':' || mf_currency_rate.date::text || ':' || mf_currency_rate.rate::text,
       ',' ORDER BY mf_currency_rate.currency_id, mf_currency_rate.date)) FROM mf_currency_rate
       WHERE mf_currency_rate.date BETWEEN '2026-01-01' AND '2026-11-13') IS DISTINCT FROM '19396929d499146d8675af75c3bf4296'
     OR (SELECT md5(string_agg(mf_bond_interest_value.bond_issue_id::text || ':' || mf_bond_interest_value.base_valuation_date::text || ':' || mf_bond_interest_value.accrued_interest::text,
       ',' ORDER BY mf_bond_interest_value.bond_issue_id, mf_bond_interest_value.base_valuation_date))
       FROM mf_bond_interest_value WHERE mf_bond_interest_value.base_valuation_date >= '2026-01-01') IS DISTINCT FROM '79a388ac08bafd3e52d6861010942920'
     OR (SELECT md5(string_agg(json_build_array(mf_bond_position.wallet_id, mf_bond_position.bond_issue_id,
       mf_bond_position.available_units, mf_bond_position.blocked_units, mf_bond_position.purchase_date,
       mf_bond_position.maturity_date, mf_bond_position.purchase_cost)::text, ','
       ORDER BY mf_bond_position.bond_issue_id, mf_bond_position.purchase_date)) FROM mf_bond_position)
       IS DISTINCT FROM '9e89faa8eb9e9112c8c53cdb26d82a0f' THEN
    RAISE EXCEPTION 'Preserving edited demo quotes or interest: review a separate valuation revision';
  END IF;
CREATE TEMP TABLE demo_valuation_target (wallet_id integer, stock_id integer PRIMARY KEY, units numeric, final_pln_value numeric) ON COMMIT DROP;
INSERT INTO demo_valuation_target (wallet_id, stock_id, units, final_pln_value) VALUES
  (2, 1, 217, 109348.67),
  (2, 2, 378, 53761.49),
  (2, 3, 138, 49873.26),
  (2, 4, 53, 34218.83),
  (2, 5, 101, 23817.92),
  (2, 6, 277, 37742.18),
  (3, 7, 438, 15821.46),
  (3, 8, 295, 26918.37),
  (3, 9, 61, 32847.19),
  (3, 10, 1, 2136.48),
  (3, 11, 0.05515782, 12835.79);
  IF EXISTS (
    SELECT mf_transaction.wallet_id, mf_transaction.stock_id,
      SUM(CASE WHEN mf_transaction.transactiontype = 'BUY' THEN mf_transaction.amount ELSE -mf_transaction.amount END)
    FROM mf_transaction GROUP BY mf_transaction.wallet_id, mf_transaction.stock_id
    EXCEPT ALL SELECT demo_valuation_target.wallet_id, demo_valuation_target.stock_id, demo_valuation_target.units FROM demo_valuation_target
  ) OR EXISTS (
    SELECT demo_valuation_target.wallet_id, demo_valuation_target.stock_id, demo_valuation_target.units FROM demo_valuation_target
    EXCEPT ALL SELECT mf_transaction.wallet_id, mf_transaction.stock_id,
      SUM(CASE WHEN mf_transaction.transactiontype = 'BUY' THEN mf_transaction.amount ELSE -mf_transaction.amount END)
    FROM mf_transaction GROUP BY mf_transaction.wallet_id, mf_transaction.stock_id
  ) OR (SELECT COUNT(*) FROM mf_transaction) <> 145 OR (SELECT COUNT(*) FROM mf_bond_position) <> 117 THEN
    RAISE EXCEPTION 'Preserving edited demo holdings; this revision does not modify transactions';
  END IF;
-- Smooth model-price changes through 2026, rather than inventing a last-day jump.
WITH demo_terminal_quotes AS (
  SELECT demo_valuation_target.stock_id,
    demo_valuation_target.final_pln_value /
      (demo_valuation_target.units * mf_currency_rate.rate * mf_stock_quote.value) AS price_multiplier
  FROM demo_valuation_target
  JOIN mf_stock ON mf_stock.id = demo_valuation_target.stock_id
  JOIN mf_stock_quote ON mf_stock_quote.stock_id = mf_stock.id AND mf_stock_quote.date = '2026-11-13'::date
  JOIN mf_currency_rate ON mf_currency_rate.currency_id = mf_stock.currency_id
    AND mf_currency_rate.date = (SELECT MAX(mf_currency_rate.date) FROM mf_currency_rate
      WHERE mf_currency_rate.currency_id = mf_stock.currency_id AND mf_currency_rate.date <= '2026-11-13'::date)
)
UPDATE mf_stock_quote SET value = ROUND(mf_stock_quote.value *
  (1 + (demo_terminal_quotes.price_multiplier - 1) *
    (mf_stock_quote.date::date - '2026-01-01'::date)::numeric / ('2026-11-13'::date - '2026-01-01'::date)), 16)
FROM demo_terminal_quotes
WHERE mf_stock_quote.stock_id = demo_terminal_quotes.stock_id
  AND mf_stock_quote.date BETWEEN '2026-01-01'::date AND '2026-11-13'::date;
CREATE TEMP TABLE demo_valuation_bond_period (bond_issue_id integer, start_date date, end_date date, opening_balance numeric, rate numeric, period_number integer) ON COMMIT DROP;
INSERT INTO demo_valuation_bond_period (bond_issue_id, start_date, end_date, opening_balance, rate, period_number) VALUES
  (1, '2026-03-01', '2027-03-01', 161.895462117174, 2.102874028817, 10),
  (2, '2026-04-01', '2027-04-01', 161.895462117174, 2.102874028817, 10),
  (3, '2026-05-01', '2027-05-01', 161.895462117174, 2.102874028817, 10),
  (4, '2026-06-01', '2027-06-01', 161.895462117174, 2.102874028817, 10),
  (5, '2026-07-01', '2027-07-01', 161.895462117174, 2.102874028817, 10),
  (6, '2026-08-01', '2027-08-01', 161.895462117174, 2.102874028817, 10),
  (7, '2026-09-01', '2027-09-01', 161.895462117174, 2.102874028817, 10),
  (8, '2026-10-01', '2027-10-01', 161.895462117174, 2.102874028817, 10),
  (9, '2026-11-01', '2027-11-01', 161.895462117174, 2.102874028817, 10),
  (11, '2026-01-01', '2027-01-01', 157.481760099195, 2.102874028817, 9),
  (12, '2026-02-01', '2027-02-01', 157.481760099195, 2.102874028817, 9),
  (13, '2026-03-01', '2027-03-01', 157.481760099195, 2.102874028817, 9),
  (14, '2026-04-01', '2027-04-01', 157.481760099195, 2.102874028817, 9),
  (15, '2026-05-01', '2027-05-01', 157.481760099195, 2.102874028817, 9),
  (16, '2026-06-01', '2027-06-01', 157.481760099195, 2.102874028817, 9),
  (17, '2026-07-01', '2027-07-01', 157.481760099195, 2.102874028817, 9),
  (18, '2026-08-01', '2027-08-01', 157.481760099195, 2.102874028817, 9),
  (19, '2026-09-01', '2027-09-01', 157.481760099195, 2.102874028817, 9),
  (20, '2026-10-01', '2027-10-01', 157.481760099195, 2.102874028817, 9),
  (21, '2026-11-01', '2027-11-01', 157.481760099195, 2.102874028817, 9),
  (23, '2026-01-01', '2027-01-01', 152.343819449127, 2.102874028817, 8),
  (24, '2026-02-01', '2027-02-01', 152.343819449127, 2.102874028817, 8),
  (25, '2026-03-01', '2027-03-01', 152.343819449127, 2.102874028817, 8),
  (26, '2026-04-01', '2027-04-01', 152.343819449127, 2.102874028817, 8),
  (27, '2026-05-01', '2027-05-01', 152.343819449127, 2.102874028817, 8),
  (28, '2026-06-01', '2027-06-01', 152.343819449127, 2.102874028817, 8),
  (29, '2026-07-01', '2027-07-01', 152.343819449127, 2.102874028817, 8),
  (30, '2026-08-01', '2027-08-01', 152.343819449127, 2.102874028817, 8),
  (31, '2026-09-01', '2027-09-01', 152.343819449127, 2.102874028817, 8),
  (32, '2026-10-01', '2027-10-01', 152.343819449127, 2.102874028817, 8),
  (33, '2026-11-01', '2027-11-01', 152.343819449127, 2.102874028817, 8),
  (35, '2026-01-01', '2027-01-01', 146.107674329285, 2.102874028817, 7),
  (36, '2026-02-01', '2027-02-01', 146.107674329285, 2.102874028817, 7),
  (37, '2026-03-01', '2027-03-01', 146.107674329285, 2.102874028817, 7),
  (38, '2026-04-01', '2027-04-01', 146.107674329285, 2.102874028817, 7),
  (39, '2026-05-01', '2027-05-01', 141.3441509005, 1.602874028817, 7),
  (40, '2026-06-01', '2027-06-01', 141.3441509005, 1.602874028817, 7),
  (41, '2026-07-01', '2027-07-01', 141.3441509005, 1.602874028817, 7),
  (42, '2026-08-01', '2027-08-01', 141.3441509005, 1.602874028817, 7),
  (43, '2026-09-01', '2027-09-01', 141.3441509005, 1.602874028817, 7),
  (44, '2026-10-01', '2027-10-01', 141.3441509005, 1.602874028817, 7),
  (45, '2026-11-01', '2027-11-01', 141.3441509005, 1.602874028817, 7),
  (47, '2026-01-01', '2027-01-01', 134.418535912095, 1.602874028817, 6),
  (48, '2026-02-01', '2027-02-01', 134.418535912095, 1.602874028817, 6),
  (49, '2026-03-01', '2027-03-01', 134.418535912095, 1.602874028817, 6),
  (50, '2026-04-01', '2027-04-01', 134.418535912095, 1.602874028817, 6),
  (51, '2026-05-01', '2027-05-01', 134.418535912095, 1.602874028817, 6),
  (52, '2026-06-01', '2027-06-01', 134.418535912095, 1.602874028817, 6),
  (53, '2026-07-01', '2027-07-01', 134.418535912095, 1.602874028817, 6),
  (54, '2026-08-01', '2027-08-01', 134.418535912095, 1.602874028817, 6),
  (55, '2026-09-01', '2027-09-01', 134.418535912095, 1.602874028817, 6),
  (56, '2026-10-01', '2027-10-01', 134.418535912095, 1.602874028817, 6),
  (57, '2026-11-01', '2027-11-01', 134.418535912095, 1.602874028817, 6),
  (59, '2026-01-01', '2027-01-01', 122.5198498083, 1.602874028817, 5),
  (60, '2026-02-01', '2027-02-01', 123.992484874844, 1.852874028817, 5),
  (61, '2026-03-01', '2027-03-01', 123.992484874844, 1.852874028817, 5),
  (62, '2026-04-01', '2027-04-01', 124.599101728439, 1.852874028817, 5),
  (63, '2026-05-01', '2027-05-01', 125.81233543563, 1.852874028817, 5),
  (64, '2026-06-01', '2027-06-01', 128.29946453537, 1.852874028817, 5),
  (65, '2026-07-01', '2027-07-01', 128.906081388965, 1.852874028817, 5),
  (66, '2026-08-01', '2027-08-01', 129.51269824256, 1.852874028817, 5),
  (67, '2026-09-01', '2027-09-01', 129.51269824256, 1.852874028817, 5),
  (68, '2026-10-01', '2027-10-01', 130.119315096155, 1.852874028817, 5),
  (69, '2026-11-01', '2027-11-01', 130.119315096155, 1.852874028817, 5),
  (71, '2026-01-01', '2027-01-01', 116.860622379611, 1.852874028817, 4),
  (72, '2026-02-01', '2027-02-01', 116.860622379611, 1.852874028817, 4),
  (73, '2026-03-01', '2027-03-01', 116.860622379611, 1.852874028817, 4),
  (74, '2026-04-01', '2027-04-01', 116.860622379611, 1.852874028817, 4),
  (75, '2026-05-01', '2027-05-01', 116.860622379611, 1.852874028817, 4),
  (76, '2026-06-01', '2027-06-01', 116.860622379611, 1.852874028817, 4),
  (77, '2026-07-01', '2027-07-01', 116.860622379611, 1.852874028817, 4),
  (78, '2026-08-01', '2027-08-01', 116.860622379611, 1.852874028817, 4),
  (79, '2026-09-01', '2027-09-01', 116.860622379611, 1.852874028817, 4),
  (80, '2026-10-01', '2027-10-01', 117.421054905132, 2.102874028817, 4),
  (81, '2026-11-01', '2027-11-01', 117.14734615244, 2.102874028817, 4),
  (83, '2026-01-01', '2027-01-01', 111.636759193609, 2.102874028817, 3),
  (84, '2026-02-01', '2027-02-01', 111.532328174718, 2.102874028817, 3),
  (85, '2026-03-01', '2027-03-01', 111.532328174718, 2.102874028817, 3),
  (86, '2026-04-01', '2027-04-01', 111.532328174718, 2.102874028817, 3),
  (87, '2026-05-01', '2027-05-01', 111.532328174718, 2.102874028817, 3),
  (88, '2026-06-01', '2027-06-01', 112.066328174718, 2.602874028817, 3),
  (89, '2026-07-01', '2027-07-01', 112.066328174718, 2.602874028817, 3),
  (90, '2026-08-01', '2027-08-01', 112.066328174718, 2.602874028817, 3),
  (91, '2026-09-01', '2027-09-01', 111.804000627493, 2.602874028817, 3),
  (92, '2026-10-01', '2027-10-01', 111.804000627493, 2.602874028817, 3),
  (93, '2026-11-01', '2027-11-01', 111.804000627493, 2.602874028817, 3),
  (95, '2026-01-01', '2027-01-01', 106.55, 2.602874028817, 2),
  (96, '2026-02-01', '2027-02-01', 106.55, 2.602874028817, 2),
  (97, '2026-03-01', '2027-03-01', 106.55, 2.602874028817, 2),
  (98, '2026-04-01', '2027-04-01', 106.55, 2.602874028817, 2),
  (99, '2026-05-01', '2027-05-01', 106.35, 2.602874028817, 2),
  (100, '2026-06-01', '2027-06-01', 106.25, 2.602874028817, 2),
  (101, '2026-07-01', '2027-07-01', 106.25, 2.602874028817, 2),
  (102, '2026-08-01', '2027-08-01', 106.0, 2.602874028817, 2),
  (103, '2026-09-01', '2027-09-01', 106.0, 2.602874028817, 2),
  (104, '2026-10-01', '2027-10-01', 106.0, 2.602874028817, 2),
  (105, '2026-11-01', '2027-11-01', 105.75, 2.602874028817, 2),
  (107, '2026-01-01', '2027-01-01', 100.0, 5.6, 1),
  (108, '2026-02-01', '2027-02-01', 100.0, 5.6, 1),
  (109, '2026-03-01', '2027-03-01', 100.0, 5.6, 1),
  (110, '2026-04-01', '2027-04-01', 100.0, 5.35, 1),
  (111, '2026-05-01', '2027-05-01', 100.0, 5.35, 1),
  (112, '2026-06-01', '2027-06-01', 100.0, 5.35, 1),
  (113, '2026-07-01', '2027-07-01', 100.0, 5.35, 1),
  (114, '2026-08-01', '2027-08-01', 100.0, 5.35, 1),
  (115, '2026-09-01', '2027-09-01', 100.0, 5.35, 1),
  (116, '2026-10-01', '2027-10-01', 100.0, 5.35, 1),
  (117, '2026-11-01', '2027-11-01', 100.0, 5.35, 1);
-- Only periods STARTING in 2026 change: earlier capitalisation is retained,
-- first-year rates still come from the verified issue offers, and CPI stays non-negative.
UPDATE mf_bond_interest_value SET accrued_interest = ROUND(
  demo_valuation_bond_period.opening_balance * (1 + demo_valuation_bond_period.rate / 100 *
    (mf_bond_interest_value.base_valuation_date - demo_valuation_bond_period.start_date)::numeric /
    (demo_valuation_bond_period.end_date - demo_valuation_bond_period.start_date)) - 100, 12)
FROM demo_valuation_bond_period
WHERE mf_bond_interest_value.bond_issue_id = demo_valuation_bond_period.bond_issue_id
  AND mf_bond_interest_value.base_valuation_date >= demo_valuation_bond_period.start_date
  AND mf_bond_interest_value.base_valuation_date < demo_valuation_bond_period.end_date;
-- Eliminate only sub-grosz numeric precision; the economic curve was solved above.
UPDATE mf_bond_interest_value SET accrued_interest = accrued_interest +
  (149187.63 - (SELECT SUM(mf_bond_position.available_units * (mf_bond_issue.nominal_value + mf_bond_interest_value.accrued_interest))
    FROM mf_bond_position JOIN mf_bond_issue ON mf_bond_issue.id = mf_bond_position.bond_issue_id
    JOIN mf_bond_interest_value ON mf_bond_interest_value.bond_issue_id = mf_bond_issue.id
      AND mf_bond_interest_value.base_valuation_date = '2026-11-13'::date - (mf_bond_position.purchase_date - mf_bond_issue.sale_start_date))) /
  (SELECT mf_bond_position.available_units FROM mf_bond_position WHERE mf_bond_position.bond_issue_id = 1)
WHERE mf_bond_interest_value.bond_issue_id = 1 AND mf_bond_interest_value.base_valuation_date =
  '2026-11-13'::date - (SELECT mf_bond_position.purchase_date - mf_bond_issue.sale_start_date
    FROM mf_bond_position JOIN mf_bond_issue ON mf_bond_issue.id = mf_bond_position.bond_issue_id WHERE mf_bond_issue.id = 1);
END;
$demo_valuation$;
