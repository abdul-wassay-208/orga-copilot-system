# Tenant Flow & User Isolation Explanation

## How User Isolation Works

### **Users are isolated by `tenant_id`, NOT by email.**

The system uses **database-level tenant isolation** through foreign key relationships:

```
Tenant (Organization)
  └── Users (belong to tenant via tenant_id)
       └── Conversations (belong to user)
            └── Messages (belong to conversation)
```

### Isolation Mechanism

1. **Database Structure:**
   - Each `User` has a `tenant_id` column (foreign key to `Tenant` table)
   - Each `Conversation` belongs to a `User` (via `user_id`)
   - Each `Message` belongs to a `Conversation` (via `conversation_id`)
   - **Isolation chain:** Messages → Conversations → Users → Tenants

2. **How Isolation is Enforced:**

   **At Login:**
   ```
   User logs in with email → JWT token generated → 
   On each request: Load user by email → Get tenant_id → 
   All queries filter by tenant_id
   ```

   **At Query Level:**
   - `findByIdAndUser()` - Only finds conversations for that specific user
   - `findByUser()` - Only finds conversations for that user's tenant
   - Additional checks: `conversation.getUser().getTenant().getId().equals(user.getTenant().getId())`

   **At Controller Level:**
   ```java
   User user = userRepository.findByEmail(email);
   // All subsequent queries automatically filter by user.getTenant().getId()
   Conversation conversation = conversationRepository.findByIdAndUser(id, user);
   // This ensures conversation belongs to user's tenant
   ```

3. **Security Guarantees:**
   - ✅ Users can ONLY see their own conversations
   - ✅ Users can ONLY see conversations from their tenant
   - ✅ Tenant Admins can see metrics but NOT message content
   - ✅ Super Admins can manage tenants but NOT read conversations

## Tenant Assignment Flow

### Current Flow (After Updates)

#### 1. **Super Admin Creates Tenant**
```
POST /api/admin/super/tenants
{
  "name": "Acme Corp",
  "domain": "acme"
}
→ Creates tenant with ID (e.g., 1)
```

#### 2. **Super Admin Assigns Users to Tenant**

**Option A: Create User and Assign to Tenant**
```
POST /api/admin/super/users
{
  "email": "john@acme.com",
  "password": "password123",
  "tenantId": 1,
  "role": "EMPLOYEE"
}
→ User created and assigned to tenant 1
```

**Option B: Assign Existing User to Different Tenant**
```
PUT /api/admin/super/users/{userId}/assign-tenant
{
  "tenantId": 2
}
→ User moved from tenant 1 to tenant 2
```

#### 3. **User Login & Isolation**
```
1. User logs in with email: "john@acme.com"
2. System loads user → Gets tenant_id = 1
3. JWT token issued (contains email, not tenant_id for security)
4. On each request:
   - Extract email from JWT
   - Load user → Get tenant_id = 1
   - All queries filter by tenant_id = 1
   - User can ONLY see data from tenant 1
```

#### 4. **User Signup with Domain-Based Tenant Detection** (NEW)
```
POST /api/auth/signup
{
  "email": "user@vinncorp.com",
  "password": "password123"
}
→ System extracts domain: "vinncorp" from email
→ Finds tenant with domain "vinncorp"
→ Assigns user to that tenant automatically
→ If tenant doesn't exist, assigns to "default" tenant
```

**How Domain Extraction Works:**
- `user@vinncorp.com` → extracts `"vinncorp"` → matches tenant with domain `"vinncorp"`
- `user@vinncorp.com` → also tries full domain `"vinncorp.com"` if `"vinncorp"` not found
- `user@acme.example.com` → extracts `"acme"` → matches tenant with domain `"acme"`
- If no matching tenant found → assigns to `"default"` tenant

#### 5. **Tenant Admin Invites Users**
```
POST /api/admin/tenant/users/invite
{
  "email": "jane@acme.com"
}
→ User automatically assigned to Tenant Admin's tenant
→ Cannot assign to different tenant (only Super Admin can)
```

## User Isolation Examples

### Example 1: Two Users, Same Email Domain, Different Tenants

**Scenario:**
- `john@example.com` → Tenant: "Acme Corp" (ID: 1)
- `john@example.com` → Tenant: "Beta Inc" (ID: 2)

**Isolation:**
- ❌ **NOT POSSIBLE** - Email must be unique globally
- ✅ **Solution:** Use different emails or subdomain-based emails
  - `john@acme.example.com` → Tenant: "Acme Corp"
  - `john@beta.example.com` → Tenant: "Beta Inc"

### Example 2: Two Users, Different Tenants

**Scenario:**
- User A: `alice@acme.com` → Tenant ID: 1
- User B: `bob@beta.com` → Tenant ID: 2

**What User A Can See:**
- ✅ Only conversations created by users in Tenant 1
- ✅ Only messages from Tenant 1 conversations
- ❌ Cannot see User B's conversations (Tenant 2)
- ❌ Cannot see any Tenant 2 data

**What User B Can See:**
- ✅ Only conversations created by users in Tenant 2
- ✅ Only messages from Tenant 2 conversations
- ❌ Cannot see User A's conversations (Tenant 1)
- ❌ Cannot see any Tenant 1 data

## Super Admin Capabilities

### What Super Admin CAN Do:
1. ✅ Create new tenants
2. ✅ View all tenants
3. ✅ Update tenant settings (limits, subscription, active status)
4. ✅ Delete tenants
5. ✅ **Create users and assign to specific tenant** (NEW)
6. ✅ **View all users across all tenants** (NEW)
7. ✅ **Assign existing users to different tenants** (NEW)
8. ✅ Create tenant admins for specific tenants

### What Super Admin CANNOT Do:
- ❌ Read user conversations (privacy guarantee)
- ❌ See message content
- ❌ Access tenant-specific data beyond metadata

## Tenant Admin Capabilities

### What Tenant Admin CAN Do:
1. ✅ Invite users (automatically assigned to their tenant)
2. ✅ Remove users (from their tenant only)
3. ✅ View usage metrics (numbers only, no content)
4. ✅ Set usage limits
5. ✅ Manage subscription

### What Tenant Admin CANNOT Do:
- ❌ Assign users to different tenants
- ❌ Read employee conversations
- ❌ See message content
- ❌ Access other tenants' data

## Best Practices

1. **Email Uniqueness:** Emails are globally unique, so use:
   - Subdomain-based emails: `user@company1.example.com`
   - Or ensure each organization uses unique email domains

2. **Tenant Assignment:** 
   - ✅ **Domain-based detection is now active** - Users are automatically assigned based on email domain
   - Super Admin can still manually assign users if needed
   - Users signing up with `@vinncorp.com` will automatically join the "vinncorp" tenant (if it exists)

3. **Security:**
   - Never expose tenant_id in JWT token
   - Always load tenant from database on each request
   - Always verify tenant ownership before data access

