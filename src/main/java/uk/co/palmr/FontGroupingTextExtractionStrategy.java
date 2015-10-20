package uk.co.palmr;

import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.TextExtractionStrategy;
import com.itextpdf.text.pdf.parser.TextRenderInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Grouping blocks of text by font name and size helps split pages up into chunks of related text
 */
public class FontGroupingTextExtractionStrategy implements TextExtractionStrategy {
  private final boolean mDebug;
  private String mLastTextFontName = null;
  private int mLastTextSpaceWidth = -1;
  private final List<StringBuilder> mTextValues = new ArrayList<>();
  private int mImageCount = 0;

  public FontGroupingTextExtractionStrategy(boolean debug) {
    super();
    mDebug = debug;
  }

  @Override
  public void beginTextBlock() {
    if (mDebug) {
      System.out.println("Beginning Text Block");
    }
    mLastTextFontName = null;
    mLastTextSpaceWidth = -1;
  }

  @Override
  public void endTextBlock() {
    if (mDebug) {
      System.out.println("EndPage Text Block");
    }
    mLastTextFontName = null;
    mLastTextSpaceWidth = -1;
  }

  @Override
  public void renderText(TextRenderInfo pTextRenderInfo) {
    if (mDebug) {
      System.out.println("\tRendering Text");
      System.out.println("\t\tText: " + pTextRenderInfo.getText());
      System.out.println("\t\tFont Name: " + pTextRenderInfo.getFont().getPostscriptFontName());
      System.out.println("\t\tWidth: " + pTextRenderInfo.getSingleSpaceWidth());
    }

    if ((mLastTextFontName != null && mLastTextFontName.equals(pTextRenderInfo.getFont().getPostscriptFontName()))
      && (mLastTextSpaceWidth != -1 && mLastTextSpaceWidth == Float.valueOf(pTextRenderInfo.getSingleSpaceWidth()).intValue())) {
      int lastIndex = mTextValues.size() - 1;
      mTextValues.set(lastIndex, mTextValues.get(lastIndex).append(pTextRenderInfo.getText()));
    } else {
      mTextValues.add(new StringBuilder(pTextRenderInfo.getText()));
      mLastTextFontName = pTextRenderInfo.getFont().getPostscriptFontName();
      mLastTextSpaceWidth = Float.valueOf(pTextRenderInfo.getSingleSpaceWidth()).intValue();
    }
  }

  @Override
  public void renderImage(ImageRenderInfo pImageRenderInfo) {
    if (mDebug) {
      System.out.println("Rendering Image");
      try {
        System.out.println("\tImage File Type: " + pImageRenderInfo.getImage().getFileType());
      } catch (IOException e) {
        System.out.println("\tImage File Type: ERROR: " + e.getMessage());
      }
    }
    mImageCount++;
  }

  @Override
  public String getResultantText() {
    StringBuilder lPageContent = new StringBuilder();
    for (StringBuilder lBlock : mTextValues) {
      lPageContent.append(lBlock);
      lPageContent.append("\r\n--\r\n");
    }
    return lPageContent.toString();
  }

  public List<StringBuilder> getTextValues() {
    return Collections.unmodifiableList(mTextValues);
  }

  public int getImageCount() {
    return mImageCount;
  }
}