import { useState } from "react";
import { getToken, setToken } from "./api";
import type { LoginResponse, Role } from "./types";
import { Login } from "./pages/Login";
import { MyRequests } from "./pages/MyRequests";
import { Approvals } from "./pages/Approvals";
import { Workflows } from "./pages/Workflows";
import { Audit } from "./pages/Audit";

const USER_KEY = "hrflow.user";

function loadUser(): LoginResponse | null {
  const raw = localStorage.getItem(USER_KEY);
  return raw ? (JSON.parse(raw) as LoginResponse) : null;
}

type Tab = "requests" | "approvals" | "workflows" | "audit";

export function App() {
  const [user, setUser] = useState<LoginResponse | null>(
    getToken() ? loadUser() : null
  );
  const [tab, setTab] = useState<Tab>("requests");

  function onLogin(resp: LoginResponse) {
    setToken(resp.token);
    localStorage.setItem(USER_KEY, JSON.stringify(resp));
    setUser(resp);
    setTab("requests");
  }

  function logout() {
    setToken(null);
    localStorage.removeItem(USER_KEY);
    setUser(null);
  }

  if (!user) return <Login onLogin={onLogin} />;

  const isAdmin: boolean = user.role === "HR_ADMIN" || user.role === "SYSTEM_ADMIN";
  const canApprove: boolean = isAdmin || user.role === "MANAGER";

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">HRFlow</div>
        <nav className="tabs">
          <TabButton label="My Requests" active={tab === "requests"} onClick={() => setTab("requests")} />
          {canApprove && (
            <TabButton label="Approvals" active={tab === "approvals"} onClick={() => setTab("approvals")} />
          )}
          {isAdmin && (
            <TabButton label="Workflows" active={tab === "workflows"} onClick={() => setTab("workflows")} />
          )}
          {isAdmin && (
            <TabButton label="Audit Log" active={tab === "audit"} onClick={() => setTab("audit")} />
          )}
        </nav>
        <div className="userbox">
          <span>{user.fullName} · <RoleBadge role={user.role} /></span>
          <button className="link" onClick={logout}>Log out</button>
        </div>
      </header>

      <main className="content">
        {tab === "requests" && <MyRequests />}
        {tab === "approvals" && canApprove && <Approvals isAdmin={isAdmin} />}
        {tab === "workflows" && isAdmin && <Workflows />}
        {tab === "audit" && isAdmin && <Audit />}
      </main>
    </div>
  );
}

function TabButton(props: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button className={props.active ? "tab active" : "tab"} onClick={props.onClick}>
      {props.label}
    </button>
  );
}

export function RoleBadge({ role }: { role: Role }) {
  return <span className={`badge role-${role}`}>{role.replace("_", " ")}</span>;
}
