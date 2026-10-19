import type { Command } from "./types";
export interface StagedUpload {
  path: string;
  entity: string;
  id: string;
  command: Command;
  cleanup: string[];
  uploaded: boolean;
}
export function uploadCommand(
  entity: string,
  id: string,
  request: string,
  path: string,
  file: { name: string; type: string; size: number },
  revision?: string,
  existing?: { id: string; revision?: string },
  documentType = "Other",
): Command {
  return {
    action: entity === "tenants" ? "document_save" : "attachment_save",
    request_id: request,
    data:
      entity === "tenants"
        ? {
            tenant_id: id,
            document_type: documentType,
            storage_path: path,
            file_name: file.name.slice(0, 200),
            mime_type: file.type,
            size_bytes: String(file.size),
            ...(existing ? { id: existing.id, revision: existing.revision } : {}),
          }
        : { entity_type: entity, id, revision, storage_path: path },
  };
}
