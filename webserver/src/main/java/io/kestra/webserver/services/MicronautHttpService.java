package io.kestra.webserver.services;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpHeaders;
import java.util.List;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.server.multipart.MultipartBody;
import reactor.core.publisher.Flux;

public abstract class MicronautHttpService {

    public static HttpRequest from(io.micronaut.http.HttpRequest<?> request) {
        HttpRequest.RequestBody body = null;
        if (request.getBody().isPresent()) {
            Object bodyContent = request.getContentType().filter(MicronautHttpService::isBinary)
                .flatMap(__ -> request.getBody(byte[].class))
                .orElseGet(() -> request.getBody(String.class).orElse(request.getBody().get()));

            if (bodyContent instanceof InputStream inputStream) {
                body = HttpRequest.InputStreamRequestBody.builder()
                    .content(inputStream)
                    .build();
            } else if (bodyContent instanceof byte[] bytes) {
                body = HttpRequest.ByteArrayRequestBody.builder()
                    .content(bytes)
                    .build();
            } else if (bodyContent instanceof String str) {
                body = HttpRequest.StringRequestBody.builder()
                    .content(str)
                    .build();
            } else {
                body = HttpRequest.JsonRequestBody.builder()
                    .content(bodyContent)
                    .build();
            }
        }

        return HttpRequest.builder()
            .uri(request.getUri())
            .method(request.getMethod().name())
            .body(body)
            .headers(HttpHeaders.of(request.getHeaders().asMap(), (a, b) -> true))
            .remoteAddress(request.getRemoteAddress())
            .build();
    }

    /**
     * Converts a multipart Micronaut request to the internal webhook request representation.
     *
     * @param request the incoming request
     * @param multipartBody decoded multipart parts
     * @return the internal request with unmodified multipart part bytes
     */
    public static HttpRequest from(io.micronaut.http.HttpRequest<?> request, MultipartBody multipartBody) {
        List<HttpRequest.WebhookMultipartPart> parts = Flux.from(multipartBody)
            .map(MicronautHttpService::toWebhookMultipartPart)
            .collectList()
            .block();

        return HttpRequest.builder()
            .uri(request.getUri())
            .method(request.getMethod().name())
            .body(HttpRequest.WebhookMultipartRequestBody.builder().parts(parts).build())
            .headers(HttpHeaders.of(request.getHeaders().asMap(), (a, b) -> true))
            .remoteAddress(request.getRemoteAddress())
            .build();
    }

    private static HttpRequest.WebhookMultipartPart toWebhookMultipartPart(CompletedPart part) {
        try {
            return new HttpRequest.WebhookMultipartPart(
                part.getName(),
                part instanceof CompletedFileUpload fileUpload ? fileUpload.getFilename() : null,
                part.getContentType().map(Object::toString).orElse(null),
                part.getBytes()
            );
        } catch (java.io.IOException e) {
            throw new UncheckedIOException("Cannot read multipart webhook part '%s'.".formatted(part.getName()), e);
        }
    }

    private static boolean isBinary(MediaType contentType) {
        if ("text".equals(contentType.getType())) {
            return false;
        }

        String subtype = contentType.getSubtype();
        return !("json".equals(subtype)
            || subtype.endsWith("+json")
            || "xml".equals(subtype)
            || subtype.endsWith("+xml")
            || "x-www-form-urlencoded".equals(subtype));
    }

    public static <T> io.micronaut.http.HttpResponse<?> to(HttpResponse<T> response) {
        var result = io.micronaut.http.HttpResponse
            .status(HttpStatus.valueOf(response.getStatus().getCode()))
            .headers(headers ->
            {
                if (response.getHeaders() != null) {
                    response.getHeaders().map().forEach((key, values) ->
                    {
                        for (String value : values) {
                            headers.add(key, value);
                        }
                    });
                }
            });

        if (response.getBody() instanceof byte[] bytes) {
            return result.body(bytes);
        } else if (response.getBody() instanceof String str) {
            return result.body(str);
        } else if (response.getBody() instanceof InputStream inputStream) {
            return result.body(inputStream);
        } else if (response.getBody() != null) {
            return result.body(response.getBody());
        } else {
            return result;
        }
    }
}
