package uk.co.palmr;

import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.FilteredTextRenderListener;
import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.LocationTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.RegionTextRenderFilter;
import com.itextpdf.text.pdf.parser.TextExtractionStrategy;
import com.itextpdf.text.pdf.parser.TextRenderInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PdfParseTest {
  private static final int CHOICE_INFO_PDF_PAGE = 20;
  private static final int CHOICE_PDF_PAGE = 21;
  private static final int COMIC_PDF_PAGE = 18;
  private static final int END_PDF_PAGE = 19;
  private static final int SUB_BOOK_TITLE = 433;

  public static void main(String[] args) throws IOException {
    PdfReader reader = new PdfReader(App.class.getClassLoader().getResource("").getPath() + "\\..\\..\\resources\\tbontb-regular.pdf");
    try {
      int page = SUB_BOOK_TITLE;

      System.out.println("Font grouping");
      tryFontGrouping(reader, page);

      System.out.println("Link annotations");
      tryDictionaryLookup(reader, page);
    }
    finally {
      reader.close();
    }
  }

  /**
   * Try looking for link regions and the text that's below the links
   */
  private static void tryDictionaryLookup(PdfReader pReader, int pPage) throws IOException {
    PdfReaderContentParser parser = new PdfReaderContentParser(pReader);
    PdfDictionary pageDict = pReader.getPageN(pPage);
    PdfArray lAnnotArray = pageDict.getAsArray(PdfName.ANNOTS);
    if (lAnnotArray != null) {
      for (int i = 0; i < lAnnotArray.size(); i++) {
        if (PdfName.LINK == lAnnotArray.getAsDict(i).get(PdfName.SUBTYPE)) {
          PdfArray lRectArray = (PdfArray) lAnnotArray.getAsDict(i).get(PdfName.RECT);

          PdfString lDestination = ((PdfDictionary)pReader.getPdfObject(lAnnotArray.getAsDict(0).getAsIndirectObject(PdfName.A))).getAsString(PdfName.D);
          pReader.getNamedDestinationFromStrings();
          pReader.getNamedDestination();
          pReader.getNamedDestination(true);

          RegionTextRenderFilter lFilter = new RegionTextRenderFilter(PdfReader.getNormalizedRectangle(lRectArray));
          TextExtractionStrategy lRenderListener = new FilteredTextRenderListener(new LocationTextExtractionStrategy(), lFilter);
          TextExtractionStrategy lStrategy = parser.processContent(pPage, lRenderListener);

          System.out.println("Annot/Link/Region matched: " + lStrategy.getResultantText());
        }
      }
    }
  }

  /**
   * Attempt to group the blocks of text by font type
   */
  private static void tryFontGrouping(PdfReader pReader, int page) {
    try {
      PdfReaderContentParser parser = new PdfReaderContentParser(pReader);
      FontGroupingTextExtractionStrategy strategy = parser.processContent(page, new FontGroupingTextExtractionStrategy(false));
      System.out.println(strategy.getResultantText());
      for (StringBuilder textBlock : strategy.getTextValues()) {
        if (textBlock.toString().matches("THE END(!!)?")) {
          System.out.println("END PAGE");
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }


}
