package uk.co.palmr;

import com.itextpdf.text.factories.RomanAlphabetFactory;
import com.itextpdf.text.factories.RomanNumberFactory;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.*;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.register.Register;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {
  private enum RelationshipTypes implements RelationshipType {
    Continues,
    Choice
  }

  private enum PageTypes implements Label {
    Page,
    ImagePage,
    EndPage,
    SubBook,
    Ignore
  }

  private static final String PDF_PAGE_NUMBER = "pdf_page_number";
  private static final String BOOK_PAGE_LABEL = "book_page_label";

  public static void main(String[] args) throws IOException {
    PdfReader reader = new PdfReader(App.class.getClassLoader().getResource("").getPath() + "\\..\\..\\resources\\tbontb-regular.pdf");

    GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(App.class.getClassLoader().getResource("").getPath() + "\\..\\..\\graph-db").newGraphDatabase();
    registerShutdownHook(graphDb);

    System.out.println("Attempting to clear database from last run");
    try (Transaction ignored = graphDb.beginTx();
         Result result = graphDb.execute("MATCH (n)\n" +
           "OPTIONAL MATCH (n)-[r]-()\n" +
           "DELETE n,r")) {
      System.out.println(result.resultAsString());
      ignored.success();
    }


    System.out.println("Attempting to populate database");
    try (Transaction tx = graphDb.beginTx()) {
      System.out.println("Creating page nodes");
      String[] bookPageLabels = getPageLabels(reader);
      Map<PdfObject, String> pdfPageToBookPageLabel = new HashMap<>(reader.getNumberOfPages());
      for (int pdfPageNumber = 1; pdfPageNumber <= reader.getNumberOfPages(); pdfPageNumber++) {
        Node pageNode = graphDb.createNode(PageTypes.Page);
        pageNode.setProperty(PDF_PAGE_NUMBER, pdfPageNumber);
        pageNode.setProperty(BOOK_PAGE_LABEL, bookPageLabels[pdfPageNumber - 1]);
        pdfPageToBookPageLabel.put(reader.getPageN(pdfPageNumber), bookPageLabels[pdfPageNumber - 1]);
      }

      System.out.println("Looping over pages");
      PdfReaderContentParser contentParser = new PdfReaderContentParser(reader);
      Map<String, PdfObject> linkDestinations = reader.getNamedDestinationFromStrings();

      for (int pdfPageNumber = 1; pdfPageNumber <= reader.getNumberOfPages(); pdfPageNumber++) {
//      int pdfPageNumber = 433;
//      {
        Node thisPage = graphDb.findNode(PageTypes.Page, PDF_PAGE_NUMBER, pdfPageNumber);
        if (thisPage == null) {
          throw new RuntimeException("Couldn't find a node for PDF page: " + pdfPageNumber);
        }

        String thisBookPageLabel = (String)thisPage.getProperty(BOOK_PAGE_LABEL);
        boolean isSubBook = false;
        if (thisBookPageLabel.startsWith("G")) {
          // All of the sub book (The Murder of Gonzago) page labels start with G
          thisPage.addLabel(PageTypes.SubBook);
        }
        if (!thisBookPageLabel.matches(".*\\d+.*")) {
          // If there's no digits in the label, ignore the page
          thisPage.addLabel(PageTypes.Ignore);
        }


        // Check for inter-page-links
        PdfDictionary pageDict = reader.getPageN(pdfPageNumber);
        PdfArray annotArray = pageDict.getAsArray(PdfName.ANNOTS);
        boolean linksOut = false;
        if (annotArray != null) {
          for (int i = 0; i < annotArray.size(); i++) {
            PdfDictionary annotDict = annotArray.getAsDict(i);
            if (PdfName.LINK == annotDict.get(PdfName.SUBTYPE)) {
              if (annotDict.contains(PdfName.A)) {
                String lDestination = ((PdfDictionary) reader.getPdfObject(annotDict.getAsIndirectObject(PdfName.A))).getAsString(PdfName.D).toString();
                if (linkDestinations.containsKey(lDestination)) {
                  PdfArray destinationInfoArray = (PdfArray) linkDestinations.get(lDestination);
                  PdfIndirectReference destinationReference = destinationInfoArray.getAsIndirectObject(0); // TODO, this could actually be an integer for the case of Remote Destinations
                  PdfObject targetPdfPage = PdfReader.getPdfObject(destinationReference);

                  // Create link if it hasn't already been made between these pages (Split-line links mean two annots with the same dest)
                  Node targetPage = graphDb.findNode(PageTypes.Page, BOOK_PAGE_LABEL, pdfPageToBookPageLabel.get(targetPdfPage));
                  boolean existingRelationship = false;
                  for (Relationship r : thisPage.getRelationships(Direction.BOTH, RelationshipTypes.Choice)) {
                    existingRelationship |= r.getEndNode().getId() == targetPage.getId();
                  }
                  if (!existingRelationship) {
                    thisPage.createRelationshipTo(targetPage, RelationshipTypes.Choice);
                    linksOut = true;
                  }
                }
                else {
                  System.out.println("Found link to unknown: " + lDestination);
                }
              }
              else {
                System.out.println("Unhandled");
              }
            }
          }
        }


        // Do some context scanning to find page types
        FontGroupingTextExtractionStrategy strategy = contentParser.processContent(pdfPageNumber, new FontGroupingTextExtractionStrategy(false));
        if (strategy.getImageCount() > 0 && (strategy.getTextValues().size() == 0 || (strategy.getTextValues().size() == 1 && strategy.getTextValues().get(0).toString().equals(bookPageLabels[pdfPageNumber])))) {
          // If the page is nothing but an image, label it as an image page (Typically an ending comic)
          thisPage.addLabel(PageTypes.ImagePage);
        }
        else {
          // If not an image page perhaps there's text to parse?
          for (StringBuilder textBlock : strategy.getTextValues()) {
            if (textBlock.toString().matches("THE END(!!)?")) {
              thisPage.addLabel(PageTypes.EndPage);
            }
          }
        }

        // Link to next page if no other relationships from this node
        if (!linksOut && !thisPage.hasLabel(PageTypes.EndPage)) {
          Node nextPage = graphDb.findNode(PageTypes.Page, PDF_PAGE_NUMBER, pdfPageNumber+1);
          if (nextPage != null) {
            thisPage.createRelationshipTo(nextPage, RelationshipTypes.Continues);
          }
        }
      }
      tx.success();
    }


    reader.close();
    graphDb.shutdown();

    // Query e.g. MATCH r=(s:SubBook {book_page_label: "Gi"})-[*..10]->(e:SubBook :EndPage) RETURN r
  }

  /**
   * Retrieves the page labels from a PDF as an array of String objects.
   * @param reader a PdfReader object that has the page labels you want to retrieve
   * @return	a String array or <code>null</code> if no page labels are present
   */
  public static String[] getPageLabels(PdfReader reader) {

    int n = reader.getNumberOfPages();

    PdfDictionary dict = reader.getCatalog();
    PdfDictionary labels = (PdfDictionary)PdfReader.getPdfObjectRelease(dict.get(PdfName.PAGELABELS));
    if (labels == null)
      return null;

    String[] labelstrings = new String[n];

    HashMap<Integer, PdfObject> numberTree = PdfNumberTree.readTree(labels);

    int pagecount = 1;
    Integer current;
    String prefix = "";
    char type = 'D';
    for (int i = 0; i < n; i++) {
      current = Integer.valueOf(i);
      if (numberTree.containsKey(current)) {
        PdfDictionary d = (PdfDictionary)PdfReader.getPdfObjectRelease(numberTree.get(current));
        if (d.contains(PdfName.ST)) {
          pagecount = ((PdfNumber)d.get(PdfName.ST)).intValue();
        }
        else {
          pagecount = 1;
        }
        if (d.contains(PdfName.P)) {
          prefix = ((PdfString)d.get(PdfName.P)).toUnicodeString();
        }
        else {
          prefix = ""; // NP - See page 596 of pdf ref, prefix is for that range only
        }
        if (d.contains(PdfName.S)) {
          type = ((PdfName)d.get(PdfName.S)).toString().charAt(1);
        }
        else {
          type = 'e';
        }
      }
      switch(type) {
        default:
          labelstrings[i] = prefix + pagecount;
          break;
        case 'R':
          labelstrings[i] = prefix + RomanNumberFactory.getUpperCaseString(pagecount);
          break;
        case 'r':
          labelstrings[i] = prefix + RomanNumberFactory.getLowerCaseString(pagecount);
          break;
        case 'A':
          labelstrings[i] = prefix + RomanAlphabetFactory.getUpperCaseString(pagecount);
          break;
        case 'a':
          labelstrings[i] = prefix + RomanAlphabetFactory.getLowerCaseString(pagecount);
          break;
        case 'e':
          labelstrings[i] = prefix;
          break;
      }
      pagecount++;
    }
    return labelstrings;
  }

  private static void registerShutdownHook(final GraphDatabaseService graphDb) {
    // Registers a shutdown hook for the Neo4j instance so that it
    // shuts down nicely when the VM exits (even if you "Ctrl-C" the
    // running application).
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        graphDb.shutdown();
      }
    });
  }
}
