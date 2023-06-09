import request from "@/utils/request";
import { LinkResult, DeepRequired, LinkRequest } from "../../interface";

/**
 * /apis/api.plugin.halo.run/v1alpha1/plugins/S3Link/attachments/link
 */
export function postApisApiPluginHaloRunV1Alpha1PluginsS3LinkAttachmentsLink(input: LinkRequest) {
    return request.post<DeepRequired<LinkResult>>(`/apis/api.plugin.halo.run/v1alpha1/plugins/S3Link/attachments/link`, input);
}
