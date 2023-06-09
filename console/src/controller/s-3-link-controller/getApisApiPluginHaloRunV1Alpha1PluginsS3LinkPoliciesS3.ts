import request from "@/utils/request";
import { Policy, DeepRequired } from "../../interface";

/**
 * /apis/api.plugin.halo.run/v1alpha1/plugins/S3Link/policies/s3
 */
export function getApisApiPluginHaloRunV1Alpha1PluginsS3LinkPoliciesS3() {
    return request.get<DeepRequired<Policy[]>>(`/apis/api.plugin.halo.run/v1alpha1/plugins/S3Link/policies/s3`);
}
