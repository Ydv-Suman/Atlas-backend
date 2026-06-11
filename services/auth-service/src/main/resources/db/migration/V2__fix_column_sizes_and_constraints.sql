-- Fix username column: entity allows 50 chars but column was 20
ALTER TABLE users ALTER COLUMN username TYPE VARCHAR(50);

-- Fix email column: RFC 5321 allows up to 254 chars
ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(254);

-- Make hashed_password NOT NULL (every user must have a password)
ALTER TABLE users ALTER COLUMN hashed_password SET NOT NULL;
