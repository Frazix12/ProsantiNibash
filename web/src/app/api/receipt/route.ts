import { BackendError, failure, json, rpc } from "@/lib/server";
import { uuid, validateLocal } from "@/lib/validation";
import type { Book, Payment } from "@/lib/types";
export const dynamic = "force-dynamic";
export async function GET(request: Request) {
  try {
    validateLocal(request);
    const id = new URL(request.url).searchParams.get("id");
    if (!uuid(id)) throw new Error("Invalid payment.");
    let result: Payment | undefined;
    try {
      result = await rpc<Payment>("pn_receipt", { p_id: id });
    } catch (error) {
      if (!(error instanceof BackendError) || error.code !== "PGRST202") throw error;
      result = (await rpc<Book>("pn_snapshot")).payments.find((p) => p.id === id);
    }
    if (!result) throw new Error("Receipt not found.");
    return json(result);
  } catch (error) {
    return failure(error);
  }
}
