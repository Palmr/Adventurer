package uk.co.palmr.adventurer.itextbug;


import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Version;
import com.itextpdf.text.pdf.PdfPageLabels;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;

/**
 * There was an issue with iText 5.5.7 and below where the page label table parsing function PdfPageLabels.getPageLabels()
 * was not clearing the prefix when the page label scope changed. I fixed that while developing this code and submitted
 * it to iText which was accepted https://github.com/itext/itextpdf/commit/856b64853eb9a28ec6c430ef4e244c160c3281f6
 *
 * I needed to create a PDF to write a unit test against so that I could check my fix worked and this is the code I
 * wrote to create the test PDF
 */
public class CreatePageLabelTestPDF {

  public static void main(String[] args) throws IOException, DocumentException {
    String lPageLabelTestPDFPath = System.getProperty("user.dir") + File.separator + "resources" + File.separator + "test-prefix-reset.pdf";
    File lTestPDF = new File(lPageLabelTestPDFPath);
    System.out.println("Creating test PDF: " + lPageLabelTestPDFPath);

    // Create a new PDF with A4 pages
    Document document = new Document(PageSize.A4);
    PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(lTestPDF));
    document.open();

    // Create 20 pages in the PDF
    int[] start = new int[20];
    for (int i = 0; i < 20; i++) {
      start[i] = writer.getPageNumber();
      document.add(new Paragraph("PDF Page: " + (i+1)));
      document.newPage();
    }

    // Create page labels for pages in groups of 5 pages at a time
    PdfPageLabels labels = new PdfPageLabels();
    labels.addPageLabel(start[0], PdfPageLabels.LOWERCASE_ROMAN_NUMERALS);          // Start off with lower case roman numerals (i - v)
    labels.addPageLabel(start[5], PdfPageLabels.DECIMAL_ARABIC_NUMERALS);           // Then use to regular digits (1 - 5)
    labels.addPageLabel(start[10], PdfPageLabels.DECIMAL_ARABIC_NUMERALS, "G", 1);  // Change to 'G' prefix and reset page count to 1 (G1 - G5)
    labels.addPageLabel(start[15], PdfPageLabels.DECIMAL_ARABIC_NUMERALS, null, 6); // Go back to regular digits but remove the prefix and carry on from the last set of digits (6 - 10)
    writer.setPageLabels(labels);
    document.close();

    // Do a quick test
    pageLabelReadTest(lPageLabelTestPDFPath);
  }

  /**
   * Read through the PDF Page Labels using iText to see what the result is with the current library
   * @param lPageLabelTestPDFPath Path to the PDF
   * @throws IOException
   */
  private static void pageLabelReadTest(String lPageLabelTestPDFPath) throws IOException {
    System.out.println("Page label output using iText library: " + new Version().getVersion());

    PdfReader reader = new PdfReader(lPageLabelTestPDFPath);
    String[] pageLabels = PdfPageLabels.getPageLabels(reader);
    for (int pdfPageNumber = 1; pdfPageNumber <= reader.getNumberOfPages(); pdfPageNumber++) {
      System.out.println(pdfPageNumber + " - " + pageLabels[pdfPageNumber-1]);
    }
  }
}
