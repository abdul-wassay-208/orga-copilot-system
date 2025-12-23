import React, { useState, useEffect } from 'react';
import { useAuth } from '../state/AuthContext';
import { apiClient } from '../api/client';

interface Tenant {
  id: number;
  name: string;
  domain: string;
  createdAt: string;
  isActive: boolean;
  subscriptionPlan: string;
  maxUsers: number;
  maxMessagesPerMonth: number;
}

interface Metrics {
  totalTenants: number;
  activeTenants: number;
  totalUsers: number;
}

interface User {
  id: number;
  email: string;
  role: string;
  tenantId: number;
  tenantName: string;
}

export const SuperAdminDashboard: React.FC = () => {
  const { logout } = useAuth();
  const [activeTab, setActiveTab] = useState<'tenants' | 'users' | 'metrics' | 'create'>('tenants');
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [newTenant, setNewTenant] = useState({ name: '', domain: '', adminEmail: '', adminPassword: '' });
  const [newUser, setNewUser] = useState({ email: '', password: '', tenantId: '', role: 'EMPLOYEE' });
  const [assigningTenant, setAssigningTenant] = useState<{ userId: number; tenantId: string } | null>(null);

  useEffect(() => {
    if (activeTab === 'tenants') {
      loadTenants();
    } else if (activeTab === 'users') {
      loadUsers();
    } else if (activeTab === 'metrics') {
      loadMetrics();
    }
  }, [activeTab]);

  const loadTenants = async () => {
    try {
      setLoading(true);
      const res = await apiClient.get('/admin/super/tenants');
      setTenants(res.data);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to load tenants');
    } finally {
      setLoading(false);
    }
  };

  const loadUsers = async () => {
    try {
      setLoading(true);
      const res = await apiClient.get('/admin/super/users');
      setUsers(res.data);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to load users');
    } finally {
      setLoading(false);
    }
  };

  const loadMetrics = async () => {
    try {
      setLoading(true);
      const res = await apiClient.get('/admin/super/metrics');
      setMetrics(res.data);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to load metrics');
    } finally {
      setLoading(false);
    }
  };

  const createTenant = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setLoading(true);
      const res = await apiClient.post('/admin/super/tenants', {
        name: newTenant.name,
        domain: newTenant.domain
      });
      
      // Create tenant admin
      if (newTenant.adminEmail && newTenant.adminPassword) {
        await apiClient.post(`/admin/super/tenants/${res.data.id}/admin`, {
          email: newTenant.adminEmail,
          password: newTenant.adminPassword
        });
      }
      
      setNewTenant({ name: '', domain: '', adminEmail: '', adminPassword: '' });
      await loadTenants();
      setActiveTab('tenants');
      alert('Tenant created successfully!');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to create tenant');
    } finally {
      setLoading(false);
    }
  };

  const updateTenant = async (tenantId: number, updates: Partial<Tenant>) => {
    try {
      setLoading(true);
      await apiClient.put(`/admin/super/tenants/${tenantId}`, updates);
      await loadTenants();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to update tenant');
    } finally {
      setLoading(false);
    }
  };

  const deleteTenant = async (tenantId: number) => {
    if (!confirm('Are you sure you want to delete this tenant? This will delete all associated data.')) return;
    try {
      setLoading(true);
      await apiClient.delete(`/admin/super/tenants/${tenantId}`);
      await loadTenants();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to delete tenant');
    } finally {
      setLoading(false);
    }
  };

  const createUser = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setLoading(true);
      await apiClient.post('/admin/super/users', {
        email: newUser.email,
        password: newUser.password,
        tenantId: newUser.tenantId,
        role: newUser.role
      });
      setNewUser({ email: '', password: '', tenantId: '', role: 'EMPLOYEE' });
      await loadUsers();
      setActiveTab('users');
      alert('User created successfully!');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to create user');
    } finally {
      setLoading(false);
    }
  };

  const assignUserToTenant = async (userId: number, tenantId: number) => {
    try {
      setLoading(true);
      await apiClient.put(`/admin/super/users/${userId}/assign-tenant`, { tenantId });
      await loadUsers();
      setAssigningTenant(null);
      alert('User assigned to tenant successfully!');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to assign user to tenant');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="admin-layout">
      <header className="admin-header">
        <h1>Super Admin Dashboard</h1>
        <button onClick={logout} className="secondary-button">Logout</button>
      </header>
      <div className="admin-container">
        <nav className="admin-nav">
          <button 
            className={activeTab === 'tenants' ? 'active' : ''} 
            onClick={() => setActiveTab('tenants')}
          >
            Tenants
          </button>
          <button 
            className={activeTab === 'users' ? 'active' : ''} 
            onClick={() => setActiveTab('users')}
          >
            Users
          </button>
          <button 
            className={activeTab === 'create' ? 'active' : ''} 
            onClick={() => setActiveTab('create')}
          >
            Create Tenant
          </button>
          <button 
            className={activeTab === 'metrics' ? 'active' : ''} 
            onClick={() => setActiveTab('metrics')}
          >
            System Metrics
          </button>
        </nav>
        <main className="admin-main">
          {error && <div className="error-text">{error}</div>}
          
          {activeTab === 'tenants' && (
            <div>
              <h2>Manage Tenants</h2>
              {loading ? (
                <div>Loading...</div>
              ) : (
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Name</th>
                      <th>Domain</th>
                      <th>Plan</th>
                      <th>Status</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {tenants.map((tenant) => (
                      <tr key={tenant.id}>
                        <td>{tenant.name}</td>
                        <td>{tenant.domain}</td>
                        <td>{tenant.subscriptionPlan}</td>
                        <td>{tenant.isActive ? 'Active' : 'Inactive'}</td>
                        <td>
                          <button 
                            onClick={() => updateTenant(tenant.id, { isActive: !tenant.isActive })}
                            className="secondary-button"
                          >
                            {tenant.isActive ? 'Deactivate' : 'Activate'}
                          </button>
                          <button 
                            onClick={() => deleteTenant(tenant.id)}
                            className="danger-button"
                          >
                            Delete
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}

          {activeTab === 'users' && (
            <div>
              <h2>Manage Users</h2>
              <div style={{ marginBottom: '2rem' }}>
                <h3>Create New User</h3>
                <form onSubmit={createUser} className="admin-form">
                  <label>
                    Email
                    <input
                      type="email"
                      value={newUser.email}
                      onChange={(e) => setNewUser({ ...newUser, email: e.target.value })}
                      required
                    />
                  </label>
                  <label>
                    Password
                    <input
                      type="password"
                      value={newUser.password}
                      onChange={(e) => setNewUser({ ...newUser, password: e.target.value })}
                      required
                    />
                  </label>
                  <label>
                    Tenant
                    <select
                      value={newUser.tenantId}
                      onChange={(e) => setNewUser({ ...newUser, tenantId: e.target.value })}
                      required
                      style={{ padding: '0.6rem 0.8rem', borderRadius: '0.6rem', background: '#020617', color: '#e5e7eb', border: '1px solid #4b5563' }}
                    >
                      <option value="">Select Tenant</option>
                      {tenants.map(t => (
                        <option key={t.id} value={t.id}>{t.name} ({t.domain})</option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Role
                    <select
                      value={newUser.role}
                      onChange={(e) => setNewUser({ ...newUser, role: e.target.value })}
                      style={{ padding: '0.6rem 0.8rem', borderRadius: '0.6rem', background: '#020617', color: '#e5e7eb', border: '1px solid #4b5563' }}
                    >
                      <option value="EMPLOYEE">Employee</option>
                      <option value="TENANT_ADMIN">Tenant Admin</option>
                    </select>
                  </label>
                  <button type="submit" disabled={loading}>
                    Create User
                  </button>
                </form>
              </div>
              <h3>All Users</h3>
              {loading ? (
                <div>Loading...</div>
              ) : (
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Email</th>
                      <th>Role</th>
                      <th>Current Tenant</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {users.map((user) => (
                      <tr key={user.id}>
                        <td>{user.email}</td>
                        <td>{user.role}</td>
                        <td>{user.tenantName}</td>
                        <td>
                          {user.role !== 'SUPER_ADMIN' && (
                            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                              <select
                                value={assigningTenant?.userId === user.id ? assigningTenant.tenantId : ''}
                                onChange={(e) => {
                                  if (e.target.value) {
                                    assignUserToTenant(user.id, parseInt(e.target.value));
                                  }
                                }}
                                style={{ padding: '0.4rem', borderRadius: '0.4rem', background: '#020617', color: '#e5e7eb', border: '1px solid #4b5563', fontSize: '0.85rem' }}
                              >
                                <option value="">Assign to...</option>
                                {tenants.map(t => (
                                  <option key={t.id} value={t.id} disabled={t.id === user.tenantId}>
                                    {t.name} {t.id === user.tenantId ? '(current)' : ''}
                                  </option>
                                ))}
                              </select>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}

          {activeTab === 'create' && (
            <div>
              <h2>Create New Tenant</h2>
              <form onSubmit={createTenant} className="admin-form">
                <label>
                  Tenant Name
                  <input
                    type="text"
                    value={newTenant.name}
                    onChange={(e) => setNewTenant({ ...newTenant, name: e.target.value })}
                    required
                  />
                </label>
                <label>
                  Domain
                  <input
                    type="text"
                    value={newTenant.domain}
                    onChange={(e) => setNewTenant({ ...newTenant, domain: e.target.value })}
                    required
                  />
                </label>
                <label>
                  Admin Email (optional)
                  <input
                    type="email"
                    value={newTenant.adminEmail}
                    onChange={(e) => setNewTenant({ ...newTenant, adminEmail: e.target.value })}
                  />
                </label>
                <label>
                  Admin Password (optional)
                  <input
                    type="password"
                    value={newTenant.adminPassword}
                    onChange={(e) => setNewTenant({ ...newTenant, adminPassword: e.target.value })}
                  />
                </label>
                <button type="submit" disabled={loading}>
                  Create Tenant
                </button>
              </form>
            </div>
          )}

          {activeTab === 'metrics' && (
            <div>
              <h2>System Metrics</h2>
              {loading ? (
                <div>Loading...</div>
              ) : metrics ? (
                <div className="metrics-grid">
                  <div className="metric-card">
                    <h3>Total Tenants</h3>
                    <p className="metric-value">{metrics.totalTenants}</p>
                  </div>
                  <div className="metric-card">
                    <h3>Active Tenants</h3>
                    <p className="metric-value">{metrics.activeTenants}</p>
                  </div>
                  <div className="metric-card">
                    <h3>Total Users</h3>
                    <p className="metric-value">{metrics.totalUsers}</p>
                  </div>
                </div>
              ) : null}
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

