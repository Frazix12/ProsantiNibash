import { failure, json, rpc } from "@/lib/server";
import { validateCommand, validateLocal } from "@/lib/validation";
import { validateBook } from "@/lib/wire";
export const dynamic = "force-dynamic";
export async function GET(request: Request) {
  try {
    validateLocal(request);
    const book = await rpc("pn_snapshot");
    validateBook(book);
    return json(book);
  } catch (error) {
    return failure(error);
  }
}
export async function POST(request: Request) {
  try {
    validateLocal(request, true);
    if (Number(request.headers.get("content-length") ?? "0") > 100000) throw new Error("Request too large.");
    const raw = await request.text();
    if (raw.length > 100000) throw new Error("Request too large.");
    const command: unknown = JSON.parse(raw);
    validateCommand(command);
    return json({
      result: await rpc(command.action === "permanent_delete" ? "pn_delete" : "pn_execute", { p_command: command }),
    });
  } catch (error) {
    return failure(error);
  }
}
