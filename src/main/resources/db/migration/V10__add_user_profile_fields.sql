-- 모바일 목데이터(MockUsersUtil) 형식에 맞춰 사용자 프로필 필드 추가.
-- 비밀번호는 기존대로 password_hash(BCrypt) 유지. 평문 저장하지 않음.
ALTER TABLE users ADD COLUMN IF NOT EXISTS name       TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS department TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone      TEXT;

-- role enum 확장: 검사원(INSPECTOR) 추가.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
  CHECK (role IN ('OPERATOR','ENGINEER','ADMIN','INSPECTOR'));
