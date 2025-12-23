import React, { useState } from 'react';
import { apiClient } from '../api/client';

export const AdminSetup: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('admin123');
  const [role, setRole] = useState<'TENANT_ADMIN' | 'SUPER_ADMIN'>('TENANT_ADMIN');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const createAdmin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setMessage(null);

    try {
      const endpoint = role === 'TENANT_ADMIN' 
        ? '/admin/setup/create-tenant-admin'
        : '/admin/setup/create-super-admin';
      
      const res = await apiClient.post(endpoint, {
        email,
        password,
        tenantName: role === 'TENANT_ADMIN' ? 'Test Organization' : 'Platform',
        tenantDomain: role === 'TENANT_ADMIN' ? 'test' : 'platform'
      });

      setMessage(`✅ ${res.data.message}\nEmail: ${res.data.email}\nPassword: ${res.data.password}\nRole: ${res.data.role}`);
    } catch (err: any) {
      setMessage(`❌ Error: ${err?.response?.data?.message || 'Failed to create admin'}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card">
        <h1>Create Admin User</h1>
        <p className="subtitle">Create a Tenant Admin or Super Admin for testing</p>
        <form onSubmit={createAdmin} className="auth-form">
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@example.com"
              required
            />
          </label>
          <label>
            Password
            <input
              type="text"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="admin123"
            />
          </label>
          <label>
            Role
            <select
              value={role}
              onChange={(e) => setRole(e.target.value as 'TENANT_ADMIN' | 'SUPER_ADMIN')}
              style={{ padding: '0.6rem 0.8rem', borderRadius: '0.6rem', background: '#020617', color: '#e5e7eb', border: '1px solid #4b5563' }}
            >
              <option value="TENANT_ADMIN">Tenant Admin</option>
              <option value="SUPER_ADMIN">Super Admin</option>
            </select>
          </label>
          {message && (
            <div className={message.startsWith('✅') ? 'success-text' : 'error-text'}>
              <pre style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{message}</pre>
            </div>
          )}
          <button type="submit" disabled={loading}>
            {loading ? 'Creating...' : 'Create Admin'}
          </button>
        </form>
        <p className="switch-link">
          <a href="/login">Go to Login</a>
        </p>
      </div>
    </div>
  );
};

