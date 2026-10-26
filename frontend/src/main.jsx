import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  ArrowUpRight, Bell, Check, ChevronDown, CircleHelp,
  CreditCard, History, Home, LogOut, ShieldCheck, Sparkles, Store,
  Wallet, X
} from "lucide-react";
import "./style.css";

const money = (amount) => new Intl.NumberFormat("en-US", {
  style: "currency", currency: "USD"
}).format(Number(amount || 0));

async function api(path, options = {}, token = "") {
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.error || payload.message || "Something went wrong");
  return payload;
}

function AuthScreen({ onAuthenticated }) {
  const [mode, setMode] = useState("login");
  const [form, setForm] = useState({ name: "", email: "", phone: "", password: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  function update(event) {
    setForm({ ...form, [event.target.name]: event.target.value });
  }

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const result = await api(`/auth/${mode === "login" ? "login" : "signup"}`, {
        method: "POST",
        body: JSON.stringify(form)
      });
      onAuthenticated(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <div className="auth-decoration decor-one" />
      <div className="auth-decoration decor-two" />
      <header className="auth-top"><Brand /><span><ShieldCheck size={15} /> Secure customer access</span></header>
      <section className="auth-layout">
        <div className="auth-pitch">
          <div className="eyebrow"><Sparkles size={14} /> A calmer way to pay</div>
          <h1>Your money,<br /><em>in good hands.</em></h1>
          <p>One clear place to manage your balance, pay the places you love, and keep track of every transaction.</p>
          <div className="pitch-note"><span className="note-icon"><Wallet size={18} /></span><span><b>One simple wallet</b><small>Everything important, at a glance.</small></span><ArrowUpRight size={16} /></div>
          <div className="auth-footnote">A friendly demo wallet. Payments use your local demo balance only.</div>
        </div>
        <div className="auth-card">
          <div className="auth-card-heading">
            <p className="eyebrow">{mode === "login" ? "WELCOME BACK" : "GET STARTED"}</p>
            <h2>{mode === "login" ? "Sign in to Quickpay" : "Create your account"}</h2>
            <p>{mode === "login" ? "Your wallet is right where you left it." : "Set up your wallet in just a moment."}</p>
          </div>
          <form onSubmit={submit}>
            {mode === "signup" && <>
              <label>Full name<input required name="name" autoComplete="name" maxLength="120" placeholder="e.g. Alex Morgan" value={form.name} onChange={update} /></label>
              <label>Phone number<input required name="phone" autoComplete="tel" maxLength="32" placeholder="+1 (555) 010-2040" value={form.phone} onChange={update} /></label>
            </>}
            <label>Email address<input required type="email" name="email" autoComplete="email" maxLength="254" placeholder="you@example.com" value={form.email} onChange={update} /></label>
            <label>Password<input required type="password" name="password" autoComplete={mode === "login" ? "current-password" : "new-password"} minLength="10" maxLength="72" placeholder={mode === "login" ? "Enter your password" : "At least 10 characters"} value={form.password} onChange={update} /></label>
            {error && <div className="form-error" role="alert">{error}</div>}
            <button className="primary-button auth-submit" disabled={busy}>{busy ? "Please wait…" : mode === "login" ? "Sign in" : "Create account"}<ArrowUpRight size={17} /></button>
          </form>
          <div className="auth-toggle">
            {mode === "login" ? "New to Quickpay?" : "Already have an account?"}
            <button onClick={() => { setMode(mode === "login" ? "signup" : "login"); setError(""); }}>
              {mode === "login" ? "Create an account" : "Sign in"}
            </button>
          </div>
          <div className="auth-legal"><ShieldCheck size={13} /> Your password is securely hashed and never stored as plain text.</div>
        </div>
      </section>
      <footer className="auth-footer"><span>© 2026 Quickpay</span><span>Customer wallet demo <i /> Built for clarity</span></footer>
    </main>
  );
}

function Brand() {
  return <div className="brand"><span className="brand-mark"><span /><span /><span /></span><span>quickpay<span className="brand-dot">.</span></span></div>;
}

const navItems = [
  { id: "overview", label: "Overview", icon: Home },
  { id: "pay", label: "Make a payment", icon: CreditCard },
  { id: "activity", label: "Transactions", icon: History }
];

export function App() {
  const [session, setSession] = useState(() => {
    try { return JSON.parse(localStorage.getItem("quickpay-session") || "null"); }
    catch { return null; }
  });
  const [page, setPage] = useState("overview");
  const [customer, setCustomer] = useState(session?.customer || null);
  const [merchants, setMerchants] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [merchantId, setMerchantId] = useState("");
  const [amount, setAmount] = useState("");
  const [toast, setToast] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(Boolean(session));

  useEffect(() => {
    if (!session?.token) return;
    localStorage.setItem("quickpay-session", JSON.stringify(session));
    refreshData();
  }, [session?.token]);

  async function refreshData() {
    setLoading(true);
    setError("");
    try {
      const [profile, merchantList, activity, alerts] = await Promise.all([
        api("/customers/me", {}, session.token),
        api("/merchants", {}, session.token),
        api("/transactions", {}, session.token),
        api("/notifications", {}, session.token)
      ]);
      setCustomer(profile);
      setMerchants(merchantList);
      setTransactions(activity);
      setNotifications(alerts);
      if (!merchantId && merchantList.length) setMerchantId(String(merchantList[0].id));
    } catch (err) {
      if (err.message.includes("Authentication") || err.message.includes("token")) logout();
      else setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  function authenticated(result) {
    const nextSession = { token: result.token, customer: result.customer };
    setSession(nextSession);
    setCustomer(result.customer);
    localStorage.setItem("quickpay-session", JSON.stringify(nextSession));
  }

  function logout() {
    localStorage.removeItem("quickpay-session");
    setSession(null);
    setCustomer(null);
    setTransactions([]);
    setNotifications([]);
    setPage("overview");
  }

  async function pay(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const result = await api("/payments", {
        method: "POST",
        body: JSON.stringify({ merchantId: Number(merchantId), amount: Number(amount) })
      }, session.token);
      setToast(result.message);
      setAmount("");
      setCustomer((current) => ({ ...current, balance: Number(current.balance) - Number(result.amount) }));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (!session?.token) return <AuthScreen onAuthenticated={authenticated} />;
  const customerName = customer?.name || session.customer?.name || "Customer";
  const firstName = customerName.split(" ")[0];
  const balance = customer?.balance ?? session.customer?.balance ?? 0;

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Brand />
        <div className="sidebar-label">YOUR SPACE</div>
        <nav className="side-nav">
          {navItems.map(({ id, label, icon: Icon }) => <button key={id} className={page === id ? "nav-link active" : "nav-link"} onClick={() => { setPage(id); setError(""); }}>
            <Icon size={17} strokeWidth={1.8} /><span>{label}</span>{id === "activity" && <span className="nav-count">{transactions.length}</span>}
          </button>)}
        </nav>
        <div className="sidebar-bottom">
          <div className="help-card"><div><CircleHelp size={17} /><b>Need a hand?</b></div><p>We're here to help you feel right at home.</p><button onClick={() => setToast("This demo has no live support connection.")}>Visit help center <ArrowUpRight size={13} /></button></div>
          <button className="profile-button"><span className="avatar">{customerName.slice(0, 1).toUpperCase()}</span><span className="profile-copy"><b>{customerName}</b><small>Personal account</small></span><ChevronDown size={15} /></button>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar"><div><span className="breadcrumb">My account</span><span className="crumb-slash">/</span><b>{navItems.find((item) => item.id === page)?.label || "Overview"}</b></div>
          <div className="top-actions"><span className="demo-pill"><i /> Demo wallet</span><button className="icon-button" aria-label="Notifications" onClick={() => setToast(notifications[0]?.message || "You're all caught up.")}><Bell size={18} />{notifications.length > 0 && <i />}</button><button className="logout-button" onClick={logout}><LogOut size={15} /> Sign out</button></div>
        </header>

        <div className="page-content">
          {error && <div className="inline-error" role="alert">{error}<button onClick={() => setError("")} aria-label="Dismiss"><X size={15} /></button></div>}
          {loading && <div className="loading-line">Refreshing your account…</div>}
          {page === "overview" && <Overview firstName={firstName} balance={balance} transactions={transactions} goPay={() => setPage("pay")} goActivity={() => setPage("activity")} />}
          {page === "pay" && <PaymentPage merchants={merchants} merchantId={merchantId} setMerchantId={setMerchantId} amount={amount} setAmount={setAmount} balance={balance} busy={busy} onSubmit={pay} />}
          {page === "activity" && <ActivityPage transactions={transactions} />}
          <footer className="page-footer"><span>Quickpay customer service</span><span><ShieldCheck size={13} /> Your account is protected</span></footer>
        </div>
      </main>
      {toast && <div className="toast" role="status"><span className="toast-check"><Check size={15} /></span><span>{toast}</span><button onClick={() => setToast("")} aria-label="Dismiss"><X size={15} /></button></div>}
    </div>
  );
}

function Overview({ firstName, balance, transactions, goPay, goActivity }) {
  const today = new Intl.DateTimeFormat("en-US", {
    weekday: "long", month: "long", day: "numeric"
  }).format(new Date()).toUpperCase();
  return <section>
    <div className="welcome-row"><div><p className="eyebrow">{today}</p><h1>Good to see you, {firstName}<span className="greeting-dot">.</span></h1><p className="page-subtitle">Here's your wallet, looking good.</p></div><button className="primary-button" aria-label="View transactions" onClick={goPay}><ArrowUpRight size={16} /> Make a payment</button></div>
    <div className="overview-grid">
      <article className="balance-card"><div className="balance-top"><span className="balance-label"><Wallet size={16} /> Available balance</span><span className="balance-badge"><i /> Ready to use</span></div><div className="balance-amount">{money(balance)}</div><div className="balance-bottom"><span>Demo wallet balance</span><span><ShieldCheck size={14} /> Protected account</span></div><div className="balance-watermark"><span /><span /><span /></div></article>
      <article className="quick-card"><div className="quick-card-title"><span className="quick-icon"><ArrowUpRight size={18} /></span><div><b>Pay someone new</b><small>Quick, clear and secure</small></div></div><p>Choose a merchant and make a payment from your demo balance.</p><button className="text-button" onClick={goPay}>Start a payment <ArrowUpRight size={14} /></button></article>
    </div>
    <div className="section-heading"><div><p className="eyebrow">YOUR ACTIVITY</p><h2>Recent transactions</h2></div><button className="subtle-button" onClick={goActivity}>See all <ArrowUpRight size={14} /></button></div>
    <TransactionList transactions={transactions.slice(0, 4)} />
  </section>;
}

function PaymentPage({ merchants, merchantId, setMerchantId, amount, setAmount, balance, busy, onSubmit }) {
  return <section className="payment-section">
    <div className="page-heading"><p className="eyebrow">PAY WITH YOUR WALLET</p><h1>Make a payment<span className="greeting-dot">.</span></h1><p className="page-subtitle">A few details and you're all set.</p></div>
    <div className="payment-layout">
      <form className="payment-card" onSubmit={onSubmit}>
        <label>Choose a merchant<select required value={merchantId} onChange={(event) => setMerchantId(event.target.value)}><option value="" disabled>Select a merchant</option>{merchants.map((merchant) => <option key={merchant.id} value={merchant.id}>{merchant.name} · {merchant.category}</option>)}</select></label>
        <label>Payment amount<div className="amount-input"><span>$</span><input required type="number" min="1.00" max="50000" step="0.01" placeholder="0.00" value={amount} onChange={(event) => setAmount(event.target.value)} /></div></label>
        <div className="available-note"><span>Available to spend</span><b>{money(balance)}</b></div>
        <button className="primary-button pay-submit" disabled={busy || !merchants.length}>{busy ? "Processing…" : "Review & pay"}<ArrowUpRight size={16} /></button>
        <p className="form-disclaimer"><ShieldCheck size={14} /> This demo uses your local wallet balance. No external payment provider is connected.</p>
      </form>
      <aside className="payment-aside"><span className="aside-icon"><CreditCard size={19} /></span><h3>Clear from start to finish</h3><p>Your payment and updated balance appear in your activity as soon as it's complete.</p><div className="aside-separator" /><div className="aside-point"><Check size={14} /><span>Securely authenticated</span></div><div className="aside-point"><Check size={14} /><span>Instant confirmation</span></div><div className="aside-point"><Check size={14} /><span>Easy-to-find history</span></div></aside>
    </div>
  </section>;
}

function ActivityPage({ transactions }) {
  return <section><div className="page-heading"><p className="eyebrow">YOUR WALLET, AT A GLANCE</p><h1>Transactions<span className="greeting-dot">.</span></h1><p className="page-subtitle">A clear record of everything you've paid.</p></div><div className="activity-card"><div className="activity-card-head"><div><b>All activity</b><small>{transactions.length} {transactions.length === 1 ? "transaction" : "transactions"}</small></div><span className="activity-filter">Recent first <ChevronDown size={14} /></span></div><TransactionList transactions={[...transactions].reverse()} /></div></section>;
}

function TransactionList({ transactions }) {
  if (!transactions.length) return <div className="empty-state"><span><History size={21} /></span><b>No transactions yet</b><p>When you make a payment, you'll find all the details here.</p></div>;
  return <div className="transaction-list">{transactions.map((transaction) => <div className="transaction-row" key={transaction.id || transaction.paymentId}><span className="transaction-merchant-icon"><Store size={17} /></span><span className="transaction-name"><b>{transaction.merchantName}</b><small>{new Date(transaction.createdAt || Date.now()).toLocaleString(undefined, { month: "short", day: "numeric", year: "numeric", hour: "numeric", minute: "2-digit" })}</small></span><span className="transaction-status"><i /> {transaction.status?.toLowerCase() || "completed"}</span><span className="transaction-amount">−{money(transaction.amount)}</span></div>)}</div>;
}

const rootElement = document.getElementById("root");
if (rootElement) createRoot(rootElement).render(<App />);
