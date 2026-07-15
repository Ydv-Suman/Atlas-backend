CREATE TABLE device_tokens (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               fcm_token TEXT NOT NULL,
                               device_os VARCHAR(10) NOT NULL,
                               created_at TIMESTAMP DEFAULT NOW(),
                               updated_at TIMESTAMP DEFAULT NOW(),
                               UNIQUE(user_id, device_os)
);