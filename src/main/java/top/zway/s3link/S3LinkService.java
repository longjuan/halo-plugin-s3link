package top.zway.s3link;


import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Policy;

public interface S3LinkService {
    Flux<Policy> listS3Policies();

    Mono<S3ListResult> listObjects(String policyName, String continuationToken,
        Integer pageSize);

    Mono<LinkResult.LinkResultItem> addAttachmentRecord(String policyName, String objectKey);
}
