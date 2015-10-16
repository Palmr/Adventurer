package uk.co.palmr;

import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
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

  public static void main(String[] args) throws IOException {
    PdfReader reader = new PdfReader("C:\\Users\\npalmer\\git-projects\\Adventurer\\resources\\tbontb-regular.pdf");
    try {
      int page = CHOICE_INFO_PDF_PAGE;

      tryFontGrouping(reader, page);

      // Try looking for link regions and the text that's below the links?
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
      TextExtractionStrategy strategy = parser.processContent(page, new FontGroupingTextExtractionStrategy());
      System.out.println(strategy.getResultantText());
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static class FontGroupingTextExtractionStrategy implements TextExtractionStrategy {
    private String mLastTextFontName = null;
    private List<StringBuilder> mTextValues = new ArrayList<>();

    @Override
    public void beginTextBlock() {
      System.out.println("Beginning Text Block");
      mLastTextFontName = null;
    }

    @Override
    public void endTextBlock() {
      System.out.println("Ending Text Block");
      mLastTextFontName = null;
    }

    @Override
    public void renderText(TextRenderInfo pTextRenderInfo) {
      System.out.println("\tRendering Text");
      System.out.println("\t\tText: " + pTextRenderInfo.getText());
      System.out.println("\t\tRender Mode: " + pTextRenderInfo.getTextRenderMode());
      System.out.println("\t\tFont Name: " + pTextRenderInfo.getFont().getPostscriptFontName());
      System.out.println("\t\tFill Colour: " + pTextRenderInfo.getFillColor());
      System.out.println("\t\tStroke Colour: " + pTextRenderInfo.getStrokeColor());
      System.out.println("\t\tMCID: " + pTextRenderInfo.getMcid());

      if (mLastTextFontName != null && mLastTextFontName.equals(pTextRenderInfo.getFont().getPostscriptFontName())) {
        int lastIndex = mTextValues.size()-1;
        mTextValues.set(lastIndex, mTextValues.get(lastIndex).append(pTextRenderInfo.getText()));
      }
      else {
        mTextValues.add(new StringBuilder(pTextRenderInfo.getText()));
        mLastTextFontName = pTextRenderInfo.getFont().getPostscriptFontName();
      }
    }

    @Override
    public void renderImage(ImageRenderInfo pImageRenderInfo) {
      System.out.println("Rendering Image");
      try {
        System.out.println("\tImage File Type: " + pImageRenderInfo.getImage().getFileType());
      }
      catch (IOException e) {
        System.out.println("\tImage File Type: ERROR: " + e.getMessage());
      }
    }

    @Override
    public String getResultantText() {
      StringBuilder lPageContent = new StringBuilder();
      for (StringBuilder lBlock : mTextValues) {
        lPageContent.append(lBlock);
        lPageContent.append("\r\n--\r\n");
      }

      return "Resulting text:\r\n\r\n" + lPageContent.toString();
    }
  }
}
