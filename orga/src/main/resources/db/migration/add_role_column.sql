-- Add role column to users table if it doesn't exist
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE';

-- Add tenant_id column to users table if it doesn't exist
ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

-- Add foreign key constraint if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'fk_users_tenant'
    ) THEN
        ALTER TABLE users 
        ADD CONSTRAINT fk_users_tenant 
        FOREIGN KEY (tenant_id) REFERENCES tenants(id);
    END IF;
END $$;

