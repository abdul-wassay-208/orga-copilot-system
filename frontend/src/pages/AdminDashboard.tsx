import React, { useState, useEffect } from 'react';
import { useAuth } from '../state/AuthContext';
import { apiClient } from '../api/client';

interface User {
  id: number;
  email: string;
  role: string;
}

interface Metrics {
  messagesThisMonth: number;
  maxMessagesPerMonth: number;
  currentUsers: number;
  maxUsers: number;
  subscriptionPlan: string;
}

export const AdminDashboard: React.FC = () => {
  const { logout } = useAuth();
  const [activeTab, setActiveTab] = useState<'users' | 'metrics' | 'settings' | 'knowledge'>('users');
  const [users, setUsers] = useState<User[]>([]);
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [inviteEmail, setInviteEmail] = useState('');
  const [settings, setSettings] = useState({
    maxUsers: 10,
    maxMessagesPerMonth: 1000,
    subscriptionPlan: 'BASIC'
  });

  useEffect(() => {
    if (activeTab === 'users') {
      loadUsers();
    } else if (activeTab === 'metrics') {
      loadMetrics();
    }
  }, [activeTab]);

  const loadUsers = async () => {
    try {
      setLoading(true);
      const res = await apiClient.get('/admin/tenant/users');
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
      const res = await apiClient.get('/admin/tenant/usage/metrics');
      setMetrics(res.data);
      setSettings({
        maxUsers: res.data.maxUsers,
        maxMessagesPerMonth: res.data.maxMessagesPerMonth,
        subscriptionPlan: res.data.subscriptionPlan
      });
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to load metrics');
    } finally {
      setLoading(false);
    }
  };

  const inviteUser = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setLoading(true);
      await apiClient.post('/admin/tenant/users/invite', { email: inviteEmail });
      setInviteEmail('');
      await loadUsers();
      alert('User invited successfully!');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to invite user');
    } finally {
      setLoading(false);
    }
  };

  const removeUser = async (userId: number) => {
    if (!confirm('Are you sure you want to remove this user?')) return;
    try {
      setLoading(true);
      await apiClient.delete(`/admin/tenant/users/${userId}`);
      await loadUsers();
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to remove user');
    } finally {
      setLoading(false);
    }
  };

  const updateLimits = async () => {
    try {
      setLoading(true);
      await apiClient.put('/admin/tenant/limits', settings);
      alert('Limits updated successfully!');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to update limits');
    } finally {
      setLoading(false);
    }
  };

  const updateSubscription = async (plan: string) => {
    try {
      setLoading(true);
      await apiClient.put('/admin/tenant/subscription', { plan });
      setSettings({ ...settings, subscriptionPlan: plan });
      await loadMetrics();
      alert('Subscription updated successfully!');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to update subscription');
    } finally {
      setLoading(false);
    }
  };

  const uploadKnowledgeBase = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setLoading(true);
      await apiClient.post('/admin/tenant/knowledge-base', { content: 'Knowledge base content' });
      alert('Knowledge base updated successfully!');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to update knowledge base');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="admin-layout">
      <header className="admin-header">
        <h1>Tenant Admin Dashboard</h1>
        <button onClick={logout} className="secondary-button">Logout</button>
      </header>
      <div className="admin-container">
        <nav className="admin-nav">
          <button 
            className={activeTab === 'users' ? 'active' : ''} 
            onClick={() => setActiveTab('users')}
          >
            Users
          </button>
          <button 
            className={activeTab === 'metrics' ? 'active' : ''} 
            onClick={() => setActiveTab('metrics')}
          >
            Usage Metrics
          </button>
          <button 
            className={activeTab === 'settings' ? 'active' : ''} 
            onClick={() => setActiveTab('settings')}
          >
            Settings
          </button>
          <button 
            className={activeTab === 'knowledge' ? 'active' : ''} 
            onClick={() => setActiveTab('knowledge')}
          >
            Knowledge Base
          </button>
        </nav>
        <main className="admin-main">
          {error && <div className="error-text">{error}</div>}
          
          {activeTab === 'users' && (
            <div>
              <h2>Manage Users</h2>
              <form onSubmit={inviteUser} className="admin-form">
                <input
                  type="email"
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  placeholder="Email address"
                  required
                />
                <button type="submit" disabled={loading}>Invite User</button>
              </form>
              <div className="users-list">
                {loading ? (
                  <div>Loading...</div>
                ) : (
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>Email</th>
                        <th>Role</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {users.map((user) => (
                        <tr key={user.id}>
                          <td>{user.email}</td>
                          <td>{user.role}</td>
                          <td>
                            {user.role !== 'TENANT_ADMIN' && (
                              <button 
                                onClick={() => removeUser(user.id)}
                                className="danger-button"
                              >
                                Remove
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </div>
          )}

          {activeTab === 'metrics' && (
            <div>
              <h2>Usage Metrics</h2>
              {loading ? (
                <div>Loading...</div>
              ) : metrics ? (
                <div className="metrics-grid">
                  <div className="metric-card">
                    <h3>Messages This Month</h3>
                    <p className="metric-value">{metrics.messagesThisMonth} / {metrics.maxMessagesPerMonth}</p>
                  </div>
                  <div className="metric-card">
                    <h3>Current Users</h3>
                    <p className="metric-value">{metrics.currentUsers} / {metrics.maxUsers}</p>
                  </div>
                  <div className="metric-card">
                    <h3>Subscription Plan</h3>
                    <p className="metric-value">{metrics.subscriptionPlan}</p>
                  </div>
                </div>
              ) : null}
            </div>
          )}

          {activeTab === 'settings' && (
            <div>
              <h2>Settings</h2>
              <div className="admin-form">
                <label>
                  Max Users
                  <input
                    type="number"
                    value={settings.maxUsers}
                    onChange={(e) => setSettings({ ...settings, maxUsers: parseInt(e.target.value) })}
                  />
                </label>
                <label>
                  Max Messages Per Month
                  <input
                    type="number"
                    value={settings.maxMessagesPerMonth}
                    onChange={(e) => setSettings({ ...settings, maxMessagesPerMonth: parseInt(e.target.value) })}
                  />
                </label>
                <div>
                  <label>Subscription Plan</label>
                  <div className="plan-buttons">
                    {['BASIC', 'PRO', 'ENTERPRISE'].map(plan => (
                      <button
                        key={plan}
                        type="button"
                        className={settings.subscriptionPlan === plan ? 'active' : ''}
                        onClick={() => updateSubscription(plan)}
                      >
                        {plan}
                      </button>
                    ))}
                  </div>
                </div>
                <button onClick={updateLimits} disabled={loading}>
                  Save Limits
                </button>
              </div>
            </div>
          )}

          {activeTab === 'knowledge' && (
            <div>
              <h2>Knowledge Base</h2>
              <form onSubmit={uploadKnowledgeBase} className="admin-form">
                <label>
                  Knowledge Base Content
                  <textarea
                    rows={10}
                    placeholder="Enter knowledge base content..."
                    style={{ width: '100%', padding: '0.5rem', borderRadius: '0.5rem', background: '#020617', color: '#e5e7eb', border: '1px solid #4b5563' }}
                  />
                </label>
                <button type="submit" disabled={loading}>
                  Upload Knowledge Base
                </button>
              </form>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

