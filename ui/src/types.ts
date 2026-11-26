// Shapes mirroring the backend DTOs we consume.

export type Role = "EMPLOYEE" | "MANAGER" | "HR_ADMIN" | "SYSTEM_ADMIN";
export type RequestType = "LEAVE" | "ONBOARDING" | "EXPENSE";
export type RequestStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

export interface LoginResponse {
  token: string;
  expiresInMinutes: number;
  employeeId: number;
  employeeCode: string;
  fullName: string;
  role: Role;
  country: string;
}

export interface RequestDto {
  id: number;
  requestType: RequestType;
  requesterId: number;
  status: RequestStatus;
  currentStepOrder: number;
  currentApproverRole: Role | null;
  escalated: boolean;
  payload: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface StepDto {
  stepOrder: number;
  approverRole: Role;
  slaHours: number;
  escalationRole: Role | null;
}

export interface WorkflowDto {
  id: number;
  requestType: RequestType;
  name: string;
  active: boolean;
  steps: StepDto[];
}

export interface AuditLogDto {
  id: number;
  actorId: number | null;
  actorCode: string;
  action: string;
  entityType: string;
  entityId: number | null;
  before: unknown;
  after: unknown;
  createdAt: string;
}

// Spring Data Page envelope (only the fields we use).
export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number;
  totalPages: number;
}
