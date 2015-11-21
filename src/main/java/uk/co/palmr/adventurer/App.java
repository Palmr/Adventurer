package uk.co.palmr.adventurer;

import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfIndirectReference;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.SimpleTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.TextExtractionStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import uk.co.palmr.adventurer.itextbug.FixedPdfPageLabels;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class App {
  private static final Logger LOGGER = LogManager.getLogger(App.class);

  /**
   * Pages can either continue onto the next page or be linked to another page via a choice
   */
  private enum RelationshipTypes implements RelationshipType {
    Continues,
    Choice
  }

  /**
   * I categorised the main page types found in To Be Or Not To Be
   */
  private enum PageTypes implements Label {
    Page,
    ImagePage,
    EndPage,
    SubBook,
    Ignore
  }

  // Property names for data stored on nodes and relationships
  private static final String PDF_PAGE_NUMBER = "pdf_page_number";
  private static final String BOOK_PAGE_LABEL = "book_page_label";
  private static final String WORD_COUNT = "word_count";

  public static void main(String[] args) throws IOException {
    String pdfFilePath = System.getProperty("user.dir") + File.separator + "resources" + File.separator + "tbontb-regular.pdf";

    // Set up the database
    GraphDatabaseService graphDb = getDatabase();

    // Set up the PDF Reader
    PdfReader reader = new PdfReader(pdfFilePath);

    // Set up a content parser for the PDF
    PdfReaderContentParser contentParser = new PdfReaderContentParser(reader);

    // Parse the page labels from the PDF
    String[] pageLabels = FixedPdfPageLabels.getPageLabels(reader);

    LOGGER.info("Populating database");
    try (Transaction tx = graphDb.beginTx()) {
      // Create nodes in the database for all the pages in the PDF
      Map<PdfObject, String> pdfPageToPageLabel = createPageNodes(reader, contentParser, pageLabels, graphDb);

      // Process each page, classifying it and updating the database
      processPages(reader, contentParser, pdfPageToPageLabel, graphDb);

      tx.success();
    }
    LOGGER.info("Finished populating database");


    reader.close();
    graphDb.shutdown();

    // Query e.g. MATCH r=(s:SubBook)-[*..20]->(e:SubBook :EndPage) RETURN r
  }

  /**
   * Set up a GraphDatabaseService for Adventurer to fill with data
   *
   * @return Graph database handle
   */
  private static GraphDatabaseService getDatabase() {
    String graphDbPath = System.getProperty("user.dir") + File.separator + "graph-db";

    GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(new File(graphDbPath)).newGraphDatabase();
    registerShutdownHook(graphDb);

    clearGraphDB(graphDb);

    return graphDb;
  }

  /**
   * Registers a shutdown hook for the Neo4j instance so that it shuts down nicely when the VM exits (even if you
   * "Ctrl-C" the running application)
   *
   * @param graphDb Database to clear
   */
  private static void registerShutdownHook(final GraphDatabaseService graphDb) {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        graphDb.shutdown();
      }
    });
  }

  /**
   * Clear out all nodes and relationships from the Graph Database
   *
   * @param graphDb Database to clear
   */
  private static void clearGraphDB(GraphDatabaseService graphDb) {
    LOGGER.info("Attempting to clear database from last run");
    try (Transaction tx = graphDb.beginTx();
         Result result = graphDb.execute(
           "MATCH (n)\n" +
           "OPTIONAL MATCH (n)-[r]-()\n" +
           "DELETE n, r")
    ) {
      // Output the result of the delete statement
      LOGGER.trace(result.resultAsString());

      tx.success();
    }
  }

  /**
   * First pass of the PDF pages to create unconnected nodes in the database and generate a map of pages to page labels.
   *
   * @param reader PdfReader to get PDF information from
   * @param contentParser PdfReaderContentParser to extract text with
   * @param pageLabels Array of page labels
   * @param graphDb Database
   * @return Map of PdfObjects representing pages to page labels
   */
  private static Map<PdfObject, String> createPageNodes(PdfReader reader, PdfReaderContentParser contentParser, String[] pageLabels, GraphDatabaseService graphDb) {
    LOGGER.info("Creating page nodes");

    Map<PdfObject, String> pdfPageToPageLabel = new HashMap<>(reader.getNumberOfPages());

    TextExtractionStrategy strategy;

    for (int pdfPageNumber = 1; pdfPageNumber <= reader.getNumberOfPages(); pdfPageNumber++) {
      Node pageNode = graphDb.createNode(PageTypes.Page);
      pageNode.setProperty(PDF_PAGE_NUMBER, pdfPageNumber);
      pageNode.setProperty(BOOK_PAGE_LABEL, pageLabels[pdfPageNumber - 1]);

      try {
        strategy = contentParser.processContent(pdfPageNumber, new SimpleTextExtractionStrategy());
        String[] words = strategy.getResultantText().split("\\s+");
        pageNode.setProperty(WORD_COUNT, words.length);
      }
      catch (IOException e) {
        LOGGER.error("Failed to parse simple text content from page to get word count", e);
      }

      pdfPageToPageLabel.put(reader.getPageN(pdfPageNumber), pageLabels[pdfPageNumber - 1]);
    }

    LOGGER.info("Finished creating page nodes");

    return pdfPageToPageLabel;
  }

  /**
   * Process each page in the PdfReader looking for features that can be noted in the database (e.g. image page, end
   * page, relationships)
   *
   * @param reader PdfReader to get PDF information from
   * @param contentParser PdfReaderContentParser to extract text with
   * @param pdfPageToPageLabel Map of PdfObjects representing pages to page labels
   * @param graphDb Database
   * @throws IOException
   */
  private static void processPages(PdfReader reader, PdfReaderContentParser contentParser, Map<PdfObject, String> pdfPageToPageLabel, GraphDatabaseService graphDb) throws IOException {
    LOGGER.info("Processing all pages");

    // Get a map of all link names to PdfObjects representing pages for the book
    Map<String, PdfObject> linkDestinations = reader.getNamedDestinationFromStrings();

    FontGroupingTextExtractionStrategy strategy;

    // Go through each page again but this time classify them and create links
    for (int pdfPageNumber = 1; pdfPageNumber <= reader.getNumberOfPages(); pdfPageNumber++) {
      LOGGER.trace("PDF Page " + pdfPageNumber);

      // Get the pre-existing node from the database for this page
      Node thisPage = graphDb.findNode(PageTypes.Page, PDF_PAGE_NUMBER, pdfPageNumber);
      if (thisPage == null) {
        throw new RuntimeException("Couldn't find a node for PDF page: " + pdfPageNumber);
      }

      // Get the page label for this page
      String thisBookPageLabel = (String)thisPage.getProperty(BOOK_PAGE_LABEL);

      // Start classifying the page based on the label
      if (thisBookPageLabel.startsWith("G")) {
        // All of the sub book (The Murder of Gonzago) page labels start with G
        thisPage.addLabel(PageTypes.SubBook);
      }
      if (!thisBookPageLabel.matches(".*\\d+.*")) {
        // If there's no digits in the label, ignore the page (roman numeral pages)
        thisPage.addLabel(PageTypes.Ignore);
      }

      // Check for inter-page-links
      PdfDictionary pageDict = reader.getPageN(pdfPageNumber);
      PdfArray annotationArray = pageDict.getAsArray(PdfName.ANNOTS);
      boolean linksOut = false;
      if (annotationArray != null) {
        for (int i = 0; i < annotationArray.size(); i++) {
          PdfDictionary annotationDictionary = annotationArray.getAsDict(i);
          if (PdfName.LINK == annotationDictionary.get(PdfName.SUBTYPE)) {
            if (annotationDictionary.contains(PdfName.A)) {
              String lDestination = ((PdfDictionary) PdfReader.getPdfObject(annotationDictionary.getAsIndirectObject(PdfName.A))).getAsString(PdfName.D).toString();
              if (linkDestinations.containsKey(lDestination)) {
                PdfArray destinationInfoArray = (PdfArray) linkDestinations.get(lDestination);
                PdfIndirectReference destinationReference = destinationInfoArray.getAsIndirectObject(0); // TODO, this could actually be an integer for the case of Remote Destinations
                PdfObject targetPdfPage = PdfReader.getPdfObject(destinationReference);

                // Create link if it hasn't already been made between these pages (Split-line links mean two annotations with the same dest)
                Node targetPage = graphDb.findNode(PageTypes.Page, BOOK_PAGE_LABEL, pdfPageToPageLabel.get(targetPdfPage));
                boolean existingRelationship = false;
                for (Relationship r : thisPage.getRelationships(Direction.BOTH, RelationshipTypes.Choice)) {
                  existingRelationship |= r.getEndNode().getId() == targetPage.getId();
                }
                if (!existingRelationship) {
                  Relationship pageLink = thisPage.createRelationshipTo(targetPage, RelationshipTypes.Choice);
                  pageLink.setProperty(WORD_COUNT, targetPage.getProperty(WORD_COUNT, 0));
                  linksOut = true;
                }
              }
              else {
                LOGGER.warn("Found link to unknown: " + lDestination);
              }
            }
            else {
              LOGGER.warn("Adventurer only handles Anchor Links currently");
            }
          }
        }
      }


      // Do some context scanning to find page types
      strategy = contentParser.processContent(pdfPageNumber, new FontGroupingTextExtractionStrategy(false));
      if (strategy.getImageCount() > 0
          && (strategy.getTextValues().size() == 0
              || (strategy.getTextValues().size() == 1
                  && thisBookPageLabel.equals(strategy.getTextValues().get(0).toString())
                  )
              )
          ) {
        // If the page is nothing but an image, label it as an image page (Typically an ending comic)
        thisPage.addLabel(PageTypes.ImagePage);
      }
      else {
        // If not an image page perhaps there's text to parse looking for an end page
        strategy.getTextValues().stream()
          .filter(textBlock -> textBlock.toString().matches("THE END(!!)?"))
          .forEach(textBlock -> thisPage.addLabel(PageTypes.EndPage));
      }

      // Link to next page if no other relationships from this node
      if (!linksOut && !thisPage.hasLabel(PageTypes.EndPage)) {
        Node nextPage = graphDb.findNode(PageTypes.Page, PDF_PAGE_NUMBER, pdfPageNumber+1);
        if (nextPage != null) {
          Relationship pageLink = thisPage.createRelationshipTo(nextPage, RelationshipTypes.Continues);
          pageLink.setProperty(WORD_COUNT, nextPage.getProperty(WORD_COUNT, 0));
        }
      }
    }

    LOGGER.info("Finished processing all pages");
  }
}
