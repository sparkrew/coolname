package org.apache.pdfbox.pdmodel.interactive.digitalsignature;

import org.apache.commons.logging.LogFactory;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class SignatureOptionsTest {
    @Test
    public void testSetVisualSignature() throws IOException {
        SignatureOptions options = new SignatureOptions();
        options.setVisualSignature(new File("path/to/visual/signature.pdf"));

        // Mock the LogFactory.getLog() method to avoid unrelated logic
        LogFactory mockLogFactory = mock(LogFactory.class);
        when(mockLogFactory.getLog(anyString())).thenReturn(mock(Log.class));

        // Verify that the third party method is called
        verify(mockLogFactory, times(1)).getLog(anyString());
    }
}