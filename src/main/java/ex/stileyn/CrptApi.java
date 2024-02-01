package ex.stileyn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrptApi {

    private static final Logger log = LoggerFactory.getLogger(CrptApi.class);
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final int REQUEST_LIMIT = 5;
    static long REQUEST_INTERVAL = TimeUnit.SECONDS.toMillis(1);

    OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Lock requestLock;
    int requestLimit;
    long lastRequestTime;

    public CrptApi() {
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.requestLock = new ReentrantLock();
        this.requestLimit = REQUEST_LIMIT;
        this.lastRequestTime = System.currentTimeMillis();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::resetLastRequestTime, REQUEST_INTERVAL, REQUEST_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void createDocument(Document document, String signature) {
        try {
            checkRequestLimit();

            ObjectNode jsonRequest = objectMapper.createObjectNode();
            jsonRequest.set("description", objectMapper.valueToTree(document.getDescription()));
            jsonRequest.put("doc_id", document.getDocId());
            jsonRequest.put("doc_status", document.getDocStatus());
            jsonRequest.put("doc_type", document.getDocType());
            jsonRequest.put("importRequest", document.isImportRequest());
            jsonRequest.put("owner_inn", document.getOwnerInn());
            jsonRequest.put("participant_inn", document.getParticipantInn());
            jsonRequest.put("producer_inn", document.getProducerInn());
            jsonRequest.put("production_date", document.getProductionDate());
            jsonRequest.put("production_type", document.getProductionType());
            jsonRequest.set("products", objectMapper.valueToTree(document.getProducts()));
            jsonRequest.put("reg_date", document.getRegDate());
            jsonRequest.put("reg_number", document.getRegNumber());

            RequestBody requestBody = RequestBody.create(
                    jsonRequest.toString(),
                    MediaType.get("application/json")
            );

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(requestBody)
                    .addHeader("Signature", signature)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                handleResponse(response);
            }
        } catch (IOException | InterruptedException e) {
            log.error("An error occurred during document creation.", e);
        }
    }

    void checkRequestLimit() throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        requestLock.lock();
        try {
            if (currentTime - lastRequestTime < REQUEST_INTERVAL) {
                if (requestLimit > 0) {
                    requestLimit--;
                } else {
                    Thread.sleep(REQUEST_INTERVAL - (currentTime - lastRequestTime));
                }
            } else {
                lastRequestTime = currentTime;
                requestLimit = 0;
            }
        } finally {
            requestLock.unlock();
        }
    }

    private void handleResponse(Response response) throws IOException {
        if (response.body() != null) {
            if (!response.isSuccessful()) {
                log.error("HTTP request failed with code: {}", response.code());
            } else {
                log.info("HTTP request successful. Response: {}", response.body().string());
            }
        } else {
            log.error("HTTP response body is null.");
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {
        private String description;
        private String participantInn;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private String regDate;
        private String regNumber;
        private List<Product> products;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Product {
        private CertificateDocument certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }

    public enum CertificateDocument {
        @JsonProperty("conformity_certificate")
        CONFORMITY_CERTIFICATE,

        @JsonProperty("conformity_declaration")
        CONFORMITY_DECLARATION
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi();
        CrptApi.Document document = CrptApi.Document.builder().build();
        String signature = "example_signature";
        crptApi.createDocument(document, signature);
    }

    private void resetLastRequestTime() {
        requestLock.lock();
        try {
            lastRequestTime = System.currentTimeMillis();
        } finally {
            requestLock.unlock();
        }
    }
}
