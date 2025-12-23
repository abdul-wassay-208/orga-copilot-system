import React, { useState, useEffect } from 'react';
import { useAuth } from '../state/AuthContext';
import { chatClient } from '../api/client';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

interface ChatMessage {
  id: number;
  from: 'user' | 'bot';
  text: string;
}

interface Conversation {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
}

const SUGGESTED_PROMPTS = [
  "How do I handle workplace conflict?",
  "I'm feeling overwhelmed at work. What should I do?",
  "How can I improve my leadership skills?",
  "What are effective communication strategies?",
  "How do I manage work-life balance?"
];

// Safe markdown renderer component - simplified to prevent crashes
const SafeMarkdown: React.FC<{ content: string }> = ({ content }) => {
  if (!content || typeof content !== 'string') {
    return <p>No content to display.</p>;
  }

  // For now, render as plain text with preserved formatting
  // This prevents any ReactMarkdown crashes
  const formattedContent = content
    .split('\n')
    .map((line, idx) => {
      // Handle headers
      if (line.startsWith('# ')) {
        return <h1 key={idx} style={{ marginTop: '1.5em', marginBottom: '0.75em', fontSize: '1.5em', fontWeight: 600, borderBottom: '1px solid rgba(148, 163, 184, 0.3)', paddingBottom: '0.5em' }}>{line.substring(2)}</h1>;
      }
      if (line.startsWith('## ')) {
        return <h2 key={idx} style={{ marginTop: '1.25em', marginBottom: '0.75em', fontSize: '1.3em', fontWeight: 600 }}>{line.substring(3)}</h2>;
      }
      if (line.startsWith('### ')) {
        return <h3 key={idx} style={{ marginTop: '1em', marginBottom: '0.5em', fontSize: '1.1em', fontWeight: 600 }}>{line.substring(4)}</h3>;
      }
      // Handle horizontal rules
      if (line.trim() === '---' || line.trim() === '***') {
        return <hr key={idx} style={{ border: 'none', borderTop: '1px solid rgba(148, 163, 184, 0.3)', margin: '0.5em 0' }} />;
      }
      // Regular paragraphs
      if (line.trim()) {
        // Simple bold detection
        const parts = line.split(/(\*\*[^*]+\*\*)/g);
        return (
          <p key={idx} style={{ margin: '0em 0', lineHeight: '1.5' }}>
            {parts.map((part, pIdx) => {
              if (part.startsWith('**') && part.endsWith('**')) {
                return <strong key={pIdx} style={{ fontWeight: 600 }}>{part.slice(2, -2)}</strong>;
              }
              return <span key={pIdx}>{part}</span>;
            })}
          </p>
        );
      }
      return <br key={idx} />;
    });

  return <div className="markdown-content">{formattedContent}</div>;
};

