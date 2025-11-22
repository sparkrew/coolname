import org.junit.Test;
import org.apache.pdfbox.util.DateConverter;
import org.apache.commons.logging.LogFactory;

public class DateConverterTest {

    @Test
    public void testGetLog() {
        // Setup
        Calendar cal = Calendar.getInstance();
        String offset = formatTZoffset(cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET), "'");
        String expected = String.format(Locale.US, ((("D:" + "%1$4tY%1$2tm%1$2td") + "%1$2tH%1$2tM%1$2tS") + "%2$s") + "'", cal, offset);

        // Call the entry point method
        String actual = DateConverter.toString(cal);

        // Assert
        assertEquals(expected, actual);
    }
}