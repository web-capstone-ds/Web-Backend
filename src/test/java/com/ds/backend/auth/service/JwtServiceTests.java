package com.ds.backend.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTests {
    private static final String SECRET = "test-secret-key-that-is-at-least-sixty-four-bytes-long-for-hs256-tests";
    private static final String PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----\\nMIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCRbaC1u9RA7EGX\\nmWJw3UhwRZV55aAMJa4pHbsSsV2/5ErqZM31mWYr3LQ0WXsNE5WclZbNSqGmdpmB\\nakrvMIdZCFgEsvECo+FwUAjyxHdZLdhxZeqwsuxuTI7wWTE5IOak6X2vl/R/c8pX\\n4rg8VtuKi0jYKVzJIIgN/O5FA4m/bXPdSSyNT8EQt89q8KDUhbi8jLlj9BPRmvAL\\nSBZHR5MB2LHYrhlJuCfc+LJawo/GYBCa652maRZH9NcNqSmZ4UQU5G7HGTi+qJtZ\\njqjkIMwJoXvlTSxp73DuauEypbQHdvkbclCxWLKkRqLUtUOSUuBrnc4icXERx4kf\\n4ByHdvp9AgMBAAECggEAANPHQkXBsf3hNfFUgEh+z60Mr8slONmO7oEoBL2cu5fw\\na8N+VlDpFp7BjidYk+yhs6a0bp1IoQ+OxcZAHyVxglM4sHiy9m0zSYGttkL490ER\\npSyVQiwXemz22ete/4m555tPoSEv1qtH0NLdoJyLoin8lHE9ARvzMHv3/El0wmX0\\nia9fU2h5ZRz+erKRfXIE2WByMzQFm4NEnW7fodlAHp2IHIzlsApKcRBlSxwzYz0p\\nZxocuOL8sRkRsx8HMFYabt6P57O5ft3bP3v+drBn6tV5+g6oTe9ma3HNtCBRvV0d\\nHU5STAaIYDEGpPsqkaCuIvOP/n+cF/N2WuduyjWEgQKBgQDK4tde/vJj6WsSQ/Tm\\n8/GGBPnWLNhVOiPh/7drM9DrgcvSuVghy6nuC/rOgvn8iNbaxo9exKc7bTi8NSps\\nif1cipNRB0xQzCAqm4EHvSOM3RhrYU2XNG5aePteKRkaxUrx7SLRRhrSxgT26+/0\\nOTkOQQiJbvwpXrt4zl/6bI84vQKBgQC3gAlAj5gwtVVc7P+Ht/w3ddV0Q644/w2s\\nuuW4jZ5ma1MsntCV4+NB/WfE8sF1xmSCpry7Vwbtg3iK39HFao/G3lXpU860n1D1\\nVLAz3R0wIYm+9rKkyyqMz7JKHxoOHqiNZpHGnf8u2Zm2Lxa19l55mNZFUIuLcTjH\\nmaesCEdEwQKBgHuAdoYXP8neDxWBVJU6Le3dHZBooA/RYoJyPV7+ndCepEpUFPgN\\nmBWWKUiIplA5pEBs2l1f7ETaiczXuEl1/krU+DQ88xHEEFifbH4ffoKDHBhRlP/U\\nJNDiEHITJnsLWkHGjolB2ZYwgFkm6lyKcNbic6Xlb45nYkt5pSc16kEpAoGAAap8\\n6wQiupZ63uwb6cyG0q4UMQkIROYz5pSPz1whywZAbBBJDoNSJeA6F/SoQvx5HyKK\\nZh0FKBTgfEpJ6kPsaD7Ogt7K6qLjpkaNjvr779ruGDi/KHXbvgmIsdmUb/phR19e\\nBXKbxX4eQiQtTZueAfacQ2bWzX5KqK1Bc3NjykECgYBJQyPb1xbHGyyNMuW0JbBs\\n00RURmVD6MjCLfW5QxIERSotBl5isOVZD9Y2uQjugKmxizL3BkiaMW75u+L3TxPw\\nprQPbcVmU1VSc8gPjI4qxrRML9nYpPtDenLwyxmk0s7qsyFNniRusp1R1Cau5Zuo\\nc0nLueKRYbVGwKQjz4frlA==\\n-----END PRIVATE KEY-----\\n
            """;

    @Test
    void serviceTokenUsesRs256AndFiveMinuteTtl() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JwtService jwtService = new JwtService(objectMapper, SECRET, PRIVATE_KEY, "", 1_800_000L, 604_800_000L);
        jwtService.validateSecret();

        String token = jwtService.createServiceToken("web-backend");
        String[] parts = token.split("\\.");
        Map<String, Object> header = decode(parts[0], objectMapper);
        Map<String, Object> payload = decode(parts[1], objectMapper);

        assertThat(header.get("alg")).isEqualTo("RS256");
        assertThat(payload.get("sub")).isEqualTo("web-backend");
        assertThat(payload.get("type")).isEqualTo("service");
        long iat = ((Number) payload.get("iat")).longValue();
        long exp = ((Number) payload.get("exp")).longValue();
        assertThat(exp - iat).isEqualTo(300L);
    }

    private Map<String, Object> decode(String value, ObjectMapper objectMapper) throws Exception {
        return objectMapper.readValue(Base64.getUrlDecoder().decode(value), new TypeReference<>() {});
    }
}
