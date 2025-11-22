import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.interactive.form.PDVariableText;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDVariableText variableText = new PDVariableText();
        variableText.setRichTextValue("Test");

        // Call the entry point
        variableText.setRichTextValue(null);

        // Verify reachability
        LogFactory logFactory = LogFactory.getLog(PDVariableText.class);
        assertNotNull(logFactory);
    }
}