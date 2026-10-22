"use client";

import { Suspense } from "react";
import { SetPasswordForm } from "@/components/set-password";

export default function ResetPasswordPage() {
  return (
    <Suspense>
      <SetPasswordForm endpoint="/auth/password/reset" title="Choose a new password" intro="This link works once." />
    </Suspense>
  );
}
