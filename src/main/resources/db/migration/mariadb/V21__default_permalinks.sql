-- A default permalink for every employer that has none (spec 3.13, 7.23).
--
-- New employers get one with their `all` feed; this gives it to the ones created
-- before. Named 'Default', publishing the `all` feed if the employer still has it,
-- and without a domain, which the back-office then asks for. No employer has a
-- permalink named 'Default' here, since only employers with none are touched.

INSERT INTO permalinks (id, created_at, last_modified_at, employer_id, name, description, feed_id)
SELECT UUID(), NOW(6), NOW(6), e.id, 'Default', NULL,
       (SELECT f.id FROM feeds f WHERE f.employer_id = e.id AND f.slug = 'all'
         ORDER BY f.created_at, f.id LIMIT 1)
  FROM employers e
 WHERE NOT EXISTS (SELECT 1 FROM permalinks p WHERE p.employer_id = e.id);
