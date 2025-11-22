import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDDocument template = new PDDocument();
        PDAcroForm theAcroForm = new PDAcroForm(template);
        template.getDocumentCatalog().setAcroForm(theAcroForm);
        PDVisibleSigBuilder.pdfStructure.setAcroForm(theAcroForm);

        // Call the entry point method
        PDVisibleSigBuilder.createAcroForm(template);

        // Verify that the third party method was reached
        LogFactory.getLog(PDVisibleSigBuilder.class);
    }
}