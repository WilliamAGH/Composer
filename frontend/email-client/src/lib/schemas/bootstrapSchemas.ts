/**
 * Bootstrap schema - validates the initial page load data from the server.
 *
 * @see docs/type-safety-zod-validation.md
 */

import { z } from "zod/v4";
import { EmailMessageSchema } from "./emailSchemas";
import { AiFunctionCatalogDtoSchema } from "./catalogSchemas";

/**
 * Schema for email client bootstrap data injected by the server.
 */
export const EmailClientBootstrapSchema = z.object({
  uiNonce: z.string().nullable(),
  messages: z.array(EmailMessageSchema),
  folderCounts: z.record(z.string(), z.number()),
  effectiveFolders: z.record(z.string(), z.string()),
  aiFunctions: AiFunctionCatalogDtoSchema.nullable(),
});

export type EmailClientBootstrap = z.infer<typeof EmailClientBootstrapSchema>;
