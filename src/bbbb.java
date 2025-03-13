package datn.example.datn.web.rest.user;
import datn.example.datn.service.AssemblyAIService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
@RestController
@RequestMapping("/api/voice-search")
public class AssemblyAIController {

    private final AssemblyAIService assemblyAIService;

    public AssemblyAIController(AssemblyAIService assemblyAIService) {
        this.assemblyAIService = assemblyAIService;
    }

    @PostMapping("/transcribe")
    public ResponseEntity<String> transcribeAudio(@RequestParam("file") MultipartFile file) {
        try {
            String text = assemblyAIService.transcribeAudio(file);
            return ResponseEntity.ok(text);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Lỗi: " + e.getMessage());
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadAudio(@RequestParam("file") MultipartFile file) {
        try {
            String rawText = assemblyAIService.transcribeAudio(file);
            String result = assemblyAIService.analyzeWithGeminiAndSearchDB(rawText);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Lỗi khi xử lý file âm thanh: " + e.getMessage());
        }
    }
}


