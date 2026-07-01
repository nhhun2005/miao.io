import type { ReactNode } from "react";

export interface ModalProps {
  /** Whether the modal is visible. */
  open: boolean;
  /** Content to display inside the modal. */
  children: ReactNode;
  /** Optional handler when clicking the backdrop. */
  onClose?: () => void;
}

/** Centered overlay modal with a backdrop. */
export function Modal({ open, children, onClose }: ModalProps) {
  if (!open) return null;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        {children}
      </div>
    </div>
  );
}
