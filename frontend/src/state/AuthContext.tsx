import React, { createContext, useContext, useEffect, useState } from 'react';
import { TOKEN_KEY } from '../constants';
import { apiClient } from '../api/client';

interface AuthContextValue {
  token: string | null;
  role: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  login: (token: string, role?: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const ROLE_KEY = 'user-role';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));
  const [role, setRole] = useState<string | null>(() => localStorage.getItem(ROLE_KEY));
  const [loading, setLoading] = useState(true);

  // Fetch user info on mount if token exists
  useEffect(() => {
    const fetchUserInfo = async () => {
      const storedToken = localStorage.getItem(TOKEN_KEY);
      if (storedToken) {
        try {
          const res = await apiClient.get('/auth/me');
          setRole(res.data.role);
          localStorage.setItem(ROLE_KEY, res.data.role);
        } catch (err) {
          // Token might be invalid, clear it
          localStorage.removeItem(TOKEN_KEY);
          localStorage.removeItem(ROLE_KEY);
          setToken(null);
          setRole(null);
        }
      }
      setLoading(false);
    };

    fetchUserInfo();
  }, []);

  useEffect(() => {
    if (token) {
      localStorage.setItem(TOKEN_KEY, token);
    } else {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(ROLE_KEY);
    }
  }, [token]);

  useEffect(() => {
    if (role) {
      localStorage.setItem(ROLE_KEY, role);
    }
  }, [role]);

  const login = (newToken: string, newRole?: string) => {
    setToken(newToken);
    if (newRole) {
      setRole(newRole);
    }
  };

  const logout = () => {
    setToken(null);
    setRole(null);
  };

  const value: AuthContextValue = {
    token,
    role,
    isAuthenticated: !!token,
    loading,
    login,
    logout
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextValue => {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
};