export const ChatPage: React.FC = () => {
  const { logout } = useAuth();
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [showSidebar, setShowSidebar] = useState(true);
  const [loadingConversations, setLoadingConversations] = useState(false);
  const [darkMode, setDarkMode] = useState(() => {
    const saved = localStorage.getItem('darkMode');
    return saved ? saved === 'true' : true;
  });
  const chatWindowRef = React.useRef<HTMLDivElement>(null);
  const messagesEndRef = React.useRef<HTMLDivElement>(null);

  // Helper function to scroll to bottom - instant like ChatGPT
  const scrollToBottom = React.useCallback((instant = false) => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: instant ? 'auto' : 'smooth', block: 'end' });
    } else if (chatWindowRef.current) {
      const element = chatWindowRef.current;
      // Instant scroll - no smooth behavior
      element.scrollTop = element.scrollHeight;
    }
  }, []);

  useEffect(() => {
    loadConversations();
  }, []);

  // Auto-scroll to bottom when messages change - instant like ChatGPT
  useEffect(() => {
    // Immediate scroll (instant, like ChatGPT)
    scrollToBottom(true);
    
    // Also ensure scroll after React has updated DOM
    const timeoutId = setTimeout(() => {
      scrollToBottom(true);
    }, 0);
    
    return () => clearTimeout(timeoutId);
  }, [messages, loading, scrollToBottom]);

  // Use MutationObserver to catch any DOM changes and scroll (like ChatGPT)
  useEffect(() => {
    if (!chatWindowRef.current) return;

    const observer = new MutationObserver(() => {
      scrollToBottom(true);
    });

    observer.observe(chatWindowRef.current, {
      childList: true,
      subtree: true,
      attributes: false,
      characterData: false
    });

    return () => observer.disconnect();
  }, [scrollToBottom]);

  const loadConversations = async () => {
    try {
      setLoadingConversations(true);
      const res = await chatClient.get('/chat/conversations');
      setConversations(res.data);
    } catch (err) {
      console.error('Failed to load conversations:', err);
    } finally {
      setLoadingConversations(false);
    }
  };

  const startNewChat = async () => {
    try {
      const res = await chatClient.post('/chat/conversations');
      setConversationId(res.data.id);
      setMessages([]);
      setError(null);
      await loadConversations();
    } catch (err) {
      console.error('Failed to create conversation:', err);
    }
  };

  const loadConversation = async (id: number) => {
    try {
      setLoading(true);
      const res = await chatClient.get(`/chat/conversations/${id}`);
      const conversation = res.data;
      setConversationId(conversation.id);
      
      const loadedMessages: ChatMessage[] = conversation.messages.map((m: any) => ({
        id: m.id,
        from: m.role === 'USER' ? 'user' : 'bot',
        text: m.content
      }));
      setMessages(loadedMessages);
      setError(null);
    } catch (err) {
      console.error('Failed to load conversation:', err);
      setError('Failed to load conversation');
    } finally {
      setLoading(false);
    }
  };

  const deleteConversation = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!confirm('Are you sure you want to delete this conversation?')) return;
    
    try {
      await chatClient.delete(`/chat/conversations/${id}`);
      if (conversationId === id) {
        setConversationId(null);
        setMessages([]);
      }
      await loadConversations();
    } catch (err) {
      console.error('Failed to delete conversation:', err);
    }
  };

  const copyMessage = (text: string) => {
    navigator.clipboard.writeText(text).then(() => {
      alert('Message copied to clipboard!');
    }).catch(err => {
      console.error('Failed to copy:', err);
    });
  };

  const shareMessage = (text: string) => {
    if (navigator.share) {
      navigator.share({
        text: text,
        title: 'Chat Message'
      }).catch(err => {
        console.error('Failed to share:', err);
      });
    } else {
      // Fallback: copy to clipboard
      copyMessage(text);
    }
  };

  const shareConversation = () => {
    const conversationText = messages.map(m => 
      `${m.from === 'user' ? 'You' : 'Bot'}: ${m.text}`
    ).join('\n\n');
    
    if (navigator.share) {
      navigator.share({
        text: conversationText,
        title: 'Chat Conversation'
      }).catch(err => {
        console.error('Failed to share:', err);
      });
    } else {
      copyMessage(conversationText);
      alert('Conversation copied to clipboard!');
    }
  };

  const exportConversation = () => {
    const conversationText = messages.map(m => 
      `${m.from === 'user' ? 'You' : 'Bot'}: ${m.text}`
    ).join('\n\n');
    
    const blob = new Blob([conversationText], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `conversation-${conversationId || 'new'}-${Date.now()}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const useSuggestedPrompt = (prompt: string) => {
    setInput(prompt);
  };

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', darkMode ? 'dark' : 'light');
    localStorage.setItem('darkMode', darkMode.toString());
  }, [darkMode]);

  const sendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const trimmed = input.trim();
    if (!trimmed) return;

    const userMessage: ChatMessage = {
      id: Date.now(),
      from: 'user',
      text: trimmed
    };
    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    
    // Scroll to bottom immediately after adding user message (instant)
    setTimeout(() => {
      scrollToBottom(true);
    }, 0);

    try {
      setLoading(true);
      const res = await chatClient.post('/chat/ask', { 
        message: trimmed,
        conversationId: conversationId 
      });
      
      if (res.data.conversationId && !conversationId) {
        setConversationId(res.data.conversationId);
      }
      
      console.log('Response received:', res.data);
      const replyText = res.data?.reply || res.data?.answer || res.data?.message || 'No response.';
      const replyString = typeof replyText === 'string' ? replyText : String(replyText);
      
      console.log('Reply text:', replyString.substring(0, 100));
      
      const botMessage: ChatMessage = {
        id: Date.now() + 1,
        from: 'bot',
        text: replyString
      };
      
      setMessages((prev) => {
        const newMessages = [...prev, botMessage];
        console.log('Messages updated, count:', newMessages.length);
        return newMessages;
      });
      
      // Scroll to bottom after bot response (instant)
      setTimeout(() => {
        scrollToBottom(true);
      }, 0);
      
      // Also scroll after a short delay to catch any async rendering
      setTimeout(() => {
        scrollToBottom(true);
      }, 50);
      
      await loadConversations();
    } catch (err: any) {
      console.error('Error sending message:', err);
      const msg = err?.response?.data?.message || err?.message || 'Failed to get response from chatbot.';
      setError(msg);
      // Remove the user message if there was an error
      setMessages((prev) => prev.slice(0, -1));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="chat-layout">
      <header className="chat-header">
        <button 
          className="sidebar-toggle" 
          onClick={() => setShowSidebar(!showSidebar)}
          title="Toggle sidebar"
        >
          ☰
        </button>
        <h1>Chatbot</h1>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <button 
            onClick={() => setDarkMode(!darkMode)}
            className="secondary-button"
            title={darkMode ? 'Switch to light mode' : 'Switch to dark mode'}
          >
            {darkMode ? '☀️' : '🌙'}
          </button>
          {messages.length > 0 && (
            <>
              <button 
                onClick={shareConversation}
                className="secondary-button"
                title="Share conversation"
              >
                🔗 Share
              </button>
              <button 
                onClick={exportConversation}
                className="secondary-button"
                title="Export conversation"
              >
                💾 Export
              </button>
            </>
          )}
          <button onClick={logout} className="secondary-button">
            Logout
          </button>
        </div>
      </header>
      <div className="chat-container">
        {showSidebar && (
          <aside className="chat-sidebar">
            <button onClick={startNewChat} className="new-chat-button">
              + New Chat
            </button>
            <div className="conversations-list">
              <h3>Conversations</h3>
              {loadingConversations ? (
                <div className="loading-text">Loading...</div>
              ) : conversations.length === 0 ? (
                <div className="empty-text">No conversations yet</div>
              ) : (
                <ul>
                  {conversations.map((conv) => (
                    <li
                      key={conv.id}
                      className={conversationId === conv.id ? 'active' : ''}
                      onClick={() => loadConversation(conv.id)}
                    >
                      <div className="conv-title">{conv.title}</div>
                      <div className="conv-meta">
                        {new Date(conv.updatedAt).toLocaleDateString()}
                        <button
                          className="delete-conv"
                          onClick={(e) => deleteConversation(conv.id, e)}
                          title="Delete conversation"
                        >
                          ×
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </aside>
        )}
        <main className="chat-main">
          <div className="chat-window" ref={chatWindowRef}>
            {messages.length === 0 && (
              <div className="chat-empty">
                <div>Start the conversation by typing a message below.</div>
                <div className="suggested-prompts">
                  <h3>Suggested Prompts:</h3>
                  <div className="prompts-grid">
                    {SUGGESTED_PROMPTS.map((prompt, idx) => (
                      <button
                        key={idx}
                        className="prompt-button"
                        onClick={() => useSuggestedPrompt(prompt)}
                      >
                        {prompt}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            )}
            {messages.map((m) => (
              <div key={m.id} className={`chat-bubble ${m.from === 'user' ? 'from-user' : 'from-bot'}`}>
                <div className="message-header">
                  <span className="sender-label">{m.from === 'user' ? 'You' : 'Dr. Jordan Reeves'}</span>
                  <div className="message-actions">
                    <button
                      className="action-button"
                      onClick={() => copyMessage(m.text)}
                      title="Copy message"
                    >
                      📋
                    </button>
                    <button
                      className="action-button"
                      onClick={() => shareMessage(m.text)}
                      title="Share message"
                    >
                      🔗
                    </button>
                  </div>
                </div>
                <div className="message-content">
                  {m.from === 'bot' ? (
                    <SafeMarkdown content={String(m.text || 'No response received.')} />
                  ) : (
                    <p>{String(m.text || '')}</p>
                  )}
                </div>
              </div>
            ))}
            {loading && (
              <div className="chat-bubble from-bot">
                <div className="message-header">
                  <span className="sender-label">Dr. Jordan Reeves</span>
                </div>
                <div className="message-content">
                  <div className="typing-indicator">
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                </div>
              </div>
            )}
            {/* Invisible element at the bottom to scroll to - like ChatGPT */}
            <div ref={messagesEndRef} style={{ height: '1px', width: '100%', flexShrink: 0 }} />
          </div>
          {error && <div className="error-text center">{error}</div>}
          <form onSubmit={sendMessage} className="chat-input-row">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Type your message..."
            />
            <button type="submit" disabled={loading}>
              {loading ? 'Sending...' : 'Send'}
            </button>
          </form>
        </main>
      </div>
    </div>
  );
};


