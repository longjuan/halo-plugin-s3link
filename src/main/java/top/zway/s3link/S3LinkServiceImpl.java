package top.zway.s3link;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Constant;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.utils.JsonUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class S3LinkServiceImpl implements S3LinkService {
    private final ReactiveExtensionClient client;

    private static final String OBJECT_KEY = "s3os.plugin.halo.run/object-key";

    @Override
    public Flux<Policy> listS3Policies() {
        return client.list(Policy.class, (policy) -> "s3os".equals(
            policy.getSpec().getTemplateName()), null);
    }

    @SuppressWarnings({"checkstyle:OperatorWrap", "checkstyle:WhitespaceAfter"})
    @Override
    public Mono<S3ListResult> listObjects(String policyName, String continuationToken,
        Integer pageSize) {
        return client.fetch(Policy.class, policyName)
            .flatMap((policy) -> {
                var configMapName = policy.getSpec().getConfigMapName();
                return client.fetch(ConfigMap.class, configMapName);
            })
            .flatMap((configMap) -> {
                var properties = getProperties(configMap);
                return Mono.using(() -> buildS3Client(properties),
                        (s3Client) -> Mono.fromCallable(
                            () -> s3Client.listObjectsV2(ListObjectsV2Request.builder()
                                .bucket(properties.getBucket())
                                .prefix(StringUtils.isNotEmpty(properties.getLocation())
                                    ? properties.getLocation() + "/" : null)
                                .delimiter("/")
                                .maxKeys(pageSize)
                                .continuationToken(StringUtils.isNotEmpty(continuationToken)
                                    ? continuationToken : null)
                                .build())).subscribeOn(Schedulers.boundedElastic()),
                        S3Client::close)
                    .flatMap(listObjectsV2Response -> {
                        List<S3Object> contents = listObjectsV2Response.contents();
                        var objectVos = contents
                            .stream().map(S3ListResult.ObjectVo::fromS3Object)
                            .filter(objectVo -> !objectVo.getKey().endsWith("/"))
                            .collect(Collectors.toMap(S3ListResult.ObjectVo::getKey, o -> o));
                        return client.list(Attachment.class,
                                attachment -> policyName.equals(
                                    attachment.getSpec().getPolicyName()), null)
                            .doOnNext(attachment -> {
                                S3ListResult.ObjectVo objectVo =
                                    objectVos.get(attachment.getMetadata().getAnnotations()
                                        .getOrDefault(OBJECT_KEY, ""));
                                if (objectVo != null) {
                                    objectVo.setIsLinked(true);
                                }
                            })
                            .then()
                            .thenReturn(new S3ListResult(new ArrayList<>(objectVos.values()),
                                listObjectsV2Response.continuationToken(),
                                listObjectsV2Response.nextContinuationToken(),
                                listObjectsV2Response.isTruncated()));
                    });
            });
    }


    @Override
    public Mono<LinkResult.LinkResultItem> addAttachmentRecord(String policyName,
        String objectKey) {
        return authenticationConsumer(authentication -> client.fetch(Policy.class, policyName)
            // TODO 检查是否已经存在
            .flatMap((policy) -> {
                var configMapName = policy.getSpec().getConfigMapName();
                return client.fetch(ConfigMap.class, configMapName);
            })
            .flatMap(configMap -> {
                var properties = getProperties(configMap);
                return Mono.using(() -> buildS3Client(properties),
                        (s3Client) -> Mono.fromCallable(
                                () -> s3Client.headObject(
                                    HeadObjectRequest.builder()
                                        .bucket(properties.getBucket())
                                        .key(objectKey)
                                        .build()))
                            .subscribeOn(Schedulers.boundedElastic()),
                        S3Client::close)
                    .map(headObjectResponse -> {
                        String externalLink = getObjectURL(properties, objectKey);
                        var metadata = new Metadata();
                        metadata.setName(UUID.randomUUID().toString());
                        metadata.setAnnotations(
                            Map.of(OBJECT_KEY, objectKey, Constant.EXTERNAL_LINK_ANNO_KEY,
                                UriUtils.encodePath(externalLink, StandardCharsets.UTF_8)));

                        var spec = new Attachment.AttachmentSpec();
                        spec.setSize(headObjectResponse.contentLength());
                        spec.setDisplayName(objectKey.substring(objectKey.lastIndexOf("/") + 1));
                        spec.setMediaType(headObjectResponse.contentType());

                        var attachment = new Attachment();
                        attachment.setMetadata(metadata);
                        attachment.setSpec(spec);
                        return attachment;
                    })
                    .doOnNext(attachment -> {
                        var spec = attachment.getSpec();
                        if (spec == null) {
                            spec = new Attachment.AttachmentSpec();
                            attachment.setSpec(spec);
                        }
                        spec.setOwnerName(authentication.getName());
                        spec.setPolicyName(policyName);
                    })
                    .flatMap(client::create)
                    .thenReturn(new LinkResult.LinkResultItem(objectKey, true, null));
            }))
            .onErrorResume(throwable ->
                Mono.just(new LinkResult.LinkResultItem(objectKey, false, throwable.getMessage())));
    }

    private <T> Mono<T> authenticationConsumer(Function<Authentication, Mono<T>> func) {
        return ReactiveSecurityContextHolder.getContext()
            .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Authentication required.")))
            .map(SecurityContext::getAuthentication)
            .flatMap(func);
    }

    private String getObjectURL(S3OsProperties properties, String objectKey) {
        if (StringUtils.isBlank(properties.getDomain())) {
            String host;
            if (properties.getEnablePathStyleAccess()) {
                host = properties.getEndpoint() + "/" + properties.getBucket();
            } else {
                host = properties.getBucket() + "." + properties.getEndpoint();
            }
            return properties.getProtocol() + "://" + host + "/" + objectKey;
        } else {
            return properties.getProtocol() + "://" + properties.getDomain() + "/" + objectKey;
        }
    }

    S3OsProperties getProperties(ConfigMap configMap) {
        var settingJson = configMap.getData().getOrDefault("default", "{}");
        return JsonUtils.jsonToObject(settingJson, S3OsProperties.class);
    }

    S3Client buildS3Client(S3OsProperties properties) {
        return S3Client.builder()
            .region(Region.of(properties.getRegion()))
            .endpointOverride(
                URI.create(properties.getEndpointProtocol() + "://" + properties.getEndpoint()))
            .credentialsProvider(() -> AwsBasicCredentials.create(properties.getAccessKey(),
                properties.getAccessSecret()))
            .serviceConfiguration(S3Configuration.builder()
                .chunkedEncodingEnabled(false)
                .pathStyleAccessEnabled(properties.getEnablePathStyleAccess())
                .build())
            .build();
    }


}
