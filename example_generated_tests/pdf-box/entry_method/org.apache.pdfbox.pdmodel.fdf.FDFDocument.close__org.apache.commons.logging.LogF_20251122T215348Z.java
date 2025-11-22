import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.pdmodel.fdf.FDFDocument;
import org.junit.Test;

public class FDFDocumentTest {
    @Test
    public void testClose() throws IOException {
        FDFDocument document = new FDFDocument();
        try {
            document.close();
        } finally {
            LogFactory.getLog(FDFDocument.class).info("Closing FDFDocument");
        }
    }
}