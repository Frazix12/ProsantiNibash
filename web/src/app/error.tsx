"use client";
import { Button } from "@/components/ui/button";
export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <main className="error-page">
      <h1>This view could not be displayed.</h1>
      <p>
        Reload the building book. If a save was interrupted, recover the original request rather than entering it again.
      </p>
      <Button onClick={reset}>Retry</Button>
      <Button onClick={() => window.location.reload()}>Reload workspace</Button>
    </main>
  );
}
