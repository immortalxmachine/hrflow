// Minimal typed API client. Stores the JWT in localStorage and attaches it as a
// Bearer token on every request. Throws ApiError (with the HTTP status) on
// non-2xx so pages can show the message.

import type {
  AuditLogDto, LoginResponse, Page, RequestDto, WorkflowDto,
} from "./types";

const TOKEN_KEY = "hrflow.token";

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}
export function setToken(token: string | null): void {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";

  const res = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`;
    try {
      const data = await res.json();
      if (data?.message) message = data.message;
      else if (data?.error) message = data.error;
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(res.status, message);
  }
  // 204 or empty body
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  login: (employeeCode: string, password: string) =>
    request<LoginResponse>("POST", "/api/auth/login", { employeeCode, password }),

  myRequests: () =>
    request<Page<RequestDto>>("GET", "/api/requests/mine?size=50"),

  createLeave: (leaveType: string, startDate: string, endDate: string) =>
    request<RequestDto>("POST", "/api/requests/leave", { leaveType, startDate, endDate }),

  inbox: () => request<RequestDto[]>("GET", "/api/approvals/inbox"),

  decide: (requestId: number, decision: "APPROVED" | "REJECTED", comment: string) =>
    request<RequestDto>("POST", `/api/approvals/${requestId}/decision`, { decision, comment }),

  workflows: () => request<WorkflowDto[]>("GET", "/api/admin/workflows"),

  saveWorkflow: (id: number, body: unknown) =>
    request<WorkflowDto>("PUT", `/api/admin/workflows/${id}`, body),

  audit: (params: { action?: string; entityType?: string }) => {
    const q = new URLSearchParams();
    if (params.action) q.set("action", params.action);
    if (params.entityType) q.set("entityType", params.entityType);
    q.set("size", "50");
    return request<Page<AuditLogDto>>("GET", `/api/admin/audit?${q.toString()}`);
  },

  runEscalation: () =>
    request<{ escalated: number }>("POST", "/api/admin/escalations/run"),
};
