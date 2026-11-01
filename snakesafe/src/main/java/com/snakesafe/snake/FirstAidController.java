package com.snakesafe.snake;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.snakesafe.config.AppProperties;

/** Serves the bundled first-aid steps (English + Kannada). The file is the only source: edit it to change the text. */
@RestController
public class FirstAidController {

    private final ObjectNode content;
    private final AppProperties props;

    public FirstAidController(ObjectMapper mapper, AppProperties props) throws IOException {
        this.props = props;
        try (InputStream in = new ClassPathResource("first-aid.json").getInputStream()) {
            this.content = (ObjectNode) mapper.readTree(in);
        }
    }

    @GetMapping("/api/first-aid")
    public ObjectNode firstAid() {
        ObjectNode copy = content.deepCopy();
        ObjectNode numbers = copy.putObject("numbers");
        numbers.put("emergency", props.emergencyNumber());
        numbers.put("poisonHelpline", props.poisonHelpline());
        return copy;
    }
}
