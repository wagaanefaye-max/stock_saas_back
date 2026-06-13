-- ============================================================
-- Créer 100 articles pour une entreprise donnée
-- Remplace 5 par l'ID réel : SELECT id, name FROM td_companies;
-- ============================================================

BEGIN;

-- 1) Paramètre entreprise
WITH params AS (
    SELECT 2 AS company_id   -- <<< ID ENTREPRISE ICI
),

-- 2) Insertion des 100 produits
     inserted AS (
INSERT INTO td_products (
    company_id,
    name,
    sku,
    description,
    price,
    purchase_price,
    status_code,
    category_code,
    created_at,
    updated_at,
    is_deleted
)
SELECT
    p.company_id,
    LEFT('Article ' || LPAD(g.n::text, 3, '0'), 30),
    'DEMO-ART-' || LPAD(g.n::text, 3, '0'),
    'Article de démonstration n°' || g.n,
    (500 + (g.n * 25))::numeric(15,2),          -- prix vente
    (300 + (g.n * 15))::numeric(15,2),          -- prix achat
    'En stock',
    (SELECT code FROM tp_category WHERE is_active = true ORDER BY code LIMIT 1),
    NOW(),
    NOW(),
    false
FROM params p
    CROSS JOIN generate_series(1, 100) AS g(n)
    RETURNING id, sku, created_at
    )

-- 3) Génération des références (format REF_YYYYMMDD_SKU_ID)
UPDATE td_products prod
SET reference = 'REF_'
    || to_char(i.created_at, 'YYYYMMDD')
    || '_'
    || LEFT(UPPER(REGEXP_REPLACE(i.sku, '[^A-Z0-9]', '', 'g')), 12)
    || '_'
    || i.id
FROM inserted i
WHERE prod.id = i.id;

-- 4) Stock initial dans le 1er entrepôt actif de l'entreprise
INSERT INTO td_stock_levels (
    product_id,
    warehouse_id,
    quantity,
    min_threshold,
    max_threshold,
    created_at,
    updated_at,
    is_deleted
)
SELECT
    prod.id,
    wh.id,
    50::numeric(15,2),
    5::numeric(15,2),
    NULL,
    NOW(),
    NOW(),
    false
FROM td_products prod
         JOIN (
    SELECT id
    FROM td_warehouses
    WHERE company_id = 5          -- <<< même ID entreprise
      AND is_deleted = false
    ORDER BY id
        LIMIT 1
) wh ON true
WHERE prod.company_id = 5        -- <<< même ID entreprise
  AND prod.sku LIKE 'DEMO-ART-%'
  AND NOT EXISTS (
    SELECT 1
    FROM td_stock_levels sl
    WHERE sl.product_id = prod.id
      AND sl.warehouse_id = wh.id
);

COMMIT;