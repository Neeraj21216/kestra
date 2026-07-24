package io.kestra.plugin.core.trigger;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.WebhookService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookOutputTest {
    @Test
    void shouldExposeMultipartFileAndFormFieldsWhenRequestIsMultipart() throws Exception {
        // Given
        byte[] image = { 0, 1, 2, 3, -1 };
        HttpRequest request = HttpRequest.of(
            URI.create("/webhook"),
            "POST",
            HttpRequest.WebhookMultipartRequestBody.builder()
                .parts(List.of(
                    new HttpRequest.WebhookMultipartPart("photo", "result.jpg", "image/jpeg", image),
                    new HttpRequest.WebhookMultipartPart("note", null, "text/plain", "looks good".getBytes())
                ))
                .build()
        );
        WebhookService webhookService = mock(WebhookService.class);
        when(webhookService.newExecution(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(webhookService.parseParameters(any())).thenReturn(Map.of());

        // When
        new Webhook().evaluate(new WebhookContext(request, null, Flow.builder().build(), new Webhook(), webhookService)).block();

        // Then
        ArgumentCaptor<Webhook.Output> output = ArgumentCaptor.forClass(Webhook.Output.class);
        org.mockito.Mockito.verify(webhookService).newExecution(any(), any(), any(), output.capture());
        assertThat(output.getValue().getParts()).containsExactly(
            new Webhook.Part("photo", "result.jpg", "image/jpeg", image.length, Base64.getEncoder().encodeToString(image))
        );
        assertThat(output.getValue().getFormFields()).containsEntry("note", List.of("looks good"));
        assertThat(output.getValue().getBodyBase64()).isNull();
    }

    @Test
    void shouldExposeBinaryBodyAsBase64WhenRequestIsBinary() throws Exception {
        // Given
        byte[] body = { 0, 1, 2, 3, -1 };
        HttpRequest request = HttpRequest.of(URI.create("/webhook"), "POST", HttpRequest.ByteArrayRequestBody.of(body));
        WebhookService webhookService = mock(WebhookService.class);
        when(webhookService.newExecution(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(webhookService.parseParameters(any())).thenReturn(Map.of());

        // When
        new Webhook().evaluate(new WebhookContext(request, null, Flow.builder().build(), new Webhook(), webhookService)).block();

        // Then
        ArgumentCaptor<Webhook.Output> output = ArgumentCaptor.forClass(Webhook.Output.class);
        org.mockito.Mockito.verify(webhookService).newExecution(any(), any(), any(), output.capture());
        assertThat(output.getValue().getBody()).isNull();
        assertThat(output.getValue().getBodyBase64()).isEqualTo(Base64.getEncoder().encodeToString(body));
    }
}
