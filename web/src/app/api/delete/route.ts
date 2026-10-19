import { BackendError, failure, json, rpc } from "@/lib/server";
import { uuid, validateLocal } from "@/lib/validation";
import { deletableTypes } from "@/lib/deletion";
export const dynamic = "force-dynamic";
export async function GET(request: Request) {
  try {
    validateLocal(request);
    const params = new URL(request.url).searchParams,
      entity = params.get("entity"),
      id = params.get("id");
    if (!deletableTypes.includes(entity as (typeof deletableTypes)[number]) || !uuid(id))
      throw new Error("Invalid deletion target.");
    return json(await rpc("pn_delete_preview", { p_entity: entity, p_id: id }));
  } catch (error) {
    if (error instanceof BackendError && error.code === "PGRST202")
      return json(
        { error: "Apply the permanent-delete database migration first. See web/README.md.", uncertain: false },
        409,
      );
    return failure(error);
  }
}
