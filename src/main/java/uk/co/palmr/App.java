package uk.co.palmr;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.SimpleTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.TextExtractionStrategy;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {
  private enum RelationshipTypes implements RelationshipType {
    ContinuesTo,
    RedirectsTo
  }

  private enum NodeTypes implements Label {
    Page,
    Ending
  }

  private static final int PAGE_OFFSET = 4;
  private static final int SUB_BOOK_OFFSET = 24;

  public static void main(String[] args) throws IOException {
    PdfReader reader = new PdfReader("C:\\Users\\npalmer\\git-projects\\Adventurer\\resources\\tbontb-regular.pdf");

    GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder("C:\\Users\\npalmer\\git-projects\\Adventurer\\graph-db").newGraphDatabase();
    registerShutdownHook(graphDb);

    try (Transaction tx = graphDb.beginTx()) {
      long[] pageNodeIdArray = new long[reader.getNumberOfPages() + 1];
      for (int page = 1 + PAGE_OFFSET; page <= reader.getNumberOfPages(); page++) {
        Node pageNode = graphDb.createNode(NodeTypes.Page);
        pageNode.setProperty("page_number", resolveBookPageNumber(page));
        pageNode.setProperty("pdf_page_number", page);
        pageNodeIdArray[page] = pageNode.getId();
      }

      PdfReaderContentParser parser = new PdfReaderContentParser(reader);
      TextExtractionStrategy strategy;
      for (int page = 1 + PAGE_OFFSET; page <= reader.getNumberOfPages(); page++) {
        strategy = parser.processContent(page, new SimpleTextExtractionStrategy());
        System.out.println(strategy.getResultantText());
        System.out.println("");

        Node thisPage = graphDb.getNodeById(pageNodeIdArray[page]);

        if (strategy.getResultantText().contains("THE END")) {
          thisPage.removeLabel(NodeTypes.Page);
          thisPage.addLabel(NodeTypes.Ending);
          System.out.println("End Page");
        }
        else {
          // TODO the choice part of the regex doesn't work multi-line properly, ideally use annotations and check for preceeding text blocks?
          Pattern pageLinkPattern = Pattern.compile("(.*?)turn to page ([0-9]+)", Pattern.CASE_INSENSITIVE);
          Matcher pageLinkMatcher = pageLinkPattern.matcher(strategy.getResultantText());
          boolean linksfound = false;
          while (pageLinkMatcher.find()) {
            System.out.println("Choice: " + pageLinkMatcher.group(1));
            System.out.println("Links to: " + pageLinkMatcher.group(2));

            Node targetPage = graphDb.getNodeById(pageNodeIdArray[resolvePDFPageNumber(Integer.valueOf(pageLinkMatcher.group(2)))]);

            Relationship relationship = thisPage.createRelationshipTo(targetPage, RelationshipTypes.RedirectsTo);
            relationship.setProperty("choice", pageLinkMatcher.group(1));

            System.out.println("Rel created from " + relationship.getStartNode().getProperty("page_number") + "[" + relationship.getStartNode().getProperty("pdf_page_number") + "] to " + relationship.getEndNode().getProperty("page_number") + "[" + relationship.getEndNode().getProperty("pdf_page_number") + "]");

            System.out.println("");
            linksfound = true;
          }
          if (!linksfound && page != reader.getNumberOfPages()) {
            Node targetPage = graphDb.getNodeById(pageNodeIdArray[page + 1]);
            thisPage.createRelationshipTo(targetPage, RelationshipTypes.ContinuesTo);
          }
        }
        System.out.println("");
        System.out.println("------------");
        System.out.println("");
      }
      reader.close();

      tx.success();
    }

    graphDb.shutdown();

    // Query e.g. MATCH (p:Page {page_number: 22})-[*..10]-(p2) RETURN p, p2
  }

  private static int resolveBookPageNumber(int pdfPageNumber) {
    if (pdfPageNumber <= 414) {
      return pdfPageNumber - PAGE_OFFSET;
    }
    else {
      return pdfPageNumber - PAGE_OFFSET - SUB_BOOK_OFFSET;
    }
  }

  private static int resolvePDFPageNumber(int bookPageNumber) {
    if (bookPageNumber <= 414) {
      return bookPageNumber + PAGE_OFFSET;
    }
    else {
      return bookPageNumber + PAGE_OFFSET + SUB_BOOK_OFFSET;
    }
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
