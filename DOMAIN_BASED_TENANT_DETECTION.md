# Domain-Based Tenant Detection

## Overview

The system now automatically assigns users to tenants based on their email domain during signup. This allows users from the same organization to be automatically grouped together.

## How It Works

### Signup Flow

1. **User signs up** with email: `user@vinncorp.com`
2. **System extracts domain** from email:
   - Extracts `"vinncorp"` from `user@vinncorp.com`
   - Also tries full domain `"vinncorp.com"` as fallback
3. **System finds tenant** with matching domain:
   - Searches for tenant where `domain = "vinncorp"`
   - If found → assigns user to that tenant ✅
   - If not found → assigns to `"default"` tenant (fallback)
4. **User is created** and assigned to the tenant

### Domain Extraction Logic

The `extractDomainFromEmail()` method:
- Extracts the part before the first dot after `@`
- Examples:
  - `user@vinncorp.com` → `"vinncorp"`
  - `user@acme.example.com` → `"acme"`
  - `user@company.co.uk` → `"company"`

### Matching Strategy

The system tries multiple matching strategies:

1. **Primary Match**: Extract domain identifier (e.g., `"vinncorp"`)
   - `user@vinncorp.com` → searches for tenant with domain `"vinncorp"`

2. **Fallback Match**: Try full domain (e.g., `"vinncorp.com"`)
   - If `"vinncorp"` not found, searches for tenant with domain `"vinncorp.com"`

3. **Default Fallback**: Assign to `"default"` tenant
   - If no matching tenant found, user is assigned to default tenant
   - This ensures signup never fails due to missing tenant

## Example Scenarios

### Scenario 1: Tenant Exists, User Signs Up

**Setup:**
- Super Admin creates tenant: `"VinnCorp"` with domain `"vinncorp"`

**User Action:**
- User signs up with: `john@vinncorp.com`

**Result:**
- ✅ User automatically assigned to "VinnCorp" tenant
- ✅ Tenant Admin for "VinnCorp" can see this user in `/api/admin/tenant/users`
- ✅ User can start chatting immediately

### Scenario 2: Tenant Doesn't Exist

**Setup:**
- No tenant with domain `"newcompany"` exists

**User Action:**
- User signs up with: `user@newcompany.com`

**Result:**
- ⚠️ User assigned to `"default"` tenant (fallback)
- ⚠️ Super Admin should create tenant and reassign user later
- ✅ Signup succeeds (doesn't fail)

### Scenario 3: Multiple Domain Formats

**Tenant Setup:**
- Tenant domain: `"vinncorp"` (without .com)

**User Signups:**
- `user@vinncorp.com` → ✅ Matches (extracts `"vinncorp"`)
- `user@vinncorp.org` → ✅ Matches (extracts `"vinncorp"`)
- `user@vinncorp.co.uk` → ✅ Matches (extracts `"vinncorp"`)

**All users assigned to same tenant!**

## Best Practices

### For Super Admins

1. **Create tenants with simple domain identifiers:**
   - ✅ Use `"vinncorp"` instead of `"vinncorp.com"`
   - ✅ This allows matching across different TLDs (.com, .org, .co.uk)

2. **Monitor default tenant:**
   - Check periodically for users in "default" tenant
   - Create appropriate tenants and reassign users

3. **Domain naming:**
   - Use lowercase, no spaces
   - Match the main part of your email domain

### For Tenant Admins

1. **Users will automatically appear** when they sign up with matching email domain
2. **No manual assignment needed** for users with correct email domain
3. **Check user list** regularly to see new signups

## Migration Notes

### Existing Users

- Existing users in "default" tenant remain unchanged
- Super Admin can reassign them using:
  ```
  PUT /api/admin/super/users/{userId}/assign-tenant
  {
    "tenantId": <target_tenant_id>
  }
  ```

### Creating Tenants

When creating a tenant, use the domain identifier (without TLD):
```json
POST /api/admin/super/tenants
{
  "name": "VinnCorp",
  "domain": "vinncorp"  // ✅ Good - matches user@vinncorp.com
}
```

Not:
```json
{
  "name": "VinnCorp",
  "domain": "vinncorp.com"  // ⚠️ Works but less flexible
}
```

## Code Implementation

The domain-based detection is implemented in:
- `AuthController.signup()` - Main signup logic
- `AuthController.extractDomainFromEmail()` - Domain extraction helper

See `orga/src/main/java/com/vinncorp/orga/auth/AuthController.java` for details.


