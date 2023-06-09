package top.zway.s3link;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public class LinkRequest {
    private String policyName;
    private List<String> objectKeys;
}