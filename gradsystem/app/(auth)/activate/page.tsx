"use client";

import { Suspense } from "react";
import { SetPasswordForm } from "@/components/set-password";

export default function ActivatePage() {
  return (
    <Suspense>
      <SetPasswordForm
        endpoint="/auth/activate"
        title="Activate your account"
        intro="Set a password to finish setting up your Graduate Research Progress System account."
      />
    </Suspense>
  );
}
