-- Run this SQL script in your PostgreSQL database to add missing columns

-- Add role column to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE';

-- Add tenant_id column to users table  
ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

-- Create tenants table if it doesn't exist
CREATE TABLE IF NOT EXISTS tenants (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    max_users INTEGER NOT NULL DEFAULT 10,
    max_messages_per_month BIGINT NOT NULL DEFAULT 1000,
    is_active BOOLEAN NOT NULL DEFAULT true,
    subscription_plan VARCHAR(50) NOT NULL DEFAULT 'BASIC'
);

-- Add foreign key constraint
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

-- Create a default tenant if it doesn't exist
INSERT INTO tenants (name, domain, created_at) 
SELECT 'Default Organization', 'default', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tenants WHERE domain = 'default');

-- Update existing users to have a tenant (if they don't have one)
UPDATE users 
SET tenant_id = (SELECT id FROM tenants WHERE domain = 'default' LIMIT 1)
WHERE tenant_id IS NULL;

-- Make tenant_id NOT NULL after setting default values
ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;

