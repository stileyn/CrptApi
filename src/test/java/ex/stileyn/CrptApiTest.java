package ex.stileyn;

import okhttp3.*;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrptApiTest {

    @Mock
    OkHttpClient httpClient;

    @Mock
    private Response response;

    @Test
    void createDocument_SuccessfulRequest() throws IOException, InterruptedException {
        CrptApi crptApi = new CrptApi();
        CrptApi.Document document = CrptApi.Document.builder().build();
        String signature = "example_signature";

        // Создаем реальный OkHttpClient
        OkHttpClient realHttpClient = new OkHttpClient();

        // Используем MockWebServer для эмуляции HTTP-сервера
        MockWebServer mockWebServer = new MockWebServer();

        // Запускаем MockWebServer
        mockWebServer.start();

        // Устанавливаем URL для MockWebServer
        String mockUrl = mockWebServer.url("/api/v3/lk/documents/create").toString();

        // Мокируем Response не получится, поэтому используем реальный OkHttpClient
        crptApi.httpClient = realHttpClient;

        // Выполняем метод createDocument
        crptApi.createDocument(document, signature);

        // Проверяем, что запрос был отправлен на MockWebServer
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals(mockUrl, recordedRequest.getRequestUrl().toString());

        // Останавливаем MockWebServer
        mockWebServer.shutdown();
    }

    @Test
    void createDocument_FailedRequest() throws IOException, InterruptedException {
        CrptApi crptApi = new CrptApi();
        CrptApi.Document document = CrptApi.Document.builder().build();
        String signature = "example_signature";

        // Мокируем OkHttpClient
        OkHttpClient mockHttpClient = mock(OkHttpClient.class);

        // Создаем экземпляр Response с помощью Response.Builder
        Response mockResponse = new Response.Builder()
                .request(new Request.Builder().url("http://example.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(500) // Пример кода ошибки
                .message("Server Error")
                .body(ResponseBody.create(MediaType.get("application/json"), "Error response"))
                .build();

        // Настраиваем поведение для mockHttpClient
        Call mockCall = mock(Call.class);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);

        // Устанавливаем mockHttpClient в crptApi
        crptApi.httpClient = mockHttpClient;

        // Выполняем метод createDocument
        crptApi.createDocument(document, signature);

    }

    @Test
    void checkRequestLimit_WaitForInterval() throws InterruptedException {
        CrptApi crptApi = new CrptApi();

        // Устанавливаем прошедшее время, чтобы сработало условие ожидания
        crptApi.lastRequestTime = System.currentTimeMillis() - CrptApi.REQUEST_INTERVAL / 2;

        // Устанавливаем лимит равным 1, чтобы метод заснул
        crptApi.requestLimit = 1;

        // Вызываем метод, который должен подождать
        crptApi.checkRequestLimit();

        // Проверяем, что прошло достаточное время и лимит сброшен
        long currentTime = System.currentTimeMillis();
        assertEquals(currentTime - crptApi.lastRequestTime, CrptApi.REQUEST_INTERVAL);
        assertEquals(crptApi.requestLimit, 0);
    }


}
