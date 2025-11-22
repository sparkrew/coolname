import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.pdmodel.documentinterchange.prepress.PDBoxStyle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.junit.Test;

public class ReachabilityTest {
    @Test
    public void testReachability() {
        // Setup
        PDBoxStyle boxStyle = new PDBoxStyle();
        LogFactory logFactory = LogFactory.getLog(PDBoxStyle.class);

        // Execute
        PDColor color = boxStyle.getGuidelineColor();

        // Verify
        assertNotNull(color);
        assertEquals(PDColor.class, color.getClass());
    }
}