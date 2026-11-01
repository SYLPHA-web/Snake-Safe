package com.snakesafe.chat;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.snakesafe.config.AppProperties;

/** Safe fixed answers. Used by the STUB chat mode and as the fallback whenever the AI assistant cannot answer. */
@Component
public class CannedReplies {

    private final AppProperties props;

    public CannedReplies(AppProperties props) {
        this.props = props;
    }

    public String reply(String message, String language) {
        boolean kn = "kn".equals(language);
        String m = message.toLowerCase(Locale.ROOT);
        String n = props.emergencyNumber();
        if (ChatService.mentionsBite(message)) {
            String call = "Call " + n + " now. ";
            return kn ? call + "ಈಗಲೇ " + n + " ಗೆ ಕರೆ ಮಾಡಿ. ಕೆಂಪು ತುರ್ತು ಬಟನ್ ಒತ್ತಿ, ಕಡಿತಕ್ಕೊಳಗಾದ ಕೈ ಅಥವಾ ಕಾಲನ್ನು ಅಲುಗಾಡಿಸಬೇಡಿ. ಪ್ರತಿ ಕಡಿತವನ್ನೂ ತುರ್ತು ಎಂದು ಪರಿಗಣಿಸಿ."
                    : call + "Tap the red Emergency button to find the nearest hospital with antivenom. Keep the bitten limb still. Treat every bite as an emergency.";
        }
        if (has(m, "first aid", "what should i do", "ಪ್ರಥಮ")) {
            return kn ? "ಶಾಂತವಾಗಿರಿ, ಕೈ ಅಥವಾ ಕಾಲನ್ನು ಅಲುಗಾಡಿಸಬೇಡಿ, ಉಂಗುರ ಮತ್ತು ಬಿಗಿ ವಸ್ತುಗಳನ್ನು ತೆಗೆಯಿರಿ, ಮತ್ತು " + n + " ಗೆ ಕರೆ ಮಾಡಿ ಆಸ್ಪತ್ರೆಗೆ ಹೋಗಿ. ಪೂರ್ಣ ಹಂತಗಳಿಗೆ ಪ್ರಥಮ ಚಿಕಿತ್ಸೆ ಪುಟ ನೋಡಿ. ಪ್ರತಿ ಕಡಿತವನ್ನೂ ತುರ್ತು ಎಂದು ಪರಿಗಣಿಸಿ."
                    : "Stay calm, keep the limb still, take off rings and tight items, call " + n + " and get to a hospital. See the First Aid page for all steps. Treat every bite as an emergency.";
        }
        if (has(m, "hospital", "antivenom", "ಆಸ್ಪತ್ರೆ")) {
            return kn ? "ಹತ್ತಿರದ ಆಸ್ಪತ್ರೆಯನ್ನು ಹುಡುಕಲು ಆಸ್ಪತ್ರೆಗಳ ಪುಟ ತೆರೆಯಿರಿ. ಹಾವು ಕಡಿದಿದ್ದರೆ ಕೆಂಪು ತುರ್ತು ಬಟನ್ ಒತ್ತಿ."
                    : "Open the Hospitals page to see hospitals near you and how recently their antivenom stock was checked. If someone was bitten, tap the red Emergency button.";
        }
        if (has(m, "catcher", "rescue", "ಹಿಡಿ")) {
            return kn ? "ನಿಮ್ಮ ಜಿಲ್ಲೆಯ ಹಾವು ಹಿಡಿಯುವವರನ್ನು ಹುಡುಕಲು ಹಾವು ಹಿಡಿಯುವವರ ಪುಟ ತೆರೆಯಿರಿ. ಅವರು ವೈದ್ಯಕೀಯ ಸಹಾಯ ನೀಡುವುದಿಲ್ಲ."
                    : "Open the Catchers page to find a snake catcher in your district. They cannot give medical help.";
        }
        if (has(m, "identify", "photo", "which snake", "ಗುರುತ", "ಫೋಟೋ")) {
            return kn ? "ಹಾವಿನ ಫೋಟೋ ಅಪ್‌ಲೋಡ್ ಮಾಡಲು ಗುರುತಿಸಿ ಪುಟ ತೆರೆಯಿರಿ. ಫಲಿತಾಂಶ ತಪ್ಪಾಗಿರಬಹುದು."
                    : "Open the Identify page and upload a photo. The result may be wrong, so never rely on it for safety.";
        }
        return kn ? "ನಾನು ಹಾವುಗಳು, ಪ್ರಥಮ ಚಿಕಿತ್ಸೆ, ಆಸ್ಪತ್ರೆಗಳು ಮತ್ತು ಈ ಆ್ಯಪ್ ಬಳಕೆ ಬಗ್ಗೆ ಸಹಾಯ ಮಾಡಬಲ್ಲೆ. ಹಾವು ಕಡಿದಿದ್ದರೆ " + n + " ಗೆ ಕರೆ ಮಾಡಿ."
                : "I can help with snakes, first aid, hospitals and how to use this app. If someone was bitten, call " + n + " now.";
    }

    private static boolean has(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
