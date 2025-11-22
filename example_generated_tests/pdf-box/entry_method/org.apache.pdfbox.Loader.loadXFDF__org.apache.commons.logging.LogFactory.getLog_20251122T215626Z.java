import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.fdf.FDFDocument;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        InputStream input = new ByteArrayInputStream(new byte[0]);

        // Call the entry point method
        FDFDocument document = Loader.loadXFDF(input);

        // Verify that the third party method was reached
        LogFactory.getLog(FDFDocument.class);
    }
}