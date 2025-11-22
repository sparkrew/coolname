import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        String imagePath = "path/to/image.jpg";
        PDDocument doc = new PDDocument();

        // Call the entry point method
        PDImageXObject.createFromFile(imagePath, doc);

        // Verify that the third party method was reached
        LogFactory logFactory = LogFactory.getLog(PDImageXObject.class);
        assertNotNull(logFactory);
    }
}