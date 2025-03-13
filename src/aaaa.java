package datn.example.datn.service;
import datn.example.datn.entity.Category;
import datn.example.datn.entity.Product;
import datn.example.datn.repository.CategoryRepository;
import datn.example.datn.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AssemblyAIService {

//    @Value("${assemblyai.api.key}")
//    private String apiKey;
//
//    @Value("${gemini.api.key}")
//    private String geminiApiKey;

    private String apiKey = "0d5bf615f83145ac80d8b222fbd67640";
    private String geminiApiKey = "AIzaSyBI0jEvIyEw-t2driEcwoc2ZB8MuEtZ5d4";


    private final RestTemplate restTemplate = new RestTemplate();

    public String transcribeAudio(MultipartFile file) throws IOException {
        // 1. Upload file lên AssemblyAI
        String uploadUrl = uploadFile(file);
        if (uploadUrl == null) {
            return "Error uploading file to AssemblyAI!";
        }

        // 2. Gửi yêu cầu nhận diện giọng nói
        String transcriptId = requestTranscription(uploadUrl);
        if (transcriptId == null) {
            return "Error sending voice recognition request!";
        }

        // 3. Kiểm tra trạng thái và lấy kết quả
        String rawText = getTranscriptionResult(transcriptId);
        if (rawText == null || rawText.isEmpty()) {
            return "No data from AssemblyAI!";
        }

        // 4. Gửi đến Gemini AI để phân tích ngữ cảnh
        return analyzeWithGemini(rawText);
    }

    private String uploadFile(MultipartFile file) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.assemblyai.com/v2/upload",
                HttpMethod.POST,
                requestEntity,
                Map.class
        );

        return response.getBody() != null ? (String) response.getBody().get("upload_url") : null;
    }

    private String requestTranscription(String audioUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("audio_url", audioUrl);

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.assemblyai.com/v2/transcript",
                HttpMethod.POST,
                requestEntity,
                Map.class
        );

        return response.getBody() != null ? (String) response.getBody().get("id") : null;
    }

    private String getTranscriptionResult(String transcriptId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = "https://api.assemblyai.com/v2/transcript/" + transcriptId;
        int maxAttempts = 10; // Số lần thử tối đa
        int attempt = 0;

        while (attempt < maxAttempts) {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                String status = (String) responseBody.get("status");
                if ("completed".equals(status)) {
                    return (String) responseBody.get("text");
                } else if ("failed".equals(status)) {
                    return "Recognize failure!";
                }
            }
            attempt++;
            try {
                Thread.sleep(2000); // Chờ 2 giây trước khi kiểm tra tiếp
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Error waiting for results!";
            }
        }
        return "Error: Timed out waiting for results from AssemblyAI.";
    }

    private String analyzeWithGemini(String text) {
        String geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 🔥 Hướng AI tập trung vào xe đạp
        String prompt = "Based on the following content, find the most suitable bicycle type. The user may not pronounce correctly, \" +\n" +
                "\"consider the products in the data that have similar pronunciations, the user can speak Vietnamese or English.\" +\n" +
                "\" Bicycle types may include: \"\n" +
                "+ \" mountain bikes, racing bikes, city bikes, folding bikes, children's bikes... \"\n" +
                "+ \" If no suitable category is found, answer 'unknown'. Input sentence: \"" + text + "\"";



        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        //content.put("parts", List.of(Map.of("text", "Trích xuất danh mục sản phẩm từ câu sau: \"" + text + "\"")));
        content.put("parts", List.of(Map.of("text", prompt)));


        requestBody.put("contents", List.of(content));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(geminiApiUrl, HttpMethod.POST, entity, Map.class);
            Map<String, Object> body = response.getBody();

            if (body != null && body.containsKey("candidates")) {
                Object candidatesObj = body.get("candidates");

                if (candidatesObj instanceof List<?>) {
                    List<?> candidates = (List<?>) candidatesObj;
                    if (!candidates.isEmpty() && candidates.get(0) instanceof Map) {
                        Map<String, Object> candidate = (Map<String, Object>) candidates.get(0);
                        Object contentResultObj = candidate.get("content");

                        if (contentResultObj instanceof Map) {
                            Map<String, Object> contentResult = (Map<String, Object>) contentResultObj;
                            Object partsObj = contentResult.get("parts");

                            if (partsObj instanceof List<?>) {
                                List<?> parts = (List<?>) partsObj;
                                if (!parts.isEmpty() && parts.get(0) instanceof Map) {
                                    Map<String, Object> firstPart = (Map<String, Object>) parts.get(0);
                                    String geminiResponse = (String) firstPart.get("text");

                                    // 🔥 Lọc danh mục từ text trả về bằng regex
                                    Pattern pattern = Pattern.compile("\\*\\*([^*]+)\\*\\*"); // Lấy text trong ** **
                                    Matcher matcher = pattern.matcher(geminiResponse);

                                    List<String> categories = new ArrayList<>();
                                    while (matcher.find()) {
                                        categories.add(matcher.group(1).trim());
                                    }

                                    if (!categories.isEmpty()) {
                                        return categories.get(0); // Lấy danh mục đầu tiên
                                    } else {
                                        return geminiResponse.trim(); // Trả về kết quả nếu không có markdown
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            return "Error when calling Gemini AI: " + e.getMessage();
        }
        return "Context analysis is not possible with Gemini AI.";
    }

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    public String analyzeWithGeminiAndSearchDB(String text) {
        // 1. Gọi Gemini AI để lấy danh mục sản phẩm
        String categoryName = analyzeWithGemini(text);

        if (categoryName == null || categoryName.isEmpty()) {
            return "Error: Unable to get catalog from Gemini AI.";
        }

        // 2. Chuẩn hóa danh mục (map từ tiếng Việt -> tiếng Anh nếu cần)
        categoryName = mapCategory(categoryName);

        // 3. Tìm danh mục gần đúng trong database
        Category category = categoryRepository.findByNameContainingIgnoreCase(categoryName);
        if (category == null) {
            return "No category found:" + categoryName;
        }

        // 4. Lấy danh sách sản phẩm thuộc danh mục
        List<Product> products = productRepository.findByCategory(category);
        if (products.isEmpty()) {
            return "No products found in category: " + categoryName;
        }

        // 5. Trả về danh sách sản phẩm ngắn gọn
        return products.stream()
                .map(p -> p.getName() + " | Giá: " + p.getOriginalPrice() + " USD")
                .collect(Collectors.joining("\n"));
    }
    private static final Map<String, String> CATEGORY_MAPPING = Map.of(
            "xe đạp leo núi", "mountain bike",
            "xe đạp đua", "road bike",
            "xe đạp thành phố", "city bike",
            "xe đạp gấp", "folding bike",
            "xe đạp trẻ em", "kids bike"
    );

    private String mapCategory(String vietnameseCategory) {
        return CATEGORY_MAPPING.getOrDefault(vietnameseCategory.toLowerCase(), vietnameseCategory);
    }
}

