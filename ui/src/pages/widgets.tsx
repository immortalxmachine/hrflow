import type { RequestStatus } from "../types";

/** Coloured pill for a request status. */
export function StatusBadge({ status }: { status: RequestStatus }) {
  return <span className={`badge status-${status}`}>{status}</span>;
}
