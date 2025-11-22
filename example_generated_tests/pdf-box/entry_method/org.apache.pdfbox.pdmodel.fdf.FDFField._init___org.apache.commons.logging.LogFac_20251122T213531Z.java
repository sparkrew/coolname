import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.fdf.FDFField;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        COSDictionary field = new COSDictionary();
        FDFField fdfField = new FDFField(field);

        // Execute
        LogFactory.getLog(fdfField);

        // Verify
        assertTrue(fdfField.getField().equals(field));
    }
}