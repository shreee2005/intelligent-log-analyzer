-- ============================================================
-- CLEAR TEST DATA SCRIPT - Run against PostgreSQL
-- ============================================================
-- Connect to your database first:
-- psql -h localhost -U admin -d loganalyzer

-- 1. Show existing users (to verify what will be deleted)
SELECT id, email, password_hash, roles, created_at FROM users;

-- 2. Show existing projects
SELECT id, name, owner_id, api_key_hash, api_key_active FROM projects;

-- 3. Delete OAuth2 test users (identified by OAUTH_USER_NO_PASSWORD)
-- This removes users created via GitHub/Google OAuth
DELETE FROM user_roles WHERE user_id IN (
    SELECT id FROM users WHERE password_hash = 'OAUTH_USER_NO_PASSWORD'
);

DELETE FROM users WHERE password_hash = 'OAUTH_USER_NO_PASSWORD';

-- 4. Optionally: Delete ALL users and projects (NUCLEAR OPTION)
-- Uncomment below if you want a completely fresh start:
-- DELETE FROM user_roles;
-- DELETE FROM users;
-- DELETE FROM projects;

-- 5. Verify cleanup
SELECT 'Users remaining:' as info, count(*) as count FROM users
UNION ALL
SELECT 'Projects remaining:', count(*) FROM projects;

-- 6. Clear browser localStorage (run in browser console):
-- localStorage.clear();
-- Or specific keys:
-- localStorage.removeItem('token');
-- localStorage.removeItem('email');
-- Object.keys(localStorage).forEach(k => { if(k.startsWith('project_api_key_')) localStorage.removeItem(k); });