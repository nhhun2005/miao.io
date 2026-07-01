import type { HTMLAttributes, ReactNode } from "react";

export interface PanelProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
}

/** Glassmorphism-styled panel used as a card container. */
export function Panel({ className = "", children, ...rest }: PanelProps) {
  const classes = ["panel", className].filter(Boolean).join(" ");
  return (
    <div className={classes} {...rest}>
      {children}
    </div>
  );
}
