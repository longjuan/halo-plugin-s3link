import request from "@/utils/request";
import { S3ListResult, DeepRequired } from "../../interface";

/**
 * /apis/api.plugin.halo.run/v1alpha1/plugins/S3Link/objects/{policyName}
 */
export function getApisApiPluginHaloRunV1Alpha1PluginsS3LinkObjectsByPolicyName(params: GetApisApiPluginHaloRunV1Alpha1PluginsS3LinkObjectsByPolicyNameParams) {
    const paramsInput = {
        continuationToken: params.continuationToken,
        continuationObject: params.continuationObject,
        pageSize: params.pageSize,
        unlinked: params.unlinked,
    };
    return request.get<DeepRequired<S3ListResult>>(`/apis/api.plugin.halo.run/v1alpha1/plugins/S3Link/objects/${params.policyName}`, {
        params: paramsInput,
    });
}

interface GetApisApiPluginHaloRunV1Alpha1PluginsS3LinkObjectsByPolicyNameParams {
    policyName: string;
    continuationToken?: string;
    continuationObject?: string;
    pageSize: number;
    unlinked?: boolean;
}
