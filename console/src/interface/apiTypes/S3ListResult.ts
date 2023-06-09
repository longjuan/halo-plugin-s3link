import { ObjectVo } from "../../interface";

export interface S3ListResult {
    currentToken?: string;
    hasMore?: boolean;
    nextToken?: string;
    objects?: ObjectVo[];
}
