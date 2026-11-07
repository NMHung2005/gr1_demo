-- Explicit mappings approved by the user in this session; do not infer by username.
START TRANSACTION;
UPDATE users SET keycloak_id='8d73fdf9-3016-49e9-b7c7-704a42da3e85'
WHERE id=5 AND username='reader01'
  AND (keycloak_id IS NULL OR keycloak_id='8d73fdf9-3016-49e9-b7c7-704a42da3e85');
UPDATE users SET keycloak_id='bbcf643e-fa39-426b-b8f6-cf08b6168416'
WHERE id=3 AND username IN ('admin', 'librarian01')
  AND (keycloak_id IS NULL OR keycloak_id='bbcf643e-fa39-426b-b8f6-cf08b6168416');
COMMIT;
