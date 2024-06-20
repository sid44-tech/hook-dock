package dev.hookdock.delivery;

import java.util.List;
import java.util.Map;

record OutboundResponse(int status, Map<String, List<String>> headers, String bodyExcerpt) {

    boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
